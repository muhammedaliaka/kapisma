package tr.kapisma.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** UHC hazırlığı sırasında yüzey sandıkları ve onları gösteren köy haritaları. */
final class UhcTreasure {
	static final int LOAD_BATCH = 4;
	private static final TicketType<ChunkPos> LOAD = TicketType.create("kapisma_treasure", Comparator.comparingLong(ChunkPos::toLong));
	private final ServerLevel level;
	private final int centerX, centerZ;
	private final double borderRadius;
	private final Set<ChunkPos> tickets = new LinkedHashSet<>();
	private final Random random;
	private final int treasureParity;
	private final int expectedCount;
	private boolean failed, closed;
	private final Queue<ChunkPos> villages;
	private final Map<ChunkPos, List<BlockPos>> columns = new LinkedHashMap<>();
	private final Set<ChunkPos> loading = new LinkedHashSet<>();
	private final List<BlockPos> chests = new ArrayList<>();
	private final List<BarrelBlockEntity> barrels = new ArrayList<>();
	private ChunkPos villageProbe;
	private ChunkPos depotChunk;
	private BlockPos depot;
	private boolean inVillage;
	private int mapsPlaced;
	private IncrementalBiomePreview pendingPreview;

	UhcTreasure(GameWorld world, Random random, double safeRadius, int count) {
		this(world.level, world.centerX, world.centerZ, world.borderRadius(), random, safeRadius, count);
	}

	// Shared production path; the isolated vanilla proof can supply its own ServerLevel.
	UhcTreasure(ServerLevel level, int centerX, int centerZ, double borderRadius, Random random, double safeRadius, int count) {
		this.level = level;
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.borderRadius = borderRadius;
		this.random = random;
		this.expectedCount = count;
		int radius = (int) safeRadius - 1;
		if (radius < 0 || count < 1 || count > (long)(radius + 1) * (radius + 1))
			throw new IllegalArgumentException("impossible treasure placement");
		treasureParity = Math.floorMod(centerX - radius, 2);
		Set<BlockPos> selected = new LinkedHashSet<>();
		while (selected.size() < count) {
			// İkişer blokluk aralık sandıkların birleşmesini engeller.
			int x = centerX - radius + 2 * random.nextInt(radius + 1);
			int z = centerZ - radius + 2 * random.nextInt(radius + 1);
			selected.add(new BlockPos(x, 0, z));
		}
		for (BlockPos pos : selected) columns.computeIfAbsent(new ChunkPos(pos), key -> new ArrayList<>()).add(pos);
		long timing = GameWorld.timingStart();
		try {
			villages = new ArrayDeque<>(villageCandidates());
		} finally {
			GameWorld.timingEnd("uhc.village_candidates", timing, 5);
		}
	}

	boolean prepare(int admissionCap) {
		if (closed) return isComplete();
		if (failed) return false;
		if (isComplete()) return true;
		int slots = ChunkAdmission.slots(admissionCap, loading.size());
		for (var entry : columns.entrySet()) {
			if (slots == 0) break;
			if (loading.add(entry.getKey())) {
				ticket(entry.getKey(), true);
				slots--;
			}
		}
		for (var it = loading.iterator(); it.hasNext();) {
			ChunkPos pos = it.next();
			if (!loaded(pos.getMinBlockX(), pos.getMinBlockZ())) continue;
			long timing = GameWorld.timingStart();
			try {
				for (BlockPos column : columns.remove(pos)) placeChest(column);
			} finally {
				GameWorld.timingEnd("uhc.place_chests", timing, 5);
			}
			if (!pos.equals(villageProbe) && !pos.equals(depotChunk)) ticket(pos, false);
			it.remove();
		}
		if (failed) return false;
		if (depot == null) {
			long timing = GameWorld.timingStart();
			try { findDepot(); }
			finally { GameWorld.timingEnd("uhc.find_depot", timing, 5); }
		}
		if (failed || depot == null || !columns.isEmpty()) return false;
		if (chests.size() != expectedCount || barrels.size() * 27 < expectedCount) {
			failed = true;
			return false;
		}
		long timing = GameWorld.timingStart();
		boolean stocked;
		try {
			stocked = IncrementalBiomePreview.ENABLED ? stockIncrementalMap() : stockMaps(2);
		} finally {
			GameWorld.timingEnd("uhc.stock_maps", timing, 0);
		}
		if (!stocked) return false;
		// All depot barrels are still pinned here. Verify that their live block
		// entities match the objects into which maps were written.
		for (int b = 0; b < barrels.size(); b++) {
			var barrel = barrels.get(b);
			int expected = Math.min(27, expectedCount - b * 27);
			int actual = 0;
			for (int i = 0; i < barrel.getContainerSize(); i++)
				if (barrel.getItem(i).is(Items.FILLED_MAP)) actual++;
			if (level.getBlockEntity(barrel.getBlockPos()) != barrel || actual != expected) {
				failed = true;
				return false;
			}
			tr.kapisma.Kapisma.LOG.info("UHC hazine fıçısı {}: ({},{},{}) harita={}", b,
				barrel.getBlockPos().getX(), barrel.getBlockPos().getY(), barrel.getBlockPos().getZ(), actual);
		}
		close();
		return isComplete();
	}

	private void placeChest(BlockPos column) {
		BlockPos pos = surface(column.getX(), column.getZ());
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			nameContainer(chest, Items.CHEST, "UHC Hazine Sandığı");
			chest.setItem(4, new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2)));
			chest.setItem(11, new ItemStack(Items.GOLD_INGOT, 3 + random.nextInt(5)));
			chest.setItem(13, new ItemStack(Items.ARROW, 6 + random.nextInt(7)));
			chest.setItem(15, new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(5)));
			chests.add(pos);
		} else failed = true;
	}

	private BlockPos surface(int x, int z) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		BlockPos pos = new BlockPos(x, Math.min(y, level.getMaxBuildHeight() - 1), z);
		if (!level.getFluidState(pos.below()).isEmpty()) level.setBlockAndUpdate(pos.below(), Blocks.OAK_PLANKS.defaultBlockState());
		return pos;
	}

	private List<ChunkPos> villageCandidates() {
		var state = level.getChunkSource().getGeneratorState();
		int r = (int) borderRadius / 16 + 1;
		int cx = centerX >> 4, cz = centerZ >> 4;
		Set<ChunkPos> candidates = new LinkedHashSet<>();
		for (var holder : state.possibleStructureSets()) {
			var set = holder.value();
			if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)
				|| set.structures().stream().noneMatch(e -> e.structure().is(StructureTags.VILLAGE))) continue;
			int spacing = spread.spacing();
			for (int rx = Math.floorDiv(cx - r, spacing); rx <= Math.floorDiv(cx + r, spacing); rx++) {
				for (int rz = Math.floorDiv(cz - r, spacing); rz <= Math.floorDiv(cz + r, spacing); rz++) {
					ChunkPos cp = spread.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
					if (Math.abs(cp.getMiddleBlockX() - centerX) >= borderRadius - 4
						|| Math.abs(cp.getMiddleBlockZ() - centerZ) >= borderRadius - 4) continue;
					if (spread.isStructureChunk(state, cp.x, cp.z)) candidates.add(cp);
				}
			}
		}
		return candidates.stream().sorted(Comparator.comparingInt(cp -> cp.getChessboardDistance(new ChunkPos(cx, cz)))).toList();
	}

	private void findDepot() {
		if (villageProbe == null) {
			villageProbe = villages.poll();
			if (villageProbe == null) {
				if (loaded(centerX, centerZ)) makeDepot(centerX, centerZ);
				return;
			}
			ticket(villageProbe, true);
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(villageProbe.x, villageProbe.z);
		if (chunk == null) return;
		var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		inVillage = chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid()
			&& structures.wrapAsHolder(start.getStructure()).is(StructureTags.VILLAGE));
		if (inVillage) makeDepot(villageProbe.getMiddleBlockX(), villageProbe.getMiddleBlockZ());
		if (!loading.contains(villageProbe) && !villageProbe.equals(depotChunk)) ticket(villageProbe, false);
		villageProbe = null;
	}

	private void makeDepot(int x, int z) {
		depotChunk = new ChunkPos(new BlockPos(x, 0, z));
		if (Math.floorMod(x, 2) == treasureParity) x += x == depotChunk.getMaxBlockX() ? -1 : 1;
		ticket(depotChunk, true);
		int barrelCount = (expectedCount + 26) / 27;
		z = Math.min(z, depotChunk.getMaxBlockZ() - barrelCount + 1);
		for (int i = 0; i < barrelCount; i++) {
			BlockPos pos = surface(x, z + i);
			level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
				nameContainer(barrel, Items.BARREL, "UHC Hazine Haritaları");
				barrels.add(barrel);
				if (depot == null) depot = pos;
			} else failed = true;
		}
	}

	private static void nameContainer(net.minecraft.world.level.block.entity.BlockEntity entity, net.minecraft.world.item.Item item, String name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		entity.applyComponentsFromItemStack(stack);
	}

	/** Called once per loading tick, never from a background worker. */
	void advancePreview() {
		if (closed || failed || pendingPreview == null || pendingPreview.complete()) return;
		long timing = GameWorld.timingStart();
		try {
			pendingPreview.step(IncrementalBiomePreview.MAX_OPERATIONS, IncrementalBiomePreview.BUDGET_NS);
		} catch (RuntimeException ex) {
			failed = true;
			tr.kapisma.Kapisma.LOG.error("UHC incremental preview failed", ex);
		} finally {
			GameWorld.timingEnd("map.preview_slice", timing, 8);
		}
	}

	private boolean stockIncrementalMap() {
		if (mapsPlaced >= chests.size()) return true;
		try {
			BlockPos target = chests.get(mapsPlaced);
			if (pendingPreview == null) {
				pendingPreview = new IncrementalBiomePreview(level, GameWorld.createCenteredMap(level, target.getX(), target.getZ(), (byte) 2, true));
				return false;
			}
			if (!pendingPreview.complete()) return false;
			var barrel = barrels.get(mapsPlaced / 27);
			if (level.getBlockEntity(barrel.getBlockPos()) != barrel) throw new IllegalStateException("depot replaced");
			publishMap(pendingPreview.take(), target);
			tr.kapisma.Kapisma.LOG.info("ULTRA_MAP_COMPLETE kind=treasure index={}", mapsPlaced);
			pendingPreview = null;
			return mapsPlaced >= chests.size();
		} catch (RuntimeException ex) {
			failed = true;
			tr.kapisma.Kapisma.LOG.error("UHC incremental map publication failed", ex);
			return false;
		}
	}

	private boolean stockMaps(int batch) {
		for (int i = 0; mapsPlaced < chests.size() && i < batch; i++) {
			BlockPos target = chests.get(mapsPlaced);
			ItemStack map = GameWorld.centeredMap(level, target.getX(), target.getZ(), (byte) 2, true);
			publishMap(map, target);
		}
		return mapsPlaced >= chests.size();
	}

	private void publishMap(ItemStack map, BlockPos target) {
		MapItemSavedData.addTargetDecoration(map, target, "hazine", MapDecorationTypes.RED_X);
		map.set(DataComponents.CUSTOM_NAME, Component.literal("Hazine Haritası " + (mapsPlaced + 1)));
		barrels.get(mapsPlaced / 27).setItem(mapsPlaced % 27, map);
		mapsPlaced++;
	}

	private boolean loaded(int x, int z) {
		return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
	}

	private void ticket(ChunkPos pos, boolean add) {
		if (add && !tickets.contains(pos)) {
			level.getChunkSource().addRegionTicket(LOAD, pos, 1, pos);
			tickets.add(pos);
		} else if (!add && tickets.contains(pos)) {
			level.getChunkSource().removeRegionTicket(LOAD, pos, 1, pos);
			tickets.remove(pos);
		}
	}

	int outstandingTickets() { return tickets.size(); }
	int outstandingChestChunks() { return loading.size(); }
	List<BlockPos> chestPositions() { return List.copyOf(chests); }
	List<BlockPos> depotPositions() { return barrels.stream().map(BarrelBlockEntity::getBlockPos).toList(); }

	boolean failed() { return failed; }

	boolean isComplete() {
		return !failed && columns.isEmpty() && chests.size() == expectedCount && mapsPlaced == expectedCount && depot != null;
	}

	void close() {
		closed = true;
		if (pendingPreview != null) { pendingPreview.cancel(); pendingPreview = null; }
		for (ChunkPos pos : List.copyOf(tickets)) ticket(pos, false);
		loading.clear();
		villageProbe = null;
		depotChunk = null;
	}

	String message() {
		if (depot == null || mapsPlaced == 0) return "Bu oyunda hazine haritaları hazırlanamadı.";
		return chests.size() + " hazine sandığı hazır. Haritalar " + (inVillage ? "köydeki" : "merkezdeki")
			+ " fıçılarda (X " + depot.getX() + ", Z " + depot.getZ() + ").";
	}
}
