package tr.kapisma.game;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import tr.kapisma.arena.SiegeEconomyRules;

/** Closed catalog, actual inventory transaction. Phase-one catalog is intentionally inert full blocks. */
final class SiegeShop {
    record Product(String id,Item item,int price) {
        SiegeEconomyRules.Item rule(){return new SiegeEconomyRules.Item(id,price,true);}
        ItemStack stack(int n){var s=new ItemStack(item,n);s.set(DataComponents.CUSTOM_NAME,Component.literal("Kuşatma malzemesi · "+price+" kredi"));
            s.set(DataComponents.CAN_PLACE_ON,new AdventureModePredicate(List.of(net.minecraft.advancements.critereon.BlockPredicate.Builder.block().of(
                net.minecraft.world.level.block.Blocks.SMOOTH_STONE,net.minecraft.world.level.block.Blocks.OAK_PLANKS,net.minecraft.world.level.block.Blocks.COBBLESTONE,
                net.minecraft.world.level.block.Blocks.STONE_BRICKS,net.minecraft.world.level.block.Blocks.IRON_BLOCK,net.minecraft.world.level.block.Blocks.OBSIDIAN,
                net.minecraft.world.level.block.Blocks.GLASS,net.minecraft.world.level.block.Blocks.BRICKS).build()),true));return s;}
        Block block(){return ((BlockItem)item).getBlock();}
    }
    static final List<Product> PRODUCTS=List.of(
        new Product("minecraft:oak_planks",Items.OAK_PLANKS,2),
        new Product("minecraft:cobblestone",Items.COBBLESTONE,3),
        new Product("minecraft:stone_bricks",Items.STONE_BRICKS,5),
        new Product("minecraft:iron_block",Items.IRON_BLOCK,18),
        new Product("minecraft:obsidian",Items.OBSIDIAN,50),
        new Product("minecraft:glass",Items.GLASS,4),
        new Product("minecraft:bricks",Items.BRICKS,6),
        new Product("minecraft:smooth_stone",Items.SMOOTH_STONE,4));
    static Product product(int i){return i>=1&&i<=PRODUCTS.size()?PRODUCTS.get(i-1):null;}
    static Product fromStack(ItemStack s){
        for(var p:PRODUCTS)if(ItemStack.isSameItemSameComponents(s,p.stack(1)))return p;
        return null;
    }
    /** Preserve paid material even if the player deliberately fragments stacks or holds a cursor. */
    static boolean prepareKitSlots(ServerPlayer p,int needed){
        var inv=p.getInventory();ItemStack cursor=p.inventoryMenu.getCarried().copy();
        var plan=new ItemStack[36];int empty=0;
        for(int i=0;i<36;i++){plan[i]=inv.getItem(i).copy();if(plan[i].isEmpty())empty++;}
        if(empty>=needed&&cursor.isEmpty())return true;
        for(int i=0;i<36;i++)if(!plan[i].isEmpty()){
            for(int j=i+1;j<36&&plan[i].getCount()<plan[i].getMaxStackSize();j++){
                if(plan[j].isEmpty()||!ItemStack.isSameItemSameComponents(plan[i],plan[j]))continue;
                int n=Math.min(plan[i].getMaxStackSize()-plan[i].getCount(),plan[j].getCount());
                plan[i].grow(n);plan[j].shrink(n);
            }
        }
        if(!cursor.isEmpty()){
            for(int i=0;i<36&&!cursor.isEmpty();i++)if(!plan[i].isEmpty()&&ItemStack.isSameItemSameComponents(plan[i],cursor)){
                int n=Math.min(plan[i].getMaxStackSize()-plan[i].getCount(),cursor.getCount());plan[i].grow(n);cursor.shrink(n);
            }
            for(int i=0;i<36&&!cursor.isEmpty();i++)if(plan[i].isEmpty()){plan[i]=cursor.copy();cursor.setCount(0);}
        }
        if(!cursor.isEmpty()||Arrays.stream(plan).filter(ItemStack::isEmpty).count()<needed)return false;
        for(int i=0;i<36;i++)inv.setItem(i,plan[i]);p.inventoryMenu.setCarried(ItemStack.EMPTY);
        inv.setChanged();p.inventoryMenu.broadcastFullState();return true;
    }
    // Work only on a copied plan, then commit all slots on server thread; no partial purchase.
    static boolean transact(ServerPlayer p,Product product,int count,boolean buy){
        if(count<1||count>1000||p.containerMenu!=p.inventoryMenu&&!SiegeShopGui.active(p)||!p.containerMenu.getCarried().isEmpty()||!p.inventoryMenu.getCarried().isEmpty())return false;
        var inv=p.getInventory();var plan=new ItemStack[36];
        for(int i=0;i<36;i++)plan[i]=inv.getItem(i).copy();
        int left=count;
        if(buy){
            for(int i=0;i<36&&left>0;i++)if(ItemStack.isSameItemSameComponents(plan[i],product.stack(1))){
                int n=Math.min(left,plan[i].getMaxStackSize()-plan[i].getCount());plan[i].grow(n);left-=n;
            }
            for(int i=5;i<36&&left>0;i++)if(plan[i].isEmpty()){
                int n=Math.min(left,product.item.getDefaultMaxStackSize());plan[i]=product.stack(n);left-=n;
            }
        }else{
            for(int i=0;i<36&&left>0;i++)if(ItemStack.isSameItemSameComponents(plan[i],product.stack(1))){
                int n=Math.min(left,plan[i].getCount());plan[i].shrink(n);left-=n;
            }
        }
        if(left!=0)return false;
        for(int i=0;i<36;i++)inv.setItem(i,plan[i]);inv.setChanged();p.containerMenu.broadcastChanges();return true;
    }
}