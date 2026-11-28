package tr.kapisma.game;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundSource;
import tr.kapisma.arena.MatchRoster.Position;

/** Authoritative seat + real vanilla horses. Never accepts client vehicle coordinates as route progress. */
public final class CaravanMountAdapter implements AutoCloseable {
    private final ServerLevel level;
    private final Map<UUID,Horse> horses=new HashMap<>();
    private final Map<UUID,Position> homes=new HashMap<>();
    private final CaravanWorldSafety safety;
    private final double yMin,yMax,xRadius,zRadius;
    private ArmorStand seat;
    private UUID driver;
    private UUID transitioning;
    private int lastInputTick=-1;
    private boolean forwardIntent;
    private boolean closed;
    public boolean internalTeleport(UUID id){thread();return id!=null&&id.equals(transitioning);}
    public CaravanMountAdapter(ServerLevel level,CaravanWorldSafety safety,double yMin,double yMax,double xRadius,double zRadius){
        this.level=Objects.requireNonNull(level);this.safety=Objects.requireNonNull(safety);
        this.yMin=yMin;this.yMax=yMax;this.xRadius=xRadius;this.zRadius=zRadius;
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    public boolean mount(ServerPlayer p,Position wagon,double heading){
        thread();if(closed||driver!=null||p.level()!=level||!p.isAlive()||p.isSpectator()||p.isPassenger())return false;
        var s=new ArmorStand(EntityType.ARMOR_STAND,level);
        s.setInvisible(true);s.setNoGravity(true);s.setInvulnerable(true);s.setSilent(true);
        var at=tr.kapisma.arena.CaravanWagonGeometry.seat(wagon,heading);s.setPos(at.x(),at.y(),at.z());
        if(!level.addFreshEntity(s)){s.discard();return false;}
        p.zza=0;p.xxa=0;
        transitioning=p.getUUID();
        try{
            if(!p.startRiding(s,true)){s.discard();return false;}
            seat=s;driver=p.getUUID();lastInputTick=-1;forwardIntent=false;return true;
        }finally{transitioning=null;}
    }
    public UUID forward(){
        thread();if(driver==null||seat==null||seat.isRemoved())return null;
        var p=level.getServer().getPlayerList().getPlayer(driver);
        if(p==null||p.level()!=level||p.getVehicle()!=seat||!p.isAlive()||p.isSpectator())return null;
        int now=level.getServer().getTickCount();return forwardIntent&&lastInputTick>=0&&now-lastInputTick>=0&&now-lastInputTick<=5?driver:null;
    }
    public void resetInput(){thread();lastInputTick=-1;forwardIntent=false;}
    public void input(ServerPlayer p,float forward,boolean shift){
        thread();if(closed||!seated(p.getUUID())||p.level()!=level)return;
        lastInputTick=level.getServer().getTickCount();
        forwardIntent=!shift&&Float.isFinite(forward)&&forward>.1f&&forward<=1f;
    }
    public boolean seated(UUID id){
        thread();if(!Objects.equals(id,driver)||seat==null||seat.isRemoved())return false;
        var p=level.getServer().getPlayerList().getPlayer(id);return p!=null&&p.getVehicle()==seat;
    }
    public void moveSeat(Position at,double heading){
        thread();if(seat==null||seat.isRemoved())return;
        transitioning=driver;
        try{var p=tr.kapisma.arena.CaravanWagonGeometry.seat(at,heading);seat.setPos(p.x(),p.y(),p.z());
            for(var rider:seat.getPassengers())seat.positionRider(rider);
        }finally{transitioning=null;}
    }
    public boolean qaSeat(Position wagon,double heading){
        thread();if(seat==null||driver==null||!seated(driver))return false;
        var at=tr.kapisma.arena.CaravanWagonGeometry.seat(wagon,heading);
        return at.distanceSquared(new Position(seat.getX(),seat.getY(),seat.getZ()))<1e-8;
    }
    public void release(){
        thread();var oldSeat=seat;var oldDriver=driver;seat=null;driver=null;lastInputTick=-1;forwardIntent=false;
        transitioning=oldDriver;
        try{if(oldSeat!=null){oldSeat.ejectPassengers();oldSeat.discard();}
            if(oldDriver!=null){var p=level.getServer().getPlayerList().getPlayer(oldDriver);if(p!=null){p.zza=0;p.xxa=0;}}
        }finally{transitioning=null;}
    }
    /** One saddle-equipped horse per member, protected from loot/foreign riders by host event guard. */
    public boolean horse(UUID owner,Position at){
        thread();if(closed||hasHorse(owner)||!safety.horseHomeSafe(at))return false;
        var old=horses.get(owner);
        // Do not spawn a replacement for an entity that may still exist in saved chunk data.
        if(old!=null&&old.getRemovalReason()!=null&&!old.getRemovalReason().shouldDestroy())return false;
        horses.remove(owner);
        var h=new Horse(EntityType.HORSE,level);
        h.setTamed(true);h.setOwnerUUID(owner);h.setPersistenceRequired();h.setInvulnerable(true);
        h.setPos(at.x(),at.y(),at.z());
        h.equipSaddle(new ItemStack(Items.SADDLE),SoundSource.NEUTRAL);
        if(!level.addFreshEntity(h)){h.discard();return false;}
        horses.put(owner,h);homes.put(owner,at);return true;
    }
    public boolean hasHorse(UUID owner){thread();var h=horses.get(owner);return h!=null&&!h.isRemoved();}
    public boolean owns(Entity e){thread();return e==seat||horses.containsValue(e);}
    public boolean horseOwner(ServerPlayer p,Entity e){thread();return horses.get(p.getUUID())==e;}
    public boolean horseAtHome(UUID owner){
        thread();var h=horses.get(owner);var home=homes.get(owner);
        return h!=null&&!h.isRemoved()&&home!=null&&new Position(h.getX(),h.getY(),h.getZ()).distanceSquared(home)<1;
    }
    /** Recall never carries a rider or a crate to the base. Keeps the same horse entity. */
    public void recall(UUID owner){
        thread();var h=horses.get(owner);var home=homes.get(owner);
        if(h==null||h.isRemoved()||home==null)return;
        h.ejectPassengers();h.getNavigation().stop();h.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.fallDistance=0;
        if(h.level()!=level||!safety.horseHomeSafe(home))throw new IllegalStateException("unsafe horse home");
        h.teleportTo(home.x(),home.y(),home.z());h.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    public void tickHorses(Set<UUID> active){
        thread();for(var entry:horses.entrySet()){
            var id=entry.getKey();var h=entry.getValue();if(h.isRemoved())continue;
            boolean invalidRider=h.getPassengers().stream().anyMatch(p->!id.equals(p.getUUID())||!active.contains(id));
            boolean outside=h.level()!=level||!tr.kapisma.arena.CaravanHorseRules.inside(h.getX(),h.getY(),h.getZ(),h.getBbWidth()/2.0,yMin,yMax,xRadius,zRadius);
            if(outside||invalidRider||!active.contains(id)&&!horseAtHome(id))recall(id);
        }
    }
    public void clearHorses(){thread();for(var h:horses.values()){h.ejectPassengers();h.discard();}horses.clear();homes.clear();}
    @Override public void close(){thread();if(closed)return;closed=true;release();clearHorses();}
}
