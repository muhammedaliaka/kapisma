package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
import static tr.kapisma.arena.CaravanTerrainLayout.*;
/** Compare possible physical cover before applying it; ground-level eye rays only. */
public final class CaravanSpawnCoverAnalysisTest {
    record C(int x,int z){}
    record Exposure(int seen,int total,int maxExit,int minExits,int reachable){}
    static int passed;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);passed++;System.out.println("PASS "+text);}
    static Set<C> layout(int variant){
        var out=new HashSet<C>();
        int[] wings=variant==1?new int[]{-4,-3,-2,2,3,4}:variant==2?new int[]{-5,-4,-3,3,4,5}:new int[]{-4,-3,-2,1,2,3};
        for(int k:wings){out.add(new C(-24,-9+k));out.add(new C(k,10));}
        return Set.copyOf(out);
    }
    static boolean screen(Cell c){return c==Cell.ESCORT_SCREEN||c==Cell.ATTACK_SCREEN;}
    static boolean baselineAir(int x,int y,int z){var c=cell(x,y,z);return c==Cell.AIR||screen(c);}
    static boolean open(C c,Set<C> screens){
        return c.x>=-36&&c.x<=36&&c.z>=-24&&c.z<=24&&!screens.contains(c)&&cell(c.x,0,c.z)!=Cell.AIR&&baselineAir(c.x,1,c.z)&&baselineAir(c.x,2,c.z);
    }
    static Map<C,Integer> walkFrom(C start,Set<C> screens){
        var d=new HashMap<C,Integer>();if(!open(start,screens))return d;
        var q=new ArrayDeque<C>();q.add(start);d.put(start,0);
        while(!q.isEmpty()){var c=q.remove();for(var n:List.of(new C(c.x+1,c.z),new C(c.x-1,c.z),new C(c.x,c.z+1),new C(c.x,c.z-1)))if(open(n,screens)&&!d.containsKey(n)){d.put(n,d.get(c)+1);q.add(n);}}
        return d;
    }
    static boolean sees(Position p,C c,Set<C> screens){
        double dx=c.x+.5-p.x(),dz=c.z+.5-p.z();int n=(int)Math.ceil(Math.hypot(dx,dz)/.15);
        for(int i=1;i<n;i++){double f=i/(double)n;var a=new C((int)Math.floor(p.x()+dx*f),(int)Math.floor(p.z()+dz*f));if(screens.contains(a)||!baselineAir(a.x,2,a.z))return false;}
        return true;
    }
    static Exposure measure(boolean escort,Set<C> screens){
        var b=escort?escortBase(1):attackBase(1);int seen=0,total=0,maxExit=0,minExits=4,reach=Integer.MAX_VALUE;
        for(int s=0;s<4;s++){
            var p=new Position(b.x()+s*1.8-2.7,1,b.z()-2);
            var d=walkFrom(new C((int)Math.floor(p.x()),(int)Math.floor(p.z())),screens);
            reach=Math.min(reach,d.size());int nearest=Integer.MAX_VALUE;boolean[] exits=new boolean[4];
            for(var e:d.entrySet()){var c=e.getKey();double dx=c.x+.5-b.x(),dz=c.z+.5-b.z();
                if(Math.abs(dx)>5||Math.abs(dz)>5){
                    nearest=Math.min(nearest,e.getValue());
                    if(e.getValue()<=12){if(dx>5)exits[0]=true;if(dx< -5)exits[1]=true;if(dz>5)exits[2]=true;if(dz< -5)exits[3]=true;}
                }
            }
            int exitCount=0;for(boolean v:exits)if(v)exitCount++;
            minExits=Math.min(minExits,exitCount);maxExit=Math.max(maxExit,nearest);
            // Fixed baseline observer set, excluding candidate walls from the denominator change.
            var baseline=walkFrom(new C((int)Math.floor(p.x()),(int)Math.floor(p.z())),Set.of());
            for(var c:baseline.keySet()){
                double dist=Math.hypot(c.x+.5-p.x(),c.z+.5-p.z());
                if(base(c.x,c.z)||dist<6||dist>20)continue;
                total++;if(open(c,screens)&&sees(p,c,screens))seen++;
            }
        }
        return new Exposure(seen,total,maxExit,minExits,reach);
    }
    static void print(String tag,boolean e,Exposure v){System.out.println("COVER_ANALYSIS "+tag+" "+(e?"ESCORT":"ATTACK")+" seen="+v.seen+" total="+v.total+" maxExit="+v.maxExit+" minExits="+v.minExits+" reachable="+v.reachable);}
    public static void main(String[] args){
        for(boolean e:new boolean[]{true,false})print("BASELINE",e,measure(e,Set.of()));
        for(int k=1;k<=3;k++){
            var walls=layout(k);boolean legal=walls.size()==12;
            for(var c:walls)legal&=!road(c.x,c.z)&&baselineAir(c.x,1,c.z)&&baselineAir(c.x,2,c.z)&&cell(c.x,0,c.z)==Cell.BASE;
            System.out.println("COVER_CANDIDATE "+k+" legal="+legal);
            for(boolean e:new boolean[]{true,false})print("V"+k,e,measure(e,walls));
        }
        var selected=layout(1);
        var actual=new HashSet<C>();
        for(int x=-36;x<=36;x++)for(int z=-24;z<=24;z++)if(screen(cell(x,1,z))){
            actual.add(new C(x,z));
            if(!screen(cell(x,2,z))||cell(x,3,z)!=Cell.AIR||road(x,z))throw new AssertionError("cover height or route");
        }
        check(selected.equals(actual)&&actual.size()==12,"actual selected six screen columns per role");
        for(boolean e:new boolean[]{true,false}){
            var before=measure(e,Set.of());var after=measure(e,actual);
            check(after.seen<before.seen*.75&&after.total==before.total,"at least 25 percent relative exposure reduction "+e);
            check(after.maxExit==before.maxExit&&after.minExits==4,"four exit directions and unchanged nearest exit "+e);
            check(after.reachable==before.reachable-12,"only physical wall footprints lost, no inaccessible ground "+e);
        }
        var attack=CaravanTerrainAnalysisTest.distances(attackBase(1));
        var escort=CaravanTerrainAnalysisTest.distances(escortBase(1));
        check(CaravanTerrainAnalysisTest.reachable(attack)==2694&&CaravanTerrainAnalysisTest.reachable(escort)==2694,"actual ground graph matches candidate");
        System.out.println("CARAVAN_COVER_ANALYSIS "+passed+"/"+passed+" PASS");
    }
}
