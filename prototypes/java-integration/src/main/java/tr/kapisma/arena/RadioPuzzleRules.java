package tr.kapisma.arena;
import java.util.*;
import java.util.random.RandomGenerator;
import tr.kapisma.arena.MatchRoster.*;

/** Private clues, shared attempt budget and cooldown. Server event actor must be authenticated. */
public final class RadioPuzzleRules {
    public enum Phase { ACTIVE, WON, LOST }
    public record State(Phase phase,int round,int ticksLeft,int attempts,int cooldown){}
    public record Hint(int round,Side role,int fragment){}
    private record Guess(UUID actor,int round,int code){}
    private final MatchRoster roster;
    private final RandomGenerator random;
    private final Thread owner=Thread.currentThread();
    private final LinkedHashMap<UUID,Guess> pending=new LinkedHashMap<>();
    private int round=1,left=1800,attempts=3,cooldown,red,blue;
    private Phase phase=Phase.ACTIVE;
    public RadioPuzzleRules(MatchRoster roster,RandomGenerator random){
        this.roster=Objects.requireNonNull(roster);this.random=Objects.requireNonNull(random);newCode();
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    private void newCode(){red=random.nextInt(10,100);blue=random.nextInt(10,100);}
    public State state(){thread();return new State(phase,round,left,attempts,cooldown);}
    public Optional<Hint> hint(UUID actor){
        thread();Side s=roster.side(actor);
        return phase!=Phase.ACTIVE||s==null?Optional.empty():Optional.of(new Hint(round,s,s==Side.RED?red:blue));
    }
    public boolean submit(UUID actor,UUID match,int expectedRound,int code){
        thread();
        if(phase!=Phase.ACTIVE||!roster.match().equals(match)||roster.side(actor)==null||expectedRound!=round||
            code<1000||code>9999||cooldown>0)return false;
        return pending.putIfAbsent(actor,new Guess(actor,expectedRound,code))==null;
    }
    public void discardPending(){thread();pending.clear();}
    public State tick(Collection<Presence> observations){
        thread();if(phase!=Phase.ACTIVE){pending.clear();return state();}
        var active=roster.active(observations);
        if(!roster.bothSides(active)){pending.clear();return state();}
        if(cooldown>0)cooldown--;
        if(cooldown==0&&!pending.isEmpty()){
            var ids=new HashSet<UUID>();for(var p:active)ids.add(p.player());
            Guess g=pending.values().stream().filter(x->x.round()==round&&ids.contains(x.actor())).findFirst().orElse(null);
            pending.clear();
            if(g!=null){
                if(g.code()==red*100+blue){
                    if(round==3)phase=Phase.WON;
                    else{round++;left=1800;attempts=3;cooldown=0;newCode();}
                    return state(); // do not decrement the new round on the old round's final tick
                }
                attempts--;cooldown=40;
                if(attempts==0){phase=Phase.LOST;return state();}
            }
        }else pending.clear();
        if(--left==0)phase=Phase.LOST;
        return state();
    }
}