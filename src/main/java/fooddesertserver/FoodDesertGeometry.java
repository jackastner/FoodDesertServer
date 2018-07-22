package fooddesertserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import java.lang.reflect.Type;

public class FoodDesertGeometry {
    private final double queriedArea, foodDesertArea;
    private final Geometry foodDesertGeometry;

    public FoodDesertGeometry(Geometry foodDesertGeometry, double foodDesertArea, double queriedArea){
        this.foodDesertGeometry = foodDesertGeometry;
        this.queriedArea = queriedArea;
        this.foodDesertArea = foodDesertArea;
    }

    public static class JsonSerializer implements com.google.gson.JsonSerializer<FoodDesertGeometry> {

        @Override
        public JsonElement serialize(FoodDesertGeometry src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject foodDesert = new JsonObject();

            foodDesert.add("total_area", context.serialize(src.queriedArea));
            foodDesert.add("desert_area", context.serialize(src.foodDesertArea));

            JsonArray desertGeom = new JsonArray();

            for (int i = 0; i < src.foodDesertGeometry.getNumGeometries(); i++) {
                /* Polygons in a Voronoi Diagram should not have interior rings*/
                Polygon poly = (Polygon) src.foodDesertGeometry.getGeometryN(i);

                JsonArray jsonPoly = new JsonArray();

                LineString ext = poly.getExteriorRing();
                jsonPoly.add(serializeRing(ext, context));

                for(int j=0; j < poly.getNumInteriorRing(); j++){
                    LineString interior = poly.getInteriorRingN(j);
                    jsonPoly.add(serializeRing(interior, context));
                }

                desertGeom.add(jsonPoly);
            }

            foodDesert.add("desert_geom", desertGeom);

            return foodDesert;
        }

        private JsonElement serializeRing(LineString ring, JsonSerializationContext context){
            JsonArray vertices = new JsonArray();
            for(Coordinate coord : ring.getCoordinates()) {
                JsonObject vertex = new JsonObject();
                vertex.add("lat", context.serialize(coord.y));
                vertex.add("lng", context.serialize(coord.x));
                vertices.add(vertex);
            }
            return vertices;
        }
    }
}
