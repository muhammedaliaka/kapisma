package tr.kapisma.game;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import tr.kapisma.arena.MatchRoster.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import tr.kapisma.arena.CaravanMapRaster;
import tr.kapisma.arena.CaravanMatchRules;

/** One private locked vanilla map per player and match. Adapter owner must prevent map transfer
 * and remove issued copies on exit. Never share a map ID between players. */
public final class CaravanMapAdapter {
    private final ServerLevel level;
    private final CaravanMapRaster raster;
    private final UUID owner;
    private final ItemStack prototype;
    private final MapItemSavedData data;
    private final net.minecraft.world.level.saveddata.maps.MapId id;
    private long lastTick=-1;
    private CaravanMapRaster.Prepared previousBase;
    private Object previousRoute;
    private CaravanMapRaster.Pixel previousWagon,previousSelf;
    private long rendered,skipped,pixelWrites;
    private static final byte[] PALETTE=new byte[15];
    static {for(byte i=0;i<PALETTE.length;i++)PALETTE[i]=color(i);}
    public String qaCounters(){
        thread();int escort=0,attack=0;
        for(byte pixel:data.colors){if(pixel==PALETTE[CaravanMapRaster.ESCORT_BASE])escort++;else if(pixel==PALETTE[CaravanMapRaster.ATTACK_BASE])attack++;}
        return " rendered="+rendered+" skipped="+skipped+" pixelWrites="+pixelWrites+" escortBasePixels="+escort+" attackBasePixels="+attack;
    }
    private boolean closed;
    public CaravanMapAdapter(ServerLevel level,CaravanMapRaster raster,UUID owner){
        this.level=level;this.raster=raster;this.owner=java.util.Objects.requireNonNull(owner);thread();
        id=level.getFreeMapId();
        data=MapItemSavedData.createFresh(0,0,(byte)0,false,false,level.dimension()).locked();
        prototype=new ItemStack(Items.FILLED_MAP);
        prototype.set(DataComponents.MAP_ID,id);
        prototype.set(DataComponents.CUSTOM_NAME,Component.literal("Kervan · Taktik Harita"));
        prototype.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(java.util.List.of(
            Component.literal("Beyaz: Konumun · Altın: Kervan"),
            Component.literal("Açık mavi: Seçili güzergâh"),
            Component.literal("Yeşil: Güvenli · Kırmızı: Riskli"),
            Component.literal("Mor kare: Eskort üssü"),
            Component.literal("Turuncu elmas: Saldırgan teslim alanı"),
            Component.literal("Üst taraf kuzey · Kişisel harita")
        )));
        CaravanTransientMaps.register(level,id,data);
    }
    private void thread(){if(!level.getServer().isSameThread())throw new IllegalStateException("wrong thread");}
    public ItemStack issue(ServerPlayer player){thread();if(closed||!owner.equals(player.getUUID())||player.level()!=level)throw new IllegalStateException("wrong map recipient");return prototype.copy();}
    public boolean owns(ItemStack s){thread();return s.is(Items.FILLED_MAP)&&java.util.Objects.equals(prototype.get(DataComponents.MAP_ID),s.get(DataComponents.MAP_ID));}
    private static byte color(byte i){
        MapColor c=switch(i){
            case CaravanMapRaster.SAFE -> MapColor.COLOR_GREEN;
            case CaravanMapRaster.RISKY -> MapColor.COLOR_RED;
            case CaravanMapRaster.SELECTED -> MapColor.COLOR_LIGHT_BLUE;
            case CaravanMapRaster.START -> MapColor.SNOW;
            case CaravanMapRaster.FINISH -> MapColor.EMERALD;
            case CaravanMapRaster.WAGON -> MapColor.GOLD;
            case CaravanMapRaster.SELF -> MapColor.QUARTZ;
            case CaravanMapRaster.GRID -> MapColor.COLOR_GRAY;
            case CaravanMapRaster.TERRAIN_LOW -> MapColor.COLOR_GREEN;
            case CaravanMapRaster.TERRAIN_MID -> MapColor.TERRACOTTA_GREEN;
            case CaravanMapRaster.TERRAIN_HIGH -> MapColor.TERRACOTTA_BROWN;
            case CaravanMapRaster.CONTOUR -> MapColor.TERRACOTTA_YELLOW;
            case CaravanMapRaster.ESCORT_BASE -> MapColor.COLOR_PURPLE;
            case CaravanMapRaster.ATTACK_BASE -> MapColor.COLOR_ORANGE;
            default -> MapColor.COLOR_BLACK;
        };
        var brightness=switch(i){
            case CaravanMapRaster.GRID,CaravanMapRaster.TERRAIN_LOW -> MapColor.Brightness.LOW;
            case CaravanMapRaster.TERRAIN_MID,CaravanMapRaster.TERRAIN_HIGH -> MapColor.Brightness.NORMAL;
            case CaravanMapRaster.CONTOUR -> MapColor.Brightness.LOW;
            case CaravanMapRaster.SELF,CaravanMapRaster.WAGON,CaravanMapRaster.SELECTED,CaravanMapRaster.ESCORT_BASE,CaravanMapRaster.ATTACK_BASE -> MapColor.Brightness.HIGH;
            default -> MapColor.Brightness.NORMAL;
        };
        return c.getPackedId(brightness);
    }
    public int refresh(long tick,CaravanMatchRules.State state,ServerPlayer player,CaravanMapRaster.Prepared terrain){
        thread();if(closed||tick<0||tick<=lastTick||lastTick>=0&&tick-lastTick<5)return 0;
        if(!owner.equals(player.getUUID())||player.level()!=level)throw new IllegalArgumentException("wrong map recipient");
        Position self=new Position(player.getX(),player.getY(),player.getZ());
        var route=state.route().route();var wagon=state.route().position();
        var wp=raster.project(wagon).orElse(null);var sp=raster.project(self).orElse(null);
        lastTick=tick;
        if(terrain==previousBase&&route==previousRoute&&java.util.Objects.equals(wp,previousWagon)&&java.util.Objects.equals(sp,previousSelf)){skipped++;return 0;}
        byte[] pixels=raster.cachedTactical(terrain,route,wagon,self);
        int changed=0;
        for(int i=0;i<pixels.length;i++){
            byte color=PALETTE[pixels[i]];
            if(data.colors[i]!=color&&data.updateColor(i%128,i/128,color))changed++;
        }
        previousBase=terrain;previousRoute=route;previousWagon=wp;previousSelf=sp;
        rendered++;pixelWrites+=changed;return changed;
    }
    /** Blank and release the active-only buffer; never recycle vanilla IDs or touch saved files. */
    public void close(){
        thread();if(closed)return;closed=true;
        try{for(int i=0;i<128*128;i++)data.updateColor(i%128,i/128,color(CaravanMapRaster.BACKGROUND));}
        finally{CaravanTransientMaps.release(level,id,data);}
    }
}
