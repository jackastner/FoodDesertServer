package fooddesertserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

import java.lang.reflect.Type;


/**
 * Type alias to GeometryCollection that provides JsonSerialization specific to VoronoiDiagrams.
 */
public class VoronoiDiagram {
    private final GeometryCollection diagram;

    public VoronoiDiagram(VoronoiDiagramBuilder builder, GeometryFactory factory){
        this.diagram = (GeometryCollection) builder.getDiagram(factory);
    }

    public static class JsonSerializer implements com.google.gson.JsonSerializer<VoronoiDiagram> {
        @Override
        public JsonElement serialize(VoronoiDiagram src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray diagram = new JsonArray();

            for (int i = 0; i < src.diagram.getNumGeometries(); i++) {
                /* Polygons in a Voronoi Diagram should not have interior rings*/
                Polygon poly = (Polygon) src.diagram.getGeometryN(i);

                LineString ext = poly.getExteriorRing();


                JsonArray vertices = new JsonArray();
                for(Coordinate coord : ext.getCoordinates()) {
                    JsonObject vertex = new JsonObject();
                    vertex.add("lat", context.serialize(coord.y));
                    vertex.add("lng", context.serialize(coord.x));
                    vertices.add(vertex);
                }

                diagram.add(vertices);
            }

            return diagram;
        }


    }
}
