package tr.kapisma.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Tombala "gorev kareleri": kartta esya yerine bir basari (ilerleme) tamamlama gorevi.
 * Sadece bu seed'de yapilabilir olanlar secilir (or. evcillestirme icin uygun hayvan, balik icin su, ticaret icin koy).
 * Oyun baslarken katilimcilarin bu ilerlemeleri sifirlanir; tamamlama takimin sayilir.
 */
public final class BingoTasks {
	private BingoTasks() {}

	public record Task(String adv, String title, String desc, Item icon, double cost) {}

	private static Set<String> mobs(Knowledge.Facts f) {
		Set<String> out = new java.util.HashSet<>();
		JsonObject biomes = Knowledge.data().getAsJsonObject("biomes");
		for (String b : f.biomes) {
			JsonObject bj = biomes.getAsJsonObject(b);
			if (bj != null) for (JsonElement e : bj.getAsJsonArray("mobs")) out.add(e.getAsString());
		}
		return out;
	}

	private static boolean blockIn(Knowledge.Facts f, String block) {
		JsonObject biomes = Knowledge.data().getAsJsonObject("biomes");
		for (String b : f.biomes) {
			JsonObject bj = biomes.getAsJsonObject(b);
			if (bj == null) continue;
			for (JsonElement e : bj.getAsJsonArray("blocks")) if (e.getAsString().equals(block)) return true;
		}
		return false;
	}

	private static double c(Map<Item, Double> costs, Item it) {
		Double v = costs.get(it);
		return v == null ? -1 : v;
	}

	/** Seed'de yapilabilir tum gorevler. */
	public static List<Task> feasible(Knowledge.Facts f, Map<Item, Double> costs) {
		List<Task> out = new ArrayList<>();
		Set<String> m = mobs(f);
		out.add(new Task("minecraft:adventure/kill_a_mob", "Canavar avla", "Bir düşman canavar öldür (zombi, iskelet, örümcek...).", Items.IRON_SWORD, 3));
		out.add(new Task("minecraft:husbandry/plant_seed", "Tohum ek", "Bir tohum ya da fidan ekip büyüt: tohumu toprağa ek.", Items.WHEAT_SEEDS, 3));
		double bow = c(costs, Items.BOW), arrow = c(costs, Items.ARROW);
		if (bow > 0 && arrow > 0) out.add(new Task("minecraft:adventure/shoot_arrow", "Ok at", "Yay yap ve bir ok at.", Items.BOW, bow + arrow + 1));
		double bed = c(costs, Items.RED_BED);
		if (bed > 0) out.add(new Task("minecraft:adventure/sleep_in_bed", "Yatakta uyu", "Yatak yap ve geceyi (ya da fırtınayı) beklerken yatakta uyu.", Items.RED_BED, bed + 5));
		boolean breedable = m.contains("cow") || m.contains("pig") || m.contains("sheep") || m.contains("chicken");
		double seeds = c(costs, Items.WHEAT_SEEDS);
		if (breedable && seeds > 0) out.add(new Task("minecraft:husbandry/breed_an_animal", "Hayvan üret", "İki hayvanı yemle sevip yavru yaptır (inek/koyun: buğday, domuz: havuç/patates, tavuk: tohum).", Items.WHEAT, 6 + seeds));
		boolean tameable = m.contains("wolf") || m.contains("cat") || m.contains("parrot") || m.contains("ocelot") || m.contains("horse") || m.contains("donkey");
		if (tameable) out.add(new Task("minecraft:husbandry/tame_an_animal", "Hayvan evcilleştir", "Bir kurt (kemik), papağan (tohum), at/eşek (üstüne binip) ya da kedi evcilleştir.", Items.BONE, 9));
		double rod = c(costs, Items.FISHING_ROD);
		if (f.hasWater() && rod > 0) out.add(new Task("minecraft:husbandry/fishy_business", "Balık tut", "Olta yap ve bir balık tut.", Items.FISHING_ROD, rod + 4));
		double bucket = c(costs, Items.BUCKET);
		boolean fishMob = m.contains("cod") || m.contains("salmon") || m.contains("pufferfish") || m.contains("tropical_fish");
		if (fishMob && bucket > 0) out.add(new Task("minecraft:husbandry/tactical_fishing", "Kovayla balık tut", "Su kovasıyla bir balığa sağ tıkla: balığı kovada yakala.", Items.COD_BUCKET, bucket + 7));
		boolean village = f.structures.keySet().stream().anyMatch(s -> s.startsWith("minecraft:village"));
		if (village) {
			double vd = 0;
			for (var p : f.places) if (p.type().equals("village")) { vd = Math.hypot(p.x() - f.cx, p.z() - f.cz); break; }
			out.add(new Task("minecraft:adventure/trade", "Köylüyle ticaret", "Bir köylüyle ticaret yap (zümrüt karşılığı eşya al/sat).", Items.EMERALD, 8 + vd / 220.0));
		}
		double shield = c(costs, Items.SHIELD);
		if (shield > 0 && m.contains("skeleton")) out.add(new Task("minecraft:story/deflect_arrow", "Oku kalkanla savur", "Kalkan yap ve bir iskelet okunu kalkanınla engelle.", Items.SHIELD, shield + 4));
		if (bucket > 0) {
			out.add(new Task("minecraft:story/lava_bucket", "Lav kovası", "Kovayı lavla doldur.", Items.LAVA_BUCKET, bucket + 4));
			out.add(new Task("minecraft:story/form_obsidian", "Obsidyen oluştur", "Akan lav kaynağının üzerine su dökerek obsidyen oluştur.", Items.OBSIDIAN, bucket + 9));
		}
		double table = c(costs, Items.ENCHANTING_TABLE);
		if (table > 0) out.add(new Task("minecraft:story/enchant_item", "Eşya büyüle", "Büyü masası yap ve bir eşyayı büyüle.", Items.ENCHANTING_TABLE, table + 6));
		double boots = c(costs, Items.LEATHER_BOOTS);
		if (boots > 0 && blockIn(f, "powder_snow")) out.add(new Task("minecraft:adventure/walk_on_powder_snow_with_leather_boots", "Toz karda yürü", "Deri botla toz karın üzerinde yürü.", Items.POWDER_SNOW_BUCKET, boots + 8));
		return out;
	}

	/** n gorev: maliyetleri birbirinden farkli olacak sekilde (kolay + orta + zor karisimi). */
	public static List<Task> pick(Knowledge.Facts f, Map<Item, Double> costs, int n, Random rnd) {
		List<Task> all = feasible(f, costs);
		Collections.shuffle(all, rnd);
		all.sort((a, b) -> Double.compare(a.cost(), b.cost()));
		List<Task> out = new ArrayList<>();
		if (n <= 0 || all.isEmpty()) return out;
		n = Math.min(n, all.size());
		// esit aralikli dilimlerden birer tane: kolay-orta-zor dagilimi
		for (int i = 0; i < n; i++) {
			int lo = i * all.size() / n, hi = Math.max(lo + 1, (i + 1) * all.size() / n);
			out.add(all.get(lo + rnd.nextInt(hi - lo)));
		}
		return out;
	}

	private static AdvancementHolder holder(MinecraftServer server, String id) {
		return server.getAdvancements().get(ResourceLocation.parse(id));
	}

	public static boolean done(MinecraftServer server, ServerPlayer p, String id) {
		AdvancementHolder h = holder(server, id);
		return h != null && p.getAdvancements().getOrStartProgress(h).isDone();
	}

	/** Oyun basinda: gorev ilerlemesini sifirlar (onceki oyunlardan kalan ilerleme sayilmasin). */
	public static void reset(MinecraftServer server, ServerPlayer p, List<Task> tasks) {
		for (Task t : tasks) {
			AdvancementHolder h = holder(server, t.adv());
			if (h == null) continue;
			var prog = p.getAdvancements().getOrStartProgress(h);
			for (String crit : new ArrayList<String>(iter(prog.getCompletedCriteria()))) p.getAdvancements().revoke(h, crit);
		}
	}

	private static List<String> iter(Iterable<String> it) {
		List<String> l = new ArrayList<>();
		for (String s : it) l.add(s);
		return l;
	}
}
