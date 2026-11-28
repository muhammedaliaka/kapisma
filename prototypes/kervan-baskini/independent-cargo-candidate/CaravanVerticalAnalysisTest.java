package tr.kapisma.arena;
import java.util.*;
import static tr.kapisma.arena.CaravanTerrainLayout.*;
import tr.kapisma.arena.MatchRoster.Position;
/** Full-block conservative surface graph, not a jump or combat simulator. */
public final class CaravanVerticalAnalysisTest {
    record P(int x,int y,int z){}
    static final Set<P> nodes=new HashSet<>();
    static int passed;
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);passed++;System.out.println("PASS "+m);}
    static boolean solid(int x,int y,int z){return cell(x,y,z)!=Cell.AIR;}
    static List<P> adj(P p){
        var result=new ArrayList<P>();for(int[] m:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy=-1;dy<=1;dy++){
            var n=new P(p.x+m[0],p.y+dy,p.z+m[1]);if(!nodes.contains(n))continue;
            if(dy>0&&solid(p.x,p.y+2,p.z))continue;
            if(dy<0&&solid(n.x,n.y+2,n.z))continue;
            result.add(n);
        }return result;
    }
    static Map<P,Integer> distances(Collection<P> starts){
        var d=new HashMap<P,Integer>();var q=new ArrayDeque<P>();
        for(var p:starts)if(nodes.contains(p)&&d.putIfAbsent(p,0)==null)q.add(p);
        while(!q.isEmpty()){var p=q.remove();for(var n:adj(p))if(d.putIfAbsent(n,d.get(p)+1)==null)q.add(n);}
        return d;
    }
    static boolean crate(P p,int ceiling){
        return p.x>=-36&&p.x<=36&&p.z>=-24&&p.z<=24&&p.y>=0&&p.y+2<=ceiling&&solid(p.x,p.y-1,p.z)&&!solid(p.x,p.y,p.z);
    }
    static boolean landing(P p,int ceiling){
        if(crate(p,ceiling))return true;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-4;dy<=1;dy++)
            if(dx*dx+dy*dy+dz*dz<=64&&crate(new P(p.x+dx,p.y+dy,p.z+dz),ceiling))return true;
        return false;
    }
    static void route(String name,List<Position> route,Map<P,Integer> d){
        int samples=0,alternate=0,max=0;long sum=0;
        for(int i=1;i<route.size();i++){
            var a=route.get(i-1);var b=route.get(i);int n=(int)Math.ceil(Math.sqrt(a.distanceSquared(b)));
            for(int j=0;j<n;j++){
                double f=(j+.5)/n;var p=new P((int)Math.floor(a.x()+(b.x()-a.x())*f),1,(int)Math.floor(a.z()+(b.z()-a.z())*f));
                Integer dist=d.get(p);if(dist==null)throw new AssertionError("wagon sample unreachable "+p);
                int exits=0;for(var next:adj(p))if(d.getOrDefault(next,Integer.MAX_VALUE)<=dist+1)exits++;
                if(exits>=2)alternate++;samples++;sum+=dist;max=Math.max(max,dist);
            }
        }
        System.out.printf(Locale.ROOT,"VERTICAL_ROUTE %s samples=%d meanExtractSteps=%.3f maxExtractSteps=%d alternateLocalSteps=%d%n",name,samples,(double)sum/samples,max,alternate);
        check(alternate==samples,"two local onward choices for every "+name+" sample");
    }
    static boolean line(P observer,Position target){
        double dx=target.x()-(observer.x+.5),dz=target.z()-(observer.z+.5),dy=(target.y()+1.0)-(observer.y+1.62);
        int steps=(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)/.25);
        for(int k=1;k<steps;k++){
            double f=k/(double)steps;
            if(solid((int)Math.floor(observer.x+.5+dx*f),(int)Math.floor(observer.y+1.62+dy*f),(int)Math.floor(observer.z+.5+dz*f)))return false;
        }return true;
    }
    static void sight(String name,List<Position> path,Set<P> surfaces){
        long ground=0,groundSeen=0,high=0,highSeen=0;
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);int n=(int)Math.ceil(Math.sqrt(a.distanceSquared(b))/3);
            for(int j=0;j<n;j++){
                double f=(j+.5)/n;var target=new Position(a.x()+(b.x()-a.x())*f,1,a.z()+(b.z()-a.z())*f);
                for(var p:surfaces){
                    double dist=Math.hypot(p.x+.5-target.x(),p.z+.5-target.z());
                    if(dist<6||dist>20)continue;
                    boolean v=line(p,target);
                    if(p.y==1){ground++;if(v)groundSeen++;}else if(p.y>=4){high++;if(v)highSeen++;}
                }
            }
        }
        System.out.printf(Locale.ROOT,"VERTICAL_SIGHT %s ground=%d/%d high=%d/%d%n",name,groundSeen,ground,highSeen,high);
        check(ground>0&&high>0,"both height groups sampled "+name);
    }
    public static void main(String[] args){
        for(int x=-36;x<=36;x++)for(int z=-24;z<=24;z++)for(int y=1;y<=HEIGHT;y++)if(solid(x,y-1,z)&&!solid(x,y,z)&&!solid(x,y+1,z))nodes.add(new P(x,y,z));
        var goals=nodes.stream().filter(p->p.y==1&&Math.hypot(p.x+.5-.5,p.z+.5-15.5)<=3.5).toList();
        var d=distances(goals);var e=distances(List.of(new P(-29,1,-9)));
        int high=0,oldBad=0,newBad=0,oldDirect=0,maxY=0,samples=0;P example=null;
        for(var p:d.keySet()){
            maxY=Math.max(maxY,p.y);if(p.y>1)high++;
            if(!crate(p,10)){oldDirect++;System.out.println("VERTICAL_DIRECT_REJECT "+p);}
            for(int dy=0;dy<=2;dy++){
                var at=new P(p.x,p.y+dy,p.z);if(solid(at.x,at.y,at.z))continue;samples++;
                if(!landing(at,10)){oldBad++;example=at;}
                if(!landing(at,HEIGHT+5))newBad++;
            }
        }
        System.out.printf("VERTICAL_AUDIT nodes=%d reachableAttack=%d reachableEscort=%d elevated=%d maxFeet=%d samples=%d oldDirectDenied=%d oldNoLanding=%d expandedNoLanding=%d example=%s%n",nodes.size(),d.size(),e.size(),high,maxY,samples,oldDirect,oldBad,newBad,example);
        check(d.keySet().equals(e.keySet()),"same reachable stepped component from both roles");
        check(newBad==0,"terrain-derived ceiling covers every sampled landing");
        check(oldDirect>0&&d.keySet().stream().allMatch(p->crate(p,HEIGHT+5)),"terrain-derived ceiling accepts every reachable standing surface");
        route("SAFE",safe(1),d);route("RISKY",risky(1),d);
        sight("SAFE",safe(1),d.keySet());sight("RISKY",risky(1),d.keySet());
        System.out.println("CARAVAN_VERTICAL "+passed+"/"+passed+" PASS");
    }
}
