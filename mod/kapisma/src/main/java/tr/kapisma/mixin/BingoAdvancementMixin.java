package tr.kapisma.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.Kapisma;
import tr.kapisma.game.BingoGame;

/** Observe a committed advancement award before the next server action. */
@Mixin(PlayerAdvancements.class)
abstract class BingoAdvancementMixin {
	@Shadow private ServerPlayer player;

	@Inject(method = "award", at = @At("RETURN"))
	private void kapisma$completed(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && player != null && Kapisma.GM != null
			&& Kapisma.GM.current instanceof BingoGame bingo)
			bingo.advancementCompleted(player, advancement.id());
	}
}
