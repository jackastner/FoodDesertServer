package fooddesertserver;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;

import com.google.maps.errors.ApiException;

import fooddesertdatabase.FoodDesertDatabase;
import googleplacesclient.GooglePlacesClient;

/**
 * @author john
 * This class combines the operations implemented by the FoodDesertDatabase and GooglePlacesClient classes to
 * implement the main functionalities of the FoodDesertServer. These include determining if a given point is in a food
 * desert, finding all grocery stores in an area and, determining the closest store to a point.
 */
public class FoodDesertQueryHandler {

    private final FoodDesertDatabase foodDb;
    private final GooglePlacesClient placesClient;

    public FoodDesertQueryHandler(FoodDesertDatabase foodDb, GooglePlacesClient placesClient) {
        this.foodDb = foodDb;
        this.placesClient = placesClient;
    }

    /**
     * Test a point to see if it is in a food desert. This function queries both
     * the local database and the google places API. New data obtained from the places
     * API is added to the database.
     */
    public boolean isInFoodDesert(Point p) throws SQLException, ParseException, ApiException, InterruptedException, IOException {
        if(!foodDb.inSearchedBuffer(p)) {
            /*call to places API and update database*/
            double bufferRadius = getBufferRadiusMeters(p);
            List<GroceryStore> stores = placesClient.nearbyQueryFor(p, (int) bufferRadius);
            foodDb.insertAll(stores);
            foodDb.insertSearchedBuffer(p, getBufferRadiusDegress(p));
        }
        return isInFoodDesertUnchecked(p);
    }

    /**
     * Test if a point is in a food desert according to the bufferRadius for that point.
     * This function assumes that data has been collected for that point so, it does
     * not check the search buffer or make a call to the Places API.
     */
    private boolean isInFoodDesertUnchecked(Point p) throws SQLException, ParseException {
        double bufferRadius = getBufferRadiusDegress(p);
        Geometry buffer = p.buffer(bufferRadius);
        List<GroceryStore> stores = foodDb.selectStore(buffer);
        return stores.isEmpty();
    }

    /**
     * Generate a buffer radius around a point that represents the area in which
     * there must be a grocery store for the point to not be in a food
     * desert.
     *
     * The current implementation returns a constant size buffer of 1 mile but,
     * future implementations should generate it dynamically based on location.
     *
     * getBufferRadiusMeters returns this radius in meters so, it should be used
     * to when talking to the Place API or other code that uses Web Mercator
     */
    private double getBufferRadiusMeters(Point p) {
        final double METERS_IN_MILE = 1609.34;
        return METERS_IN_MILE;
    }

    /**
     * Generate a buffer radius around a point that represents the area in which
     * there must be a grocery store for the point to not be in a food
     * desert.
     *
     * The current implementation returns a constant size buffer of 1 mile but,
     * future implementations should generate it dynamically based on location.
     *
     * getBufferRadiusDegres returns this radius in decimal degrees so, it should be
     * used when talking to code such as the SpatiaLite Database that uses WGS84.
     */
    private double getBufferRadiusDegress(Point p) {
        double radiusMeters = getBufferRadiusMeters(p);
        final double DEGREES_IN_METER = 1.0/111_111.0;
        return radiusMeters * DEGREES_IN_METER;
    }
}