package tr.kapisma.xray;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import tr.kapisma.Kapisma;

/**
 * Paketlerde tamamen kapalı vanilla cevherleri taşla değiştirir. Açık cevher,
 * mağara havası ve diğer blok durumları gerçek hâlleriyle gönderilir.
 */
public final class Xray {
	private Xray() {}

	// ---------------- ayarlar ----------------
	public static volatile int revealRadius = 10;
	/** Oyuncu başına tek turda açılacak en çok hücre (düşük donanım profilinde küçülür). */
	public static volatile int bfsCap = 2500;
	/** Test için: null değilse oyun durumuna bakmadan bu modu uygular. */
	public static volatile Integer forceMode = null;
	public static volatile long salt = 0x9E3779B97F4A7C15L;

	// ---------------- istatistik ----------------
	public static long statPackets, statPacketCells, statSent, statRuns, statChunks, statHidden, statHiddenOres, statFakes, statNanos, statReveals, statRevealCells, statRevealNanos;
	// Opt-in until real-client performance and parity are established.
	private static final boolean EMPTY_REVEAL_SKIP = "1".equals(System.getenv("KAPISMA_XRAY_EMPTY_SKIP"));
	public static long statEmptyRevealSkips;

	// ---------------- blok sınıflandırma ----------------
	private static final byte ORE = 1, NONOPQ = 2;
	private static byte[] FLAGS;

	private static synchronized void init() {
		if (FLAGS != null) return;
		byte[] flags = new byte[Block.BLOCK_STATE_REGISTRY.size()];
		for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
			var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
			String path = key.getPath();
			int id = Block.getId(state);
			if (key.getNamespace().equals("minecraft") && (path.endsWith("_ore") || path.equals("ancient_debris"))) flags[id] |= ORE;
			if (!state.canOcclude()) flags[id] |= NONOPQ;
		}
		FLAGS = flags;
	}

	private static byte flags(BlockState state) {
		return FLAGS[Block.getId(state)];
	}

	// ---------------- mod belirleme ----------------
	/** 0 kapalı, 1 gömülü cevherleri gizle. Eski ayarlardaki 2 de 1 olarak çalışır. */
	public static int modeNow(ServerLevel sl) {
		Integer f = forceMode;
		if (f != null) return Math.min(f, 1);
		var gm = Kapisma.GM;
		if (gm == null || gm.current == null) return 0;
		return Math.min(gm.current.xrayMode(sl), 1);
	}

	// ---------------- chunk üstverisi ----------------
	/** Son gönderimde gerçek durumdan farklı gösterilen hücreler (bölüm başına 4096 bit). */
	static final class Meta {
		final int minSec;
		final BitSet[] bits;
		Meta(int minSec, int n) { this.minSec = minSec; this.bits = new BitSet[n]; }
		boolean get(int lx, int y, int lz) {
			int si = (y >> 4) - minSec;
			if (si < 0 || si >= bits.length) return false;
			BitSet b = bits[si];
			return b != null && b.get(((y & 15) << 8) | (lz << 4) | lx);
		}
		/** Conservative vertical intersection; no hidden bit in any intersecting
		 * section means the BFS cannot publish a reveal in that section. */
		boolean hasHiddenSections(int lowY, int highY) {
			int first = Math.max(0, (lowY >> 4) - minSec);
			int last = Math.min(bits.length - 1, (highY >> 4) - minSec);
			for (int si = first; si <= last; si++) {
				BitSet b = bits[si];
				if (b != null && !b.isEmpty()) return true;
			}
			return false;
		}
		int count() {
			int c = 0;
			for (BitSet b : bits) if (b != null) c += b.cardinality();
			return c;
		}
	}

	/** Chunk basina: son ustveri + sonuc onbellegi (8 oyuncu ayni chunk'i alinca bir kez hesaplanir). */
	static final class Info {
		Meta meta;
		int version, builtVersion = -1, builtMode = -1, builtTick;
		Built built;
	}

	private static volatile int tickNow;
	private static final Map<LevelChunk, Info> INFOS = Collections.synchronizedMap(new WeakHashMap<>());

	public record Built(byte[] buffer, Meta fresh) {
		/** Paketteki bu hücre gerçekten gizlenmiş mi (blok varlığı süzmek için)? */
		public boolean isHidden(int lx, int y, int lz) { return fresh.get(lx, y, lz); }
	}

	// ---------------- taş türü için deterministik karıştırıcı ----------------
	private static long mix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static long hash(long a, long b, long c) {
		return mix(salt ^ mix(a * 31 + b) ^ mix(c + 0x1234567L));
	}

	// Hash is only used by the transition band. Keep every replacement identical.
	static BlockState replacement(boolean nether, int x, int y, int z) {
		return host(nether, y, !nether && y >= 0 && y < 8 ? hash(x, y, z) : 0L);
	}

	private static BlockState host(boolean nether, int y, long h) {
		if (nether) return Blocks.NETHERRACK.defaultBlockState();
		if (y < 0) return Blocks.DEEPSLATE.defaultBlockState();
		if (y >= 8) return Blocks.STONE.defaultBlockState();
		return ((h >>> 20) & 7) < (8 - y) ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	// ---------------- paket üretimi ----------------
	private static final class Ctx {
		final LevelChunk[] nb = new LevelChunk[5];   // 0 kendi, 1 -x, 2 +x, 3 -z, 4 +z
		final int minY, maxY, minSec;

		Ctx(ServerLevel sl, LevelChunk c) {
			minY = c.getMinBuildHeight();
			maxY = minY + c.getHeight();
			minSec = c.getMinSection();
			int cx = c.getPos().x, cz = c.getPos().z;
			nb[0] = c;
			nb[1] = sl.getChunkSource().getChunkNow(cx - 1, cz);
			nb[2] = sl.getChunkSource().getChunkNow(cx + 1, cz);
			nb[3] = sl.getChunkSource().getChunkNow(cx, cz - 1);
			nb[4] = sl.getChunkSource().getChunkNow(cx, cz + 1);
		}

		private int chunkIdx(int lx, int lz) {
			if (lx >= 0 && lx < 16) {
				if (lz >= 0 && lz < 16) return 0;
				return lz < 0 ? 3 : 4;
			}
			if (lz < 0 || lz > 15) return -1;   // çapraz komşu yok
			return lx < 0 ? 1 : 2;
		}

		/** Gerçek blok durumu; bilinmiyorsa null. */
		BlockState get(int lx, int y, int lz) {
			if (y < minY || y >= maxY) return null;
			int i = chunkIdx(lx, lz);
			if (i < 0) return null;
			LevelChunk c = nb[i];
			if (c == null) return null;
			LevelChunkSection sec = c.getSection((y >> 4) - minSec);
			if (sec.hasOnlyAir()) return Blocks.AIR.defaultBlockState();
			return sec.getBlockState(lx & 15, y & 15, lz & 15);
		}

		boolean visibleOpen(int lx, int y, int lz) {
			BlockState state = get(lx, y, lz);
			return state == null || (flags(state) & NONOPQ) != 0;
		}

		boolean exposed(int lx, int y, int lz) {
			return visibleOpen(lx, y + 1, lz) || visibleOpen(lx, y - 1, lz) || visibleOpen(lx - 1, y, lz) || visibleOpen(lx + 1, y, lz)
				|| visibleOpen(lx, y, lz - 1) || visibleOpen(lx, y, lz + 1);
		}
	}

	/**
	 * Bagimsiz kopya. PalettedContainer.copy() paleti kopyalar ama paletin "buyut" islevi ORIJINAL konteyneri gosterir:
	 * kopyaya palet kapasitesini asan (16., 32. ... farkli blok) bir deger yazinca indeks kopyanin bit genisligine sigmaz
	 * ("The value 16 is not in the specified inclusive range of 0 to 15") ve gercek bolum de bozulur.
	 * Pakete yazip yeni konteynere okumak kendi buyutme islevini kurar.
	 */
	static PalettedContainer<BlockState> independentCopy(PalettedContainer<BlockState> src) {
		FriendlyByteBuf tmp = new FriendlyByteBuf(Unpooled.buffer(2048));
		try {
			src.write(tmp);
			PalettedContainer<BlockState> c = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
			c.read(tmp);
			return c;
		} finally {
			tmp.release();
		}
	}

	/** Test: bagimsiz kopyaya palet kapasitesini asacak kadar farkli blok yazilir; hata yok, orijinal bolum degismemis olmali. */
	public static String copyTest(ServerPlayer p) {
		LevelChunk ch = p.level().getChunkAt(p.blockPosition());
		LevelChunkSection sec = ch.getSection(ch.getSectionIndex(p.getBlockY()));
		PalettedContainer<BlockState> orig = sec.getStates();
		BlockState[] before = new BlockState[4096];
		for (int i = 0; i < 4096; i++) before[i] = orig.get(i & 15, (i >> 8) & 15, (i >> 4) & 15);
		PalettedContainer<BlockState> c = independentCopy(orig);
		Block[] pool = {Blocks.STONE, Blocks.DEEPSLATE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
			Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.COPPER_ORE, Blocks.DIRT, Blocks.GRAVEL, Blocks.SAND, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
			Blocks.TUFF, Blocks.CLAY, Blocks.NETHERRACK, Blocks.OBSIDIAN, Blocks.GLASS, Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.JUNGLE_LOG,
			Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MOSS_BLOCK, Blocks.CALCITE, Blocks.AMETHYST_BLOCK, Blocks.SNOW_BLOCK, Blocks.ICE, Blocks.PACKED_ICE,
			Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.BRICKS, Blocks.SANDSTONE, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.MUD};
		try {
			for (int i = 0; i < pool.length; i++) c.set(i & 15, (i >> 4) & 15, 7, pool[i].defaultBlockState());
		} catch (RuntimeException e) {
			return "copytest HATA: " + e;
		}
		for (int i = 0; i < 4096; i++) {
			if (orig.get(i & 15, (i >> 8) & 15, (i >> 4) & 15) != before[i]) return "copytest HATA: orijinal bolum degisti";
		}
		// bilgi: eski yontem (copy()) ayni yazimda cokuyor mu? (teshis dogrulamasi; kopya yeni konteyner, orijinal etkilenmez diye ayri bolum kullanilir)
		String vanilla;
		try {
			PalettedContainer<BlockState> v = independentCopy(orig).copy();
			for (int i = 0; i < pool.length; i++) v.set(i & 15, (i >> 4) & 15, 7, pool[i].defaultBlockState());
			vanilla = "ok";
		} catch (RuntimeException e) {
			vanilla = "COKTU";
		}
		return "copytest OK (" + pool.length + " blok, orijinal degismedi; eski copy() yontemi: " + vanilla + ")";
	}

	/** Chunk paket verisini yeniden üretir; koruma kapalıysa null. */
	public static Built build(LevelChunk chunk) {
		return build(chunk, true);
	}

	static Meta metaOf(LevelChunk c) {
		Info i = INFOS.get(c);
		return i == null ? null : i.meta;
	}

	static Built build(LevelChunk chunk, boolean useCache) {
		if (!(chunk.getLevel() instanceof ServerLevel sl)) return null;
		int mode = modeNow(sl);
		if (mode <= 0) return null;
		if (FLAGS == null) init();
		Info info = INFOS.computeIfAbsent(chunk, k -> new Info());
		if (useCache && info.built != null && info.builtVersion == info.version && info.builtMode == mode) return info.built;
		int verAtStart = info.version;
		long t0 = System.nanoTime();
		Ctx cx = new Ctx(sl, chunk);
		boolean nether = sl.dimensionType().ultraWarm();
		LevelChunkSection[] secs = chunk.getSections();
		int n = secs.length;
		PalettedContainer<BlockState>[] displays = newArr(n);
		int[] nonEmpty = new int[n];
		Meta meta = new Meta(cx.minSec, n);
		int ox = chunk.getPos().getMinBlockX(), oz = chunk.getPos().getMinBlockZ();
		long hiddenTotal = 0, oresTotal = 0;

		for (int si = 0; si < n; si++) {
			LevelChunkSection sec = secs[si];
			if (sec.hasOnlyAir()) continue;
			int baseY = (si + cx.minSec) << 4;
			if (!sec.maybeHas(state -> (flags(state) & ORE) != 0)) continue;
			PalettedContainer<BlockState> states = sec.getStates();
			PalettedContainer<BlockState> disp = null;
			BitSet bits = null;
			for (int ly = 0; ly < 16; ly++) {
				int y = baseY + ly;
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						BlockState s = states.get(lx, ly, lz);
						if ((flags(s) & ORE) == 0 || cx.exposed(lx, y, lz)) continue;
						int idx = (ly << 8) | (lz << 4) | lx;
						if (disp == null) { disp = independentCopy(states); bits = new BitSet(4096); }
						disp.set(lx, ly, lz, replacement(nether, ox + lx, y, oz + lz));
						bits.set(idx);
						hiddenTotal++;
						oresTotal++;
					}
				}
			}
			if (disp == null) continue;
			displays[si] = disp;
			meta.bits[si] = bits;
			int[] cnt = {0};
			disp.count((st, c) -> { if (!st.isAir()) cnt[0] += c; });
			nonEmpty[si] = cnt[0];
		}

		// ---- serileştir ----
		FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer(32768));
		byte[] arr;
		try {
			for (int si = 0; si < n; si++) {
				if (displays[si] == null) secs[si].write(out);
				else {
					out.writeShort(nonEmpty[si]);
					displays[si].write(out);
					secs[si].getBiomes().write(out);
				}
			}
			arr = new byte[out.readableBytes()];
			out.readBytes(arr);
		} finally {
			out.release();
		}

		// ---- önceki üstveriyle birleştir (bir istemci eski görüntüyü almış olabilir) ----
		Meta old = info.meta;
		Meta merged = meta;
		if (old != null && old.bits.length == n) {
			merged = new Meta(cx.minSec, n);
			for (int si = 0; si < n; si++) {
				BitSet a = meta.bits[si], b = old.bits[si];
				if (a == null && b == null) continue;
				BitSet m = a == null ? (BitSet) b.clone() : (BitSet) a.clone();
				if (a != null && b != null) m.or(b);
				merged.bits[si] = m;
			}
		}
		info.meta = merged;

		statChunks++;
		statHidden += hiddenTotal;
		statHiddenOres += oresTotal;
		statNanos += System.nanoTime() - t0;
		Built result = new Built(arr, meta);
		boolean complete = cx.nb[1] != null && cx.nb[2] != null && cx.nb[3] != null && cx.nb[4] != null;
		if (useCache && complete) { info.built = result; info.builtVersion = verAtStart; info.builtMode = mode; info.builtTick = tickNow; }
		else info.built = null;
		return result;
	}

	@SuppressWarnings("unchecked")
	private static PalettedContainer<BlockState>[] newArr(int n) {
		return (PalettedContainer<BlockState>[]) new PalettedContainer[n];
	}

	/** Paketteki blok varlığı bu hücrede gizlenmiş mi? */
	public static boolean hiddenAt(Meta m, int lx, int y, int lz) {
		return m != null && m.get(lx, y, lz);
	}

	// ---------------- oyuncu başına izleme / açığa çıkarma ----------------
	static final class PState {
		ServerLevel level;
		final LongOpenHashSet sent = new LongOpenHashSet();
		final Long2ObjectOpenHashMap<LongOpenHashSet> revealed = new Long2ObjectOpenHashMap<>();
		boolean dirty;
		int lastRun = -100;
		double x, y, z;
	}

	private static final Map<UUID, PState> STATES = new java.util.HashMap<>();
	private static final Map<ServerLevel, List<BlockPos>> CHANGES = new IdentityHashMap<>();
	private static final Map<ServerLevel, Integer> MODE_CACHE = new IdentityHashMap<>();

	private static PState state(ServerPlayer p) {
		return STATES.computeIfAbsent(p.getUUID(), u -> new PState());
	}

	public static void onChunkSent(ServerPlayer p, ServerLevel level, LevelChunk chunk) {
		if (Kapisma.GM == null) return;
		PState ps = state(p);
		if (ps.level != level) {
			ps.level = level;
			ps.sent.clear();
			ps.revealed.clear();
		}
		statSent++;
		long k = chunk.getPos().toLong();
		ps.sent.add(k);
		ps.revealed.remove(k);
		ps.dirty = true;
	}

	public static void onChunkDropped(ServerPlayer p, ChunkPos pos) {
		PState ps = STATES.get(p.getUUID());
		if (ps == null) return;
		long k = pos.toLong();
		ps.sent.remove(k);
		ps.revealed.remove(k);
	}

	public static void onBlockChanged(ServerLevel level, BlockPos pos) {
		Integer m = MODE_CACHE.get(level);
		if (m == null || m <= 0) return;
		List<BlockPos> l = CHANGES.computeIfAbsent(level, k -> new ArrayList<>());
		if (l.size() < 512) l.add(pos.immutable());
		// onbellegi gecersiz kil: bu chunk (ve sinirdaysa komsusu)
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4, lx = pos.getX() & 15, lz = pos.getZ() & 15;
		bump(level, cx, cz);
		if (lx == 0) bump(level, cx - 1, cz);
		if (lx == 15) bump(level, cx + 1, cz);
		if (lz == 0) bump(level, cx, cz - 1);
		if (lz == 15) bump(level, cx, cz + 1);
	}

	private static void bump(ServerLevel level, int cx, int cz) {
		LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
		if (c == null) return;
		Info i = INFOS.get(c);
		if (i != null) i.version++;
	}

	public static void onDisconnect(ServerPlayer p) {
		STATES.remove(p.getUUID());
	}

	/** Her sunucu tikinde: kirli oyuncular için BFS ile gerçek görüntüyü açığa çıkarır. */
	public static void tick(MinecraftServer server) {
		try {
			tick0(server);
		} catch (Throwable t) {
			if (errors++ < 5) Kapisma.LOG.error("anti-xray tick hatasi", t);
		}
	}

	private static int errors;

	private static void tick0(MinecraftServer server) {
		tickNow = server.getTickCount();
		if (tickNow % 400 == 0) {
			// 30 sn'den eski onbellek paketlerini birak (bellek)
			synchronized (INFOS) {
				for (Info i : INFOS.values()) if (i.built != null && tickNow - i.builtTick > 600) i.built = null;
			}
		}
		if (FLAGS == null) {
			if (Kapisma.GM == null || Kapisma.GM.current == null) return;
			init();
		}
		MODE_CACHE.clear();
		for (ServerLevel sl : server.getAllLevels()) {
			int m = modeNow(sl);
			if (m > 0) MODE_CACHE.put(sl, m);
		}
		CHANGES.keySet().retainAll(MODE_CACHE.keySet());
		if (MODE_CACHE.isEmpty()) { CHANGES.clear(); return; }
		int t = server.getTickCount();
		int near = revealRadius + 6;
		// blok değişimi yakındaki oyuncuyu kirletir
		for (var e : CHANGES.entrySet()) {
			List<BlockPos> l = e.getValue();
			if (l.isEmpty()) continue;
			for (ServerPlayer p : e.getKey().players()) {
				PState ps = STATES.get(p.getUUID());
				if (ps == null || ps.dirty) continue;
				BlockPos playerPos = p.blockPosition();
				for (BlockPos b : l) {
					if (b.distSqr(playerPos) <= (double) near * near) { ps.dirty = true; break; }
				}
			}
			l.clear();
		}
		int budget = 6;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerLevel sl = p.serverLevel();
			Integer mode = MODE_CACHE.get(sl);
			if (mode == null) continue;
			PState ps = STATES.get(p.getUUID());
			if (ps == null || ps.level != sl) continue;
			double d2 = p.distanceToSqr(ps.x, ps.y, ps.z);
			if (!ps.dirty && d2 < 9.0) continue;
			boolean fast = d2 >= 64.0 || p.getDeltaMovement().lengthSqr() > 0.25;   // dusme/ucma/isinlanma: beklemeden
			if (!fast && t - ps.lastRun < (ps.dirty ? 1 : 4)) continue;
			if (budget-- <= 0) break;
			long t0 = System.nanoTime();
			statRuns++;
			revealAround(sl, p, ps);
			statRevealNanos += System.nanoTime() - t0;
			ps.dirty = false;
			ps.lastRun = t;
			ps.x = p.getX(); ps.y = p.getY(); ps.z = p.getZ();
		}
	}

	private static final LongOpenHashSet VISITED = new LongOpenHashSet();
	private static final LongArrayList QUEUE = new LongArrayList();
	private static final int[][] BFS_DIRECTIONS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** Çıktı: açığa çıkarılan hücreler (bölüm anahtarı -> kısa konumlar). */
	private static Map<Long, ShortOpenHashSet> bfs(ServerLevel sl, PState ps, BlockPos origin, int radius) {
		Map<Long, ShortOpenHashSet> batches = new java.util.HashMap<>();
		VISITED.clear();
		QUEUE.clear();
		BlockPos feet = origin;
		VISITED.add(feet.asLong());
		QUEUE.add(feet.asLong());
		BlockPos head = feet.above();
		if (VISITED.add(head.asLong())) QUEUE.add(head.asLong());
		int head_i = 0;
		var src = sl.getChunkSource();
		long cacheKey = Long.MIN_VALUE;
		LevelChunk cacheChunk = null;
		Meta cacheMeta = null;
		boolean cacheSent = false;
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		int cap = bfsCap;
		int cells = 0;
		int minY = sl.getMinBuildHeight(), maxY = sl.getMaxBuildHeight();
		int[][] dirs = BFS_DIRECTIONS;
		while (head_i < QUEUE.size() && cells < cap) {
			long cl = QUEUE.getLong(head_i++);
			int x = BlockPos.getX(cl), y = BlockPos.getY(cl), z = BlockPos.getZ(cl);
			cells++;
			// bu hücrenin kendisi
			for (int d = -1; d < 6; d++) {
				if (d == -1 && head_i > 2) continue;   // hücrenin kendisi sadece tohumlar için
				int nx = x, ny = y, nz = z;
				if (d >= 0) { nx += dirs[d][0]; ny += dirs[d][1]; nz += dirs[d][2]; }
				if (ny < minY || ny >= maxY) continue;
				if (d >= 0) {
					if (Math.max(Math.abs(nx - feet.getX()), Math.max(Math.abs(ny - feet.getY()), Math.abs(nz - feet.getZ()))) > radius) continue;
					if (!VISITED.add(BlockPos.asLong(nx, ny, nz))) continue;
				}
				long ck = ChunkPos.asLong(nx >> 4, nz >> 4);
				if (ck != cacheKey) {
					cacheChunk = src.getChunkNow(nx >> 4, nz >> 4);
					cacheKey = ck;
					cacheMeta = cacheChunk == null ? null : metaOf(cacheChunk);
					cacheSent = ps.sent.contains(ck);
				}
				if (cacheChunk == null) continue;
				LevelChunkSection sec = cacheChunk.getSection((ny >> 4) - cacheChunk.getMinSection());
				BlockState s = sec.hasOnlyAir() ? Blocks.AIR.defaultBlockState() : sec.getBlockState(nx & 15, ny & 15, nz & 15);
				Meta m = cacheMeta;
				boolean open = (flags(s) & NONOPQ) != 0;
				if (m != null && cacheSent && m.get(nx & 15, ny, nz & 15)) {
					LongOpenHashSet rv = ps.revealed.computeIfAbsent(ck, k -> new LongOpenHashSet());
					if (rv.add(BlockPos.asLong(nx, ny, nz))) {
						batches.computeIfAbsent(SectionPos.asLong(nx >> 4, ny >> 4, nz >> 4), k -> new ShortOpenHashSet())
							.add(SectionPos.sectionRelativePos(mp.set(nx, ny, nz)));
					}
				}
				if (d >= 0 && open) QUEUE.add(BlockPos.asLong(nx, ny, nz));
			}
		}
		statRevealCells += cells;
		return batches;
	}

	/**
	 * A necessary (not sufficient) condition for any reveal: an already-sent,
	 * currently loaded chunk has hidden metadata in the scan's vertical band.
	 * Include both feet and head seeds, even for radius 0. No chunk generation,
	 * no changes to sent/revealed sets, no stale cache of the decision.
	 * The original BFS may cross unsent chunks, so never restrict its traversal.
	 */
	static boolean mayReveal(ServerLevel sl, PState ps, BlockPos origin, int radius) {
		if (!sl.getServer().isSameThread() || ps.level != sl || radius < 0) return true;
		int lowY = Math.max(sl.getMinBuildHeight(), origin.getY() - radius);
		int highY = Math.min(sl.getMaxBuildHeight() - 1, origin.getY() + Math.max(radius, 1));
		if (lowY > highY) return false;
		var src = sl.getChunkSource();
		for (int cx = (origin.getX() - radius) >> 4; cx <= (origin.getX() + radius) >> 4; cx++) {
			for (int cz = (origin.getZ() - radius) >> 4; cz <= (origin.getZ() + radius) >> 4; cz++) {
				if (!ps.sent.contains(ChunkPos.asLong(cx, cz))) continue;
				LevelChunk c = src.getChunkNow(cx, cz);
				if (c == null) continue;
				Meta m = metaOf(c);
				if (m != null && m.hasHiddenSections(lowY, highY)) return true;
			}
		}
		return false;
	}

	static Map<Long, ShortOpenHashSet> revealBatches(ServerLevel sl, PState ps, BlockPos origin, int radius, boolean skip) {
		if (skip && !mayReveal(sl, ps, origin, radius)) {
			statEmptyRevealSkips++;
			return Collections.emptyMap();
		}
		return bfs(sl, ps, origin, radius);
	}

	private static void revealAround(ServerLevel sl, ServerPlayer p, PState ps) {
		var batches = revealBatches(sl, ps, p.blockPosition(), 6, EMPTY_REVEAL_SKIP);
		if (batches.isEmpty()) return;
		statReveals++;
		var src = sl.getChunkSource();
		for (var e : batches.entrySet()) {
			SectionPos sp = SectionPos.of(e.getKey());
			LevelChunk c = src.getChunkNow(sp.x(), sp.z());
			if (c == null) continue;
			LevelChunkSection sec = c.getSection(sp.y() - c.getMinSection());
			p.connection.send(new ClientboundSectionBlocksUpdatePacket(sp, e.getValue(), sec));
			statPackets++;
			statPacketCells += e.getValue().size();
			for (short s : e.getValue()) {
				BlockPos bp = sp.relativeToBlockPos(s);
				BlockState st = sec.getBlockState(bp.getX() & 15, bp.getY() & 15, bp.getZ() & 15);
				if (st.hasBlockEntity()) {
					BlockEntity be = c.getBlockEntity(bp);
					if (be != null) {
						Packet<?> up = be.getUpdatePacket();
						if (up != null) p.connection.send(up);
					}
				}
			}
		}
	}

	/** Test: oyuncunun chunk'ında yeraltında bir mağara hücresi arar. */
	public static BlockPos findCave(ServerPlayer p) {
		ServerLevel sl = p.serverLevel();
		LevelChunk c = p.level().getChunkAt(p.blockPosition());
		if (FLAGS == null) init();
		ChunkPos cp = c.getPos();
		for (int y = sl.getMinBuildHeight() + 2; y < sl.getMaxBuildHeight() - 2; y++) {
			for (int z = 1; z < 15; z++) for (int x = 1; x < 15; x++) {
				BlockPos bp = new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
				BlockState rs = c.getBlockState(bp);
				if (!rs.isAir() || y > c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 8) continue;
				int open = 0;
				for (var d : net.minecraft.core.Direction.values()) if ((flags(c.getBlockState(bp.relative(d))) & NONOPQ) != 0) open++;
				if (open >= 3) return bp;
			}
		}
		return null;
	}

	/** Test: gercek gonderim yolunu calistirir (chunk gonderildi say + tarama + paketler). Doner: gonderilen blok sayisi. */
	public static String simulate(ServerPlayer p) {
		ServerLevel sl = p.serverLevel();
		if (FLAGS == null) init();
		int mode = modeNow(sl);
		if (mode <= 0) return "kapali";
		ChunkPos cp = p.chunkPosition();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			LevelChunk c = sl.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz);
			if (c == null) continue;
			build(c, true);
			onChunkSent(p, sl, c);
		}
		PState ps = state(p);
		long before = statPacketCells;
		revealAround(sl, p, ps);
		ps.dirty = false;
		return "paketlenen blok=" + (statPacketCells - before) + ", toplam paket=" + statPackets;
	}

	// ---------------- denetim (test) ----------------
	public static String audit(ServerPlayer p) {
		ServerLevel sl = p.serverLevel();
		if (FLAGS == null) init();
		int mode = modeNow(sl);
		if (mode <= 0) return "Koruma bu dünyada kapalı (mod 0).";
		StringBuilder sb = new StringBuilder();
		ChunkPos cp = p.chunkPosition();
		int bad = 0, chunks = 0, hiddenCells = 0, hiddenOres = 0, fakeCells = 0, buriedOresShown = 0, diffOutsideMeta = 0, sameInMeta = 0, exposedOres = 0;
		long t0 = System.nanoTime();
		var biomes = sl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk c = sl.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz);
				if (c == null) continue;
				Built b1 = build(c, false);
				Built b2 = build(c, false);
				if (b1 == null || b2 == null) continue;
				chunks++;
				if (!java.util.Arrays.equals(b1.buffer, b2.buffer)) {
					// dunya iki hesap arasinda degismis olabilir (yeni chunk son isleme): ucuncu hesapla ikincisi esit mi?
					Built b3 = build(c, false);
					if (b3 == null || !java.util.Arrays.equals(b2.buffer, b3.buffer)) { bad++; sb.append("[belirsiz:chunk ").append(c.getPos()).append("] "); }
					else { sb.append("[dünya değişti ").append(c.getPos()).append("] "); b1 = b3; }
				}
				LevelChunkSection[] real = c.getSections();
				FriendlyByteBuf in = new FriendlyByteBuf(Unpooled.wrappedBuffer(b1.buffer));
				Ctx cx = new Ctx(sl, c);
				try {
					for (int si = 0; si < real.length; si++) {
						LevelChunkSection cs = new LevelChunkSection(biomes);
						cs.read(in);
						int baseY = (si + c.getMinSection()) << 4;
						for (int ly = 0; ly < 16; ly++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
							BlockState rs = real[si].getBlockState(lx, ly, lz);
							BlockState ds = cs.getBlockState(lx, ly, lz);
							boolean inMeta = b1.fresh.get(lx, baseY + ly, lz);
							if (inMeta) {
								hiddenCells++;
								if (rs == ds) sameInMeta++;
								if ((flags(rs) & ORE) != 0) hiddenOres++;
								if ((flags(ds) & ORE) != 0 && (flags(rs) & ORE) == 0) fakeCells++;
								if ((flags(ds) & ORE) != 0 && (flags(rs) & ORE) != 0) buriedOresShown++;
							} else if (rs != ds) {
								diffOutsideMeta++;
							} else if ((flags(rs) & ORE) != 0) {
								if (!cx.exposed(lx, baseY + ly, lz) && nbKnown(cx, lx, lz)) buriedOresShown++;   // gömülü gerçek cevher aynen gösterilmiş
								else exposedOres++;
							}
						}
					}
					if (in.readableBytes() != 0) { bad++; sb.append("[artan bayt ").append(in.readableBytes()).append("] "); }
				} catch (Throwable t) {
					bad++;
					sb.append("[çözme hatası ").append(t).append("] ");
				}
			}
		}
		// ağ turu: gerçek paket kodeği
		try {
			LevelChunk c = p.level().getChunkAt(p.blockPosition());
			var pkt = new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(c, sl.getLightEngine(), null, null);
			var rb = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), sl.registryAccess());
			net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(rb, pkt);
			var back = net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(rb);
			if (back.getX() != c.getPos().x || back.getZ() != c.getPos().z) { bad++; sb.append("[ağ turu koordinat] "); }
		} catch (Throwable t) {
			bad++;
			sb.append("[ağ turu hatası ").append(t).append("] ");
		}
		sb.append(String.format("mod=%d chunk=%d gizli=%d (cevher=%d, sahte=%d) gömülü-gösterilen=%d açıktaki-cevher=%d meta-dışı-fark=%d meta-içi-aynı=%d hata=%d süre=%dms",
			mode, chunks, hiddenCells, hiddenOres, fakeCells, buriedOresShown, exposedOres, diffOutsideMeta, sameInMeta, bad, (System.nanoTime() - t0) / 1_000_000));
		if (hiddenCells != hiddenOres || fakeCells > 0 || diffOutsideMeta > 0 || buriedOresShown > 0 || sameInMeta > 0 || bad > 0) sb.append(" SORUN");
		else sb.append(" TEMIZ");
		return sb.toString();
	}

	/** Chunk-kenarındaki hücrenin tüm komşuları biliniyor mu (komşu chunk yoksa cevher geçici olarak görünür). */
	private static boolean nbKnown(Ctx cx, int lx, int lz) {
		if (lx == 0 && cx.nb[1] == null) return false;
		if (lx == 15 && cx.nb[2] == null) return false;
		if (lz == 0 && cx.nb[3] == null) return false;
		if (lz == 15 && cx.nb[4] == null) return false;
		return true;
	}

	public static String status() {
		return String.format("anti-xray: gönderilen=%d chunk=%d gizli=%d cevher=%d sahte=%d ort=%.2fms | açığa: %d tarama, %d paketli, %d hücre, ort %.3fms, gönderilen paket=%d (%d blok)",
			statSent, statChunks, statHidden, statHiddenOres, statFakes, statChunks == 0 ? 0 : statNanos / 1e6 / statChunks,
			statRuns, statReveals, statRevealCells, statRuns == 0 ? 0 : statRevealNanos / 1e6 / statRuns, statPackets, statPacketCells)
			+ " | bos tarama atlandi=" + statEmptyRevealSkips + " etkin=" + EMPTY_REVEAL_SKIP;
	}
}
