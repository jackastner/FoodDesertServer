package fooddesertserver;

import org.locationtech.jts.geom.Point;

/**
 * @author john
 *
 *         The GroceryStore class represents a grocery store found by the places
 *         API and stored in the food desert database.
 *
 *         This class is immutable.
 */
public class GroceryStore {

    // -1 if not yet in database
    private final int id;

    // null if no name is available
    private final String name;

    // should not be null
    private final Point location;

    /**
     * @param id
     *            Id in the food desert database of this store. If no id is known,
     *            the the constructor without this argument should be used.
     * @param name
     *            The name of the store. This value may be null if the store does
     *            not have a known name.
     * @param location
     *            The coordinates of this store. This value cannot be null and an
     *            IllegalArgumentException is thrown if it is.
     */
    public GroceryStore(int id, String name, Point location) {
        if (location == null) {
            throw new IllegalArgumentException("GroceryStore cannot be constructed with a null location!");
        }
        this.id = id;
        this.name = name;
        this.location = location;
    }

    /**
     * This constructor is identical to the previous but, the id of this store is
     * initialized to a default value (-1).
     */
    public GroceryStore(String name, Point location) {
        this(-1, name, location);
    }

    /**
     * @return the id of this grocery store in the food desert database or -1 if
     *         this store does not exist in the database. This method return -1 iff
     *         hasId returns false.
     */
    public int getId() {
        return id;
    }

    /**
     * @return A boolean value indicating whether this store has an id. A store will
     *         not have an idea if it has not been inserted into the database. this
     *         function returns false iff getId returns -1.
     */
    public boolean hasId() {
        return id != -1;
    }

    /**
     * @return The name of this grocery store. This can be null if the store has no
     *         known name. This is null iff hasName return false.
     */
    public String getName() {
        return name;
    }

    /**
     * @return A boolean value indicating whether this store has a known name. This
     *         is false iff getName returns null.
     */
    public boolean hasName() {
        return name != null;
    }

    /**
     * @return The coordinates of this store. This will never be null because a
     *         store with unknown coordinates is not meaningful in the food desert
     *         database. This property is ensured in the constructor.
     */
    public Point getLocation() {
        return location;
    }

    /* Construct and return a new GroceryStore identical to this one but with the
     * specified Id. This method should be called after a store is inserted into a
     * database for the first time.
     *
     * This method should only be called once and only on objects not instantiated
     * with an id. Calling this method when id is already set will cause an illegal
     * state exception.
     *
     * @param id The id used to construct the new GroceryStore
     *
     * @return A new GroceryStore object with the specified id */
    public GroceryStore setId(int id) {
        if (this.id != -1) {
            throw new IllegalStateException("Grocery store Id should be set only once!");
        }

        return new GroceryStore(id, this.name, this.location);
    }

    @Override
    public String toString() {
        return String.format("GroceryStore: {id = %d, name = %s, location = %s}", id, name, location.toText());
    }
}
