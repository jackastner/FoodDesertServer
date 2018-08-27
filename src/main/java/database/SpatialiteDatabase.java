package database;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.sqlite.SQLiteConfig;

import java.sql.*;
import java.util.function.Function;

/**
 * A class that handles opening and closing a connection for a Spatialite database.
 * @author john
 */
public class SpatialiteDatabase implements AutoCloseable {

    /* All subclasses should use this value as their EPSG so that they can communicate without
     * converting coordinate systems. */
    protected static final String EPSG = "3857";

    /*must load sqlite jdbc class before connecting to any sqlite database*/
    static {
         try {
             Class.forName("org.sqlite.JDBC");
         } catch (Exception e) {
             e.printStackTrace();
         }
    }

    /**
     * Construct a common subquery used when working with a SpatiaLite spatial index.
     * The constructed subquery will select ids from the provided table such that the geometries
     * of the associated rows intersect the search frame provided as an argument to an SQL prepared statement.
     * @param indexedTable Geometry table to be queried.
     * @return An SQL query to be used in a prepared statement that expects a WKT encoded search frame to be inserted
     *         as it's prepared statement parameter.
     */
    protected static String spatialIndexSubQuery(String indexedTable){
        return "SELECT ROWID " +
               "FROM SpatialIndex " +
               "WHERE f_table_name = '" + indexedTable + "' " +
               "  AND search_frame = GeomFromText(?));";
    }

    /**
     * geomReader is used to parse geographic elements of the database into JTS
     * objects. It is a member of this super class because WKT reading is a common task
     * required by any class wanting to communicate with a Spatialite database.
     *
     * I believe that WKTReader class is *not* thread safe so, I have made this
     * field ThreadLocal. Consider removing this if WKTReader is thread safe or
     * using synchronization instead of ThreadLocal.
     */
    protected static final ThreadLocal<WKTReader> geomReader = ThreadLocal.withInitial(WKTReader::new);

    protected final Connection connection;

    /* Most geometry construction is handled by geomReader but there are some cases
     * where this class needs to build a Geometry directly. */
    protected final GeometryFactory geoFactory;

    /**
     * Opens a connection for the Sqlite database in dbFile then loads the required extensions for Spatialite.
     */
    protected SpatialiteDatabase(String dbFile) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile, config.toProperties());
        initSpatiaLite();
        this.geoFactory = new GeometryFactory();
    }

    /* Using spatialite commands requires loading an extension */
    private void initSpatiaLite() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SELECT load_extension('mod_spatialite')");
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    /**
     * A utility function to execute a database query where the result set will contain exactly 1 geometry.
     * @param sql The SQL query to be executed. This string can (should) be intended for use as a prepared statement.
     * @param args Arguments that wil passed through to the SQL query as string arguments to a prepared statement.
     * @return The single geometry returned by the query.
     * @throws SQLException Thrown when the result set is empty of and exception is thrown by JDBC
     */
     protected Geometry querySingleGeometryResult(String sql, String... args) throws SQLException, ParseException {
         ResultProcessor<Geometry> op = result -> {
             String resultWKT = result.getString(1);
             /* It appears that when a result geometry is empty, the WKT returned by SpatiaLite is null */
             if(resultWKT == null){
                 return geoFactory.createGeometryCollection();
             } else {
                 return geomReader.get().read(resultWKT);
             }
         };
         return queryWithResult(sql, op, args);
     }

    /**
     * A utility function to execute a database query where the result set will contain at least one result.
     * @param sql The SQL query to be executed. This string can (should) be intended for use as a prepared statement.
     * @param args Arguments that wil passed through to the SQL query as string arguments to a prepared statement.
     * @return the result set of the query.
     * @throws SQLException Thrown when the result set is empty of and exception is thrown by JDBC
     */
    protected <T> T queryWithResult(String sql, ResultProcessor<T> op, String... args) throws SQLException, ParseException {
         try(PreparedStatement stmt = connection.prepareStatement(sql)) {
             for (int i = 0; i < args.length; i++) {
                 stmt.setString(i + 1, args[i]);
             }

             ResultSet result = stmt.executeQuery();
             if (result.next()) {
                 return op.process(result);
             } else {
                 throw new SQLException("A query that should always return a result did not return anything!");
             }
         }
    }

    /**
     * Pass through autocommit function of the connection.
     * This lets calling classes batch a sequence of queries.
     */
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);
    }

    /**
     * Pass through commit function of the connection.
     */
    public void commit() throws SQLException {
        connection.commit();
    }

    @FunctionalInterface
    public interface ResultProcessor<T> extends Function<ResultSet, T> {
        default T apply(ResultSet set){
            try{
                return process(set);
            } catch (Exception ignored) {
                return null;
            }
        }

        T process(ResultSet set) throws SQLException, ParseException;
    }
}
