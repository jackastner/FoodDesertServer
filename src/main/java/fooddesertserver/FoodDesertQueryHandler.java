package fooddesertserver;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.osgeo.proj4j.*;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;

import com.google.maps.errors.ApiException;

import grocerystoresource.GroceryStoreSource;
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

    /* Input and output coordinates for Proj4j can be reused but, each thread requires its own copy. */
    private static final ThreadLocal<ProjCoordinate> projInput  = ThreadLocal.withInitial(ProjCoordinate::new),
                                                     projOutput = ThreadLocal.withInitial(ProjCoordinate::new);

    private final FoodDesertDatabase foodDb;
    private final GroceryStoreSource placesClient;
    private final GeometryFactory geoFactory;

    private final CoordinateTransform dbToSrc, srcToDb;


    public FoodDesertQueryHandler(FoodDesertDatabase foodDb, GroceryStoreSource placesClient) {
        this.foodDb = foodDb;
        this.placesClient = placesClient;
        this.geoFactory = new GeometryFactory();

        /* Construct coordinate system transformations between the store source and
         * database. */

        CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
        CRSFactory csFactory = new CRSFactory();

        CoordinateReferenceSystem crsDb = csFactory.createFromName("EPSG:"+foodDb.getEpsg());
        CoordinateReferenceSystem crsSrc = csFactory.createFromName("EPSG:"+placesClient.getEpsg());

        dbToSrc = ctFactory.createTransform(crsDb, crsSrc);
        srcToDb = ctFactory.createTransform(crsSrc, crsDb);
    }


    /**
     * Test a point to see if it is in a food desert. This function queries both
     * the local database and the google places API. New data obtained from the places
     * API is added to the database.
     */
    public boolean isInFoodDesert(Coordinate p) throws SQLException, ParseException, ApiException, InterruptedException, IOException {
        Coordinate dbCoord = projSrcToDb(p);
        if(!foodDb.inSearchedBuffer(dbCoord)) {
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
        Point coordPoint = geoFactory.createPoint(projSrcToDb(p));
        double bufferRadius = getBufferRadiusMeters(p);
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
        double bufferRadius = getBufferRadiusMeters(p);

        /* collect grocery stores from source and project them into the database coordinates
         * system before inserting into the database. */
        List<GroceryStore> stores = placesClient.nearbyQueryFor(p, (int) bufferRadius)
                                                .stream()
                                                .map(s -> s.transform(this::projSrcToDb))
                                                .collect(Collectors.toList());
        foodDb.insertAll(stores);

        /* insert query point and query size into database (after projection).*/
        Coordinate dbCoord = projSrcToDb(p);
        foodDb.insertSearchedBuffer(dbCoord, bufferRadius);
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

        Geometry projectedSearchFrame = new PointTransformer(this::projSrcToDb).transform(searchFrame);
        Geometry unsearchedBuffer = foodDb.selectUnsearchedBuffer(projectedSearchFrame);

        double radius = getBufferRadiusMeters(unsearchedBuffer.getCoordinate());
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
                Geometry buffer = queryPoint.buffer(radius);

                /* Buffer.intersects(unsearchedBuffer) was returning true when I really shouldn't.
                 * My best guess is that this is caused by floating point rounding errors. The current hack
                 * to solve this is allowing a very small amount of overlap between search buffers and the
                 * unsearched buffer.
                 */
                if(buffer.intersection(unsearchedBuffer).getArea() > 1e-3){
                    insertPlacesQuery(projDbToSrc(queryPoint.getCoordinate()));
                }
                j++;
            } while (yPrime <= boundingRect.getMaxY());
            i++;
        } while(boundingRect.contains(x,y));

        return foodDb.selectStore(projectedSearchFrame);
    }

    /**
     * A utility method overloading getAllGroceryStores(Geometry) to make call to the method with out a
     * geometry. This lets the server call to this function without needing to instantiate its own geometry
     * factory.
     * @param searchFrame Area being searched.
     * @return All stores withing the search frame.
     */
    public List<GroceryStore> getAllGroceryStore(Envelope searchFrame) throws InterruptedException, SQLException, ApiException, ParseException, IOException {
        return getAllGroceryStores(geoFactory.toGeometry(searchFrame));
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

    /* These methods handel marshalling data between jts  coordinates and Proj4j coordinates so that
     * point projections can be done with minimal boiler plate. */

    private Coordinate projSrcToDb(Coordinate srcCoord){
        return projJts(srcCoord, srcToDb);
    }

    private Coordinate projDbToSrc(Coordinate dbCoord){
        return projJts(dbCoord, dbToSrc);
    }

    private static Coordinate projJts(Coordinate jtsCoord, CoordinateTransform proj){
        projInput.get().x = jtsCoord.x;
        projInput.get().y = jtsCoord.y;

        proj.transform(projInput.get(), projOutput.get());

        return new Coordinate(projOutput.get().x, projOutput.get().y);
    }
}