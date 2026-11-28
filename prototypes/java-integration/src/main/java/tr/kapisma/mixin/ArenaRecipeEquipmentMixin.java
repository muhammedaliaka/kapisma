package tr.kapisma.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.Kapisma;
import tr.kapisma.game.NativeArenaGame;
@Mixin(RecipeBookMenu.class)
public abstract class ArenaRecipeEquipmentMixin {
    @Inject(method="handlePlacement(ZLnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/server/level/ServerPlayer;)V",at=@At("HEAD"),cancellable=true)
    private void kapisma$denyAutoCraft(boolean all,RecipeHolder<?> recipe,ServerPlayer p,CallbackInfo ci){
        if(Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame arena&&arena.locksEquipment(p)){
            p.containerMenu.broadcastFullState();ci.cancel();
        }
    }
}
