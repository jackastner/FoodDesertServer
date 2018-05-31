package googleplaceclient;

import org.junit.*;

import com.google.maps.errors.ApiException;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;

import fooddesertserver.GroceryStore;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;

import googleplacesclient.GooglePlacesClient;

public class GooglePlacesClientTest {

    private static String googleApiKey;

    private GooglePlacesClient placeClient;
    private LatLng collegePark;
    private LatLng nullPoint;

    @BeforeClass
    public static void getApiKey() throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("google_api_key")) {
            props.load(in);
        }
        googleApiKey = props.getProperty("google_api_key");
    }

    @Before
    public void setupPlaceClient() {
        placeClient = new GooglePlacesClient(googleApiKey);
        collegePark = new LatLng(38.996, -76.927);
        nullPoint = new LatLng(0, 0);
    }

    /* Just test that a request can be made to the API without an exception being
     * thrown on empty results */
    @Test
    public void requestWithoutError() throws ApiException, InterruptedException, IOException {
        placeClient.nearbyQueryFor(collegePark, 1, PlaceType.GROCERY_OR_SUPERMARKET);
    }

    /* test that a zero radius request returns an empty result */
    @Test
    public void requestReturnsEmpty() throws ApiException, InterruptedException, IOException {
        List<GroceryStore> results = placeClient.nearbyQueryFor(nullPoint, 1, PlaceType.GROCERY_OR_SUPERMARKET);
        assertTrue(results.isEmpty());
    }

    /* Test that nearbyQuery does not return null even if result is empty */
    @Test
    public void requestReturnsNonNull() throws ApiException, InterruptedException, IOException {
        List<GroceryStore> results = placeClient.nearbyQueryFor(nullPoint, 1, PlaceType.GROCERY_OR_SUPERMARKET);
        assertNotNull(results);
    }

    /* Test that some results are returned for a query that should return results */
    @Test
    public void requestReturnsResults() throws ApiException, InterruptedException, IOException {
        List<GroceryStore> results = placeClient.nearbyQueryFor(collegePark, 10 * 1600, PlaceType.GROCERY_OR_SUPERMARKET);
        System.out.println(results);
        assertFalse(results.isEmpty());
    }

}
