package tr.kapisma.mixin;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.Kapisma;
import tr.kapisma.game.NativeArenaGame;
@Mixin(ServerPlayer.class)
public abstract class ArenaPlayerEquipmentMixin {
    @Inject(method="drop(Z)Z",at=@At("HEAD"),cancellable=true)
    private void kapisma$denyDropBeforeRemoval(boolean entire,CallbackInfoReturnable<Boolean> ci){
        var p=(ServerPlayer)(Object)this;
        if(Kapisma.GM!=null&&Kapisma.GM.current instanceof NativeArenaGame arena&&arena.locksEquipment(p))ci.setReturnValue(false);
    }
}
