package tr.kapisma.game;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import tr.kapisma.arena.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.CaravanScenePlan.*;

/** Match-owned vanilla display + interaction entities. No chest inventory or ItemEntity exists.
 * Wire interact() from the owned world's UseEntityCallback; pass fresh server observations.
 * Visual projection only: collision/mount and actual route-driving input are separate acceptance gates.
 */
public final class CaravanSceneAdapter implements AutoCloseable {
    private record Part(Display.BlockDisplay display,CaravanWagonGeometry.Piece geometry){}
    private record EntityView(View view,List<Part> parts,Interaction hit,double heading){}
    private final ServerLevel level;
    private final CaravanMatchRules rules;
    private final CaravanWorldSafety safety;
    private final Map<Key,EntityView> live=new HashMap<>();
    private final Map<UUID,Key> hitKeys=new HashMap<>();
    private boolean closed;
    private int anchorLeg=-1;
    private Set<Integer> pendingDrops=Set.of();
    private final Map<Integer,Position> safeAnchors=new HashMap<>();
    private final Map<Integer,Integer> retryLanding=new HashMap<>();
    public CaravanSceneAdapter(ServerLevel level,CaravanMatchRules rules,CaravanWorldSafety safety){
        this.level=Objects.requireNonNull(level);this.rules=Objects.requireNonNull(rules);this.safety=Objects.requireNonNull(safety);thread();
    }
    /** Host tick with real world collision gate; caller owns authoritative roster and forward intent. */
    public CaravanMatchRules.State tick(long tick,Collection<Presence> observations,Collection<UUID> online,UUID forwardActor){
        thread();if(closed)throw new IllegalStateException("closed scene");
        rules.tick(tick,observations,online,forwardActor,safety::wagonStep);sync();return rules.state();
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    private BlockState material(String name){
        return switch(name){
            case "oak"->Blocks.OAK_PLANKS.defaultBlockState();
            case "dark"->Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case "wheel"->Blocks.COAL_BLOCK.defaultBlockState();
            case "hub"->Blocks.IRON_BLOCK.defaultBlockState();
            case "barrel"->Blocks.BARREL.defaultBlockState();
            default->throw new IllegalArgumentException("unknown wagon material");
        };
    }
    private Part part(CaravanWagonGeometry.Piece geometry,Position at,double heading){
        var e=new Display.BlockDisplay(EntityType.BLOCK_DISPLAY,level);
        e.setBlockState(material(geometry.material()));e.setNoGravity(true);e.setInvulnerable(true);
        var part=new Part(e,geometry);movePart(part,at,heading,true);
        if(!level.addFreshEntity(e)){e.discard();throw new IllegalStateException("display spawn failed");}
        return part;
    }
    private static void movePart(Part part,Position at,double heading,boolean rotate){
        var g=part.geometry();var p=CaravanWagonGeometry.offset(at,heading,g.x(),g.y(),g.z());
        part.display().setPos(p.x(),p.y(),p.z());
        if(rotate)part.display().setTransformation(new com.mojang.math.Transformation(
            new org.joml.Vector3f(),new org.joml.Quaternionf().rotateY((float)-heading),
            new org.joml.Vector3f((float)g.sx(),(float)g.sy(),(float)g.sz()),new org.joml.Quaternionf()));
    }
    private double heading(View view){
        return view.key().kind()==Kind.WAGON||view.key().kind()==Kind.WAGON_CARGO?rules.heading():0;
    }
    private Interaction hit(View view){
        var e=new Interaction(EntityType.INTERACTION,level);
        e.setWidth(.85f);e.setHeight(1f);e.setResponse(true);
        e.addTag("kapisma.caravan.hit");e.addTag("kapisma.caravan."+view.key().kind().name());
        e.addTag("kapisma.caravan.leg."+view.key().leg());e.addTag("kapisma.caravan.crate."+view.key().crate());
        e.setNoGravity(true);e.setInvulnerable(true);moveHit(e,view,heading(view));
        if(!level.addFreshEntity(e)){e.discard();throw new IllegalStateException("hit spawn failed");}
        return e;
    }
    private static void moveHit(Interaction e,View v,double heading){
        var p=v.position();var k=v.key().kind();
        if(k==Kind.WAGON||k==Kind.WAGON_CARGO)p=CaravanWagonGeometry.hit(p,heading,k==Kind.WAGON_CARGO);
        e.setPos(p.x(),p.y(),p.z());
    }
    private EntityView create(View view){
        var p=view.position();var parts=new ArrayList<Part>();Interaction h=null;double angle=heading(view);
        try{
            switch(view.key().kind()){
                case WAGON -> {for(var g:CaravanWagonGeometry.body())parts.add(part(g,p,angle));}
                case WAGON_CARGO -> parts.add(part(CaravanWagonGeometry.CARGO,p,angle));
                case DROPPED -> parts.add(part(new CaravanWagonGeometry.Piece("barrel",-.5,0,-.5,1,1,1),p,0));
                case CARRIED -> parts.add(part(new CaravanWagonGeometry.Piece("barrel",-.5,1.7,-.5,1,1,1),p,0));
            }
            if(view.clickable())h=hit(view);
            return new EntityView(view,List.copyOf(parts),h,angle);
        }catch(RuntimeException ex){for(var part:parts)part.display().discard();if(h!=null)h.discard();throw ex;}
    }
    private void remove(Key key){
        var v=live.remove(key);if(v==null)return;
        for(var part:v.parts())part.display().discard();
        if(v.hit()!=null){hitKeys.remove(v.hit().getUUID());v.hit().discard();}
    }
    private boolean intact(EntityView v){
        return v.parts().stream().allMatch(p->!p.display().isRemoved()&&level.getEntity(p.display().getUUID())==p.display())
            &&(v.hit()==null||!v.hit().isRemoved()&&level.getEntity(v.hit().getUUID())==v.hit());
    }
    public void sync(){
        thread();if(closed)return;
        var state=rules.state();
        if(anchorLeg!=state.leg()){anchorLeg=state.leg();safeAnchors.clear();retryLanding.clear();}
        var pending=new HashSet<Integer>();
        if(state.phase()==CaravanMatchRules.Phase.LEG){
            int now=level.getServer().getTickCount();
            var wagon=state.route().position();
            // All wagon cargo shares the same known physical position.
            boolean wagonSafe=safety.crateSafe(wagon);
            for(var crate:state.crates()){
                int id=crate.id();var at=crate.position();
                switch(crate.status()){
                    case ON_WAGON -> {if(wagonSafe)safeAnchors.put(id,wagon);retryLanding.remove(id);}
                    case STOLEN -> {safeAnchors.remove(id);retryLanding.remove(id);}
                    case CARRIED -> {
                        retryLanding.remove(id);
                        // At most one cheap validation per moving carrier per sync.
                        if(!at.equals(safeAnchors.get(id))&&safety.crateSafe(at))safeAnchors.put(id,at);
                    }
                    case DROPPED -> {
                        if(safety.crateSafe(at)){safeAnchors.put(id,at);retryLanding.remove(id);break;}
                        var retry=retryLanding.get(id);
                        if(retry!=null&&now-retry<20){pending.add(id);break;}
                        var local=safety.nearestDrop(at);
                        boolean recovered=local.isPresent()&&rules.relocateDropped(state.leg(),id,at,local.get());
                        if(!recovered){
                            var anchor=safeAnchors.get(id);
                            if(anchor==null||!safety.crateSafe(anchor))anchor=wagonSafe?wagon:null;
                            if(anchor!=null&&rules.restoreDropped(state.leg(),id,at,anchor)){
                                recovered=true;
                                tr.kapisma.Kapisma.LOG.warn("Caravan crate {} recovered to last safe anchor in leg {}",id,state.leg());
                            }
                        }
                        if(recovered){
                            var landed=rules.state().crates().stream().filter(c->c.id()==id).findFirst().orElseThrow().position();
                            safeAnchors.put(id,landed);retryLanding.remove(id);
                        }else{
                            if(retry==null)tr.kapisma.Kapisma.LOG.warn("Caravan crate {} awaiting safe landing in leg {}",id,state.leg());
                            retryLanding.put(id,now);pending.add(id);
                        }
                    }
                }
            }
        }else{safeAnchors.clear();retryLanding.clear();}
        pendingDrops=Set.copyOf(pending);
        var desired=CaravanScenePlan.project(rules.state(),pendingDrops);
        for(var k:List.copyOf(live.keySet()))if(!desired.containsKey(k)||!intact(live.get(k)))remove(k);
        for(var entry:desired.entrySet()){
            var k=entry.getKey();var v=entry.getValue();var old=live.get(k);
            if(old==null){var created=create(v);live.put(k,created);if(created.hit()!=null)hitKeys.put(created.hit().getUUID(),k);continue;}
            var p=v.position();double angle=heading(v);boolean rotated=Double.compare(old.heading(),angle)!=0;
            if(rotated||!old.view().position().equals(p)){
                for(var part:old.parts())movePart(part,p,angle,rotated);
                if(old.hit()!=null)moveHit(old.hit(),v,angle);
            }
            live.put(k,new EntityView(v,old.parts(),old.hit(),angle));
        }
    }
    /** Return false on unowned/stale entity, invalid actor, or denied action. No client crate ID accepted. */
    public boolean interact(ServerPlayer actor,Entity target,Supplier<Collection<Presence>> observations){
        thread();if(closed||actor.level()!=level||target.level()!=level||!actor.isAlive()||actor.isSpectator()||target.isRemoved())return false;
        var k=hitKeys.get(target.getUUID());if(k==null)return false;
        var e=live.get(k);
        if(e==null||e.hit()!=target||level.getEntity(target.getUUID())!=target||actor.distanceToSqr(target)>16||!actor.hasLineOfSight(target))return false;
        var action=CaravanScenePlan.action(e.view(),rules.state(),actor.getUUID(),pendingDrops);
        if(action.isEmpty())return false;
        boolean ok=rules.act(k.leg(),actor.getUUID(),action.get(),k.crate(),observations.get());
        if(ok)sync();
        return ok;
    }
    public boolean owns(Entity target){thread();return target.level()==level&&hitKeys.containsKey(target.getUUID());}
    public boolean protects(Entity target){thread();return owns(target)||target.level()==level&&live.values().stream().anyMatch(v->v.parts().stream().anyMatch(p->p.display()==target));}
    public boolean currentClear(){thread();var p=rules.state().route().position();return safety.wagonStep(p,p);}
    public boolean qaGeometry(){
        thread();var state=rules.state();if(state.phase()!=CaravanMatchRules.Phase.LEG)return false;
        var w=state.route().position();double angle=rules.heading();
        var wagon=live.get(new Key(state.leg(),Kind.WAGON,0));
        if(wagon==null||wagon.parts().size()!=CaravanWagonGeometry.body().size())return false;
        for(var v:live.values())if(v.view().key().kind()==Kind.WAGON||v.view().key().kind()==Kind.WAGON_CARGO){
            for(var part:v.parts()){
                var g=part.geometry();var expected=CaravanWagonGeometry.offset(w,angle,g.x(),g.y(),g.z());
                var e=part.display();if(expected.distanceSquared(new Position(e.getX(),e.getY(),e.getZ()))>1e-8)return false;
                var tr=Display.createTransformation(e.getEntityData());
                var x=tr.getMatrix().transformPosition(new org.joml.Vector3f(1,0,0));
                if(Math.abs(x.x-g.sx()*Math.cos(angle))>1e-4||Math.abs(x.y)>1e-4||Math.abs(x.z-g.sx()*Math.sin(angle))>1e-4)return false;
            }
            var expected=CaravanWagonGeometry.hit(w,angle,v.view().key().kind()==Kind.WAGON_CARGO);var h=v.hit();
            if(h==null||expected.distanceSquared(new Position(h.getX(),h.getY(),h.getZ()))>1e-8)return false;
        }
        return true;
    }
    public int entityCount(){thread();return live.values().stream().mapToInt(e->e.parts().size()+(e.hit()==null?0:1)).sum();}
    @Override public void close(){thread();if(closed)return;closed=true;safeAnchors.clear();retryLanding.clear();pendingDrops=Set.of();for(var k:List.copyOf(live.keySet()))remove(k);}
}
