package tr.kapisma.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.Kapisma;
import tr.kapisma.game.NativeArenaGame;
@Mixin(ItemEntity.class)
public abstract class ArenaItemPickupMixin {
    @Inject(method="playerTouch(Lnet/minecraft/world/entity/player/Player;)V",at=@At("HEAD"),cancellable=true)
    private void kapisma$denyForeignItem(Player p,CallbackInfo ci){
        if(p instanceof ServerPlayer sp&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame arena&&arena.locksEquipment(sp))ci.cancel();
    }
}
