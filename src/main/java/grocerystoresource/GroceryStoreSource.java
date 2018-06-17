package grocerystoresource;

import com.google.maps.errors.ApiException;
import fooddesertserver.GroceryStore;
import org.locationtech.jts.geom.Coordinate;

import java.io.IOException;
import java.util.List;

/**
 * This class represents an external source for grocery store data. The FoodDesertQueryHandler class relies a
 * concrete instance of this interface to locate grocery stores in areas that are not marked as searched in the
 * database.
 */
public interface GroceryStoreSource {
    /**
     * Look for grocery stores in an area defined by a center and a radius.
     *
     * @param location The center of the query area.
     * @param radius Radius in meters around the query point to search for grocery stores.
     * @return A list of grocery stores found in that area.
     */
    List<GroceryStore> nearbyQueryFor(Coordinate location, int radius)
            throws ApiException, InterruptedException, IOException;
}
