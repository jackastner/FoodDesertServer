package database.network;

import database.SpatialiteDatabase; import fooddesertserver.FoodDesertQueryHandler;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NetworkDatabase extends SpatialiteDatabase {
    private static final Logger logger = LoggerFactory.getLogger(FoodDesertQueryHandler.class);

    private static final String NODE_TABLE = "network_nodes";
    private static final String NODE_ID = "node_id";
    private static final String CARDINALITY = "cardinality";
    private static final String NODE_GEOMETRY = "geometry";

    private static final String EDGE_TABLE = "network";
    private static final String EDGE_ID = "id";
    private static final String NODE_FROM = "node_from";
    private static final String NODE_TO = "node_to";
    private static final String LENGTH = "length";
    private static final String EDGE_GEOMETRY = "geometry";


    /**
     * Opens a connection for the Sqlite database in dbFile then loads the required extensions for Spatialite.
     *
     * @param dbFile
     */
    public NetworkDatabase(String dbFile) throws SQLException {
        super(dbFile);
    }

    private static Node readResultNode(ResultSet result) throws SQLException, ParseException {
       int nodeId = result.getInt(1);
       int cardinality = result.getInt(2);
       String pointWKT = result.getString(3);

       Coordinate geometry = geomReader.get().read(pointWKT).getCoordinate();
       return new Node(nodeId, cardinality, geometry);
    }

    public Node getNearestNode(Coordinate coordinate, double max_dist) throws SQLException, ParseException {
       String sql =
               "SELECT " + NODE_ID + ", " + CARDINALITY + ", AsText(" + NODE_GEOMETRY + "), Min(Distance(" + NODE_GEOMETRY + ", GeomFromText(?))) " +
               "FROM " + NODE_TABLE + " " +
               "WHERE " + NODE_ID + " IN (" +
                    spatialIndexSubQuery(NODE_TABLE) + ");";

       Point coordPoint = geoFactory.createPoint(coordinate);

       Geometry searchFrame = coordPoint.buffer(max_dist);

       return queryWithResult(sql, NetworkDatabase::readResultNode, coordPoint.toText(), searchFrame.toText());
    }

    public Node getNode(int nodeId) throws SQLException, ParseException {
        String sql =
                "SELECT " + NODE_ID + ", " + CARDINALITY + ", AsText(" + NODE_GEOMETRY + ") " +
                "FROM " + NODE_TABLE + " " +
                "WHERE " + NODE_ID + "=?";
        return queryWithResult(sql, NetworkDatabase::readResultNode, String.valueOf(nodeId));
    }

    public List<Edge> getEdges(Node node) throws SQLException, ParseException {
        String sql =
                "SELECT " + EDGE_ID + ", " + NODE_FROM + ", " + NODE_TO + ", " + LENGTH + ", AsText(" + EDGE_GEOMETRY + ") " +
                "FROM " + EDGE_TABLE + " " +
                "WHERE " + NODE_FROM + "=? OR " + NODE_TO + "=?";

        List<Edge> resultList = new ArrayList<>(node.getCardinality());

        ResultProcessor<List<Edge>> processor = result -> {
            do{
                int edgeId = result.getInt(1);
                int nodeFrom  = result.getInt(2);
                int nodeTo = result.getInt(3);
                double length = result.getDouble(4);
                String edgeWKT = result.getString(5);
                LineString edgeGeom = (LineString) geomReader.get().read(edgeWKT);

                resultList.add(new Edge(edgeId, nodeFrom, nodeTo, length, edgeGeom));
            }while(result.next());

            return resultList;
        };
        return queryWithResult(sql, processor, String.valueOf(node.getId()), String.valueOf(node.getId()));
    }
}
