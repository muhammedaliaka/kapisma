package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.CaravanScenePlan.*;
import tr.kapisma.arena.CaravanMatchRules.Action;
public final class CaravanScenePlanTest {
    static int passed;
    static final UUID M=new UUID(0,10),R=new UUID(0,11),B=new UUID(0,12);
    static final Position S=new Position(0,0,0),F=new Position(8,0,0),X=new Position(30,0,0);
    static final MatchRoster roster=new MatchRoster(M,List.of(R),List.of(B));
    static void check(boolean v,String label){if(!v)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Presence p(UUID id,Position at){return new Presence(id,M,true,true,at);}
    static List<Presence> both(Position r,Position b){return List.of(p(R,r),p(B,b));}
    static Key key(Kind k,int id){return new Key(1,k,id);}
    public static void main(String[] args){
        var g=new CaravanMatchRules(roster,List.of(S,new Position(4,0,4),F),List.of(S,F),X,3,20,2,.5,3);
        var a=CaravanScenePlan.project(g.state());
        var wagon=a.get(key(Kind.WAGON,0));var cargo=a.get(key(Kind.WAGON_CARGO,1));
        check(a.size()==2&&wagon.clickable()&&cargo.clickable(),"bounded wagon and cargo targets");
        check(CaravanScenePlan.action(wagon,g.state(),R).orElseThrow()==Action.DRIVE,"wagon requests driving");
        check(g.act(1,R,Action.DRIVE,0,both(S,S)),"driver claimed");
        check(CaravanScenePlan.action(wagon,g.state(),R).orElseThrow()==Action.LEAVE_DRIVER,"same driver requests release");
        check(g.act(1,B,Action.TAKE,1,both(S,S)),"crate taken");
        var b=CaravanScenePlan.project(g.state());
        check(b.containsKey(key(Kind.WAGON_CARGO,2))&&!b.containsKey(cargo.key()),"cargo hit identity changes after take");
        check(CaravanScenePlan.action(cargo,g.state(),B).isEmpty(),"obsolete hit rejected");
        var carried=b.get(key(Kind.CARRIED,1));
        check(B.equals(carried.carrier())&&!carried.clickable(),"carrier display only");
        check(CaravanScenePlan.action(carried,g.state(),R).isEmpty(),"cannot directly steal carrier display");
        g.tick(1,both(S,new Position(2,0,0)),R);
        var c=CaravanScenePlan.project(g.state());
        check(c.get(key(Kind.CARRIED,1)).position().equals(new Position(2,0,0)),"carried visual follows server");
        check(CaravanScenePlan.action(wagon,g.state(),R).isEmpty(),"stale wagon position rejected until sync");
        check(g.death(B),"carrier death");
        var d=CaravanScenePlan.project(g.state());
        var dropped=d.get(key(Kind.DROPPED,1));
        check(!d.containsKey(carried.key())&&dropped.clickable(),"death converts carried to dropped");
        check(CaravanScenePlan.action(dropped,g.state(),R).orElseThrow()==Action.TAKE,"defender recovery interaction");
        var landing=new Position(2,0,1);
        check(!g.relocateDropped(2,1,dropped.position(),landing)&&!g.relocateDropped(1,1,dropped.position(),X),"wrong leg and distant recovery rejected");
        check(g.relocateDropped(1,1,dropped.position(),landing),"server safe landing preserves crate identity");
        check(!g.relocateDropped(1,1,dropped.position(),S)&&CaravanScenePlan.action(dropped,g.state(),R).isEmpty(),"stale recovery location denied");
        dropped=CaravanScenePlan.project(g.state()).get(key(Kind.DROPPED,1));
        check(dropped.position().equals(landing),"recovered world position matches authoritative crate");
        check(g.act(1,R,Action.LEAVE_DRIVER,0,both(g.state().route().position(),S)),"driver released");
        check(g.act(1,R,Action.TAKE,1,both(dropped.position(),S)),"defender takes dropped crate");
        check(CaravanScenePlan.action(dropped,g.state(),R).isEmpty(),"drop hit consumed once");
        for(int t=2;t<=20;t++)g.tick(t,both(S,S),null);
        check(g.state().phase()==CaravanMatchRules.Phase.BREAK&&CaravanScenePlan.project(g.state()).isEmpty(),"intermission removes all entities");
        g.tick(21,both(S,S),null);g.tick(22,both(S,S),null);
        check(CaravanScenePlan.project(g.state()).keySet().stream().allMatch(k->k.leg()==2),"new leg owns new entity keys");
        check(CaravanScenePlan.action(wagon,g.state(),B).isEmpty(),"old leg target denied");
        g.stop();check(CaravanScenePlan.project(g.state()).isEmpty(),"match close removes all projections");
        var pile=new CaravanMatchRules(roster,List.of(S,new Position(4,0,4),F),List.of(S,F),X,4,200,2,.5,3);
        check(pile.act(1,B,Action.TAKE,1,both(S,S))&&pile.act(1,B,Action.DROP,0,both(S,S)),"pile first drop");
        check(pile.act(1,B,Action.TAKE,2,both(S,S))&&pile.act(1,B,Action.DROP,0,both(S,S)),"pile second drop");
        check(pile.act(1,B,Action.TAKE,3,both(S,S))&&pile.act(1,B,Action.DROP,0,both(S,new Position(2,0,0))),"separate crate drop");
        var stacked=CaravanScenePlan.project(pile.state());
        var first=stacked.get(key(Kind.DROPPED,1));
        check(first!=null&&!stacked.containsKey(key(Kind.DROPPED,2))&&stacked.containsKey(key(Kind.DROPPED,3)),"one visible overlapping target, separate crate retained");
        var pending=Set.of(1);
        var second=CaravanScenePlan.project(pile.state(),pending).get(key(Kind.DROPPED,2));
        check(second!=null&&CaravanScenePlan.action(second,pile.state(),R,pending).orElseThrow()==Action.TAKE&&CaravanScenePlan.action(first,pile.state(),R,pending).isEmpty(),"pending excluded before grouping and validation");
        check(pile.act(1,R,Action.TAKE,1,both(S,S)),"first crate retrieved");
        check(CaravanScenePlan.project(pile.state()).containsKey(key(Kind.DROPPED,2))&&CaravanScenePlan.action(first,pile.state(),B).isEmpty(),"second becomes visible, stale first denied");
        check(pile.act(1,B,Action.TAKE,2,both(S,S))&&pile.state().crates().size()==4&&pile.carrying(R)&&pile.carrying(B),"sequential pickup preserves identities");
        check(!CaravanScenePlan.overlaps(S,new Position(0,1,0))&&!CaravanScenePlan.overlaps(S,new Position(.85,0,0))&&CaravanScenePlan.overlaps(S,new Position(.84,.99,.84)),"vertical and horizontal hitbox separation");
        System.out.println("CARAVAN_SCENE "+passed+"/"+passed+" PASS");
    }
}
