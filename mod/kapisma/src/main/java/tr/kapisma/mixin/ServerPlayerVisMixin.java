package tr.kapisma.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.guard.AntiCheat;

/** Duvar arkasindaki oyuncu, bakan oyuncunun istemcisine hic gonderilmez (ESP / wallhack, oyuncu radari etkisiz). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerVisMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void kapisma$cull(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
		if (AntiCheat.hide((ServerPlayer) (Object) this, viewer)) cir.setReturnValue(false);
	}
}
