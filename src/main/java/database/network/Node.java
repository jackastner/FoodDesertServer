package database.network;

import org.locationtech.jts.geom.Coordinate;
import java.util.List;

/**
 * This class represents an entry into the *_nodes table in a Spatialite database generated from OSM data.
 */
public class Node {
    private final int id;
    private final int cardinality;
    private final Coordinate geometry;

    private List<Edge> edges;

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

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public List<Edge> getEdges(){
        return edges;
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
