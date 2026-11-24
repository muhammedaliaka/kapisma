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
 * Hile koruması ("halüsinasyon" sistemi). Sunucu istemciye giden chunk paketini şöyle değiştirir:
 * <ul>
 * <li>Havaya açık olmayan (gömülü) cevherler yerine ev sahibi taş gösterilir; gerçek yer hiç gönderilmez.</li>
 * <li>Aynı chunk bölümünde, cevherin gerçek yerinden farklı, tamamen taşla çevrili yerlere sahte cevher konur
 *     (deterministik: aynı chunk hep aynı yalanı söyler, iki paketi karşılaştırarak ayıklanamaz).</li>
 * <li>Yeraltındaki boşluklar (mağara, tünel, yapı içi) ve yapay bloklar (tuğla, tahta, ray, sandık...) düz taş gösterilir.
 *     Bu bloklara ait blok varlığı (sandık, spawner) verisi de paketten çıkarılır.</li>
 * </ul>
 * Oyuncu yaklaşınca (bağlı hava boyunca BFS) ya da bir blok kırılınca gerçek hâli blok güncellemesiyle gönderilir.
 * Oyun mantığı sunucuda gerçek dünyayla çalışır; yalnız istemciye giden görüntü değişir.
 */
public final class Xray {
	private Xray() {}

	// ---------------- ayarlar ----------------
	/** Yeraltı sayılması için yüzeyden en az bu kadar derin olmalı. */
	public static final int DEPTH = 6;
	public static volatile int revealRadius = 10;
	/** Oyuncu başına tek turda açılacak en çok hücre (düşük donanım profilinde küçülür). */
	public static volatile int bfsCap = 2500;
	/** Test için: null değilse oyun durumuna bakmadan bu modu uygular. */
	public static volatile Integer forceMode = null;
	public static volatile long salt = 0x9E3779B97F4A7C15L;

	// ---------------- istatistik ----------------
	public static long statPackets, statPacketCells, statSent, statRuns, statChunks, statHidden, statHiddenOres, statFakes, statNanos, statReveals, statRevealCells, statRevealNanos;

	// ---------------- blok sınıflandırma ----------------
	private static final byte ORE = 1, NAT = 2, NONOPQ = 4, FLUID = 8;
	private static byte[] FLAGS;
	private static byte[] KIND;

	private static synchronized void init() {
		if (FLAGS != null) return;
		int n = Block.BLOCK_STATE_REGISTRY.size();
		byte[] f = new byte[n];
		byte[] k = new byte[n];
		for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
			int id = Block.getId(s);
			String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
			byte fl = 0;
			int kind = oreKind(p);
			if (kind > 0) fl |= ORE;
			if (natural(p)) fl |= NAT;
			if (!s.canOcclude()) fl |= NONOPQ;
			if (!s.getFluidState().isEmpty()) fl |= FLUID;
			f[id] = fl;
			k[id] = (byte) kind;
		}
		KIND = k;
		FLAGS = f;
	}

	/** 1 kömür 2 demir 3 bakır 4 altın 5 kızıltaş 6 lapis 7 elmas 8 zümrüt 9 nether altını 10 kuvars 11 antik kalıntı */
	private static int oreKind(String p) {
		if (p.equals("ancient_debris")) return 11;
		if (!p.endsWith("_ore")) return 0;
		if (p.equals("nether_gold_ore")) return 9;
		if (p.equals("nether_quartz_ore")) return 10;
		if (p.contains("coal")) return 1;
		if (p.contains("iron")) return 2;
		if (p.contains("copper")) return 3;
		if (p.contains("gold")) return 4;
		if (p.contains("redstone")) return 5;
		if (p.contains("lapis")) return 6;
		if (p.contains("diamond")) return 7;
		if (p.contains("emerald")) return 8;
		return 0;
	}

	private static boolean natural(String p) {
		switch (p) {
			case "stone", "deepslate", "dirt", "coarse_dirt", "grass_block", "podzol", "mycelium", "rooted_dirt", "gravel", "sand", "red_sand",
				"sandstone", "red_sandstone", "clay", "granite", "diorite", "andesite", "tuff", "terracotta", "netherrack", "basalt", "blackstone",
				"soul_sand", "soul_soil", "magma_block", "crimson_nylium", "warped_nylium", "bedrock", "snow_block", "ice", "packed_ice", "blue_ice",
				"dripstone_block", "moss_block", "mud", "obsidian", "end_stone", "infested_stone", "infested_deepslate", "water", "lava",
				"nether_wart_block", "warped_wart_block", "shroomlight", "crying_obsidian", "glowstone", "sculk":
				return true;
			default:
				return p.endsWith("_terracotta") && !p.contains("glazed");
		}
	}

	private static byte flags(BlockState s) {
		return FLAGS[Block.getId(s)];
	}

	private static BlockState oreBlock(int kind, boolean deep) {
		return switch (kind) {
			case 1 -> deep ? Blocks.DEEPSLATE_COAL_ORE.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState();
			case 2 -> deep ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
			case 3 -> deep ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState();
			case 4 -> deep ? Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState() : Blocks.GOLD_ORE.defaultBlockState();
			case 5 -> deep ? Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState() : Blocks.REDSTONE_ORE.defaultBlockState();
			case 6 -> deep ? Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState() : Blocks.LAPIS_ORE.defaultBlockState();
			case 7 -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState() : Blocks.DIAMOND_ORE.defaultBlockState();
			case 8 -> deep ? Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState() : Blocks.EMERALD_ORE.defaultBlockState();
			case 9 -> Blocks.NETHER_GOLD_ORE.defaultBlockState();
			case 10 -> Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
			default -> Blocks.ANCIENT_DEBRIS.defaultBlockState();
		};
	}

	// ---------------- mod belirleme ----------------
	/** 0 kapalı, 1 cevher gizle + sahte cevher, 2 ek olarak yeraltı boşlukları/yapıları düz taş. */
	public static int modeNow(ServerLevel sl) {
		Integer f = forceMode;
		if (f != null) return f;
		var gm = Kapisma.GM;
		if (gm == null || gm.current == null) return 0;
		int m = gm.current.xrayMode(sl);
		if (m > 0 && !sl.dimensionType().hasSkyLight()) m = Math.min(m, 1);   // Nether/End: boşluk gizleme yok (ufuk görüşü oyunun parçası)
		return m;
	}

	// ---------------- chunk üstverisi ----------------
	/** Son gönderimde gerçek durumdan farklı gösterilen hücreler (bölüm başına 4096 bit) + sütun yeraltı eşiği. */
	static final class Meta {
		final int minSec;
		final BitSet[] bits;
		final int[] ut;   // y <= ut[z*16+x] ise yeraltı
		Meta(int minSec, int n, int[] ut) { this.minSec = minSec; this.bits = new BitSet[n]; this.ut = ut; }
		boolean get(int lx, int y, int lz) {
			int si = (y >> 4) - minSec;
			if (si < 0 || si >= bits.length) return false;
			BitSet b = bits[si];
			return b != null && b.get(((y & 15) << 8) | (lz << 4) | lx);
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

	// ---------------- sahte/gizleme için deterministik karıştırıcı ----------------
	private static long mix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static long hash(long a, long b, long c) {
		return mix(salt ^ mix(a * 31 + b) ^ mix(c + 0x1234567L));
	}

	private static BlockState host(int mode, boolean nether, int y, long h) {
		if (nether) return Blocks.NETHERRACK.defaultBlockState();
		if (y < 0) return Blocks.DEEPSLATE.defaultBlockState();
		if (y >= 8) return Blocks.STONE.defaultBlockState();
		return ((h >>> 20) & 7) < (8 - y) ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	// ---------------- paket üretimi ----------------
	private static final class Ctx {
		final ServerLevel sl;
		final LevelChunk self;
		final LevelChunk[] nb = new LevelChunk[5];   // 0 kendi, 1 -x, 2 +x, 3 -z, 4 +z
		final int[][] ut = new int[5][];
		final int minY, maxY, minSec;
		final int mode;
		final boolean caves;

		Ctx(ServerLevel sl, LevelChunk c, int mode) {
			this.sl = sl;
			self = c;
			this.mode = mode;
			caves = mode >= 2;
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

		int[] utOf(int i) {
			int[] u = ut[i];
			if (u == null) {
				u = new int[256];
				LevelChunk c = nb[i];
				if (!caves) java.util.Arrays.fill(u, Integer.MIN_VALUE);
				else for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
					int h = Math.min(c.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z), c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
					u[(z << 4) | x] = h - DEPTH;   // getHeight = en ust blok y
				}
				ut[i] = u;
			}
			return u;
		}

		/** Sütunun (yerel x,z -1..16) yeraltı eşiği; bilinmiyorsa Integer.MIN_VALUE (yeraltı değil). */
		int utAt(int lx, int lz) {
			int i = chunkIdx(lx, lz);
			if (i < 0 || nb[i] == null) return Integer.MIN_VALUE;
			return utOf(i)[((lz & 15) << 4) | (lx & 15)];
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

		/** Bu komşu, gösterilen dünyada gözle görülür boş mu (dışarıdan bakan biri içeri görebilir mi)? */
		boolean visibleOpen(int lx, int y, int lz) {
			BlockState s = get(lx, y, lz);
			if (s == null) return false;
			if ((flags(s) & NONOPQ) == 0) return false;
			return !(caves && y <= utAt(lx, lz));
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
		Ctx cx = new Ctx(sl, chunk, mode);
		boolean nether = sl.dimension() == net.minecraft.world.level.Level.NETHER;
		LevelChunkSection[] secs = chunk.getSections();
		int n = secs.length;
		PalettedContainer<BlockState>[] displays = newArr(n);
		int[] nonEmpty = new int[n];
		Meta meta = new Meta(cx.minSec, n, cx.utOf(0));
		int ox = chunk.getPos().getMinBlockX(), oz = chunk.getPos().getMinBlockZ();
		int cposX = chunk.getPos().x, cposZ = chunk.getPos().z;
		int maxUt = Integer.MIN_VALUE;
		if (cx.caves) for (int v : cx.utOf(0)) maxUt = Math.max(maxUt, v);
		long hiddenTotal = 0, oresTotal = 0, fakesTotal = 0;

		for (int si = 0; si < n; si++) {
			LevelChunkSection sec = secs[si];
			if (sec.hasOnlyAir()) continue;
			int baseY = (si + cx.minSec) << 4;
			boolean hasOre = sec.maybeHas(s -> (flags(s) & ORE) != 0);
			boolean hasUg = cx.caves && baseY <= maxUt && sec.maybeHas(s -> {
				byte f = flags(s);
				return (f & (NAT | FLUID)) == 0;
			});
			if (!hasOre && !hasUg) continue;
			PalettedContainer<BlockState> states = sec.getStates();
			PalettedContainer<BlockState> disp = null;
			BitSet bits = null;
			LongArrayList ores = null;   // paketlenmiş: yerel idx<<8 | tür
			for (int ly = 0; ly < 16; ly++) {
				int y = baseY + ly;
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						BlockState s = states.get(lx, ly, lz);
						byte f = flags(s);
						if ((f & (ORE | NAT | FLUID)) == NAT || (f & FLUID) != 0) continue;
						boolean hide;
						if ((f & ORE) != 0) {
							hide = !cx.exposed(lx, y, lz);
						} else if ((f & NAT) != 0) {
							continue;
						} else if (cx.caves && y <= cx.utAt(lx, lz)) {
							hide = (f & NONOPQ) != 0 || !cx.exposed(lx, y, lz);
						} else {
							hide = false;
						}
						if (!hide) continue;
						int idx = (ly << 8) | (lz << 4) | lx;
						if (disp == null) { disp = independentCopy(states); bits = new BitSet(4096); }
						long h = hash(ox + lx, y, oz + lz);
						disp.set(lx, ly, lz, host(mode, nether, y, h));
						bits.set(idx);
						hiddenTotal++;
						if ((f & ORE) != 0) {
							if (ores == null) ores = new LongArrayList();
							ores.add(((long) idx << 8) | KIND[Block.getId(s)]);
							oresTotal++;
						}
					}
				}
			}
			if (disp == null) continue;
			// ---- sahte cevherler: gerçek gömülü cevherin yerine, aynı bölümde başka yere ----
			if (ores != null) {
				for (int oi = 0; oi < ores.size(); oi++) {
					long o = ores.getLong(oi);
					int kind = (int) (o & 255);
					int idx = (int) (o >>> 8);
					long h = hash(cposX * 4099L + si, cposZ, idx);
					int roll = (int) (h & 3);
					int copies = roll == 0 ? 0 : roll == 1 ? 2 : 1;
					for (int c = 0; c < copies; c++) {
						for (int attempt = 0; attempt < 6; attempt++) {
							long hh = mix(h ^ ((long) (c * 8 + attempt) << 40));
							int tx = (int) (hh & 15), tz = (int) ((hh >>> 4) & 15), ty = (int) ((hh >>> 8) & 15);
							if (tx < 1 || tx > 14 || tz < 1 || tz > 14 || ty < 1 || ty > 14) continue;
							int tidx = (ty << 8) | (tz << 4) | tx;
							if (bits.get(tidx)) continue;
							BlockState real = states.get(tx, ty, tz);
							if ((flags(real) & (NAT | ORE | NONOPQ | FLUID)) != NAT) continue;
							if (!solid(disp, tx + 1, ty, tz) || !solid(disp, tx - 1, ty, tz) || !solid(disp, tx, ty + 1, tz)
								|| !solid(disp, tx, ty - 1, tz) || !solid(disp, tx, ty, tz + 1) || !solid(disp, tx, ty, tz - 1)) continue;
							int yy = baseY + ty;
							boolean deep = !nether && (real.is(Blocks.DEEPSLATE) || yy < 0);
							disp.set(tx, ty, tz, oreBlock(kind, deep));
							bits.set(tidx);
							fakesTotal++;
							break;
						}
					}
				}
			}
			displays[si] = disp;
			meta.bits[si] = bits;
			int[] cnt = {0};
			disp.count((st, c) -> { if (!st.isAir()) cnt[0] += c; });
			nonEmpty[si] = cnt[0];
		}

		// ---- serileştir ----
		FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer(32768));
		for (int si = 0; si < n; si++) {
			if (displays[si] == null) secs[si].write(out);
			else {
				out.writeShort(nonEmpty[si]);
				displays[si].write(out);
				secs[si].getBiomes().write(out);
			}
		}
		byte[] arr = new byte[out.readableBytes()];
		out.readBytes(arr);
		out.release();

		// ---- önceki üstveriyle birleştir (bir istemci eski görüntüyü almış olabilir) ----
		Meta old = info.meta;
		Meta merged = meta;
		if (old != null && old.bits.length == n) {
			merged = new Meta(cx.minSec, n, meta.ut);
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
		statFakes += fakesTotal;
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

	private static boolean solid(PalettedContainer<BlockState> disp, int x, int y, int z) {
		return (flags(disp.get(x, y, z)) & (NONOPQ | FLUID)) == 0;
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
				for (BlockPos b : l) {
					if (b.distSqr(p.blockPosition()) <= (double) near * near) { ps.dirty = true; break; }
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
			boolean moved = mode >= 2 && p.distanceToSqr(ps.x, ps.y, ps.z) >= 9.0;
			if (!ps.dirty && !moved) continue;
			double d2 = p.distanceToSqr(ps.x, ps.y, ps.z);
			boolean fast = d2 >= 64.0 || p.getDeltaMovement().lengthSqr() > 0.25;   // dusme/ucma/isinlanma: beklemeden
			if (!fast && t - ps.lastRun < (ps.dirty ? 1 : 4)) continue;
			if (budget-- <= 0) break;
			long t0 = System.nanoTime();
			statRuns++;
			revealAround(sl, p, ps, mode);
			statRevealNanos += System.nanoTime() - t0;
			ps.dirty = false;
			ps.lastRun = t;
			ps.x = p.getX(); ps.y = p.getY(); ps.z = p.getZ();
		}
	}

	private static final LongOpenHashSet VISITED = new LongOpenHashSet();
	private static final LongArrayList QUEUE = new LongArrayList();

	/** Çıktı: açığa çıkarılan hücreler (bölüm anahtarı -> kısa konumlar). */
	private static Map<Long, ShortOpenHashSet> bfs(ServerLevel sl, ServerPlayer p, PState ps, int mode, BlockPos origin, int radius, boolean record) {
		Map<Long, ShortOpenHashSet> batches = new java.util.HashMap<>();
		VISITED.clear();
		QUEUE.clear();
		int surfR = Math.min(radius, 6);
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
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
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
					if (!record || rv.add(BlockPos.asLong(nx, ny, nz))) {
						batches.computeIfAbsent(SectionPos.asLong(nx >> 4, ny >> 4, nz >> 4), k -> new ShortOpenHashSet())
							.add(SectionPos.sectionRelativePos(mp.set(nx, ny, nz)));
					}
				}
				if (d >= 0 && open) {
					boolean ug = m != null && mode >= 2 && ny <= m.ut[((nz & 15) << 4) | (nx & 15)];
					int lim = ug ? radius : surfR;
					int cheb = Math.max(Math.abs(nx - feet.getX()), Math.max(Math.abs(ny - feet.getY()), Math.abs(nz - feet.getZ())));
					if (cheb <= lim) QUEUE.add(BlockPos.asLong(nx, ny, nz));
				}
			}
		}
		statRevealCells += cells;
		return batches;
	}

	private static void revealAround(ServerLevel sl, ServerPlayer p, PState ps, int mode) {
		int r = mode >= 2 ? revealRadius : 6;
		var batches = bfs(sl, p, ps, mode, p.blockPosition(), r, true);
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

	/** Test: oyuncunun chunk'ında gizli bir mağara hücresi (>=3 açık komşu) arar. */
	public static BlockPos findCave(ServerPlayer p) {
		ServerLevel sl = p.serverLevel();
		LevelChunk c = p.level().getChunkAt(p.blockPosition());
		Built b = build(c, true);
		if (b == null) return null;
		ChunkPos cp = c.getPos();
		for (int y = sl.getMinBuildHeight() + 2; y < sl.getMaxBuildHeight() - 2; y++) {
			for (int z = 1; z < 15; z++) for (int x = 1; x < 15; x++) {
				if (!b.fresh.get(x, y, z)) continue;
				BlockPos bp = new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
				BlockState rs = c.getBlockState(bp);
				if ((flags(rs) & (NONOPQ | FLUID)) != NONOPQ) continue;
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
		revealAround(sl, p, ps, mode);
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
		Map<Long, LevelChunkSection[]> views = new java.util.HashMap<>();
		Map<Long, Built> builts = new java.util.HashMap<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk c = sl.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz);
				if (c == null) continue;
				Built b1 = build(c, false);
				Built b2 = build(c, false);
				if (b1 == null || b2 == null) continue;
				chunks++;
				builts.put(c.getPos().toLong(), b1);
				if (!java.util.Arrays.equals(b1.buffer, b2.buffer)) {
					// dunya iki hesap arasinda degismis olabilir (yeni chunk son isleme): ucuncu hesapla ikincisi esit mi?
					Built b3 = build(c, false);
					if (b3 == null || !java.util.Arrays.equals(b2.buffer, b3.buffer)) { bad++; sb.append("[belirsiz:chunk ").append(c.getPos()).append("] "); }
					else { sb.append("[dünya değişti ").append(c.getPos()).append("] "); b1 = b3; }
				}
				LevelChunkSection[] real = c.getSections();
				FriendlyByteBuf in = new FriendlyByteBuf(Unpooled.wrappedBuffer(b1.buffer));
				Ctx cx = new Ctx(sl, c, mode);
				LevelChunkSection[] view = new LevelChunkSection[real.length];
				try {
					for (int si = 0; si < real.length; si++) {
						LevelChunkSection cs = new LevelChunkSection(biomes);
						cs.read(in);
						view[si] = cs;
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
				views.put(c.getPos().toLong(), view);
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
		// ---- mağara doğrulaması: gizli bir mağara hücresinde dur, BFS uygula, bağlı hava + komşuları gerçek görünmeli ----
		int caveBad = 0, caveOpen = 0, revealed = 0;
		LevelChunk mid = sl.getChunkSource().getChunkNow(cp.x, cp.z);
		Built mb = builts.get(cp.toLong());
		BlockPos origin = null;
		if (mid != null && mb != null && mode >= 2) {
			int minY = sl.getMinBuildHeight();
			outer:
			for (int y = minY + 2; y < sl.getMaxBuildHeight() - 2; y++) {
				for (int z = 1; z < 15; z++) for (int x = 1; x < 15; x++) {
					if (!mb.fresh.get(x, y, z)) continue;
					BlockPos bp = new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
					BlockState rs = mid.getBlockState(bp);
					if ((flags(rs) & (NONOPQ | FLUID)) != NONOPQ) continue;
					int open = 0;
					for (var d : net.minecraft.core.Direction.values()) if ((flags(mid.getBlockState(bp.relative(d))) & NONOPQ) != 0) open++;
					if (open >= 3) { origin = bp; break outer; }
				}
			}
		}
		if (origin != null) {
			PState ps = new PState();
			ps.level = sl;
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) ps.sent.add(ChunkPos.asLong(cp.x + dx, cp.z + dz));
			int R = Math.min(revealRadius, 8);
			var batches = bfs(sl, p, ps, mode, origin, R, true);
			for (var e : batches.entrySet()) {
				SectionPos sp = SectionPos.of(e.getKey());
				LevelChunkSection[] view = views.get(ChunkPos.asLong(sp.x(), sp.z()));
				LevelChunk rc = sl.getChunkSource().getChunkNow(sp.x(), sp.z());
				if (view == null || rc == null) continue;
				for (short sh : e.getValue()) {
					BlockPos bp = sp.relativeToBlockPos(sh);
					int si = (bp.getY() >> 4) - rc.getMinSection();
					view[si].setBlockState(bp.getX() & 15, bp.getY() & 15, bp.getZ() & 15, rc.getBlockState(bp), false);
					revealed++;
				}
			}
			// bağımsız taşma doldurma (yarıçap 6, gerçek boş hücreler): hücre ve 6 komşusu istemci görünümünde gerçeğe eşit olmalı
			java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
			java.util.HashSet<Long> seen = new java.util.HashSet<>();
			q.add(origin);
			seen.add(origin.asLong());
			while (!q.isEmpty() && seen.size() < 2500) {
				BlockPos c = q.poll();
				caveOpen++;
				for (var d : net.minecraft.core.Direction.values()) {
					BlockPos n = c.relative(d);
					LevelChunk nc = sl.getChunkSource().getChunkNow(n.getX() >> 4, n.getZ() >> 4);
					LevelChunkSection[] view = views.get(ChunkPos.asLong(n.getX() >> 4, n.getZ() >> 4));
					if (nc == null || view == null || n.getY() < sl.getMinBuildHeight() || n.getY() >= sl.getMaxBuildHeight()) continue;
					BlockState real = nc.getBlockState(n);
					BlockState seenS = view[(n.getY() >> 4) - nc.getMinSection()].getBlockState(n.getX() & 15, n.getY() & 15, n.getZ() & 15);
					if (real != seenS) caveBad++;
					boolean open = (flags(real) & NONOPQ) != 0;
					if (open && Math.max(Math.abs(n.getX() - origin.getX()), Math.max(Math.abs(n.getY() - origin.getY()), Math.abs(n.getZ() - origin.getZ()))) <= 4 && seen.add(n.asLong())) q.add(n);
				}
			}
		}
		// ---- blok varlığı süzme: gizli hücreye sandık koy, pakette görünmemeli; sonra geri al ----
		String beRep = "";
		if (origin != null && mid != null) {
			BlockState prev = mid.getBlockState(origin);
			sl.setBlock(origin, Blocks.CHEST.defaultBlockState(), 3);
			var pd = new net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData(mid);
			List<BlockPos> got = new ArrayList<>();
			pd.getBlockEntitiesTagsConsumer(mid.getPos().x, mid.getPos().z).accept((bp, type, tag) -> got.add(bp.immutable()));
			Meta mm = metaOf(mid);
			int leak = 0;
			for (BlockPos bp : got) if (mm != null && mm.get(bp.getX() & 15, bp.getY(), bp.getZ() & 15)) leak++;
			boolean chestSeen = got.contains(origin);
			int real = mid.getBlockEntitiesPos().size();
			if (leak > 0 || chestSeen) { bad++; }
			sl.setBlock(origin, prev, 3);
			// yüzeydeki sandık görünür kalmalı
			BlockPos surf = new BlockPos(cp.getMinBlockX() + 8, mid.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8) + 1, cp.getMinBlockZ() + 8);
			BlockState prevS = mid.getBlockState(surf);
			sl.setBlock(surf, Blocks.CHEST.defaultBlockState(), 3);
			var pd2 = new net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData(mid);
			List<BlockPos> got2 = new ArrayList<>();
			pd2.getBlockEntitiesTagsConsumer(mid.getPos().x, mid.getPos().z).accept((bp, type, tag) -> got2.add(bp.immutable()));
			boolean surfSeen = got2.contains(surf);
			if (!surfSeen) bad++;
			sl.setBlock(surf, prevS, 3);
			beRep = String.format(" | blokVarlığı: gerçek=%d gönderilen=%d sızan=%d gizli-sandık-görünüyor=%s yüzey-sandık-görünüyor=%s |", real, got.size(), leak, chestSeen, surfSeen);
		}
		sb.append(String.format("mod=%d chunk=%d gizli=%d (cevher=%d, sahte=%d) sızıntı(gömülü-gerçek-gösterilen)=%d açıktaki-cevher=%d meta-dışı-fark=%d meta-içi-aynı=%d | mağara: %s açıkHücre=%d açığaÇıkan=%d bozuk=%d |%s hata=%d süre=%dms",
			mode, chunks, hiddenCells, hiddenOres, fakeCells, buriedOresShown, exposedOres, diffOutsideMeta, sameInMeta,
			origin == null ? "yok" : origin.toShortString(), caveOpen, revealed, caveBad, beRep, bad, (System.nanoTime() - t0) / 1_000_000));
		if (diffOutsideMeta > 0 || buriedOresShown > 0 || sameInMeta > 0 || caveBad > 0 || bad > 0) sb.append(" SORUN");
		else sb.append(" TEMIZ");
		return sb.toString();
	}

	/** Chunk-kenarındaki hücrenin tüm komşuları biliniyor mu (kenarda komşu chunk yoksa temkinli gizleme normaldir). */
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
			statRuns, statReveals, statRevealCells, statRuns == 0 ? 0 : statRevealNanos / 1e6 / statRuns, statPackets, statPacketCells);
	}
}
