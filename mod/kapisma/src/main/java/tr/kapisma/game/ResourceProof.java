package tr.kapisma.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable production witness; each verification starts with a fresh finite raw-material ledger. */
final class ResourceProof<K> {
	static final class Step<K> {
		final K item;
		final int yield;
		final boolean raw;
		final List<Input<K>> inputs;
		final Step<K> station, tool, fuel;
		final int toolDurability, cookTicks, fuelTicks;

		Step(K item, int yield, boolean raw, List<Input<K>> inputs, Step<K> station,
				Step<K> tool, int toolDurability, Step<K> fuel, int cookTicks, int fuelTicks) {
			this.item = item; this.yield = yield; this.raw = raw; this.inputs = List.copyOf(inputs);
			this.station = station; this.tool = tool; this.toolDurability = toolDurability;
			this.fuel = fuel; this.cookTicks = cookTicks; this.fuelTicks = fuelTicks;
		}
	}
	record Input<K>(Step<K> step, int quantity) {}

	private final Map<K, Integer> raw, stock = new HashMap<>(), wear = new HashMap<>();
	private final Set<K> placed = new HashSet<>();
	private int remaining = 4096;

	// A separate budget per card. A rejected candidate must not spend any material.
	static final class Ledger<K> {
		private ResourceProof<K> current;
		Ledger(Map<K, Integer> raw) { current = new ResourceProof<>(raw); }

		private Ledger(ResourceProof<K> proof) { current = new ResourceProof<>(proof); }
		Ledger<K> fork() { return new Ledger<>(current); }

		/** Estimate the fraction of currently available raw resources a reservation consumes. */
		double scarcity(List<Input<K>> demands) {
			var trial = fork();
			if (!trial.reserve(demands)) return Double.POSITIVE_INFINITY;
			double cost = 0;
			for (var entry : current.raw.entrySet()) {
				int before = entry.getValue(), after = trial.current.raw.getOrDefault(entry.getKey(), 0);
				if (before > after) cost += (before - after) / (double) Math.max(1, before);
			}
			return cost;
		}

		boolean reserve(List<Input<K>> demands) { return commit(demands, false); }

		// Bingo cells are credited on acquisition, not on surrender of the item.
		// Keep the produced items in inventory for later recipes. Actual ingredients
		// and furnace fuel still leave the finite ledger.
		boolean observe(List<Input<K>> demands) { return commit(demands, true); }

		private boolean commit(List<Input<K>> demands, boolean keepOutputs) {
			var trial = new ResourceProof<>(current);
			for (Input<K> demand : demands) {
				trial.remaining = 4096;
				if (!trial.take(demand.step(), demand.quantity(), 0)) return false;
			}
			if (keepOutputs) for (Input<K> demand : demands)
				trial.stock.merge(demand.step().item, demand.quantity(), Integer::sum);
			current = trial;
			return true;
		}
	}

	private ResourceProof(Map<K, Integer> raw) { this.raw = new HashMap<>(raw); }

	private ResourceProof(ResourceProof<K> source) {
		this(source.raw);
		stock.putAll(source.stock);
		wear.putAll(source.wear);
		placed.addAll(source.placed);
	}

	static <K> boolean proves(Step<K> step, int quantity, Map<K, Integer> raw) {
		return new ResourceProof<>(raw).take(step, quantity, 0);
	}

	private boolean take(Step<K> step, int quantity, int depth) {
		if (step == null || quantity <= 0 || depth > 64 || --remaining < 0 || step.yield <= 0) return false;
		int available = stock.getOrDefault(step.item, 0);
		if (available >= quantity) {
			stock.put(step.item, available - quantity);
			return true;
		}
		stock.remove(step.item);
		int missing = quantity - available;
		int batches = (missing + step.yield - 1) / step.yield;
		if (step.raw) {
			int left = raw.getOrDefault(step.item, 0);
			if (left < batches) return false;
			raw.put(step.item, left - batches);
		}
		if (step.station != null && !placed.contains(step.station.item)) {
			if (!take(step.station, 1, depth + 1)) return false;
			placed.add(step.station.item);
		}
		if (step.tool != null) {
			if (step.toolDurability <= 0) return false;
			int left = wear.getOrDefault(step.tool.item, 0);
			int deficit = Math.max(0, batches - left);
			int tools = (deficit + step.toolDurability - 1) / step.toolDurability;
			if (tools > 0 && !take(step.tool, tools, depth + 1)) return false;
			wear.put(step.tool.item, left + tools * step.toolDurability - batches);
		}
		for (Input<K> input : step.inputs) {
			if (!take(input.step(), input.quantity() * batches, depth + 1)) return false;
		}
		if (step.fuel != null) {
			if (step.cookTicks <= 0 || step.fuelTicks <= 0) return false;
			long ticks = (long) step.cookTicks * batches;
			long units = (ticks + step.fuelTicks - 1) / step.fuelTicks;
			if (units > Integer.MAX_VALUE || !take(step.fuel, (int) units, depth + 1)) return false;
		}
		int surplus = batches * step.yield - missing;
		stock.merge(step.item, surplus, Integer::sum);
		return true;
	}
}
