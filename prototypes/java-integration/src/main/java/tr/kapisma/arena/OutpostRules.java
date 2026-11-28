package tr.kapisma.arena;

import java.util.*;
import tr.kapisma.arena.MatchRoster.*;

/** Server-thread-only deterministic rules. A tick represents one accepted server tick, never wall time. */
public final class OutpostRules {
    public record State(UUID match,int point,int leftTicks,int red,int blue,int remainder,Side winner) {}
    public record Update(State state,boolean paused,boolean warning,boolean rotated,boolean contested) {}
    private final MatchRoster roster;
    private final Thread owner=Thread.currentThread();
    private final List<Position> points;
    private final int rotationTicks,warningTicks,goal;
    private int point,left,red,blue,remainder;
    private Side winner;
    public OutpostRules(MatchRoster roster,List<Position> points,int rotationSeconds,int warningSeconds,int goal) {
        this.roster=Objects.requireNonNull(roster);
        this.points=List.copyOf(points);
        if(points.size()<2||points.size()>16||new HashSet<>(points).size()!=points.size()||
           rotationSeconds<2||rotationSeconds>3600||warningSeconds<1||warningSeconds>=rotationSeconds||goal<1||goal>10000)
            throw new IllegalArgumentException("invalid rules");
        this.rotationTicks=rotationSeconds*20;this.warningTicks=warningSeconds*20;this.goal=goal;
        left=rotationTicks;
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public State snapshot(){thread();return new State(roster.match(),point,left,red,blue,remainder,winner);}
    public void restore(State s){
        thread();Objects.requireNonNull(s);
        if(!roster.match().equals(s.match())||s.point()<0||s.point()>=points.size()||
           s.leftTicks()<1||s.leftTicks()>rotationTicks||s.remainder()<0||s.remainder()>=20||
           s.red()<0||s.red()>goal||s.blue()<0||s.blue()>goal||
           (s.winner()==Side.RED)!=(s.red()==goal)||(s.winner()==Side.BLUE)!=(s.blue()==goal)||
           (s.winner()==null && s.remainder()!=(rotationTicks-s.leftTicks())%20)||
           (s.winner()!=null && s.remainder()!=0))
            throw new IllegalArgumentException("invalid checkpoint");
        point=s.point();left=s.leftTicks();red=s.red();blue=s.blue();remainder=s.remainder();winner=s.winner();
    }
    public Update tick(Collection<Presence> samples) {
        thread();
        if(winner!=null)return new Update(snapshot(),false,false,false,false);
        var active=roster.active(samples);
        if(!roster.bothSides(active))return new Update(snapshot(),true,false,false,false);
        boolean a=false,b=false;
        Position center=points.get(point);
        for(var p:active){
            var pos=p.position();
            // Feet within the gold floor's 9x9 horizontal area, up to 3 blocks tall.
            if(Math.abs(pos.x()-center.x())>=4.5||Math.abs(pos.z()-center.z())>=4.5||
                pos.y()<center.y()||pos.y()>=center.y()+3)continue;
            a|=roster.side(p.player())==Side.RED;b|=roster.side(p.player())==Side.BLUE;
        }
        if(++remainder==20){
            remainder=0;
            if(a&&!b&&++red==goal)winner=Side.RED;
            if(b&&!a&&++blue==goal)winner=Side.BLUE;
        }
        if(winner!=null)return new Update(snapshot(),false,false,false,a&&b);
        --left;
        boolean warn=left==warningTicks,rotate=left==0;
        if(rotate){point=(point+1)%points.size();left=rotationTicks;}
        return new Update(snapshot(),false,warn,rotate,a&&b);
    }
}
