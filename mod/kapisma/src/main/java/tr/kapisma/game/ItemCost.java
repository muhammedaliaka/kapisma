package tr.kapisma.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Doğrulanmış hammaddelerden başlayan, gerekli aleti ve istasyonu izleyen tarif planları. */
public final class ItemCost {
	private ItemCost() {}

	record Plan(int count, double effort, String hint, ResourceProof.Step<Item> proof) {}
	private record Station(double effort, String hint, ResourceProof.Step<Item> proof, boolean needsFuel) {}

	private static ResourceProof.Step<Item> step(Item item, int yield, boolean raw, List<ResourceProof.Input<Item>> inputs,
			ResourceProof.Step<Item> station, ResourceProof.Step<Item> tool, ResourceProof.Step<Item> fuel, int cook, int burn) {
		return new ResourceProof.Step<>(item, yield, raw, inputs, station, tool,
			tool == null ? 0 : tool.item.getDefaultInstance().getMaxDamage(), fuel, cook, burn);
	}

	static Map<Item, Plan> compute(MinecraftServer server, BingoSurvey survey) {
		Map<Item, Plan> plans = new HashMap<>();
		var recipes = server.getRecipeManager().getRecipes();
		var fuels = AbstractFurnaceBlockEntity.getFuel();
		Map<Item, Integer> rawCounts = rawCounts(survey);
		var registries = server.registryAccess();
		boolean changed = true;
		for (int pass = 0; changed && pass < 32; pass++) {
			changed = false;
			for (var entry : survey.sources.entrySet()) {
				var source = entry.getValue();
				Plan tool = plans.get(source.tool().item);
				if (source.tool() != BingoSurvey.Tool.HAND && tool == null) continue;
				changed |= offer(plans, entry.getKey(), new Plan(source.count(),
					source.cost() + (tool == null ? 0 : tool.effort() * 0.65), source.hint(),
					step(entry.getKey(), 1, true, List.of(), null, tool == null ? null : tool.proof(), null, 0, 0)));
			}
			Plan bucket = plans.get(Items.BUCKET);
			if (bucket != null && survey.water) changed |= offer(plans, Items.WATER_BUCKET, new Plan(1, bucket.effort() + 1, "Bir su kaynağını kovayla al.",
				step(Items.WATER_BUCKET, 1, true, List.of(new ResourceProof.Input<>(bucket.proof(), 1)), null, null, null, 0, 0)));
			if (bucket != null && survey.lava) changed |= offer(plans, Items.LAVA_BUCKET, new Plan(1, bucket.effort() + 4, "Yaklaşılabilir bir lav kaynağını kovayla al.",
				step(Items.LAVA_BUCKET, 1, true, List.of(new ResourceProof.Input<>(bucket.proof(), 1)), null, null, null, 0, 0)));
			for (var holder : recipes) {
				Recipe<?> recipe = holder.value();
				var output = recipe.getResultItem(registries);
				if (output.isEmpty()) continue;
				List<Ingredient> inputs = recipe.getIngredients().stream().filter(ing -> !ing.isEmpty()).toList();
				if (inputs.isEmpty()) continue;
				Station station = station(recipe, inputs.size(), plans);
				if (station == null) continue;
				Plan fuel = null;
				int cook = 0, burn = 0;
				if (station.needsFuel()) {
					if (!(recipe instanceof AbstractCookingRecipe cooking)) continue;
					cook = cooking.getCookingTime();
					for (Item item : List.of(Items.COAL, Items.CHARCOAL, Items.STICK, Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
							Items.BIRCH_PLANKS, Items.ACACIA_PLANKS, Items.JUNGLE_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS)) {
						Plan candidate = plans.get(item);
						int duration = fuels.getOrDefault(item, 0);
						if (candidate == null || duration <= 0 || candidate.count() * (long) duration < cook) continue;
						if (fuel == null || candidate.effort() / duration < fuel.effort() / burn) {
							fuel = candidate; burn = duration;
						}
					}
					if (fuel == null) continue;
				}
				var selected = selectIngredients(inputs, plans);
				if (selected == null) continue;
				int crafts = 64;
				double max = 0, total = 0;
				for (var input : selected.entrySet()) {
					Plan plan = plans.get(input.getKey());
					crafts = Math.min(crafts, plan.count() / input.getValue());
					max = Math.max(max, plan.effort());
					total += plan.effort() * (1 + 0.12 * (input.getValue() - 1));
				}
				if (fuel != null) crafts = Math.min(crafts, (int) Math.min(64L, (long) fuel.count() * burn / Math.max(1, cook)));
				if (crafts == 0) continue;
				double effort = max + (total - max) * 0.34 + station.effort() * 0.38 + 0.6;
				Plan existing = plans.get(output.getItem());
				if (existing != null && effort >= existing.effort() - 0.001) continue;
				String names = String.join(" + ", selected.entrySet().stream()
					.sorted(Comparator.comparing(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey())))
					.map(e -> (e.getValue() == 1 ? "" : e.getValue() + "× ") + ItemHints.name(e.getKey())).toList());
				List<ResourceProof.Input<Item>> needs = selected.entrySet().stream()
					.sorted(Comparator.comparing(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey())))
					.map(e -> new ResourceProof.Input<>(plans.get(e.getKey()).proof(), e.getValue())).toList();
				var proof = step(output.getItem(), output.getCount(), false, needs, station.proof(), null,
					fuel == null ? null : fuel.proof(), cook, burn);
				// A cheap path which spends the same wood/ore twice must not replace a valid path.
				if (!ResourceProof.proves(proof, 1, rawCounts)) continue;
				changed |= offer(plans, output.getItem(), new Plan(Math.min(64, crafts * output.getCount()), effort, station.hint() + names, proof));
			}
		}
		plans.replaceAll((item, plan) -> {
			int low = 0, high = Math.min(64, plan.count());
			while (low < high) {
				int mid = (low + high + 1) / 2;
				if (ResourceProof.proves(plan.proof(), mid, rawCounts)) low = mid;
				else high = mid - 1;
			}
			return new Plan(low, plan.effort(), plan.hint(), plan.proof());
		});
		plans.values().removeIf(plan -> plan.count() == 0);
		return plans;
	}

	static Map<Item, Integer> rawCounts(BingoSurvey survey) {
		Map<Item, Integer> counts = new HashMap<>();
		survey.sources.forEach((item, source) -> counts.put(item, source.count()));
		if (survey.water) counts.put(Items.WATER_BUCKET, 1);
		if (survey.lava) counts.put(Items.LAVA_BUCKET, 1);
		return counts;
	}

	private static Map<Item, Integer> selectIngredients(List<Ingredient> inputs, Map<Item, Plan> plans) {
		Map<Item, Integer> selected = new HashMap<>();
		List<Ingredient> ordered = new ArrayList<>(inputs);
		ordered.sort(Comparator.comparingInt(ing -> ing.getItems().length));
		for (Ingredient ingredient : ordered) {
			Item best = null;
			double effort = Double.POSITIVE_INFINITY;
			for (var stack : ingredient.getItems()) {
				Item candidate = stack.getItem();
				Plan plan = plans.get(candidate);
				if (plan == null || plan.count() <= selected.getOrDefault(candidate, 0) || plan.effort() >= effort) continue;
				best = candidate;
				effort = plan.effort();
			}
			if (best == null) return null;
			selected.merge(best, 1, Integer::sum);
		}
		return selected;
	}

	private static Station station(Recipe<?> recipe, int size, Map<Item, Plan> plans) {
		var type = recipe.getType();
		if (type == RecipeType.CRAFTING) {
			boolean needsTable = recipe instanceof ShapedRecipe shaped
				? shaped.getWidth() > 2 || shaped.getHeight() > 2 : size > 4;
			return needsTable ? withStation(plans, Items.CRAFTING_TABLE, "Çalışma masası · ", false) : new Station(0, "Üret · ", null, false);
		}
		if (type == RecipeType.SMELTING) return withStation(plans, Items.FURNACE, "Fırın · ", true);
		if (type == RecipeType.BLASTING) return withStation(plans, Items.BLAST_FURNACE, "Maden fırını · ", true);
		if (type == RecipeType.SMOKING) return withStation(plans, Items.SMOKER, "Duman fırını · ", true);
		if (type == RecipeType.CAMPFIRE_COOKING) return withStation(plans, Items.CAMPFIRE, "Kamp ateşi · ", false);
		if (type == RecipeType.STONECUTTING) return withStation(plans, Items.STONECUTTER, "Taş kesici · ", false);
		return null;
	}

	private static Station withStation(Map<Item, Plan> plans, Item item, String hint, boolean needsFuel) {
		var plan = plans.get(item);
		if (plan == null) return null;
		return new Station(plan.effort(), hint, plan.proof(), needsFuel);
	}

	private static boolean offer(Map<Item, Plan> plans, Item item, Plan next) {
		Plan old = plans.get(item);
		if (old == null || next.effort() < old.effort() - 0.001) {
			plans.put(item, next);
			return true;
		}
		return false;
	}

	public static String starText(double cost) {
		int stars = cost < 4 ? 1 : cost < 8 ? 2 : cost < 14 ? 3 : cost < 22 ? 4 : 5;
		return "★".repeat(stars) + "☆".repeat(5 - stars);
	}
}
