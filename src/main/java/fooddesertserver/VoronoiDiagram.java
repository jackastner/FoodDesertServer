package fooddesertserver;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;


public class VoronoiDiagram {
    private final GeometryCollection diagram;

    public VoronoiDiagram(VoronoiDiagramBuilder builder, GeometryFactory factory){
        this.diagram = (GeometryCollection) builder.getDiagram(factory);
    }
}
