package grocerystoresource;

import fooddesertserver.GroceryStore;
import org.locationtech.jts.geom.Coordinate;

import java.util.List;

public class GroceryStoreSourceExceptionImpl implements GroceryStoreSource {
    @Override
    public List<GroceryStore> nearbyQueryFor(Coordinate location, int radius) throws Exception {
        throw new Exception("Exception thrown intentionally for testing purposes.");
    }

    @Override
    public String getEpsg() {
        return "4326";
    }
}
