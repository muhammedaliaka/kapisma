package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
/** Shared local wagon coordinates, +X forward. Clearance encloses all rotated corners and rider. */
public final class CaravanWagonGeometry {
    public static final double RADIUS=1.85, HEIGHT=3.4;
    public record Piece(String material,double x,double y,double z,double sx,double sy,double sz){}
    private static final List<Piece> BODY=List.of(
        new Piece("oak",-1.45,.42,-.85,2.9,.26,1.7),
        new Piece("dark",-1.35,.68,-.85,2.7,.32,.15),
        new Piece("dark",-1.35,.68,.70,2.7,.32,.15),
        new Piece("dark",-1.4,.68,-.70,.18,.38,1.4),
        new Piece("oak",-.98,.68,-.55,.48,.20,1.1),
        new Piece("dark",1.27,.68,-.70,.18,.38,1.4),
        new Piece("wheel",-1.35,.02,-.99,.55,.66,.26),
        new Piece("wheel",.80,.02,-.99,.55,.66,.26),
        new Piece("wheel",-1.35,.02,.73,.55,.66,.26),
        new Piece("wheel",.80,.02,.73,.55,.66,.26),
        new Piece("hub",-1.18,.22,-1.02,.21,.24,.05),
        new Piece("hub",.97,.22,-1.02,.21,.24,.05),
        new Piece("hub",-1.18,.22,.97,.21,.24,.05),
        new Piece("hub",.97,.22,.97,.21,.24,.05)
    );
    public static final Piece CARGO=new Piece("barrel",.35,.68,-.5,1,1,1);
    private CaravanWagonGeometry(){}
    public static List<Piece> body(){return BODY;}
    public static double heading(Position from,Position to){
        double dx=to.x()-from.x(),dz=to.z()-from.z();
        return Math.hypot(dx,dz)>1e-9?Math.atan2(dz,dx):0;
    }
    public static Position offset(Position p,double heading,double x,double y,double z){
        double c=Math.cos(heading),s=Math.sin(heading);
        return new Position(p.x()+x*c-z*s,p.y()+y,p.z()+x*s+z*c);
    }
    public static Position seat(Position p,double heading){return offset(p,heading,-.75,.05,0);}
    public static Position hit(Position p,double heading,boolean cargo){return offset(p,heading,cargo?.85:-.85,cargo?.68:0,0);}
}
