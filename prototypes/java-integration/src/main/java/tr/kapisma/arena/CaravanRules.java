package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;

/** Two-leg escort: position/cargo never supplied by a client. */
public final class CaravanRules {
    public enum Phase { LEG, BREAK, ENDED }
    public record Leg(int distance,int cargo,int ticksLeft) implements Comparable<Leg>{
        public int compareTo(Leg other){
            int c=Integer.compare(distance,other.distance);
            if(c==0)c=Integer.compare(cargo,other.cargo);
            return c==0?Integer.compare(ticksLeft,other.ticksLeft):c;
        }
    }
    public record State(Phase phase,int leg,int distance,int cargo,int ticksLeft,int breakTicks,Leg red,Leg blue,Side winner){}
    private final MatchRoster roster;
    private final Thread owner=Thread.currentThread();
    private final Position start;
    private Phase phase=Phase.LEG;
    private int leg=1,distance,cargo=20,left=2400,breakTicks,remainder;
    private Leg red,blue;
    private Side winner;
    public CaravanRules(MatchRoster roster,Position start){this.roster=Objects.requireNonNull(roster);this.start=Objects.requireNonNull(start);}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public Side escort(){return leg==1?Side.RED:Side.BLUE;}
    public Position position(){thread();return new Position(start.x()+distance,start.y(),start.z());}
    public State state(){thread();return new State(phase,leg,distance,cargo,left,breakTicks,red,blue,winner);}
    /** During BREAK the adapter supplies registered online players even if they are temporarily spectators. */
    public State tick(Collection<Presence> observations){
        thread();if(phase==Phase.ENDED)return state();
        var active=roster.active(observations);
        if(!roster.bothSides(active))return state();
        if(phase==Phase.BREAK){
            if(--breakTicks==0){leg=2;phase=Phase.LEG;distance=0;cargo=20;left=2400;remainder=0;}
            return state();
        }
        boolean e=false,a=false;Position point=position();
        for(var p:active)if(p.position().distanceSquared(point)<=25){
            e|=roster.side(p.player())==escort();a|=roster.side(p.player())!=escort();
        }
        --left;
        if(++remainder==20){remainder=0;if(e&&!a)distance++;else if(a&&!e)cargo--;}
        if(distance==48||cargo==0||left==0){
            Leg result=new Leg(distance,cargo,left);
            if(leg==1){red=result;phase=Phase.BREAK;breakTicks=100;}
            else{blue=result;phase=Phase.ENDED;int c=red.compareTo(blue);winner=c==0?null:c>0?Side.RED:Side.BLUE;}
        }
        return state();
    }
}
