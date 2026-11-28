package tr.kapisma.arena;

import java.util.*;

/** Immutable identity and membership for a single match. Observations must be server-derived. */
public final class MatchRoster {
    public enum Side { RED, BLUE }
    public record Position(double x, double y, double z) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("non-finite position");
        }
        public double distanceSquared(Position p) {
            double dx=x-p.x,dy=y-p.y,dz=z-p.z;
            return dx*dx+dy*dy+dz*dz;
        }
    }
    public record Presence(UUID player, UUID match, boolean inArena, boolean active, Position position) {
        public Presence { Objects.requireNonNull(player);Objects.requireNonNull(match);Objects.requireNonNull(position); }
    }
    private final UUID match;
    private final Map<UUID,Side> sides;
    public MatchRoster(UUID match, List<UUID> red, List<UUID> blue) {
        this.match=Objects.requireNonNull(match);
        Objects.requireNonNull(red);Objects.requireNonNull(blue);
        if(red.isEmpty()||blue.isEmpty()||red.size()+blue.size()>8||Math.abs(red.size()-blue.size())>1)
            throw new IllegalArgumentException("2–8 balanced players");
        var map=new LinkedHashMap<UUID,Side>();
        for(var id:red) put(map,id,Side.RED);
        for(var id:blue) put(map,id,Side.BLUE);
        sides=Collections.unmodifiableMap(map);
    }
    private static void put(Map<UUID,Side> m,UUID id,Side side) {
        if(m.putIfAbsent(Objects.requireNonNull(id),side)!=null)throw new IllegalArgumentException("duplicate member");
    }
    public UUID match(){return match;}
    public Set<UUID> members(){return sides.keySet();}
    public Side side(UUID id){return sides.get(id);}
    public boolean accepts(Presence p) {
        return match.equals(p.match())&&p.inArena()&&p.active()&&sides.containsKey(p.player());
    }
    /** Duplicate observations are rejected entirely, not counted or selected arbitrarily. */
    public List<Presence> active(Collection<Presence> input) {
        var counts=new HashMap<UUID,Integer>();
        var valid=new ArrayList<Presence>();
        for(var p:input)if(accepts(p)){counts.merge(p.player(),1,Integer::sum);valid.add(p);}
        valid.removeIf(p->counts.get(p.player())!=1);
        return List.copyOf(valid);
    }
    public boolean bothSides(List<Presence> active) {
        boolean red=false,blue=false;
        for(var p:active){red|=side(p.player())==Side.RED;blue|=side(p.player())==Side.BLUE;}
        return red&&blue;
    }
    public boolean canDamage(UUID source,UUID target,boolean inSameArena,boolean running) {
        if(!running||!inSameArena||source.equals(target))return false;
        Side a=side(source),b=side(target);
        return a!=null&&b!=null&&a!=b;
    }
}
