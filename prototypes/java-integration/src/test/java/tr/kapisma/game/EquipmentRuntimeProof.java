package tr.kapisma.game;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import tr.kapisma.Kapisma;
public final class EquipmentRuntimeProof implements ModInitializer {
    public void onInitialize(){
        if(!Boolean.getBoolean("kapisma.arenaProof"))throw new IllegalStateException("QA flag required");
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("arena-equipment-proof")
            .requires(s->s.getEntity()==null&&s.hasPermission(4))
            .then(Commands.argument("actor",EntityArgument.player())
              .then(Commands.literal("snapshot").executes(c->snapshot(EntityArgument.getPlayer(c,"actor"))))
              .then(Commands.literal("locked").executes(c->run(EntityArgument.getPlayer(c,"actor"),true)))
              .then(Commands.literal("control").executes(c->run(EntityArgument.getPlayer(c,"actor"),false))))));
    }
    private static int snapshot(ServerPlayer p){
        System.out.println("PACKETQA_SERVER "+p.getScoreboardName()+" slot="+p.getInventory().getItem(0).getCount()+" cursor="+p.containerMenu.getCarried().getCount());
        return 1;
    }
    private static void ok(boolean condition,String label){
        if(!condition)throw new IllegalStateException("EQUIP_FAIL "+label);
        System.out.println("EQUIP_PASS "+label);
    }
    private static final class ProbeArrow extends Arrow {
        ProbeArrow(Level l,ServerPlayer p){super(l,p.getX(),p.getY(),p.getZ(),new ItemStack(Items.ARROW),null);inGround=true;pickup=AbstractArrow.Pickup.ALLOWED;}
    }
    private static int run(ServerPlayer p,boolean locked){
        if(!(Kapisma.GM.current instanceof NativeArenaGame game)||game.locksEquipment(p)!=locked)throw new IllegalStateException("wrong test arena");
        var inv=p.getInventory();var menu=p.inventoryMenu;
        if(p.containerMenu!=menu||!menu.getCarried().isEmpty())throw new IllegalStateException("dirty fixture");
        int slot=menu.findSlot(inv,0).orElseThrow();
        ItemStack original=inv.getItem(0).copy();
        if(original.isEmpty())throw new IllegalStateException("empty test kit");
        // Direct actual server menu methods; no fabricated outcomes, not client packets.
        menu.clicked(slot,0,ClickType.PICKUP,p);
        ok(inv.getItem(0).isEmpty()&&ItemStack.matches(original,menu.getCarried()),"normal_cursor_"+locked);
        if(locked){
            menu.clicked(-999,0,ClickType.PICKUP,p);
            ok(ItemStack.matches(original,menu.getCarried()),"outside_cursor_preserved");
            menu.clicked(1,0,ClickType.PICKUP,p);
            ok(menu.getSlot(1).getItem().isEmpty()&&ItemStack.matches(original,menu.getCarried()),"craft_slot_denied");
            menu.clicked(-999,0,ClickType.QUICK_CRAFT,p);
            ok(ItemStack.matches(original,menu.getCarried()),"quick_craft_denied");
        }
        menu.clicked(slot,0,ClickType.PICKUP,p);
        ok(ItemStack.matches(original,inv.getItem(0))&&menu.getCarried().isEmpty(),"normal_replace_"+locked);
        if(locked){
            menu.clicked(slot,1,ClickType.THROW,p);
            ok(ItemStack.matches(original,inv.getItem(0)),"inventory_throw_preserved");
        }
        var container=new SimpleContainer(27);container.setItem(0,new ItemStack(Items.DIAMOND));
        var chest=ChestMenu.threeRows(99,inv,container);
        chest.clicked(0,0,ClickType.QUICK_MOVE,p);
        ok(container.getItem(0).is(Items.DIAMOND)==locked,"external_shift_"+locked);
        // Synthetic unspawned entity: exercises vanilla pickup method without leaving world debris.
        var entity=new ItemEntity(p.level(),p.getX(),p.getY(),p.getZ(),new ItemStack(Items.EMERALD));
        entity.setNoPickUpDelay();entity.playerTouch(p);
        ok(inv.contains(new ItemStack(Items.EMERALD))!=locked,"item_pickup_"+locked);
        var arrow=new ProbeArrow(p.level(),p);
        arrow.playerTouch(p);
        ok(inv.contains(new ItemStack(Items.ARROW))!=locked,"arrow_pickup_"+locked);
        // Test-only known materials; no player-supplied success result.
        inv.setItem(8,new ItemStack(Items.OAK_PLANKS,2));
        var recipe=p.serverLevel().getRecipeManager().byKey(ResourceLocation.parse("minecraft:stick")).orElseThrow();
        p.awardRecipes(java.util.List.of(recipe));
        menu.handlePlacement(false,recipe,p);
        boolean placed=false;
        for(int i=1;i<=4;i++)placed|=menu.getSlot(i).hasItem();
        ok(placed!=locked,"recipe_placement_"+locked);
        if(locked)ok(inv.getItem(8).getCount()==2,"recipe_material_preserved");
        if(!locked){
            p.getInventory().selected=0;
            ok(p.drop(true)&&inv.getItem(0).isEmpty(),"ordinary_drop_allowed");
        }
        System.out.println("EQUIP_DONE "+locked);return 1;
    }
}