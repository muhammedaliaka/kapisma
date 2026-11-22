package tr.kapisma.lobby;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import tr.kapisma.Kapisma;
import tr.kapisma.config.Settings;
import tr.kapisma.ui.MenuService;

/**
 * Lobi panosu (ekranin sag tarafi): host, oyuncu sayisi, secili oyun ve ozeti, takim/irk, parkur ve kapisma ilk 3.
 * Oyun calisirken gizlenir (oyunlar kendi panosunu kullanir), oyun bitince geri gelir. Menu acikken de yaninda gorunur.
 */
public final class LobbyBoard {
	private LobbyBoard() {}

	private static final String NAME = "kp_lobby";
	private static Objective obj;
	private static List<String> shown;

	private static String cut(String s, int n) {
		return s.length() > n ? s.substring(0, n) : s;
	}

	private static String summary(Settings st) {
		return switch (st.game) {
			case "UHC" -> "§7PvP §f" + (int) st.num("UHC", "pvpGraceMin") + " dk sonra §7· sınır §f±" + (int) st.num("UHC", "borderStart");
			case "BINGO" -> "§f" + (int) st.num("BINGO", "lines") + " çizgi §7· PvP §f" + (!st.flag("BINGO", "pvp") ? "kapalı"
				: (int) st.num("BINGO", "pvpGraceMin") > 0 ? (int) st.num("BINGO", "pvpGraceMin") + " dk sonra" : "açık");
			case "MANHUNT" -> "§7Runner §f" + (st.runnerNames.isEmpty() ? (int) st.num("MANHUNT", "runners") + " (rastgele)" : String.join(",", st.runnerNames));
			case "KATIL" -> "§f" + (int) st.num("KATIL", "killers") + " gizli katil §7· ses önerilir";
			default -> "";
		};
	}

	private static List<String> lines(MinecraftServer server) {
		var gm = Kapisma.GM;
		Settings st = gm.settings;
		List<String> l = new ArrayList<>();
		ServerPlayer h = gm.host();
		l.add("§7Host §f" + (h != null ? cut(h.getGameProfile().getName(), 14) : "yok"));
		l.add("§7Oyuncu §f" + server.getPlayerList().getPlayerCount() + "§7/8");
		l.add("§8──────────");
		l.add("§a▶ §f" + MenuService.gameLabel(st.game));
		l.add(summary(st));
		l.add("§7" + ("DUO".equals(st.teamMode) ? "Duo" : "Solo") + " §8· §7Irk §f" + MenuService.originModeLabel(st.originMode.getOrDefault(st.game, "RANDOM")).split(" ")[0]);
		l.add("§8──────────");
		l.add("§6Parkur ilk 3");
		var tp = Lobby.scores.topParkour(3);
		for (int i = 0; i < 3; i++) l.add(i < tp.size() ? "§f" + (i + 1) + ". " + cut(tp.get(i).name, 10) + " §a" + tp.get(i).parkourPoints : "§8" + (i + 1) + ". —");
		l.add("§6Kapışma ilk 3");
		var tg = Lobby.scores.topGames(3);
		for (int i = 0; i < 3; i++) l.add(i < tg.size() ? "§f" + (i + 1) + ". " + cut(tg.get(i).name, 10) + " §a" + tg.get(i).gamePoints : "§8" + (i + 1) + ". —");
		return l;
	}

	/** Her 20 tikte bir cagrilir. */
	public static void tick(MinecraftServer server) {
		var gm = Kapisma.GM;
		if (gm == null || Lobby.scores == null) return;
		var sb = server.getScoreboard();
		boolean gameOn = gm.current != null && !gm.current.isClosed();
		var cur = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		boolean ours = obj != null && cur == obj;
		if (gameOn) {
			if (ours) sb.setDisplayObjective(DisplaySlot.SIDEBAR, null);
			return;
		}
		if (cur != null && !ours) {
			// oyun yokken kalan kp_* panosu eskidir (sunucu yeniden basladi / oyun cokerek kapandi): kaldir ve yerine lobi panosunu koy.
			// kp_ ile baslamayan baska bir pano (komutla konmus) varsa dokunma.
			if (!cur.getName().startsWith("kp")) return;
			sb.removeObjective(cur);
			obj = null;
		}
		for (var o : new ArrayList<>(sb.getObjectives()))
			if (o.getName().startsWith("kp") && !o.getName().equals(NAME) && !gameOn) sb.removeObjective(o);   // eski oyun panolari
		if (obj == null || sb.getObjective(NAME) != obj) {
			var old = sb.getObjective(NAME);
			if (old != null) sb.removeObjective(old);
			obj = sb.addObjective(NAME, ObjectiveCriteria.DUMMY, Component.literal("KAPIŞMA").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
			shown = null;
		}
		if (!ours) sb.setDisplayObjective(DisplaySlot.SIDEBAR, obj);
		List<String> now = lines(server);
		for (int i = 0; i < now.size(); i++) {
			String x = now.get(i);
			for (int k = 0; k < i; k++) if (now.get(k).equals(x)) { x += "§r"; k = -1; }
			now.set(i, x);
		}
		if (now.equals(shown)) return;
		if (shown != null) for (String s : shown) sb.resetSinglePlayerScore(ScoreHolder.forNameOnly(s), obj);
		for (int i = 0; i < now.size(); i++) sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(now.get(i)), obj).set(now.size() - i);
		shown = now;
	}
}
