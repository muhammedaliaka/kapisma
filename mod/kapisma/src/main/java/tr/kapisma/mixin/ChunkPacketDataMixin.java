package tr.kapisma.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tr.kapisma.Kapisma;
import tr.kapisma.xray.Xray;

/** Chunk paketi olusturulunca (sadece korunan oyun dunyalarinda) goruntuyu "halusinasyon" ile degistirir. */
@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ChunkPacketDataMixin {
	@Mutable @Shadow @Final private byte[] buffer;
	@Mutable @Shadow @Final private List<Object> blockEntitiesData;

	@Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("TAIL"))
	private void kapisma$xray(LevelChunk chunk, CallbackInfo ci) {
		try {
			Xray.Built b = Xray.build(chunk);
			if (b == null) return;
			this.buffer = b.buffer();
			List<Object> kept = new ArrayList<>(this.blockEntitiesData.size());
			for (Object o : this.blockEntitiesData) {
				BEInfoAccessor a = (BEInfoAccessor) o;
				int packed = a.kapisma$packedXZ();
				if (b.isHidden(packed >> 4, a.kapisma$y(), packed & 15)) continue;
				kept.add(o);
			}
			this.blockEntitiesData = kept;
		} catch (Throwable t) {
			Kapisma.LOG.error("anti-xray chunk hatasi (gercek paket gonderiliyor)", t);
		}
	}
}
