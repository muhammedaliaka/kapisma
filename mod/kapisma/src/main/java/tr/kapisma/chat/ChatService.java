package tr.kapisma.chat;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;
import tr.kapisma.game.Game;
import tr.kapisma.team.TeamService.GameTeam;

/**
 * Sohbet kanallari. Lobide normal yazi herkese gider. Oyun basladiktan sonra (takimlar varken)
 * normal yazi SADECE takima gider; herkese/baska takima/tek kisiye yazmak icin komut gerekir.
 * Elenenler sadece diger elenenlerle (seyirci) yazisir.
 */
public final class ChatService {
	private ChatService() {}

	private static Game tipGame;
	private static final Set<UUID> tipped = new HashSet<>();

	private static Game game() {
		return Kapisma.GM != null ? Kapisma.GM.current : null;
	}

	public static boolean isSpectator(ServerPlayer p) {
		Game g = game();
		return g != null && !g.isClosed() && g.contains(p.getUUID()) && (p.isSpectator() || g.eliminated.contains(p.getUUID()));
	}

	private static boolean teamChatActive(ServerPlayer p) {
		Game g = game();
		return g != null && g.contains(p.getUUID()) && g.phase != Game.Phase.ENDED && g.teamOf(p.getUUID()) != null;
	}

	/** Normal (komutsuz) sohbet yazisi. true = vanilla olarak herkese gitsin. */
	public static boolean handlePlain(ServerPlayer sender, String text) {
		Game g = game();
		if (g == null || g.isClosed()) return true;
		if (!g.contains(sender.getUUID())) {
			all(sender, text); // lobby arrivals must not broadcast to active spectators
			return false;
		}
		if (isSpectator(sender)) {
			Component line = Component.literal("[Seyirci] " + name(sender) + ": " + text).withStyle(ChatFormatting.GRAY);
			for (ServerPlayer r : sender.getServer().getPlayerList().getPlayers()) {
				if (isSpectator(r)) r.sendSystemMessage(line);
			}
			return false;
		}
		if (!teamChatActive(sender)) { all(sender, text); return false; }
		if (g.globalChat()) { all(sender, text); return false; }
		team(sender, text);
		if (tipGame != g) { tipGame = g; tipped.clear(); }
		if (tipped.add(sender.getUUID())) {
			Msg.tell(sender, "Oyun sırasında yazdığın mesaj sadece takımına gider. Herkese: /herkes <mesaj>, başka takıma: /takim <ad> <mesaj>, tek kişiye: /ozel <oyuncu> <mesaj>");
		}
		return false;
	}

	/** Filter vanilla /msg, /tell, /w and /teammsg recipients before sending. */
	public static java.util.Collection<ServerPlayer> filterVanilla(net.minecraft.commands.CommandSourceStack source, java.util.Collection<ServerPlayer> recipients) {
		ServerPlayer sender = source.getPlayer();
		if (sender == null) return recipients;
		var filtered = recipients.stream().filter(r -> isSpectator(sender) == isSpectator(r)).toList();
		if (filtered.size() != recipients.size()) {
			Msg.tell(sender, "Seyirciler kendi aralarında yazışabilir.");
			Kapisma.LOG.info("Sohbet izolasyonu: {} alıcı engellendi ({})", recipients.size() - filtered.size(), sender.getGameProfile().getName());
		}
		return filtered;
	}

	private static String name(ServerPlayer p) {
		return p.getGameProfile().getName();
	}

	private static Component tag(String t, ChatFormatting c) {
		return Component.literal("[" + t + "] ").withStyle(c);
	}

	private static Component body(ServerPlayer from, String text) {
		return Component.literal(name(from) + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
	}

	private static void deliver(ServerPlayer from, Iterable<ServerPlayer> to, Component line) {
		for (ServerPlayer r : to) if (isSpectator(from) == isSpectator(r)) r.sendSystemMessage(line);
		from.getServer().sendSystemMessage(line);
	}

	/** Kendi takima. Oyun yoksa uyarir. */
	public static int team(ServerPlayer p, String text) {
		Game g = game();
		if (g == null || !g.contains(p.getUUID()) || g.teamOf(p.getUUID()) == null) {
			Msg.tell(p, "Takım sohbeti sadece oyun içinde çalışır. Lobide normal yazı herkese gider.");
			return 0;
		}
		if (isSpectator(p)) { Msg.tell(p, "Yalnızca diğer seyircilere yazabilirsin."); return 0; }
		if ("KATIL".equals(g.id())) return all(p, text);
		GameTeam t = g.teamOf(p.getUUID());
		Component line = tag("Takım", ChatFormatting.AQUA).copy().append(body(p, text));
		deliver(p, members(g, t), line);
		return 1;
	}

	private static List<ServerPlayer> members(Game g, GameTeam t) {
		return t.members.stream().map(id -> p(g, id)).filter(x -> x != null).toList();
	}

	private static ServerPlayer p(Game g, UUID id) {
		return Kapisma.GM.server.getPlayerList().getPlayer(id);
	}

	/** Herkese. */
	public static int all(ServerPlayer p, String text) {
		if (isSpectator(p)) { Msg.tell(p, "Yalnızca diğer seyircilere yazabilirsin."); return 0; }
		Component line = tag("Herkes", ChatFormatting.GOLD).copy().append(body(p, text));
		deliver(p, p.getServer().getPlayerList().getPlayers(), line);
		return 1;
	}

	/** Belirli bir takima (ad veya renk adi; bosluk yerine _ kullanilabilir). */
	public static int toTeam(ServerPlayer p, String teamToken, String text) {
		Game g = game();
		if (g == null || g.teams.isEmpty() || !g.contains(p.getUUID())) {
			Msg.tell(p, "Takımlar oyun başlayınca oluşur. Şimdilik /herkes veya /ozel <oyuncu> kullan.");
			return 0;
		}
		if (isSpectator(p) || "KATIL".equals(g.id())) { Msg.tell(p, "Bu oyunda hedefli takım sohbetini kullanamazsın."); return 0; }
		String key = norm(teamToken);
		GameTeam found = null;
		for (GameTeam t : g.teams) {
			if (norm(t.name).equals(key)) { found = t; break; }
		}
		if (found == null) {
			for (GameTeam t : g.teams) if (norm(t.name).startsWith(key)) { found = t; break; }
		}
		if (found == null) {
			StringBuilder sb = new StringBuilder();
			for (GameTeam t : g.teams) sb.append(t.name.replace(' ', '_')).append("  ");
			Msg.tell(p, "Takım bulunamadı: " + teamToken + ". Takımlar: " + sb);
			return 0;
		}
		GameTeam mine = g.teamOf(p.getUUID());
		Component line = Component.literal("[→ " + found.name + "] ").withStyle(found.color).copy().append(body(p, text));
		List<ServerPlayer> to = new java.util.ArrayList<>(members(g, found));
		if (mine != found) to.add(p);
		deliver(p, to, line);
		return 1;
	}

	/** Tek kisiye. */
	public static int whisper(ServerPlayer p, ServerPlayer target, String text) {
		if (isSpectator(p) != isSpectator(target)) {
			Msg.tell(p, "Seyirciler kendi aralarında yazışabilir.");
			return 0;
		}
		Component toT = Component.literal("[Özel] ").withStyle(ChatFormatting.LIGHT_PURPLE).copy().append(body(p, text));
		Component toP = Component.literal("[Özel → " + name(target) + "] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
		target.sendSystemMessage(toT);
		if (target != p) p.sendSystemMessage(toP);
		p.getServer().sendSystemMessage(toT);
		return 1;
	}

	private static String norm(String s) {
		return s.toLowerCase(new Locale("tr")).replace(' ', '_');
	}

	public static List<String> teamTokens() {
		Game g = game();
		if (g == null || "KATIL".equals(g.id())) return List.of();
		return g.teams.stream().map(t -> t.name.replace(' ', '_')).toList();
	}
}
