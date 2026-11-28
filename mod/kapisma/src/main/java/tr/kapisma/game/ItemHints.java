package tr.kapisma.game;

import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Kartta gösterilen eşya adları ve biyom çevirileri. */
public final class ItemHints {
	private ItemHints() {}

	public static String name(Item item) {
		return new ItemStack(item).getHoverName().getString();
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

}
