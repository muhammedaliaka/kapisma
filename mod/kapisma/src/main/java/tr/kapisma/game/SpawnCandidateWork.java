package tr.kapisma.game;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.LongSupplier;

/** Resumable legacy spawn search. Server-thread caller owns the RNG and height lookup. */
final class SpawnCandidateWork {
    @FunctionalInterface interface Land { boolean test(int x,int z); }
    private final int cx,cz,bound,offset;
    private final double separation;
    private final Random random;
    private final int[][] taken;
    private int tries;
    private int[] result;
    private boolean cancelled,running;

    SpawnCandidateWork(int cx,int cz,double radius,Random random,List<int[]> taken,double separation) {
        this.cx=cx;this.cz=cz;
        int r=radius<=0?8:(int)radius;
        bound=2*r+1;offset=r;
        if(bound<=0)throw new IllegalArgumentException("radius");
        this.random=Objects.requireNonNull(random);
        this.separation=separation;
        this.taken=taken.stream().map(int[]::clone).toArray(int[][]::new);
    }
    boolean complete(){return !cancelled&&(result!=null||tries==200);}
    int[] result(){
        if(!complete())throw new IllegalStateException("search incomplete");
        return result==null?null:result.clone();
    }
    void cancel(){cancelled=true;}
    int step(int maxAttempts,long budgetNs,LongSupplier clock,Land land){
        if(maxAttempts<=0||budgetNs<=0)throw new IllegalArgumentException("budget");
        Objects.requireNonNull(clock);Objects.requireNonNull(land);
        if(running)throw new IllegalStateException("reentrant search");
        if(cancelled||complete())return 0;
        running=true;
        int done=0;
        try{
            long start=clock.getAsLong();
            while(!cancelled&&!complete()&&done<maxAttempts){
                if(done>0&&clock.getAsLong()-start>=budgetNs)break;
                int x=cx+random.nextInt(bound)-offset,z=cz+random.nextInt(bound)-offset;
                double sep=separation*(tries<100?1.0:tries<150?0.6:0.3);
                tries++;done++;
                boolean far=true;
                for(int[] t:taken){
                    double dx=t[0]-x,dz=t[1]-z;
                    if(dx*dx+dz*dz<sep*sep){far=false;break;}
                }
                if(far&&land.test(x,z))result=new int[]{x,z};
            }
            return done;
        }catch(RuntimeException|Error ex){cancelled=true;throw ex;}
        finally{running=false;}
    }
}
