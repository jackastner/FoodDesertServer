package database.network;

public class Edge {
    private final int id;
    private final int node_from;
    private final int node_to;
    private final double length;

    public Edge(int id, int node_from, int node_to, double length) {
        this.id = id;
        this.node_from = node_from;
        this.node_to = node_to;
        this.length = length;
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
}
