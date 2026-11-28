package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.Position;
import static tr.kapisma.arena.CaravanTerrainLayout.*;
public final class CaravanTerrainLayoutTest {
    static int passed;
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);passed++;System.out.println("PASS "+msg);}
    static double length(List<Position> path){double n=0;for(int i=1;i<path.size();i++)n+=Math.sqrt(path.get(i-1).distanceSquared(path.get(i)));return n;}
    static boolean clearance(List<Position> path){
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);int samples=(int)Math.ceil(Math.sqrt(a.distanceSquared(b))/.1);
            for(int k=0;k<=samples;k++){
                double t=k/(double)samples,x=a.x()+(b.x()-a.x())*t,z=a.z()+(b.z()-a.z())*t;
                double radius=CaravanWagonGeometry.RADIUS+.02;
                for(int xx=(int)Math.floor(x-radius);xx<=(int)Math.floor(x+radius);xx++)
                    for(int zz=(int)Math.floor(z-radius);zz<=(int)Math.floor(z+radius);zz++){
                        if(cell(xx,0,zz)==Cell.AIR)return false;
                        for(int h=1;h<=4;h++)if(cell(xx,h,zz)!=Cell.AIR)return false;
                    }
            }
        }return true;
    }
    public static void main(String[] args){
        var s=safe(1);var r=risky(1);
        check(s.getFirst().equals(r.getFirst())&&s.getLast().equals(r.getLast()),"shared endpoints");
        check(length(s)>length(r),"risky route shorter");
        check(s.get(1).equals(r.get(1))&&s.get(4).equals(r.get(4)),"shared physical fork and merge");
        check(clearance(s),"entire safe corridor swept wagon clearance");
        check(clearance(r),"entire risk corridor swept wagon clearance");
        var attack=attackBase(1);
        check(Math.sqrt(segmentDistanceSq(attack.x(),attack.z(),r.get(2),r.get(3)))<Math.sqrt(segmentDistanceSq(attack.x(),attack.z(),s.get(2),s.get(3))),"risky road nearer attacker base");
        check(cell(0,3,-12)==Cell.AIR&&cell(0,5,-12)==Cell.TUNNEL_ROOF,"tunnel clear passage under roof");
        check(cell(0,7,-16)!=Cell.AIR,"tunnel sides have mass");
        for(var b:List.of(escortBase(1),attackBase(1))){
            boolean ok=true;
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                int x=(int)Math.floor(b.x())+dx,z=(int)Math.floor(b.z())+dz;
                var ground=cell(x,0,z);ok&=ground==Cell.BASE||ground==Cell.ESCORT_MARK||ground==Cell.EXTRACTION_MARK;
                for(int h=1;h<=3;h++)ok&=cell(x,h,z)==Cell.AIR;
            }
            check(ok,"base and horse area clear");
        }
        check(cell(-33,0,-9)==Cell.ESCORT_MARK&&cell(-29,0,-9)==Cell.BASE,"purple escort perimeter, open interior");
        check(cell(3,0,15)==Cell.EXTRACTION_MARK&&cell(0,0,12)==Cell.EXTRACTION_MARK&&cell(0,0,15)==Cell.BASE,"orange extraction diamond and clear center");
        check(cell(24,0,-2)==Cell.FINISH_MARK&&cell(24,0,2)==Cell.FINISH_MARK,"gold finish stripe");
        check(cell(-16,0,-5)==Cell.SAFE_MARK&&cell(-10,0,3)==Cell.RISKY_MARK,"route guidance stripes");
        int marks=0;boolean legal=true;
        for(int x=-36;x<=36;x++)for(int z=-24;z<=24;z++){
            var c=cell(x,0,z);
            if(c==Cell.ESCORT_MARK||c==Cell.EXTRACTION_MARK||c==Cell.FINISH_MARK||c==Cell.SAFE_MARK||c==Cell.RISKY_MARK){
                marks++;
                for(int h=1;h<=3;h++)legal&=cell(x,h,z)==Cell.AIR;
                if(c==Cell.EXTRACTION_MARK)legal&=Math.hypot(x+.5-attack.x(),z+.5-attack.z())<=3.5;
                if(c==Cell.ESCORT_MARK)legal&=base(x,z);
                if(c==Cell.FINISH_MARK||c==Cell.SAFE_MARK||c==Cell.RISKY_MARK)legal&=road(x,z);
            }
        }
        check(marks>=50&&legal,"every marker is clear, inside its objective and walkable");
        int structure=0;boolean same=true,clear=true;
        for(int dx=-3;dx<=3;dx++)for(int depth=0;depth<=2;depth++)for(int h=1;h<=5;h++){
            var e=landmark(-29+dx,h,-15-depth,true);var a=landmark(dx,h,21+depth,false);
            same&=(e==Cell.AIR)==(a==Cell.AIR);
            if(e!=Cell.AIR){
                structure++;
                clear&=!base(-29+dx,-15-depth)&&!base(dx,21+depth)&&!road(-29+dx,-15-depth)&&!road(dx,21+depth);
                clear&=cell(-29+dx,h,-15-depth)==e&&cell(dx,h,21+depth)==a;
            }else{
                // Hills must not fill the shelter interior.
                clear&=cell(-29+dx,h,-15-depth)==Cell.AIR&&cell(dx,h,21+depth)==Cell.AIR;
            }
        }
        check(same&&structure==48,"identical 48-block landmark collision layout");
        check(clear,"landmarks outside bases and roads with open interiors");
        check(landmark(-32,1,-15,true)==Cell.ESCORT_POST&&landmark(3,1,21,false)==Cell.ATTACK_POST,"distinct role architecture");
        check(landmark(-29,5,-16,true)==Cell.ESCORT_CANOPY&&landmark(0,5,22,false)==Cell.ATTACK_CANOPY,"color-matched canopy");
        for(var b:List.of(escortBase(1),attackBase(1))){
            boolean spawnClear=true;
            for(int slot=0;slot<4;slot++)for(double z: new double[]{b.z()-2,b.z()+3}){
                double x=b.x()+slot*1.8-2.7;
                for(int xx=(int)Math.floor(x-.72);xx<=(int)Math.floor(x+.72);xx++)
                    for(int zz=(int)Math.floor(z-.72);zz<=(int)Math.floor(z+.72);zz++){
                        spawnClear&=cell(xx,0,zz)!=Cell.AIR;
                        for(int h=1;h<=3;h++)spawnClear&=cell(xx,h,zz)==Cell.AIR;
                    }
            }
            check(spawnClear,"all four players and four horses retain clear footprints");
        }
        for(int x:new int[]{-6,6}){
            check(cell(x,1,-16)==Cell.TUNNEL_PORTAL&&cell(x,1,-8)==Cell.TUNNEL_PORTAL&&cell(x,5,-12)==Cell.TUNNEL_KEYSTONE,"masonry portal and keystone "+x);
            boolean aperture=true;
            for(int z=-15;z<=-9;z++)for(int h=1;h<=4;h++)aperture&=cell(x,h,z)==Cell.AIR;
            check(aperture,"seven-block wide, four-block high portal opening "+x);
        }
        int rock=0,grass=0,roof=0;
        for(int x=-36;x<=36;x++)for(int z=-24;z<=24;z++)for(int y=1;y<HEIGHT;y++){
            var c=cell(x,y,z);if(c==Cell.ROCK)rock++;if(c==Cell.GRASS)grass++;if(c==Cell.TUNNEL_ROOF)roof++;
        }
        check(rock>100&&grass>100&&roof>10,"nonflat hills valley tunnel");
        boolean unchanged=true,valid=true,pinned=true,foliageValid=true;int scenic=0,columns=0,high=0,trunks=0,leaves=0,trees=0;var tops=new HashSet<Integer>();
        for(int x=-SCENIC_X;x<=SCENIC_X;x++)for(int z=-SCENIC_Z;z<=SCENIC_Z;z++){
            if(Math.abs(x)<=PLAY_X&&Math.abs(z)<=PLAY_Z){
                for(int h=0;h<HEIGHT;h++)unchanged&=renderCell(x,h,z)==cell(x,h,z);
                continue;
            }
            columns++;pinned&=Math.floorDiv(x,16)>=Math.floorDiv(-PLAY_X,16)&&Math.floorDiv(x,16)<=Math.floorDiv(PLAY_X,16)&&Math.floorDiv(z,16)>=Math.floorDiv(-PLAY_Z,16)&&Math.floorDiv(z,16)<=Math.floorDiv(PLAY_Z,16);
            boolean gap=false;int top=-1;
            for(int h=0;h<HEIGHT;h++){
                var c=renderCell(x,h,z);
                if(c==Cell.SCENIC_TRUNK){trunks++;foliageValid&=treeSite(x,z)&&h>scenicHeight(x,z)&&h<=scenicHeight(x,z)+3;}
                else if(c==Cell.SCENIC_LEAVES){leaves++;foliageValid&=h>scenicHeight(x,z);}
                else if(c==Cell.AIR)gap=true;
                else{valid&=!gap&&(c==Cell.GRASS||c==Cell.DIRT||c==Cell.ROCK);scenic++;top=h;}
            }
            valid&=top>=0&&renderCell(x,top,z)==Cell.GRASS;
            tops.add(top);if(top>=8)high++;
            if(treeSite(x,z)){trees++;foliageValid&=renderCell(x,top+1,z)==Cell.SCENIC_TRUNK&&renderCell(x,top+3,z)==Cell.SCENIC_TRUNK&&renderCell(x,top+4,z)==Cell.SCENIC_LEAVES;}
        }
        check(unchanged,"all playable geometry exactly unchanged");
        check(columns==1730&&pinned,"scenic ring only existing 24 pinned chunks");
        check(valid&&tops.size()>=7&&high>100,"solid layered ridge and varied skyline");
        check(renderCell(44,0,0)==Cell.AIR&&renderCell(0,0,31)==Cell.AIR&&renderCell(43,HEIGHT,30)==Cell.AIR,"bounded scenery");
        check(foliageValid&&trees>=8&&trunks==trees*3&&leaves>=trees*4,"spaced supported conifers outside playable area");
        System.out.println("SCENIC_ANALYSIS columns="+columns+" blocks="+scenic+" heights="+tops+" high="+high+" trees="+trees+" trunks="+trunks+" leaves="+leaves);
        System.out.println("CARAVAN_TERRAIN "+passed+"/"+passed+" PASS");
    }
}
