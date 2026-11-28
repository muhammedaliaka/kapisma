package tr.kapisma.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/** Oyunun kendi dünyasından doğrulanan ve sınır içindeki erişilebilir kaynaklar. */
public final class BingoSurvey {
	enum Tool {
		HAND(null, ""), WOOD(Items.WOODEN_PICKAXE, "tahta kazma"), STONE(Items.STONE_PICKAXE, "taş kazma"),
		IRON(Items.IRON_PICKAXE, "demir kazma"), DIAMOND(Items.DIAMOND_PICKAXE, "elmas kazma"),
		SWORD(Items.WOODEN_SWORD, "kılıç");

		final Item item;
		final String label;
		Tool(Item item, String label) { this.item = item; this.label = label; }
	}

	record Drop(Item item, Tool tool) {}
	record Source(int count, double cost, String hint, Tool tool) {}

	private static final int BATCH = 4;
	private long scanNanos;
	private int pendingPolls;
	private final long started = System.nanoTime();
	private int reportedScans = -1;
	private static final TicketType<ChunkPos> TICKET = TicketType.create("kapisma_bingo_survey", Comparator.comparingLong(ChunkPos::toLong));
	private static final Map<Block, Drop> DROPS = buildDrops();

	private final GameWorld world;
	private final ServerLevel level;
	private final VanillaLootProof loot;
	private final ArrayDeque<ChunkPos> candidates;
	private final Set<ChunkPos> loading = new LinkedHashSet<>();
	private final Set<ChunkPos> scheduled = new LinkedHashSet<>();
	private boolean expanded;
	final Knowledge.Facts facts = new Knowledge.Facts();
	final Map<Item, Source> sources = new HashMap<>();
	private final List<BlockPos> landings = new ArrayList<>();
	private final long deadline;
	private final boolean reserveNether, reserveEnd;
	int scanned;
	boolean water, lava;

	BingoSurvey(GameWorld world, int seconds, boolean reserveNether, boolean reserveEnd) {
		this(world, seconds * 1000L, reserveNether, reserveEnd);
	}

	private BingoSurvey(GameWorld world, long millis, boolean reserveNether, boolean reserveEnd) {
		this.world = world;
		this.level = world.level;
		this.loot = new VanillaLootProof(level.getServer());
		deadline = System.nanoTime() + millis * 1_000_000L;
		this.reserveNether = reserveNether;
		this.reserveEnd = reserveEnd;
		facts.cx = world.centerX;
		facts.cz = world.centerZ;
		facts.radius = (int) world.borderRadius();
		scheduled.addAll(sampleChunks());
		candidates = new ArrayDeque<>(scheduled);
	}

	/** No new chunk generation or tickets on the running race thread. */
	static BingoSurvey race(GameWorld world, Iterable<ChunkPos> positions, long millis) {
		var survey = new BingoSurvey(world, millis, false, false);
		for (ChunkPos pos : positions) {
			if (System.nanoTime() >= survey.deadline || survey.scanned >= 12) break;
			if (!survey.inside(pos.getMinBlockX(), pos.getMinBlockZ()) || !survey.inside(pos.getMaxBlockX(), pos.getMaxBlockZ())) continue;
			LevelChunk chunk = world.level.getChunkSource().getChunkNow(pos.x, pos.z);
			if (chunk == null) continue;
			survey.scanRaceSurface(chunk);
			survey.scanned++;
		}
		// Gravel has a chance of yielding flint. Only count repeated opportunities,
		// not a guaranteed one gravel per block.
		var gravel = survey.sources.get(Items.GRAVEL);
		if (gravel != null) {
			if (gravel.count() < 8) survey.sources.remove(Items.GRAVEL);
			else survey.sources.put(Items.GRAVEL, new Source(gravel.count() / 8, gravel.cost(), gravel.hint(), gravel.tool()));
		}
		return survey;
	}

	private void scanRaceSurface(LevelChunk chunk) {
		caveCache.clear();
		caveBudget[0] = CAVE_NODES_PER_CHUNK;
		var cp = chunk.getPos();
		var pos = new BlockPos.MutableBlockPos();
		for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
			if (System.nanoTime() >= deadline) return;
			int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			int wx = cp.getMinBlockX() + x, wz = cp.getMinBlockZ() + z;
			// Check actual surface and a short, solid, hand-breakable path underneath.
			for (int depth = 0; depth <= 12; depth++) {
				pos.set(wx, y - depth, wz);
				if (pos.getY() <= level.getMinBuildHeight()) break;
				BlockState state = chunk.getBlockState(pos);
				Drop drop = state.is(Blocks.GRAVEL) ? new Drop(Items.GRAVEL, Tool.HAND) : drop(state);
				if (drop != null && (sources.get(drop.item()) == null || sources.get(drop.item()).count() < 256)
						&& loot.accepts(state.getBlock())
						&& (approach(pos) != null || depth > 0 && depth <= 4 && shallowPath(chunk, wx, y, wz, depth))) {
					addSource(drop, 0.8 + depth + Math.hypot(wx - world.centerX, wz - world.centerZ) / 115,
						"Yüzeye yakın · " + region(pos));
				}
				// Trees and exposed stone are counted above; never infer a deep ore from
				// an inaccessible section merely because its palette contains iron.
				if (!handDiggable(state) && !state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) break;
			}
		}
	}

	private boolean shallowPath(LevelChunk chunk, int x, int top, int z, int depth) {
		if (!canStand(level, new BlockPos(x, top + 1, z), false)) return false;
		for (int y = top; y > top - depth; y--)
			if (!handDiggable(chunk.getBlockState(new BlockPos(x, y, z)))) return false;
		return true;
	}

	private static boolean handDiggable(BlockState state) {
		return state.getFluidState().isEmpty() && (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.COARSE_DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
			|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY));
	}

	private void addSource(Drop drop, double effort, String hint) {
		Source previous = sources.get(drop.item());
		int count = previous == null ? 1 : Math.min(256, previous.count() + 1);
		if (previous != null && previous.cost() <= effort)
			sources.put(drop.item(), new Source(count, previous.cost(), previous.hint(), previous.tool()));
		else sources.put(drop.item(), new Source(count, effort, hint, drop.tool()));
	}

	private Set<ChunkPos> sampleChunks() {
		Set<ChunkPos> out = new LinkedHashSet<>();
		int cx = world.centerX >> 4, cz = world.centerZ >> 4;
		addChunk(out, cx, cz);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) addChunk(out, cx + dx, cz + dz);
		// Nether dönüş alanı dünya koordinatı 0,0 merkezlidir; arena merkezi kaymış olabilir.
		if (reserveNether) {
			for (int x : new int[] {-7, -3, 0, 3, 7}) for (int z : new int[] {-7, -3, 0, 3, 7}) {
				if (x == 0 && z == 0 || Math.abs(x) == Math.abs(z) || x == 0 && Math.abs(z) == 3
						|| z == 0 && Math.abs(x) == 3) addChunk(out, x, z);
			}
		}
		int r = Math.max(2, facts.radius / 32);
		if (!reserveNether && !reserveEnd && r >= 6) {
			// Four compact 2x2 clusters, one in each quadrant. The generator can
			// reuse neighbouring status dependencies instead of opening a new
			// distant generation region for every individual sample.
			for (int dx : new int[] {-1, 1}) for (int dz : new int[] {-1, 1}) {
				int anchorX = cx + dx * r, anchorZ = cz + dz * r;
				for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
					addChunk(out, anchorX - dx * x, anchorZ - dz * z);
			}
		} else {
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) addChunk(out, cx + dx * r, cz + dz * r);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) addChunk(out, cx + dx * (2 * r - 1), cz + dz * (2 * r - 1));
		}
		return out;
	}

	/** A single bounded second pass, only when the first verified budget is insufficient.
	 * Never scan the same chunk twice or extend the original wall-clock deadline. */
	boolean expand() {
		if (expanded || !loading.isEmpty() || !candidates.isEmpty()
			|| deadline - System.nanoTime() < 3_000_000_000L) return false;
		expanded = true;
		Set<ChunkPos> extra = new LinkedHashSet<>();
		int cx = world.centerX >> 4, cz = world.centerZ >> 4;
		int r = Math.max(2, facts.radius / 32);
		int inner = Math.max(2, r / 2), outer = Math.max(3, r + r / 2);
		if (!reserveNether && !reserveEnd && r >= 6) {
			// Two more disjoint clusters on the axes and two at intermediate
			// distances, still bounded to sixteen previously unscanned chunks.
			for (int[] anchor : new int[][] {{-outer, 0}, {outer, 0}, {0, -inner}, {0, inner}}) {
				int ax = cx + anchor[0], az = cz + anchor[1];
				for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) addChunk(extra, ax + x, az + z);
			}
		} else {
		for (int dx : new int[] {-inner, inner}) for (int dz : new int[] {-outer, outer}) {
			addChunk(extra, cx + dx, cz + dz);
			addChunk(extra, cx + dz, cz + dx);
		}
		for (int dx : new int[] {-inner, 0, inner}) for (int dz : new int[] {-inner, 0, inner})
			if (dx != 0 || dz != 0) addChunk(extra, cx + dx, cz + dz);
		}
		extra.removeAll(scheduled);
		scheduled.addAll(extra);
		candidates.addAll(extra);
		return !candidates.isEmpty();
	}

	private void addChunk(Set<ChunkPos> out, int x, int z) {
		ChunkPos pos = new ChunkPos(x, z);
		if (inside(pos.getMinBlockX(), pos.getMinBlockZ()) && inside(pos.getMaxBlockX(), pos.getMaxBlockZ())) out.add(pos);
	}

	boolean inside(int x, int z) {
		return level.getWorldBorder().isWithinBounds(x + 0.5, z + 0.5);
	}

	/** Her turda en çok bir tam parça taranır. Üretim, sınırlı sayıda biletle arka planda ilerler. */
	boolean tick() {
		if (System.nanoTime() >= deadline) { close(); reportTiming(); return true; }
		while (loading.size() < BATCH && !candidates.isEmpty()) {
			ChunkPos pos = candidates.removeFirst();
			level.getChunkSource().addRegionTicket(TICKET, pos, 1, pos);
			loading.add(pos);
		}
		for (var it = loading.iterator(); it.hasNext();) {
			ChunkPos pos = it.next();
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
			if (chunk == null) continue;
			long start = System.nanoTime();
			scan(chunk);
			scanNanos += System.nanoTime() - start;
			level.getChunkSource().removeRegionTicket(TICKET, pos, 1, pos);
			it.remove();
			scanned++;
			break;
		}
		boolean done = loading.isEmpty() && candidates.isEmpty();
		if (done) reportTiming(); else pendingPolls++;
		return done;
	}

	private void reportTiming() {
		if (reportedScans == scanned) return;
		reportedScans = scanned;
		tr.kapisma.Kapisma.LOG.info("Tombala tarama ölçümü: chunk={} duvarMs={} taramaMs={} beklemeTuru={}", scanned,
			(System.nanoTime() - started) / 1_000_000L, scanNanos / 1_000_000L, pendingPolls);
	}

	List<BlockPos> landings() { return List.copyOf(landings); }

	void close() {
		for (ChunkPos pos : loading) level.getChunkSource().removeRegionTicket(TICKET, pos, 1, pos);
		loading.clear();
		candidates.clear();
	}

	private void scan(LevelChunk chunk) {
		caveCache.clear();
		caveBudget[0] = CAVE_NODES_PER_CHUNK;
		ChunkPos cp = chunk.getPos();
		var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		for (var start : chunk.getAllStarts().values()) {
			if (!start.isValid()) continue;
			var bounds = start.getBoundingBox();
			if (!inside(bounds.minX(), bounds.minZ()) || !inside(bounds.maxX(), bounds.maxZ())) continue;
			var id = structures.getKey(start.getStructure());
			if (id != null) facts.structures.merge(id.toString(), 1, Integer::sum);
		}
		int[] surface = new int[256];
		for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
			int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			surface[z * 16 + x] = y;
			int wx = cp.getMinBlockX() + x, wz = cp.getMinBlockZ() + z;
			if (x % 4 == 2 && z % 4 == 2) {
				level.getBiome(new BlockPos(wx, y, wz)).unwrapKey().ifPresent(key -> facts.biomes.add(key.location().getPath()));
				BlockPos floor = new BlockPos(wx, y, wz);
				if (wx >= -150 && wx < 150 && wz >= -150 && wz < 150 && landingSafe(level, floor.above())) landings.add(floor.above());
			}
		}
		var pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < chunk.getSections().length; i++) {
			if (System.nanoTime() >= deadline) return;
			var section = chunk.getSection(i);
			if (!section.maybeHas(BingoSurvey::interesting)) continue;
			int baseY = (chunk.getMinSection() + i) * 16;
			for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
				BlockState state = section.getBlockState(x, y, z);
				Drop drop = drop(state);
				var fluid = state.getFluidState();
				if (drop == null && (!fluid.isSource() || !(fluid.is(Fluids.WATER) || fluid.is(Fluids.LAVA)))) continue;
				pos.set(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z);
				if (reserveEnd && Math.abs(pos.getX()) <= 4 && Math.abs(pos.getZ()) <= 4
					|| reserveNether && Math.abs(pos.getX() - 8) <= 3 && Math.abs(pos.getZ() - 8) <= 3) continue;
				int depth = Math.max(0, surface[z * 16 + x] - pos.getY());
				boolean newFluid = fluid.isSource() && (fluid.is(Fluids.WATER) && !water || fluid.is(Fluids.LAVA) && !lava);
				double distance = Math.hypot(pos.getX() - world.centerX, pos.getZ() - world.centerZ);
				double lower = drop == null ? 0 : 0.8 + distance / 115 + depth / 18.0 + 0.7 * drop.tool().ordinal();
				// Once the count is capped, another identical resource cannot improve
				// the proof unless its best possible effort is lower. Avoid expensive
				// cave searches in that case. New fluid evidence remains independent.
				if (drop != null) {
					Source known = sources.get(drop.item());
					if (known != null && known.count() >= 256 && known.tool() == drop.tool() && lower >= known.cost()) drop = null;
					else if (!loot.accepts(state.getBlock())) drop = null;
				}
				if (drop == null && !newFluid) continue;
				BlockPos approach = approach(pos);
				if (approach == null) continue;
				if (newFluid) {
					if (fluid.is(Fluids.WATER)) water = true;
					else if (fluid.is(Fluids.LAVA)) lava = true;
				}
				if (drop == null) continue;
				boolean dark = level.getBrightness(LightLayer.SKY, approach) < 8 && level.getBrightness(LightLayer.BLOCK, approach) < 8;
				double effort = lower + (dark ? 2.4 : 0);
				Source previous = sources.get(drop.item());
				int count = previous == null ? 1 : Math.min(256, previous.count() + 1);
				if (previous != null && previous.cost() <= effort) {
					sources.put(drop.item(), new Source(count, previous.cost(), previous.hint(), drop.tool()));
				} else {
					String hint = (depth <= 5 ? "Yüzeyde" : (dark ? "Karanlık mağarada" : "Mağarada") + " · Y " + (Math.floorDiv(pos.getY(), 8) * 8) + " civarı")
						+ " · " + region(pos)
						+ (drop.tool() == Tool.HAND ? "" : " · " + drop.tool().label);
					sources.put(drop.item(), new Source(count, effort, hint, drop.tool()));
				}
			}
		}
	}

	/** Bitişik, ayakta durulabilen bir açıklık olmadan damar kart adayı olmaz. */
	private BlockPos approach(BlockPos block) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos feet = block.relative(direction);
			if (accessible(feet)) return feet;
		}
		BlockPos above = block.above();
		return accessible(above) ? above : null;
	}

	/** Sadece yüklü, sınır içi bloklar; küçük kapalı cepler kanıt sayılmaz. */
	private boolean accessible(BlockPos feet) {
		if (!inside(feet.getX(), feet.getZ()) || !canStand(level, feet, false)) return false;
		return caveReachable(level, feet, caveCache, caveBudget, deadline);
	}

	// Bütçe, bir chunk'taki çok sayıdaki cevherin aynı mağarayı tekrar tekrar taramasını sınırlar.
	private static final int CAVE_NODES_PER_CHUNK = 4096;
	private final Map<BlockPos, Boolean> caveCache = new HashMap<>();
	private final int[] caveBudget = {CAVE_NODES_PER_CHUNK};

	/** OP tanı komutu da üretimde kullanılan aynı erişim denetimini çalıştırır. */
	public static boolean debugCaveAccess(ServerLevel level, BlockPos feet) {
		return caveReachable(level, feet, new HashMap<>(), new int[] {CAVE_NODES_PER_CHUNK},
			System.nanoTime() + 20_000_000L);
	}

	private static boolean caveReachable(ServerLevel level, BlockPos feet, Map<BlockPos, Boolean> cache,
			int[] budget, long deadline) {
		if (!inside(level, feet) || !canStand(level, feet, false)) return false;
		Boolean known = cache.get(feet);
		if (known != null) return known;
		if (openSky(level, feet)) return true;
		return CaveReachability.search(feet, cache, budget, deadline,
			pos -> inside(level, pos) && canStand(level, pos, false),
			pos -> openSky(level, pos),
			pos -> clearHead(level, pos));
	}

	private static boolean inside(ServerLevel level, BlockPos pos) {
		return level.getWorldBorder().isWithinBounds(pos.getX() + 0.5, pos.getZ() + 0.5);
	}

	private static boolean openSky(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk != null && pos.getY() > chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15);
	}

	private static boolean clearHead(ServerLevel level, BlockPos pos) {
		if (pos.getY() >= level.getMaxBuildHeight()) return false;
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk != null && open(level, chunk, pos, false);
	}

	private String region(BlockPos pos) {
		int dx = pos.getX() - world.centerX, dz = pos.getZ() - world.centerZ;
		if (Math.abs(dx) < 48 && Math.abs(dz) < 48) return "merkez civarı";
		String northSouth = Math.abs(dz) < 48 ? "" : dz < 0 ? "kuzey" : "güney";
		String eastWest = Math.abs(dx) < 48 ? "" : dx < 0 ? "batı" : "doğu";
		return "merkezin " + northSouth + eastWest + " bölgesi";
	}

	static boolean landingSafe(ServerLevel level, BlockPos feet) {
		return canStand(level, feet, true);
	}

	private static boolean canStand(ServerLevel level, BlockPos feet, boolean airOnly) {
		if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) return false;
		LevelChunk chunk = level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4);
		if (chunk == null || !open(level, chunk, feet, airOnly) || !open(level, chunk, feet.above(), airOnly)) return false;
		BlockPos floor = feet.below();
		BlockState support = chunk.getBlockState(floor);
		return support.isFaceSturdy(level, floor, Direction.UP) && support.getFluidState().isEmpty()
			&& !support.is(Blocks.MAGMA_BLOCK) && !support.is(Blocks.POWDER_SNOW) && !support.is(BlockTags.LEAVES);
	}

	private static boolean open(ServerLevel level, LevelChunk chunk, BlockPos pos, boolean airOnly) {
		BlockState state = chunk.getBlockState(pos);
		return state.isAir() || !airOnly && state.getFluidState().isEmpty()
			&& !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.POWDER_SNOW)
			&& !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.WITHER_ROSE)
			&& state.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean interesting(BlockState state) {
		return DROPS.containsKey(state.getBlock()) || state.is(BlockTags.LOGS) || state.is(BlockTags.WOOL) || !state.getFluidState().isEmpty();
	}

	private static Drop drop(BlockState state) {
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.WOOL)) {
			// A custom tag alone cannot prove a vanilla block drops itself.
			String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			if (name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")
					|| name.endsWith("_hyphae") || name.endsWith("_wool"))
				return new Drop(state.getBlock().asItem(), Tool.HAND);
			return null;
		}
		if (state.getBlock() instanceof CropBlock crop) {
			// Mature wheat seed count may be zero; immature wheat gives one seed.
			if (state.is(Blocks.WHEAT) ? crop.isMaxAge(state) : !crop.isMaxAge(state)) return null;
		}
		return DROPS.get(state.getBlock());
	}

	private static Map<Block, Drop> buildDrops() {
		Map<Block, Drop> map = new HashMap<>();
		map.put(Blocks.GRASS_BLOCK, new Drop(Items.DIRT, Tool.HAND));
		for (Block block : List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.CACTUS, Blocks.SUGAR_CANE,
			Blocks.BAMBOO, Blocks.PUMPKIN, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.LILY_PAD, Blocks.OAK_PLANKS,
			Blocks.ACACIA_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.HAY_BLOCK,
			Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM,
			Blocks.BLUE_ORCHID, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP))
			map.put(block, new Drop(block.asItem(), Tool.HAND));
		for (Block block : List.of(Blocks.COBBLESTONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF,
			Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.MOSSY_COBBLESTONE, Blocks.TERRACOTTA, Blocks.MOSS_BLOCK,
			Blocks.MUD, Blocks.CALCITE, Blocks.POINTED_DRIPSTONE, Blocks.DRIPSTONE_BLOCK))
			map.put(block, new Drop(block.asItem(), block.defaultBlockState().requiresCorrectToolForDrops() ? Tool.WOOD : Tool.HAND));
		map.put(Blocks.STONE, new Drop(Items.COBBLESTONE, Tool.WOOD));
		map.put(Blocks.DEEPSLATE, new Drop(Items.COBBLED_DEEPSLATE, Tool.WOOD));
		map.put(Blocks.CLAY, new Drop(Items.CLAY_BALL, Tool.HAND));
		map.put(Blocks.WHEAT, new Drop(Items.WHEAT_SEEDS, Tool.HAND));
		map.put(Blocks.CARROTS, new Drop(Items.CARROT, Tool.HAND));
		map.put(Blocks.POTATOES, new Drop(Items.POTATO, Tool.HAND));
		map.put(Blocks.COBWEB, new Drop(Items.STRING, Tool.SWORD));
		ore(map, Items.COAL, Tool.WOOD, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
		ore(map, Items.RAW_IRON, Tool.STONE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
		ore(map, Items.RAW_COPPER, Tool.STONE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
		ore(map, Items.RAW_GOLD, Tool.IRON, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
		ore(map, Items.DIAMOND, Tool.IRON, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
		ore(map, Items.REDSTONE, Tool.IRON, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
		ore(map, Items.LAPIS_LAZULI, Tool.STONE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
		ore(map, Items.EMERALD, Tool.IRON, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
		map.put(Blocks.OBSIDIAN, new Drop(Items.OBSIDIAN, Tool.DIAMOND));
		return Map.copyOf(map);
	}

	private static void ore(Map<Block, Drop> map, Item item, Tool tool, Block first, Block second) {
		Drop drop = new Drop(item, tool);
		map.put(first, drop);
		map.put(second, drop);
	}
}
