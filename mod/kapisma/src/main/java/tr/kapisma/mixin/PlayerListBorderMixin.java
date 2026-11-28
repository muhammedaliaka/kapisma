package tr.kapisma.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerListBorderMixin {
	@Redirect(method = "sendLevelInfo", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
	private ServerLevel kapisma$borderWorld(MinecraftServer server, ServerPlayer player, ServerLevel destination) {
		return destination;
	}
}
