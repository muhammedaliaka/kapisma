package tr.kapisma.lobby;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import tr.kapisma.Msg;

/** Lobideki yazi panolari (text_display): hos geldin, parkur/kapisma ilk 3. */
final class Boards {
	private Boards() {}

	static final String TAG_PARKOUR = "kp_board_parkur";
	static final String TAG_GAME = "kp_board_game";
	static final String TAG_INFO = "kp_info";

	/** Verilen etiketli panoyu kaldirir, yenisini koyar. */
	static void put(ServerLevel l, String tag, double x, double y, double z, Component text, float scale) {
		AABB box = new AABB(x - 3, y - 4, z - 3, x + 3, y + 4, z + 3);
		for (Display.TextDisplay old : l.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Display.TextDisplay.class), box, e -> e.getTags().contains(tag)))
			old.discard();
		CompoundTag t = new CompoundTag();
		t.putString("id", "minecraft:text_display");
		t.putString("text", Component.Serializer.toJson(text, l.registryAccess()));
		t.putString("billboard", "center");
		t.putString("alignment", "center");
		t.putInt("line_width", 260);
		t.putInt("background", 0x66000000);
		t.putFloat("view_range", 1.5f);
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf(tag));
		t.put("Tags", tags);
		CompoundTag tr = new CompoundTag();
		tr.put("translation", floats(0, 0, 0));
		tr.put("scale", floats(scale, scale, scale));
		tr.put("left_rotation", floats(0, 0, 0, 1));
		tr.put("right_rotation", floats(0, 0, 0, 1));
		t.put("transformation", tr);
		Optional<Entity> e = EntityType.create(t, l);
		if (e.isEmpty()) return;
		e.get().moveTo(x, y, z, 0f, 0f);
		l.addFreshEntity(e.get());
	}

	private static ListTag floats(float... v) {
		ListTag l = new ListTag();
		for (float f : v) l.add(FloatTag.valueOf(f));
		return l;
	}

	private static MutableComponent line(String s, ChatFormatting... f) {
		return Component.literal(s).withStyle(f);
	}

	private static String mmss(int sec) {
		return Msg.mmss(sec);
	}

	/** Puan panolarini guncelle. */
	static void refresh(ServerLevel l, Scores sc) {
		MutableComponent pk = line("PARKUR  -  İLK 3\n", ChatFormatting.GOLD, ChatFormatting.BOLD);
		List<Scores.Entry> tp = sc.topParkour(3);
		if (tp.isEmpty()) pk.append(line("\nHenüz kimse puan almadı.\nDoğudaki kapıdan parkura gir!", ChatFormatting.GRAY));
		for (int i = 0; i < tp.size(); i++) {
			Scores.Entry e = tp.get(i);
			pk.append(line("\n" + (i + 1) + ". ", i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
			pk.append(line(e.name, ChatFormatting.AQUA));
			pk.append(line("  " + e.parkourPoints + " puan", ChatFormatting.GREEN));
			if (e.bestTimeSec >= 0) pk.append(line("  " + mmss(e.bestTimeSec), ChatFormatting.GRAY));
		}
		pk.append(line("\n\nBölüm puanı: Kolay 10 · Orta 20 · Zor 40 · Çok Zor 80", ChatFormatting.DARK_GRAY));
		put(l, TAG_PARKOUR, Lobby.HUB_X_BOARD_PARKOUR[0], Lobby.HUB_X_BOARD_PARKOUR[1], Lobby.HUB_X_BOARD_PARKOUR[2], pk, 1.5f);

		MutableComponent gm = line("KAPIŞMA  -  İLK 3\n", ChatFormatting.RED, ChatFormatting.BOLD);
		List<Scores.Entry> tg = sc.topGames(3);
		if (tg.isEmpty()) gm.append(line("\nHenüz oyun oynanmadı.\nNether Yıldızı ile oyun başlat!", ChatFormatting.GRAY));
		for (int i = 0; i < tg.size(); i++) {
			Scores.Entry e = tg.get(i);
			gm.append(line("\n" + (i + 1) + ". ", i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
			gm.append(line(e.name, ChatFormatting.AQUA));
			gm.append(line("  " + e.gamePoints + " puan", ChatFormatting.GREEN));
			gm.append(line("  " + e.wins + " galibiyet", ChatFormatting.GRAY));
		}
		gm.append(line("\n\nGalibiyet 100 · Öldürme 10 · Katılım 5", ChatFormatting.DARK_GRAY));
		put(l, TAG_GAME, Lobby.HUB_X_BOARD_GAME[0], Lobby.HUB_X_BOARD_GAME[1], Lobby.HUB_X_BOARD_GAME[2], gm, 1.5f);
	}
}
