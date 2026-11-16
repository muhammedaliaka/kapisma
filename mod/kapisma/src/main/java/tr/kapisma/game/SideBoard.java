package tr.kapisma.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Oyun ici yan panel (sag ortada skor tablosu): satirlari verilen listeden gunceller, degismediyse paket gondermez. */
final class SideBoard {
	private final MinecraftServer server;
	private final String name;
	private Objective obj;
	private List<String> last;

	SideBoard(MinecraftServer server, String name, String title) {
		this.server = server;
		this.name = name;
		var sb = server.getScoreboard();
		var old = sb.getObjective(name);
		if (old != null) sb.removeObjective(old);
		obj = sb.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
			ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
		sb.setDisplayObjective(DisplaySlot.SIDEBAR, obj);
	}

	void update(List<String> in) {
		if (obj == null) return;
		var sb = server.getScoreboard();
		List<String> lines = new ArrayList<>(in.size() > 15 ? in.subList(0, 15) : in);
		for (int i = 0; i < lines.size(); i++) {           // ayni metinli satirlar tek skor olur: gorunmez ek ile ayir
			String l = lines.get(i);
			for (int k = 0; k < i; k++) if (lines.get(k).equals(l)) { l += "§r"; k = -1; }
			lines.set(i, l);
		}
		if (lines.equals(last)) return;
		if (last != null) for (String l : last) sb.resetSinglePlayerScore(ScoreHolder.forNameOnly(l), obj);
		for (int i = 0; i < lines.size(); i++) sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(lines.get(i)), obj).set(lines.size() - i);
		last = lines;
	}

	void close() {
		if (obj == null) return;
		var sb = server.getScoreboard();
		if (sb.getDisplayObjective(DisplaySlot.SIDEBAR) == obj) sb.setDisplayObjective(DisplaySlot.SIDEBAR, null);
		var o = sb.getObjective(name);
		if (o != null) sb.removeObjective(o);
		obj = null;
		last = null;
	}
}
