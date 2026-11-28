package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
public final class ModeRulesTest {
    private static int passed;
    private static final UUID M=UUID.randomUUID(),R=UUID.randomUUID(),B=UUID.randomUUID(),O=UUID.randomUUID();
    private static final MatchRoster ROSTER=new MatchRoster(M,List.of(R),List.of(B));
    private static final Position ZERO=new Position(0,0,0),FAR=new Position(0,0,30);
    private static Presence p(UUID id,Position pos){return new Presence(id,M,true,true,pos);}
    private static List<Presence> both(){return List.of(p(R,ZERO),p(B,FAR));}
    private static void ok(boolean condition,String name){if(!condition)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    public static void main(String[] args){
        caravan();clues();build();flight();radio();auction();System.out.println("PASS total "+passed);
    }
    static void caravan(){
        var g=new CaravanRules(ROSTER,ZERO);
        var initial=g.state();
        for(int i=0;i<60;i++)g.tick(List.of(p(R,ZERO)));
        ok(initial.equals(g.state()),"caravan missing side pause");
        for(int i=0;i<20;i++)g.tick(List.of(p(R,ZERO),p(B,ZERO)));
        ok(g.state().distance()==0&&g.state().cargo()==20,"caravan contested");
        for(int i=0;i<20;i++)g.tick(List.of(p(R,FAR),p(B,ZERO)));
        ok(g.state().cargo()==19&&g.state().distance()==0,"caravan raider damage");
        for(int i=0;i<48*20;i++)g.tick(List.of(p(R,g.position()),p(B,FAR)));
        ok(g.state().phase()==CaravanRules.Phase.BREAK&&g.state().red().distance()==48&&g.state().red().cargo()==19,"caravan natural delivery");
        for(int i=0;i<100;i++)g.tick(List.of(p(R,ZERO)));
        ok(g.state().breakTicks()==100,"caravan break pause");
        for(int i=0;i<100;i++)g.tick(both());
        ok(g.state().leg()==2&&g.escort()==Side.BLUE&&g.state().cargo()==20&&g.state().distance()==0,"caravan role swap reset");
        for(int i=0;i<20*20;i++)g.tick(List.of(p(R,g.position()),p(B,FAR)));
        ok(g.state().phase()==CaravanRules.Phase.ENDED&&g.state().blue().cargo()==0&&g.state().winner()==Side.RED,"caravan destruction and result");
        ok(new CaravanRules.Leg(48,20,10).compareTo(new CaravanRules.Leg(48,20,9))>0&&new CaravanRules.Leg(48,19,10).compareTo(new CaravanRules.Leg(48,20,0))<0,"caravan tie-break");
    }
    static void clues(){
        List<ClueRaceRules.Clue> red=new ArrayList<>(),blue=new ArrayList<>();
        for(int i=0;i<4;i++){red.add(new ClueRaceRules.Clue("R"+i,new Position(i*10,0,0)));blue.add(new ClueRaceRules.Clue("B"+i,new Position(i*10,0,30)));}
        var g=new ClueRaceRules(ROSTER,red,blue);
        ok(g.hint(R).orElseThrow().label().equals("R0")&&g.hint(B).orElseThrow().label().equals("B0")&&g.hint(O).isEmpty(),"clue private current route");
        ok(!g.request(R,M,1)&&!g.request(O,M,0)&&!g.request(R,UUID.randomUUID(),0),"clue stale and outsider denied");
        g.request(R,M,0);g.tick(List.of(p(R,red.get(1).target()),p(B,FAR)));
        ok(g.state().red()==0,"clue skip rejected");
        g.request(R,M,0);g.discardPending();g.tick(both());
        ok(g.state().red()==0,"clue rapid reconnect queue invalidated");
        g.request(R,M,0);g.tick(List.of(p(R,ZERO)));
        g.tick(both());ok(g.state().red()==0,"clue pending dropped on pause");
        for(int i=0;i<4;i++){
            ok(g.request(R,M,i)&&g.request(B,M,i)&&!g.request(R,M,i),"clue requests once "+i);
            g.tick(List.of(p(R,red.get(i).target()),p(B,blue.get(i).target())));
            ok(g.state().red()==i+1&&g.state().blue()==i+1,"clue synchronized progression "+i);
        }
        ok(g.state().ended()&&g.state().winner()==null&&g.hint(R).isEmpty(),"clue simultaneous final tie");
    }
    static void build(){
        var extra=UUID.randomUUID();
        var odd=new BuildAssaultRules(new MatchRoster(M,List.of(R,extra),List.of(B)));
        ok(odd.supply(R)==64&&odd.supply(extra)==64&&odd.supply(B)==0,"odd build shared budget first leg");
        var oddObs=List.of(p(R,ZERO),p(B,ZERO),p(extra,ZERO));
        for(int i=0;i<600;i++)odd.tick(oddObs);
        ok(odd.capture(p(B,ZERO),new BuildAssaultRules.Cell(0,0,0),()->true),"odd build swap");
        ok(odd.supply(B)==128&&odd.supply(R)==0&&odd.supply(extra)==0,"odd build same total second leg");
        for(int count=2;count<=8;count++){
            var red=new ArrayList<UUID>();var blue=new ArrayList<UUID>();
            for(int i=0;i<count;i++)(i%2==0?red:blue).add(UUID.randomUUID());
            var r=new MatchRoster(M,red,blue);var g2=new BuildAssaultRules(r);
            int total=red.stream().mapToInt(g2::supply).sum();
            var obs=r.members().stream().map(id->p(id,ZERO)).toList();
            for(int i=0;i<600;i++)g2.tick(obs);
            ok(g2.capture(p(blue.getFirst(),ZERO),new BuildAssaultRules.Cell(0,0,0),()->true)&&total==128*Math.min(red.size(),blue.size())&&blue.stream().mapToInt(g2::supply).sum()==total,"balanced build budget "+count);
        }
        var g=new BuildAssaultRules(ROSTER);
        var cell=new BuildAssaultRules.Cell(1,0,0);var core=new BuildAssaultRules.Cell(0,0,0);
        g.tick(both());
        ok(!g.place(p(B,ZERO),cell,()->true)&&!g.place(p(R,ZERO),core,()->true)&&!g.place(p(R,ZERO),new BuildAssaultRules.Cell(11,0,0),()->true),"build role and boundary");
        ok(!g.place(p(R,ZERO),cell,()->false)&&g.supply(R)==128,"build failed world write no charge");
        ok(g.place(p(R,ZERO),cell,()->true)&&!g.place(p(R,ZERO),cell,()->true)&&g.supply(R)==127,"build atomic placement once");
        g.invalidateReadiness();
        ok(!g.place(p(R,ZERO),new BuildAssaultRules.Cell(2,0,0),()->true),"build disconnect immediately invalidates");
        int left=g.state().ticksLeft();
        g.tick(List.of(p(R,ZERO)));ok(g.state().ticksLeft()==left,"build paused time");
        for(int i=0;i<left;i++)g.tick(both());
        ok(g.state().phase()==BuildAssaultRules.Phase.ASSAULT&&!g.place(p(R,ZERO),new BuildAssaultRules.Cell(2,0,0),()->true),"build stage transition");
        ok(!g.breakWall(p(R,ZERO),cell,()->true)&&g.breakWall(p(B,ZERO),cell,()->true),"build attacker wall break");
        ok(!g.capture(p(R,ZERO),core,()->true)&&!g.capture(p(B,ZERO),core,()->false),"build core unauthorized or failed");
        ok(g.capture(p(B,ZERO),core,()->true)&&g.state().leg()==2&&g.supply(B)==128&&g.supply(R)==0,"build core role swap");
        for(int i=0;i<600+1800;i++)g.tick(both());
        ok(g.state().phase()==BuildAssaultRules.Phase.ENDED&&g.state().red()==0&&g.state().winner()==Side.BLUE,"build natural timeout comparison");
    }
    static FlightRaceRules.Sample sample(UUID id,double x,boolean glide,boolean teleport){return new FlightRaceRules.Sample(id,M,new Position(x,0,0),true,glide,teleport);}
    static void flight(){
        var gates=List.of(new FlightRaceRules.Gate(10,0,0,3,3),new FlightRaceRules.Gate(30,0,0,3,3),new FlightRaceRules.Gate(50,0,0,3,3),new FlightRaceRules.Gate(70,0,0,3,3));
        var g=new FlightRaceRules(M,List.of(R,B),gates);
        ok(FlightRaceRules.crosses(new Position(8,0,0),new Position(12,0,0),gates.getFirst())&&!FlightRaceRules.crosses(new Position(12,0,0),new Position(8,0,0),gates.getFirst())&&!FlightRaceRules.crosses(new Position(8,4,0),new Position(12,4,0),gates.getFirst()),"flight swept forward geometry");
        g.tick(List.of(sample(R,8,false,false)));g.tick(List.of(sample(R,12,false,false)));
        ok(g.progress(R)==0,"flight no ground progress");
        g.tick(List.of(sample(R,28,true,false)));g.tick(List.of(sample(R,32,true,false)));
        ok(g.progress(R)==0,"flight future gate denied");
        g.tick(List.of(sample(R,8,true,false)));g.tick(List.of(sample(R,12,true,true)));
        ok(g.progress(R)==0,"flight teleport denied");
        g.tick(List.of(sample(R,0,true,false)));g.tick(List.of(sample(R,20,true,false)));
        ok(g.progress(R)==0,"flight implausible movement denied");
        g.tick(List.of(sample(R,8,true,false),sample(R,8,true,false)));g.tick(List.of(sample(R,12,true,false)));
        ok(g.progress(R)==0,"flight duplicate observations denied");
        for(int i=0;i<4;i++){
            int x=10+20*i;
            g.tick(List.of(sample(R,x-2,true,false),sample(B,x-2,true,false)));
            g.tick(List.of(sample(R,x+2,true,false),sample(B,x+2,true,false)));
            ok(g.progress(R)==i+1&&g.progress(B)==i+1,"flight ordered gate "+(i+1));
        }
        ok(g.state().ended()&&g.state().winners().equals(Set.of(R,B)),"flight same tick co-winners");
    }
    static void radio(){
        var g=new RadioPuzzleRules(ROSTER,new Random(42));
        int a=g.hint(R).orElseThrow().fragment(),b=g.hint(B).orElseThrow().fragment(),code=a*100+b;
        ok(a>=10&&a<100&&b>=10&&b<100&&g.hint(O).isEmpty(),"radio private role fragments");
        ok(!g.submit(O,M,1,code)&&!g.submit(R,M,2,code)&&!g.submit(R,M,1,999),"radio invalid guess denied");
        g.submit(R,M,1,code);g.discardPending();g.tick(both());
        ok(g.state().round()==1,"radio rapid reconnect queue invalidated");
        g.submit(R,M,1,code);g.tick(List.of(p(R,ZERO)));g.tick(both());
        ok(g.state().round()==1&&g.state().attempts()==3,"radio pending cleared on pause");
        int wrong=code==1000?1001:1000;
        ok(g.submit(R,M,1,wrong)&&g.submit(B,M,1,wrong),"radio simultaneous queued");
        g.tick(both());
        ok(g.state().attempts()==2&&g.state().cooldown()==40&&!g.submit(R,M,1,code),"radio one charged guess cooldown");
        for(int i=0;i<40;i++)g.tick(both());
        for(int round=1;round<=3;round++){
            code=g.hint(R).orElseThrow().fragment()*100+g.hint(B).orElseThrow().fragment();
            ok(g.submit(R,M,round,code),"radio submit round "+round);g.tick(both());
            if(round<3)ok(g.state().round()==round+1&&g.state().ticksLeft()==1800&&g.state().attempts()==3,"radio fresh full timer "+round);
        }
        ok(g.state().phase()==RadioPuzzleRules.Phase.WON&&g.hint(R).isEmpty(),"radio all rounds won");
        var timeout=new RadioPuzzleRules(ROSTER,new Random(1));
        for(int i=0;i<1800;i++)timeout.tick(both());
        ok(timeout.state().phase()==RadioPuzzleRules.Phase.LOST,"radio full natural timeout");
    }
    static AuctionRules.Observation o(UUID id){return new AuctionRules.Observation(id,M,true,true);}
    static void auction(){
        var g=new AuctionRules(M,List.of(R,B),new Random(3));var all=List.of(o(R),o(B));
        ok(g.balance(R).orElseThrow()==100&&g.balance(B).orElseThrow()==100&&g.balance(O).isEmpty(),"auction private match balances");
        ok(g.bid(R,M,1,60)&&g.bid(B,M,1,40)&&!g.bid(O,M,1,30)&&!g.bid(R,M,1,101),"auction valid bid budget");
        g.tick(all);ok(g.offer(R).orElseThrow()==60&&g.offer(B).orElseThrow()==40&&g.balance(R).orElseThrow()==100,"auction no precharge");
        g.bid(B,M,1,-1);g.tick(all);ok(g.offer(B).orElseThrow()==0,"auction withdrawal");
        g.bid(R,M,1,99);g.discardPending();g.tick(all);
        ok(g.offer(R).orElseThrow()==60,"auction rapid reconnect pending bid invalidated");
        g.bid(R,M,1,99);int left=g.state().ticksLeft();g.tick(List.of(o(R)));
        ok(g.state().ticksLeft()==left&&g.offer(R).orElseThrow()==60,"auction pending dropped when offline");
        for(int i=0;i<left;i++)g.tick(all);
        ok(g.state().round()==2&&g.state().lastAward().orElseThrow().winner().equals(R)&&g.balance(R).orElseThrow()==40&&g.balance(B).orElseThrow()==100,"auction winner only pays");
        g.bid(R,M,2,30);g.bid(B,M,2,30);for(int i=0;i<400;i++)g.tick(all);
        ok(g.balance(R).orElseThrow()+g.balance(B).orElseThrow()==110&&g.state().lastAward().orElseThrow().winner()!=null,"auction tie single award");
        for(int i=0;i<400;i++)g.tick(all);
        ok(g.state().phase()==AuctionRules.Phase.COMBAT&&g.state().lastAward().orElseThrow().winner()==null,"auction no-bid no charge");
        left=g.state().ticksLeft();g.tick(List.of(o(R)));ok(g.state().ticksLeft()==left,"auction combat pause");
        ok(g.death(B,M)&&!g.death(B,M),"auction death idempotent");
        g.tick(all);ok(g.state().phase()==AuctionRules.Phase.ENDED&&g.state().winner().equals(R),"auction last survivor");
        var tie=new AuctionRules(M,List.of(R,B),new Random(4));
        for(int i=0;i<1200;i++)tie.tick(all);
        tie.death(R,M);tie.death(B,M);tie.tick(all);
        ok(tie.state().phase()==AuctionRules.Phase.ENDED&&tie.state().winner()==null,"auction simultaneous elimination draw");
    }
}