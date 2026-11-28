package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;

/** Single-leg fixed-speed wagon route, server-tick authoritative, no world writes. */
public final class CaravanRouteRules {
    public enum Route { SAFE, RISKY }
    public record State(Route route,UUID driver,Position position,double distance,double length,boolean arrived,boolean ready,boolean waitingAtJunction) {}
    private final MatchRoster roster;
    private final Side escort;
    private final Thread owner=Thread.currentThread();
    private final Map<Route,List<Position>> paths;
    private final Map<Route,Double> lengths;
    private final double speed,reachSq;
    private Map<UUID,Presence> active=Map.of();
    private Route route=Route.SAFE;
    private UUID driver;
    private Position position;
    private int segment;
    private double offset,distance,heading;
    public double heading(){thread();return heading;}
    private long lastTick=-1;
    private boolean arrived,ready,ended,waitingAtJunction;

    public CaravanRouteRules(MatchRoster roster,Side escort,List<Position> safe,List<Position> risky,double speed,double reach){
        this.roster=Objects.requireNonNull(roster);this.escort=Objects.requireNonNull(escort);
        if(!Double.isFinite(speed)||speed<=0||speed>1||!Double.isFinite(reach)||reach<=0||reach>8)
            throw new IllegalArgumentException("speed/reach");
        this.speed=speed;reachSq=reach*reach;
        safe=List.copyOf(safe);risky=List.copyOf(risky);
        double a=length(safe),b=length(risky);
        if(!safe.getFirst().equals(risky.getFirst())||!safe.getLast().equals(risky.getLast())||safe.equals(risky)||!(b<a))
            throw new IllegalArgumentException("routes must share endpoints; risky shorter");
        if(new HashSet<>(safe).size()!=safe.size()||new HashSet<>(risky).size()!=risky.size())throw new IllegalArgumentException("repeated node");
        var commonSafe=safe.stream().filter(risky::contains).toList();
        var commonRisk=risky.stream().filter(safe::contains).toList();
        if(!commonSafe.equals(commonRisk))throw new IllegalArgumentException("junction order");
        paths=Map.of(Route.SAFE,safe,Route.RISKY,risky);
        lengths=Map.of(Route.SAFE,a,Route.RISKY,b);position=safe.getFirst();heading=CaravanWagonGeometry.heading(position,safe.get(1));
    }
    private static double length(List<Position> path){
        if(path.size()<2||path.size()>512)throw new IllegalArgumentException("route size");
        double total=0;
        for(int i=1;i<path.size();i++){
            double n=Math.sqrt(path.get(i-1).distanceSquared(path.get(i)));
            if(!Double.isFinite(n)||n<=0||n>512)throw new IllegalArgumentException("route segment");
            total+=n;
        }
        if(total>10000)throw new IllegalArgumentException("route length");return total;
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    public State state(){thread();return new State(route,driver,position,distance,distance+remaining(),arrived,ready&&!ended,waitingAtJunction);}
    private double remaining(){
        var path=paths.get(route);double left=-offset;
        for(int i=segment+1;i<path.size();i++)left+=Math.sqrt(path.get(i-1).distanceSquared(path.get(i)));
        return Math.max(0,left);
    }
    private Route alternative(){return route==Route.SAFE?Route.RISKY:Route.SAFE;}
    private boolean junction(){
        if(offset!=0||arrived)return false;
        var current=paths.get(route);var other=paths.get(alternative());
        int i=other.indexOf(position);
        return i>=0&&i+1<other.size()&&segment+1<current.size()&&!current.get(segment+1).equals(other.get(i+1));
    }
    public List<Position> selectedPath(){thread();return paths.get(route);}
    /** Call once per actual server tick. Missed/paused ticks never accrue movement credit.
     * Adapter supplies observations and a server-validated driver movement intent only. */
    public State tick(long tick,Collection<Presence> observations,boolean forward){
        return tick(tick,observations,forward,roster.bothSides(roster.active(observations)));
    }
    public State tick(long tick,Collection<Presence> observations,boolean forward,boolean teamsConnected){
        return tick(tick,observations,forward,teamsConnected,(from,to)->true);
    }
    public State tick(long tick,Collection<Presence> observations,boolean forward,boolean teamsConnected,java.util.function.BiPredicate<Position,Position> clear){
        thread();Objects.requireNonNull(clear);if(tick<0)throw new IllegalArgumentException("negative tick");
        if(ended||arrived||tick<=lastTick)return state();
        lastTick=tick;observe(observations,teamsConnected);
        if(!ready||driver==null||!forward||active.get(driver).position().distanceSquared(position)>reachSq)return state();
        if(!waitingAtJunction)advance(clear);return state();
    }
    /** Refresh authoritative presence before an interaction without advancing time. */
    public void observe(Collection<Presence> observations){observe(observations,roster.bothSides(roster.active(observations)));}
    public void observe(Collection<Presence> observations,boolean teamsConnected){
        thread();if(ended||arrived)return;
        var present=roster.active(observations);
        var map=new HashMap<UUID,Presence>();for(var p:present)map.put(p.player(),p);active=Map.copyOf(map);
        ready=teamsConnected;
        if(driver!=null&&!active.containsKey(driver))driver=null;
    }
    public boolean claimDriver(UUID actor){
        thread();var p=active.get(actor);
        if(ended||arrived||!ready||driver!=null||p==null||roster.side(actor)!=escort||p.position().distanceSquared(position)>reachSq)return false;
        driver=actor;return true;
    }
    public boolean choose(UUID actor,Route chosen){
        thread();if(ended||arrived||!ready||driver==null||!driver.equals(actor)||chosen==null||!junction())return false;
        var p=active.get(actor);if(p==null||p.position().distanceSquared(position)>reachSq)return false;
        segment=paths.get(chosen).indexOf(position);
        route=chosen;waitingAtJunction=false;return true;
    }
    public boolean releaseDriver(UUID actor){thread();if(driver==null||!driver.equals(actor))return false;driver=null;return true;}
    /** Lifecycle hook must run before another action can be accepted. */
    public void invalidate(UUID actor){
        thread();if(ended)return;
        var map=new HashMap<>(active);map.remove(actor);active=Map.copyOf(map);
        ready=roster.bothSides(List.copyOf(active.values()));if(actor.equals(driver))driver=null;
    }
    private void advance(java.util.function.BiPredicate<Position,Position> clear){
        double step=speed;
        var path=paths.get(route);
        while(step>0&&segment<path.size()-1){
            var a=path.get(segment);var b=path.get(segment+1);
            double len=Math.sqrt(a.distanceSquared(b)),n=Math.min(step,len-offset);
            double next=offset+n,f=next/len;
            Position proposed=next>=len?b:new Position(a.x()+(b.x()-a.x())*f,a.y()+(b.y()-a.y())*f,a.z()+(b.z()-a.z())*f);
            if(!clear.test(position,proposed))break;
            if(n>1e-9)heading=CaravanWagonGeometry.heading(a,b);
            offset=next;distance+=n;step-=n;position=proposed;
            if(offset>=len){segment++;offset=0;if(junction()){waitingAtJunction=true;break;}}
        }
        if(segment==path.size()-1){arrived=true;ready=false;driver=null;waitingAtJunction=false;}
    }
    public void end(){thread();ended=true;ready=false;driver=null;active=Map.of();}
}
