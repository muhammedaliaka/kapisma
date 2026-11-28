package tr.kapisma.game;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import tr.kapisma.arena.MatchRoster.Position;

/** Block-only clearance of the wagon and safe drop positions, without chunk generation.
 * Flat/stepped route candidate: ramps and physical mounts require separate acceptance.
 */
public final class CaravanWorldSafety {
    private final ServerLevel level;
    private final int minX,maxX,minY,maxY,minZ,maxZ;
    public CaravanWorldSafety(ServerLevel level,int minX,int maxX,int minY,int maxY,int minZ,int maxZ){
        this.level=Objects.requireNonNull(level);
        if(minX>=maxX||minY>=maxY||minZ>=maxZ)throw new IllegalArgumentException("arena bounds");
        this.minX=minX;this.maxX=maxX;this.minY=minY;this.maxY=maxY;this.minZ=minZ;this.maxZ=maxZ;
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    private boolean loaded(AABB b){
        if(b.minX<minX||b.maxX>maxX||b.minY<minY||b.maxY>maxY||b.minZ<minZ||b.maxZ>maxZ||
            b.minY<level.getMinBuildHeight()||b.maxY>=level.getMaxBuildHeight())return false;
        for(int x=((int)Math.floor(b.minX))>>4;x<=((int)Math.floor(b.maxX))>>4;x++)
            for(int z=((int)Math.floor(b.minZ))>>4;z<=((int)Math.floor(b.maxZ))>>4;z++)
                if(!level.getChunkSource().hasChunk(x,z))return false;
        return level.getWorldBorder().isWithinBounds(b);
    }
    private boolean solidFloor(Position p,double x,double z){
        var b=BlockPos.containing(p.x()+x,p.y()-.05,p.z()+z);
        return level.getBlockState(b).isFaceSturdy(level,b,Direction.UP)&&level.getFluidState(b).isEmpty();
    }
    private boolean clear(Position p,double width,double depth,double height){
        var box=new AABB(p.x()-width,p.y()+.001,p.z()-depth,p.x()+width,p.y()+height,p.z()+depth);
        if(!loaded(box.inflate(.02,1,.02))||level.getBlockCollisions(null,box).iterator().hasNext())return false;
        if(!solidFloor(p,0,0))return false;
        for(double x:new double[]{-width+.1,width-.1})for(double z:new double[]{-depth+.1,depth-.1})
            if(!solidFloor(p,x,z))return false;
        for(var b:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if(!level.getFluidState(b).isEmpty())return false;
        return true;
    }
    public boolean wagonStep(Position from,Position to){
        thread();double distance=Math.sqrt(from.distanceSquared(to));
        if(distance>1.000001)return false;
        int count=Math.max(1,(int)Math.ceil(distance/.20));
        for(int i=1;i<=count;i++){
            double f=i/(double)count;
            var p=new Position(from.x()+(to.x()-from.x())*f,from.y()+(to.y()-from.y())*f,from.z()+(to.z()-from.z())*f);
            if(!clear(p,tr.kapisma.arena.CaravanWagonGeometry.RADIUS,tr.kapisma.arena.CaravanWagonGeometry.RADIUS,tr.kapisma.arena.CaravanWagonGeometry.HEIGHT))return false;
        }
        return true;
    }
    public boolean crateSafe(Position p){thread();return clear(p,.48,.48,1);}
    public boolean horseHomeSafe(Position p){thread();return clear(p,.72,.72,2.1);}
    /** Bounded nearest safe landing search; no forced chunk load and no automatic score change. */
    public Optional<Position> nearestDrop(Position at){
        thread();if(crateSafe(at))return Optional.of(at);
        int x=(int)Math.floor(at.x()),y=(int)Math.floor(at.y()),z=(int)Math.floor(at.z());
        Position best=null;double distance=Double.POSITIVE_INFINITY;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-4;dy<=1;dy++){
            var p=new Position(x+dx+.5,y+dy,z+dz+.5);
            double d=p.distanceSquared(at);
            if(d<distance&&crateSafe(p)){distance=d;best=p;}
        }
        return Optional.ofNullable(best);
    }
}
