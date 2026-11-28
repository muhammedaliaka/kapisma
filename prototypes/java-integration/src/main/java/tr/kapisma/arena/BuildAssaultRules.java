package tr.kapisma.arena;
import java.util.*;
import java.util.function.BooleanSupplier;
import tr.kapisma.arena.MatchRoster.*;

/** Logical block ledger. Adapter must apply world block changes only after authorized server events. */
public final class BuildAssaultRules {
    public enum Phase { BUILD, ASSAULT, ENDED }
    public record Cell(int x,int y,int z){}
    public record State(Phase phase,int leg,int ticksLeft,int red,int blue,Side winner){}
    private final MatchRoster roster;private final Thread owner=Thread.currentThread();
    private final Map<UUID,Integer> supply=new HashMap<>();
    private final Set<Cell> placed=new HashSet<>();
    private Phase phase=Phase.BUILD;private int leg=1,left=600,red,blue;private Side winner;
    private boolean ready;
    public BuildAssaultRules(MatchRoster roster){this.roster=Objects.requireNonNull(roster);refill();}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public Side defender(){return leg==1?Side.RED:Side.BLUE;}
    private void refill(){
        supply.clear();placed.clear();
        var defenders=roster.members().stream().filter(id->roster.side(id)==defender()).sorted().toList();
        int smaller=Math.min(defenders.size(),roster.members().size()-defenders.size());
        int budget=128*smaller,each=budget/defenders.size(),extra=budget%defenders.size();
        for(int i=0;i<defenders.size();i++)supply.put(defenders.get(i),each+(i<extra?1:0));
    }
    public State state(){thread();return new State(phase,leg,left,red,blue,winner);}
    public int supply(UUID actor){thread();return supply.getOrDefault(actor,0);}
    public boolean inBuild(Cell c){return c.x()>=-10&&c.x()<=10&&c.y()>=0&&c.y()<=6&&c.z()>=-6&&c.z()<=6&&!c.equals(new Cell(0,0,0));}
    private boolean permitted(Presence actor,Side side){return ready&&roster.accepts(actor)&&roster.side(actor.player())==side;}
    /** Adapter provides a server-side world mutation, not a client-supplied success flag. */
    public boolean place(Presence actor,Cell cell,BooleanSupplier apply){
        thread();Objects.requireNonNull(apply);
        if(phase!=Phase.BUILD||!permitted(actor,defender())||!inBuild(cell)||supply(actor.player())==0||placed.contains(cell))return false;
        if(!apply.getAsBoolean())return false;
        placed.add(cell);supply.compute(actor.player(),(k,v)->v-1);return true;
    }
    public boolean breakWall(Presence actor,Cell cell,BooleanSupplier apply){
        thread();Objects.requireNonNull(apply);
        if(phase!=Phase.ASSAULT||!permitted(actor,opponent())||!placed.contains(cell)||!apply.getAsBoolean())return false;
        return placed.remove(cell);
    }
    public void invalidateReadiness(){thread();ready=false;}
    private Side opponent(){return defender()==Side.RED?Side.BLUE:Side.RED;}
    public boolean capture(Presence actor,Cell cell,BooleanSupplier apply){
        thread();Objects.requireNonNull(apply);
        if(phase!=Phase.ASSAULT||!permitted(actor,opponent())||!cell.equals(new Cell(0,0,0))||!apply.getAsBoolean())return false;
        endLeg(left+1);return true;
    }
    private void endLeg(int score){
        if(leg==1){blue=score;leg=2;phase=Phase.BUILD;left=600;refill();}
        else{red=score;phase=Phase.ENDED;winner=red==blue?null:red>blue?Side.RED:Side.BLUE;}
        ready=false;
    }
    public State tick(Collection<Presence> observations){
        thread();if(phase==Phase.ENDED)return state();
        ready=roster.bothSides(roster.active(observations));
        if(!ready)return state();
        if(--left==0){
            if(phase==Phase.BUILD){phase=Phase.ASSAULT;left=1800;}
            else endLeg(0);
        }
        return state();
    }
}