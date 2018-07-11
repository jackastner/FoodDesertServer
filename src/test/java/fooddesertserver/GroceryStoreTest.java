package fooddesertserver;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

public class GroceryStoreTest {

    private Coordinate testPoint;

    @Before
    public void setup() {
        testPoint = new Coordinate(0, 0);
    }

    /**
     * Test that a GroceryStore cannot be constructed with a null location
     */
    @Test(expected = IllegalArgumentException.class)
    public void locationNotNull0() {
        new GroceryStore("name", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void locationNotNull1() {
        new GroceryStore(-1, "name", null);
    }

    /**
     * A store can be created without an id. In this case id should be initialized
     * to -1 and, hasId should return false.
     */
    @Test
    public void noId() {
        GroceryStore store = new GroceryStore("", testPoint);
        assertFalse(store.hasId());
        assertEquals(-1, store.getId());
    }

    /**
     * A store can be created with a null name if the store does not have a known
     * name. In this case, getName should return null and hasName should return
     * false.
     */
    @Test
    public void noName() {
        GroceryStore store = new GroceryStore(1, null, testPoint);
        assertFalse(store.hasName());
        assertNull(store.getName());
    }

    /**
     * A store constructed without an id can later be used to construct a similar
     * store with a specified id.
     */
    @Test
    public void setId() {
        GroceryStore withoutID = new GroceryStore("", testPoint);
        GroceryStore withId = withoutID.setId(1);

        assertFalse(withoutID.hasId());
        assertEquals(-1, withoutID.getId());

        assertTrue(withId.hasId());
        assertEquals(1, withId.getId());
    }

    /**
     * Once a grocery store has an id, it cannot be changed. attempting to do so
     * should result in an IllegalStateException.
     */
    @Test(expected = IllegalStateException.class)
    public void changeId() {
        GroceryStore withId = new GroceryStore(1, "test", testPoint);

        withId.setId(2);
    }

    @Test
    public void testEquals(){
        GroceryStore store0 = new GroceryStore(1, "test", testPoint);
        GroceryStore store1 = new GroceryStore(1, "test", testPoint);
        GroceryStore store2 = new GroceryStore("test", testPoint);

        assertEquals(store0, store0);
        assertEquals(store0, store1);

        assertNotEquals(store0, store2);
        assertNotEquals(store0, null);
        assertNotEquals(store0, new Object());
    }

    @Test
    public void testHashCode(){
        GroceryStore store0 = new GroceryStore(1, "test", testPoint);
        GroceryStore store1 = new GroceryStore(1, "test", testPoint);

        assertEquals(store0.hashCode(), store1.hashCode());
    }

}
