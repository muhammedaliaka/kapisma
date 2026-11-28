package tr.kapisma.arena;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Server-tick flight boost authorization, independent of the race checkpoint ledger.
 * Unlimited rockets, one accepted rocket per 200 active ticks. Adapter owns actual motion,
 * server-side gliding state, arena identity and pad collision validation.
 */
public final class FlightBoostRules {
    public static final int ROCKET_COOLDOWN=200;
    public enum PadKind { SPEED, LIFT }
    public record Observation(UUID player,UUID match,boolean inArena,boolean active,boolean gliding) {
        public Observation {Objects.requireNonNull(player);Objects.requireNonNull(match);}
    }
    public record Pad(String id,PadKind kind,int cooldownTicks) {
        public Pad {
            Objects.requireNonNull(id);Objects.requireNonNull(kind);
            if(id.isBlank()||id.length()>80||cooldownTicks<1||cooldownTicks>12000)throw new IllegalArgumentException("pad");
        }
    }
    private final Thread owner=Thread.currentThread();
    private final UUID match;
    private final Set<UUID> members;
    private final Map<String,Pad> pads;
    private final Map<UUID,Long> lastRocket=new HashMap<>();
    private final Map<UUID,Map<String,Long>> lastPad=new HashMap<>();
    private Map<UUID,Observation> active=Map.of();
    private long tick;
    private boolean running,ended,applying;
    public FlightBoostRules(UUID match,Collection<UUID> members,Collection<Pad> pads){
        this.match=Objects.requireNonNull(match);
        this.members=Set.copyOf(members);
        if(this.members.size()!=members.size()||this.members.size()<2||this.members.size()>8)throw new IllegalArgumentException("roster");
        var map=new HashMap<String,Pad>();
        for(Pad p:pads)if(map.putIfAbsent(p.id,p)!=null)throw new IllegalArgumentException("duplicate pad");
        if(map.size()>128)throw new IllegalArgumentException("too many pads");
        this.pads=Map.copyOf(map);
    }
    private void thread(){if(Thread.currentThread()!=owner||applying)throw new IllegalStateException("wrong thread or reentry");}
    public void tick(boolean raceRunning,Collection<Observation> samples){
        thread();if(ended)return;
        running=raceRunning;
        if(!running){active=Map.of();return;}
        tick++;
        var map=new HashMap<UUID,Observation>();var duplicate=new HashSet<UUID>();
        for(Observation o:samples)if(match.equals(o.match)&&members.contains(o.player)&&o.inArena&&o.active){
            if(map.putIfAbsent(o.player,o)!=null)duplicate.add(o.player);
        }
        duplicate.forEach(map::remove);active=Map.copyOf(map);
    }
    private boolean permitted(UUID id,UUID epoch,boolean requireGliding){
        Observation o=active.get(id);
        return running&&!ended&&match.equals(epoch)&&o!=null&&(!requireGliding||o.gliding);
    }
    public int remaining(UUID id){
        thread();if(!members.contains(id))return -1;
        Long last=lastRocket.get(id);return last==null?0:(int)Math.max(0,ROCKET_COOLDOWN-(tick-last));
    }
    private boolean apply(BooleanSupplier action){Objects.requireNonNull(action);applying=true;try{return action.getAsBoolean();}finally{applying=false;}}
    public boolean rocket(UUID id,UUID epoch,BooleanSupplier serverBoost){
        thread();if(!permitted(id,epoch,true)||remaining(id)!=0||!apply(serverBoost))return false;
        lastRocket.put(id,tick);return true;
    }
    /** The adapter calls this only after validating contact with the actual pad geometry.
     * Pad cooldown is independent: it neither resets nor consumes the rocket timer.
     */
    public boolean pad(UUID id,UUID epoch,String padId,BooleanSupplier serverBoost){
        thread();Pad pad=pads.get(padId);if(pad==null||!permitted(id,epoch,pad.kind==PadKind.SPEED))return false;
        Map<String,Long> previous=lastPad.get(id);Long last=previous==null?null:previous.get(padId);
        if(last!=null&&tick-last<pad.cooldownTicks||!apply(serverBoost))return false;
        lastPad.computeIfAbsent(id,k->new HashMap<>()).put(padId,tick);return true;
    }
    /** Called immediately on teleport/checkpoint, death, quit: new observation required; no timer reset. */
    public void invalidate(UUID id){thread();var next=new HashMap<>(active);next.remove(id);active=Map.copyOf(next);}
    public void end(){thread();ended=true;running=false;active=Map.of();}
}
