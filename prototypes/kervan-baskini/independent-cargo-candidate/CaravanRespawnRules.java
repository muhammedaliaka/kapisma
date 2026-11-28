package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Side;
/** One server-owned 200-active-tick delay per death. No offline reset or duplicate death extension. */
public final class CaravanRespawnRules {
    public static final int DELAY=200;
    private final MatchRoster roster;
    private final Thread owner=Thread.currentThread();
    private final Map<UUID,Integer> pending=new HashMap<>();
    private long lastTick=-1;
    private boolean closed;
    public CaravanRespawnRules(MatchRoster roster){this.roster=Objects.requireNonNull(roster);}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public boolean death(UUID id){thread();return !closed&&roster.side(id)!=null&&pending.putIfAbsent(id,DELAY)==null;}
    public boolean waiting(UUID id){thread();return pending.containsKey(id);}
    public int remaining(UUID id){thread();return pending.getOrDefault(id,0);}
    /** Online IDs are authoritative roster connection status, NOT active combatant presence.
     * A dead spectator must still count as online or a 1v1 match could deadlock.
     * The timer continues while both teams have connected players; delivery of spawn waits for connection.
     */
    public List<UUID> tick(long tick,Collection<UUID> online,boolean matchActive){
        thread();if(tick<0)throw new IllegalArgumentException("tick");
        if(closed||tick<=lastTick)return List.of();
        lastTick=tick;Set<UUID> connected=new HashSet<>(online);
        boolean red=false,blue=false;
        for(UUID id:connected){red|=roster.side(id)==Side.RED;blue|=roster.side(id)==Side.BLUE;}
        if(!matchActive||!red||!blue)return List.of();
        pending.replaceAll((id,left)->Math.max(0,left-1));
        return pending.entrySet().stream().filter(e->e.getValue()==0&&connected.contains(e.getKey())).map(Map.Entry::getKey).sorted().toList();
    }
    /** Only acknowledge AFTER successful server-side spawn into own base. Failed spawn is retried. */
    public boolean acknowledge(UUID id){thread();return !closed&&Objects.equals(pending.get(id),0)&&pending.remove(id)!=null;}
    public Side base(UUID id){thread();return roster.side(id);}
    public void resetLeg(){thread();if(!closed)pending.clear();}
    public void end(){thread();closed=true;pending.clear();}
}
