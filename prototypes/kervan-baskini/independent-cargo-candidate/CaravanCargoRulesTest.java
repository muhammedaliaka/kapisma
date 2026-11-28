package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.CaravanCargoRules.Status;

public final class CaravanCargoRulesTest {
    static int passed;
    static final UUID MATCH=new UUID(0,1), D=new UUID(0,2), A=new UUID(0,3), B=new UUID(0,4), FOREIGN=new UUID(0,5);
    static final Position W=new Position(0,0,0), E=new Position(30,0,0), M=new Position(10,0,0);
    static final MatchRoster R=new MatchRoster(MATCH,List.of(D),List.of(A,B));
    static Presence p(UUID id, Position pos){return new Presence(id,MATCH,true,true,pos);}
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static CaravanCargoRules fresh(){return new CaravanCargoRules(R,Side.RED,W,E,3,3);}
    static void see(CaravanCargoRules g,Position w,Presence... p){g.observe(List.of(p),w);}
    static boolean state(CaravanCargoRules g,int id,Status s){return g.snapshot().get(id-1).status()==s;}
    public static void main(String[] args) throws Exception {
        var g=fresh();
        check(g.snapshot().size()==3&&g.stolen()==0,"initial fixed identities");
        check(!g.take(A,1),"no action before observation");
        see(g,W,p(A,W));check(!g.ready()&&!g.take(A,1),"pause absent defenders");
        see(g,W,p(D,W),p(A,W),p(B,W));
        check(!g.take(D,1),"defender cannot remove wagon cargo");
        check(!g.take(FOREIGN,1)&&!g.take(A,99),"foreign and unknown rejected");
        check(g.take(A,1)&&g.carrying(A),"attacker picks up crate");
        check(!g.take(A,2)&&!g.take(B,1),"one crate per player and no double take");
        check(!g.deliver(A),"wrong extraction location");
        see(g,W,p(D,M),p(A,M),p(B,W));
        check(g.snapshot().get(0).position().equals(M),"carrier location follows server");
        check(g.drop(A)&&!g.drop(A)&&state(g,1,Status.DROPPED),"drop once");
        check(!g.take(B,1),"distant attacker denied");
        check(g.take(D,1)&&g.carrying(D),"defender recovers dropped crate");
        check(!g.deliver(D),"defender must return to wagon");
        see(g,W,p(D,W),p(A,M),p(B,W));
        check(g.deliver(D)&&state(g,1,Status.ON_WAGON),"return intact to wagon");
        check(g.take(B,1),"returned crate can be stolen again");
        see(g,W,p(D,W),p(A,M),p(B,E));
        check(g.deliver(B)&&g.stolen()==1&&state(g,1,Status.STOLEN),"extract once");
        check(!g.deliver(B)&&!g.take(B,1),"no replay after extraction");
        see(g,W,p(D,W),p(A,W),p(B,W));
        check(g.take(A,2),"second crate acquired");
        see(g,W,p(D,W),p(A,M),p(B,W));
        check(g.invalidate(A)&&state(g,2,Status.DROPPED)&&g.snapshot().get(1).position().equals(M),"disconnect/death drops last server position");
        check(!g.deliver(A)&&!g.take(A,3),"invalidated actor cannot act");
        see(g,W,p(D,W),p(A,M),p(A,E),p(B,W));
        check(!g.take(A,2),"duplicate observations rejected");
        see(g,W,p(D,W),p(A,M),p(B,W));
        check(g.take(A,2),"reconnect can legitimately recover");
        see(g,W,p(D,W),p(B,W));
        check(state(g,2,Status.DROPPED)&&!g.carrying(A),"missing carrier drops automatically");
        see(g,M,p(D,M),p(B,M));
        check(g.snapshot().get(2).position().equals(M),"unclaimed crate follows wagon");
        check(g.take(B,3),"remaining wagon crate acquired");
        g.end();check(!g.ready()&&!g.take(B,2)&&!g.deliver(B),"end locks all commands");
        check(g.snapshot().size()==3&&g.stolen()==1&&!g.carrying(B),"end preserves identity and result");
        var reverse=new CaravanCargoRules(R,Side.BLUE,W,E,1,3);
        see(reverse,W,p(D,W),p(B,W));check(!reverse.take(B,1)&&reverse.take(D,1),"reversed team roles");
        var other=fresh();see(other,W,p(D,W),new Presence(A,new UUID(0,99),true,true,W));
        check(!other.ready()&&!other.take(A,1),"wrong match rejected");
        see(other,W,p(D,W),new Presence(A,MATCH,false,true,W));check(!other.ready(),"outside arena rejected");
        see(other,W,p(D,W),new Presence(A,MATCH,true,false,W));check(!other.ready(),"inactive actor rejected");
        var failure=new java.util.concurrent.atomic.AtomicBoolean(false);
        var t=new Thread(()->{try{other.take(A,1);}catch(IllegalStateException ex){failure.set(true);}});
        t.start();t.join();check(failure.get(),"wrong thread rejected");
        var rescue=fresh();see(rescue,W,p(D,W),p(A,W));check(rescue.take(A,1),"recovery fixture take");
        see(rescue,W,p(D,W),p(A,new Position(0,30,0)));check(rescue.drop(A),"recovery fixture drop high");
        var high=new Position(0,30,0);
        check(!rescue.relocateDropped(1,high,W),"ordinary relocation cannot cross eight blocks");
        check(!rescue.restoreDropped(1,M,W)&&!rescue.restoreDropped(2,W,M),"stale expected and wagon crate rejected");
        check(rescue.restoreDropped(1,high,W)&&rescue.snapshot().getFirst().status()==Status.DROPPED&&rescue.stolen()==0&&rescue.snapshot().size()==3,"exceptional recovery preserves identity and score");
        check(!rescue.restoreDropped(1,high,M),"replayed recovery denied");
        see(rescue,W,p(D,W),p(A,W));check(rescue.take(A,1)&&!rescue.restoreDropped(1,W,M),"recovered crate take once, carried cannot restore");
        rescue.end();check(!rescue.restoreDropped(1,W,M),"ended recovery denied");
        System.out.println("CARAVAN_CARGO "+passed+"/"+passed+" PASS");
    }
}
