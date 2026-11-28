package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;

/** Independent candidate for one escort leg; no Minecraft or client authority. */
public final class CaravanCargoRules {
    public enum Status { ON_WAGON, CARRIED, DROPPED, STOLEN }
    public record Crate(int id, Status status, UUID carrier, Position position) {}
    private final MatchRoster roster;
    private final Side escort;
    private final Thread owner = Thread.currentThread();
    private final Map<Integer,Crate> crates = new LinkedHashMap<>();
    private final Map<UUID,Presence> active = new HashMap<>();
    private final Position extraction;
    private final double radiusSq;
    private Position wagon;
    private boolean ready, ended;

    public CaravanCargoRules(MatchRoster roster,Side escort,Position wagon,Position extraction,int count,double radius) {
        this.roster=Objects.requireNonNull(roster);
        this.escort=Objects.requireNonNull(escort);
        this.wagon=Objects.requireNonNull(wagon);
        this.extraction=Objects.requireNonNull(extraction);
        if(count<1||count>64||!Double.isFinite(radius)||radius<=0||radius>8||wagon.distanceSquared(extraction)<=4*radius*radius)
            throw new IllegalArgumentException("configuration");
        radiusSq=radius*radius;
        for(int i=1;i<=count;i++)crates.put(i,new Crate(i,Status.ON_WAGON,null,wagon));
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public List<Crate> snapshot(){thread();return List.copyOf(crates.values());}
    public int stolen(){thread();return (int)crates.values().stream().filter(c->c.status()==Status.STOLEN).count();}
    public boolean carrying(UUID id){thread();return crates.values().stream().anyMatch(c->c.status()==Status.CARRIED&&id.equals(c.carrier()));}
    public boolean ready(){thread();return ready&&!ended;}
    /** Only verified server observations. Absent or ambiguous carrier drops at last accepted location. */
    public void observe(Collection<Presence> observations,Position wagonPosition){
        observe(observations,wagonPosition,roster.bothSides(roster.active(observations)));
    }
    public void observe(Collection<Presence> observations,Position wagonPosition,boolean teamsConnected){
        thread();if(ended)return;
        wagon=Objects.requireNonNull(wagonPosition);
        var present=roster.active(observations);
        active.clear();for(var p:present)active.put(p.player(),p);
        ready=teamsConnected;
        crates.replaceAll((id,c)->switch(c.status()){
            case ON_WAGON -> new Crate(id,Status.ON_WAGON,null,wagon);
            case CARRIED -> {
                var p=active.get(c.carrier());
                yield p==null?new Crate(id,Status.DROPPED,null,c.position()):new Crate(id,Status.CARRIED,c.carrier(),p.position());
            }
            default -> c;
        });
    }
    private boolean near(Presence p,Position at){return p.position().distanceSquared(at)<=radiusSq;}
    /** Actor identity comes from server connection. No coordinates or quantities accepted from client. */
    public boolean take(UUID actor,int id){
        thread();var p=active.get(actor);var c=crates.get(id);
        if(!ready()||p==null||c==null||carrying(actor)||!near(p,c.position()))return false;
        if(c.status()!=Status.DROPPED&&c.status()!=Status.ON_WAGON)return false;
        if(c.status()==Status.ON_WAGON&&roster.side(actor)==escort)return false;
        crates.put(id,new Crate(id,Status.CARRIED,actor,p.position()));return true;
    }
    /** Carrier can voluntarily put its own crate down. */
    public boolean drop(UUID actor){
        thread();if(!ready()||!active.containsKey(actor))return false;
        return release(actor);
    }
    /** Server lifecycle hook; disconnect/death invalidates actor immediately, even during pause. */
    public boolean invalidate(UUID actor){
        thread();if(ended)return false;
        active.remove(actor);ready=roster.bothSides(List.copyOf(active.values()));
        return release(actor);
    }
    private boolean release(UUID actor){
        for(var c:crates.values())if(c.status()==Status.CARRIED&&actor.equals(c.carrier())){
            crates.put(c.id(),new Crate(c.id(),Status.DROPPED,null,c.position()));return true;
        }
        return false;
    }
    /** Attackers extract at their designated base; defenders recover by returning to wagon. */
    public boolean deliver(UUID actor){
        thread();var p=active.get(actor);
        if(!ready()||p==null)return false;
        boolean defender=roster.side(actor)==escort;
        if(!near(p,defender?wagon:extraction))return false;
        for(var c:crates.values())if(c.status()==Status.CARRIED&&actor.equals(c.carrier())){
            crates.put(c.id(),new Crate(c.id(),defender?Status.ON_WAGON:Status.STOLEN,null,defender?wagon:extraction));return true;
        }
        return false;
    }
    /** Server world adapter may move a dropped crate to a verified nearby landing cell.
     * Identity, carrier, delivery and score are unchanged; compare expected location first. */
    public boolean relocateDropped(int id,Position expected,Position safe){
        thread();var c=crates.get(id);
        if(ended||c==null||c.status()!=Status.DROPPED||!c.position().equals(expected)||safe==null||expected.distanceSquared(safe)>64)return false;
        crates.put(id,new Crate(id,Status.DROPPED,null,safe));return true;
    }
    /** Exceptional server-only recovery. Caller must revalidate the previously safe anchor.
     * Unlike nearby landing, this can cross the ordinary 8-block limit. Never credits delivery. */
    public boolean restoreDropped(int id,Position expected,Position verifiedAnchor){
        thread();var c=crates.get(id);
        if(ended||c==null||c.status()!=Status.DROPPED||!c.position().equals(expected)||verifiedAnchor==null)return false;
        crates.put(id,new Crate(id,Status.DROPPED,null,verifiedAnchor));return true;
    }
    public void end(){thread();if(ended)return;ended=true;ready=false;active.clear();
        crates.replaceAll((id,c)->c.status()==Status.CARRIED?new Crate(id,Status.DROPPED,null,c.position()):c);}
}
