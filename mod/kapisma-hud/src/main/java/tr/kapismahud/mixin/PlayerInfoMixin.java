package tr.kapismahud.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapismahud.KapismaHudClient;

/** Katil Kim sirasinda herkesin skin'i ayni (sunucu "disguise" paketi gonderince). */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void kapisma$sameSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (KapismaHudClient.disguise) cir.setReturnValue(KapismaHudClient.DISGUISE_SKIN);
	}
}
