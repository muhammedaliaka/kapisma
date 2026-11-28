package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
/** Server-owned grapple state. Adapter supplies verified anchors and applies collision-aware velocity. */
public final class CaravanGrappleRules {
    public static final int COOLDOWN=600,MAX_ACTIVE=200;
    public static final double MAX_RANGE=300;
    public record Motion(UUID player,Position anchor,Position acceleration){}
    private static final class Hook{
        final Position anchor;final long expires;long pulseUntil,lastPulse;
        Hook(Position anchor,long tick,boolean manual){this.anchor=anchor;expires=tick+MAX_ACTIVE;lastPulse=tick;pulseUntil=manual?tick+5:Long.MAX_VALUE;}
    }
    private final MatchRoster roster;
    private final Thread owner=Thread.currentThread();
    private final boolean manual;
    private final Map<UUID,Long> nextFire=new HashMap<>();
    private final Map<UUID,Hook> hooks=new HashMap<>();
    private Map<UUID,Presence> active=Map.of();
    private long tick=-1,lastAdvance=-1;
    private boolean running,ended;
    public CaravanGrappleRules(MatchRoster roster,boolean manual){this.roster=Objects.requireNonNull(roster);this.manual=manual;}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public boolean manual(){return manual;}
    public boolean attached(UUID actor){thread();return hooks.containsKey(actor);}
    public long remaining(UUID actor){thread();return Math.max(0,nextFire.getOrDefault(actor,0L)-Math.max(0,tick));}
    /** Called each real tick, and before interaction with authoritative filtered player observations. */
    public void observe(long now,Collection<Presence> observations,boolean matchRunning){
        thread();if(ended||now<tick||now<0)return;
        tick=now;running=matchRunning;
        var map=new HashMap<UUID,Presence>();for(var p:roster.active(observations))map.put(p.player(),p);
        active=Map.copyOf(map);
        hooks.keySet().removeIf(id->!running||!active.containsKey(id));
    }
    public boolean fire(UUID actor,Position anchor){
        thread();var p=active.get(actor);
        if(ended||!running||p==null||anchor==null||hooks.containsKey(actor)||remaining(actor)>0)return false;
        double d=p.position().distanceSquared(anchor);
        if(d<4||d>MAX_RANGE*MAX_RANGE)return false;
        hooks.put(actor,new Hook(anchor,tick,manual));nextFire.put(actor,tick+COOLDOWN);return true;
    }
    /** In manual mode, each deliberate press gives a short pull pulse. Spam is rate-limited. */
    public boolean pulse(UUID actor){
        thread();var h=hooks.get(actor);
        if(!manual||!running||h==null||!active.containsKey(actor)||tick-h.lastPulse<4)return false;
        h.lastPulse=tick;h.pulseUntil=tick+5;return true;
    }
    public List<Motion> advance(java.util.function.Predicate<Position> anchorValid){
        Objects.requireNonNull(anchorValid);return advance((id,anchor)->anchorValid.test(anchor));
    }
    public List<Motion> advance(java.util.function.BiPredicate<UUID,Position> ropeValid){
        thread();Objects.requireNonNull(ropeValid);
        if(ended||!running||tick<=lastAdvance)return List.of();lastAdvance=tick;
        var out=new ArrayList<Motion>();
        for(var entry:List.copyOf(hooks.entrySet())){
            UUID id=entry.getKey();Hook h=entry.getValue();Presence p=active.get(id);
            if(p==null||tick>=h.expires||!ropeValid.test(id,h.anchor)){hooks.remove(id);continue;}
            var pos=p.position();double dx=h.anchor.x()-pos.x(),dy=h.anchor.y()-pos.y(),dz=h.anchor.z()-pos.z();
            double d=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(d<=1.65||d>MAX_RANGE+1){hooks.remove(id);continue;}
            if(tick>=h.pulseUntil)continue;
            double strength=manual?.18:.085;
            out.add(new Motion(id,h.anchor,new Position(dx/d*strength,dy/d*strength,dz/d*strength)));
        }
        return List.copyOf(out);
    }
    /** Death, logout, dismount/arena transition: release rope but never reset cooldown. */
    public void cancel(UUID actor){thread();hooks.remove(actor);}
    public void clearActive(){thread();hooks.clear();active=Map.of();running=false;}
    public void end(){thread();ended=true;clearActive();nextFire.clear();}
}
