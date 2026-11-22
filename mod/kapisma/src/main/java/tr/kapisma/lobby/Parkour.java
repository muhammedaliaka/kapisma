package tr.kapisma.lobby;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;

/**
 * Lobi parkuru: tek kare spiral rota, 4 zorluk bolumu (kolay, orta, zor, cok zor).
 * Rota tools/parkour_gen.py ile uretilir ve fizik simulasyonuyla her ziplamasinin gecilebilir oldugu dogrulanir;
 * burada yalnizca (data/kapisma/parkour.json) okunup bloklara cevrilir.
 */
public final class Parkour {
	private Parkour() {}

	public static final String ITEM_TAG = "kapisma_pk";
	public static final String[] LEVEL_NAMES = {"KOLAY", "ORTA", "ZOR", "ÇOK ZOR"};
	private static final ChatFormatting[] LEVEL_FMT = {ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.RED};
	private static final Block[] CONC = {Blocks.LIME_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.RED_CONCRETE};
	private static final Block[] TERR = {Blocks.LIME_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA};

	public record Plat(int i, int x, int y, int z, int sx, int sz, int level, String kind, int h, int dx, int dz, int period, int on, int phase) {
		boolean isCheck() { return kind.equals("start") || kind.equals("cp") || kind.equals("end"); }
		Vec3 center() { return new Vec3(x + sx / 2.0, Lobby.BASE_Y + y + 1, z + sz / 2.0); }
	}

	public static final List<Plat> PLATS = new ArrayList<>();
	/** Yuzey blok konumu -> platform indeksi (start/cp/end). */
	private static final Map<Long, Integer> CHECK = new HashMap<>();

	private static final class Run {
		int cp;
		long startTick;
		double lastY;
		boolean finished;
		long exitAt;
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	// ------------------------------------------------------------------ veri + insa
	public static void load() {
		PLATS.clear();
		CHECK.clear();
		try (var in = Parkour.class.getResourceAsStream("/data/kapisma/parkour.json")) {
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			JsonArray arr = root.getAsJsonArray("platforms");
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				PLATS.add(new Plat(o.get("i").getAsInt(), (int) Math.round(o.get("x").getAsDouble()), o.get("y").getAsInt(),
					(int) Math.round(o.get("z").getAsDouble()), o.get("sx").getAsInt(), o.get("sz").getAsInt(),
					o.get("level").getAsInt(), o.get("kind").getAsString(),
					geti(o, "h"), geti(o, "dx"), geti(o, "dz"), geti(o, "period"), geti(o, "on"), geti(o, "phase")));
			}
		} catch (Exception e) {
			Kapisma.LOG.error("parkour.json okunamadi", e);
		}
		for (Plat p : PLATS) {
			if (!p.isCheck()) continue;
			for (int dx = 0; dx < p.sx; dx++)
				for (int dz = 0; dz < p.sz; dz++) CHECK.put(new BlockPos(p.x + dx, Lobby.BASE_Y + p.y, p.z + dz).asLong(), p.i);
		}
	}

	private static int geti(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsInt() : 0;
	}

	private static void setState(ServerLevel l, int x, int y, int z, net.minecraft.world.level.block.state.BlockState st) {
		l.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static void set(ServerLevel l, int x, int y, int z, Block b) {
		l.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	public static void build(ServerLevel l) {
		for (Plat p : PLATS) {
			int y = Lobby.BASE_Y + p.y;
			for (int dx = -1; dx <= p.sx; dx++) for (int dz = -1; dz <= p.sz; dz++) l.getChunk((p.x + dx) >> 4, (p.z + dz) >> 4);
			for (int dx = 0; dx < p.sx; dx++) {
				for (int dz = 0; dz < p.sz; dz++) {
					Block b;
					switch (p.kind) {
						case "start" -> b = Blocks.LIME_CONCRETE;
						case "cp" -> b = Blocks.GOLD_BLOCK;
						case "corner" -> b = TERR[p.level];
						case "blink" -> b = blinkOn(p, 0) ? Blocks.REDSTONE_BLOCK : Blocks.AIR;
						case "end" -> {
							boolean ring = dx == 0 || dz == 0 || dx == p.sx - 1 || dz == p.sz - 1;
							b = ring ? CONC[p.level] : (dx == p.sx / 2 && dz == p.sz / 2 ? Blocks.SEA_LANTERN : Blocks.QUARTZ_BLOCK);
						}
						default -> b = CONC[p.level];
					}
					set(l, p.x + dx, y, p.z + dz, b);
				}
			}
			if (p.kind.equals("climb")) buildClimb(l, p);
			if (p.kind.equals("end")) {
				for (int[] c : new int[][] {{p.sx - 1, 0}, {0, p.sz - 1}, {p.sx - 1, p.sz - 1}}) set(l, p.x + c[0], y + 1, p.z + c[1], Blocks.LANTERN);
				String next = p.level < 3 ? "Sonraki: " + LEVEL_NAMES[p.level + 1] : "PARKUR BİTTİ!";
				Lobby.sign(l, new BlockPos(p.x, y + 1, p.z), 8, LEVEL_NAMES[p.level], "BÖLÜM TAMAM", next, "Seçenekler sohbette");
			} else if (p.kind.equals("start")) {
				Lobby.sign(l, new BlockPos(p.x + 2, y + 1, p.z), 4, "PARKUR BAŞLANGICI", "Buradan başla", "Düşersen son", "checkpoint'e dönersin");
			} else if (p.kind.equals("cp")) {
				Lobby.sign(l, new BlockPos(p.x, y + 1, p.z), 0, "CHECKPOINT", LEVEL_NAMES[p.level], "", "");
			}
		}
	}

	/**
	 * Spiral merdiven: onceki platform (a, 3x3) uzerinde 1 blokluk direk (stone brick) ve etrafinda dolanan merdiven.
	 * Halka 3x3: koseler duz blok (sahanlik), kenar ortalari merdiven. Her yuzde 1 merdiven + sahanlik = 1 blok yukselis,
	 * dort yuz = bir tur = 4 blok. Sahanliklar duz oldugu icin donusler sorunsuz yurunur. Son sahanlik b platformuna bitisiktir.
	 */
	private static void buildClimb(ServerLevel l, Plat p) {
		Plat a = PLATS.get(p.i - 1);
		int dx = p.dx, dz = p.dz;
		int yA = Lobby.BASE_Y + a.y, yB = Lobby.BASE_Y + p.y, h = yB - yA;
		// b platformunun yakin satirinin altina ayak
		for (int dxx = 0; dxx < p.sx; dxx++) {
			for (int dzz = 0; dzz < p.sz; dzz++) {
				boolean near = dx > 0 ? dxx == 0 : dx < 0 ? dxx == p.sx - 1 : dz > 0 ? dzz == 0 : dzz == p.sz - 1;
				if (!near) continue;
				for (int y = yA - 3; y < yB; y++) set(l, p.x + dxx, y, p.z + dzz, Blocks.STONE_BRICKS);
			}
		}
		// saat yonunde: kose 0=KB(0,0) 1=KD(2,0) 2=GD(2,2) 3=GB(0,2); kenar i, kose i -> i+1 arasinda
		int[][] corner = {{0, 0}, {2, 0}, {2, 2}, {0, 2}};
		int[][] mid = {{1, 0}, {2, 1}, {1, 2}, {0, 1}};
		net.minecraft.core.Direction[] travel = {net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH,
			net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.NORTH};
		boolean[] toward = new boolean[4];       // b'ye bitisik koseler
		for (int i = 0; i < 4; i++) {
			int u = corner[i][0], v = corner[i][1];
			toward[i] = dx > 0 ? u == 2 : dx < 0 ? u == 0 : dz > 0 ? v == 2 : v == 0;
		}
		int c0 = -1, s = 1;
		search:
		for (int pass = 0; pass < 2; pass++)
			for (int i = 0; i < 4; i++)
				for (int dir : new int[] {1, -1})
					if (toward[Math.floorMod(i + dir * h, 4)] && (pass == 1 || !toward[i])) { c0 = i; s = dir; break search; }
		if (c0 < 0) { c0 = 0; s = 1; }
		// direk
		for (int y = yA + 1; y <= yB; y++) set(l, a.x + 1, y, a.z + 1, Blocks.STONE_BRICKS);
		set(l, a.x + 1, yB + 1, a.z + 1, Blocks.LANTERN);
		Block land = CONC[p.level];
		for (int j = 1; j <= h; j++) {
			int ci = Math.floorMod(c0 + (j - 1) * s, 4), cn = Math.floorMod(c0 + j * s, 4);
			int side = s == 1 ? ci : cn;
			var face = s == 1 ? travel[side] : travel[side].getOpposite();
			var stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, face);
			setState(l, a.x + mid[side][0], yA + j, a.z + mid[side][1], stair);
			set(l, a.x + corner[cn][0], yA + j, a.z + corner[cn][1], land);
		}
		int opp = Math.floorMod(c0 + 2 * s, 4);
		int sx = a.x + corner[opp][0], sz = a.z + corner[opp][1];
		double ang = Math.atan2(-(corner[c0][0] - corner[opp][0]), (corner[c0][1] - corner[opp][1]));
		int rot = Math.floorMod((int) Math.round(Math.toDegrees(ang) / 22.5), 16);
		Lobby.sign(l, new BlockPos(sx, yA + 1, sz), rot, "MERDİVEN", h + " blok yukarı", "direğin etrafında", "dolanarak çık");
	}

	// ------------------------------------------------------------------ zamanli kizilTas koprusu
	private static boolean blinkOn(Plat p, long tick) {
		return (tick + p.phase) % p.period < p.on;
	}

	private static final Map<Integer, Integer> BLINK_STATE = new HashMap<>();   // plat idx -> 0 kapali, 1 acik, 2 uyari

	/** Her 2 tikte: blink platformlarini zamana gore ac/kapa. Son 10 tikte cam uyarisi. */
	private static void blinkTick(ServerLevel l, long tick) {
		for (Plat p : PLATS) {
			if (!p.kind.equals("blink")) continue;
			long t = (tick + p.phase) % p.period;
			int state = t >= p.on ? 0 : (t >= p.on - 10 ? 2 : 1);
			Integer old = BLINK_STATE.get(p.i);
			if (old != null && old == state) continue;
			BLINK_STATE.put(p.i, state);
			Block b = state == 0 ? Blocks.AIR : state == 1 ? Blocks.REDSTONE_BLOCK : Blocks.RED_STAINED_GLASS;
			int y = Lobby.BASE_Y + p.y;
			for (int dx = 0; dx < p.sx; dx++) for (int dz = 0; dz < p.sz; dz++) set(l, p.x + dx, y, p.z + dz, b);
		}
	}

	// ------------------------------------------------------------------ esyalar
	private static ItemStack item(net.minecraft.world.item.Item it, String name, ChatFormatting color, String code) {
		ItemStack st = new ItemStack(it);
		st.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false).withColor(color).withBold(true)));
		CompoundTag tag = new CompoundTag();
		tag.putString(ITEM_TAG, code);
		st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return st;
	}

	public static String itemCode(ItemStack st) {
		CustomData d = st.get(DataComponents.CUSTOM_DATA);
		if (d == null) return null;
		CompoundTag t = d.copyTag();
		return t.contains(ITEM_TAG) ? t.getString(ITEM_TAG) : null;
	}

	private static void giveItems(ServerPlayer p) {
		p.getInventory().setItem(0, item(Items.LIME_DYE, "Son checkpoint'e dön", ChatFormatting.GREEN, "checkpoint"));
		p.getInventory().setItem(1, item(Items.RED_DYE, "Parkura baştan başla", ChatFormatting.RED, "bas"));
		p.getInventory().setItem(2, item(Items.OAK_DOOR, "Parkurdan çık", ChatFormatting.GOLD, "cik"));
	}

	private static void takeItems(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (itemCode(p.getInventory().getItem(i)) != null) p.getInventory().setItem(i, ItemStack.EMPTY);
	}

	// ------------------------------------------------------------------ calisma
	public static boolean running(ServerPlayer p) {
		return RUNS.containsKey(p.getUUID());
	}

	/** Oyuncu lobiden ayrildi/oyuna girdi: calismayi sessizce birak. */
	public static void forget(ServerPlayer p) {
		if (RUNS.remove(p.getUUID()) != null) takeItems(p);
	}

	private static void startRun(MinecraftServer s, ServerPlayer p) {
		Run r = new Run();
		r.startTick = s.getTickCount();
		r.lastY = p.getY();
		RUNS.put(p.getUUID(), r);
		Kapisma.LOG.info("PARKUR {} basladi", p.getGameProfile().getName());
		giveItems(p);
		p.sendSystemMessage(Msg.prefix().append(Component.literal("Parkur başladı! Bölümler: ").withStyle(ChatFormatting.YELLOW))
			.append(Component.literal("KOLAY").withStyle(ChatFormatting.GREEN)).append(Component.literal(" > ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("ORTA").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" > ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("ZOR").withStyle(ChatFormatting.GOLD)).append(Component.literal(" > ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("ÇOK ZOR").withStyle(ChatFormatting.RED)));
		p.sendSystemMessage(Component.literal("Altın platformlar checkpoint (kızıl köprüler ve merdivenler değil). Düşersen son checkpoint'e dönersin. Çantandaki öğelerle de dönebilir, baştan başlayabilir ya da çıkabilirsin.").withStyle(ChatFormatting.GRAY));
		Msg.sound(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, 1.4f);
	}

	private static MutableComponent button(String label, String cmd, ChatFormatting color, String tip) {
		return Component.literal("[" + label + "]").withStyle(sty -> sty.withColor(color).withBold(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tip))));
	}

	private static void sendOptions(ServerPlayer p) {
		p.sendSystemMessage(Component.literal("Ne yapmak istersin?  ").withStyle(ChatFormatting.WHITE)
			.append(button("Son checkpoint'ten devam", "/parkur checkpoint", ChatFormatting.GREEN, "Bu platformdan devam et"))
			.append(Component.literal(" "))
			.append(button("Baştan başla", "/parkur bas", ChatFormatting.RED, "Parkurun başına dön, süre sıfırlanır"))
			.append(Component.literal(" "))
			.append(button("Parkurdan çık", "/parkur cik", ChatFormatting.GOLD, "Lobi meydanına dön")));
		p.sendSystemMessage(Component.literal("Devam etmek için ileri zıpla: sonraki bölüm başlar.").withStyle(ChatFormatting.GRAY));
	}

	private static void reach(MinecraftServer s, ServerPlayer p, int idx) {
		Run r = RUNS.get(p.getUUID());
		Plat pl = PLATS.get(idx);
		if (r == null) {
			if (pl.kind.equals("start")) startRun(s, p);
			return;
		}
		if (idx <= r.cp || r.finished) return;
		r.cp = idx;
		Kapisma.LOG.info("PARKUR {} {} platformuna ulasti (bolum {}, idx {})", p.getGameProfile().getName(), pl.kind, LEVEL_NAMES[pl.level], idx);
		ServerLevel l = (ServerLevel) p.level();
		if (pl.kind.equals("cp")) {
			Msg.actionbar(p, Component.literal("Checkpoint! (" + LEVEL_NAMES[pl.level] + ")").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			Msg.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
			l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 12, 0.5, 0.5, 0.5, 0.0);
		} else if (pl.kind.equals("end")) {
			int pts = Lobby.scores.parkourLevelCleared(p.getUUID().toString(), p.getGameProfile().getName(), pl.level);
			Lobby.boardsDirty = true;
			l.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 0.8, 0.8, 0.1);
			Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
			Msg.title(p, LEVEL_NAMES[pl.level] + " bitti!", pts > 0 ? "+" + pts + " puan" : "Bu bölümün puanını almıştın", 5, 50, 15);
			if (pl.level < 3) {
				sendOptions(p);
			} else {
				r.finished = true;
				r.exitAt = s.getTickCount() + 200;
				int sec = (int) ((s.getTickCount() - r.startTick) / 20);
				boolean best = Lobby.scores.parkourFinished(p.getUUID().toString(), p.getGameProfile().getName(), sec);
				Msg.broadcast(s, p.getGameProfile().getName() + " parkuru " + Msg.mmss(sec) + " sürede bitirdi!" + (best ? " (kişisel rekor)" : ""));
				p.sendSystemMessage(Component.literal("Tebrikler, tüm parkuru bitirdin! 10 sn içinde meydana döneceksin.").withStyle(ChatFormatting.GOLD));
				Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
			}
		}
	}

	// ------------------------------------------------------------------ eylemler (komut + esya)
	public static void action(ServerPlayer p, String what) {
		if (!Lobby.inLobby(p)) return;
		Run r = RUNS.get(p.getUUID());
		MinecraftServer s = p.getServer();
		switch (what) {
			case "cik" -> {
				if (r == null) { Msg.tell(p, "Parkurda değilsin."); return; }
				forget(p);
				Lobby.toHub(p);
				Msg.tell(p, "Parkurdan çıktın.");
			}
			case "bas" -> {
				if (r == null) { startAtStart(s, p); return; }
				r.cp = 0;
				r.finished = false;
				r.startTick = s.getTickCount();
				teleport(p, PLATS.get(0));
				Msg.tell(p, "Parkur baştan başladı, süre sıfırlandı.");
			}
			case "checkpoint" -> {
				if (r == null) { Msg.tell(p, "Önce parkura başla: doğudaki yeşil platforma çık."); return; }
				teleport(p, PLATS.get(r.cp));
			}
			default -> Msg.tell(p, "Parkur: /parkur checkpoint | bas | cik");
		}
	}

	private static void startAtStart(MinecraftServer s, ServerPlayer p) {
		teleport(p, PLATS.get(0));
		startRun(s, p);
	}

	private static void teleport(ServerPlayer p, Plat pl) {
		Vec3 c = pl.center();
		p.teleportTo(Lobby.LEVEL, c.x, c.y, c.z, p.getYRot(), 0f);
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		p.fallDistance = 0;
		Run r = RUNS.get(p.getUUID());
		if (r != null) r.lastY = c.y;
	}

	// ------------------------------------------------------------------ dongu (4 tikte bir)
	public static void tick(MinecraftServer s, ServerLevel level) {
		long now = s.getTickCount();
		blinkTick(level, now);
		for (ServerPlayer p : new ArrayList<>(level.players())) {
			Run r = RUNS.get(p.getUUID());
			if (p.onGround()) {
				BlockPos below = p.blockPosition().below();
				Integer idx = CHECK.get(below.asLong());
				if (idx != null) reach(s, p, idx);
				r = RUNS.get(p.getUUID());
				if (r != null) r.lastY = p.getY();
			} else if (r != null && p.getY() < r.lastY - 6) {
				Kapisma.LOG.info("PARKUR {} dustu, checkpoint idx {}", p.getGameProfile().getName(), r.cp);
				Msg.actionbar(p, Component.literal("Düştün! Son checkpoint'e dönüyorsun").withStyle(ChatFormatting.RED));
				teleport(p, PLATS.get(r.cp));
				Msg.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1f);
			}
			if (r != null && r.finished && now >= r.exitAt) {
				forget(p);
				Lobby.toHub(p);
			}
			if (r != null && !r.finished && now % 20 < 4) {
				Plat cur = PLATS.get(r.cp);
				Msg.actionbar(p, Component.literal("Parkur · " + LEVEL_NAMES[cur.level] + " · " + Msg.mmss((int) ((now - r.startTick) / 20))).withStyle(LEVEL_FMT[cur.level]));
			}
		}
	}
}
