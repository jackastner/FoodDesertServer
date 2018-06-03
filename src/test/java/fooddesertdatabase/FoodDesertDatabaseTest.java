package fooddesertdatabase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;

import fooddesertserver.GroceryStore;

public class FoodDesertDatabaseTest {

    private static FoodDesertDatabase dbInterface;

    private GeometryFactory geoFactory;
    private GroceryStore testStoreName;
    private GroceryStore testStoreNullName;
    private Geometry searchFrame;

    @BeforeClass
    public static void openDB() throws SQLException, IOException {
        String testDBName = "test.db";

        Path dbPath = Paths.get(testDBName);
        if (Files.exists(dbPath, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(dbPath);
        }

        dbInterface = FoodDesertDatabase.createDatabase(testDBName);
    }

    @AfterClass
    public static void closeDB() throws SQLException {
        dbInterface.close();
    }

    @Before
    public void setupDB() throws SQLException {
        dbInterface.truncate();

        geoFactory = new GeometryFactory();
        testStoreName = new GroceryStore("test", geoFactory.createPoint(new Coordinate(0, 0)));
        testStoreNullName = new GroceryStore(null, geoFactory.createPoint(new Coordinate(1, 1)));

        searchFrame = geoFactory.createPolygon(new Coordinate[] {
                new Coordinate(-5, -5),
                new Coordinate( 5, -5),
                new Coordinate( 5,  5),
                new Coordinate(-5,  5),
                new Coordinate(-5, -5)
        });
    }

    /**
     * Test that basic insertions will return a store with a new, valid, id.
     */
    @Test
    public void basicInsertTest() throws SQLException {
        GroceryStore r0 = dbInterface.insertStore(testStoreName);
        assertNotEquals(-1, r0.getId());

        GroceryStore r1 = dbInterface.insertStore(testStoreNullName);
        assertNotEquals(-1, r1.getId());
    }

    /**
     * Test that inserting a store that already is in the database (has an id) will
     * result in an exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void doubleInsertTest() throws SQLException {
        GroceryStore r0 = dbInterface.insertStore(testStoreName);
        dbInterface.insertStore(r0);
    }

    /**
     * Test that a simple spatial query
     */
    @Test
    public void testSpatialQuery() throws SQLException, ParseException {
        dbInterface.insertStore(testStoreName);

        List<GroceryStore> result = dbInterface.selectStore(searchFrame);

        assertFalse(result.isEmpty());
        assertEquals(testStoreName.getName(), result.get(0).getName());
        assertEquals(testStoreName.getLocation(), result.get(0).getLocation());
    }

    /**
     * Test that an spatial query that should not contain any store is actualy empty
     */
    @Test
    public void testSpatialQueryEmpty() throws SQLException, ParseException {
        List<GroceryStore> result = dbInterface.selectStore(searchFrame);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNoDuplicates() throws SQLException, ParseException {
        dbInterface.insertAll(testStoreName,testStoreNullName,testStoreName,testStoreNullName);

        List<GroceryStore> result = dbInterface.selectStore(searchFrame);
        assertEquals(2,result.size());
    }


    /**
     * test that a spatial query can correctly return more than one store
     */
    @Test
    public void testSpatialQueryMultiple() throws SQLException, ParseException {
        dbInterface.insertStore(testStoreName);
        dbInterface.insertStore(testStoreNullName);

        List<GroceryStore> result = dbInterface.selectStore(searchFrame);

        assertEquals(2, result.size());
    }


    /**
     * Test the same behavior as testSpatialQueryMultiple but also
     * test that insertAll behaves correctly.
     */
    @Test
    public void testInsertAll() throws SQLException, ParseException {
        dbInterface.insertAll(testStoreName, testStoreNullName);

        List<GroceryStore> result = dbInterface.selectStore(searchFrame);

        assertEquals(2, result.size());
    }

    /**
     * Test that a point within the searched buffer is correctly reported as
     * in the buffer.
     */
    @Test
    public void testInSearchedBuffer() throws SQLException {
        Point testPoint = geoFactory.createPoint(new Coordinate(0, 0));

        dbInterface.insertSearchedBuffer(testPoint,10);
        assertTrue(dbInterface.inSearchedBuffer(testPoint));
    }

    /**
     * Test a point is not erroneously reported as being in the searched
     * buffer.
     */
    @Test
    public void testNotInSearchedBuffer() throws SQLException {
        Point testPoint0 = geoFactory.createPoint(new Coordinate(0, 0));
        Point testPoint1 = geoFactory.createPoint(new Coordinate(100, 100));

        dbInterface.insertSearchedBuffer(testPoint0,10);
        assertFalse(dbInterface.inSearchedBuffer(testPoint1));
    }
}