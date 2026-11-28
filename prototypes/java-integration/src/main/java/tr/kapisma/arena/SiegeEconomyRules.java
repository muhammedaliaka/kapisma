package tr.kapisma.arena;

import java.util.*;
import java.util.function.BooleanSupplier;
import tr.kapisma.arena.MatchRoster.*;

/** New simultaneous siege rules, separate from legacy two-leg adapter until integration.
 * All observations, catalog IDs, world mutations and damage amounts are server-authoritative.
 * Credits are conserved; each purchased unit can be placed OR sold, never both.
 */
public final class SiegeEconomyRules {
    public static final int START_CREDITS=1000, START_POINTS=500;
    public enum Phase { BUILD, BATTLE, ENDED }
    public enum Kit { SIEGE, FIGHTER, ARCHER, INFILTRATOR, SUPPORT }
    public record Cell(int x,int y,int z) {}
    public record Zone(int minX,int maxX,int minY,int maxY,int minZ,int maxZ) {
        public Zone { if(minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("zone"); }
        boolean contains(Cell c){return c.x>=minX&&c.x<=maxX&&c.y>=minY&&c.y<=maxY&&c.z>=minZ&&c.z<=maxZ;}
        boolean overlaps(Zone z){return minX<=z.maxX&&maxX>=z.minX&&minY<=z.maxY&&maxY>=z.minY&&minZ<=z.maxZ&&maxZ>=z.minZ;}
    }
    public record Item(String id,int price,boolean placeable) {
        public Item { Objects.requireNonNull(id);if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||price<1||price>START_CREDITS)throw new IllegalArgumentException("catalog"); }
    }
    public record State(Phase phase,int buildTicks,int redPoints,int bluePoints,Side winner,boolean ready) {}
    private record Placed(Side side,String id) {}
    private final Thread owner=Thread.currentThread();
    private final MatchRoster roster;
    private final Map<String,Item> catalog;
    private final EnumMap<Side,Zone> zones=new EnumMap<>(Side.class);
    private final EnumMap<Side,Integer> credits=new EnumMap<>(Side.class),points=new EnumMap<>(Side.class),damage=new EnumMap<>(Side.class);
    private final Map<UUID,Map<String,Integer>> stock=new HashMap<>();
    private final Map<UUID,Kit> kits=new HashMap<>();
    private final Map<Cell,Placed> placed=new HashMap<>();
    private Set<UUID> active=Set.of();
    private Phase phase=Phase.BUILD;
    private int buildTicks;
    private Side winner;
    private boolean ready,applying;
    public SiegeEconomyRules(MatchRoster roster,Collection<Item> items,Zone red,Zone blue,int buildTicks){
        this.roster=Objects.requireNonNull(roster);
        Objects.requireNonNull(red);Objects.requireNonNull(blue);
        if(red.overlaps(blue)||buildTicks<1||buildTicks>72000)throw new IllegalArgumentException("configuration");
        var entries=new LinkedHashMap<String,Item>();
        for(Item item:items)if(entries.putIfAbsent(item.id,item)!=null)throw new IllegalArgumentException("duplicate item");
        if(entries.isEmpty()||entries.size()>4096)throw new IllegalArgumentException("catalog size");
        this.catalog=Map.copyOf(entries);this.buildTicks=buildTicks;
        zones.put(Side.RED,red);zones.put(Side.BLUE,blue);
        for(Side side:Side.values()){credits.put(side,START_CREDITS);points.put(side,START_POINTS);damage.put(side,0);}
        for(UUID id:roster.members())stock.put(id,new HashMap<>());
    }
    private void thread(){if(Thread.currentThread()!=owner||applying)throw new IllegalStateException("wrong thread or reentrant mutation");}
    private boolean permitted(Presence p){return ready&&phase!=Phase.ENDED&&roster.accepts(p)&&active.contains(p.player());}
    public State state(){thread();return new State(phase,buildTicks,points.get(Side.RED),points.get(Side.BLUE),winner,ready);}
    public OptionalInt credits(UUID id){thread();Side side=roster.side(id);return side==null?OptionalInt.empty():OptionalInt.of(credits.get(side));}
    public int stock(UUID id,String item){thread();return stock.getOrDefault(id,Map.of()).getOrDefault(item,0);}
    public Optional<Kit> kit(UUID id){thread();return Optional.ofNullable(kits.get(id));}
    private boolean apply(BooleanSupplier action){Objects.requireNonNull(action);applying=true;try{return action.getAsBoolean();}finally{applying=false;}}
    private void change(UUID id,String item,int delta){
        var map=stock.get(id);int amount=map.getOrDefault(item,0)+delta;
        if(amount==0)map.remove(item);else map.put(item,amount);
    }
    public boolean buy(Presence p,String id,int count,BooleanSupplier grant){
        thread();Item item=catalog.get(id);
        if(!permitted(p)||item==null||count<1||count>START_CREDITS)return false;
        Side side=roster.side(p.player());long cost=(long)item.price*count;
        if(cost>credits.get(side)||!apply(grant))return false;
        credits.put(side,credits.get(side)-(int)cost);change(p.player(),id,count);return true;
    }
    public boolean sell(Presence p,String id,int count,BooleanSupplier remove){
        thread();Item item=catalog.get(id);
        if(!permitted(p)||item==null||count<1||count>stock(p.player(),id))return false;
        Side side=roster.side(p.player());long refund=(long)item.price*count;
        if(refund+credits.get(side)>START_CREDITS||!apply(remove))return false;
        change(p.player(),id,-count);credits.put(side,credits.get(side)+(int)refund);return true;
    }
    /** Only own zone, both build and battle. Adapter verifies real inventory and cell. */
    public boolean place(Presence p,String id,Cell cell,BooleanSupplier mutate){
        thread();Item item=catalog.get(id);
        if(!permitted(p)||item==null||!item.placeable||cell==null||stock(p.player(),id)<1)return false;
        Side side=roster.side(p.player());
        if(!zones.get(side).contains(cell)||placed.containsKey(cell)||!apply(mutate))return false;
        change(p.player(),id,-1);placed.put(cell,new Placed(side,id));return true;
    }
    /** Destruction never refunds credits or creates sellable items. */
    public boolean destroy(Presence p,Cell cell,BooleanSupplier mutate){
        thread();Placed block=placed.get(cell);
        if(!permitted(p)||phase!=Phase.BATTLE||block==null||!apply(mutate))return false;
        placed.remove(cell);return true;
    }
    /** Five kit choices, no post-start switching until respawn policy is defined. */
    public boolean chooseKit(Presence p,Kit kit){
        thread();if(!permitted(p)||phase!=Phase.BUILD||kit==null)return false;
        kits.put(p.player(),kit);return true;
    }
    /** Damage cause/amount must be validated by adapter; batch to permit simultaneous draw. */
    public boolean queueDamage(Presence attacker,Side victim,int amount){
        thread();if(!permitted(attacker)||phase!=Phase.BATTLE||victim==null||victim==roster.side(attacker.player())||amount<1||amount>START_POINTS)return false;
        damage.put(victim,Math.min(START_POINTS,damage.get(victim)+amount));return true;
    }
    public void invalidateReadiness(){
        thread();ready=false;active=Set.of();for(Side side:Side.values())damage.put(side,0);
    }
    public State tick(Collection<Presence> observations){
        thread();if(phase==Phase.ENDED)return state();
        var present=roster.active(observations);
        ready=roster.bothSides(present);
        active=ready?Set.copyOf(present.stream().map(Presence::player).toList()):Set.of();
        if(!ready){for(Side side:Side.values())damage.put(side,0);return state();}
        if(phase==Phase.BUILD){if(--buildTicks==0){phase=Phase.BATTLE;for(UUID id:roster.members())kits.putIfAbsent(id,Kit.FIGHTER);}return state();}
        for(Side side:Side.values()){points.put(side,Math.max(0,points.get(side)-damage.get(side)));damage.put(side,0);}
        int red=points.get(Side.RED),blue=points.get(Side.BLUE);
        if(red==0||blue==0){phase=Phase.ENDED;winner=red==blue?null:red==0?Side.BLUE:Side.RED;ready=false;active=Set.of();}
        return state();
    }
}
