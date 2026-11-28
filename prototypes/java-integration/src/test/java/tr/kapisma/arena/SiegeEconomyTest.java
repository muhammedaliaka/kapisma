package tr.kapisma.arena;
import java.util.*;
import tr.kapisma.arena.MatchRoster.*;
import tr.kapisma.arena.SiegeEconomyRules.*;
public final class SiegeEconomyTest {
    static int passed;
    static final UUID M=UUID.randomUUID(),R=UUID.randomUUID(),R2=UUID.randomUUID(),B=UUID.randomUUID(),O=UUID.randomUUID();
    static final MatchRoster roster=new MatchRoster(M,List.of(R,R2),List.of(B));
    static Presence p(UUID id){return new Presence(id,M,true,true,new Position(0,0,0));}
    static final List<Presence> all=List.of(p(R),p(R2),p(B));
    static final String STONE="minecraft:stone",TNT="minecraft:tnt",POTION="minecraft:potion";
    static final Zone RED=new Zone(-10,-1,0,10,-5,5),BLUE=new Zone(1,10,0,10,-5,5);
    static final Cell RC=new Cell(-2,1,0),BC=new Cell(2,1,0);
    static final List<Item> ITEMS=List.of(new Item(STONE,3,true),new Item(TNT,100,true),new Item(POTION,20,false));
    static SiegeEconomyRules game(){return new SiegeEconomyRules(roster,ITEMS,RED,BLUE,2);}
    static void ok(boolean cond,String name){if(!cond)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    static void reject(Runnable action,String name){try{action.run();throw new AssertionError(name);}catch(IllegalArgumentException|IllegalStateException expected){ok(true,name);}}
    static int balance(SiegeEconomyRules g,UUID id){return g.credits(id).orElseThrow();}
    public static void main(String[] args){
        reject(()->new SiegeEconomyRules(roster,ITEMS,RED,RED,1),"overlapping zones rejected");
        reject(()->new Item("minecraft:stone",0,true),"zero price rejected");
        reject(()->new SiegeEconomyRules(roster,List.of(ITEMS.getFirst(),ITEMS.getFirst()),RED,BLUE,1),"duplicate catalog rejected");
        var g=game();
        ok(balance(g,R)==1000&&balance(g,R2)==1000&&balance(g,B)==1000&&g.state().redPoints()==500&&g.state().bluePoints()==500,"both teams equal even with uneven roster");
        ok(g.credits(O).isEmpty()&&!g.buy(p(R),STONE,1,()->true),"outsider and not-ready denied");
        g.tick(all);
        ok(g.buy(p(R),TNT,9,()->true)&&balance(g,R2)==100&&balance(g,B)==1000,"shared funds debit separate enemy funds");
        ok(!g.buy(p(R2),TNT,2,()->{throw new AssertionError("must not grant");}),"overspend denied before grant");
        ok(!g.buy(p(R2),STONE,0,()->true)&&!g.buy(p(R2),STONE,Integer.MAX_VALUE,()->true)&&!g.buy(p(R2),"bad",1,()->true),"invalid catalog and counts");
        ok(!g.buy(p(R2),TNT,1,()->false)&&balance(g,R)==100,"failed grant no debit");
        ok(g.buy(p(R2),TNT,1,()->true)&&balance(g,R)==0,"shared balance exact exhaustion");
        ok(!g.sell(p(R2),TNT,2,()->true)&&!g.sell(p(R),STONE,1,()->true),"cannot sell teammate or unpaid stock");
        ok(!g.sell(p(R),TNT,1,()->false)&&balance(g,R)==0,"failed removal no refund");
        ok(g.sell(p(R),TNT,2,()->true)&&balance(g,R2)==200&&g.stock(R,TNT)==7,"full resale and shared refund");
        ok(!g.place(p(R),TNT,BC,()->true)&&!g.place(p(R),TNT,RC,()->false)&&g.stock(R,TNT)==7,"enemy zone and failed world mutation denied");
        ok(g.place(p(R),TNT,RC,()->true)&&!g.place(p(R),TNT,RC,()->true)&&g.stock(R,TNT)==6,"one placement one debit");
        ok(!g.sell(p(R),TNT,7,()->true),"placed item not resellable");
        ok(g.buy(p(B),POTION,1,()->true)&&!g.place(p(B),POTION,BC,()->true),"nonplaceable item");
        ok(g.chooseKit(p(R),Kit.SIEGE)&&g.chooseKit(p(R2),Kit.SUPPORT)&&Kit.values().length==5,"five kit selections");
        ok(!g.queueDamage(p(R),Side.BLUE,500)&&!g.destroy(p(B),RC,()->true),"build phase damage denied");
        g.tick(all);
        ok(g.state().phase()==Phase.BATTLE&&g.kit(B).orElseThrow()==Kit.FIGHTER&&!g.chooseKit(p(R),Kit.ARCHER),"battle starts and kits locked");
        ok(g.destroy(p(B),RC,()->true)&&!g.destroy(p(B),RC,()->true)&&balance(g,R)==200,"destruction does not refund and cannot repeat");
        ok(g.place(p(R),TNT,RC,()->true)&&g.stock(R,TNT)==5,"battle repair from paid stock");
        ok(g.buy(p(B),STONE,10,()->true)&&g.place(p(B),STONE,BC,()->true),"both teams build and repair during battle");
        ok(!g.queueDamage(p(R),Side.RED,1)&&!g.queueDamage(p(O),Side.BLUE,1)&&!g.queueDamage(p(R),Side.BLUE,0),"invalid damage denied");
        ok(g.queueDamage(p(R),Side.BLUE,200)&&g.queueDamage(p(B),Side.RED,100),"simultaneous queued damage");
        g.tick(all);
        ok(g.state().redPoints()==400&&g.state().bluePoints()==300,"both point pools reduced");
        g.queueDamage(p(R),Side.BLUE,300);g.invalidateReadiness();g.tick(all);
        ok(g.state().bluePoints()==300,"disconnect clears pending damage");
        g.tick(List.of(p(R),p(B),p(B)));
        ok(!g.state().ready()&&!g.buy(p(R),STONE,1,()->true),"duplicate opposing observations pause");
        g.tick(all);
        var foreign=new Presence(R,UUID.randomUUID(),true,true,new Position(0,0,0));
        ok(!g.buy(foreign,STONE,1,()->true),"stale match rejected");
        reject(()->g.buy(p(R),STONE,1,()->{g.buy(p(R),STONE,1,()->true);return true;}),"reentrant grant rejected");
        ok(balance(g,R)==200,"reentrant failure no debit");
        g.queueDamage(p(R),Side.BLUE,500);g.tick(all);
        ok(g.state().phase()==Phase.ENDED&&g.state().winner()==Side.RED&&g.state().bluePoints()==0&&!g.buy(p(R),STONE,1,()->true),"zero points natural end");
        var tie=game();tie.tick(all);tie.tick(all);tie.queueDamage(p(R),Side.BLUE,500);tie.queueDamage(p(B),Side.RED,500);tie.tick(all);
        ok(tie.state().phase()==Phase.ENDED&&tie.state().winner()==null,"same-tick mutual destruction draw");
        var random=new Random(19);var e=game();e.tick(all);
        int buys=0;
        for(int i=0;i<2000;i++){
            UUID actor=random.nextBoolean()?R:R2;
            if(random.nextBoolean()){if(e.buy(p(actor),STONE,1,()->true))buys++;}
            else if(e.sell(p(actor),STONE,1,()->true))buys--;
            if(balance(e,R)<0||balance(e,R)>1000||balance(e,R)+3*buys!=1000||e.stock(R,STONE)+e.stock(R2,STONE)!=buys)throw new AssertionError("credit invariant "+i);
        }
        ok(true,"2000 randomized shared-ledger transactions preserve funds and stock");
        System.out.println("PASS total "+passed);
    }
}
