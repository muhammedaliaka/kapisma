package tr.kapisma.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.TeamMsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tr.kapisma.chat.ChatService;

@Mixin(TeamMsgCommand.class)
abstract class TeamChatMixin {
	@Redirect(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
	private static void kapisma$isolate(ServerPlayer receiver, OutgoingChatMessage message, boolean filtered, ChatType.Bound type,
		CommandSourceStack source, net.minecraft.world.entity.Entity entity, net.minecraft.world.scores.PlayerTeam team, java.util.List<ServerPlayer> targets, PlayerChatMessage original) {
		if (!ChatService.filterVanilla(source, java.util.List.of(receiver)).isEmpty())
			receiver.sendChatMessage(message, filtered, type);
	}
}
