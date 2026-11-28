package tr.kapisma.arena;
import java.util.*;
import java.util.random.RandomGenerator;

/** Match-only currency. Public state never contains bids or balances. */
public final class AuctionRules {
    public enum Phase { AUCTION, COMBAT, ENDED }
    public record Award(int round,UUID winner){}
    public record State(Phase phase,int round,int ticksLeft,int alive,Optional<Award> lastAward,UUID winner){}
    public record Observation(UUID player,UUID match,boolean inArena,boolean connected){
        public Observation{Objects.requireNonNull(player);Objects.requireNonNull(match);}
    }
    private final UUID match;private final Set<UUID> members;
    private final Set<UUID> alive;
    private final Map<UUID,Integer> coins=new HashMap<>(),offers=new HashMap<>();
    private final Map<UUID,Integer> pending=new LinkedHashMap<>();
    private final Set<UUID> deaths=new HashSet<>();
    private final RandomGenerator random;private final Thread owner=Thread.currentThread();
    private Phase phase=Phase.AUCTION;private int round=1,left=400;
    private Award lastAward;private UUID winner;
    public AuctionRules(UUID match,Collection<UUID> participants,RandomGenerator random){
        this.match=Objects.requireNonNull(match);members=Set.copyOf(participants);this.random=Objects.requireNonNull(random);
        if(members.size()!=participants.size()||members.size()<2||members.size()>8)throw new IllegalArgumentException("invalid roster");
        alive=new HashSet<>(members);for(var id:members)coins.put(id,100);
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public State state(){thread();return new State(phase,round,left,alive.size(),Optional.ofNullable(lastAward),winner);}
    /** Server adapter must derive actor from authenticated session; no arbitrary UUID from UI. */
    public OptionalInt balance(UUID actor){thread();return members.contains(actor)?OptionalInt.of(coins.get(actor)):OptionalInt.empty();}
    public OptionalInt offer(UUID actor){thread();return members.contains(actor)?OptionalInt.of(offers.getOrDefault(actor,0)):OptionalInt.empty();}
    public boolean bid(UUID actor,UUID matchId,int expectedRound,int amount){
        thread();if(phase!=Phase.AUCTION||!match.equals(matchId)||!members.contains(actor)||expectedRound!=round||
            (amount!=-1&&(amount<1||amount>coins.get(actor))))return false;
        pending.put(actor,amount);return true;
    }
    public boolean death(UUID actor,UUID matchId){
        thread();return phase==Phase.COMBAT&&match.equals(matchId)&&alive.contains(actor)&&deaths.add(actor);
    }
    private Set<UUID> online(Collection<Observation> observations){
        var count=new HashMap<UUID,Integer>();
        for(var o:observations)if(match.equals(o.match())&&members.contains(o.player())&&o.inArena()&&o.connected())
            count.merge(o.player(),1,Integer::sum);
        var result=new HashSet<UUID>();count.forEach((id,n)->{if(n==1)result.add(id);});return result;
    }
    public void discardPending(){thread();pending.clear();}
    public State tick(Collection<Observation> observations){
        thread();if(phase==Phase.ENDED)return state();
        Set<UUID> online=online(observations);
        if(phase==Phase.AUCTION){
            if(!online.containsAll(members)){pending.clear();return state();}
            pending.forEach((id,n)->{if(n==-1)offers.remove(id);else offers.put(id,n);});
            pending.clear();
            if(--left==0)resolve();
        }else{
            // Death callbacks are server-observed, may precede the disconnect in the same tick.
            alive.removeAll(deaths);deaths.clear();
            if(alive.isEmpty()||(alive.size()==1&&online.containsAll(alive))){
                phase=Phase.ENDED;winner=alive.isEmpty()?null:alive.iterator().next();return state();
            }
            if(online.containsAll(alive)&&--left==0){phase=Phase.ENDED;winner=null;}
        }
        return state();
    }
    private void resolve(){
        int max=offers.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        UUID win=null;
        if(max>0){
            var tied=offers.entrySet().stream().filter(e->e.getValue()==max).map(Map.Entry::getKey).sorted().toList();
            win=tied.get(random.nextInt(tied.size()));
            coins.put(win,coins.get(win)-max);
        }
        lastAward=new Award(round,win);offers.clear();pending.clear();
        if(++round==4){phase=Phase.COMBAT;left=1800;}
        else left=400;
    }
}