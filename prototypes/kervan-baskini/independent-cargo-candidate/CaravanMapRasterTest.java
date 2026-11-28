package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
import tr.kapisma.arena.CaravanRouteRules.Route;
import static tr.kapisma.arena.CaravanMapRaster.*;
public final class CaravanMapRasterTest {
    static int passed;
    static final Position S=new Position(-20,0,0), E=new Position(20,0,0), T=new Position(-20,0,10), U=new Position(20,0,10);
    static void check(boolean v,String label){if(!v)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static byte pixel(CaravanMapRaster r,byte[] a,Position p){var q=r.project(p).orElseThrow();return a[q.z()*SIZE+q.x()];}
    public static void main(String[] args){
        var r=new CaravanMapRaster(List.of(S,T,U,E),List.of(S,E));
        var w=new Position(0,0,10);
        var a=r.frame(Route.SAFE,w,null);
        check(a.length==16384,"vanilla map pixel count");
        check(pixel(r,a,S)==START&&pixel(r,a,E)==FINISH,"shared route endpoints");
        check(pixel(r,a,new Position(-10,0,10))==SELECTED,"selected safe route");
        check(pixel(r,a,new Position(0,0,0))==RISKY,"alternative risk route");
        check(pixel(r,a,w)==WAGON,"wagon marker");
        var b=r.frame(Route.RISKY,new Position(0,0,0),null);
        check(pixel(r,b,new Position(0,0,0))==WAGON,"wagon moves to actual position");
        check(pixel(r,b,w)==SAFE,"previous marker cleared");
        check(pixel(r,b,new Position(10,0,0))==SELECTED,"selected risky route");
        check(r.project(new Position(10000,0,10000)).isEmpty(),"off-map coordinates rejected");
        var self=new Position(3,0,5);
        var c=r.frame(Route.SAFE,w,self);
        check(pixel(r,c,self)==SELF,"optional self marker");
        check(pixel(r,a,self)==BACKGROUND,"public map contains no player marker");
        check(Arrays.equals(a,r.frame(Route.SAFE,w,null)),"deterministic frame");
        a[0]=99;check(r.frame(Route.SAFE,w,null)[0]==BACKGROUND,"no mutable shared frame");
        boolean bad=false;try{new CaravanMapRaster(List.of(S,S),List.of(S,S));}catch(IllegalArgumentException ex){bad=true;}
        check(bad,"degenerate projection denied");
        int[] terrain=new int[TERRAIN_SIZE*TERRAIN_SIZE];
        Arrays.fill(terrain,UNKNOWN_HEIGHT);
        var tactical=r.tactical(Route.SAFE,w,self,terrain);
        check(pixel(r,tactical,self)==SELF&&pixel(r,tactical,w)==WAGON,"personal tactical markers");
        check(tactical[8*SIZE+16]==GRID,"coordinate grid");
        check(tactical[4*SIZE+64]==START,"north indicator");
        check(tactical[9*SIZE+9]==BACKGROUND,"unknown terrain not invented");
        Arrays.fill(terrain,60);for(int z=0;z<TERRAIN_SIZE;z++)for(int x=12;x<TERRAIN_SIZE;x++)terrain[z*TERRAIN_SIZE+x]=70;
        tactical=r.tactical(Route.SAFE,w,self,terrain);
        check(tactical[9*SIZE+9]==TERRAIN_LOW&&tactical[9*SIZE+109]==TERRAIN_HIGH,"height band shading");
        check(tactical[9*SIZE+48]==CONTOUR,"real height band boundary");
        var sample=r.samplePosition(0,0);
        check(r.project(sample).orElseThrow().equals(new Pixel(2,2)),"terrain sample projection");
        var other=r.tactical(Route.SAFE,w,new Position(-3,0,5),terrain);
        check(pixel(r,other,self)!=SELF,"separate personalized raster");
        check(arenaColumn(-36,-24,36,24)&&arenaColumn(36,24,36,24),"inclusive arena edge columns");
        check(!arenaColumn(-37,0,36,24)&&!arenaColumn(37,0,36,24)&&!arenaColumn(0,-25,36,24)&&!arenaColumn(0,25,36,24),"outside world excluded");
        check(boundedSurface(301,300,311,y->{throw new AssertionError("heightmap fast path");})==301,"surface at arena floor");
        check(boundedSurface(312,300,311,y->{throw new AssertionError("heightmap fast path");})==312,"surface at top of slab");
        check(boundedSurface(300,300,311,y->true)==UNKNOWN_HEIGHT,"surface below arena unknown");
        check(boundedSurface(319,300,311,y->y==304)==305,"unrelated overhead blocks excluded");
        check(boundedSurface(319,300,311,y->false)==UNKNOWN_HEIGHT,"empty arena column unknown");
        check(elevationBand(301,301)==TERRAIN_LOW&&elevationBand(302,301)==TERRAIN_LOW&&elevationBand(303,301)==TERRAIN_MID&&elevationBand(305,301)==TERRAIN_MID&&elevationBand(306,301)==TERRAIN_HIGH&&elevationBand(UNKNOWN_HEIGHT,301)==BACKGROUND,"stable relative terrain bands");
        var arena=new CaravanMapRaster(CaravanTerrainLayout.safe(301),CaravanTerrainLayout.risky(301),
            List.of(new Position(-36,301,-24),new Position(37,301,25)),301,CaravanTerrainLayout.escortBase(301),CaravanTerrainLayout.attackBase(301));
        int[] realistic=new int[TERRAIN_SIZE*TERRAIN_SIZE];Arrays.fill(realistic,UNKNOWN_HEIGHT);
        int inside=0;
        for(int i=0;i<realistic.length;i++){
            var at=arena.samplePosition(i%TERRAIN_SIZE,i/TERRAIN_SIZE);
            int x=(int)Math.floor(at.x()),z=(int)Math.floor(at.z());
            if(!arenaColumn(x,z,36,24))continue;
            inside++;
            for(int h=CaravanTerrainLayout.HEIGHT-1;h>=0;h--)if(CaravanTerrainLayout.cell(x,h,z)!=CaravanTerrainLayout.Cell.AIR){
                realistic[i]=301+h;break;
            }
        }
        check(inside>400&&inside<800,"rectangular arena excludes map margins");
        var actual=arena.tactical(Route.SAFE,new Position(-1000,301,0),null,realistic);
        check(pixel(arena,actual,new Position(0,301,10))==TERRAIN_LOW,"level valley terrain is low band");
        for(byte v:new byte[]{TERRAIN_LOW,TERRAIN_MID,TERRAIN_HIGH,CONTOUR,BACKGROUND}){
            int count=0;for(byte p:actual)if(p==v)count++;
            check(count>30,"realistic raster contains terrain category "+v);
        }
        var ec=arena.project(CaravanTerrainLayout.escortBase(301)).orElseThrow();
        var ac=arena.project(CaravanTerrainLayout.attackBase(301)).orElseThrow();
        for(var route:Route.values()){
            var layer=arena.tactical(route,new Position(-1000,301,0),null,realistic);
            int escortCount=0,attackCount=0;
            for(byte px:layer){if(px==ESCORT_BASE)escortCount++;if(px==ATTACK_BASE)attackCount++;}
            check(escortCount==24&&attackCount==16,"both hollow public objectives "+route);
            check(layer[(ec.z()-3)*SIZE+ec.x()]==ESCORT_BASE&&layer[(ac.z()-4)*SIZE+ac.x()]==ATTACK_BASE,"symbols at physical objective positions "+route);
            var selfAt=arena.tactical(route,CaravanTerrainLayout.safe(301).getFirst(),CaravanTerrainLayout.attackBase(301),realistic);
            check(selfAt[ac.z()*SIZE+ac.x()]==SELF&&selfAt[(ac.z()-4)*SIZE+ac.x()]==ATTACK_BASE,"owner at extraction remains distinct "+route);
        }
        int[] changedOutside=realistic.clone();
        for(int i=0;i<changedOutside.length;i++)if(changedOutside[i]==UNKNOWN_HEIGHT)changedOutside[i]=1000;
        check(pixel(arena,arena.tactical(Route.SAFE,new Position(-1000,301,0),null,changedOutside),new Position(0,301,10))==TERRAIN_LOW,"fixed floor color independent of unrelated elevation");
        var home=CaravanTerrainLayout.escortBase(301);
        var enemyHome=CaravanTerrainLayout.attackBase(301);
        check(arena.project(home).isPresent()&&arena.project(enemyHome).isPresent(),"both bases inside map coverage");
        for(var corner:List.of(new Position(-36,301,-24),new Position(36,301,24),new Position(-36,310,24),new Position(36,310,-24)))
            check(arena.project(corner).isPresent()&&pixel(arena,arena.tactical(Route.SAFE,CaravanTerrainLayout.safe(301).getFirst(),corner,terrain),corner)==SELF,"arena corner self remains visible");
        check(pixel(arena,arena.tactical(Route.SAFE,CaravanTerrainLayout.safe(301).getFirst(),home,terrain),home)==SELF,"escort spawn self marker visible");
        var overlap=arena.frame(Route.SAFE,home,home);
        int own=0,cart=0;for(byte v:overlap){if(v==SELF)own++;if(v==WAGON)cart++;}
        check(own>=4&&cart==13&&pixel(arena,overlap,home)==WAGON,"shared rider/wagon location retains both markers");
        var near=new Position(home.x()+.75,home.y()+1.4,home.z());
        var seated=arena.tactical(Route.SAFE,home,near,terrain);own=0;cart=0;
        for(byte v:seated){if(v==SELF)own++;if(v==WAGON)cart++;}
        check(own>=4&&cart==13,"mounted rider remains visible without obscuring wagon");
        var publicFrame=arena.frame(Route.SAFE,home,null);own=0;for(byte v:publicFrame)if(v==SELF)own++;
        check(own==0,"no personal outline on unpersonalized map");
        var prepared=arena.prepare(terrain);
        check(prepared==arena.prepare(terrain.clone()),"equal heights reuse shared layer");
        Random random=new Random(27112026);
        int frames=0;
        for(int round=0;round<40;round++){
            int[] h=new int[TERRAIN_SIZE*TERRAIN_SIZE];
            for(int i=0;i<h.length;i++)h[i]=random.nextInt(5)==0?UNKNOWN_HEIGHT:300+random.nextInt(12);
            var layer=arena.prepare(h);
            for(var route:Route.values())for(int n=0;n<16;n++){
                var cartPos=new Position(random.nextInt(90)-45,301,random.nextInt(70)-35);
                var ownPos=n%4==0?cartPos:n%4==1?null:new Position(random.nextInt(90)-45,301,random.nextInt(70)-35);
                byte[] reference=arena.tactical(route,cartPos,ownPos,h);
                if(!Arrays.equals(reference,arena.cachedTactical(layer,route,cartPos,ownPos)))throw new AssertionError("cached frame mismatch "+round+" "+n);
                frames++;
            }
        }
        check(frames==1280,"1280 full raster equivalence cases (20971520 pixels)");
        var cache=arena.prepare(terrain);int[] changed=terrain.clone();changed[0]++;
        check(cache!=arena.prepare(changed),"height mutation invalidates shared cache");
        check(cache!=arena.prepare(terrain),"restore heights rebuilds cache");
        byte[] isolated=arena.cachedTactical(arena.prepare(terrain),Route.SAFE,home,home);isolated[0]=99;
        check(arena.cachedTactical(arena.prepare(terrain),Route.SAFE,home,home)[0]!=99,"private frame cannot corrupt shared layer");
        System.out.println("CARAVAN_MAP "+passed+"/"+passed+" PASS");
    }
}
