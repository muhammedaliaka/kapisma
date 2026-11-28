package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
import tr.kapisma.arena.CaravanRouteRules.Route;

/** Shared tactical route diagram: no enemy positions or other hidden player data. */
public final class CaravanMapRaster {
    public static final int SIZE=128;
    public static final byte BACKGROUND=0, SAFE=1, RISKY=2, SELECTED=3, START=4, FINISH=5, WAGON=6, SELF=7, GRID=8, TERRAIN_LOW=9, TERRAIN_HIGH=10, CONTOUR=11, TERRAIN_MID=12, ESCORT_BASE=13, ATTACK_BASE=14;
    public static final int TERRAIN_SIZE=32, UNKNOWN_HEIGHT=Integer.MIN_VALUE;
    public record Pixel(int x,int z){}
    private final List<Position> safe,risky;
    private final double midX,midZ,scale;
    private final Integer baseHeight;
    private final Position escortBase,attackBase;
    private int[] cachedHeights;
    private Prepared prepared;
    public static final class Prepared {
        private final byte[] safe,risky;
        private Prepared(byte[] safe,byte[] risky){this.safe=safe;this.risky=risky;}
    }
    /** Shared immutable terrain and route layer; rebuild only for changed sampled heights.
     * Server thread owns the cache. The caller cannot mutate either layer. */
    public Prepared prepare(int[] heights){
        if(heights.length!=TERRAIN_SIZE*TERRAIN_SIZE)throw new IllegalArgumentException("terrain size");
        if(prepared!=null&&Arrays.equals(cachedHeights,heights))return prepared;
        // A point beyond the projection is deliberately invisible, leaving only static layers.
        Position invisible=new Position(midX+SIZE/scale*2,0,midZ+SIZE/scale*2);
        prepared=new Prepared(tactical(Route.SAFE,invisible,null,heights),tactical(Route.RISKY,invisible,null,heights));
        cachedHeights=heights.clone();return prepared;
    }
    public byte[] cachedTactical(Prepared base,Route selected,Position wagon,Position self){
        Objects.requireNonNull(base);Objects.requireNonNull(selected);Objects.requireNonNull(wagon);
        byte[] out=(selected==Route.SAFE?base.safe:base.risky).clone();
        markers(out,wagon,self);north(out);return out;
    }
    public CaravanMapRaster(List<Position> safe,List<Position> risky){this(safe,risky,List.of());}
    public CaravanMapRaster(List<Position> safe,List<Position> risky,List<Position> bounds){this(safe,risky,bounds,null);}
    public CaravanMapRaster(List<Position> safe,List<Position> risky,List<Position> bounds,Integer baseHeight){this(safe,risky,bounds,baseHeight,null,null);}
    public CaravanMapRaster(List<Position> safe,List<Position> risky,List<Position> bounds,Integer baseHeight,Position escortBase,Position attackBase){
        this.safe=List.copyOf(safe);this.risky=List.copyOf(risky);this.baseHeight=baseHeight;
        if((escortBase==null)!=(attackBase==null)||escortBase!=null&&escortBase.equals(attackBase))throw new IllegalArgumentException("base pair");
        this.escortBase=escortBase;this.attackBase=attackBase;
        if(safe.size()<2||risky.size()<2||safe.size()>512||risky.size()>512)throw new IllegalArgumentException("paths");
        var all=new ArrayList<>(this.safe);all.addAll(this.risky);all.addAll(List.copyOf(bounds));
        if(escortBase!=null){all.add(escortBase);all.add(attackBase);}
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(var p:all){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());}
        midX=minX+(maxX-minX)/2;midZ=minZ+(maxZ-minZ)/2;
        double span=Math.max(maxX-minX,maxZ-minZ);
        if(!Double.isFinite(span)||span<=0||span>20000)throw new IllegalArgumentException("map span");
        scale=112/span;
    }
    public Optional<Pixel> project(Position p){
        double x=64+(p.x()-midX)*scale,z=64+(p.z()-midZ)*scale;
        if(x<0||x>=SIZE||z<0||z>=SIZE)return Optional.empty();
        return Optional.of(new Pixel((int)x,(int)z));
    }
    public byte[] frame(Route selected,Position wagon,Position self){
        Objects.requireNonNull(selected);Objects.requireNonNull(wagon);
        byte[] result=new byte[SIZE*SIZE];
        if(selected==Route.SAFE){linePath(result,risky,RISKY);linePath(result,safe,SELECTED);}
        else {linePath(result,safe,SAFE);linePath(result,risky,SELECTED);}
        mark(result,safe.getFirst(),START,2);mark(result,safe.getLast(),FINISH,2);
        if(escortBase!=null){baseIcon(result,escortBase,false);baseIcon(result,attackBase,true);}
        markers(result,wagon,self);return result;
    }
    private void markers(byte[] result,Position wagon,Position self){
        if(self!=null)mark(result,self,SELF,1);
        mark(result,wagon,WAGON,2);
        // A driver sitting on the wagon must not disappear from their own map.
        // Keep the wagon's gold center and use a white outline around the shared position.
        if(self!=null){
            var sp=project(self);var wp=project(wagon);
            if(sp.isPresent()&&wp.isPresent()){
                var a=sp.get();var b=wp.get();int dx=a.x-b.x,dz=a.z-b.z;
                if(dx*dx+dz*dz<=4){
                    for(int oz=-3;oz<=3;oz++)for(int ox=-3;ox<=3;ox++){
                        int d=ox*ox+oz*oz;
                        int x=a.x+ox,z=a.z+oz;
                        if(d>=6&&d<=10&&x>=0&&x<SIZE&&z>=0&&z<SIZE&&result[z*SIZE+x]!=WAGON)set(result,x,z,SELF);
                    }
                }
            }
        }
    }
    /** World sampling coordinates for a 32x32 coarse terrain grid. */
    public Position samplePosition(int x,int z){
        if(x<0||z<0||x>=TERRAIN_SIZE||z>=TERRAIN_SIZE)throw new IllegalArgumentException("sample");
        return new Position(midX+(x*4+2.5-64)/scale,0,midZ+(z*4+2.5-64)/scale);
    }
    public static boolean arenaColumn(int x,int z,int xRadius,int zRadius){
        return xRadius>=0&&zRadius>=0&&x>=-xRadius&&x<=xRadius&&z>=-zRadius&&z<=zRadius;
    }
    /** Heightmap is exclusive. A column above our arena may contain unrelated world blocks.
     * Only scan the bounded arena slab when the fast heightmap result exceeds it. */
    public static int boundedSurface(int top,int floor,int ceiling,java.util.function.IntPredicate blocking){
        if(floor>ceiling||ceiling==Integer.MAX_VALUE)throw new IllegalArgumentException("height range");
        if(top<=floor)return UNKNOWN_HEIGHT;
        if(top<=ceiling+1)return top;
        for(int y=ceiling;y>=floor;y--)if(blocking.test(y))return y+1;
        return UNKNOWN_HEIGHT;
    }
    public static byte elevationBand(int height,int base){
        if(height==UNKNOWN_HEIGHT)return BACKGROUND;
        long delta=(long)height-base;
        return delta>=5?TERRAIN_HIGH:delta>=2?TERRAIN_MID:TERRAIN_LOW;
    }
    /** Tactical style: north up, dark terrain, coordinate grid, true sampled contour bands.
     * Unknown terrain stays plain; never fabricate elevations. */
    public byte[] tactical(Route selected,Position wagon,Position self,int[] heights){
        if(heights.length!=TERRAIN_SIZE*TERRAIN_SIZE)throw new IllegalArgumentException("terrain size");
        byte[] overlay=frame(selected,wagon,self);
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int h:heights)if(h!=UNKNOWN_HEIGHT){min=Math.min(min,h);max=Math.max(max,h);}
        for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++){
            int i=z*SIZE+x;if(overlay[i]!=BACKGROUND)continue;
            int gx=x/4,gz=z/4,h=heights[gz*TERRAIN_SIZE+gx];
            byte color=BACKGROUND;
            if(h!=UNKNOWN_HEIGHT){
                color=baseHeight==null?((long)h-min>((long)max-min)/2?TERRAIN_HIGH:TERRAIN_LOW):elevationBand(h,baseHeight);
                if(x%4==0&&gx>0&&differentBand(h,heights[gz*TERRAIN_SIZE+gx-1])||
                   z%4==0&&gz>0&&differentBand(h,heights[(gz-1)*TERRAIN_SIZE+gx]))color=CONTOUR;
            }
            if((x%16==0||z%16==0)&&color!=CONTOUR)color=GRID;
            overlay[i]=color;
        }
        north(overlay);return overlay;
    }
    private static void north(byte[] overlay){
        // Compact north arrow in the reserved map margin.
        for(int z=2;z<=7;z++)set(overlay,64,z,START);
        set(overlay,63,3,START);set(overlay,65,3,START);
    }
    private boolean differentBand(int a,int b){
        if(b==UNKNOWN_HEIGHT)return false;
        return baseHeight==null?Math.floorDiv(a,5)!=Math.floorDiv(b,5):
            Math.floorDiv((long)a-baseHeight,2)!=Math.floorDiv((long)b-baseHeight,2);
    }
    /** Public objective symbols, hollow so a player at the base stays visible. */
    private void baseIcon(byte[] a,Position p,boolean attack){
        project(p).ifPresent(q->{for(int z=-4;z<=4;z++)for(int x=-4;x<=4;x++){
            if(attack?Math.abs(x)+Math.abs(z)==4:Math.max(Math.abs(x),Math.abs(z))==3)
                set(a,q.x+x,q.z+z,attack?ATTACK_BASE:ESCORT_BASE);
        }});
    }
    private void mark(byte[] a,Position p,byte value,int radius){
        project(p).ifPresent(q->{for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++)if(x*x+z*z<=radius*radius)set(a,q.x+x,q.z+z,value);});
    }
    private static void set(byte[] a,int x,int z,byte v){if(x>=0&&z>=0&&x<SIZE&&z<SIZE)a[z*SIZE+x]=v;}
    private void linePath(byte[] a,List<Position> route,byte color){
        for(int i=1;i<route.size();i++){
            Pixel p=project(route.get(i-1)).orElseThrow(),q=project(route.get(i)).orElseThrow();
            int x=p.x,z=p.z,dx=Math.abs(q.x-x),dz=-Math.abs(q.z-z),sx=x<q.x?1:-1,sz=z<q.z?1:-1,error=dx+dz;
            while(true){set(a,x,z,color);if(x==q.x&&z==q.z)break;int twice=2*error;if(twice>=dz){error+=dz;x+=sx;}if(twice<=dx){error+=dx;z+=sz;}}
        }
    }
}
