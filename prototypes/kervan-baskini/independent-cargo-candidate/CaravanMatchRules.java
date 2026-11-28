package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.CaravanRouteRules.Route;

/** Two-leg orchestration. Only authoritative adapter observations accepted.
 * Every leg has fresh cargo and route state. All state changes are server-thread only.
 */
public final class CaravanMatchRules {
    public enum Phase { LEG, BREAK, ENDED }
    public enum Action { DRIVE, LEAVE_DRIVER, SAFE, RISKY, TAKE, DROP, DELIVER }
    public record LegResult(boolean arrived,int secured,int progressBasisPoints,int remainingTicks,int delivered,int stolen) implements Comparable<LegResult>{
        public int compareTo(LegResult other){
            int c=Integer.compare(delivered,other.delivered);
            if(c==0)c=Integer.compare(other.stolen,stolen);
            if(c==0)c=Integer.compare(progressBasisPoints,other.progressBasisPoints);
            return c==0?Integer.compare(remainingTicks,other.remainingTicks):c;
        }
    }
    public enum Decision { DELIVERED, STOLEN, PROGRESS, TIME, DRAW }
    /** Same ordered criteria as LegResult.compareTo; presentation cannot change scoring. */
    public static Decision decision(LegResult red,LegResult blue){
        Objects.requireNonNull(red);Objects.requireNonNull(blue);
        if(red.delivered()!=blue.delivered())return Decision.DELIVERED;
        if(red.stolen()!=blue.stolen())return Decision.STOLEN;
        if(red.progressBasisPoints()!=blue.progressBasisPoints())return Decision.PROGRESS;
        if(red.remainingTicks()!=blue.remainingTicks())return Decision.TIME;
        return Decision.DRAW;
    }
    public static String reason(LegResult red,LegResult blue){return switch(decision(red,blue)){
        case DELIVERED->"Teslim edilen sandık sayısı";
        case STOLEN->"Daha az çalınan sandık";
        case PROGRESS->"Güzergâh ilerlemesi";
        case TIME->"Kalan süre";
        case DRAW->"Tüm ölçütler eşit";
    };}
    public static String resultLine(Side side,LegResult result){
        Objects.requireNonNull(side);Objects.requireNonNull(result);
        return (side==Side.RED?"Kırmızı":"Mavi")+" eskort: "+result.delivered()+" teslim · "+result.stolen()+" çalındı · %"+(result.progressBasisPoints()/100)+" ilerleme";
    }
    public record State(Phase phase,int leg,Side escort,int ticksLeft,int breakLeft,CaravanRouteRules.State route,
                        List<CaravanCargoRules.Crate> crates,LegResult red,LegResult blue,Side winner) {}
    private final Thread owner=Thread.currentThread();
    private final MatchRoster roster;
    private final List<Position> safe,risky;
    private final Position extraction;
    private final int count,legTicks,breakTicks;
    private final double speed,reach;
    private CaravanRouteRules route;
    private CaravanCargoRules cargo;
    private final CaravanRespawnRules respawns;
    private List<UUID> readyRespawns=List.of();
    private Phase phase=Phase.LEG;
    private int leg=1,left,intermission;
    private long lastTick=-1;
    private LegResult red,blue;
    private Side winner;
    public CaravanMatchRules(MatchRoster roster,List<Position> safe,List<Position> risky,Position extraction,
                             int count,int legTicks,int breakTicks,double speed,double reach){
        this.roster=Objects.requireNonNull(roster);this.respawns=new CaravanRespawnRules(roster);this.safe=List.copyOf(safe);this.risky=List.copyOf(risky);
        this.extraction=Objects.requireNonNull(extraction);
        if(legTicks<1||legTicks>72000||breakTicks<1||breakTicks>1200)throw new IllegalArgumentException("duration");
        this.count=count;this.legTicks=legTicks;this.breakTicks=breakTicks;this.speed=speed;this.reach=reach;begin();
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("wrong thread");}
    private Side escort(){return leg==1?Side.RED:Side.BLUE;}
    private void begin(){
        phase=Phase.LEG;left=legTicks;intermission=0;
        route=new CaravanRouteRules(roster,escort(),safe,risky,speed,reach);
        cargo=new CaravanCargoRules(roster,escort(),safe.getFirst(),extraction,count,reach);
    }
    public State state(){thread();return new State(phase,leg,escort(),left,intermission,route.state(),cargo.snapshot(),red,blue,winner);}
    public boolean carrying(UUID id){thread();return phase==Phase.LEG&&cargo.carrying(id);}
    /** Adapter applies and removes a dedicated modifier; never overwrite base speed. */
    public double movementMultiplier(UUID id){thread();return carrying(id)?.65:1;}
    public List<Position> selectedPath(){thread();return route.selectedPath();}
    public double heading(){thread();return route.heading();}
    private void finishLeg(){
        var r=route.state();
        int delivered=r.arrived()?(int)cargo.snapshot().stream().filter(c->c.status()==CaravanCargoRules.Status.ON_WAGON).count():0;
        var result=new LegResult(r.arrived(),count-cargo.stolen(),r.arrived()?10000:(int)Math.floor(10000*r.distance()/r.length()),left,delivered,cargo.stolen());
        route.end();cargo.end();
        if(leg==1){red=result;phase=Phase.BREAK;intermission=breakTicks;}
        else{blue=result;phase=Phase.ENDED;respawns.end();readyRespawns=List.of();int cmp=red.compareTo(blue);winner=cmp==0?null:cmp>0?Side.RED:Side.BLUE;}
    }
    /** Only current driver input can move the wagon; another player cannot accelerate it. */
    public State tick(long tick,Collection<Presence> observations,UUID forwardActor){
        return tick(tick,observations,connected(observations),forwardActor);
    }
    private Set<UUID> connected(Collection<Presence> observations){
        var ids=new HashSet<UUID>();
        for(var p:observations)if(roster.match().equals(p.match())&&p.inArena()&&roster.side(p.player())!=null)ids.add(p.player());
        return ids;
    }
    private boolean bothConnected(Collection<UUID> ids){
        boolean red=false,blue=false;
        for(UUID id:ids){red|=roster.side(id)==Side.RED;blue|=roster.side(id)==Side.BLUE;}
        return red&&blue;
    }
    private List<Presence> alive(Collection<Presence> observations){
        return roster.active(observations).stream().filter(p->!respawns.waiting(p.player())).toList();
    }
    public State tick(long tick,Collection<Presence> observations,Collection<UUID> online,UUID forwardActor){
        return tick(tick,observations,online,forwardActor,(from,to)->true);
    }
    public State tick(long tick,Collection<Presence> observations,Collection<UUID> online,UUID forwardActor,java.util.function.BiPredicate<Position,Position> clear){
        thread();Objects.requireNonNull(clear);if(tick<0)throw new IllegalArgumentException("tick");
        if(phase==Phase.ENDED||tick<=lastTick)return state();
        lastTick=tick;var active=alive(observations).stream().filter(p->online.contains(p.player())).toList();boolean connected=bothConnected(online);
        readyRespawns=respawns.tick(tick,online,true);
        if(phase==Phase.BREAK){
            if(connected&&--intermission==0){leg=2;begin();}
            return state();
        }
        route.observe(active,connected);cargo.observe(active,route.state().position(),connected);
        if(!connected)return state();
        UUID driver=route.state().driver();
        boolean forward=driver!=null&&driver.equals(forwardActor)&&!cargo.carrying(driver);
        var r=route.tick(tick,active,forward,connected,clear);
        cargo.observe(active,r.position(),connected);--left;
        if(r.arrived()||left==0||cargo.stolen()==count)finishLeg();
        return state();
    }
    /** Expected leg prevents delayed commands from a preceding leg. Fresh snapshot is mandatory. */
    public boolean act(int expectedLeg,UUID actor,Action action,int crate,Collection<Presence> observations){
        thread();if(phase!=Phase.LEG||expectedLeg!=leg||actor==null||action==null)return false;
        var active=alive(observations);boolean connected=bothConnected(connected(observations));
        route.observe(active,connected);cargo.observe(active,route.state().position(),connected);
        if(!connected||active.stream().noneMatch(p->actor.equals(p.player())))return false;
        UUID driver=route.state().driver();
        boolean ok=switch(action){
            case DRIVE -> !cargo.carrying(actor)&&route.claimDriver(actor);
            case LEAVE_DRIVER -> route.releaseDriver(actor);
            case SAFE -> route.choose(actor,Route.SAFE);
            case RISKY -> route.choose(actor,Route.RISKY);
            case TAKE -> !actor.equals(driver)&&cargo.take(actor,crate);
            case DROP -> cargo.drop(actor);
            case DELIVER -> cargo.deliver(actor);
        };
        if(ok&&cargo.stolen()==count)finishLeg();
        return ok;
    }
    public boolean relocateDropped(int expectedLeg,int id,Position expected,Position safe){
        thread();return phase==Phase.LEG&&leg==expectedLeg&&cargo.relocateDropped(id,expected,safe);
    }
    /** Internal world safety recovery, not an Action or a client command. */
    public boolean restoreDropped(int expectedLeg,int id,Position expected,Position verifiedAnchor){
        thread();return phase==Phase.LEG&&leg==expectedLeg&&cargo.restoreDropped(id,expected,verifiedAnchor);
    }
    public void invalidate(UUID id){thread();readyRespawns=readyRespawns.stream().filter(x->!x.equals(id)).toList();if(phase==Phase.LEG){route.invalidate(id);cargo.invalidate(id);}}
    public boolean death(UUID id){thread();if(phase!=Phase.LEG||!respawns.death(id))return false;invalidate(id);return true;}
    public boolean waitingForRespawn(UUID id){thread();return respawns.waiting(id);}
    public int respawnRemaining(UUID id){thread();return respawns.remaining(id);}
    public List<UUID> readyRespawns(){thread();return readyRespawns.stream().filter(respawns::waiting).toList();}
    public boolean acknowledgeRespawn(UUID id){thread();return readyRespawns.contains(id)&&respawns.acknowledge(id);}
    public void stop(){thread();route.end();cargo.end();respawns.end();readyRespawns=List.of();phase=Phase.ENDED;}
}
