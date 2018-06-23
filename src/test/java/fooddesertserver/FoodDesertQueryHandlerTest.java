package fooddesertserver;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

import grocerystoresource.GroceryStoreSourceTestImpl;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;

import com.google.maps.errors.ApiException;

import fooddesertdatabase.FoodDesertDatabase;

public class FoodDesertQueryHandlerTest {

    private static FoodDesertDatabase dbInterface;

    private GroceryStoreSourceTestImpl storeSource;
    private FoodDesertQueryHandler queryHandler;
    private Coordinate collegePark;
    private Coordinate nullPoint;

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
    public void setupPlaceClient() throws SQLException {
        dbInterface.truncate();

        storeSource = new GroceryStoreSourceTestImpl();
        queryHandler = new FoodDesertQueryHandler(dbInterface, storeSource);

        /*construct points as (lng,lat)*/
        collegePark = new Coordinate(-76.927, 38.996);
        nullPoint = new Coordinate(0, 0);
    }

    /**
     * Test that a point where there is a grocery store is correctly identified as not in a food desert.
     */
    @Test
    public void testNotInFoodDesert() throws SQLException, ParseException, ApiException, InterruptedException, IOException {
        storeSource.returnStoreNext();
        assertFalse(queryHandler.isInFoodDesert(collegePark));
    }

    /**
     * Test that a point where there is not a grocery store is correctly identified as in a food desert.
     */
    @Test
    public void testInFoodDesert() throws SQLException, ParseException, ApiException, InterruptedException, IOException {
        /* This make the assumption that there are not grocery stores in the middle of the ocean.
         * While a fairly safe assumption, it should probably be verified if this test starts failing  */
        assertTrue(queryHandler.isInFoodDesert(nullPoint));
    }

    /**
     * Simple test for getAllGroceryStores. This only checks that at least 1 stores is returned.
     */
    @Test
    public void testGetAllGroceryStores() throws InterruptedException, SQLException, ApiException, ParseException, IOException {
        GeometryFactory geometryFactory = new GeometryFactory();
        Envelope searchEnv = new Envelope(-76.991844, -76.795807,38.916682, 39.020251);
        Geometry searchFrame = geometryFactory.toGeometry(searchEnv);
        //forces one store into the results

        List<GroceryStore> allStores = queryHandler.getAllGroceryStores(searchFrame);

        /* check that the all stores call made at least 1 call. */
        assertTrue(storeSource.getNumQueries() > 0);
    }
}
