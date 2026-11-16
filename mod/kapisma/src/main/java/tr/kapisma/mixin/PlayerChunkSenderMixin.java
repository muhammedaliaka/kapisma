package tr.kapisma.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.xray.Xray;

/** Hangi oyuncuya hangi chunk gitti: gizli bloklari yaklasinca acmak icin izlenir. */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {
	@Inject(method = "sendChunk", at = @At("TAIL"))
	private static void kapisma$sent(ServerGamePacketListenerImpl handler, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
		try { Xray.onChunkSent(handler.getPlayer(), level, chunk); } catch (Throwable t) { tr.kapisma.Kapisma.LOG.error("anti-xray onChunkSent", t); }
	}

	@Inject(method = "dropChunk", at = @At("HEAD"))
	private void kapisma$drop(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
		try { Xray.onChunkDropped(player, pos); } catch (Throwable t) { tr.kapisma.Kapisma.LOG.error("anti-xray onChunkDropped", t); }
	}
}
