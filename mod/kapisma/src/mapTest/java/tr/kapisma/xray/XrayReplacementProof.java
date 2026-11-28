package tr.kapisma.xray;

import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;

/** Compare the optimized replacement with the unchanged original hash/host path. */
public final class XrayReplacementProof {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Method hash=Xray.class.getDeclaredMethod("hash",long.class,long.class,long.class);
        Method host=Xray.class.getDeclaredMethod("host",boolean.class,int.class,long.class);
        hash.setAccessible(true);host.setAccessible(true);
        Random rng=new Random(817);
        int n=0;
        long salt=Xray.salt;
        try{
            for(int seed=0;seed<4;seed++){
                Xray.salt=rng.nextLong();
                for(boolean nether:new boolean[]{false,true})for(int y=-64;y<320;y++)for(int i=0;i<32;i++){
                    int x=rng.nextInt(60000001)-30000000,z=rng.nextInt(60000001)-30000000;
                    long h=(Long)hash.invoke(null,(long)x,(long)y,(long)z);
                    BlockState expected=(BlockState)host.invoke(null,nether,y,h);
                    if(expected!=Xray.replacement(nether,x,y,z))
                        throw new AssertionError("replacement parity");
                    n++;
                }
            }
        }finally{Xray.salt=salt;}
        System.out.println("XRAY_REPLACEMENT_PARITY "+n+"/"+n+" PASS");
    }
}
