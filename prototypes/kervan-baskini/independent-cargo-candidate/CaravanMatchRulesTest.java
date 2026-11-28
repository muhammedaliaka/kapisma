package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.CaravanMatchRules.*;
import static tr.kapisma.arena.CaravanMatchRules.*;
public final class CaravanMatchRulesTest {
    static final UUID MATCH=new UUID(0,51),R=new UUID(0,52),B=new UUID(0,53);
    static final Position S=new Position(0,0,0),E=new Position(4,0,0),X=new Position(20,0,0);
    static final MatchRoster roster=new MatchRoster(MATCH,List.of(R),List.of(B));
    static final List<Position> SAFE=List.of(S,new Position(0,0,2),new Position(4,0,2),E),RISKY=List.of(S,E);
    static int passed;
    static Presence p(UUID id,Position pos){return new Presence(id,MATCH,true,true,pos);}
    static List<Presence> both(Position r,Position b){return List.of(p(R,r),p(B,b));}
    static CaravanMatchRules fresh(int cargo,int time){return new CaravanMatchRules(roster,SAFE,RISKY,X,cargo,time,2,.5,2);}
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    static boolean act(CaravanMatchRules g,int leg,UUID who,Action a,int item,Position r,Position b){return g.act(leg,who,a,item,both(r,b));}
    public static void main(String[] args){
        var g=fresh(2,20);
        check(g.state().escort()==Side.RED&&g.state().leg()==1,"red first escort");
        check(!act(g,1,B,Action.DRIVE,0,S,S),"attacker cannot drive");
        check(!act(g,1,R,Action.DRIVE,0,X,S),"remote driver denied");
        check(act(g,1,R,Action.DRIVE,0,S,S),"fresh observations permit driver");
        check(act(g,1,R,Action.RISKY,0,S,S),"driver selects risky");
        check(!act(g,1,R,Action.TAKE,1,S,S),"driver cannot carry");
        check(act(g,1,B,Action.TAKE,1,S,S)&&g.movementMultiplier(B)==.65,"cargo movement penalty");
        check(!act(g,1,B,Action.TAKE,2,S,S),"one carried crate");
        g.tick(1,both(S,S),B);check(g.state().route().distance()==0,"attacker forward ignored");
        g.tick(2,both(S,S),R);check(g.state().route().distance()==.5,"driver moves");
        check(!act(g,1,B,Action.DELIVER,1,S,S),"cannot extract remotely");
        check(act(g,1,B,Action.DELIVER,1,S,X)&&g.movementMultiplier(B)==1,"extraction removes speed penalty");
        int left=g.state().ticksLeft();g.tick(2,both(S,X),R);
        check(g.state().ticksLeft()==left,"duplicate tick consumes no time");
        g.tick(3,List.of(p(R,S)),R);check(g.state().ticksLeft()==left,"pause freezes timer");
        check(!g.act(1,R,Action.LEAVE_DRIVER,0,List.of(p(R,S))),"pause rejects interaction");
        for(int t=4;t<=10;t++)g.tick(t,both(g.state().route().position(),X),R);
        check(g.state().phase()==Phase.BREAK&&g.state().red().arrived()&&g.state().red().secured()==1,"first leg ends by arrival");
        check(!g.carrying(B)&&g.movementMultiplier(B)==1,"break no carrying");
        check(!act(g,1,R,Action.DRIVE,0,S,S),"break rejects action");
        g.tick(11,List.of(p(B,S)),B);check(g.state().breakLeft()==2,"break pauses");
        g.tick(12,both(S,S),null);g.tick(13,both(S,S),null);
        check(g.state().leg()==2&&g.state().escort()==Side.BLUE&&g.state().phase()==Phase.LEG,"roles swap");
        check(g.state().crates().stream().allMatch(c->c.status()==CaravanCargoRules.Status.ON_WAGON),"fresh cargo next leg");
        check(!act(g,1,R,Action.TAKE,1,S,S),"old leg packet rejected");
        check(act(g,2,B,Action.DRIVE,0,S,S)&&act(g,2,B,Action.RISKY,0,S,S),"blue drives second leg");
        for(int t=14;t<=21;t++)g.tick(t,both(X,g.state().route().position()),B);
        check(g.state().phase()==Phase.ENDED&&g.state().winner()==Side.BLUE,"both arrive intact cargo wins");
        check(!act(g,2,R,Action.TAKE,1,S,S),"ended denies commands");
        var h=fresh(1,2);
        check(act(h,1,B,Action.TAKE,1,S,S),"one crate fixture");
        h.invalidate(B);check(!h.carrying(B)&&h.state().crates().getFirst().status()==CaravanCargoRules.Status.DROPPED,"disconnect release");
        check(!act(h,1,B,Action.TAKE,1,S,X),"fresh distant snapshot rejects stale pickup");
        check(act(h,1,B,Action.TAKE,1,S,S),"fresh nearby pickup");
        check(act(h,1,B,Action.DELIVER,1,S,X)&&h.state().phase()==Phase.BREAK,"all cargo stolen ends leg");
        h.tick(1,both(S,S),null);h.tick(2,both(S,S),null);
        h.tick(3,both(S,S),null);h.tick(4,both(S,S),null);
        check(h.state().phase()==Phase.ENDED&&h.state().winner()==Side.BLUE,"timeout score");
        var tie=fresh(2,1);tie.tick(1,both(S,S),null);tie.tick(2,both(S,S),null);tie.tick(3,both(S,S),null);tie.tick(4,both(S,S),null);
        check(tie.state().phase()==Phase.ENDED&&tie.state().winner()==null,"identical results draw");
        var stop=fresh(2,10);act(stop,1,B,Action.TAKE,1,S,S);stop.stop();
        check(stop.movementMultiplier(B)==1&&stop.state().phase()==Phase.ENDED,"stop removes carry effects");
        check(new LegResult(true,3,10000,1,3,0).compareTo(new LegResult(true,2,10000,99,2,0))>0,"delivery count beats time");
        check(new LegResult(true,2,10000,50,0,0).compareTo(new LegResult(false,2,9000,1,0,1))>0,"stolen cargo tie breaker");
        var lost=fresh(2,20);
        act(lost,1,R,Action.DRIVE,0,S,S);act(lost,1,R,Action.RISKY,0,S,S);act(lost,1,B,Action.TAKE,1,S,S);
        for(int t=1;t<=8;t++)lost.tick(t,both(lost.state().route().position(),X),R);
        check(lost.state().red().delivered()==1&&lost.state().red().stolen()==0,"carried crate not delivered with wagon");
        var death=fresh(2,500);
        act(death,1,B,Action.TAKE,1,S,S);
        check(death.death(B)&&!death.carrying(B)&&death.waitingForRespawn(B),"death drops cargo and enters respawn");
        check(!act(death,1,B,Action.TAKE,1,S,S),"waiting player cannot act even with stale alive observation");
        var dead=new Presence(B,MATCH,true,false,S);
        for(int t=1;t<200;t++)death.tick(t,List.of(p(R,S),dead),null);
        check(death.state().ticksLeft()==301&&death.respawnRemaining(B)==1&&death.readyRespawns().isEmpty(),"1v1 death does not freeze match or respawn");
        death.tick(200,List.of(p(R,S),dead),null);
        check(death.readyRespawns().equals(List.of(B))&&death.acknowledgeRespawn(B),"spawn at 200th tick with acknowledgement");
        check(act(death,1,B,Action.TAKE,1,S,S),"respawned player can recover dropped cargo");
        check(death.death(B)&&!death.acknowledgeRespawn(B),"new death cannot reuse earlier spawn acknowledgement");
        var best=new LegResult(true,20,10000,600,20,0);
        var less=new LegResult(true,19,10000,900,19,1);
        check(decision(best,less)==Decision.DELIVERED&&best.compareTo(less)>0,"result explanation delivery priority");
        var stolen=new LegResult(true,19,10000,600,20,1);
        check(decision(best,stolen)==Decision.STOLEN&&best.compareTo(stolen)>0,"result explanation stolen priority");
        var partial=new LegResult(false,20,9000,900,20,0);
        check(decision(best,partial)==Decision.PROGRESS&&best.compareTo(partial)>0,"result explanation progress priority");
        var slow=new LegResult(true,20,10000,500,20,0);
        check(decision(best,slow)==Decision.TIME&&best.compareTo(slow)>0,"result explanation time priority");
        check(decision(best,best)==Decision.DRAW&&best.compareTo(best)==0,"result explanation exact draw");
        check(resultLine(Side.RED,less).equals("Kırmızı eskort: 19 teslim · 1 çalındı · %100 ilerleme"),"round summary uses exact delivered/stolen");
        check(reason(less,best).equals("Teslim edilen sandık sayısı"),"winner reason independent of ordering");
        var recovery=fresh(2,100);
        check(act(recovery,1,B,Action.TAKE,1,S,S),"exceptional recovery match fixture");
        var high=new Position(0,30,0);recovery.tick(1,both(S,high),null);recovery.death(B);
        check(!recovery.restoreDropped(2,1,high,S)&&recovery.restoreDropped(1,1,high,S),"exceptional restore guarded by leg");
        check(recovery.state().crates().getFirst().status()==CaravanCargoRules.Status.DROPPED&&recovery.state().crates().getFirst().position().equals(S)&&recovery.state().ticksLeft()==99,"exceptional restore preserves dropped state and clock");
        recovery.stop();check(!recovery.restoreDropped(1,1,S,X),"terminal match recovery denied");
        System.out.println("CARAVAN_MATCH "+passed+"/"+passed+" PASS");
    }
}
