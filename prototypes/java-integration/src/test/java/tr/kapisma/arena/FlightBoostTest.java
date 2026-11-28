package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.FlightBoostRules.*;
public final class FlightBoostTest {
    static int passed;
    static final UUID M=UUID.randomUUID(),A=UUID.randomUUID(),B=UUID.randomUUID(),X=UUID.randomUUID();
    static Observation obs(UUID id,boolean glide){return new Observation(id,M,true,true,glide);}
    static List<Observation> both(){return List.of(obs(A,true),obs(B,true));}
    static void ok(boolean b,String n){if(!b)throw new AssertionError(n);passed++;System.out.println("PASS "+n);}
    public static void main(String[] args){
        var g=new FlightBoostRules(M,List.of(A,B),List.of(new Pad("speed",PadKind.SPEED,20),new Pad("lift",PadKind.LIFT,40)));
        ok(!g.rocket(A,M,()->true)&&g.remaining(X)==-1,"inactive and outsider");
        g.tick(true,both());
        ok(!g.rocket(A,X,()->true)&&!g.rocket(X,M,()->true),"match isolation");
        ok(!g.rocket(A,M,()->false)&&g.remaining(A)==0,"failed motion no cooldown");
        ok(g.rocket(A,M,()->true)&&g.remaining(A)==200,"first unlimited rocket");
        ok(!g.rocket(A,M,()->{throw new AssertionError("unexpected boost");})&&g.rocket(B,M,()->true),"same tick duplicate blocked independent racer");
        ok(g.pad(A,M,"speed",()->true)&&!g.pad(A,M,"speed",()->true)&&g.remaining(A)==200,"speed pad independent cooldown");
        g.invalidate(A);
        ok(!g.rocket(A,M,()->true)&&!g.pad(A,M,"lift",()->true)&&g.remaining(A)==200,"checkpoint and death invalidate no timer reset");
        for(int i=0;i<50;i++)g.tick(false,both());
        ok(g.remaining(A)==200,"paused race no cooldown progress");
        for(int i=0;i<199;i++)g.tick(true,both());
        ok(g.remaining(A)==1&&!g.rocket(A,M,()->true),"199 tick denied");
        g.tick(true,both());
        ok(g.remaining(A)==0&&g.rocket(A,M,()->true),"exact 200 ticks allowed");
        g.tick(true,List.of(obs(A,false),obs(B,true)));
        ok(!g.pad(A,M,"speed",()->true)&&g.pad(A,M,"lift",()->true),"lift from ground speed only while gliding");
        g.tick(true,List.of(obs(A,true),obs(A,true),obs(B,true)));
        ok(!g.pad(A,M,"speed",()->true)&&!g.rocket(A,M,()->true),"duplicate observation denied");
        g.tick(true,both());
        ok(!g.pad(A,M,"unknown",()->true)&&!g.pad(A,M,"speed",()->false)&&g.pad(A,M,"speed",()->true),"known pad valid and failure no cooldown");
        for(int i=0;i<1000;i++)g.tick(true,both());
        ok(g.rocket(A,M,()->true)&&g.rocket(B,M,()->true),"no total usage limit");
        g.end();g.tick(true,both());
        ok(!g.pad(A,M,"lift",()->true)&&!g.rocket(A,M,()->true),"ended race cannot revive");
        System.out.println("PASS total "+passed);
    }
}
