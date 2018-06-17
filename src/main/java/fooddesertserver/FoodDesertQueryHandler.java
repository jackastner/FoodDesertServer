package fooddesertserver;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import grocerystoresource.GroceryStoreSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;

import com.google.maps.errors.ApiException;

import fooddesertdatabase.FoodDesertDatabase;

/**
 * @author john
 * This class combines the operations implemented by the FoodDesertDatabase and GooglePlacesClient classes to
 * implement the main functionalities of the FoodDesertServer. These include determining if a given point is in a food
 * desert, finding all grocery stores in an area and, determining the closest store to a point.
 *
 * This class is thread safe. See FoodDesertDatabase and GooglePlacesClient for details.
 */
public class FoodDesertQueryHandler {

    private final FoodDesertDatabase foodDb;
    private final GroceryStoreSource placesClient;
    private final GeometryFactory geoFactory;

    public FoodDesertQueryHandler(FoodDesertDatabase foodDb, GroceryStoreSource placesClient) {
        this.foodDb = foodDb;
        this.placesClient = placesClient;
        this.geoFactory = new GeometryFactory();
    }

    /**
     * Test a point to see if it is in a food desert. This function queries both
     * the local database and the google places API. New data obtained from the places
     * API is added to the database.
     */
    public boolean isInFoodDesert(Coordinate p) throws SQLException, ParseException, ApiException, InterruptedException, IOException {
        if(!foodDb.inSearchedBuffer(p)) {
            insertPlacesQuery(p);
        }
        return isInFoodDesertUnchecked(p);
    }

    /**
     * Test if a point is in a food desert according to the bufferRadius for that point.
     * This function assumes that data has been collected for that point so, it does
     * not check the search buffer or make a call to the Places API.
     */
    private boolean isInFoodDesertUnchecked(Coordinate p) throws SQLException, ParseException {
        double bufferRadius = getBufferRadiusDegrees(p);
        Point coordPoint = geoFactory.createPoint(p);
        Geometry buffer = coordPoint.buffer(bufferRadius);
        List<GroceryStore> stores = foodDb.selectStore(buffer);
        return stores.isEmpty();
    }


    /**
     * Make a call to the Places API for a coordinate and insert the result into the database.
     * @param p Center of query.
     * @throws InterruptedException
     * @throws ApiException
     * @throws IOException
     */
    private void insertPlacesQuery(Coordinate p) throws InterruptedException, ApiException, IOException, SQLException {
            /*call to places API and update database*/
            double bufferRadius = getBufferRadiusMeters(p);
            List<GroceryStore> stores = placesClient.nearbyQueryFor(p, (int) bufferRadius);
            foodDb.insertAll(stores);
            foodDb.insertSearchedBuffer(p, getBufferRadiusDegrees(p));
    }

    /**
     * Returns all grocery stores in the search frame. Parts of the frame that have been searched are retrieved directly
     * from the database. For any part of the search frame that is not in the searched buffer, this method makes a call
     * to the Places API to retrieve information on that location. This is then used to update the database.
     *
     * @param searchFrame Area being searched for stores.
     * @return All stores within search frame.
     */
    public List<GroceryStore> getAllGroceryStores(Geometry searchFrame) throws SQLException, ParseException, InterruptedException, ApiException, IOException {
        /* This implementation is not close to optimal. It is likely that many more requests are made to the places API
         * than are required. This can be improved by better choice of query coordinates and by increasing the size of
         * each search buffer. The problem with increasing search buffer size is that a too large buffer will cause the
         * places API to hit its upper limit (60) and not return all stores in the area. Because of this, buffer radius
         * should be dynamic. */
        Geometry unsearchedBuffer = foodDb.selectUnsearchedBuffer(searchFrame);

        /* Repeat until unsearched area is empty.
         * The semantics of .isEmpty() and .getArea() == 0 seem to be different.
         * (i.e a line is non-empty but has area == 0.) */
        while(unsearchedBuffer.getArea() != 0){
            /*pick a vertex of the unsearched area*/
            Coordinate vertex = unsearchedBuffer.getCoordinate();

            /*do a query at that point*/
            insertPlacesQuery(vertex);

            /* subtract searched area from unsearched buffer*/
            Geometry buffer = geoFactory.createPoint(vertex).buffer(getBufferRadiusDegrees(vertex));
            unsearchedBuffer = unsearchedBuffer.difference(buffer);
        }

        return foodDb.selectStore(searchFrame);
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
    public double getBufferRadiusMeters(Coordinate p) {
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
     * getBufferRadiusDegrees this radius in decimal degrees so, it should be
     * used when talking to code such as the SpatiaLite Database that uses WGS84.
     */
    private double getBufferRadiusDegrees(Coordinate p) {
        double radiusMeters = getBufferRadiusMeters(p);
        /*this is a fairly rough estimate, see: https://gis.stackexchange.com/a/2964/85520*/
        final double DEGREES_IN_METER = 1.0/111_111.0;
        return radiusMeters * DEGREES_IN_METER;
    }
}