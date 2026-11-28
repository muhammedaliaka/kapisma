package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
public final class CaravanGrappleRulesTest{
    static final UUID M=new UUID(1,1),R=new UUID(1,2),B=new UUID(1,3),X=new UUID(1,4);
    static final MatchRoster roster=new MatchRoster(M,List.of(R),List.of(B));
    static final Position P=new Position(0,0,0),A=new Position(3,4,0);
    static List<Presence> at(Position p){return List.of(new Presence(R,M,true,true,p),new Presence(B,M,true,true,P));}
    static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        var g=new CaravanGrappleRules(roster,false);
        check(!g.fire(R,A),"no fire without observation");
        g.observe(0,at(P),true);
        check(!g.fire(X,A)&&!g.fire(R,new Position(301,0,0))&&!g.fire(R,new Position(1,0,0)),"unknown and out-of-range anchors denied");
        check(g.fire(R,A)&&g.remaining(R)==600&&g.attached(R),"launch starts full 30-second cooldown");
        check(!g.fire(R,A)&&!g.pulse(R),"duplicate launch and manual pulse denied");
        var motion=g.advance(x->true);
        check(motion.size()==1&&motion.getFirst().acceleration().y()>motion.getFirst().acceleration().x(),"automatic pull includes height");
        check(g.advance(x->true).isEmpty(),"no duplicate tick acceleration");
        g.observe(1,at(P),true);check(g.advance(x->false).isEmpty()&&!g.attached(R),"destroyed anchor detaches");
        g.observe(599,at(P),true);check(g.remaining(R)==1&&!g.fire(R,A),"cooldown enforced until exact boundary");
        g.observe(600,at(P),true);check(g.fire(R,A),"unlimited reuse after cooldown");
        g.cancel(R);check(!g.attached(R)&&g.remaining(R)==600,"death cancels rope not cooldown");
        g.observe(601,List.of(new Presence(B,M,true,true,P)),true);
        check(!g.fire(R,A),"disconnected player rejected");
        g.observe(1200,at(P),true);check(g.fire(R,A),"reconnection does not permanently disable");
        g.observe(1201,at(new Position(3,3,0)),true);check(g.advance(x->true).isEmpty()&&!g.attached(R),"close anchor release");
        var h=new CaravanGrappleRules(roster,true);h.observe(0,at(P),true);h.fire(R,A);
        check(h.advance(x->true).size()==1&&!h.pulse(R),"manual initial pull and spam guard");
        h.observe(5,at(P),true);check(h.advance(x->true).isEmpty()&&h.attached(R),"manual stops pull without press");
        check(h.pulse(R)&&!h.pulse(R),"one pulse per allowed interval");
        h.observe(6,at(P),true);check(h.advance(x->true).size()==1,"manual repeated press moves");
        h.observe(200,at(P),true);check(h.advance(x->true).isEmpty()&&!h.attached(R),"max attachment duration");
        h.observe(600,at(P),false);check(!h.fire(R,A),"paused match denies new hook");
        h.observe(601,at(P),true);check(h.fire(R,A),"unpaused match permits");
        h.clearActive();check(!h.attached(R)&&h.remaining(R)==600,"round cleanup retains cooldown");
        h.end();check(!h.fire(R,A)&&h.advance(x->true).isEmpty(),"match end terminal");
        var ropes=new CaravanGrappleRules(roster,true);ropes.observe(0,at(P),true);
        check(ropes.fire(R,A)&&ropes.fire(B,A),"independent simultaneous ropes");
        check(ropes.advance((id,anchor)->true).size()==2,"both clear ropes pull");
        ropes.observe(6,at(P),true);
        check(ropes.advance((id,anchor)->id.equals(B)).isEmpty()&&!ropes.attached(R)&&ropes.attached(B),"blocked manual idle rope cancels only affected player");
        check(ropes.remaining(R)==594&&!ropes.fire(R,A),"obstruction retains exact cooldown");
        check(ropes.pulse(B),"clear rope remains usable");
        ropes.observe(7,at(P),true);
        check(ropes.advance((id,anchor)->true).size()==1,"only clear rope applies acceleration");
        System.out.println("CARAVAN_GRAPPLE "+passed+"/"+passed+" PASS");
    }
}
