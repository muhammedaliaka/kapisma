package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
import static tr.kapisma.arena.CaravanTerrainLayout.*;
/** Conservative ground-plane navigation and LOS proxies, not combat or win-rate prediction. */
public final class CaravanTerrainAnalysisTest {
    static final int W=73,D=49;
    static int passed;
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);passed++;System.out.println("PASS "+msg);}
    static boolean walk(int x,int z){return x>=-36&&x<=36&&z>=-24&&z<=24&&cell(x,0,z)!=Cell.AIR&&cell(x,1,z)==Cell.AIR&&cell(x,2,z)==Cell.AIR;}
    static int[][] distances(Position p){
        int[][] d=new int[W][D];for(var row:d)Arrays.fill(row,-1);
        int x=(int)Math.floor(p.x()),z=(int)Math.floor(p.z());
        if(!walk(x,z))throw new AssertionError("spawn blocked");
        var q=new ArrayDeque<int[]>();q.add(new int[]{x,z});d[x+36][z+24]=0;
        int[][] moves={{1,0},{-1,0},{0,1},{0,-1}};
        while(!q.isEmpty()){
            var n=q.removeFirst();
            for(var m:moves){int xx=n[0]+m[0],zz=n[1]+m[1];
                if(walk(xx,zz)&&d[xx+36][zz+24]<0){d[xx+36][zz+24]=d[n[0]+36][n[1]+24]+1;q.add(new int[]{xx,zz});}
            }
        }
        return d;
    }
    static int reachable(int[][] d){int count=0;for(var row:d)for(int n:row)if(n>=0)count++;return count;}
    static int approach(int[][] d,Position p){
        int best=Integer.MAX_VALUE;
        for(int x=(int)Math.floor(p.x()-3.5);x<=(int)Math.floor(p.x()+3.5);x++)
            for(int z=(int)Math.floor(p.z()-3.5);z<=(int)Math.floor(p.z()+3.5);z++){
                if(walk(x,z)&&Math.hypot(x+.5-p.x(),z+.5-p.z())<=3.5&&d[x+36][z+24]>=0)best=Math.min(best,d[x+36][z+24]);
            }
        return best;
    }
    static boolean visible(Position observer,Position target){
        double dx=target.x()-observer.x(),dz=target.z()-observer.z();
        int n=(int)Math.ceil(Math.hypot(dx,dz)/.2);
        for(int i=1;i<n;i++){
            double f=i/(double)n;int x=(int)Math.floor(observer.x()+dx*f),z=(int)Math.floor(observer.z()+dz*f);
            if(cell(x,2,z)!=Cell.AIR)return false;
        }
        return true;
    }
    record Metrics(double length,int samples,int visible,double meanAttack,double meanEscort,int maxAttack,int maxEscort){}
    static Metrics measure(List<Position> path,int[][] attack,int[][] escort){
        double len=0,sa=0,se=0;int count=0,vis=0,ma=0,me=0;
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);double l=Math.sqrt(a.distanceSquared(b));len+=l;
            int n=(int)Math.ceil(l);
            for(int j=0;j<n;j++){
                double f=(j+.5)/n;
                var p=new Position(a.x()+(b.x()-a.x())*f,1,a.z()+(b.z()-a.z())*f);
                int ad=approach(attack,p),ed=approach(escort,p);
                if(ad==Integer.MAX_VALUE||ed==Integer.MAX_VALUE)throw new AssertionError("inaccessible wagon sample "+p);
                sa+=ad;se+=ed;ma=Math.max(ma,ad);me=Math.max(me,ed);
                if(visible(attackBase(1),p))vis++;count++;
            }
        }
        return new Metrics(len,count,vis,sa/count,se/count,ma,me);
    }
    static void report(String name,Metrics m){System.out.printf(Locale.ROOT,"MAP_ANALYSIS %s length=%.3f driveSeconds=%.3f samples=%d baseLOS=%d meanAttackWalk=%.3f meanEscortWalk=%.3f maxAttackWalk=%d maxEscortWalk=%d%n",name,m.length,m.length/1.5,m.samples,m.visible,m.meanAttack,m.meanEscort,m.maxAttack,m.maxEscort);}
    public static void main(String[] args){
        var attack=distances(attackBase(1));var escort=distances(escortBase(1));
        var a=measure(safe(1),attack,escort);var b=measure(risky(1),attack,escort);
        report("SAFE",a);report("RISKY",b);
        System.out.printf("MAP_ANALYSIS groundReachAttack=%d groundReachEscort=%d%n",reachable(attack),reachable(escort));
        check(reachable(attack)==reachable(escort),"both roles share a connected ground component");
        check(a.length>b.length,"safe route longer at fixed speed");
        check(b.meanAttack<a.meanAttack,"risky route has lower attacker approach cost");
        check(b.visible/(double)b.samples>a.visible/(double)a.samples,"risky route more visible from attack base");
        check(a.maxAttack<100&&b.maxAttack<100&&a.maxEscort<100&&b.maxEscort<100,"all sampled route positions have ground access for both roles");
        System.out.println("CARAVAN_ANALYSIS "+passed+"/"+passed+" PASS");
    }
}
