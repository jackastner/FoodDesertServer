package fooddesertserver;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.util.GeometryTransformer;

import java.util.function.Function;

/**
 * This class is used to apply a transformation to every point in a JTS geometry.
 * In FoodDesertQueryHandler, it is used to project geometry from source coordinate systems
 * to database coordinate systems.
 */
class PointTransformer extends GeometryTransformer {
    private final Function<Coordinate, Coordinate> transform;

    /**
     * @param transform The transformation that will be applied to each point. Do not use this to mutate original points.
     *                  Return the new points instead.
     */
    PointTransformer(Function<Coordinate, Coordinate> transform){
       this.transform = transform;
    }

    @Override
    protected CoordinateSequence transformCoordinates(CoordinateSequence coords, Geometry parent){
        Coordinate[] newCoords = new Coordinate[coords.size()];
        for(int i = 0; i < coords.size(); i++){
            newCoords[i] = transform.apply(coords.getCoordinate(i));
        }
        return new CoordinateArraySequence(newCoords);
    }
}
