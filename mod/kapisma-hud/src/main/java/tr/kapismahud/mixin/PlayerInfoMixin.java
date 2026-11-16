package tr.kapismahud.mixin;

import java.util.UUID;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapismahud.KapismaHudClient;

/** Katil Kim sirasinda herkesin skin'i ayni (sunucu "disguise" paketi gonderince). */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
	private static final UUID SAME = new UUID(0L, 0L);

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void kapisma$sameSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (KapismaHudClient.disguise) cir.setReturnValue(DefaultPlayerSkin.get(SAME));
	}
}
