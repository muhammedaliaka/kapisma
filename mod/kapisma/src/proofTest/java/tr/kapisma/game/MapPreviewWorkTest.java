package tr.kapisma.game;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class MapPreviewWorkTest {
    private static int passed;
    private static final LongSupplier FIXED = () -> 0L;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++;
        System.out.println("PASS " + label);
    }
    private static MapPreviewWork fresh() { return new MapPreviewWork(-17, 19, 2); }
    private static void drain(MapPreviewWork w, MapPreviewWork.Sample s, MapPreviewWork.Paint p) {
        int steps = 0;
        while (!w.complete()) {
            if (w.step(137, 1000, FIXED, s, p) <= 0 || ++steps > 1000) throw new AssertionError("no progress");
        }
    }
    public static void main(String[] args) {
        var w = fresh();
        int[] first = new int[2];
        int n = w.step(1, 1000, FIXED, (x,z) -> { first[0]=x; first[1]=z; return false; }, (x,z,b,c)->{throw new AssertionError();});
        check(n==1 && w.sampled()==1 && first[0]==-272 && first[1]==-240, "negative center truncation and one-operation cap");
        var t = fresh();
        AtomicLong clock = new AtomicLong();
        n=t.step(1000, 10, clock::get, (x,z)->{clock.addAndGet(6); return false;},(x,z,b,c)->{});
        check(n==2 && t.sampled()==2, "soft elapsed budget checked before next operation");
        var pattern = fresh();
        int[] samples={0}, pixels={0};
        drain(pattern,(x,z)->{
            int idx=samples[0]++;
            if(x!=-272+(idx%128)*4 || z!=-240+(idx/128)*4) throw new AssertionError("sample order");
            return (((idx%128)+(idx/128))&1)==0;
        },(x,z,b,c)->{
            int idx=pixels[0]++;
            if(x!=1+idx/126 || z!=1+idx%126 || b!=(((x+z)&1)==0) || c!=4) throw new AssertionError("paint pattern/order");
        });
        check(samples[0]==16384 && pixels[0]==15876 && pattern.complete(), "all samples and interior pixels exactly once, checkerboard");
        check(pattern.step(99,99,FIXED,(x,z)->{throw new AssertionError();},(x,z,b,c)->{throw new AssertionError();})==0,"completed job no-op");
        var water=fresh();
        drain(water,(x,z)->true,(x,z,b,c)->{if(!b||c!=8)throw new AssertionError("all water");});
        check(water.complete(),"eight water neighbors");
        var land=fresh();
        drain(land,(x,z)->false,(x,z,b,c)->{if(b||c!=0)throw new AssertionError("all land");});
        check(land.complete(),"zero water neighbors");
        var cancelled=fresh();
        cancelled.step(3,100,FIXED,(x,z)->false,(x,z,b,c)->{});
        cancelled.cancel();
        check(cancelled.cancelled() && !cancelled.complete() && cancelled.step(99,99,FIXED,(x,z)->{throw new AssertionError();},(x,z,b,c)->{})==0,"cancel prevents further work");
        var failed=fresh();
        try { failed.step(2,99,FIXED,(x,z)->{throw new IllegalStateException("sample failed");},(x,z,b,c)->{});throw new AssertionError(); }
        catch(IllegalStateException expected) { check(failed.cancelled()&&!failed.complete(),"callback error invalidates job"); }
        var reentrant=fresh();
        try { reentrant.step(2,99,FIXED,(x,z)->{reentrant.step(1,1,FIXED,(a,b)->false,(a,b,c,d)->{});return false;},(x,z,b,c)->{});throw new AssertionError(); }
        catch(IllegalStateException expected) { check(reentrant.cancelled(),"reentrant call rejected"); }
        try { new MapPreviewWork(0,0,5);throw new AssertionError(); }
        catch(IllegalArgumentException expected) { check(true,"invalid scale rejected"); }
        try { new MapPreviewWork(Integer.MAX_VALUE,0,4);throw new AssertionError(); }
        catch(ArithmeticException expected) { check(true,"coordinate overflow rejected"); }
        try { fresh().step(0,99,FIXED,(x,z)->false,(x,z,b,c)->{});throw new AssertionError(); }
        catch(IllegalArgumentException expected) { check(true,"invalid budget rejected"); }
        try { new MapPreviewWork(Integer.MIN_VALUE,0,0);throw new AssertionError(); }
        catch(ArithmeticException expected) { check(true,"negative coordinate underflow rejected"); }
        var painting=fresh();
        painting.step(16384,100,FIXED,(x,z)->true,(x,z,b,c)->{throw new AssertionError("early paint");});
        painting.step(1,100,FIXED,(x,z)->{throw new AssertionError("extra sample");},(x,z,b,c)->{});
        painting.cancel();
        check(painting.painted()==1 && !painting.complete() && painting.step(99,99,FIXED,(x,z)->{throw new AssertionError();},(x,z,b,c)->{throw new AssertionError();})==0,"partial paint cannot complete after cancel");
        var phased=fresh();
        int slices=0;
        while(!phased.complete()) {
            boolean sampling=phased.sampled()<16384;
            int before=phased.painted();
            int ops=phased.stepPhased(512,4096,100,FIXED,(x,z)->true,(x,z,b,c)->{});
            if(ops<=0 || ops>(sampling?512:4096) || (sampling && phased.painted()!=before))
                throw new AssertionError("phase cap/transition");
            slices++;
        }
        check(slices==36 && phased.sampled()==16384 && phased.painted()==15876,"phase-specific cap and no cross-phase slice");
        var phasedBudget=fresh();
        phasedBudget.stepPhased(16384,4096,100,FIXED,(x,z)->false,(x,z,b,c)->{});
        AtomicLong paintClock=new AtomicLong();
        int painted=phasedBudget.stepPhased(512,4096,10,paintClock::get,(x,z)->{throw new AssertionError();},
            (x,z,b,c)->paintClock.addAndGet(6));
        check(painted==2 && phasedBudget.painted()==2,"paint cap still honors soft time budget");
        System.out.println("MAP PREVIEW WORK " + passed + "/16 PASS");
    }
}
