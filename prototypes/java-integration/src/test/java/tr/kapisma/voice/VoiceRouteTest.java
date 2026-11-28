package tr.kapisma.voice;
import java.util.*;
import tr.kapisma.voice.VoiceRouteScope.*;
public final class VoiceRouteTest {
    private static int passed;
    private static void ok(boolean v,String label){if(!v)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception{
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),s=UUID.randomUUID(),s2=UUID.randomUUID(),l=UUID.randomUUID(),l2=UUID.randomUUID();
        var participants=Set.of(a,b,s,s2);var spectators=Set.of(s,s2);
        Key key=new Key(UUID.randomUUID(),UUID.randomUUID(),20);
        ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,s,null),"unknown plugin spectator denied");
        ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,a,null),"unknown plugin active denied");
        ok(VoiceRouteScope.permitsPlugin(Set.of(),Set.of(),null,l,null),"unknown lobby sound preserved");
        ok(!VoiceRouteScope.permitsPlugin(participants,spectators,a,l,null)&&!VoiceRouteScope.permitsPlugin(participants,spectators,l,a,null),"plugin match/lobby isolation");
        ok(!VoiceRouteScope.permitsPlugin(participants,spectators,a,s,null)&&!VoiceRouteScope.permitsPlugin(participants,spectators,s,a,null),"known plugin spectator isolation");
        ok(VoiceRouteScope.permitsPlugin(participants,spectators,a,b,null),"known active plugin allowed");
        VoiceRouteScope.send(Kind.ANALOG,a,b,key,()->{
            Route r=VoiceRouteScope.current(b,key,Kind.ANALOG);
            ok(r!=null&&VoiceRouteScope.permitsPlugin(participants,spectators,null,b,r),"trusted Analog active delivery");
            ok(VoiceRouteScope.current(s,key,Kind.ANALOG)==null&&VoiceRouteScope.current(b,new Key(key.packetSender(),key.channel(),21),Kind.ANALOG)==null&&VoiceRouteScope.current(b,key,Kind.SPECTATOR_RELAY)==null,"receiver sequence kind binding");
            ok(!VoiceRouteScope.permitsPlugin(participants,spectators,s,b,r),"scope known source mismatch");
        });
        ok(VoiceRouteScope.current(b,key,Kind.ANALOG)==null,"scope cleared");
        VoiceRouteScope.send(Kind.ANALOG,a,s,key,()->ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,s,VoiceRouteScope.current(s,key,Kind.ANALOG)),"Analog active to spectator denied"));
        VoiceRouteScope.send(Kind.ANALOG,s,a,key,()->ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,a,VoiceRouteScope.current(a,key,Kind.ANALOG)),"Analog spectator to active denied"));
        VoiceRouteScope.send(Kind.ANALOG,l,a,key,()->ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,a,VoiceRouteScope.current(a,key,Kind.ANALOG)),"Analog cross lobby denied"));
        VoiceRouteScope.send(Kind.ANALOG,l,l2,key,()->ok(VoiceRouteScope.permitsPlugin(participants,spectators,null,l2,VoiceRouteScope.current(l2,key,Kind.ANALOG)),"Analog lobby same cohort"));
        VoiceRouteScope.send(Kind.SPECTATOR_RELAY,s,s2,key,()->ok(VoiceRouteScope.permitsPlugin(participants,spectators,null,s2,VoiceRouteScope.current(s2,key,Kind.SPECTATOR_RELAY)),"private spectator relay preserved"));
        VoiceRouteScope.send(Kind.SPECTATOR_RELAY,s,a,key,()->ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,a,VoiceRouteScope.current(a,key,Kind.SPECTATOR_RELAY)),"spectator relay cannot escape"));
        try{VoiceRouteScope.send(Kind.ANALOG,a,b,key,()->{throw new IllegalStateException("test");});}catch(IllegalStateException e){}
        ok(VoiceRouteScope.current(b,key,Kind.ANALOG)==null,"scope cleared after exception");
        VoiceRouteScope.send(Kind.ANALOG,a,b,key,()->{
            VoiceRouteScope.send(Kind.SPECTATOR_RELAY,s,s2,key,()->ok(VoiceRouteScope.current(b,key,Kind.ANALOG)==null,"nested scope isolated"));
            ok(VoiceRouteScope.current(b,key,Kind.ANALOG)!=null,"outer scope restored");
            var isolated=new java.util.concurrent.atomic.AtomicBoolean();
            Thread t=new Thread(()->isolated.set(VoiceRouteScope.current(b,key,Kind.ANALOG)==null));
            t.start();try{t.join();}catch(InterruptedException e){throw new AssertionError(e);}
            ok(isolated.get(),"scope thread isolated");
        });
        var count=new java.util.concurrent.atomic.AtomicInteger();
        Runnable queued=VoiceRouteScope.guard(count::incrementAndGet);
        queued.run();
        ok(count.get()==1,"queued Analog same epoch runs");
        VoiceRouteScope.publishEpoch(new Object());
        queued.run();
        ok(count.get()==1,"old match queued Analog dropped");
        VoiceRouteScope.send(Kind.ANALOG,a,b,key,()->{
            var route=VoiceRouteScope.current(b,key,Kind.ANALOG);
            VoiceRouteScope.publishEpoch(new Object());
            ok(!VoiceRouteScope.isCurrent(route.epoch()),"in-flight old epoch detected");
        });
        ok(!VoiceRouteScope.permitsOrdinaryRadio(participants,spectators,a,b),"radio direct proximity/group denied");
        ok(!VoiceRouteScope.permitsOrdinaryRadio(participants,spectators,a,l)&&!VoiceRouteScope.permitsOrdinaryRadio(participants,spectators,l,a),"radio lobby boundary");
        ok(VoiceRouteScope.permitsOrdinaryRadio(participants,spectators,s,s2),"radio spectator ordinary voice");
        ok(VoiceRouteScope.permitsOrdinaryRadio(participants,spectators,l,l2),"radio unrelated lobby voice");
        ok(!VoiceRouteScope.permitsOrdinaryRadio(participants,spectators,null,a),"radio unknown ordinary source denied");
        ok(!VoiceRouteScope.permitsRadioPlugin(participants,spectators,a,b,null),"radio unscoped known plugin denied");
        ok(!VoiceRouteScope.permitsRadioPlugin(participants,spectators,null,a,null),"radio unscoped unknown plugin denied");
        VoiceRouteScope.send(Kind.ANALOG,a,b,key,()->ok(VoiceRouteScope.permitsRadioPlugin(participants,spectators,null,b,VoiceRouteScope.current(b,key,Kind.ANALOG)),"radio scoped Analog allowed"));
        VoiceRouteScope.send(Kind.SPECTATOR_RELAY,s,s2,key,()->ok(VoiceRouteScope.permitsRadioPlugin(participants,spectators,null,s2,VoiceRouteScope.current(s2,key,Kind.SPECTATOR_RELAY)),"radio spectator relay allowed"));
        Key real=new Key(a,UUID.randomUUID(),41);
        var nested=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        VoiceRouteScope.guardMicrophone(a,()->nested.set(VoiceRouteScope.guard(()->VoiceRouteScope.sendAnalog(b,l,real,()->{
            var route=VoiceRouteScope.current(l,real,Kind.ANALOG);
            ok(route.from().equals(a)&&route.carrier().equals(b),"actual mic source survives two dispatches");
            ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,l,route),"actual mic cross cohort denied");
        })))).run();
        nested.get().run();
        VoiceRouteScope.guardMicrophone(s,()->VoiceRouteScope.sendAnalog(b,a,new Key(s,key.channel(),42),()->{
            var route=VoiceRouteScope.current(a,new Key(s,key.channel(),42),Kind.ANALOG);
            ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,a,route),"spectator through active radio denied");
        })).run();
        VoiceRouteScope.guardMicrophone(a,()->VoiceRouteScope.sendAnalog(s,b,real,()->ok(!VoiceRouteScope.permitsPlugin(participants,spectators,null,b,VoiceRouteScope.current(b,real,Kind.ANALOG)),"active through spectator radio denied"))).run();
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        VoiceRouteScope.sendAnalog(b,l,real,calls::incrementAndGet);
        ok(calls.get()==0,"unscoped mismatched packet origin denied");
        Runnable micTask=VoiceRouteScope.guardMicrophone(a,calls::incrementAndGet);
        VoiceRouteScope.publishEpoch(new Object());micTask.run();
        ok(calls.get()==0,"old first microphone dispatch dropped");
        nested.get().run();ok(calls.get()==0,"old nested dispatch dropped");
        System.out.println("PASS total "+passed);
    }
}