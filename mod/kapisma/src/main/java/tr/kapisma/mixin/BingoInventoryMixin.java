package tr.kapisma.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.Kapisma;
import tr.kapisma.game.BingoGame;

/** Capture committed server inventory mutations, including pickup and crafting/slot transfer. */
@Mixin(Inventory.class)
public abstract class BingoInventoryMixin {
	@Shadow @Final public Player player;

	@Unique
	private void kapisma$observeBingo() {
		if (player instanceof ServerPlayer p && Kapisma.GM != null && Kapisma.GM.current instanceof BingoGame bingo)
			bingo.inventoryChanged(p);
	}

	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
	private void kapisma$afterAdd(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) kapisma$observeBingo();
	}

	@Inject(method = "setItem", at = @At("RETURN"))
	private void kapisma$afterSet(int slot, ItemStack stack, CallbackInfo ci) {
		kapisma$observeBingo();
	}

	@Inject(method = "setChanged", at = @At("RETURN"))
	private void kapisma$afterChange(CallbackInfo ci) {
		kapisma$observeBingo();
	}
}
