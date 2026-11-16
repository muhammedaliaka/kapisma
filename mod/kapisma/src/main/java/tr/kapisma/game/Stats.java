package tr.kapisma.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import tr.kapisma.origin.OriginService;
import tr.kapisma.team.TeamService.GameTeam;

/** /istatistik: son (veya calisan) oyunda kim kimi kac kez oldurdu, kim oldurdu. */
public final class Stats {
	private Stats() {}

	private static String name(Game g, UUID id) {
		return g.names.getOrDefault(id, id.toString().substring(0, 6));
	}

	public static List<String> playerNames(Game g) {
		return g == null ? List.of() : new ArrayList<>(g.names.values());
	}

	private static UUID byName(Game g, String n) {
		for (Map.Entry<UUID, String> e : g.names.entrySet()) if (e.getValue().equalsIgnoreCase(n)) return e.getKey();
		return null;
	}

	public static int send(ServerPlayer viewer, Game g, String target) {
		if (g == null || g.names.isEmpty()) {
			viewer.sendSystemMessage(Component.literal("Gösterilecek oyun yok: önce bir oyun oynanmalı.").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (target == null) {
			UUID me = viewer.getUUID();
			if (g.names.containsKey(me)) return one(viewer, g, me);
			return table(viewer, g);
		}
		UUID id = byName(g, target);
		if (id == null) {
			viewer.sendSystemMessage(Component.literal("Oyuncu bulunamadı: " + target + ". Oyuncular: " + String.join(", ", playerNames(g))).withStyle(ChatFormatting.RED));
			return 0;
		}
		return one(viewer, g, id);
	}

	private static int table(ServerPlayer v, Game g) {
		v.sendSystemMessage(Component.literal("━━ " + g.title() + " istatistikleri ━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (UUID id : g.players) {
			String nm = name(g, id);
			v.sendSystemMessage(Component.literal(nm).withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.YELLOW)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/istatistik " + nm)))
				.append(Component.literal("  öldürme " + g.kills.getOrDefault(id, 0) + "  ölüm " + g.deaths.getOrDefault(id, 0)).withStyle(ChatFormatting.WHITE)));
		}
		return 1;
	}

	private static int one(ServerPlayer v, Game g, UUID id) {
		GameTeam t = g.teamOf(id);
		String o = g.originOf(id);
		v.sendSystemMessage(Component.literal("━━ " + name(g, id)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal((o != null ? "  (" + OriginService.label(o) + ")" : "") + (t != null ? "  " + t.name : "") + " ━━")
				.withStyle(t != null ? t.color : ChatFormatting.GRAY)));
		Map<UUID, Integer> killed = g.killMatrix.getOrDefault(id, Map.of());
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<UUID, Integer> e : killed.entrySet()) sb.append(name(g, e.getKey())).append(" ×").append(e.getValue()).append("   ");
		v.sendSystemMessage(Component.literal("Öldürdükleri: ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal(sb.length() == 0 ? "kimseyi öldürmedi" : sb.toString()).withStyle(ChatFormatting.WHITE)));
		sb.setLength(0);
		for (Map.Entry<UUID, Map<UUID, Integer>> e : g.killMatrix.entrySet()) {
			Integer n = e.getValue().get(id);
			if (n != null) sb.append(name(g, e.getKey())).append(" ×").append(n).append("   ");
		}
		for (Map.Entry<String, Integer> e : g.envDeaths.getOrDefault(id, Map.of()).entrySet()) sb.append(e.getKey()).append(" ×").append(e.getValue()).append("   ");
		v.sendSystemMessage(Component.literal("Onu öldürenler: ").withStyle(ChatFormatting.RED)
			.append(Component.literal(sb.length() == 0 ? "hiç ölmedi" : sb.toString()).withStyle(ChatFormatting.WHITE)));
		v.sendSystemMessage(Component.literal("Toplam: öldürme " + g.kills.getOrDefault(id, 0) + " | ölüm " + g.deaths.getOrDefault(id, 0)).withStyle(ChatFormatting.GRAY));
		return 1;
	}
}
