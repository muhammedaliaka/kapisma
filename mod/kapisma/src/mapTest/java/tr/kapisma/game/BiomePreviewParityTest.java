package tr.kapisma.game;

import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;

/** Independent reference derived from local Minecraft 1.21.1 bytecode.
 * Synthetic biome masks, real Minecraft palette and sine table; not a world integration test. */
public final class BiomePreviewParityTest {
    private static final int W=128;
    private static final Brightness[] SHALLOW={
        Brightness.LOWEST,Brightness.LOWEST,Brightness.LOW,Brightness.LOW,
        Brightness.LOW,Brightness.LOW,Brightness.NORMAL,Brightness.NORMAL
    };
    private static final Brightness[] WAVES={
        Brightness.LOW,Brightness.NORMAL,Brightness.HIGH,Brightness.NORMAL,Brightness.LOW
    };
    private static int oracle(int x,int z,boolean sea,int n) {
        if (!sea) return n==0 ? -1 : Byte.toUnsignedInt(MapColor.COLOR_BROWN.getPackedId(n>3?Brightness.NORMAL:Brightness.LOWEST));
        if(n==8 && z%2!=0) return -1;
        Brightness b=n<8?SHALLOW[n]:WAVES[((x+(int)(7f*Mth.sin(z)))/8)%5];
        return Byte.toUnsignedInt(MapColor.COLOR_ORANGE.getPackedId(b));
    }
    private static void verifyMask(int cx,int cz,int scale,int mask) {
        int d=1<<scale, sx=(cx/d-64)*d, sz=(cz/d-64)*d;
        boolean[] sea=new boolean[W*W];
        for(int z=0;z<W;z++) for(int x=0;x<W;x++) {
            int xx=sx+x*d, zz=sz+z*d;
            sea[z*W+x]=switch(mask) {
                case 0 -> false;
                case 1 -> true;
                case 2 -> ((x+z)&1)==0;
                default -> Math.floorMod(xx*31+zz*17+xx*zz,19)<9;
            };
        }
        byte[] expect=new byte[W*W], actual=new byte[W*W];
        Arrays.fill(expect,(byte)37); Arrays.fill(actual,(byte)37);
        for(int x=1;x<127;x++) for(int z=1;z<127;z++) {
            int n=0;
            for(int iz=z-1;iz<=z+1;iz++) for(int ix=x-1;ix<=x+1;ix++)
                if((ix!=x||iz!=z)&&sea[iz*W+ix]) n++;
            int c=oracle(x,z,sea[z*W+x],n);
            if(c>=0) expect[z*W+x]=(byte)c;
        }
        var work=new MapPreviewWork(cx,cz,scale);
        int steps=0;
        while(!work.complete()) {
            int count=work.step(257,100,()->0L,(x,z)->{
                if((x-sx)%d!=0||(z-sz)%d!=0)throw new AssertionError("spacing");
                return sea[(z-sz)/d*W+(x-sx)/d];
            },(x,z,b,n)->{
                int c=IncrementalBiomePreview.packedColor(x,z,b,n);
                if(c>=0)actual[z*W+x]=(byte)c;
            });
            if(count<=0||++steps>1000)throw new AssertionError("stalled");
        }
        if(!Arrays.equals(expect,actual))throw new AssertionError("pixel mismatch "+cx+","+cz+","+scale+","+mask);
    }
    public static void main(String[] args) {
        int cases=0;
        for(int x=1;x<127;x++)for(int z=1;z<127;z++)for(int n=0;n<=8;n++)for(boolean sea:new boolean[]{false,true}){
            if(oracle(x,z,sea,n)!=IncrementalBiomePreview.packedColor(x,z,sea,n))throw new AssertionError("palette mismatch");
            cases++;
        }
        System.out.println("PALETTE "+cases+"/"+cases+" PASS");
        int maps=0;
        for(int[] center:new int[][]{{0,0},{-17,19},{17,-19},{-29999900,29999900}})
            for(int scale=0;scale<=4;scale++)for(int mask=0;mask<4;mask++){
                verifyMask(center[0],center[1],scale,mask);maps++;
            }
        System.out.println("SYNTHETIC MAP ARRAYS "+maps+"/"+maps+" PASS");
    }
}
