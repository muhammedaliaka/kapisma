package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
/** Deterministic initial valley arena; route corridors and bases reserved before terrain. */
public final class CaravanTerrainLayout {
    public enum Cell { AIR, GRASS, ROAD, STONE, DIRT, ROCK, BASE, TUNNEL_ROOF, ESCORT_MARK, EXTRACTION_MARK, FINISH_MARK, SAFE_MARK, RISKY_MARK, ESCORT_POST, ESCORT_BEAM, ESCORT_CANOPY, ATTACK_POST, ATTACK_BEAM, ATTACK_CANOPY, TUNNEL_PORTAL, TUNNEL_KEYSTONE, ESCORT_SCREEN, ATTACK_SCREEN, SCENIC_TRUNK, SCENIC_LEAVES }
    public static final int HEIGHT=12, PLAY_X=36, PLAY_Z=24, SCENIC_X=43, SCENIC_Z=30;
    /** Visual perimeter only; never used for route, map, or cargo eligibility. */
    public static Cell renderCell(int x,int y,int z){
        if(y<0||y>=HEIGHT||Math.abs(x)>SCENIC_X||Math.abs(z)>SCENIC_Z)return Cell.AIR;
        if(Math.abs(x)<=PLAY_X&&Math.abs(z)<=PLAY_Z)return cell(x,y,z);
        int h=scenicHeight(x,z);
        if(y<=h)return y==h?Cell.GRASS:y>=h-2?Cell.DIRT:Cell.ROCK;
        // Short conifers on spaced lattice sites. Canopies never enter playable geometry.
        for(int cx=x-1;cx<=x+1;cx++)for(int cz=z-1;cz<=z+1;cz++){
            if(!treeSite(cx,cz))continue;
            int dy=y-scenicHeight(cx,cz);
            if(cx==x&&cz==z&&dy>=1&&dy<=3)return Cell.SCENIC_TRUNK;
            if(dy>=2&&dy<=4&&Math.abs(cx-x)+Math.abs(cz-z)<= (dy==4?0:1))return Cell.SCENIC_LEAVES;
        }
        return Cell.AIR;
    }
    private static double hashNoise(int x,int z){
        long v=0x9E3779B97F4A7C15L*x+0xC2B2AE3D27D4EB4FL*z+0x632BE59BD9B4E019L;
        v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;v=(v^(v>>>27))*0x94D049BB133111EBL;
        return ((v^(v>>>31))>>>11)*0x1.0p-53;
    }
    private static double noise(int x,int z,int span){
        int gx=Math.floorDiv(x,span),gz=Math.floorDiv(z,span);
        double u=Math.floorMod(x,span)/(double)span,v=Math.floorMod(z,span)/(double)span;
        u=u*u*(3-2*u);v=v*v*(3-2*v);
        return (1-v)*((1-u)*hashNoise(gx,gz)+u*hashNoise(gx+1,gz))+v*((1-u)*hashNoise(gx,gz+1)+u*hashNoise(gx+1,gz+1));
    }
    static int scenicHeight(int x,int z){
        int depth=Math.max(Math.abs(x)-PLAY_X,Math.abs(z)-PLAY_Z);
        double peak=4+7*noise(x,z,13)+3*noise(x+19,z-11,5);
        return Math.min(HEIGHT-1,Math.max(1,(int)Math.round(depth*peak/6.0)));
    }
    static boolean treeSite(int x,int z){
        if(Math.floorMod(x,5)!=2||Math.floorMod(z,5)!=2||Math.abs(x)>=SCENIC_X||Math.abs(z)>=SCENIC_Z)return false;
        int depth=Math.max(Math.abs(x)-PLAY_X,Math.abs(z)-PLAY_Z);
        if(depth<3||scenicHeight(x,z)>HEIGHT-5||hashNoise(x,z)<.27)return false;
        int h=scenicHeight(x,z);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(Math.abs(dx)+Math.abs(dz)==1&&scenicHeight(x+dx,z+dz)>h+1)return false;
        return true;
    }
    public static List<Position> safe(double y){return List.of(new Position(-23.5,y,.5),new Position(-15.5,y,.5),new Position(-15.5,y,-11.5),new Position(15.5,y,-11.5),new Position(15.5,y,.5),new Position(24.5,y,.5));}
    public static List<Position> risky(double y){return List.of(new Position(-23.5,y,.5),new Position(-15.5,y,.5),new Position(-6.5,y,4.5),new Position(8.5,y,4.5),new Position(15.5,y,.5),new Position(24.5,y,.5));}
    public static Position escortBase(double y){return new Position(-28.5,y,-8.5);}
    public static Position attackBase(double y){return new Position(.5,y,15.5);}
    private static final List<Position> SAFE=safe(0),RISKY=risky(0);
    private CaravanTerrainLayout(){}
    static double segmentDistanceSq(double x,double z,Position a,Position b){
        double dx=b.x()-a.x(),dz=b.z()-a.z(),f=((x-a.x())*dx+(z-a.z())*dz)/(dx*dx+dz*dz);
        f=Math.max(0,Math.min(1,f));double xx=x-a.x()-f*dx,zz=z-a.z()-f*dz;return xx*xx+zz*zz;
    }
    private static boolean corridor(double x,double z,List<Position> path,double radius){
        for(int i=1;i<path.size();i++)if(segmentDistanceSq(x,z,path.get(i-1),path.get(i))<=radius*radius)return true;
        return false;
    }
    public static boolean road(int x,int z){return corridor(x+.5,z+.5,SAFE,3.4)||corridor(x+.5,z+.5,RISKY,3.4);}
    public static boolean base(int x,int z){return nearBase(x,z,escortBase(0))||nearBase(x,z,attackBase(0));}
    private static boolean nearBase(int x,int z,Position p){return Math.abs(x+.5-p.x())<=5&&Math.abs(z+.5-p.z())<=5;}
    private static int hill(int x,int z,int cx,int cz,int radius,int height){
        double d=Math.hypot(x-cx,z-cz);return Math.max(0,(int)Math.ceil(height*(1-d/radius)));
    }
    /** Same open shelter geometry behind each role's base, facing the objective.
     * No chests, doors, collision traps or dynamic entities. */
    public static Cell landmark(int x,int y,int z,boolean escort){
        int cx=escort?-29:0,dx=x-cx;
        int depth=escort?-15-z:z-21;
        if(Math.abs(dx)>3||depth<0||depth>2||y<1||y>5)return Cell.AIR;
        if(Math.abs(dx)==3&&(depth==0||depth==2)&&y<=3)return escort?Cell.ESCORT_POST:Cell.ATTACK_POST;
        if(y==4)return escort?Cell.ESCORT_BEAM:Cell.ATTACK_BEAM;
        if(y==5&&Math.abs(dx)<=2)return escort?Cell.ESCORT_CANOPY:Cell.ATTACK_CANOPY;
        return Cell.AIR;
    }
    public static Cell cell(int x,int y,int z){
        if(y<0||y>=HEIGHT)return Cell.AIR;
        boolean road=road(x,z),base=base(x,z);
        if(y==0){
            // Match the public tactical map symbols with walkable, full-block floor markings.
            int ex=x-(int)Math.floor(escortBase(0).x()),ez=z-(int)Math.floor(escortBase(0).z());
            if(Math.max(Math.abs(ex),Math.abs(ez))==4)return Cell.ESCORT_MARK;
            int ax=x-(int)Math.floor(attackBase(0).x()),az=z-(int)Math.floor(attackBase(0).z());
            if(Math.abs(ax)+Math.abs(az)==3)return Cell.EXTRACTION_MARK;
            if(x==24&&Math.abs(z)<=2)return Cell.FINISH_MARK;
            // Two approach stripes beyond the common junction; not on the shared road.
            if(road&&x>=-17&&x<=-14&&z==-5)return Cell.SAFE_MARK;
            if(road&&x==-10&&z>=1&&z<=5)return Cell.RISKY_MARK;
            return base?Cell.BASE:road?Cell.ROAD:Cell.GRASS;
        }
        // Paired flank screens, chosen from measured visibility/egress alternatives.
        // Leave the three-block central gap and keep all spawn/horse footprints open.
        if(y<=2){
            int k=z+9;
            if(x==-24&&(Math.abs(k)>=2&&Math.abs(k)<=4))return Cell.ESCORT_SCREEN;
            if(z==10&&(Math.abs(x)>=2&&Math.abs(x)<=4))return Cell.ATTACK_SCREEN;
        }
        Cell shelter=landmark(x,y,z,true);if(shelter!=Cell.AIR)return shelter;
        shelter=landmark(x,y,z,false);if(shelter!=Cell.AIR)return shelter;
        // Masonry portals face east/west. The arch lintel remains above the swept wagon.
        if(Math.abs(x)==6&&Math.abs(z+12)<=4){
            int span=Math.abs(z+12);
            if(span==4&&y<=6)return Cell.TUNNEL_PORTAL;
            if(y==5||y==6)return span==0&&y==5?Cell.TUNNEL_KEYSTONE:Cell.TUNNEL_PORTAL;
        }
        // Tunnel spans the safe road, clear 4 blocks above the ground.
        boolean tunnel=Math.abs(x)<=5&&Math.abs(z+12)<=5;
        if(tunnel&&road)return y>=5&&y<=7?Cell.TUNNEL_ROOF:Cell.AIR;
        if(road||base)return Cell.AIR;
        int h=Math.max(hill(x,z,-4,-13,12,9),Math.max(hill(x,z,19,12,11,8),hill(x,z,-22,16,9,6)));
        if(tunnel)h=Math.max(h,7);
        if(y>h)return Cell.AIR;
        return y==h?Cell.GRASS:y>=h-2?Cell.DIRT:Cell.ROCK;
    }
}
