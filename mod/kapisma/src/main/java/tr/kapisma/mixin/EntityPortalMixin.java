package tr.kapisma.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;

/** UHC/Tombala gecici dunyalarinda Nether ve End portallari kapali: portaldan geciş oyuncuyu ortak dunyalara tasirdi. */
@Mixin(Entity.class)
public abstract class EntityPortalMixin {
	@Inject(method = "setAsInsidePortal", at = @At("HEAD"), cancellable = true)
	private void kapisma$blockPortal(Portal portal, BlockPos pos, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide || Kapisma.GM == null || Kapisma.GM.current == null) return;
		if (!Kapisma.GM.current.blocksPortals(self.level())) return;
		if (Kapisma.GM.current instanceof tr.kapisma.game.BingoGame game
			&& (portal instanceof net.minecraft.world.level.block.NetherPortalBlock || portal instanceof net.minecraft.world.level.block.EndPortalBlock)
			&& game.permitsPortal(self.level(), portal, self)) {
			if (self instanceof ServerPlayer player && self.level() instanceof net.minecraft.server.level.ServerLevel source
					&& !game.portalReady(source, player, portal instanceof net.minecraft.world.level.block.EndPortalBlock)) {
				ci.cancel();
				if (player.tickCount % 20 == 0) Msg.actionbar(player, net.minecraft.network.chat.Component.literal("Portalın hedef bölgesi hazırlanıyor."));
			}
			return;
		}
		ci.cancel();
		if (self instanceof ServerPlayer p && p.tickCount % 40 == 0) Msg.actionbar(p, net.minecraft.network.chat.Component.literal("Bu portal maçta açık değil."));
	}
}
