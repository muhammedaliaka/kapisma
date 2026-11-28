package tr.kapisma.game;

public final class ChunkAdmissionTest {
    private static int passed;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    public static void main(String[] args){
        var p=new ChunkAdmission();
        check(p.update(0,51)==1,"high tick pressure immediately limits admission");
        check(p.update(0,30)==1&&p.update(0,30)==1&&p.update(0,30)==1&&p.update(0,30)==1,"no premature recovery");
        check(p.update(0,30)==2,"five healthy polls recover one slot");
        for(int i=0;i<10;i++)p.update(0,20);
        check(p.update(0,20)==4,"healthy recovery reaches four");
        check(p.update(1,20)==2,"pending spawn limits treasure load");
        check(p.update(3,20)==1,"three pending spawns limit to one");
        check(p.update(0,Double.NaN)==1&&p.update(0,-1)==1,"invalid telemetry conservative");
        check(ChunkAdmission.slots(1,4)==0&&ChunkAdmission.slots(2,2)==0,"no eviction or new tickets above cap");
        check(ChunkAdmission.slots(4,2)==2&&ChunkAdmission.slots(1,0)==1,"bounded admission and progress");
        var q=new ChunkAdmission();
        for(int i=0;i<4;i++)q.update(0,20);
        q.update(3,55);
        for(int i=0;i<4;i++)q.update(0,20);
        check(q.update(0,55)==1,"pressure resets recovery");
        int pending=64,active=0,steps=0;
        while(pending>0||active>0){
            int cap=q.update(4,80);
            int add=Math.min(pending,ChunkAdmission.slots(cap,active));
            pending-=add;active+=add;
            if(active>0)active--;
            if(++steps>64)throw new AssertionError("starvation");
        }
        check(steps==64,"64 requests drain even at minimum admission when work completes");
        try{ChunkAdmission.slots(0,1);throw new AssertionError();}catch(IllegalArgumentException e){check(true,"invalid cap rejected");}
        System.out.println("CHUNK ADMISSION "+passed+"/12 PASS");
    }
}
