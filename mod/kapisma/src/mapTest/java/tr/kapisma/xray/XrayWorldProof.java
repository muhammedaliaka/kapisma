package tr.kapisma.xray;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

/** Defensive, isolated-world proof. No client, no gameplay-world modifications. */
public final class XrayWorldProof {
    private static boolean ore(BlockState state){
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("minecraft")&&(id.getPath().endsWith("_ore")||id.getPath().equals("ancient_debris"));
    }
    private static boolean exposed(ServerLevel level,int x,int y,int z){
        for(Direction d:Direction.values()){
            int xx=x+d.getStepX(),yy=y+d.getStepY(),zz=z+d.getStepZ();
            if(yy<level.getMinBuildHeight()||yy>=level.getMaxBuildHeight())return true;
            LevelChunk c=level.getChunkSource().getChunkNow(xx>>4,zz>>4);
            if(c==null||!c.getBlockState(new BlockPos(xx,yy,zz)).canOcclude())return true;
        }
        return false;
    }
    private static Xray.PState state(ServerLevel level, long[] sent){
        var p=new Xray.PState();
        p.level=level;
        for(long k:sent)p.sent.add(k);
        return p;
    }
    private static boolean checkReveal(ServerLevel level, BlockPos at, int radius, long[] sent){
        var old=state(level,sent);
        var next=state(level,sent);
        long skips=Xray.statEmptyRevealSkips;
        var expected=Xray.revealBatches(level,old,at,radius,false);
        var actual=Xray.revealBatches(level,next,at,radius,true);
        if(!expected.equals(actual)||!old.revealed.equals(next.revealed)||!old.sent.equals(next.sent))
            throw new AssertionError("reveal output/state mismatch "+at+" radius="+radius);
        boolean skipped=Xray.statEmptyRevealSkips>skips;
        if(skipped&&!expected.isEmpty())throw new AssertionError("unsafe reveal skip");
        return skipped;
    }
    private static void proveRevealSkip(ServerLevel level,int cx,int cz){
        long[] keys=new long[3];
        for(int i=0;i<3;i++)keys[i]=net.minecraft.world.level.ChunkPos.asLong(cx+i,cz);
        int n=0,skips=0;
        for(int dx:new int[]{0,7,15,16,31,47})for(int z:new int[]{0,7,15})
            for(int y:new int[]{-64,-49,-16,-1,0,15,16,63,64,80,239,319})
                for(int radius:new int[]{0,1,6}){
                    var at=new BlockPos(cx*16+dx,y,cz*16+z);
                    if(checkReveal(level,at,radius,keys))skips++;
                    n++;
                }
        // An empty sent set cannot publish anything, even when other metadata exists.
        if(!checkReveal(level,new BlockPos(cx*16+7,0,cz*16+7),6,new long[0]))
            throw new AssertionError("empty sent set did not skip");
        n++;skips++;
        // Inject a known positive metadata bit into a real loaded section, then
        // restore it exactly. This tests the head seed across a section boundary
        // at radius 0, and the normal radius-6 path without changing world blocks.
        LevelChunk ch=level.getChunk(cx,cz);
        var meta=Xray.metaOf(ch);
        if(meta==null)throw new AssertionError("missing proof metadata");
        int y=80,si=(y>>4)-meta.minSec,x=7,z=7;
        java.util.BitSet saved=meta.bits[si];
        try{
            var b=saved==null?new java.util.BitSet(4096):(java.util.BitSet)saved.clone();
            b.set(((y&15)<<8)|(z<<4)|x);
            meta.bits[si]=b;
            var at=new BlockPos(cx*16+x,y-1,cz*16+z);
            for(int radius:new int[]{0,6}){
                var legacy=Xray.revealBatches(level,state(level,keys),at,radius,false);
                if(legacy.isEmpty())throw new AssertionError("trivial positive reveal fixture");
                if(checkReveal(level,at,radius,keys))throw new AssertionError("positive reveal skipped");
                n++;
            }
        }finally{meta.bits[si]=saved;}
        if(skips==0||skips>=n)throw new AssertionError("trivial skip proof");
        System.out.println("REAL_WORLD_XRAY_REVEAL_SKIP cases="+n+" skipped="+skips+" positive=2 output_and_state_equal=true PASS");
    }
    public static void run(ServerLevel level){
        if(!level.getServer().isSameThread())throw new AssertionError("wrong thread");
        Integer previous=Xray.forceMode;
        long checked=0,hidden=0,visible=0;
        try{
            Xray.forceMode=1;
            var origin=level.getSharedSpawnPos();
            int cx=origin.getX()>>4,cz=origin.getZ()>>4;
            for(int index=0;index<3;index++){
                LevelChunk chunk=level.getChunk(cx+index,cz);
                LevelChunkSection[] original=chunk.getSections();
                BlockState[] before=new BlockState[original.length*4096];
                for(int si=0;si<original.length;si++)for(int i=0;i<4096;i++)
                    before[si*4096+i]=original[si].getBlockState(i&15,(i>>8)&15,(i>>4)&15);
                var one=Xray.build(chunk,false);
                var two=Xray.build(chunk,false);
                if(one==null||two==null||!Arrays.equals(one.buffer(),two.buffer()))throw new AssertionError("unstable packet");
                var input=new FriendlyByteBuf(Unpooled.wrappedBuffer(one.buffer()));
                try{
                    int ox=chunk.getPos().getMinBlockX(),oz=chunk.getPos().getMinBlockZ();
                    for(int si=0;si<original.length;si++){
                        LevelChunkSection decoded=new LevelChunkSection(level.registryAccess().registryOrThrow(Registries.BIOME));
                        decoded.read(input);
                        int by=(chunk.getMinSection()+si)<<4;
                        for(int i=0;i<4096;i++){
                            int x=i&15,z=(i>>4)&15,y=(i>>8)&15;
                            var real=original[si].getBlockState(x,y,z);
                            var shown=decoded.getBlockState(x,y,z);
                            if(real!=before[si*4096+i])throw new AssertionError("world state changed");
                            boolean shouldHide=ore(real)&&!exposed(level,ox+x,by+y,oz+z);
                            if(one.isHidden(x,by+y,z)!=shouldHide)throw new AssertionError("protection decision mismatch");
                            if(shouldHide){
                                if(shown==real||ore(shown)||shown.isAir())throw new AssertionError("invalid protected replacement");
                                hidden++;
                            }else{
                                if(shown!=real)throw new AssertionError("unrelated block changed");
                                if(ore(real))visible++;
                            }
                            checked++;
                        }
                    }
                    if(input.readableBytes()!=0)throw new AssertionError("packet trailing data");
                }finally{input.release();}
            }
            if(hidden==0||visible==0)throw new AssertionError("trivial proof fixture");
            proveRevealSkip(level,cx,cz);
        }finally{Xray.forceMode=previous;}
        System.out.println("REAL_WORLD_XRAY chunks=3 cells="+checked+" protected="+hidden+" exposed="+visible+
            " world_unchanged=true packets_equal=true PASS");
    }
}
