package database.network;

import org.locationtech.jts.geom.LineString;

import java.util.Objects;

public class Edge {
    private final int id;
    private final int node_from;
    private final int node_to;
    private final double length;

    private final LineString geometry;

    public Edge(int id, int node_from, int node_to, double length, LineString geometry) {
        this.id = id;
        this.node_from = node_from;
        this.node_to = node_to;
        this.length = length;
        this.geometry = geometry;
    }

    public int getNode_from() {
        return node_from;
    }

    public int getNode_to() {
        return node_to;
    }

    public double getLength() {
        return length;
    }

    public int getId() {
        return id;
    }

    public LineString getGeometry(){
        return geometry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge)) return false;
        Edge edge = (Edge) o;
        return id == edge.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
