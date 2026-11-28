package tr.kapisma.game;
import java.util.Arrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import tr.kapisma.arena.CaravanMapRaster;
/** One shared sampler per match, bounded work, no forced chunk generation. */
public final class CaravanTerrainSampler {
    private final ServerLevel level;
    private final CaravanMapRaster raster;
    private final int[] heights=new int[CaravanMapRaster.TERRAIN_SIZE*CaravanMapRaster.TERRAIN_SIZE];
    private final int floor,ceiling,xRadius,zRadius;
    private int cursor;
    private long lastTick=-1;
    public CaravanTerrainSampler(ServerLevel level,CaravanMapRaster raster,int floor,int xRadius,int zRadius){
        this.level=level;this.raster=raster;this.floor=floor;this.ceiling=floor+tr.kapisma.arena.CaravanTerrainLayout.HEIGHT-1;
        this.xRadius=xRadius;this.zRadius=zRadius;thread();Arrays.fill(heights,CaravanMapRaster.UNKNOWN_HEIGHT);
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    public void tick(long tick){
        thread();if(tick<0||tick<=lastTick)return;lastTick=tick;
        for(int n=0;n<32;n++){
            int i=cursor++%heights.length;cursor%=heights.length;
            var p=raster.samplePosition(i%CaravanMapRaster.TERRAIN_SIZE,i/CaravanMapRaster.TERRAIN_SIZE);
            int x=(int)Math.floor(p.x()),z=(int)Math.floor(p.z());
            if(!CaravanMapRaster.arenaColumn(x,z,xRadius,zRadius)||!level.getChunkSource().hasChunk(x>>4,z>>4)){
                heights[i]=CaravanMapRaster.UNKNOWN_HEIGHT;continue;
            }
            int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            heights[i]=CaravanMapRaster.boundedSurface(top,floor,ceiling,y->{
                var pos=new net.minecraft.core.BlockPos(x,y,z);
                var s=level.getBlockState(pos);
                return s.blocksMotion()||!s.getFluidState().isEmpty();
            });
        }
    }
    public int[] snapshot(){thread();return heights.clone();}
    public String qaStats(){
        thread();int known=0,outside=0,low=0,mid=0,high=0,min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int i=0;i<heights.length;i++){
            int h=heights[i];if(h==CaravanMapRaster.UNKNOWN_HEIGHT)continue;
            var p=raster.samplePosition(i%CaravanMapRaster.TERRAIN_SIZE,i/CaravanMapRaster.TERRAIN_SIZE);
            if(!CaravanMapRaster.arenaColumn((int)Math.floor(p.x()),(int)Math.floor(p.z()),xRadius,zRadius))outside++;
            known++;min=Math.min(min,h);max=Math.max(max,h);
            switch(CaravanMapRaster.elevationBand(h,floor+1)){
                case CaravanMapRaster.TERRAIN_LOW->low++;
                case CaravanMapRaster.TERRAIN_MID->mid++;
                case CaravanMapRaster.TERRAIN_HIGH->high++;
            }
        }
        return " terrainKnown="+known+" terrainOutside="+outside+" terrainLow="+low+" terrainMid="+mid+" terrainHigh="+high+" terrainMin="+(known==0?0:min)+" terrainMax="+(known==0?0:max);
    }
}
