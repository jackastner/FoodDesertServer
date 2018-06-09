package googleplaceclient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import com.google.maps.errors.ApiException;

import fooddesertserver.GroceryStore;
import googleplacesclient.GooglePlacesClient;

public class GooglePlacesClientTest {

    private static String googleApiKey;

    private GooglePlacesClient placeClient;
    private Point collegePark;
    private Point nullPoint;

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
        GeometryFactory geoFact = new GeometryFactory();
        placeClient = new GooglePlacesClient(googleApiKey);

        /*construct points as (lng,lat)*/
        collegePark = geoFact.createPoint(new Coordinate(-76.927, 38.996));
        nullPoint = geoFact.createPoint(new Coordinate(0, 0));
    }

    /* Just test that a request can be made to the API without an exception being
     * thrown on empty results */
    @Test
    public void requestWithoutError() throws ApiException, InterruptedException, IOException {
        placeClient.nearbyQueryFor(collegePark, 1);
    }

    /* test that a zero radius request returns an empty result */
    @Test
    public void requestReturnsEmpty() throws ApiException, InterruptedException, IOException {
        List<GroceryStore> results = placeClient.nearbyQueryFor(nullPoint, 1);
        assertTrue(results.isEmpty());
    }

    /* Test that nearbyQuery does not return null even if result is empty */
    @Test
    public void requestReturnsNonNull() throws ApiException, InterruptedException, IOException {
        List<GroceryStore> results = placeClient.nearbyQueryFor(nullPoint, 1);
        assertNotNull(results);
    }

    /* Test that some results are returned for a query that should return results */
    @Test
    public void requestReturnsResults() throws ApiException, InterruptedException, IOException {
        List<GroceryStore> results = placeClient.nearbyQueryFor(collegePark, 10 * 1600);
        System.out.println(results);
        assertFalse(results.isEmpty());
    }

}
