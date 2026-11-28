package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.CaravanRouteRules.Route;
public final class CaravanRouteRulesTest {
    static int passed;
    static final UUID MATCH=new UUID(0,11), D=new UUID(0,12), A=new UUID(0,13), B=new UUID(0,14);
    static final Position S=new Position(0,0,0), E=new Position(4,0,0), C=new Position(0,0,3);
    static final MatchRoster R=new MatchRoster(MATCH,List.of(D,B),List.of(A));
    static final List<Position> SAFE=List.of(S,C,new Position(4,0,3),E), RISK=List.of(S,E);
    static Presence p(UUID id,Position pos){return new Presence(id,MATCH,true,true,pos);}
    static CaravanRouteRules fresh(){return new CaravanRouteRules(R,Side.RED,SAFE,RISK,.5,3);}
    static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);passed++;System.out.println("PASS "+msg);}
    static boolean eq(double a,double b){return Math.abs(a-b)<1e-8;}
    static List<Presence> at(Position q){return List.of(p(D,q),p(B,q),p(A,E));}
    public static void main(String[] args){
        var g=fresh();
        check(g.state().route()==Route.SAFE&&eq(g.state().length(),10),"default safe path");
        check(eq(g.heading(),Math.PI/2),"initial heading follows first segment");
        check(!g.claimDriver(D),"cannot drive before server observation");
        g.tick(0,at(S),true);
        check(!g.claimDriver(A)&&g.claimDriver(D)&&!g.claimDriver(B),"single escort driver");
        check(!g.choose(B,Route.RISKY)&&g.choose(D,Route.RISKY),"driver chooses risk");
        check(eq(g.state().length(),4)&&g.selectedPath().equals(RISK),"shorter risk route");
        check(eq(g.heading(),Math.PI/2),"route choice alone does not rotate");
        g.tick(1,at(S),true);
        check(eq(g.state().distance(),.5)&&g.state().position().equals(new Position(.5,0,0)),"constant speed");
        check(eq(g.heading(),0),"heading follows actual movement");
        g.tick(1,at(S),true);g.tick(0,at(S),true);
        check(eq(g.state().distance(),.5),"duplicate and old tick denied");
        check(!g.choose(D,Route.SAFE),"route locked after movement");
        g.tick(2,at(S),false);check(eq(g.state().distance(),.5),"no forward no motion");
        g.tick(3,List.of(p(D,S)),true);check(!g.state().ready()&&eq(g.state().distance(),.5),"pause missing enemy");
        g.tick(100,at(S),true);check(eq(g.state().distance(),1),"no catch-up after paused ticks");
        g.invalidate(D);check(g.state().driver()==null,"disconnect drops driver");
        g.tick(101,at(S),true);check(eq(g.state().distance(),1)&&g.claimDriver(B),"replacement driver");
        g.tick(102,List.of(p(B,new Position(20,0,0)),p(A,E)),true);
        check(eq(g.state().distance(),1),"driver distance enforced");
        for(int i=103;i<109;i++)g.tick(i,at(g.state().position()),true);
        check(g.state().arrived()&&eq(g.state().distance(),4)&&g.state().position().equals(E),"exact endpoint");
        check(!g.claimDriver(D)&&!g.choose(D,Route.SAFE),"arrival terminal");
        var h=fresh();h.tick(0,at(S),false);check(h.claimDriver(D),"safe driver");
        for(int i=1;i<=7;i++)h.tick(i,at(h.state().position()),true);
        check(h.state().position().equals(new Position(.5,0,3))&&eq(h.state().distance(),3.5),"turn follows path not shortcut");
        check(eq(h.heading(),0),"turn tracks latest actual segment");
        h.end();h.tick(8,at(h.state().position()),true);check(eq(h.state().distance(),3.5)&&!h.state().ready(),"end locks movement");
        boolean invalid=false;
        try{new CaravanRouteRules(R,Side.RED,SAFE,List.of(S,new Position(5,0,0)),.5,3);}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"route endpoints validated");
        invalid=false;try{new CaravanRouteRules(R,Side.RED,RISK,SAFE,.5,3);}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"risk route length validated");
        var J=new Position(2,0,0);
        var L=new Position(4,0,0);
        var F=new Position(6,0,0);
        var detour=new Position(3,0,3);
        var j=new CaravanRouteRules(R,Side.RED,List.of(S,J,detour,L,F),List.of(S,J,L,F),.75,3);
        j.tick(0,at(S),false);check(j.claimDriver(D),"junction fixture driver");
        check(!j.choose(D,Route.RISKY),"no route change on common road");
        for(int i=1;i<=3;i++)j.tick(i,at(j.state().position()),true);
        check(j.state().position().equals(J)&&j.state().waitingAtJunction()&&eq(j.state().distance(),2),"stops exactly at physical junction");
        j.tick(4,at(J),true);check(eq(j.state().distance(),2),"wait for explicit choice");
        check(!j.choose(A,Route.RISKY)&&j.choose(D,Route.RISKY),"authorized switch at fork");
        check(j.state().position().equals(J)&&eq(j.state().distance(),2)&&eq(j.state().length(),6),"no teleport or progress reset");
        j.tick(5,at(J),true);check(eq(j.state().distance(),2.75)&&!j.choose(D,Route.SAFE),"no mid-edge switch");
        for(int i=6;i<=10;i++)j.tick(i,at(j.state().position()),true);
        check(j.state().arrived()&&eq(j.state().distance(),6),"hybrid journey exact distance");
        var blocked=fresh();blocked.tick(0,at(S),false);blocked.claimDriver(D);blocked.choose(D,Route.RISKY);
        blocked.tick(1,at(S),true,true,(from,to)->false);
        check(blocked.state().position().equals(S)&&eq(blocked.state().distance(),0),"collision gate stops movement without score");
        check(eq(blocked.heading(),Math.PI/2),"collision denial preserves heading");
        blocked.tick(2,at(S),true,true,(from,to)->true);
        check(eq(blocked.state().distance(),.5),"cleared path resumes at fixed speed");
        blocked.tick(3,at(blocked.state().position()),true,true,(from,to)->false);
        check(eq(blocked.state().distance(),.5),"blocked later movement preserves progress");
        System.out.println("CARAVAN_ROUTE "+passed+"/"+passed+" PASS");
    }
}
