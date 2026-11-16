package tr.kapisma.game;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;
import tr.kapisma.team.TeamService.GameTeam;

/**
 * Tombala (Bingo, lockout): 5x5 esya karti, bir esyayi ILK alan takim o esyayi kazanir.
 * Hedefe (varsayilan 13) ulasan takim kazanir. Hedef kalan esyalarla da yakalanamiyorsa erken biter.
 */
public final class BingoGame extends WorldGame {
	public static final String CARD_TAG = "kapisma_card";

	// Nether/End gerektirmez (oyun tek dunyada, +-1000 blok). Zorluk kabaca: ne kadar ugrasi / ne kadar nadir.
	// Her tohumda Knowledge ile alandaki biyom ve yapilara gore elenir: bulunamayacak esya karta girmez.
	private static final List<Item> EASY = List.of(
		Items.COAL, Items.IRON_INGOT, Items.TORCH, Items.FURNACE, Items.CHEST, Items.STONE_PICKAXE,
		Items.BUCKET, Items.LADDER, Items.BREAD, Items.STONE_SWORD, Items.BOWL, Items.OAK_BOAT,
		Items.CRAFTING_TABLE, Items.STONE_AXE, Items.BARREL, Items.STONE_BRICKS, Items.GLASS, Items.FLINT,
		Items.APPLE, Items.BONE, Items.SUGAR, Items.PAPER, Items.WHITE_WOOL, Items.COOKED_BEEF, Items.LEATHER,
		Items.COBBLESTONE_WALL, Items.SANDSTONE, Items.OAK_SIGN, Items.PAINTING, Items.STONE_SHOVEL, Items.CLAY_BALL);
	private static final List<Item> MEDIUM = List.of(
		Items.SHIELD, Items.COMPASS, Items.CLOCK, Items.SHEARS, Items.FLINT_AND_STEEL, Items.PISTON,
		Items.MINECART, Items.IRON_PICKAXE, Items.IRON_SWORD, Items.IRON_HELMET, Items.CAMPFIRE,
		Items.SMOKER, Items.HOPPER, Items.LANTERN, Items.FISHING_ROD, Items.BOW, Items.CARTOGRAPHY_TABLE,
		Items.BOOK, Items.BLAST_FURNACE, Items.IRON_BARS, Items.RAIL, Items.GUNPOWDER, Items.STRING, Items.SPIDER_EYE,
		Items.INK_SAC, Items.SLIME_BALL, Items.CACTUS, Items.MELON_SLICE, Items.SWEET_BERRIES, Items.PUMPKIN_PIE,
		Items.MUSHROOM_STEW, Items.COOKIE, Items.KELP, Items.DRIED_KELP, Items.LILY_PAD, Items.ICE, Items.SNOWBALL,
		Items.BRICK, Items.SUGAR_CANE, Items.COCOA_BEANS, Items.VINE, Items.BAMBOO, Items.ARROW, Items.LOOM,
		Items.GLASS_BOTTLE, Items.TERRACOTTA, Items.RED_SAND, Items.BEETROOT_SOUP, Items.COMPOSTER, Items.RABBIT_STEW);
	private static final List<Item> HARD = List.of(
		Items.DIAMOND, Items.GOLDEN_APPLE, Items.TNT, Items.GOLDEN_CARROT, Items.JUKEBOX, Items.BOOKSHELF, Items.CAKE,
		Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.EMERALD,
		Items.ENDER_PEARL, Items.SADDLE, Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SPONGE, Items.PRISMARINE_SHARD,
		Items.AMETHYST_SHARD, Items.SPYGLASS, Items.PACKED_ICE, Items.GLOW_INK_SAC, Items.LEAD, Items.CROSSBOW,
		Items.IRON_CHESTPLATE, Items.IRON_BOOTS, Items.STICKY_PISTON, Items.CAULDRON, Items.HAY_BLOCK, Items.BELL,
		Items.DIAMOND_AXE, Items.GOLDEN_PICKAXE, Items.EMERALD_BLOCK, Items.ANVIL, Items.GRINDSTONE, Items.TNT_MINECART,
		Items.BLUE_ICE, Items.MOSS_BLOCK, Items.GLOW_BERRIES, Items.POINTED_DRIPSTONE);

	/** Bu oyunun dunyasinda bulunan biyom/yapi bilgisi (ipuclari ve biyom paneli icin). */
	private Knowledge.Facts facts;

	public Knowledge.Facts facts() { return facts; }

	private final Item[] card = new Item[25];
	/** Esya -> tahmini maliyet (bu seed'de ne kadar ugrasla elde edilir). */
	private Map<Item, Double> costs = Map.of();
	/** Gorev kareleri: kart hucresi bir ikon esya ile temsil edilir (esya olarak sayilmaz). */
	private final Map<Item, BingoTasks.Task> taskOf = new HashMap<>();
	private List<BingoTasks.Task> taskList = new ArrayList<>();
	private final Map<Item, GameTeam> claimed = new HashMap<>();
	private int goal, limit, needLines;

	/** Takim basina hedef secilen (isaretlenen) esyalar: kagittan secilir, yan panelde gorunur. */
	private final Map<GameTeam, Set<Item>> targets = new HashMap<>();
	private Map<Item, String> hints = Map.of();
	private final Map<GameTeam, net.minecraft.world.scores.Objective> boards = new HashMap<>();
	private final Map<GameTeam, List<String>> boardLines = new HashMap<>();

	/** 5 satir + 5 sutun + 2 capraz = 12 tombala cizgisi (kart indeksleri). */
	private static final int[][] LINES = buildLines();

	private static int[][] buildLines() {
		int[][] r = new int[12][5];
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				r[i][j] = i * 5 + j;          // satir i
				r[5 + i][j] = j * 5 + i;      // sutun i
			}
		}
		for (int j = 0; j < 5; j++) {
			r[10][j] = j * 6;                 // sol ustten sag alta capraz
			r[11][j] = (j + 1) * 4;           // sag ustten sol alta capraz
		}
		return r;
	}

	/** t takiminin tamamladigi cizgi sayisi. */
	private int lines(GameTeam t) {
		int n = 0;
		for (int[] l : LINES) {
			boolean all = true;
			for (int i : l) if (claimed.get(card[i]) != t) { all = false; break; }
			if (all) n++;
		}
		return n;
	}

	/** t takiminin hala tamamlayabilecegi cizgi sayisi (tamamlananlar dahil): icinde baska takimin esyasi olmayanlar. */
	private int possibleLines(GameTeam t) {
		int n = 0;
		for (int[] l : LINES) {
			boolean ok = true;
			for (int i : l) {
				GameTeam c = claimed.get(card[i]);
				if (c != null && c != t) { ok = false; break; }
			}
			if (ok) n++;
		}
		return n;
	}

	/** Karsilastirma: once cizgi, sonra esya. */
	private int rankValue(GameTeam t) {
		return lines(t) * 100 + score(t);
	}

	public BingoGame(GameManager mgr) {
		super(mgr);
	}

	@Override
	public String id() { return "BINGO"; }

	@Override
	public String title() { return "Tombala"; }

	@Override
	protected GameWorld.Options worldOptions() {
		return new GameWorld.Options(true, true, true, true);
	}

	@Override
	protected double borderRadius() { return st.num("BINGO", "borderStart"); }

	@Override
	protected boolean eliminationMode() { return false; }

	private static final String[] BANNED = {"spawn_egg", "command_block", "structure_", "jigsaw", "debug_stick", "barrier", "bedrock", "end_portal",
		"reinforced_deepslate", "knowledge_book", "written_book", "filled_map", "tipped_arrow", "enchanted_book", "potion", "music_disc", "smithing_template",
		"banner_pattern", "pottery_sherd", "trim", "firework", "sherd", "spawner", "trial_", "vault", "farmland", "dirt_path", "frogspawn", "petrified",
		"infested", "budding_amethyst", "player_head", "_head", "_skull", "_nugget", "enchanted_golden_apple", "totem", "elytra", "creaking", "heavy_core",
		"ominous", "wind_charge", "mace", "breeze", "bundle", "recovery_compass", "echo_shard", "disc_fragment", "goat_horn", "_planks", "_log", "_wood",
		"stripped_", "light", "chorus", "_spawn", "sniffer", "armadillo", "turtle_scute", "shulker", "end_crystal", "dragon", "beacon", "netherite", "nether_star", "conduit"};

	/** Klasik, taninan esyalar: kartta sadece bunlar cikar (zorluk maliyet modelinden gelir). */
	private static final String[] CLASSIC = {"stone_pickaxe", "stone_axe", "stone_sword", "stone_shovel", "stone_hoe", "iron_pickaxe", "iron_axe", "iron_sword", "iron_shovel", "iron_hoe", "golden_pickaxe", "golden_sword", "golden_axe", "diamond_pickaxe", "diamond_sword", "diamond_axe", "diamond_shovel", "diamond_hoe", "shears", "flint_and_steel", "fishing_rod", "bow", "crossbow", "arrow", "shield", "bucket", "compass", "clock", "spyglass", "lead", "name_tag", "saddle", "carrot_on_a_stick", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "golden_helmet", "golden_boots", "diamond_helmet", "diamond_boots", "furnace", "blast_furnace", "smoker", "crafting_table", "chest", "barrel", "trapped_chest", "bookshelf", "jukebox", "note_block", "piston", "sticky_piston", "observer", "hopper", "dropper", "dispenser", "tnt", "lever", "redstone_torch", "repeater", "comparator", "daylight_detector", "target", "lectern", "loom", "cartography_table", "fletching_table", "smithing_table", "grindstone", "stonecutter", "anvil", "cauldron", "composter", "lantern", "campfire", "torch", "ladder", "rail", "powered_rail", "detector_rail", "activator_rail", "minecart", "hopper_minecart", "chest_minecart", "tnt_minecart", "furnace_minecart", "oak_boat", "brewing_stand", "glass", "glass_bottle", "glass_pane", "iron_bars", "iron_door", "iron_trapdoor", "heavy_weighted_pressure_plate", "light_weighted_pressure_plate", "stone_pressure_plate", "tripwire_hook", "bell", "lightning_rod", "chain", "scaffolding", "decorated_pot", "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block", "coal_block", "copper_block", "hay_block", "bone_block", "slime_block", "sponge", "packed_ice", "blue_ice", "ice", "snow_block", "clay", "bricks", "stone_bricks", "mossy_stone_bricks", "mossy_cobblestone", "cobbled_deepslate", "polished_andesite", "polished_diorite", "polished_granite", "smooth_stone", "sandstone", "red_sandstone", "terracotta", "white_wool", "white_carpet", "white_bed", "white_banner", "white_concrete", "white_stained_glass", "white_terracotta", "cut_copper", "moss_block", "dripstone_block", "pointed_dripstone", "amethyst_block", "calcite", "tuff", "bread", "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_salmon", "cooked_cod", "baked_potato", "golden_apple", "golden_carrot", "apple", "carrot", "potato", "melon_slice", "sweet_berries", "glow_berries", "pumpkin_pie", "cake", "cookie", "mushroom_stew", "rabbit_stew", "beetroot_soup", "suspicious_stew", "dried_kelp", "leather", "feather", "string", "bone", "bone_meal", "gunpowder", "slime_ball", "spider_eye", "ender_pearl", "ink_sac", "glow_ink_sac", "rotten_flesh", "egg", "milk_bucket", "water_bucket", "lava_bucket", "powder_snow_bucket", "paper", "book", "map", "sugar", "sugar_cane", "bamboo", "kelp", "cactus", "lily_pad", "vine", "cocoa_beans", "pumpkin", "melon", "wheat", "flint", "clay_ball", "brick", "gold_ingot", "iron_ingot", "copper_ingot", "diamond", "emerald", "lapis_lazuli", "redstone", "coal", "charcoal", "amethyst_shard", "nautilus_shell", "prismarine_shard", "prismarine_crystals", "sea_pickle", "heart_of_the_sea", "sunflower", "painting", "item_frame", "glow_item_frame", "flower_pot", "armor_stand", "oak_sign", "oak_door", "oak_fence", "oak_stairs", "oak_trapdoor", "spruce_boat", "white_dye", "red_dye", "green_dye", "blue_dye", "black_dye"};
	private static Set<Item> classicSet;

	private static Set<Item> classic() {
		if (classicSet == null) {
			Set<Item> r = new HashSet<>();
			for (String id : CLASSIC) net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse("minecraft:" + id)).ifPresent(r::add);
			classicSet = r;
		}
		return classicSet;
	}

	private static boolean cardworthy(Item it) {
		if (!classic().contains(it)) return false;
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).getPath();
		for (String b : BANNED) if (id.contains(b)) return false;
		return it != Items.AIR;
	}

	private static final String[] COLORS = {"light_blue_", "light_gray_", "dark_oak_", "white_", "orange_", "magenta_", "yellow_", "lime_", "pink_", "gray_",
		"cyan_", "purple_", "blue_", "brown_", "green_", "red_", "black_", "oak_", "spruce_", "birch_", "jungle_", "acacia_", "mangrove_", "cherry_", "bamboo_",
		"crimson_", "warped_"};

	/** Renk/agac cesidi farkini siler: ayni ailenin birden cok uyesi kartta olmasin. */
	private static String familyKey(Item it) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).getPath();
		for (String c : COLORS) if (id.startsWith(c)) return id.substring(c.length());
		return id;
	}

	private static String typeKey(Item it) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).getPath();
		if (id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel") || id.endsWith("_hoe") || id.endsWith("_sword")) return "tool:3";
		if (id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings") || id.endsWith("_boots")) return "armor:3";
		if (id.endsWith("_stairs") || id.endsWith("_slab") || id.endsWith("_wall") || id.endsWith("_fence") || id.endsWith("_fence_gate") || id.endsWith("_door")
			|| id.endsWith("_trapdoor") || id.endsWith("_button") || id.endsWith("_pressure_plate")) return "variant:3";
		if (id.endsWith("_boat") || id.endsWith("_sign") || id.endsWith("_hanging_sign")) return "wood:2";
		if (id.contains("_bucket")) return "bucket:2";
		if (id.contains("minecart") || id.contains("rail")) return "rail:2";
		if (id.endsWith("_dye")) return "dye:1";
		if (it.components().has(DataComponents.FOOD)) return "food:5";
		return null;
	}

	private int fillTier(List<Item> src, int want, List<Item> out, Set<String> fam, Map<String, Integer> types, boolean caps) {
		int got = 0;
		for (Item it : new ArrayList<>(src)) {
			if (got >= want) break;
			if (out.contains(it)) continue;
			String fk = familyKey(it);
			if (fam.contains(fk)) continue;
			String tk = typeKey(it);
			if (caps && tk != null) {
				int cap = Integer.parseInt(tk.substring(tk.indexOf(':') + 1));
				if (types.getOrDefault(tk, 0) >= cap) continue;
			}
			fam.add(fk);
			if (tk != null) types.merge(tk, 1, Integer::sum);
			out.add(it);
			src.remove(it);
			got++;
		}
		return got;
	}

	private void makeCard() {
		facts = Knowledge.scan(world.level, world.centerX, world.centerZ, (int) borderRadius());
		costs = ItemCost.compute(server, facts);
		int diff = Math.max(1, Math.min(3, (int) st.num("BINGO", "difficulty")));
		taskOf.clear();
		for (BingoTasks.Task t : BingoTasks.pick(facts, costs, (int) st.num("BINGO", "tasks"), rnd)) taskOf.putIfAbsent(t.icon(), t);
		taskList = new ArrayList<>(taskOf.values());
		int need = 25 - taskList.size();
		double cap = diff == 3 ? 28 : 22;
		List<Map.Entry<Item, Double>> cand = new ArrayList<>();
		for (var en : costs.entrySet()) {
			double cst = en.getValue();
			if (cst < 2.0 || cst > cap || !cardworthy(en.getKey()) || taskOf.containsKey(en.getKey())) continue;
			cand.add(en);
		}
		cand.sort((x, y) -> Double.compare(x.getValue(), y.getValue()));
		// Zorluk dilimleri bu seed'in kendi dagilimina gore (yuzdelik): ne kadar uzak/zorsa o kadar yukari kayar
		double q1 = diff == 1 ? 0.40 : diff == 3 ? 0.60 : 0.50, q2 = diff == 1 ? 0.75 : diff == 3 ? 0.88 : 0.82;
		int i1 = (int) (cand.size() * q1), i2 = (int) (cand.size() * q2);
		List<Item> e = new ArrayList<>(), m = new ArrayList<>(), h = new ArrayList<>();
		for (int i = 0; i < cand.size(); i++) (i < i1 ? e : i < i2 ? m : h).add(cand.get(i).getKey());
		Kapisma.LOG.info("Tombala havuzu (zorluk {}): kolay {} orta {} zor {} | gorev {} | biyom {} | yapilar {}", diff, e.size(), m.size(), h.size(),
			taskList.size(), facts.biomes.size(), facts.structures);
		Comparator<Item> byId = Comparator.comparing(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString());
		e.sort(byId); m.sort(byId); h.sort(byId);
		Collections.shuffle(e, rnd); Collections.shuffle(m, rnd); Collections.shuffle(h, rnd);
		int[] want = diff == 1 ? new int[] {12, 9, 4} : diff == 3 ? new int[] {5, 10, 10} : new int[] {8, 10, 7};
		int tot = want[0] + want[1] + want[2];
		for (int i = 0; i < 3; i++) want[i] = (int) Math.round(want[i] * need / (double) tot);
		want[1] += need - (want[0] + want[1] + want[2]);

		List<Item> all = new ArrayList<>();
		Set<String> fam = new HashSet<>();
		Map<String, Integer> types = new HashMap<>();
		List<List<Item>> tiers = List.of(e, m, h);
		for (int i = 0; i < 3; i++) fillTier(tiers.get(i), want[i], all, fam, types, true);
		// eksikse komsu zorluklardan, sonra sinirlari gevsetip tamamla
		for (List<Item> t : List.of(m, e, h)) if (all.size() < need) fillTier(t, need - all.size(), all, fam, types, true);
		for (List<Item> t : List.of(m, e, h)) if (all.size() < need) fillTier(t, need - all.size(), all, fam, types, false);
		Collections.shuffle(all, rnd);
		List<Item> cells = new ArrayList<>(all);
		cells.addAll(taskOf.keySet());
		Collections.shuffle(cells, rnd);
		for (int i = 0; i < 25 && i < cells.size(); i++) card[i] = cells.get(i);
		double sum = 0;
		for (Item it : card) if (it != null) sum += taskOf.containsKey(it) ? taskOf.get(it).cost() : costs.getOrDefault(it, 8.0);
		Kapisma.LOG.info("Tombala karti: {} eşya + {} görev, ortalama zorluk {}", all.size(), taskList.size(), String.format("%.1f", sum / 25.0));
		StringBuilder cl = new StringBuilder();
		for (Item it : card) if (it != null) cl.append(taskOf.containsKey(it) ? "GOREV:" + taskOf.get(it).title() : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).getPath()).append('=').append(String.format("%.1f", taskOf.containsKey(it) ? taskOf.get(it).cost() : costs.getOrDefault(it, 0.0))).append(' ');
		Kapisma.LOG.info("Kart icerigi: {}", cl);
	}

	/** Hucrenin gorunen adi: gorevse gorev basligi, degilse esya adi. */
	private Component cellName(Item it) {
		var t = taskOf.get(it);
		return t != null ? Component.literal("Görev: " + t.title()) : new ItemStack(it).getHoverName();
	}

	public List<Item> cardItems() {
		return List.of(card);
	}

	/** Test/yonetici: karttaki ilk n esyayi oyuncuya verir. */
	public int debugGive(ServerPlayer p, int n) {
		int given = 0;
		for (int i = 0; i < Math.min(n, 25); i++) {
			if (card[i] != null) { p.getInventory().add(new ItemStack(card[i])); given++; }
		}
		return given;
	}

	private ItemStack cardItem() {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Bingo Kartı").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.AQUA).withBold(true)));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(CARD_TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	@Override
	protected boolean minimapEnabled() { return st.flag("BINGO", "minimap"); }

	@Override
	protected void onStart() {
		if (minimapEnabled()) giveWorldMap();
		goal = (int) st.num("BINGO", "goal");
		needLines = (int) st.num("BINGO", "lines");
		limit = (int) st.num("BINGO", "timeLimitMin") * 1200;
		makeCard();
		Map<Item, String> hh = new HashMap<>(st.flag("BINGO", "hints") ? ItemHints.build(server, card, facts) : Map.of());
		for (var en : taskOf.entrySet()) hh.remove(en.getKey());
		hints = hh;
		for (ServerPlayer p : onlinePlayers()) BingoTasks.reset(server, p, taskList);
		setupBoards();
		for (ServerPlayer p : onlinePlayers()) {
			p.getInventory().setItem(8, cardItem());
		}
		StringBuilder sb = new StringBuilder("Tombala başladı! Hedef: " + needLines + " çizgi (satır/sütun/çapraz)"
			+ (goal > 0 ? " veya " + goal + " eşya" : "") + ". Lockout: eşyayı ilk alan takım sayar." + (taskList.isEmpty() ? "" : " Kartta " + taskList.size() + " görev karesi var (ilerleme tamamla).") + " Kart: kâğıda sağ tık veya /kart");
		Msg.broadcast(server, sb.toString());
	}

	@Override
	protected void onRunningTick() {
		if (ticks % 20 != 0) return;
		scan();
		updateBoards();
		if (phase != Phase.RUNNING) return;
		if (limit > 0 && ticks >= limit) {
			GameTeam lead = leader();
			boolean tie = lead != null && teams.stream().filter(t -> t != lead).anyMatch(t -> rankValue(t) == rankValue(lead));
			finish(tie ? null : lead, "Süre doldu");
			return;
		}
		hud();
	}

	private int score(GameTeam t) {
		int n = 0;
		for (GameTeam c : claimed.values()) if (c == t) n++;
		return n;
	}

	private GameTeam leader() {
		GameTeam best = null;
		for (GameTeam t : teams) if (best == null || rankValue(t) > rankValue(best)) best = t;
		return best;
	}

	private void scan() {
		for (ServerPlayer p : onlinePlayers()) {
			if (p.isSpectator()) continue;
			GameTeam t = teamOf(p.getUUID());
			if (t == null) continue;
			Set<Item> have = new HashSet<>();
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty()) have.add(s.getItem());
			}
			for (Item it : card) {
				if (!claimed.containsKey(it) && (taskOf.containsKey(it) ? BingoTasks.done(server, p, taskOf.get(it).adv()) : have.contains(it))) {
					claimed.put(it, t);
					Msg.broadcast(server, Msg.prefix().append(t.display()).append(Component.literal(taskOf.containsKey(it) ? " tamamladı: " : " aldı: ").withStyle(ChatFormatting.YELLOW))
						.append(cellName(it)).append(Component.literal("  (" + score(t) + " eşya, " + lines(t) + "/" + needLines + " çizgi)").withStyle(ChatFormatting.GRAY)));
					for (UUID id : t.members) {
						ServerPlayer m = player(id);
						if (m != null) {
							Msg.title(m, "+1", cellName(it).getString(), 0, 25, 10);
							Msg.sound(m, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
						}
					}
					if (checkWin()) return;
				}
			}
		}
	}

	/** Yeterli cizgiye / hedefe ulasan veya kimse cizgi tamamlayamaz hale gelmisse oyunu bitirir. */
	private boolean checkWin() {
		for (GameTeam t : teams) {
			int l = lines(t);
			if (l >= needLines) {
				finish(t, l + " tombala (çizgi) tamamladı");
				return true;
			}
		}
		if (goal > 0) {
			GameTeam lead = leader();
			if (lead != null && score(lead) >= goal) {
				finish(lead, score(lead) + " eşyaya ulaştı");
				return true;
			}
		}
		boolean anyoneCan = false;
		for (GameTeam t : teams) if (possibleLines(t) >= needLines) { anyoneCan = true; break; }
		if (!anyoneCan && goal == 0) {
			GameTeam lead = leader();
			boolean tie = lead != null && teams.stream().filter(t -> t != lead).anyMatch(t -> rankValue(t) == rankValue(lead));
			finish(tie ? null : lead, "Kimse " + needLines + " çizgi tamamlayamaz");
			return true;
		}
		return false;
	}

	private void hud() {
		StringBuilder sb = new StringBuilder();
		for (GameTeam t : teams) sb.append(t.name).append(' ').append(lines(t)).append("/").append(needLines).append("ç ").append(score(t)).append("e  ");
		Component c = Component.literal(sb + "| " + Msg.mmss(limit > 0 ? (limit - ticks) / 20 : ticks / 20) + (limit > 0 ? " kaldı" : ""))
			.withStyle(ChatFormatting.WHITE);
		boolean bi = st.flag("BINGO", "biomeInfo");
		for (ServerPlayer p : onlinePlayers()) {
			Component line = c;
			if (bi && !p.isSpectator()) {
				line = Component.literal("").append(c).append(Component.literal("  ⌖ " + biomeAt(p)).withStyle(ChatFormatting.AQUA));
			}
			Msg.actionbar(p, line);
		}
	}

	/** Oyuncunun bulundugu biyomun Turkce adi. */
	static String biomeAt(ServerPlayer p) {
		return p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> ItemHints.biomeName(k.location().getPath())).orElse("?");
	}

	/** Bolgede hangi biyom ve yapilar var (koordinat yok): ne arayacagini planlamak icin. */
	private void openRegionInfo(ServerPlayer p) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Bölge bilgisi (±" + (int) borderRadius() + " blok)"));
		for (int i = 0; i < 27; i++) gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" ")).hideTooltip());
		GuiElementBuilder bio = new GuiElementBuilder(Items.GRASS_BLOCK).setName(Component.literal("Bölgedeki biyomlar").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GREEN).withBold(true)));
		if (facts != null) {
			List<String> names = new ArrayList<>();
			for (String b : facts.biomes) names.add(ItemHints.biomeName(b));
			Collections.sort(names);
			for (String n : names) bio.addLoreLine(Component.literal("• " + n).withStyle(x -> x.withItalic(false).withColor(ChatFormatting.WHITE)));
		}
		gui.setSlot(11, bio);
		GuiElementBuilder str = new GuiElementBuilder(Items.CHEST).setName(Component.literal("Bölgedeki yapılar").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GOLD).withBold(true)));
		if (facts != null) {
			Set<String> seen = new java.util.TreeSet<>();
			for (String sId : facts.structures.keySet()) seen.add(Knowledge.structureName(sId));
			if (seen.isEmpty()) str.addLoreLine(Component.literal("Hiç yapı yok").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GRAY)));
			for (String n : seen) str.addLoreLine(Component.literal("• " + n).withStyle(x -> x.withItalic(false).withColor(ChatFormatting.WHITE)));
		}
		gui.setSlot(15, str);
		gui.setSlot(26, new GuiElementBuilder(Items.ARROW).setName(Component.literal("Geri").withStyle(x -> x.withItalic(false)))
			.setCallback((idx, type, action) -> openCard(p)));
		gui.open();
	}

	@Override
	protected List<String> summaryExtra() {
		List<String> r = new ArrayList<>();
		for (GameTeam t : teams) r.add(t.name + ": " + lines(t) + " çizgi, " + score(t) + " eşya");
		return r;
	}

	@Override
	public boolean allowDamage(LivingEntity e, DamageSource src, float amount) {
		if (!(e instanceof ServerPlayer target) || !contains(target.getUUID())) return true;
		if (phase == Phase.COUNTDOWN) return false;
		if (src.getEntity() instanceof ServerPlayer attacker && attacker != target && contains(attacker.getUUID())) {
			return st.flag("BINGO", "pvp");
		}
		return true;
	}

	@Override
	public void onUseItem(ServerPlayer p, ItemStack stack) {
		CustomData d = stack.get(DataComponents.CUSTOM_DATA);
		if (d != null && d.copyTag().getBoolean(CARD_TAG)) openCard(p);
	}

	public void openCard(ServerPlayer p) {
		if (phase != Phase.RUNNING && phase != Phase.ENDED) {
			Msg.tell(p, "Kart oyun başlayınca açılır.");
			return;
		}
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x5, p, false);
		buildCard(gui, p);
		gui.open();
	}

	private void buildCard(SimpleGui gui, ServerPlayer p) {
		GameTeam mine = teamOf(p.getUUID());
		Set<Item> myTargets = mine != null ? targets.getOrDefault(mine, Set.of()) : Set.of();
		gui.setTitle(Component.literal("Tombala  çizgi " + (mine != null ? lines(mine) : 0) + "/" + needLines + "  eşya " + (mine != null ? score(mine) : 0)));
		for (int i = 0; i < 45; i++) {
			gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" ")).hideTooltip());
		}
		for (int r = 0; r < 5; r++) {
			for (int c = 0; c < 5; c++) {
				Item it = card[r * 5 + c];
				GameTeam owner = claimed.get(it);
				boolean target = owner == null && myTargets.contains(it);
				String star = target ? "★ " : "";
				GuiElementBuilder b = new GuiElementBuilder(it)
					.setName(((net.minecraft.network.chat.MutableComponent) cellName(it).copy())
						.withStyle(x -> x.withItalic(false).withColor(owner != null ? owner.color : (target ? ChatFormatting.YELLOW : ChatFormatting.WHITE))))
					.addLoreLine(Component.literal(star + (owner != null ? "Alan: " + owner.name : target ? "HEDEFİN" : "Henüz alınmadı"))
						.withStyle(x -> x.withItalic(false).withColor(owner != null ? owner.color : (target ? ChatFormatting.YELLOW : ChatFormatting.GRAY))))
					.glow(owner != null || target);
				if (owner == null && hints.containsKey(it)) {
					b.addLoreLine(Component.literal("İpucu: " + hints.get(it)).withStyle(x -> x.withItalic(false).withColor(ChatFormatting.AQUA)));
				}
				if (owner == null && taskOf.containsKey(it)) {
					b.addLoreLine(Component.literal("Görev: " + taskOf.get(it).desc()).withStyle(x -> x.withItalic(false).withColor(ChatFormatting.AQUA)));
				}
				Double cs = taskOf.containsKey(it) ? Double.valueOf(taskOf.get(it).cost()) : costs.get(it);
				if (owner == null && cs != null) {
					b.addLoreLine(Component.literal("Zorluk: " + ItemCost.starText(cs)).withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GOLD)));
				}
				if (owner == null && mine != null) {
					b.addLoreLine(Component.literal("Tıkla: hedef seç / kaldır").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.DARK_GRAY)));
					b.setCallback((idx, type, action) -> {
						Set<Item> set = targets.computeIfAbsent(mine, k -> new java.util.LinkedHashSet<>());
						if (!set.remove(it)) set.add(it);
						Msg.sound(p, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.3f);
						updateBoards();
						buildCard(gui, p);
					});
				}
				gui.setSlot(r * 9 + c + 2, b);
			}
		}
		sideButtons(gui, p, mine);
	}

	/** Kart ekraninin yan dugmeleri: kombinasyonlar, hedefleri temizle. Kart gui'den cagrilir. */
	private void sideButtons(SimpleGui gui, ServerPlayer p, GameTeam mine) {
		if (mine == null) return;
		if (st.flag("BINGO", "hints")) {
			gui.setSlot(0, new GuiElementBuilder(Items.WRITABLE_BOOK)
				.setName(Component.literal("Kombinasyonlar").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GREEN).withBold(true)))
				.addLoreLine(Component.literal("Hangi çizgiye gitmen mantıklı?").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GRAY)))
				.addLoreLine(Component.literal("Tıkla: önerilen çizgileri gör").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GRAY)))
				.setCallback((idx, type, action) -> openCombos(p)));
		}
		if (st.flag("BINGO", "biomeInfo") && facts != null) {
			gui.setSlot(18, new GuiElementBuilder(Items.SPYGLASS)
				.setName(Component.literal("Bölge biyomları").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.AQUA).withBold(true)))
				.addLoreLine(Component.literal("Bu alanda hangi biyom/yapı var?").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GRAY)))
				.setCallback((idx, type, action) -> openRegionInfo(p)));
		}
		gui.setSlot(9, new GuiElementBuilder(Items.BARRIER)
			.setName(Component.literal("Hedefleri temizle").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.RED)))
			.setCallback((idx, type, action) -> {
				targets.remove(mine);
				updateBoards();
				buildCard(gui, p);
			}));
	}

	private static final String[] LINE_NAMES = {"Satır 1", "Satır 2", "Satır 3", "Satır 4", "Satır 5",
		"Sütun 1", "Sütun 2", "Sütun 3", "Sütun 4", "Sütun 5", "Çapraz \\", "Çapraz /"};

	/** Takima gore cizgi onerileri: eksigi az olan once, imkansizlar sonda. */
	public void openCombos(ServerPlayer p) {
		GameTeam mine = teamOf(p.getUUID());
		if (mine == null) return;
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, p, false);
		gui.setTitle(Component.literal("Kombinasyonlar: " + lines(mine) + "/" + needLines + " çizgi"));
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < LINES.length; i++) order.add(i);
		order.sort((x, y) -> {
			int kx = lineState(mine, x), ky = lineState(mine, y);
			if (kx != ky) return Integer.compare(kx, ky);
			return Integer.compare(missing(mine, x), missing(mine, y));
		});
		for (int pos = 0; pos < order.size() && pos < 27; pos++) {
			int li = order.get(pos);
			boolean impossible = lineState(mine, li) == 2;
			boolean done = missing(mine, li) == 0 && !impossible;
			GuiElementBuilder b = new GuiElementBuilder(impossible ? Items.BARRIER : done ? Items.LIME_DYE : Items.PAPER)
				.setName(Component.literal(LINE_NAMES[li] + (impossible ? "  (imkânsız)" : done ? "  (tamam)" : "  eksik " + missing(mine, li)))
					.withStyle(x -> x.withItalic(false).withColor(impossible ? ChatFormatting.RED : done ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
			for (int i : LINES[li]) {
				Item it = card[i];
				GameTeam owner = claimed.get(it);
				String mark = owner == mine ? "✔ " : owner != null ? "✘ " : "○ ";
				ChatFormatting col = owner == mine ? ChatFormatting.GREEN : owner != null ? ChatFormatting.RED : ChatFormatting.WHITE;
				String extra = owner == null && hints.containsKey(it) ? " — " + hints.get(it) : owner != null && owner != mine ? " — " + owner.name : "";
				b.addLoreLine(Component.literal(mark + ItemHints.name(it) + extra).withStyle(x -> x.withItalic(false).withColor(col)));
			}
			if (!impossible && !done) {
				b.addLoreLine(Component.literal("Tıkla: eksikleri hedef yap").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.DARK_GRAY)));
				b.setCallback((idx, type, action) -> {
					Set<Item> set = targets.computeIfAbsent(mine, k -> new java.util.LinkedHashSet<>());
					for (int i : LINES[li]) if (claimed.get(card[i]) == null) set.add(card[i]);
					Msg.tell(p, LINE_NAMES[li] + " için eksikler hedef yapıldı.");
					updateBoards();
					openCard(p);
				});
			}
			gui.setSlot(pos, b);
		}
		gui.setSlot(26, new GuiElementBuilder(Items.ARROW).setName(Component.literal("Geri").withStyle(x -> x.withItalic(false)))
			.setCallback((idx, type, action) -> openCard(p)));
		gui.open();
	}

	/** 0 = devam, 1 = tamam(eksik yok), 2 = imkansiz (icinde rakip esyasi var). */
	private int lineState(GameTeam t, int li) {
		boolean miss = false;
		for (int i : LINES[li]) {
			GameTeam c = claimed.get(card[i]);
			if (c != null && c != t) return 2;
			if (c == null) miss = true;
		}
		return miss ? 0 : 1;
	}

	private int missing(GameTeam t, int li) {
		int n = 0;
		for (int i : LINES[li]) if (claimed.get(card[i]) != t) n++;
		return n;
	}

	// ---------------- yan panel (scoreboard) ----------------
	private void setupBoards() {
		if (!st.flag("BINGO", "sidebar")) return;
		var sb = server.getScoreboard();
		for (GameTeam t : teams) {
			String name = "kpb_" + t.index;
			var old = sb.getObjective(name);
			if (old != null) sb.removeObjective(old);
			var o = sb.addObjective(name, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
				Component.literal("TOMBALA").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false,
				net.minecraft.network.chat.numbers.BlankFormat.INSTANCE);
			var slot = net.minecraft.world.scores.DisplaySlot.teamColorToSlot(t.color);
			if (slot != null) sb.setDisplayObjective(slot, o);
			boards.put(t, o);
		}
		updateBoards();
	}

	private String itemName(Item it) {
		String n = cellName(it).getString();
		return n.length() > 22 ? n.substring(0, 22) : n;
	}

	private void updateBoards() {
		sendHud();
		var sb = server.getScoreboard();
		for (GameTeam t : teams) {
			var obj = boards.get(t);
			if (obj == null) continue;
			List<String> lines = new ArrayList<>();
			String timeStr = limit > 0 ? Msg.mmss((limit - ticks) / 20) + " kaldı" : Msg.mmss(ticks / 20);
			lines.add("§7Süre: §f" + timeStr);
			lines.add("§bÇizgi §f" + lines(t) + "/" + needLines + "  §bEşya §f" + score(t));
			List<GameTeam> rivals = new ArrayList<>(teams);
			rivals.remove(t);
			rivals.sort((x, y) -> Integer.compare(rankValue(y), rankValue(x)));
			for (int i = 0; i < rivals.size() && i < 3; i++) {
				GameTeam r = rivals.get(i);
				lines.add(r.color + r.name + " §f" + lines(r) + "ç " + score(r) + "e");
			}
			lines.add("§8────────────");
			Set<Item> mine = targets.getOrDefault(t, Set.of());
			if (mine.isEmpty()) {
				lines.add("§7Hedef yok: kâğıda sağ tık,");
				lines.add("§7eşyaya tıkla = hedef seç");
			} else {
				lines.add("§eHedeflerin:");
				int shown = 0;
				for (Item it : mine) {
					if (lines.size() >= 15) break;
					GameTeam owner = claimed.get(it);
					String mark = owner == t ? "§a✔ " : owner != null ? "§c✘ §m" : "§f○ ";
					lines.add(mark + itemName(it));
					shown++;
				}
			}
			// ayni satir metni olmasin diye benzersizlestir
			for (int i = 0; i < lines.size(); i++) {
				String l = lines.get(i);
				for (int k = 0; k < i; k++) if (lines.get(k).equals(l)) { l += "§r"; k = -1; }
				lines.set(i, l);
			}
			if (lines.equals(boardLines.get(t))) continue;
			List<String> old = boardLines.get(t);
			if (old != null) for (String l : old) sb.resetSinglePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(l), obj);
			for (int i = 0; i < lines.size(); i++) {
				sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(lines.get(i)), obj).set(lines.size() - i);
			}
			boardLines.put(t, lines);
		}
	}

	private static int rgb(ChatFormatting f) {
		Integer c = f.getColor();
		return c != null ? c : 0xFFFFFF;
	}

	/** Istemci modu (kapisma-hud) olan oyunculara sol ust karti gonderir. */
	private void sendHud() {
		for (GameTeam t : teams) {
			var p0 = new tr.kapisma.net.HudPayload();
			p0.active = true;
			p0.title = "TOMBALA";
			p0.teamName = t.name;
			p0.teamRgb = rgb(t.color);
			p0.lines = lines(t);
			p0.need = needLines;
			p0.items = score(t);
			p0.countdown = limit > 0;
			p0.timeSec = limit > 0 ? Math.max(0, (limit - ticks) / 20) : ticks / 20;
			Set<Item> tg = targets.getOrDefault(t, Set.of());
			for (Item it : card) {
				GameTeam o = claimed.get(it);
				p0.card.add(new tr.kapisma.net.HudPayload.Cell(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).toString(),
					o != null ? rgb(o.color) : -1, o == t, tg.contains(it)));
			}
			List<GameTeam> rivals = new ArrayList<>(teams);
			rivals.remove(t);
			rivals.sort((x, y) -> Integer.compare(rankValue(y), rankValue(x)));
			for (GameTeam r : rivals) p0.rivals.add(new tr.kapisma.net.HudPayload.Rival(r.name, rgb(r.color), lines(r), score(r)));
			for (java.util.UUID id : t.members) {
				ServerPlayer m = player(id);
				if (m != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(m, tr.kapisma.net.HudPayload.TYPE)) {
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(m, p0);
				}
			}
		}
	}

	@Override
	protected void onClose() {
		for (ServerPlayer m : onlinePlayers()) {
			if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(m, tr.kapisma.net.HudPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(m, tr.kapisma.net.HudPayload.off());
			}
		}
		var sb = server.getScoreboard();
		for (var o : boards.values()) sb.removeObjective(o);
		boards.clear();
	}
}
