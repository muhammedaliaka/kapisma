package tr.kapisma.game;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import tr.kapisma.arena.*;
import tr.kapisma.arena.MatchRoster.*;
/** Server raycast + bounded acceleration; vanilla held item and particle rope. */
public final class CaravanHookAdapter implements AutoCloseable {
    private static final String MARKER="kapisma_caravan_hook";
    private final ServerLevel level;
    private final CaravanGrappleRules rules;
    private final int xRadius,zRadius,minY,maxY;
    public CaravanHookAdapter(ServerLevel level,MatchRoster roster,int y,int xRadius,int zRadius,boolean manual){
        this.level=Objects.requireNonNull(level);this.rules=new CaravanGrappleRules(roster,manual);
        this.xRadius=xRadius;this.zRadius=zRadius;minY=y;maxY=Math.min(level.getMaxBuildHeight()-1,y+CaravanTerrainLayout.HEIGHT+5);
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    public static ItemStack item(){
        var s=new ItemStack(Items.FISHING_ROD);var tag=new CompoundTag();tag.putBoolean(MARKER,true);
        s.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));s.set(DataComponents.UNBREAKABLE,new Unbreakable(true));
        s.set(DataComponents.CUSTOM_NAME,Component.literal("Tırmanma kancası · 30 sn"));return s;
    }
    public static boolean isHook(ItemStack s){
        var data=s.get(DataComponents.CUSTOM_DATA);return s.is(Items.FISHING_ROD)&&data!=null&&data.copyTag().getBoolean(MARKER);
    }
    private boolean inside(Vec3 p){return p.x>=-xRadius&&p.x<xRadius+1&&p.z>=-zRadius&&p.z<zRadius+1&&p.y>=minY&&p.y<maxY;}
    private boolean loaded(BlockPos p){return level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4);}
    private boolean validAnchor(Position p){
        Vec3 v=new Vec3(p.x(),p.y(),p.z());if(!inside(v))return false;
        var b=BlockPos.containing(v);return loaded(b)&&!level.getBlockState(b).getCollisionShape(level,b).isEmpty()&&level.getFluidState(b).isEmpty();
    }
    private boolean loadedRange(Vec3 start,Vec3 end){
        for(int cx=((int)Math.floor(Math.min(start.x,end.x)))>>4;cx<=((int)Math.floor(Math.max(start.x,end.x)))>>4;cx++)
            for(int cz=((int)Math.floor(Math.min(start.z,end.z)))>>4;cz<=((int)Math.floor(Math.max(start.z,end.z)))>>4;cz++)
                if(!level.getChunkSource().hasChunk(cx,cz))return false;
        return true;
    }
    /** A rope cannot keep applying force through a new solid obstruction.
     * Validate even while manual pull is idle; no chunk creation. */
    private boolean ropeClear(UUID id,Position a){
        var p=level.getServer().getPlayerList().getPlayer(id);
        if(p==null||p.level()!=level||!p.isAlive()||p.isSpectator()||p.isPassenger()||!validAnchor(a))return false;
        var start=p.getEyePosition();var end=new Vec3(a.x(),a.y(),a.z());
        if(!inside(start)||!loadedRange(start,end))return false;
        var hit=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(BlockPos.containing(end));
    }
    private Optional<Position> anchor(ServerPlayer p){
        Vec3 start=p.getEyePosition(),look=p.getViewVector(1);
        if(!inside(start))return Optional.empty();
        // Clip endpoint before raycasting to avoid inspecting/generating chunks outside arena.
        double range=CaravanGrappleRules.MAX_RANGE;
        for(double d=1;d<=range;d+=.5)if(!inside(start.add(look.scale(d)))){range=Math.max(0,d-.5);break;}
        if(range<2)return Optional.empty();
        Vec3 end=start.add(look.scale(range));
        if(!loadedRange(start,end))return Optional.empty();
        var hit=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK)return Optional.empty();
        var normal=hit.getDirection().getNormal();
        Vec3 v=hit.getLocation().subtract(normal.getX()*.05,normal.getY()*.05,normal.getZ()*.05);
        Position a=new Position(v.x,v.y,v.z);return validAnchor(a)?Optional.of(a):Optional.empty();
    }
    public boolean use(ServerPlayer p,long tick,Collection<Presence> eligible,boolean running){
        thread();rules.observe(tick,eligible,running);
        UUID id=p.getUUID();if(rules.attached(id))return rules.manual()?rules.pulse(id):false;
        if(rules.remaining(id)>0){p.displayClientMessage(Component.literal("Kanca: "+((rules.remaining(id)+19)/20)+" sn"),true);return false;}
        if(!running||eligible.stream().noneMatch(o->o.player().equals(id)))return false;
        return anchor(p).filter(a->rules.fire(id,a)).isPresent();
    }
    public void tick(long tick,Collection<Presence> eligible,boolean running){
        thread();rules.observe(tick,eligible,running);
        for(var m:rules.advance(this::ropeClear)){
            var p=level.getServer().getPlayerList().getPlayer(m.player());
            if(p==null||p.level()!=level||!p.isAlive()||p.isSpectator()||p.isPassenger()){rules.cancel(m.player());continue;}
            var a=m.acceleration();Vec3 before=p.getDeltaMovement(),v=before.scale(.92).add(a.x(),a.y(),a.z());
            if(v.length()>.72)v=v.normalize().scale(.72);
            p.setDeltaMovement(v);p.hurtMarked=true;p.fallDistance=0;
            if(tick%5==0){
                var from=p.getEyePosition();var dest=new Vec3(m.anchor().x(),m.anchor().y(),m.anchor().z());
                int n=Math.min(12,Math.max(2,(int)Math.ceil(from.distanceTo(dest)/2)));
                for(int i=1;i<=n;i++){
                    var pos=from.lerp(dest,i/(double)n);
                    level.sendParticles(ParticleTypes.CRIT,pos.x,pos.y,pos.z,1,0,0,0,0);
                }
            }
        }
    }
    public long remaining(UUID id){thread();return rules.remaining(id);}
    public boolean attached(UUID id){thread();return rules.attached(id);}
    public void cancel(UUID id){thread();rules.cancel(id);}
    public void newLeg(){thread();rules.clearActive();}
    @Override public void close(){thread();rules.end();}
}
