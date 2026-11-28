package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;

/** Server-confirmed team clue sequence. All candidates in one tick resolve together. */
public final class ClueRaceRules {
    public record Clue(String label,Position target){public Clue{Objects.requireNonNull(label);Objects.requireNonNull(target);}}
    public record State(int red,int blue,int ticksLeft,boolean ended,Side winner){}
    private final MatchRoster roster;
    private final Thread owner=Thread.currentThread();
    private final EnumMap<Side,List<Clue>> routes=new EnumMap<>(Side.class);
    private final Map<UUID,Integer> pending=new HashMap<>();
    private int red,blue,left=3600;private boolean ended;private Side winner;
    public ClueRaceRules(MatchRoster roster,List<Clue> red,List<Clue> blue){
        this.roster=Objects.requireNonNull(roster);
        if(red.size()!=4||blue.size()!=4||new HashSet<>(red.stream().map(Clue::target).toList()).size()!=4||
            new HashSet<>(blue.stream().map(Clue::target).toList()).size()!=4)throw new IllegalArgumentException("four distinct targets per team");
        routes.put(Side.RED,List.copyOf(red));routes.put(Side.BLUE,List.copyOf(blue));
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    private int stage(Side side){return side==Side.RED?red:blue;}
    public State state(){thread();return new State(red,blue,left,ended,winner);}
    public Optional<Clue> hint(UUID actor){
        thread();Side side=roster.side(actor);
        return ended||side==null?Optional.empty():Optional.of(routes.get(side).get(stage(side)));
    }
    public boolean request(UUID actor,UUID match,int expectedStage){
        thread();Side side=roster.side(actor);
        if(ended||!roster.match().equals(match)||side==null||stage(side)!=expectedStage)return false;
        return pending.putIfAbsent(actor,expectedStage)==null;
    }
    public void discardPending(){thread();pending.clear();}
    public State tick(Collection<Presence> observations){
        thread();if(ended){pending.clear();return state();}
        var active=roster.active(observations);
        if(!roster.bothSides(active)){pending.clear();return state();}
        boolean r=false,b=false;
        for(var p:active){
            Side side=roster.side(p.player());int stage=stage(side);
            if(Objects.equals(pending.get(p.player()),stage)&&p.position().distanceSquared(routes.get(side).get(stage).target())<=9){
                r|=side==Side.RED;b|=side==Side.BLUE;
            }
        }
        pending.clear();if(r)red++;if(b)blue++;left--;
        if(red==4||blue==4||left==0){ended=true;winner=red==blue?null:red>blue?Side.RED:Side.BLUE;}
        return state();
    }
}