package tr.kapisma.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/** Her oyun icin Fantasy ile acilan gecici, seed'li overworld. Bitince silinir. */
public final class GameWorld {
	public final RuntimeWorldHandle handle;
	public final ServerLevel level;
	public final long seed;
	public final int centerX;
	public final int centerZ;

	private GameWorld(RuntimeWorldHandle handle, long seed, int cx, int cz) {
		this.handle = handle;
		this.level = handle.asWorld();
		this.seed = seed;
		this.centerX = cx;
		this.centerZ = cz;
	}

	public record Options(boolean naturalRegen, boolean mobSpawn, boolean daylight, boolean keepInventory) {}

	public static GameWorld open(MinecraftServer server, long seed, int cx, int cz, Options o) {
		RuntimeWorldConfig cfg = new RuntimeWorldConfig()
			.setDimensionType(BuiltinDimensionTypes.OVERWORLD)
			.setGenerator(server.overworld().getChunkSource().getGenerator())
			.setSeed(seed)
			.setDifficulty(Difficulty.HARD)
			.setMirrorOverworldGameRules(false)
			.setMirrorOverworldDifficulty(false)
			.setShouldTickTime(true)
			.setTimeOfDay(1000)
			.setGameRule(GameRules.RULE_NATURAL_REGENERATION, o.naturalRegen())
			.setGameRule(GameRules.RULE_DOMOBSPAWNING, o.mobSpawn())
			.setGameRule(GameRules.RULE_DAYLIGHT, o.daylight())
			.setGameRule(GameRules.RULE_KEEPINVENTORY, o.keepInventory())
			.setGameRule(GameRules.RULE_SPAWN_CHUNK_RADIUS, 0)
			.setGameRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS, false)
			.setGameRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN, true);
		RuntimeWorldHandle h = Fantasy.get(server).openTemporaryWorld(cfg);
		GameWorld w = new GameWorld(h, seed, cx, cz);
		w.level.setDefaultSpawnPos(new BlockPos(cx, 80, cz), 0f);
		return w;
	}

	public void setBorder(double radius) {
		WorldBorder b = level.getWorldBorder();
		b.setCenter(centerX + 0.5, centerZ + 0.5);
		b.setSize(radius * 2);
		b.setWarningBlocks(20);
		b.setDamagePerBlock(1.5);
		b.setDamageSafeZone(2);
	}

	public void shrinkBorder(double toRadius, long millis) {
		WorldBorder b = level.getWorldBorder();
		b.lerpSizeBetween(b.getSize(), toRadius * 2, millis);
	}

	public double borderRadius() {
		return level.getWorldBorder().getSize() / 2;
	}

	/** Yuzeyde guvenli (kara, sivi degil) bir nokta; yoksa null. Chunk'i uretir. */
	public BlockPos safeSurface(int x, int z) {
		return safeSurface(level, x, z);
	}

	public static BlockPos safeSurface(ServerLevel level, int x, int z) {
		// Ucuz on eleme: chunk uretmeden jeneratorun tahmini yuksekligine bak (deniz/okyanus ise atla).
		var gen = level.getChunkSource().getGenerator();
		int est = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
		if (est <= gen.getSeaLevel() + 1) return null;
		level.getChunk(x >> 4, z >> 4);
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
		return m != null ? m : new BlockPos(centerX, level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ), centerZ);
	}

	public boolean loaded(int x, int z) {
		return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
	}

	/** Chunk'i arka planda yukletmek/birakmak icin (zorla yuklenen chunk). */
	public void force(int x, int z, boolean on) {
		var cp = new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4);
		var cache = level.getChunkSource();
		// setChunkForced chunk'i senkron yukler; bilet asenkron yukler (sunucu donmaz). Uzaklik 1: merkez + komsular yeterli.
		if (on) cache.addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
		else cache.removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 1, cp);
	}

	public void delete() {
		handle.delete();
	}
}
