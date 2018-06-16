package googleplacesclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import com.google.maps.GeoApiContext;
import com.google.maps.NearbySearchRequest;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;

import fooddesertserver.GroceryStore;



/**
 * @author john
 *        This class handles communication with the Google Places API mostly through
 *        calls to the com.google.maps.PlaceApi class which does the real work.
 *
 *        This class should be thread safe, but that is based on the assumption that
 *        org.locationtech.jts.geom.GeometryFactory is thread safe. I could not find
 *        documentation of this fact anywhere but, after reading the source code, I think it is thread safe.
 *        (https://github.com/locationtech/jts/blob/master/modules/core/src/main/java/org/locationtech/jts/geom/GeometryFactory.java)
 */
public class GooglePlacesClient {

    private final GeoApiContext context;

    /* Factory used to construct points for grocery stores
     * I *think* this class is thread safe so, I'm not wrapping it
     * in a ThreadLocal. If weird issues start showing up this
     * could be why. */
    private final GeometryFactory geoFactory;

    /* Since this class talks to the Google Places API, an API key is needed to
     * instantiate it. A key can be obtained from */
    public GooglePlacesClient(String googleApiKey) {
        context = new GeoApiContext.Builder().apiKey(googleApiKey).build();
        geoFactory = new GeometryFactory();
    }

    /**
     *The Places API requires a 2 second wait between a request and a subsequent
     * request for the next page. This means that a query can take multiple second
     * to complete so, this method should never be called on the main thread.
     *
     * @param location The center of the query area.
     * @param radius Radius in meters around the query point to search for grocery stores.
     * @return A list of up to 60 grocery stores found in that area.
     */
    public List<GroceryStore> nearbyQueryFor(Point location, int radius)
            throws ApiException, InterruptedException, IOException {
        // initialize to size 60 because the places API returns max 60 results
        List<GroceryStore> results = new ArrayList<>(60);

        // prepare initial query for Places API
        NearbySearchRequest request = PlacesApi.nearbySearchQuery(this.context, pointToLatLng(location));
        PlacesSearchResponse response = request.radius(radius).type(PlaceType.GROCERY_OR_SUPERMARKET).await();

        fillResultsList(response, results);

        return results;
    }

    /* This function gathers the results included in a Places response into a List
     * and, handles any additional pages of results by placing new calls to the
     * API */
    private void fillResultsList(PlacesSearchResponse response, List<GroceryStore> results)
            throws ApiException, InterruptedException, IOException {
        // Gather results of the query into the results List
        for (PlacesSearchResult result : response.results) {
            //name and location of the store are obtained from the API query
            Point storeLocation = latLngToPoint(result.geometry.location);
            String name = result.name;
            //the store is constructed without an id because it is not currently in a DB
            GroceryStore store = new GroceryStore(name, storeLocation);
            results.add(store);
        }

        // Gather results from the next page if there is one
        if (response.nextPageToken != null) {
            NearbySearchRequest searchRequest = PlacesApi.nearbySearchNextPage(context, response.nextPageToken);

            /* A 2 second interval is required between calls to the places API. This causes
             * large delays if called on the main thread */
            Thread.sleep(2000);
            PlacesSearchResponse pagingResponse = searchRequest.pageToken(response.nextPageToken).await();

            /* This call is recursive but, stack height should be limited to 3 because the
             * Places API only returns up to 3 pages of 20 results */
            fillResultsList(pagingResponse, results);
        }
    }

    /*Create a JTS point for a LatLng obtained from the Places API*/
    private Point latLngToPoint(LatLng latLng) {
        /*JTS expects coords at (lng,lat)*/
        return geoFactory.createPoint(new Coordinate(latLng.lng, latLng.lat));
    }

    /*Create a LatLng for use in the Place API from a JTS Point */
    private LatLng pointToLatLng(Point p) {
        return new LatLng(p.getY(), p.getX());
    }
}
