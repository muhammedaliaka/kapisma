package tr.kapisma.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;

/** Karttaki esyalar icin kisa "nasil elde edilir" ipuclari (tariflerden uretilir). */
public final class ItemHints {
	private ItemHints() {}

	/** Tarifi olmayan, dogadan alinan esyalar. */
	private static final Map<Item, String> RAW = Map.of(
		Items.DIAMOND, "Elmas madeni kaz (derinde, demir kazma)",
		Items.COAL, "Kömür madeni kaz (taş kazma yeter)",
		Items.REDSTONE_BLOCK, "Redstone cevheri kaz, 9 redstone ile craft",
		Items.LAPIS_BLOCK, "Lapis cevheri kaz, 9 lapis ile craft");

	public static String name(Item it) {
		return new ItemStack(it).getHoverName().getString();
	}

	private static String ingName(Ingredient ing) {
		ItemStack[] items = ing.getItems();
		if (items.length == 0) return null;
		String n = items[0].getHoverName().getString();
		return items.length > 3 ? n + " (herhangi)" : n;
	}

	public static Map<Item, String> build(MinecraftServer server, Item[] card, Knowledge.Facts facts) {
		Map<Item, String> out = new LinkedHashMap<>();
		var access = server.registryAccess();
		for (Item it : card) {
			String craft = null, smelt = null, other = null;
			for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
				Recipe<?> r = h.value();
				if (r.getResultItem(access).getItem() != it) continue;
				RecipeType<?> type = r.getType();
				if (type == RecipeType.CRAFTING && craft == null) {
					craft = craftText(r);
				} else if ((r instanceof SmeltingRecipe || r instanceof SmokingRecipe) && smelt == null) {
					var ing = r.getIngredients();
					if (!ing.isEmpty()) smelt = (r instanceof SmokingRecipe ? "Dumanlı fırında pişir: " : "Fırında pişir: ") + ingName(ing.get(0));
				} else if (r instanceof AbstractCookingRecipe && other == null) {
					var ing = r.getIngredients();
					if (!ing.isEmpty()) other = "Ocakta pişir: " + ingName(ing.get(0));
				}
			}
			String hint = craft != null ? craft : smelt != null ? smelt : other != null ? other : null;
			if (hint == null) hint = rawHint(it, facts, RAW.get(it));
			out.put(it, hint);
		}
		return out;
	}

	static final Map<String, String> BIOME_TR = new java.util.HashMap<>();
	static {
		String[] kv = {"plains", "ova", "sunflower_plains", "ayçiçeği ovası", "forest", "orman", "flower_forest", "çiçek ormanı", "birch_forest", "huş ormanı",
			"old_growth_birch_forest", "yaşlı huş ormanı", "dark_forest", "karanlık orman", "taiga", "tayga", "snowy_taiga", "karlı tayga",
			"old_growth_pine_taiga", "yaşlı çam taygası", "old_growth_spruce_taiga", "yaşlı ladin taygası", "jungle", "cungle",
			"sparse_jungle", "seyrek cungle", "bamboo_jungle", "bambu cunglesi", "savanna", "savan", "savanna_plateau", "savan platosu",
			"windswept_savanna", "rüzgârlı savan", "desert", "çöl", "badlands", "mesa", "eroded_badlands", "aşınmış mesa", "wooded_badlands", "ormanlı mesa",
			"swamp", "bataklık", "mangrove_swamp", "mangrov bataklığı", "river", "nehir", "beach", "kumsal", "snowy_beach", "karlı kumsal",
			"ocean", "okyanus", "deep_ocean", "derin okyanus", "cold_ocean", "soğuk okyanus", "deep_cold_ocean", "derin soğuk okyanus",
			"lukewarm_ocean", "ılık okyanus", "deep_lukewarm_ocean", "derin ılık okyanus", "warm_ocean", "sıcak okyanus", "frozen_ocean", "donmuş okyanus",
			"deep_frozen_ocean", "derin donmuş okyanus", "frozen_river", "donmuş nehir", "snowy_plains", "karlı ova", "ice_spikes", "buz dikenleri",
			"meadow", "çayır", "grove", "koru", "snowy_slopes", "karlı yamaç", "frozen_peaks", "donmuş zirve", "jagged_peaks", "sivri zirve",
			"stony_peaks", "taşlı zirve", "windswept_hills", "rüzgârlı tepe", "windswept_gravelly_hills", "rüzgârlı çakıllı tepe",
			"windswept_forest", "rüzgârlı orman", "stony_shore", "taşlı kıyı", "cherry_grove", "kiraz korusu", "mushroom_fields", "mantar adası",
			"lush_caves", "gür mağara", "dripstone_caves", "sarkıt mağarası", "deep_dark", "derin karanlık"};
		for (int i = 0; i + 1 < kv.length; i += 2) BIOME_TR.put(kv[i], kv[i + 1]);
	}

	public static String biomeName(String id) {
		return BIOME_TR.getOrDefault(id, id.replace('_', ' '));
	}

	private static String rawHint(Item it, Knowledge.Facts facts, String fallback) {
		if (facts != null) {
			var biomes = Knowledge.biomesFor(it, facts, 99);
			var structs = Knowledge.structuresFor(it, facts);
			List<String> parts = new ArrayList<>();
			if (!biomes.isEmpty() && biomes.size() <= 4) {
				parts.add("biyom: " + String.join(", ", biomes.stream().map(ItemHints::biomeName).toList()));
			}
			if (!structs.isEmpty()) parts.add("sandık/yapı: " + String.join(", ", structs.stream().map(Knowledge::structureName).distinct().limit(3).toList()));
			if (!parts.isEmpty()) return (fallback != null ? fallback : "Doğada bul") + " (" + String.join("; ", parts) + ")";
		}
		return fallback != null ? fallback : "Doğada bul ya da yaratıktan düşür";
	}

	private static String craftText(Recipe<?> r) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (Ingredient ing : r.getIngredients()) {
			String n = ingName(ing);
			if (n != null) counts.merge(n, 1, Integer::sum);
		}
		List<String> parts = new ArrayList<>();
		for (var e : counts.entrySet()) {
			if (parts.size() >= 3) { parts.add("…"); break; }
			parts.add(e.getValue() > 1 ? e.getValue() + "× " + e.getKey() : e.getKey());
		}
		return "Craft: " + String.join(" + ", parts);
	}
}
