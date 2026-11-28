package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Side;
public final class CaravanRespawnRulesTest {
    static int passed;
    static final UUID R=new UUID(0,1), B=new UUID(0,2), C=new UUID(0,3), X=new UUID(0,4);
    static final MatchRoster roster=new MatchRoster(new UUID(1,1),List.of(R),List.of(B,C));
    static void check(boolean v,String label){if(!v)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        var g=new CaravanRespawnRules(roster);
        var all=List.of(R,B,C);
        check(!g.death(X)&&g.death(B)&&g.waiting(B)&&g.remaining(B)==200,"member death only");
        g.tick(1,all,true);check(g.remaining(B)==199&&!g.death(B),"duplicate death no extension");
        g.tick(1,all,true);check(g.remaining(B)==199,"duplicate tick");
        g.tick(10,all,false);g.tick(11,List.of(B,C),true);check(g.remaining(B)==199,"paused or missing team freezes");
        for(int t=12;t<210;t++)checkSilent(g.tick(t,all,true).isEmpty());
        check(g.remaining(B)==1&&!g.acknowledge(B),"199 active ticks not ready");
        check(g.tick(210,all,true).equals(List.of(B)),"exactly 200 active ticks");
        check(g.tick(211,all,true).equals(List.of(B)),"retry unacknowledged spawn");
        check(g.base(B)==Side.BLUE&&g.acknowledge(B)&&!g.waiting(B)&&!g.acknowledge(B),"own base and single acknowledgement");
        check(g.death(B),"new death allowed");
        for(int t=212;t<412;t++)checkSilent(g.tick(t,List.of(R,C),true).isEmpty());
        check(g.waiting(B)&&g.remaining(B)==0,"offline timer completion retained");
        check(g.tick(412,all,true).equals(List.of(B)),"reconnect ready without reset");
        g.resetLeg();check(!g.waiting(B),"round transition clears");
        g.death(R);g.end();check(!g.waiting(R)&&!g.death(R)&&g.tick(413,all,true).isEmpty(),"match cleanup");
        System.out.println("CARAVAN_RESPAWN "+passed+"/"+passed+" PASS");
    }
    static void checkSilent(boolean v){if(!v)throw new AssertionError("unexpected early spawn");}
}
