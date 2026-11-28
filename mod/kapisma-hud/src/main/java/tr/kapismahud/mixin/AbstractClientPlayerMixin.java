package tr.kapismahud.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapismahud.KapismaHudClient;

/** Oyuncu bilgisi henüz yüklenmemişken de aynı skin kullanılır. */
@Mixin(value = AbstractClientPlayer.class, priority = 1100)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void kapisma$sameSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (KapismaHudClient.disguise) cir.setReturnValue(KapismaHudClient.DISGUISE_SKIN);
	}
}
