package tr.kapisma.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tr.kapisma.Kapisma;
import tr.kapisma.game.BingoGame;

@Mixin({EndPortalBlock.class, NetherPortalBlock.class})
public abstract class ArenaPortalMixin {
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void kapisma$arenaDestination(ServerLevel source, Entity traveler, BlockPos pos,
			CallbackInfoReturnable<DimensionTransition> cir) {
		if (Kapisma.GM != null && Kapisma.GM.current instanceof BingoGame game && game.blocksPortals(source)) {
			cir.setReturnValue(game.portalTransition(source, traveler, (Object) this instanceof EndPortalBlock));
		}
	}
}
