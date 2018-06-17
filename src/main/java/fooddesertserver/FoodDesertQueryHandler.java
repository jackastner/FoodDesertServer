package fooddesertserver;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import grocerystoresource.GroceryStoreSource;
import org.locationtech.jts.geom.*;
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
        /* This implementation should now be optimal for a fixed query radius because it uses a hexagonal tiling of
         * circles. This produces minimal overlap between the query areas. Further improvement could come from dynamically
         * increasing the size of query circles.
         *
         * The tiling is generated starting at (minx,miny) of the unsearched bounding rectangle. The center of a hexagon
         * is placed at this corner. Take a line through this point with dx=radius * 3/2 and dy=radius * sqrt(3)/2. The
         * center of a hexagon is placed for all integers i such that (i*dx, i*dy) is inside the bounding rectangle.
         * Finally, for each point placed on the line, place points on a vertical line through that point such that the
         * points are within the bounding rectangle and adjacent points are separated by radius*sqrt(3) units */

        Geometry unsearchedBuffer = foodDb.selectUnsearchedBuffer(searchFrame);
        double radius = getBufferRadiusDegrees(unsearchedBuffer.getCoordinate());
        Envelope boundingRect = unsearchedBuffer.getEnvelopeInternal();

        int i = 0;
        double x,y;
        do{
            x = boundingRect.getMinX() + radius * i * 1.5;
            y = boundingRect.getMinY() + radius * Math.sqrt(3) * i / 2.0;
            double yPrime;
            int j = -i/2;
            do {
                yPrime = y + (radius * Math.sqrt(3) * j);
                Point queryPoint = geoFactory.createPoint(new Coordinate(x,yPrime));
                Geometry buffer = queryPoint.buffer(getBufferRadiusDegrees(queryPoint.getCoordinate()));
                if(buffer.getEnvelope().intersects(unsearchedBuffer)){
                    insertPlacesQuery(queryPoint.getCoordinate());
                }
                j++;
            } while (yPrime <= boundingRect.getMaxY());
            i++;
        } while(boundingRect.contains(x,y));

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