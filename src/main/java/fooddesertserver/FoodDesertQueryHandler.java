package fooddesertserver;

import fooddesertdatabase.FoodDesertDatabase;
import grocerystoresource.GroceryStoreSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.osgeo.proj4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author john
 * This class combines the operations implemented by the FoodDesertDatabase and GooglePlacesClient classes to
 * implement the main functionalities of the FoodDesertServer. These include determining if a given point is in a food
 * desert, finding all grocery stores in an area and, determining the closest store to a point.
 *
 * This class is thread safe. See FoodDesertDatabase and GooglePlacesClient for details.
 */
public class FoodDesertQueryHandler {

    private final Logger logger = LoggerFactory.getLogger(FoodDesertQueryHandler.class);

    /* Input and output coordinates for Proj4j can be reused but, each thread requires its own copy. */
    private static final ThreadLocal<ProjCoordinate> projInput  = ThreadLocal.withInitial(ProjCoordinate::new),
                                                     projOutput = ThreadLocal.withInitial(ProjCoordinate::new);

    /*Used when generating buffers. It is important that this number is divisible by 3 because the total number of segments
     * (4*quadrant segments) must be divisible by 5 for generated buffers to work with a hexagonal tiling.*/
    private static final int BUFFER_QUADRANT_SEGMENTS = 9;

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
    public boolean isInFoodDesert(Coordinate p) throws SQLException, ParseException{
        Coordinate dbCoord = projSrcToDb(p);
        if(!foodDb.inSearchedBuffer(dbCoord)) {
            insertAllPlacesQueries(p);

            Point coordPoint = geoFactory.createPoint(projSrcToDb(p));
            double bufferRadius = getBufferRadiusMeters(p);
            Polygon buffer = (Polygon) coordPoint.buffer(bufferRadius);
            foodDb.insertSearchedBuffer(buffer);
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
     * Make a call to the Places API for each coordinate in argument collection and insert the results into the database.
     *
     * This method does not update the searched buffer so, the caller must do this themselves.
     * @param ps Centers of queries
     */
    private void insertAllPlacesQueries(Collection<Coordinate> ps) throws SQLException {
        Collection<GroceryStore> allStores = new ArrayList<>();

        /* Loop through all coordinates and make queries before database insert.
         * This lets all results be inserted in a single database transaction */
        for(Coordinate p : ps){
            int bufferRadius = (int) getBufferRadiusMeters(p);

            try {
                List<GroceryStore> stores = placesClient.nearbyQueryFor(p, bufferRadius)
                                        .stream()
                                        .map(s -> s.transform(this::projSrcToDb))
                                        .collect(Collectors.toList());
                allStores.addAll(stores);
            } catch (Exception e){
                logger.error("Exception while querying GroceryStoreSource. Ignoring and treating response as empty.", e);
            }

        }

        foodDb.insertAll(allStores);
    }

    /**
     * Utility to apply varargs syntax to insertAllPlacesQueries.
     *
     * This method does not update the searched buffer so, the caller must do this themselves.
     * @param ps Centers of queries.
     */
    private void insertAllPlacesQueries(Coordinate... ps) throws SQLException {
        insertAllPlacesQueries(Arrays.asList(ps));
    }


    /**
     * Returns all grocery stores in the search frame. Parts of the frame that have been searched are retrieved directly
     * from the database. For any part of the search frame that is not in the searched buffer, this method makes a call
     * to the Places API to retrieve information on that location. This is then used to update the database.
     *
     * @param searchFrame Area being searched for stores.
     * @return All stores within search frame.
     */
    public List<GroceryStore> getAllGroceryStores(Geometry searchFrame) throws SQLException, ParseException {
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

        /* This loop collects coordinates to query rather than actualy making the queries. */
        Collection<Coordinate> queryCoordinates = new ArrayList<>();
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

                /* Buffer.intersects(unsearchedBuffer) was returning true when I really shouldn't.
                 * This happens because of how circles are approximated by JTS. A circle generated by JTS is represented
                 * as an n-gon for some large enough n to look like a circle on the map.
                 *
                 * This is a problem because the default number of segments used per quadrant is 8 (total 32 quadrants).
                 * Since 32 is not divisible by 6, the the vertices of the tiled hexagons are not guaranteed to line up
                 * with vertices of the buffer. This caused some areas that should be marked as searched to not be covered.
                 *
                 * The solution was to change the number of segments used in the buffer to a number divisible by 6. */
                Geometry buffer = queryPoint.buffer(radius, BUFFER_QUADRANT_SEGMENTS);

                if(buffer.intersects(unsearchedBuffer)){
                    queryCoordinates.add(projDbToSrc(queryPoint.getCoordinate()));
                }
                j++;

            } while (yPrime <= boundingRect.getMaxY());
            i++;
        } while(boundingRect.contains(x,y));

        /* place query at each coordinate. */
        insertAllPlacesQueries(queryCoordinates);

        /* entire area that was unsearched has now been searched */
        if(unsearchedBuffer.isValid()){
            foodDb.insertSearchedBuffer(unsearchedBuffer);
        } else {
            throw new IllegalStateException("Unsearched buffer was invalid");
        }

        /*project data back to source projection before returning*/
        return foodDb.selectStore(projectedSearchFrame)
                     .stream()
                     .map(e -> e.transform(this::projDbToSrc))
                     .collect(Collectors.toList());
    }

    /**
     * A utility method overloading getAllGroceryStores(Geometry) to make call to the method with out a
     * geometry. This lets the server call to this function without needing to instantiate its own geometry
     * factory.
     * @param searchFrame Area being searched.
     * @return All stores withing the search frame.
     */
    public List<GroceryStore> getAllGroceryStore(Envelope searchFrame) throws  SQLException,  ParseException {
        return getAllGroceryStores(geoFactory.toGeometry(searchFrame));
    }


    public VoronoiDiagram getVoronoiDiagram(Geometry searchFrame) throws  SQLException,  ParseException {
        List<Coordinate>  diagramSites = this.getAllGroceryStores(searchFrame)
                                             .stream()
                                             .map(GroceryStore::getLocation)
                                             .collect(Collectors.toList());

        VoronoiDiagramBuilder diagramBuilder = new VoronoiDiagramBuilder();
        diagramBuilder.setSites(diagramSites);
        diagramBuilder.setClipEnvelope(searchFrame.getEnvelopeInternal());

        return new VoronoiDiagram(diagramBuilder, geoFactory);
    }

    public VoronoiDiagram getVoronoiDiagram(Envelope searchFrame) throws SQLException, ParseException{
        return getVoronoiDiagram(geoFactory.toGeometry(searchFrame));
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