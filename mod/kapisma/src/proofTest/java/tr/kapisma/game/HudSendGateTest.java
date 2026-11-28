package tr.kapisma.game;

import java.util.UUID;

public final class HudSendGateTest {
    private static int passed;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    private record State(boolean active,int x,int z,String label){}
    public static void main(String[] args){
        var gate=new HudSendGate<State>(40);
        var id=new UUID(0,1);var other=new UUID(0,2);var conn=new Object();var rejoin=new Object();
        var a=new State(true,12,14,"a");var b=new State(true,13,14,"a");var off=new State(false,0,0,"");
        check(gate.shouldSend(id,conn,a,10),"first snapshot");
        check(gate.shouldSend(id,conn,a,10),"uncommitted send never suppressed");
        gate.markSent(id,conn,a,10);
        check(!gate.shouldSend(id,conn,new State(true,12,14,"a"),20),"equal values suppressed");
        check(gate.shouldSend(id,conn,b,20),"position change immediate");
        check(gate.shouldSend(id,conn,new State(true,12,14,"b"),20),"label change immediate");
        check(gate.shouldSend(id,conn,off,20),"inactive transition immediate");
        check(!gate.shouldSend(id,conn,a,49)&&gate.shouldSend(id,conn,a,50),"40-tick heartbeat");
        check(gate.shouldSend(id,rejoin,a,20),"new connection never inherits cache");
        check(gate.shouldSend(other,conn,a,20),"other player isolated");
        check(gate.shouldSend(id,conn,a,0),"tick reset sends");
        gate.forget(id);check(gate.shouldSend(id,conn,a,20),"disconnect or channel loss invalidates");
        gate.markSent(id,conn,a,20);gate.markSent(other,conn,a,20);gate.clear();
        check(gate.shouldSend(id,conn,a,21)&&gate.shouldSend(other,conn,a,21),"match close invalidates all");
        int old=0,actual=0;
        for(int tick=10;tick<=1200;tick+=10){
            old++;
            if(gate.shouldSend(id,conn,a,tick)){actual++;gate.markSent(id,conn,a,tick);}
        }
        check(old==120&&actual==30,"stationary model 120 to 30 sends");
        int moving=0;
        gate.clear();
        for(int tick=10;tick<=1200;tick+=10){
            var state=new State(true,tick,14,"a");
            if(gate.shouldSend(id,conn,state,tick)){moving++;gate.markSent(id,conn,state,tick);}
        }
        check(moving==120,"moving model preserves all updates");
        System.out.println("HUD SEND GATE "+passed+"/14 PASS");
    }
}
