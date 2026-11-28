package tr.kapisma.game;
import eu.pb4.sgui.api.GuiHelpers;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import tr.kapisma.Kapisma;
public final class SiegeRuntimeProof implements ModInitializer {
    public void onInitialize(){
        if(!Boolean.getBoolean("kapisma.arenaProof"))throw new IllegalStateException("QA flag required");
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("arena-siege-proof")
            .requires(s->s.getEntity()==null&&s.hasPermission(4))
            .then(Commands.argument("actor",EntityArgument.player())
                .then(Commands.literal("click").then(Commands.argument("slot",IntegerArgumentType.integer(-999,100)).then(Commands.argument("button",IntegerArgumentType.integer(0,1))
                    .executes(c->click(EntityArgument.getPlayer(c,"actor"),IntegerArgumentType.getInteger(c,"slot"),IntegerArgumentType.getInteger(c,"button"),false)))))
                .then(Commands.literal("stale").executes(c->click(EntityArgument.getPlayer(c,"actor"),0,0,true)))
                .then(Commands.literal("fragment").executes(c->fragment(EntityArgument.getPlayer(c,"actor"))))
                .then(Commands.literal("snapshot").executes(c->snapshot(EntityArgument.getPlayer(c,"actor")))))));
    }
    private static NativeArenaGame game(ServerPlayer p){
        if(!(Kapisma.GM.current instanceof NativeArenaGame g)||!g.siegeShopReady(p))throw new IllegalStateException("wrong siege");
        return g;
    }
    private static int click(ServerPlayer p,int slot,int button,boolean stale){
        game(p);
        if(!(GuiHelpers.getCurrentGui(p) instanceof SiegeShopGui))throw new IllegalStateException("not shop");
        var menu=p.containerMenu;
        p.connection.handleContainerClick(new ServerboundContainerClickPacket(stale?menu.containerId+100:menu.containerId,menu.getStateId(),slot,button,ClickType.PICKUP,ItemStack.EMPTY,new Int2ObjectOpenHashMap<>()));
        return snapshot(p);
    }
    private static int snapshot(ServerPlayer p){
        var g=game(p);int oak=0,slots=0;
        for(int i=0;i<36;i++){var s=p.getInventory().getItem(i);if(!s.isEmpty())slots++;if(SiegeShop.fromStack(s)==SiegeShop.product(1))oak+=s.getCount();}
        var carried=p.inventoryMenu.getCarried();
        if(SiegeShop.fromStack(carried)==SiegeShop.product(1))oak+=carried.getCount();
        System.out.printf("SIEGEQA %s gui=%s credits=%d oak=%d ledger=%d slots=%d cursor=%d kit=%d%n",p.getScoreboardName(),GuiHelpers.getCurrentGui(p) instanceof SiegeShopGui,g.siegeBalance(p),oak,g.siegeStock(p,1),slots,carried.getCount(),g.siegeSelectedKit(p));return 1;
    }
    /** Deliberately fragment already-paid stock; no new items and no ledger mutation. */
    private static int fragment(ServerPlayer p){
        var g=game(p);if(p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty())throw new IllegalStateException("close shop");
        int total=0;var product=SiegeShop.product(1);
        for(int i=0;i<36;i++){var s=p.getInventory().getItem(i);if(s.isEmpty())continue;if(SiegeShop.fromStack(s)!=product)throw new IllegalStateException("other stock");total+=s.getCount();}
        if(total!=g.siegeStock(p,1)||total!=37)throw new IllegalStateException("need exactly 37 paid blocks");
        for(int i=0;i<36;i++)p.getInventory().setItem(i,product.stack(1));
        p.inventoryMenu.setCarried(product.stack(1));p.getInventory().setChanged();p.inventoryMenu.broadcastFullState();return snapshot(p);
    }
}
