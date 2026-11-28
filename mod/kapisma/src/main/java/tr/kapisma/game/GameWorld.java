package tr.kapisma.game;

import java.util.ArrayList;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import tr.kapisma.Kapisma;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/** Her oyun icin Fantasy ile acilan gecici, seed'li overworld. Bitince silinir. */
public final class GameWorld {
	// Only worlds created by Kapisma. Never close vanilla or another mod's dimensions.
	private static final Set<ServerLevel> OWNED_TEMPORARY = Collections.newSetFromMap(new IdentityHashMap<>());
	public final RuntimeWorldHandle handle;
	public final ServerLevel level;
	public final long seed;
	public final int centerX;
	public final int centerZ;
	private final Map<ChunkPos, Integer> forced = new HashMap<>();

	private GameWorld(RuntimeWorldHandle handle, long seed, int cx, int cz) {
		this.handle = handle;
		this.level = handle.asWorld();
		this.seed = seed;
		this.centerX = cx;
		this.centerZ = cz;
	}

	public record Options(boolean naturalRegen, boolean mobSpawn, boolean daylight, boolean keepInventory) {}

	public static GameWorld open(MinecraftServer server, long seed, int cx, int cz, Options o) {
		RuntimeWorldHandle h = openDimension(server, seed, Level.OVERWORLD, BuiltinDimensionTypes.OVERWORLD, o);
		GameWorld w = new GameWorld(h, seed, cx, cz);
		w.level.setDefaultSpawnPos(new BlockPos(cx, 80, cz), 0f);
		return w;
	}

	static RuntimeWorldHandle openDimension(MinecraftServer server, long seed, ResourceKey<Level> template,
			ResourceKey<DimensionType> type, Options o) {
		ServerLevel source = server.getLevel(template);
		if (source == null) throw new IllegalStateException("Boyut üreteci yok: " + template.location());
		RuntimeWorldConfig cfg = new RuntimeWorldConfig()
			.setDimensionType(type)
			.setGenerator(source.getChunkSource().getGenerator())
			.setSeed(seed)
			.setDifficulty(Difficulty.HARD)
			.setMirrorOverworldGameRules(false)
			.setMirrorOverworldDifficulty(false)
			.setShouldTickTime(true)
			.setTimeOfDay(1000)
			.setGameRule(GameRules.RULE_NATURAL_REGENERATION, o.naturalRegen())
			.setGameRule(GameRules.RULE_DOMOBSPAWNING, o.mobSpawn())
			.setGameRule(GameRules.RULE_DAYLIGHT, o.daylight())
			.setGameRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE, 0)
			.setGameRule(GameRules.RULE_KEEPINVENTORY, o.keepInventory())
			.setGameRule(GameRules.RULE_SPAWN_CHUNK_RADIUS, 0)
			.setGameRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS, false)
			.setGameRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN, true);
		RuntimeWorldHandle handle = Fantasy.get(server).openTemporaryWorld(cfg);
		OWNED_TEMPORARY.add(handle.asWorld());
		return handle;
	}

	/** Fantasy 0.6.4 fires UNLOAD after registry removal, before deleting its directory.
	 * Flush/close vanilla IO workers here: an unloaded chunk count is not an IO barrier.
	 */
	public static void onWorldUnload(MinecraftServer server, ServerLevel level) {
		if (!OWNED_TEMPORARY.remove(level)) return;
		long start = System.nanoTime();
		try {
			level.close();
			Kapisma.LOG.info("Arena depolama kapandı: {} ({} ms)", level.dimension().location(),
				(System.nanoTime() - start) / 1_000_000L);
		} catch (IOException | RuntimeException ex) {
			Kapisma.LOG.error("Arena depolama güvenli kapatılamadı: {}", level.dimension().location(), ex);
		}
	}

	public void setBorder(double radius) {
		setBorder(level, centerX + 0.5, centerZ + 0.5, radius);
	}

	static void setBorder(ServerLevel level, double x, double z, double radius) {
		WorldBorder b = level.getWorldBorder();
		b.setCenter(x, z);
		b.setSize(radius * 2);
		b.setWarningBlocks(20);
		b.setDamagePerBlock(1.5);
		b.setDamageSafeZone(2);
	}

	public void shrinkBorder(double toRadius, long millis) {
		WorldBorder b = level.getWorldBorder();
		b.lerpSizeBetween(b.getSize(), toRadius * 2, millis);
	}

	// Diagnostic only: opt in before launching the JVM; no world state changes.
	private static final boolean ULTRA_TIMING = "1".equals(System.getenv("KAPISMA_ULTRA_TIMING"));

	static long timingStart() { return ULTRA_TIMING ? System.nanoTime() : 0L; }

	static void timingEnd(String phase, long start, long minMillis) {
		if (!ULTRA_TIMING) return;
		long elapsed = System.nanoTime() - start;
		if (elapsed >= minMillis * 1_000_000L)
			Kapisma.LOG.info("ULTRA_TIMING phase={} elapsed_ns={}", phase, elapsed);
	}

	static ItemStack centeredMap(ServerLevel level, int x, int z, byte scale, boolean unlimitedTracking) {
		ItemStack map = createCenteredMap(level, x, z, scale, unlimitedTracking);
		long timing = timingStart();
		try {
			MapItem.renderBiomePreviewMap(level, map);
		} finally {
			timingEnd("map.biome_preview", timing, 0);
		}
		return map;
	}

	static ItemStack createCenteredMap(ServerLevel level, int x, int z, byte scale, boolean unlimitedTracking) {
		long timing = timingStart();
		ItemStack map = MapItem.create(level, x, z, scale, true, unlimitedTracking);
		MapItemSavedData data = MapItem.getSavedData(map, level);
		if (data != null && (data.centerX != x || data.centerZ != z)) {
			// Vanilla ortalamasını maç veya hazine konumuna taşı.
			var registries = level.registryAccess();
			var tag = data.save(new CompoundTag(), registries);
			tag.putInt("xCenter", x);
			tag.putInt("zCenter", z);
			level.setMapData(map.get(DataComponents.MAP_ID), MapItemSavedData.load(tag, registries));
		}
		timingEnd("map.create", timing, 0);
		return map;
	}

	public double borderRadius() {
		return level.getWorldBorder().getSize() / 2;
	}

	/** Yuzeyde guvenli (kara, sivi degil) bir nokta; yoksa null. Chunk'i uretir. */
	public BlockPos safeSurface(int x, int z) {
		return safeSurface(level, x, z);
	}

	public static BlockPos safeSurface(ServerLevel level, int x, int z) {
		if (!aboveEstimatedSea(level, x, z)) return null;
		level.getChunk(x >> 4, z >> 4);
		return checkSurface(level, x, z);
	}

	private static boolean aboveEstimatedSea(ServerLevel level, int x, int z) {
		var gen = level.getChunkSource().getGenerator();
		return gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()) > gen.getSeaLevel() + 1;
	}

	/** Only for pinned, already FULL chunks on the server thread. A missing chunk never loads synchronously.
	 * estimatedLand must originate from candidate() for the same world and coordinates. */
	static BlockPos safeSurfaceLoaded(ServerLevel level, int x, int z, boolean estimatedLand) {
		if (!level.getServer().isSameThread()) throw new IllegalStateException("surface requires server thread");
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
		if (!estimatedLand && !aboveEstimatedSea(level, x, z)) return null;
		return checkSurface(level, x, z);
	}

	private static BlockPos checkSurface(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);
		BlockPos below = feet.below();
		if (y <= level.getMinBuildHeight() + 1) return null;
		var bs = level.getBlockState(below);
		if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(below).isEmpty()) return null;
		if (bs.isAir() || bs.is(net.minecraft.tags.BlockTags.LEAVES) || bs.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
			|| bs.is(net.minecraft.world.level.block.Blocks.CACTUS) || bs.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return null;
		return feet;
	}

	/** Dagitim araligi: takimlar arasi en az bu kadar uzaklik (yaricap ve takim sayisina gore). */
	public static double minSepFor(double radius, int n) {
		return radius <= 0 ? 0 : Math.max(30, radius / Math.sqrt(Math.max(1, n)));
	}

	/** Chunk uretmeden aday baslangic noktasi: radius<=0 ise merkez civari, degilse merkezli kare icinde rastgele. */
	public int[] candidate(double radius, Random rnd, List<int[]> taken, double minSep) {
		long timing = timingStart();
		try {
			return findCandidate(radius, rnd, taken, minSep);
		} finally {
			timingEnd("arena.candidate", timing, 5);
		}
	}

	static SpawnCandidateWork candidateWork(int cx, int cz, double radius, Random rnd, List<int[]> taken, double minSep) {
		return new SpawnCandidateWork(cx, cz, radius, rnd, taken, minSep);
	}

	static int advanceCandidate(ServerLevel level, SpawnCandidateWork work) {
		if (!level.getServer().isSameThread()) throw new IllegalStateException("candidate requires server thread");
		long timing = timingStart();
		try {
			return work.step(8, 5_000_000L, System::nanoTime, (x,z) -> aboveEstimatedSea(level, x, z));
		} finally {
			timingEnd("arena.candidate_slice", timing, 5);
		}
	}

	private int[] findCandidate(double radius, Random rnd, List<int[]> taken, double minSep) {
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		for (int tries = 0; tries < 200; tries++) {
			int x, z;
			if (radius <= 0) {
				x = centerX + rnd.nextInt(17) - 8;
				z = centerZ + rnd.nextInt(17) - 8;
			} else {
				int r = (int) radius;
				x = centerX + rnd.nextInt(2 * r + 1) - r;
				z = centerZ + rnd.nextInt(2 * r + 1) - r;
			}
			double sep = minSep * (tries < 100 ? 1.0 : tries < 150 ? 0.6 : 0.3);
			boolean far = true;
			for (int[] t : taken) {
				double dx = t[0] - x, dz = t[1] - z;
				if (dx * dx + dz * dz < sep * sep) { far = false; break; }
			}
			if (!far) continue;
			if (gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) > gen.getSeaLevel() + 1) return new int[] {x, z};
		}
		return null;
	}

	/** n takim icin baslangic noktalari (anlik uretimle). radius<=0: hepsi merkez civarinda. */
	public List<BlockPos> scatter(int n, double radius, Random rnd) {
		List<BlockPos> out = new ArrayList<>();
		List<int[]> taken = new ArrayList<>();
		double minSep = minSepFor(radius, n);
		for (int i = 0; i < n; i++) {
			BlockPos found = null;
			for (int tries = 0; tries < 30 && found == null; tries++) {
				int[] c = candidate(radius, rnd, taken, minSep);
				if (c == null) break;
				found = safeSurface(c[0], c[1]);
				if (found != null) taken.add(c);
			}
			if (found == null) {                    // son care: merkez
				found = safeSurface(centerX, centerZ);
				if (found == null) found = new BlockPos(centerX, level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ), centerZ);
			}
			out.add(found);
		}
		return out;
	}

	/** Merkezdeki guvenli nokta (yoksa yuzey yuksekligi): zaman asiminda yedek baslangic. */
	public BlockPos centerSpot() {
		BlockPos m = safeSurface(centerX, centerZ);
		if (m != null) return m;
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
		BlockPos feet = new BlockPos(centerX, Math.min(top + 1, level.getMaxBuildHeight() - 2), centerZ);
		level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
		return feet;
	}

	public boolean loaded(int x, int z) {
		return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
	}

	/** Chunk'i arka planda yukletmek/birakmak icin (zorla yuklenen chunk). */
	public void force(int x, int z, boolean on) {
		var cp = new ChunkPos(x >> 4, z >> 4);
		var cache = level.getChunkSource();
		// setChunkForced chunk'i senkron yukler; bilet asenkron yukler (sunucu donmaz). Uzaklik 1: merkez + komsular yeterli.
		int count = forced.getOrDefault(cp, 0);
		if (on) {
			forced.put(cp, count + 1);
			if (count == 0) cache.addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
		} else if (count == 1) {
			forced.remove(cp);
			cache.removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
		} else if (count > 1) forced.put(cp, count - 1);
	}

	public void delete() {
		for (ChunkPos pos : forced.keySet()) level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, pos, 1, pos);
		forced.clear();
		handle.delete();
	}
}
