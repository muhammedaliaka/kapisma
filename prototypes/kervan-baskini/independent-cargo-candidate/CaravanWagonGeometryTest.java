package tr.kapisma.arena;
import tr.kapisma.arena.MatchRoster.Position;
import java.util.*;
public final class CaravanWagonGeometryTest {
    private static int n;
    private static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);n++;System.out.println("PASS "+s);}
    private static boolean eq(Position a,Position b){return a.distanceSquared(b)<1e-12;}
    public static void main(String[] args){
        var p=new Position(10,20,30);
        check(eq(CaravanWagonGeometry.seat(p,0),new Position(9.25,20.05,30)),"east seat");
        check(eq(CaravanWagonGeometry.seat(p,Math.PI/2),new Position(10,20.05,29.25)),"south seat");
        check(eq(CaravanWagonGeometry.hit(p,-Math.PI/2,true),new Position(10,20.68,29.15)),"north cargo");
        check(eq(CaravanWagonGeometry.hit(p,Math.PI,false),new Position(10.85,20,30)),"west hit");
        check(Math.abs(CaravanWagonGeometry.heading(p,new Position(11,20,31))-Math.PI/4)<1e-12,"diagonal heading");
        check(CaravanWagonGeometry.body().size()==14,"bounded 14-piece body");
        var pieces=new ArrayList<>(CaravanWagonGeometry.body());pieces.add(CaravanWagonGeometry.CARGO);
        int corners=0;
        for(var g:pieces)for(double dx:new double[]{0,g.sx()})for(double dz:new double[]{0,g.sz()})for(int deg=0;deg<360;deg++){
            var q=CaravanWagonGeometry.offset(p,Math.toRadians(deg),g.x()+dx,g.y()+g.sy(),g.z()+dz);
            if(Math.abs(q.x()-p.x())>CaravanWagonGeometry.RADIUS||Math.abs(q.z()-p.z())>CaravanWagonGeometry.RADIUS||
                q.y()-p.y()>CaravanWagonGeometry.HEIGHT||g.y()<0)throw new AssertionError("geometry escapes swept envelope "+g);
            corners++;
        }
        check(corners==21600,"all model corners within clearance for 360 headings");
        check(CaravanWagonGeometry.body().stream().filter(g->g.material().equals("wheel")).count()==4,"four wheels");
        System.out.println("CARAVAN_WAGON "+n+"/"+n+" PASS");
    }
}
