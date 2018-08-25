package database.network;

import org.locationtech.jts.geom.Coordinate;

/**
 * This class represents an entry into the *_nodes table in a Spatialite database generated from OSM data.
 */
public class Node {
    private final int id;
    private final int cardinality;
    private final Coordinate geometry;

    private double distance;

    Node(int id, int cardinality, Coordinate geometry){
        this.id = id;
        this.cardinality = cardinality;
        this.geometry = new Coordinate(geometry);

        distance = Double.POSITIVE_INFINITY;
    }

    public Coordinate getGeometry() {
        /* Creating new Coordinate object to maintain immutability of Node class. */
        return new Coordinate(geometry);
    }

    public int getCardinality() {
        return cardinality;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Node &&
               ((Node) o).id == this.id;
    }

    @Override
    public int hashCode(){
        return id;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    @Override
    public String toString() {
        return "Node{" +
                "id=" + id +
                ", cardinality=" + cardinality +
                ", geometry=" + geometry +
                ", distance=" + distance +
                '}';
    }
}
