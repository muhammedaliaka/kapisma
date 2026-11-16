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
		ci.cancel();
		if (self instanceof ServerPlayer p && p.tickCount % 40 == 0) Msg.actionbar(p, net.minecraft.network.chat.Component.literal("Bu oyunda Nether ve End kapalı."));
	}
}
