package tr.kapisma;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Mesaj / baslik yardimcilari. */
public final class Msg {
	private Msg() {}

	public static MutableComponent prefix() {
		return Component.literal("[Kapışma] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}

	public static Component info(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
	}

	public static Component error(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	public static void broadcast(MinecraftServer s, String text) {
		s.getPlayerList().broadcastSystemMessage(info(text), false);
	}

	public static void broadcast(MinecraftServer s, Component c) {
		s.getPlayerList().broadcastSystemMessage(c, false);
	}

	public static void tell(ServerPlayer p, String text) {
		p.sendSystemMessage(info(text));
	}

	public static void title(ServerPlayer p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.YELLOW)));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
	}

	public static void actionbar(ServerPlayer p, Component c) {
		p.connection.send(new ClientboundSetActionBarTextPacket(c));
	}

	public static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		p.playNotifySound(s, net.minecraft.sounds.SoundSource.MASTER, vol, pitch);
	}

	public static void soundAll(MinecraftServer srv, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		for (ServerPlayer p : srv.getPlayerList().getPlayers()) sound(p, s, vol, pitch);
	}

	public static String mmss(int seconds) {
		if (seconds < 0) seconds = 0;
		return String.format("%02d:%02d", seconds / 60, seconds % 60);
	}
}
