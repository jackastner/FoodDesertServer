package grocerystoresource;

import fooddesertserver.GroceryStore;
import org.locationtech.jts.geom.Coordinate;

import java.util.Collections;
import java.util.List;

/**
 * Test implementation of the GroceryStoreSource interface that never returns any stores while keeping track
 * of the number of queries made.
 */
public class GroceryStoreSourceTestImpl implements GroceryStoreSource {
    private int numQueries;
    private boolean shouldReturnStore;

    public GroceryStoreSourceTestImpl(){
        numQueries = 0;
        shouldReturnStore = false;
    }

    /**
     * @param location The center of the query area.
     * @param radius Radius in meters around the query point to search for grocery stores.
     * @return An empty list.
     */
    @Override
    public List<GroceryStore> nearbyQueryFor(Coordinate location, int radius) {
        numQueries++;
        if(shouldReturnStore){
            shouldReturnStore = false;
            return Collections.singletonList(new GroceryStore("test", location));
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String getEpsg() {
        return "4326";
    }

    /**
     * This method is useful for verify behavior of a FoodDesertQueryHandler.
     * @return Number of queries made to this instance.
     */
    public int getNumQueries(){
        return numQueries;
    }

    /**
     * Set the number of queries count to 0. Also useful for tests.
     */
    public void resetNumQueries(){
        numQueries = 0;
    }

    /**
     * Make the next query return a store.
     */
    public void returnStoreNext(){
        shouldReturnStore = true;
    }
}
