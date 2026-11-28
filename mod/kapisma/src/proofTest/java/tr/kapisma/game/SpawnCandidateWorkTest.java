package tr.kapisma.game;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class SpawnCandidateWorkTest {
    private static int passed;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    private static int[] reference(int cx,int cz,double r,Random rnd,List<int[]> taken,double min,SpawnCandidateWork.Land land){
        for(int i=0;i<200;i++){
            int x,z;
            if(r<=0){x=cx+rnd.nextInt(17)-8;z=cz+rnd.nextInt(17)-8;}
            else{int a=(int)r;x=cx+rnd.nextInt(2*a+1)-a;z=cz+rnd.nextInt(2*a+1)-a;}
            double s=min*(i<100?1:i<150?0.6:0.3);
            boolean far=true;
            for(int[] t:taken){double dx=t[0]-x,dz=t[1]-z;if(dx*dx+dz*dz<s*s){far=false;break;}}
            if(far&&land.test(x,z))return new int[]{x,z};
        }
        return null;
    }
    public static void main(String[] args){
        int cases=0;
        for(long seed=0;seed<100;seed++)for(double r:new double[]{0,20,100,500})for(int mode=0;mode<3;mode++){
            int m=mode;var a=new Random(seed);var b=new Random(seed);
            List<int[]> taken=List.of(new int[]{-17,19},new int[]{25,-30});
            SpawnCandidateWork.Land land=(x,z)->m==0||m==1&&Math.floorMod(x*31+z*17,13)==0;
            int[] expected=reference(-17,19,r,a,taken,40,land);
            var work=new SpawnCandidateWork(-17,19,r,b,taken,40);
            int slices=0;
            while(!work.complete()){
                int n=work.step(8,5,()->0,land);
                if(n<1||n>8||++slices>25)throw new AssertionError("progress");
            }
            if(!Arrays.equals(expected,work.result())||a.nextLong()!=b.nextLong())throw new AssertionError("result/RNG mismatch");
            cases++;
        }
        check(cases==1200,"1200 legacy result and RNG state comparisons");
        var work=new SpawnCandidateWork(0,0,100,new Random(3),List.of(),0);
        AtomicLong clock=new AtomicLong();
        check(work.step(8,5,clock::get,(x,z)->{clock.addAndGet(3);return false;})==2,"soft time budget");
        try{work.result();throw new AssertionError();}catch(IllegalStateException expected){check(true,"early result rejected");}
        work.cancel();
        check(!work.complete()&&work.step(8,5,()->0,(x,z)->{throw new AssertionError();})==0,"cancelled search no-op");
        var exhausted=new SpawnCandidateWork(0,0,0,new Random(0),List.of(),0);
        int tries=0;while(!exhausted.complete())tries+=exhausted.step(8,5,()->0,(x,z)->false);
        check(tries==200&&exhausted.result()==null,"exact exhaustion");
        check(exhausted.step(8,5,()->0,(x,z)->true)==0,"completed no-op");
        var failed=new SpawnCandidateWork(0,0,10,new Random(2),List.of(),0);
        try{failed.step(8,5,()->0,(x,z)->{throw new IllegalStateException("probe");});throw new AssertionError();}
        catch(IllegalStateException expected){check(!failed.complete()&&failed.step(1,1,()->0,(x,z)->true)==0,"failure invalidates search");}
        var taken=new int[]{0,0};
        var snapshot=new SpawnCandidateWork(0,0,0,new Random(1),List.of(taken),100);
        taken[0]=10000;taken[1]=10000;
        while(!snapshot.complete())snapshot.step(8,5,()->0,(x,z)->true);
        check(snapshot.result()==null,"taken coordinates snapshotted");
        System.out.println("SPAWN CANDIDATE WORK "+passed+"/8 PASS");
    }
}
