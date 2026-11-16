package tr.kapisma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo")
public interface BEInfoAccessor {
	@Accessor("packedXZ")
	int kapisma$packedXZ();

	@Accessor("y")
	int kapisma$y();
}
