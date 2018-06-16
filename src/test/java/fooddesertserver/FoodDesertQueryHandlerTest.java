package fooddesertserver;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.ParseException;

import com.google.maps.errors.ApiException;

import fooddesertdatabase.FoodDesertDatabase;
import googleplacesclient.GooglePlacesClient;

public class FoodDesertQueryHandlerTest {

    private static FoodDesertDatabase dbInterface;
    private static String googleApiKey;

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

    @BeforeClass
    public static void getApiKey() throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("google_api_key")) {
            props.load(in);
        }
        googleApiKey = props.getProperty("google_api_key");
    }

    @AfterClass
    public static void closeDB() throws SQLException {
        dbInterface.close();
    }

    @Before
    public void setupPlaceClient() throws SQLException {
        dbInterface.truncate();

        GooglePlacesClient placeClient = new GooglePlacesClient(googleApiKey);
        queryHandler = new FoodDesertQueryHandler(dbInterface, placeClient);

        /*construct points as (lng,lat)*/
        collegePark = new Coordinate(-76.927, 38.996);
        nullPoint = new Coordinate(0, 0);
    }

    /**
     * Test that a point where there is a grocery store is correctly identified as not in a food desert.
     */
    @Test
    public void testNotInFoodDesert() throws SQLException, ParseException, ApiException, InterruptedException, IOException {
        /* Ask about college park. This could be true or false depending on what the place API returns
         * but, it will mark the area around college park as searched. */
        queryHandler.isInFoodDesert(collegePark);

        /* Add a fake store in college park so that the next query must return true */
        dbInterface.insertStore(new GroceryStore("test", collegePark));

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

}
