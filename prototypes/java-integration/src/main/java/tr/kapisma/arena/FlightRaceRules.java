package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;

/** Ordered forward plane crossing using successive server positions; teleports are never crossings. */
public final class FlightRaceRules {
    public record Gate(double x,double y,double z,double halfWidth,double halfHeight){
        public Gate{if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(halfWidth)||!Double.isFinite(halfHeight)||halfWidth<=0||halfHeight<=0)throw new IllegalArgumentException("invalid gate");}
    }
    public record Sample(UUID player,UUID match,Position position,boolean inArena,boolean gliding,boolean teleported){
        public Sample{Objects.requireNonNull(player);Objects.requireNonNull(match);Objects.requireNonNull(position);}
    }
    public record State(int elapsedTicks,boolean ended,Set<UUID> winners){}
    private record Previous(Position position,boolean gliding,int tick){}
    private final UUID match;private final Set<UUID> members;private final List<Gate> gates;
    private final Thread owner=Thread.currentThread();
    private final Map<UUID,Integer> progress=new HashMap<>();private final Map<UUID,Previous> previous=new HashMap<>();
    private int ticks;private boolean ended;private Set<UUID> winners=Set.of();
    public FlightRaceRules(UUID match,Collection<UUID> members,List<Gate> gates){
        this.match=Objects.requireNonNull(match);this.members=Set.copyOf(members);this.gates=List.copyOf(gates);
        if(this.members.size()!=members.size()||members.size()<2||members.size()>8||gates.size()!=4)throw new IllegalArgumentException("invalid race");
        for(int i=1;i<gates.size();i++)if(gates.get(i).x()<=gates.get(i-1).x())throw new IllegalArgumentException("gate order");
        for(var id:members)progress.put(id,0);
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public int progress(UUID id){thread();return progress.getOrDefault(id,-1);}
    public State state(){thread();return new State(ticks,ended,winners);}
    public void invalidate(UUID actor){thread();previous.remove(actor);}
    public static boolean crosses(Position a,Position b,Gate g){
        if(a.x()>=g.x()||b.x()<g.x()||b.x()<=a.x())return false;
        double t=(g.x()-a.x())/(b.x()-a.x());
        double y=a.y()+t*(b.y()-a.y()),z=a.z()+t*(b.z()-a.z());
        return Math.abs(y-g.y())<=g.halfHeight()&&Math.abs(z-g.z())<=g.halfWidth();
    }
    public State tick(Collection<Sample> observations){
        thread();if(ended)return state();ticks++;
        var seen=new HashMap<UUID,Integer>();
        for(var s:observations)if(members.contains(s.player())&&match.equals(s.match()))seen.merge(s.player(),1,Integer::sum);
        var finished=new HashSet<UUID>();
        for(var s:observations){
            UUID id=s.player();
            if(!members.contains(id)||!match.equals(s.match()))continue;
            if(seen.get(id)!=1||!s.inArena()||s.teleported()){previous.remove(id);continue;}
            var old=previous.put(id,new Previous(s.position(),s.gliding(),ticks));
            int index=progress.get(id);
            if(old==null||old.tick()!=ticks-1||!old.gliding()||!s.gliding()||
                old.position().distanceSquared(s.position())>144||index>=gates.size())continue;
            if(crosses(old.position(),s.position(),gates.get(index))){
                progress.put(id,index+1);if(index+1==gates.size())finished.add(id);
            }
        }
        if(!finished.isEmpty()||ticks==1800){ended=true;winners=Set.copyOf(finished);}
        return state();
    }
}
