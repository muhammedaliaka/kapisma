package tr.kapisma.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.xray.Xray;

/** Blok degisince yakindaki oyuncular bir sonraki tikte gizli bloklari yeniden degerlendirir. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
	@Shadow @Final ServerLevel level;

	@Inject(method = "blockChanged", at = @At("HEAD"))
	private void kapisma$changed(BlockPos pos, CallbackInfo ci) {
		try { Xray.onBlockChanged(this.level, pos); } catch (Throwable t) { tr.kapisma.Kapisma.LOG.error("anti-xray onBlockChanged", t); }
	}
}
