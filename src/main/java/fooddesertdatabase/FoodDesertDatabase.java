package fooddesertdatabase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteErrorCode;

import fooddesertserver.GroceryStore;

/**
 * @author john
 *         This class is used to interact with SqLite/SpatialLite databases
 *         with a 'grocery_store' table defined according to the schema in this
 *         class.
 *
 *         This class is Thread safe.
 */
public class FoodDesertDatabase implements AutoCloseable {

    /* String definitions for tables and columns in the spatial database */
    private static final String GROCERY_TABLE = "grocery_stores";
    private static final String GROCERY_ID_COLUMN = "id";
    private static final String GROCERY_LOCATION_COLUMN = "location";
    private static final String GROCERY_NAME_COLUMN = "name";

    private static final String SEARCHED_TABLE = "searched_area";
    private static final String SEARCHED_ID_COLUMN = "id";
    private static final String SEARCHED_BUFFER_COLUMN = "buffer";

    private static final String EPGS = "4326";

    /* Loads SqLite database connection */
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* geomReader is used to parse geographic elements of the database into JTS
     * objects.
     * *
     * I believe that WKTReader class is *not* thread safe so, I have made this
     * field ThreadLocal. Consider removing this if WKTReader is thread safe or
     * using synchronization instead of ThreadSafe.
     */
    private static final ThreadLocal<WKTReader> geomReader = new ThreadLocal<WKTReader>() {
        protected WKTReader initialValue() {
            return new WKTReader();
        }
    };

    /* This is the connection used to access the database */
    private final Connection connection;

    /**
     * Opens a connection and constructs an interface for accessing the database in
     * dbFile. This should only be called on a database that was created by a call
     * to SpatiaLiteInterface.createDatabase(String)
     *
     * @param dbFile name of SqLite database file
     * @throws SQLException
     */
    public FoodDesertDatabase(String dbFile) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile, config.toProperties());
        initSpatiaLite();
    }

    /**
     * Instantiate a SpatiaLite interface for an existing connection. Used to reuse
     * the connection used to create the database in createDatabase.
     *
     * @param spatialiteConnection
     * @throws SQLException
     */
    private FoodDesertDatabase(Connection spatialiteConnection) throws SQLException {
        this.connection = spatialiteConnection;
        initSpatiaLite();
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
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);

        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbname, config.toProperties());

        try (Statement stmt = conn.createStatement()) {
            /*declarations required to use SpatiaLite functions*/
            stmt.execute("SELECT load_extension('mod_spatialite')");
            stmt.execute("SELECT InitSpatialMetaData(1)");

            /*Create main grocery table*/
            stmt.execute("CREATE TABLE " + GROCERY_TABLE + "(" + GROCERY_ID_COLUMN + " INTEGER NOT NULL PRIMARY KEY, "
                    + GROCERY_NAME_COLUMN + " TEXT, " + GROCERY_LOCATION_COLUMN + " UNIQUE)");

            /*add a geometry column to this table and index it with a spatial index*/
            stmt.execute("SELECT RecoverGeometryColumn('" + GROCERY_TABLE + "', '" + GROCERY_LOCATION_COLUMN + "', " + EPGS
                    + ", 'POINT', 2)");
            stmt.execute("SELECT CreateSpatialIndex('" + GROCERY_TABLE + "', '" + GROCERY_LOCATION_COLUMN + "')");

            /*Create table to store area that has already been searched*/
            stmt.execute("CREATE TABLE " + SEARCHED_TABLE + "(" + SEARCHED_ID_COLUMN + " INTEGER NOT NULL PRIMARY KEY)");

            /*add a geometry column to this table and index it with a spatial index*/
            stmt.execute("SELECT AddGeometryColumn('" + SEARCHED_TABLE + "', '" + SEARCHED_BUFFER_COLUMN + "', " + EPGS
                    + ", 'POLYGON', 2)");
            stmt.execute("SELECT CreateSpatialIndex('" + SEARCHED_TABLE + "', '" + SEARCHED_BUFFER_COLUMN + "')");
        }

        return new FoodDesertDatabase(conn);
    }

    /* Using spatialite commands requires loading an extension */
    private void initSpatiaLite() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SELECT load_extension('mod_spatialite')");
        }
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

        String sql = "INSERT INTO " + GROCERY_TABLE + " ( " + GROCERY_NAME_COLUMN + ", " + GROCERY_LOCATION_COLUMN + ") "
                + "VALUES ( ? , GeomFromText(? , " + EPGS + "));";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, store.getName());
            stmt.setString(2, store.getLocation().toText());

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
        String sql = "SELECT " + GROCERY_ID_COLUMN + ", " + GROCERY_NAME_COLUMN + ", AsText(" + GROCERY_LOCATION_COLUMN + ") FROM "
                + GROCERY_TABLE + " WHERE " + GROCERY_ID_COLUMN + " IN (SELECT ROWID FROM SpatialIndex "
                + "WHERE f_table_name = '" + GROCERY_TABLE + "' AND search_frame = GeomFromText(?));";

        List<GroceryStore> selectedStores = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, searchFrame.toText());

            ResultSet selected = stmt.executeQuery();
            while (selected.next()) {
                int id = selected.getInt(1);
                String name = selected.getString(2);
                String locationWKT = selected.getString(3);
                Point location = (Point) geomReader.get().read(locationWKT);

                selectedStores.add(new GroceryStore(id, name, location));
            }
        }
        return selectedStores;
    }

    /**
     * Mark an area around a point as searched for grocery stores.
     *
     * @param searched The center of the searched area.
     * @param radius Distance around the center that was searched.
     * @throws SQLException
     */
    public void insertSearchedBuffer(Point searched, double radius) throws SQLException {
        String sql = "INSERT INTO " + SEARCHED_TABLE + " ( " + SEARCHED_BUFFER_COLUMN + ") "
                   + "VALUES (GeomFromText(? , " + EPGS + "));";
        Geometry buffer = searched.buffer(radius);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, buffer.toText());
            stmt.executeUpdate();
        }
    }

    /**
     * Test if a point is inside the area that has been searched for grocery stores.
     *
     * @param query Test point
     * @return True if query is contained within the searched area
     * @throws SQLException
     */
    public boolean inSearchedBuffer(Point query) throws SQLException {
        String sql = "SELECT count(*) FROM searched_area WHERE CONTAINS(searched_area.buffer, GeomFromText(?)) = 1;";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, query.toText());
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

    @Override
    public void close() throws SQLException {
        connection.close();
    }

}