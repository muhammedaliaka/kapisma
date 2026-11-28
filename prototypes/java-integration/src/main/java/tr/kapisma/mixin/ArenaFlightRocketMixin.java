package tr.kapisma.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.Kapisma;
import tr.kapisma.game.NativeArenaGame;
@Mixin(FireworkRocketItem.class)
public abstract class ArenaFlightRocketMixin {
    @Inject(method="use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",at=@At("HEAD"),cancellable=true)
    private void kapisma$flightRocket(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci){
        if(player instanceof ServerPlayer p&&Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame game){
            var result=game.useFlightRocket(p,hand);if(result!=null)ci.setReturnValue(result);
        }
    }
}
