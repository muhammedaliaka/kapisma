package tr.kapisma.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Solo / Duo takimlari vanilla scoreboard takimi olarak kurar (renkli isim, takim ici ateş kontrolü). */
public final class TeamService {
	private TeamService() {}

	public static final String PREFIX = "kp_";

	/** Oyuncunun lobide sectigi takim rengi / ismi (bos = otomatik). */
	public static final class Pref {
		public ChatFormatting color;
		public String name;
	}

	public static final ChatFormatting[] COLORS = {
		ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
		ChatFormatting.LIGHT_PURPLE, ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.DARK_GREEN,
		ChatFormatting.DARK_PURPLE, ChatFormatting.WHITE
	};
	public static final String[] COLOR_NAMES = {
		"Kırmızı", "Mavi", "Yeşil", "Sarı", "Pembe", "Turkuaz", "Turuncu", "Koyu Yeşil", "Mor", "Beyaz"
	};

	public static String colorName(ChatFormatting c) {
		for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == c) return COLOR_NAMES[i];
		return c.getName();
	}

	public static final class GameTeam {
		public final int index;
		public final String id;
		public final ChatFormatting color;
		public final String name;
		public final List<UUID> members = new ArrayList<>();

		GameTeam(int index, String id, ChatFormatting color, String name) {
			this.index = index;
			this.id = id;
			this.color = color;
			this.name = name;
		}

		public Component display() {
			return Component.literal(name).withStyle(color);
		}
	}

	/**
	 * Oyunculari karistirir; duo ise ciftler (tek kalan solo). Takimlari scoreboard'a yazar.
	 * prefs: oyuncularin sectigi renk/isim. Renk ilk gelene aittir, cakisirsa bos renk atanir.
	 */
	public static List<GameTeam> assign(MinecraftServer s, List<ServerPlayer> players, boolean duo, boolean friendlyFire, Random rnd,
	                                    java.util.Map<UUID, Pref> prefs) {
		clear(s);
		List<ServerPlayer> shuffled = new ArrayList<>(players);
		Collections.shuffle(shuffled, rnd);
		int size = duo ? 2 : 1;
		List<List<ServerPlayer>> groups = new ArrayList<>();
		for (int i = 0; i < shuffled.size(); i += size) groups.add(new ArrayList<>(shuffled.subList(i, Math.min(i + size, shuffled.size()))));

		// 1) tercih edilen renkler (ilk gelen alir)
		ChatFormatting[] picked = new ChatFormatting[groups.size()];
		java.util.Set<ChatFormatting> used = new java.util.HashSet<>();
		for (int g = 0; g < groups.size(); g++) {
			for (ServerPlayer p : groups.get(g)) {
				Pref pr = prefs.get(p.getUUID());
				if (pr != null && pr.color != null && used.add(pr.color)) { picked[g] = pr.color; break; }
			}
		}
		// 2) kalanlara bos renkler
		int next = 0;
		for (int g = 0; g < groups.size(); g++) {
			if (picked[g] != null) continue;
			while (next < COLORS.length && used.contains(COLORS[next])) next++;
			ChatFormatting c = COLORS[next % COLORS.length];
			used.add(c);
			picked[g] = c;
			next++;
		}

		List<GameTeam> teams = new ArrayList<>();
		Scoreboard sb = s.getScoreboard();
		for (int g = 0; g < groups.size(); g++) {
			List<ServerPlayer> group = groups.get(g);
			String name = null;
			for (ServerPlayer p : group) {
				Pref pr = prefs.get(p.getUUID());
				if (pr != null && pr.name != null && !pr.name.isBlank()) { name = pr.name.trim(); break; }
			}
			if (name == null) name = group.size() == 1 && !duo ? group.get(0).getGameProfile().getName() : colorName(picked[g]) + " Takım";
			GameTeam gt = new GameTeam(teams.size(), PREFIX + teams.size(), picked[g], name);
			PlayerTeam pt = sb.addPlayerTeam(gt.id);
			pt.setColor(gt.color);
			pt.setDisplayName(Component.literal(name));
			pt.setAllowFriendlyFire(friendlyFire);
			pt.setSeeFriendlyInvisibles(true);
			for (ServerPlayer p : group) {
				sb.addPlayerToTeam(p.getScoreboardName(), pt);
				gt.members.add(p.getUUID());
			}
			teams.add(gt);
		}
		return teams;
	}

	/** Manhunt: iki sabit takim (hunter / runner). */
	public static List<GameTeam> assignRoles(MinecraftServer s, List<ServerPlayer> runners, List<ServerPlayer> hunters, boolean friendlyFire) {
		clear(s);
		Scoreboard sb = s.getScoreboard();
		List<GameTeam> teams = new ArrayList<>();
		String[] ids = {PREFIX + "runner", PREFIX + "hunter"};
		String[] names = {"Runner", "Hunter"};
		ChatFormatting[] cols = {ChatFormatting.GREEN, ChatFormatting.RED};
		List<List<ServerPlayer>> groups = List.of(runners, hunters);
		for (int i = 0; i < 2; i++) {
			GameTeam gt = new GameTeam(i, ids[i], cols[i], names[i]);
			PlayerTeam pt = sb.addPlayerTeam(ids[i]);
			pt.setColor(cols[i]);
			pt.setDisplayName(Component.literal(names[i]));
			pt.setAllowFriendlyFire(friendlyFire);
			for (ServerPlayer p : groups.get(i)) {
				sb.addPlayerToTeam(p.getScoreboardName(), pt);
				gt.members.add(p.getUUID());
			}
			teams.add(gt);
		}
		return teams;
	}

	/** Katil Kim: roller sunucuda kalır; istemcilere herkes aynı takımda görünür. */
	public static List<GameTeam> assignRolesHidden(MinecraftServer s, List<ServerPlayer> innocents, List<ServerPlayer> killers) {
		clear(s);
		Scoreboard sb = s.getScoreboard();
		PlayerTeam visibleTeam = sb.addPlayerTeam(PREFIX + "gizli");
		visibleTeam.setColor(ChatFormatting.WHITE);
		visibleTeam.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
		visibleTeam.setAllowFriendlyFire(true);
		List<GameTeam> teams = new ArrayList<>();
		String[] ids = {PREFIX + "masum", PREFIX + "katil"};
		String[] names = {"Masumlar", "Katil"};
		List<List<ServerPlayer>> groups = List.of(innocents, killers);
		for (int i = 0; i < 2; i++) {
			GameTeam gt = new GameTeam(i, ids[i], ChatFormatting.WHITE, names[i]);
			for (ServerPlayer p : groups.get(i)) {
				sb.addPlayerToTeam(p.getScoreboardName(), visibleTeam);
				gt.members.add(p.getUUID());
			}
			teams.add(gt);
		}
		return teams;
	}

	public static void clear(MinecraftServer s) {
		Scoreboard sb = s.getScoreboard();
		for (PlayerTeam t : new ArrayList<>(sb.getPlayerTeams())) {
			if (t.getName().startsWith(PREFIX)) sb.removePlayerTeam(t);
		}
	}
}
