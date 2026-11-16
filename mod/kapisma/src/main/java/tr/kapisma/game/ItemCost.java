package tr.kapisma.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Tombala zorluk modeli: bir esyanin BU seed'de (bolgede) ne kadar ugrasla elde edilecegini tahmin eder.
 * Birim "dakika benzeri caba"dir: yapi malzemesi kolay olabilir ama en yakin kaynagi uzaksa pahalilasir.
 * Maliyet = kaynak maliyeti (kazma/avlama/sandik) + yola cikma (en yakin biyom/yapi uzakligi / 220) + tarif zincirinin eklemesi.
 */
public final class ItemCost {
	private ItemCost() {}

	/** blok kirarak elde edilen esyalarin ek zorlugu (kazma seviyesi, derinlik, nadirlik). */
	private static final Map<String, Double> EXTRA = Map.ofEntries(
		Map.entry("minecraft:coal", 1.0), Map.entry("minecraft:raw_copper", 2.0), Map.entry("minecraft:raw_iron", 2.5),
		Map.entry("minecraft:raw_gold", 5.5), Map.entry("minecraft:redstone", 4.5), Map.entry("minecraft:lapis_lazuli", 5.5),
		Map.entry("minecraft:diamond", 8.5), Map.entry("minecraft:emerald", 10.0), Map.entry("minecraft:amethyst_shard", 8.0),
		Map.entry("minecraft:flint", 1.0), Map.entry("minecraft:clay_ball", 1.4), Map.entry("minecraft:glow_berries", 3.0),
		Map.entry("minecraft:obsidian", 8.0), Map.entry("minecraft:packed_ice", 5.0), Map.entry("minecraft:blue_ice", 9.0),
		Map.entry("minecraft:sponge", 8.0), Map.entry("minecraft:cobweb", 2.5), Map.entry("minecraft:moss_block", 2.0),
		Map.entry("minecraft:pointed_dripstone", 3.0), Map.entry("minecraft:calcite", 2.0), Map.entry("minecraft:sculk", 8.0));

	/** yaratik ek zorlugu (avlama + dusme). */
	private static final Map<String, Double> MOB = Map.ofEntries(
		Map.entry("cow", 1.0), Map.entry("pig", 1.0), Map.entry("sheep", 1.0), Map.entry("chicken", 1.0), Map.entry("rabbit", 2.0),
		Map.entry("zombie", 1.5), Map.entry("skeleton", 2.5), Map.entry("spider", 2.0), Map.entry("creeper", 2.5), Map.entry("enderman", 6.0),
		Map.entry("witch", 8.0), Map.entry("slime", 6.0), Map.entry("squid", 3.5), Map.entry("glow_squid", 6.0), Map.entry("drowned", 3.0),
		Map.entry("cod", 2.5), Map.entry("salmon", 2.5), Map.entry("fox", 3.0), Map.entry("wolf", 3.0), Map.entry("llama", 5.0),
		Map.entry("panda", 8.0), Map.entry("parrot", 5.0), Map.entry("ocelot", 6.0), Map.entry("turtle", 6.0), Map.entry("guardian", 12.0),
		Map.entry("bat", 3.0), Map.entry("goat", 4.0), Map.entry("polar_bear", 7.0), Map.entry("horse", 4.0), Map.entry("donkey", 4.0));

	/** yapi turu baz zorlugu (bulma + cikarma). */
	private static final Map<String, Double> STRUCT = Map.ofEntries(
		Map.entry("village", 2.0), Map.entry("mineshaft", 3.5), Map.entry("desert_pyramid", 5.0), Map.entry("jungle_pyramid", 6.0),
		Map.entry("igloo", 3.0), Map.entry("shipwreck", 5.0), Map.entry("buried_treasure", 9.0), Map.entry("ruined_portal", 3.5),
		Map.entry("pillager_outpost", 7.0), Map.entry("ancient_city", 18.0), Map.entry("trial_chambers", 14.0),
		Map.entry("ocean_monument", 16.0), Map.entry("mansion", 22.0), Map.entry("swamp_hut", 4.0), Map.entry("trail_ruins", 8.0));

	/** Sandik ganimetinde nadir olan esyalar: sans payi (dogrudan kazmak/uretmek daha guvenilir). */
	private static final Map<String, Double> LOOT_RARE = Map.ofEntries(
		Map.entry("minecraft:diamond", 12.0), Map.entry("minecraft:emerald", 8.0), Map.entry("minecraft:iron_ingot", 2.0),
		Map.entry("minecraft:golden_apple", 5.0), Map.entry("minecraft:enchanted_golden_apple", 40.0), Map.entry("minecraft:diamond_pickaxe", 14.0),
		Map.entry("minecraft:diamond_sword", 14.0), Map.entry("minecraft:diamond_axe", 14.0), Map.entry("minecraft:diamond_chestplate", 20.0),
		Map.entry("minecraft:name_tag", 4.0), Map.entry("minecraft:saddle", 4.0), Map.entry("minecraft:iron_pickaxe", 3.0), Map.entry("minecraft:iron_sword", 3.0),
		Map.entry("minecraft:iron_chestplate", 4.0), Map.entry("minecraft:iron_helmet", 4.0), Map.entry("minecraft:iron_leggings", 4.0), Map.entry("minecraft:iron_boots", 4.0));

	/** Yaratik ganimetinde cok nadir dusenler. */
	private static final Map<String, Double> MOB_RARE = Map.ofEntries(
		Map.entry("minecraft:iron_ingot", 40.0), Map.entry("minecraft:carrot", 5.0), Map.entry("minecraft:potato", 5.0), Map.entry("minecraft:emerald", 25.0),
		Map.entry("minecraft:poppy", 6.0), Map.entry("minecraft:totem_of_undying", 30.0), Map.entry("minecraft:iron_nugget", 10.0), Map.entry("minecraft:gold_ingot", 25.0));

	private static double travel(double dist) {
		return dist / 220.0;
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
	}

	private static boolean relax(Map<Item, Double> c, Item it, double v) {
		if (it == null || it == Items.AIR) return false;
		Double old = c.get(it);
		if (old == null || v < old - 1e-9) { c.put(it, v); return true; }
		return false;
	}

	private static void relaxBlock(Map<Item, Double> c, JsonObject bd, String blockId, double base, boolean ore) {
		// cevher bloklari kendi esyasini ancak ipeksi dokunusla verir: gercekci degil, sadece dusenleri say
		double self = base + 0.6 + (ore ? 12.0 : 0.0);
		var b = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId));
		if (b.isPresent()) relax(c, b.get().asItem(), self);
		relax(c, item(blockId), self);
		JsonElement drops = bd.get(blockId);
		if (drops != null && drops.isJsonArray()) {
			for (JsonElement e : drops.getAsJsonArray()) {
				String id = e.getAsString();
				// cevherin kendi blogu "dusen" listesinde de gecer (ipeksi dokunus): pahali say
				relax(c, item(id), base + 0.6 + EXTRA.getOrDefault(id, 0.8) + (ore && id.equals(blockId) ? 12.0 : 0.0));
			}
		}
	}

	/** Bolgedeki en yakin yapi (tur) uzakligi; yoksa -1. */
	private static double nearest(Knowledge.Facts f, String type) {
		double best = -1;
		for (var p : f.places) {
			if (!p.type().equals(type)) continue;
			double d = Math.hypot(p.x() - f.cx, p.z() - f.cz);
			if (best < 0 || d < best) best = d;
		}
		return best;
	}

	/** Esya -> tahmini maliyet (sadece bolgede elde edilebilenler). */
	public static Map<Item, Double> compute(MinecraftServer server, Knowledge.Facts f) {
		JsonObject d = Knowledge.data();
		JsonObject biomes = d.getAsJsonObject("biomes");
		JsonObject bd = d.getAsJsonObject("blockDrops");
		JsonObject md = d.getAsJsonObject("mobDrops");
		Map<Item, Double> c = new HashMap<>();

		for (JsonElement e : d.getAsJsonArray("general")) relaxBlock(c, bd, e.getAsString(), 0.0, e.getAsString().endsWith("_ore"));

		for (String bn : f.biomes) {
			JsonObject bj = biomes.getAsJsonObject(bn);
			if (bj == null) continue;
			double tv = travel(f.biomeDist.getOrDefault(bn, 0));
			for (JsonElement e : bj.getAsJsonArray("blocks")) {
				String id = "minecraft:" + e.getAsString();
				boolean ore = id.endsWith("_ore");
				relaxBlock(c, bd, id, ore ? tv * 0.5 : tv, ore);
			}
			for (JsonElement e : bj.getAsJsonArray("mobs")) {
				String mob = e.getAsString();
				JsonElement drops = md.get("minecraft:" + mob);
				if (drops == null || !drops.isJsonArray()) continue;
				double base = tv + MOB.getOrDefault(mob, 4.0) + 0.5;
				for (JsonElement dr : drops.getAsJsonArray()) relax(c, item(dr.getAsString()), base + MOB_RARE.getOrDefault(dr.getAsString(), 0.0));
			}
		}

		JsonObject sl = d.getAsJsonObject("structureLoot");
		JsonObject sb = d.getAsJsonObject("structureBlocks");
		for (String sid : f.structures.keySet()) {
			String type = Knowledge.structType(sid);
			double dist = nearest(f, type);
			double base = STRUCT.getOrDefault(type, 6.0) + travel(Math.max(0, dist));
			JsonElement loot = sl.get(sid);
			if (loot != null && loot.isJsonArray()) for (JsonElement e : loot.getAsJsonArray()) relax(c, item(e.getAsString()), base + 1.5 + LOOT_RARE.getOrDefault(e.getAsString(), 0.0));
			JsonElement blks = sb.get(sid);
			if (blks == null && sid.startsWith("minecraft:village_")) blks = sb.get("minecraft:village_plains");
			if (blks != null && blks.isJsonArray()) for (JsonElement e : blks.getAsJsonArray()) relaxBlock(c, bd, e.getAsString(), base, false);
		}
		JsonElement dun = sl.get("kapisma:dungeon");
		if (dun != null && dun.isJsonArray()) for (JsonElement e : dun.getAsJsonArray()) relax(c, item(e.getAsString()), 5.5 + LOOT_RARE.getOrDefault(e.getAsString(), 0.0));
		if (f.hasWater()) {
			double wd = 99999;
			for (String bn : f.biomes) if (bn.contains("ocean") || bn.contains("river") || bn.contains("swamp") || bn.contains("beach")) wd = Math.min(wd, f.biomeDist.getOrDefault(bn, 0));
			JsonElement fish = d.get("fishing");
			if (fish != null && fish.isJsonArray()) for (JsonElement e : fish.getAsJsonArray()) relax(c, item(e.getAsString()), 6.0 + travel(wd));
		}
		if (c.containsKey(Items.COBBLESTONE)) relax(c, Items.COBBLESTONE, 1.0);

		// tarif zinciri
		var access = server.registryAccess();
		List<RecipeHolder<?>> recipes = new ArrayList<>(server.getRecipeManager().getRecipes());
		boolean changed = true;
		int guard = 0;
		while (changed && guard++ < 60) {
			changed = false;
			Double bucket = c.get(Items.BUCKET);
			if (bucket != null) {
				changed |= relax(c, Items.WATER_BUCKET, bucket + 1.2);
				changed |= relax(c, Items.LAVA_BUCKET, bucket + 4.0);
				Double cow = null;
				for (String bn : f.biomes) {
					JsonObject bj = biomes.getAsJsonObject(bn);
					if (bj == null) continue;
					for (JsonElement e : bj.getAsJsonArray("mobs")) if (e.getAsString().equals("cow")) {
						double v = travel(f.biomeDist.getOrDefault(bn, 0)) + 1.5;
						if (cow == null || v < cow) cow = v;
					}
				}
				if (cow != null) changed |= relax(c, Items.MILK_BUCKET, bucket + cow);
			}
			Double ws = c.get(Items.WHEAT_SEEDS);
			if (ws != null) changed |= relax(c, Items.WHEAT, ws + 2.0);
			Double bs = c.get(Items.BEETROOT_SEEDS);
			if (bs != null) changed |= relax(c, Items.BEETROOT, bs + 2.0);
			Double ms = c.get(Items.MELON_SEEDS);
			if (ms != null) changed |= relax(c, Items.MELON_SLICE, ms + 2.0);
			Double ps = c.get(Items.PUMPKIN_SEEDS);
			if (ps != null) changed |= relax(c, Items.PUMPKIN, ps + 2.0);

			for (RecipeHolder<?> h : recipes) {
				Recipe<?> r = h.value();
				Item result = r.getResultItem(access).getItem();
				if (result == Items.AIR) continue;
				var ings = r.getIngredients();
				if (ings.isEmpty()) continue;
				Map<Item, Integer> cnt = new HashMap<>();
				boolean ok = true;
				for (Ingredient ing : ings) {
					if (ing.isEmpty()) continue;
					Item best = null;
					double bc = Double.MAX_VALUE;
					for (var st : ing.getItems()) {
						Double v = c.get(st.getItem());
						if (v != null && v < bc) { bc = v; best = st.getItem(); }
					}
					if (best == null) { ok = false; break; }
					cnt.merge(best, 1, Integer::sum);
				}
				if (!ok || cnt.isEmpty()) continue;
				double max = 0, sum = 0, extra = 0;
				for (var e : cnt.entrySet()) {
					double v = c.get(e.getKey());
					max = Math.max(max, v);
					sum += v;
					extra += v * 0.06 * (e.getValue() - 1);
				}
				double total = 0.6 + max + 0.45 * (sum - max) + extra;
				changed |= relax(c, result, total);
			}
		}
		return c;
	}

	/** 1-5 yildiz. */
	public static int stars(double cost) {
		return cost < 4 ? 1 : cost < 8 ? 2 : cost < 14 ? 3 : cost < 22 ? 4 : 5;
	}

	public static String starText(double cost) {
		int s = stars(cost);
		return "★".repeat(s) + "☆".repeat(5 - s) + " (~" + Math.max(2, (int) Math.round(cost)) + " dk)";
	}
}
