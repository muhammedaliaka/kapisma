package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
import tr.kapisma.arena.CaravanCargoRules.Status;
/** Deterministic projection of authoritative cargo state into non-inventory world entities.
 * Keys contain the leg, preventing a removed entity from targeting a fresh crate next leg.
 */
public final class CaravanScenePlan {
    public enum Kind { WAGON, WAGON_CARGO, DROPPED, CARRIED }
    public record Key(int leg,Kind kind,int crate){}
    public record View(Key key,Position position,UUID carrier,boolean clickable){}
    private CaravanScenePlan(){}
    public static Map<Key,View> project(CaravanMatchRules.State state){return project(state,Set.of());}
    public static Map<Key,View> project(CaravanMatchRules.State state,Set<Integer> pending){
        Objects.requireNonNull(pending);
        if(state.phase()!=CaravanMatchRules.Phase.LEG)return Map.of();
        var out=new LinkedHashMap<Key,View>();
        var wagon=state.route().position();
        add(out,new Key(state.leg(),Kind.WAGON,0),wagon,null,true);
        // One wagon cargo target for the first unclaimed crate, not 20 overlapping hitboxes.
        state.crates().stream().filter(c->c.status()==Status.ON_WAGON).findFirst()
            .ifPresent(c->add(out,new Key(state.leg(),Kind.WAGON_CARGO,c.id()),wagon,null,true));
        // Use the lowest ID of directly overlapping 0.85-wide, 1-high interaction boxes.
        // Other identities remain in the ledger and are exposed on subsequent pickups.
        var visibleDrops=new ArrayList<Position>();
        for(var c:state.crates().stream().sorted(Comparator.comparingInt(CaravanCargoRules.Crate::id)).toList()){
            if(c.status()==Status.DROPPED&&!pending.contains(c.id())&&visibleDrops.stream().noneMatch(p->overlaps(p,c.position()))){
                visibleDrops.add(c.position());add(out,new Key(state.leg(),Kind.DROPPED,c.id()),c.position(),null,true);
            }
            if(c.status()==Status.CARRIED)add(out,new Key(state.leg(),Kind.CARRIED,c.id()),c.position(),c.carrier(),false);
        }
        return Collections.unmodifiableMap(out);
    }
    static boolean overlaps(Position a,Position b){return Math.abs(a.x()-b.x())<.85&&Math.abs(a.z()-b.z())<.85&&Math.abs(a.y()-b.y())<1;}
    private static void add(Map<Key,View> out,Key key,Position p,UUID carrier,boolean clickable){
        if(out.putIfAbsent(key,new View(key,p,carrier,clickable))!=null)throw new IllegalArgumentException("duplicate scene identity");
    }
    public static Optional<CaravanMatchRules.Action> action(View view,CaravanMatchRules.State state,UUID actor){
        return action(view,state,actor,Set.of());
    }
    public static Optional<CaravanMatchRules.Action> action(View view,CaravanMatchRules.State state,UUID actor,Set<Integer> pending){
        if(view==null||actor==null||!view.clickable()||!Objects.equals(project(state,pending).get(view.key()),view))return Optional.empty();
        return switch(view.key().kind()){
            case WAGON -> Optional.of(actor.equals(state.route().driver())?CaravanMatchRules.Action.LEAVE_DRIVER:CaravanMatchRules.Action.DRIVE);
            case WAGON_CARGO,DROPPED -> Optional.of(CaravanMatchRules.Action.TAKE);
            case CARRIED -> Optional.empty();
        };
    }
}
