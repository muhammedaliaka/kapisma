package tr.kapisma.mixin;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
	@Invoker("updatePlayer")
	void kapisma$updatePlayer(ServerPlayer viewer);

	@Accessor("seenBy")
	Set<ServerPlayerConnection> kapisma$seenBy();
}
