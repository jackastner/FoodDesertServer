package database.fooddesert;

import database.SpatialiteDatabase;
import fooddesertserver.GroceryStore;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.sqlite.SQLiteErrorCode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author john
 *         This class is used to interact with SqLite/SpatialLite databases
 *         with a 'grocery_store' table defined according to the schema in this
 *         class.
 *
 *         All coordinates inserted into this database should be in WebMercator (EPSG 3857).
 *         Likewise, all coordinates retreived from this database will be projected in WebMercator.
 *
 *         This class is Thread safe.
 */
public class FoodDesertDatabase extends SpatialiteDatabase {

    /* String definitions for tables and columns in the spatial database */
    private static final String GROCERY_TABLE = "grocery_stores";
    private static final String GROCERY_ID_COLUMN = "id";
    private static final String GROCERY_LOCATION_COLUMN = "location";
    private static final String GROCERY_NAME_COLUMN = "name";

    private static final String SEARCHED_TABLE = "searched_area";
    private static final String SEARCHED_ID_COLUMN = "id";
    private static final String SEARCHED_BUFFER_COLUMN = "buffer";

    /**
     * Opens a connection and constructs an interface for accessing the database in
     * dbFile. This should only be called on a database that was created by a call
     * to SpatiaLiteInterface.createDatabase(String)
     *
     * @param dbFile name of SqLite database file
     * @throws SQLException
     */
    public FoodDesertDatabase(String dbFile) throws SQLException {
        super(dbFile);
    }

    /**
     * Creates a new database file with one user defined Grocery Store table contain
     * and ID, Name and, Location column. The location column is a SpatiaLite
     * geometry column and, a spatial index is created on this column.
     *
     * @param dbname Name of the database file to be created
     * @return A SpatialLiteInterface that provides access to the created database
     * @throws SQLException
     */
    public static FoodDesertDatabase createDatabase(String dbname) throws SQLException {
        FoodDesertDatabase database = new FoodDesertDatabase(dbname);

        try (Statement stmt = database.connection.createStatement()) {
            /*declarations required to use SpatiaLite functions*/
            stmt.execute("SELECT load_extension('mod_spatialite')");
            stmt.execute("SELECT InitSpatialMetaData(1)");

            /*Create main grocery table*/
            stmt.execute("CREATE TABLE " + GROCERY_TABLE + "(" + GROCERY_ID_COLUMN + " INTEGER NOT NULL PRIMARY KEY, "
                    + GROCERY_NAME_COLUMN + " TEXT, " + GROCERY_LOCATION_COLUMN + " UNIQUE)");

            /*add a geometry column to this table and index it with a spatial index*/
            stmt.execute("SELECT RecoverGeometryColumn('" + GROCERY_TABLE + "', '" + GROCERY_LOCATION_COLUMN + "', " + EPSG
                    + ", 'POINT', 2)");
            stmt.execute("SELECT CreateSpatialIndex('" + GROCERY_TABLE + "', '" + GROCERY_LOCATION_COLUMN + "')");

            /*Create table to store area that has already been searched*/
            stmt.execute("CREATE TABLE " + SEARCHED_TABLE + "(" + SEARCHED_ID_COLUMN + " INTEGER NOT NULL PRIMARY KEY)");

            /*add a geometry column to this table and index it with a spatial index*/
            stmt.execute("SELECT AddGeometryColumn('" + SEARCHED_TABLE + "', '" + SEARCHED_BUFFER_COLUMN + "', " + EPSG
                    + ", 'MULTIPOLYGON', 2)");
            stmt.execute("SELECT CreateSpatialIndex('" + SEARCHED_TABLE + "', '" + SEARCHED_BUFFER_COLUMN + "')");
        }

        return database;
    }


    /**
     * This method inserts a Grocery store into the SpatiaLite database and returns
     * a new store with Id set to the id of the store in the database. If this
     * method is called with on a store with id already set, an illegal argument
     * exception is thrown.
     *
     * @param store A newly created GroceryStore to be inserted into the database
     * @return An updated copy of the GroceryStore with id set to reflect the stores
     *         id in the database
     * @throws SQLException
     */
    public GroceryStore insertStore(GroceryStore store) throws SQLException {
        if (store.hasId()) {
            throw new IllegalArgumentException("Store already exists in database!");
        }

        String sql =
            "INSERT INTO " + GROCERY_TABLE + " ( " + GROCERY_NAME_COLUMN + ", " + GROCERY_LOCATION_COLUMN + ") " +
            "VALUES ( ? , GeomFromText(? , " + EPSG + "));";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            Point coordPoint = geoFactory.createPoint(store.getLocation());
            stmt.setString(1, store.getName());
            stmt.setString(2, coordPoint.toText());

            try {
                stmt.executeUpdate();
            } catch (SQLException sqlEx ) {
                /* It's fine if the unique constraint fails since that just means that a duplicate
                 * was not added to the db. Any other error should be re-thrown.
                 *
                 * This check actually only checks if the fail is caused by any constraint error.
                 * I would like it to check for specifically unique errors.*/
                if(sqlEx.getErrorCode() != SQLiteErrorCode.SQLITE_CONSTRAINT.code) {
                    throw sqlEx;
                }
            }
        }

        int id;
        sql = "SELECT last_insert_rowid();";
        try (Statement stmt = connection.createStatement()) {
            ResultSet res = stmt.executeQuery(sql);
            res.next();
            id = res.getInt(1);
        }

        return store.setId(id);
    }

    /**
     * Insert all of a collection of stores into the database.
     *
     * @param stores
     * @throws SQLException
     */
    public void insertAll(Iterable<GroceryStore> stores) throws SQLException {
        connection.setAutoCommit(false);
        for(GroceryStore s : stores) {
            insertStore(s);
        }
        connection.commit();
        connection.setAutoCommit(true);
    }

    public void insertAll(GroceryStore... stores) throws SQLException {
        insertAll(Arrays.asList(stores));
    }

    /**
     * Perform a spatial search on GroceryStores in the SpatialLite database.
     *
     * @param searchFrame Area to be searched. The search is conducted in the containing
     *        rectangle of this geometry
     * @return A list of GroceryStores in the database within the search frame
     * @throws SQLException
     * @throws ParseException
     */
    public List<GroceryStore> selectStore(Geometry searchFrame) throws SQLException, ParseException {
        String sql =
            "SELECT " + GROCERY_ID_COLUMN + ", " + GROCERY_NAME_COLUMN + ", AsText(" + GROCERY_LOCATION_COLUMN + ") " +
            "FROM " + GROCERY_TABLE + " " +
            "WHERE " + GROCERY_ID_COLUMN + " IN (" +
                spatialIndexSubQuery(GROCERY_TABLE) + ");";

        List<GroceryStore> selectedStores = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, searchFrame.toText());

            ResultSet selected = stmt.executeQuery();
            while (selected.next()) {
                int id = selected.getInt(1);
                String name = selected.getString(2);
                String locationWKT = selected.getString(3);
                Point location = (Point) geomReader.get().read(locationWKT);

                selectedStores.add(new GroceryStore(id, name, location.getCoordinate()));
            }
        }
        return selectedStores;
    }

    /**
     * Mark an area as searched for grocery stores.
     *
     * @param buffer The are that has been searched for stores.
     * @throws SQLException
     */
    public void insertSearchedBuffer(MultiPolygon buffer) throws SQLException {
        String sql =
            "INSERT INTO " + SEARCHED_TABLE + " ( " + SEARCHED_BUFFER_COLUMN + ") " +
            "VALUES (GeomFromText(? , " + EPSG + "));";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, buffer.toText());
            stmt.executeUpdate();
        }
    }

    public void insertSearchedBuffer(Polygon buffer) throws SQLException {
       MultiPolygon multiPolygon = geoFactory.createMultiPolygon(new Polygon[]{buffer});
       insertSearchedBuffer(multiPolygon);
    }

    public void insertSearchedBuffer(Geometry buffer) throws SQLException {
        if(buffer instanceof MultiPolygon){
            insertSearchedBuffer((MultiPolygon) buffer);
        } else if (buffer instanceof Polygon){
            insertSearchedBuffer((Polygon) buffer);
        }
    }

    /**
     * Test if a point is inside the area that has been searched for grocery stores.
     *
     * @param query Test point
     * @return True if query is contained within the searched area
     * @throws SQLException
     */
    public boolean inSearchedBuffer(Coordinate query) throws SQLException {
        String sql =
            "SELECT count(*) " +
            "FROM searched_area " +
            "WHERE CONTAINS(searched_area.buffer, GeomFromText(?)) = 1;";

        Point coordPoint = geoFactory.createPoint(query);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, coordPoint.toText());
            ResultSet result = stmt.executeQuery();
            if(result.next()) {
                int count = result.getInt(1);
                return count > 0;
            } else {
                throw new SQLException("A query that should always return a result did not return anything!");
            }
        }
    }

    /**
     * Select from the database the area within the search frame that is also within the area that has been searched
     * for grocery store.
     *
     * @param searchFrame Area being compared with the searched area
     * @return Geometry containing the intersection of searchFrame and the grocery store searched area.
     */
    public Geometry selectSearchedBuffer(Geometry searchFrame) throws SQLException, ParseException {
        String sql =
            "SELECT AsText(ST_Intersection(GeomFromText(?), ST_Union(" + SEARCHED_BUFFER_COLUMN + "))) " +
            "FROM " + SEARCHED_TABLE + " " +
            "WHERE " + SEARCHED_ID_COLUMN + " IN (" +
                spatialIndexSubQuery(SEARCHED_TABLE) + ");";

        String searchFrameWKT = searchFrame.toText();
        return querySingleGeometryResult(sql, searchFrameWKT, searchFrameWKT);
    }

    /**
     * Select from the database the area within the search frame that is NOT within the area that has been searched for
     * grocery stores.
     *
     * @param searchFrame Area being compared with the searched area.
     * @return Geometry containing the difference between the search frame and the searched area.
     */
    public Geometry selectUnsearchedBuffer(Geometry searchFrame) throws SQLException, ParseException {
        /* The case expression is needed because SpatiaLite returns null instead of an empty geometry. This is inconvenient
         * because SpatiaLite functions do not treat null inputs as empty geometries. */
        String sql =
            "SELECT CASE " +
                "WHEN ST_Union(" + SEARCHED_BUFFER_COLUMN + ") IS NULL " +
                    "THEN ? " +
                "ELSE " +
                    "AsText(ST_Difference(GeomFromText(?), ST_Union(" + SEARCHED_BUFFER_COLUMN + "))) " +
            "END " +
            "FROM " + SEARCHED_TABLE + " " +
            "WHERE " + SEARCHED_ID_COLUMN + " IN (" +
                spatialIndexSubQuery(SEARCHED_TABLE) + ");";

        String searchFrameWKT = searchFrame.toText();
        return querySingleGeometryResult(sql, searchFrameWKT, searchFrameWKT, searchFrameWKT);
    }

    /**
     * Delete the contents of this database while preserving the structure
     * @throws SQLException
     */
    public void truncate() throws SQLException {
        String sql0 = "DELETE FROM " + GROCERY_TABLE + ";";
        String sql1 = "DELETE FROM " + SEARCHED_TABLE + ";";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql0);
            stmt.executeUpdate(sql1);
        }
    }

    public String getEpsg(){
        return EPSG;
    }
}