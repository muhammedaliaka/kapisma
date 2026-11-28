package tr.kapismahud.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapismahud.KapismaHudClient;

/** Katil Kim'de P, vanilla sosyal etkileşim menüsüyle çakışır. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void kapisma$handleKillerKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		if (window == Minecraft.getInstance().getWindow().getWindow() && KapismaHudClient.handleKillerKey(key, scancode, action)) ci.cancel();
	}
}
