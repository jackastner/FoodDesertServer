package googleplacesclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.maps.GeoApiContext;
import com.google.maps.NearbySearchRequest;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;

public class GooglePlacesClient {

    private final GeoApiContext context;

    /* Since this class talks to the Google Places API, an API key is needed to
     * instantiate it. A key can be obtained from */
    public GooglePlacesClient(String googleApiKey) {
        context = new GeoApiContext.Builder().apiKey(googleApiKey).build();
    }

    /* The Places API requires a 2 second wait between a request and a subsequent
     * request for the next page. This means that a query can take multiple second
     * to complete so, this method should never be called on the main thread. */
    public List<LatLng> nearbyQueryFor(LatLng location, int radius, PlaceType type)
            throws ApiException, InterruptedException, IOException {
        // initialize to size 60 because the places API returns max 60 results
        List<LatLng> results = new ArrayList<>(60);

        // prepare initial query for Places API
        NearbySearchRequest request = PlacesApi.nearbySearchQuery(this.context, location);
        PlacesSearchResponse response = request.radius(radius).type(type).await();

        fillResultsList(response, results);

        return results;
    }

    /* This function gathers the results included in a Places response into a List
     * and, handles any additional pages of results by placing new calls to the
     * API */
    private void fillResultsList(PlacesSearchResponse response, List<LatLng> results)
            throws ApiException, InterruptedException, IOException {
        // Gather results of the query into the results List
        for (PlacesSearchResult result : response.results) {
            LatLng storeLocation = result.geometry.location;
            results.add(storeLocation);
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
}
