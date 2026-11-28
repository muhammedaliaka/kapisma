package tr.kapisma.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Advancement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Görevlerin koşulları, 1.21.1 ilerlemeleri ve maçta doğrulanan kaynaklarla eşleşir. */
public final class BingoTasks {
	private BingoTasks() {}

	public record Task(String adv, ResourceKey<Level> zone, String title, String desc, Item icon, double cost) {}

	static List<Task> pick(MinecraftServer server, BingoSurvey survey, Map<Item, ItemCost.Plan> plans,
			int count, boolean nether, boolean end, Random rnd) {
		List<Task> selected = new ArrayList<>();
		if (count <= 0) return selected;
		List<Task> dimensions = new ArrayList<>();
		if (nether) dimensions.add(new Task(null, Level.NETHER, "Nether'a geç", "Maç portalından Nether'a geç.", Items.NETHERRACK, 5));
		if (end) dimensions.add(new Task(null, Level.END, "End'e geç", "X 0, Z 0 konumundaki End portalından geç.", Items.END_STONE, 7));
		if (dimensions.size() > count) Collections.shuffle(dimensions, rnd);
		selected.addAll(dimensions.subList(0, Math.min(count, dimensions.size())));

		List<Task> possible = new ArrayList<>();
		add(possible, plans, Items.CRAFTING_TABLE, "story/root", "Çalışma masası edin", "Bir çalışma masasını envanterine al.", 0);
		Item stone = plans.containsKey(Items.COBBLESTONE) ? Items.COBBLESTONE : Items.COBBLED_DEEPSLATE;
		add(possible, plans, stone, "story/mine_stone", "Kazma taşı edin", "Kırık taş, kırık derin kaya veya kara taşı envanterine al.", 0.5);
		add(possible, plans, Items.STONE_PICKAXE, "story/upgrade_tools", "Taş kazma edin", "Bir taş kazmayı envanterine al.", 0);
		add(possible, plans, Items.IRON_INGOT, "story/smelt_iron", "Demir külçesi edin", "Bir demir külçesini envanterine al.", 0);
		add(possible, plans, Items.IRON_PICKAXE, "story/iron_tools", "Demir kazma edin", "Bir demir kazmayı envanterine al.", 0);
		List.of(Items.IRON_BOOTS, Items.IRON_HELMET, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE).stream()
			.filter(plans::containsKey).min(Comparator.comparingDouble(item -> plans.get(item).effort()))
			.ifPresent(item -> add(possible, plans, item, "story/obtain_armor", "Demir zırh edin", "Bir demir zırh parçasını envanterine al.", 0));
		add(possible, plans, Items.DIAMOND, "story/mine_diamond", "Elmas edin", "Bir elması envanterine al.", 0);
		List.of(Items.DIAMOND_BOOTS, Items.DIAMOND_HELMET, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE).stream()
			.filter(plans::containsKey).min(Comparator.comparingDouble(item -> plans.get(item).effort()))
			.ifPresent(item -> add(possible, plans, item, "story/shiny_gear", "Elmas zırh edin", "Bir elmas zırh parçasını envanterine al.", 0));
		if (plans.containsKey(Items.ARROW))
			add(possible, plans, Items.CROSSBOW, "adventure/ol_betsy", "Arbalet ateşle", "Arbaletle bir ok ateşle.", plans.get(Items.ARROW).effort() + 1);
		if (plans.containsKey(Items.WOODEN_HOE) && plans.containsKey(Items.DIRT))
			List.of(Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.BEETROOT_SEEDS).stream()
				.filter(plans::containsKey).min(Comparator.comparingDouble(item -> plans.get(item).effort()))
				.ifPresent(item -> add(possible, plans, item, "husbandry/plant_seed", "Tohum ek", "Doğrulanmış tohumu sürülmüş toprağa ek: " + ItemHints.name(item), 1.2));
		add(possible, plans, Items.LAVA_BUCKET, "story/lava_bucket", "Lav kovası edin", "Lavla doldurduğun kovayı envanterine al.", 0);
		add(possible, plans, Items.OBSIDIAN, "story/form_obsidian", "Obsidyen edin", "Obsidyeni envanterine al.", 0);
		if (survey.water) add(possible, plans, Items.FISHING_ROD, "husbandry/fishy_business", "Balık tut", "Oltayla morina, somon, balon ya da tropikal balık yakala.", 4);
		for (Item bed : List.of(Items.WHITE_BED, Items.RED_BED, Items.BROWN_BED, Items.BLACK_BED, Items.GRAY_BED,
				Items.LIGHT_GRAY_BED, Items.ORANGE_BED, Items.YELLOW_BED, Items.GREEN_BED, Items.LIME_BED, Items.CYAN_BED,
				Items.LIGHT_BLUE_BED, Items.BLUE_BED, Items.PURPLE_BED, Items.MAGENTA_BED, Items.PINK_BED)) {
			if (!plans.containsKey(bed)) continue;
			add(possible, plans, bed, "adventure/sleep_in_bed", "Yatakta uyu", "Gece olunca yatakta uyu.", 4);
			break;
		}
		possible.removeIf(task -> !vanillaTask(server, task.adv()));
		Collections.shuffle(possible, rnd);
		possible.sort(Comparator.comparingDouble(Task::cost));
		int remaining = Math.min(count - selected.size(), possible.size());
		for (int i = 0; i < remaining; i++) {
			int lo = i * possible.size() / remaining;
			int hi = (i + 1) * possible.size() / remaining;
			selected.add(possible.get(lo + rnd.nextInt(hi - lo)));
		}
		// Preferred spread first; if a shared-budget reservation fails, the caller
		// may try the remaining independently verified tasks before reducing count.
		for (Task task : possible) if (!selected.contains(task)) selected.add(task);
		return selected;
	}

	private static void add(List<Task> out, Map<Item, ItemCost.Plan> plans, Item item, String id, String title, String text, double extra) {
		ItemCost.Plan plan = plans.get(item);
		if (plan != null) out.add(new Task("minecraft:" + id, null, title, text, item, plan.effort() + extra));
	}

	/** Resource proofs describe vanilla advancement conditions, not arbitrary
	 * replacement criteria or rewards supplied by mods and datapacks. */
	private static boolean vanillaTask(MinecraftServer server, String id) {
		var active = holder(server, id);
		if (active == null) return false;
		try {
			var key = ResourceLocation.parse(id);
			var path = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), "advancement/" + key.getPath() + ".json");
			var resource = server.getResourceManager().getResource(path).orElse(null);
			if (resource == null || !resource.knownPackInfo().map(pack -> pack.isVanilla()).orElse(false)) {
				tr.kapisma.Kapisma.LOG.info("Tombala görev kanıtı reddedildi: {} (vanilla dışı veya eksik tanım)", id);
				return false;
			}
			var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			try (var reader = resource.openAsReader()) {
				var expected = Advancement.CODEC.parse(ops, JsonParser.parseReader(reader)).result().orElse(null);
				if (expected == null) return false;
				var encoded = Advancement.CODEC.encodeStart(ops, expected).result().orElse(null);
				var actual = Advancement.CODEC.encodeStart(ops, active.value()).result().orElse(null);
				boolean ok = encoded != null && encoded.equals(actual);
				if (!ok) tr.kapisma.Kapisma.LOG.info("Tombala görev kanıtı reddedildi: {} (etkin tanım değişmiş)", id);
				return ok;
			}
		} catch (Exception ex) {
			tr.kapisma.Kapisma.LOG.warn("Tombala görev tanımı doğrulanamadı: {}", id, ex);
			return false;
		}
	}

	private static AdvancementHolder holder(MinecraftServer server, String id) {
		return server.getAdvancements().get(ResourceLocation.parse(id));
	}

	public static boolean done(MinecraftServer server, ServerPlayer p, String id) {
		AdvancementHolder h = holder(server, id);
		return h != null && p.getAdvancements().getOrStartProgress(h).isDone();
	}

	public static void reset(MinecraftServer server, ServerPlayer p, List<Task> tasks) {
		for (Task task : tasks) {
			if (task.adv() == null) continue;
			AdvancementHolder h = holder(server, task.adv());
			if (h == null) continue;
			var prog = p.getAdvancements().getOrStartProgress(h);
			List<String> completed = new ArrayList<>();
			prog.getCompletedCriteria().forEach(completed::add);
			for (String crit : completed) p.getAdvancements().revoke(h, crit);
		}
	}
}
