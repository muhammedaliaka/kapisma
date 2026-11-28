package tr.kapisma.game;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Only currently active match maps. Vanilla ID allocation remains authoritative. */
public final class CaravanTransientMaps {
    private static final Map<ServerLevel,Map<MapId,MapItemSavedData>> ACTIVE=new WeakHashMap<>();
    private CaravanTransientMaps(){}
    private static void thread(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    public static synchronized void register(ServerLevel level,MapId id,MapItemSavedData data){
        thread(level);Objects.requireNonNull(id);Objects.requireNonNull(data);
        if(ACTIVE.computeIfAbsent(level,k->new HashMap<>()).putIfAbsent(id,data)!=null)throw new IllegalStateException("duplicate active map id");
    }
    public static synchronized MapItemSavedData lookup(ServerLevel level,MapId id){
        // Read only, no allocation on unrelated vanilla lookups.
        var maps=ACTIVE.get(level);return maps==null?null:maps.get(id);
    }
    public static synchronized void release(ServerLevel level,MapId id,MapItemSavedData expected){
        thread(level);var maps=ACTIVE.get(level);
        if(maps==null||!maps.remove(id,expected))throw new IllegalStateException("map registry ownership mismatch");
        if(maps.isEmpty())ACTIVE.remove(level);
    }
    public static synchronized int count(ServerLevel level){thread(level);var maps=ACTIVE.get(level);return maps==null?0:maps.size();}
}
