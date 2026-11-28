package tr.kapisma.guard;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import tr.kapisma.Kapisma;
import tr.kapisma.mixin.ChunkMapAccessor;
import tr.kapisma.mixin.TrackedEntityAccessor;

/**
 * Oyuncu hilelerine karsi sunucu tarafi koruma (oyun kuralinda "antiCheat" aciksa, sadece oyun BASLAYINCA):
 *  1) Gorus hatti (ESP / wallhack / oyuncu radari): duvar/kati blok arkasindaki oyuncu istemciye HIC gonderilmez.
 *  2) Dövüş: menzil ve katı duvar kontrolleri sunucuda yapılır. Tıklama hızı tek başına yaptırım oluşturmaz.
 *  3) Seed: istemciye giden "hashed seed" uydurma (her zaman).
 * Istemcide calisan hileler (fullbright, freecam gorsel, Baritone ...) sunucudan engellenemez; docs/DENEY_PLANI.md bunu acikca yazar.
 */
public final class AntiCheat {
	private AntiCheat() {}

	/** Gorus hatti yenileme araligi (tik). Dusuk guc cihazda daha uzun. */
	public static volatile int interval = 4;
	/** Gorunur olduktan sonra bu kadar tik daha gorunur kal (kose donerken goz kirpma olmasin). */
	private static final int HOLD = 16;
	private static final double NEAR = 8.0;
	private static final double FAR = 144.0;

	/** Test icin: null = oyuna baglı, true/false = zorla. */
	public static volatile Boolean force = null;

	private static final Map<Long, int[]> cache = new ConcurrentHashMap<>(); // {gorunur(0/1), hesap tiki, son gorunme tiki}
	private static final Set<ServerLevel> wasActive = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private static final Map<UUID, ArrayDeque<Integer>> swings = new HashMap<>();
	public static final Map<String, Integer> violations = new ConcurrentHashMap<>();
	public static volatile long evals, hiddenDecisions;

	public static long fakeHashedSeed(long real) {
		long salt = Kapisma.GM != null ? Kapisma.GM.settings.xraySalt : 0x5DEECE66DL;
		long z = (real ^ salt) + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static boolean active(Level level) {
		Boolean f = force;
		if (f != null) return f;
		return Kapisma.GM != null && Kapisma.GM.current != null && Kapisma.GM.current.cheatGuard(level);
	}

	// ------------------------------------------------------------------ 1) Gorus hatti

	/** ServerPlayer.broadcastToPlayer kancasi: true = bu oyuncuyu "viewer"a gonderme. */
	public static boolean hide(ServerPlayer target, ServerPlayer viewer) {
		if (target == viewer) return false;
		Level lvl = target.level();
		if (lvl != viewer.level() || lvl.isClientSide || !active(lvl)) return false;
		if (viewer.isSpectator() || target.isSpectator()) return false;
		double d2 = viewer.distanceToSqr(target);
		if (d2 < NEAR * NEAR) return false;
		if (target.hasEffect(MobEffects.GLOWING)) return false;          // parlayan oyuncu (final) duvardan da gorunur olmali
		if (sameTeam(viewer, target)) return false;
		if (d2 > FAR * FAR) return false; // Menzilin geri kalanını vanilla takibi yönetir.
		int now = viewer.getServer().getTickCount();
		long key = ((long) viewer.getId() << 32) | (target.getId() & 0xFFFFFFFFL);
		int[] e = cache.get(key);
		if (e != null && now - e[1] >= 0 && now - e[1] < interval) return e[0] == 0;
		if (e == null) { e = new int[] {0, now, now - HOLD - 1}; cache.put(key, e); }
		evals++;
		boolean sees = canSee((ServerLevel) lvl, viewer, target);
		if (sees) e[2] = now;
		boolean visible = sees || now - e[2] < HOLD;
		e[0] = visible ? 1 : 0;
		e[1] = now;
		if (!visible) hiddenDecisions++;
		return !visible;
	}

	private static boolean sameTeam(ServerPlayer a, ServerPlayer b) {
		var g = Kapisma.GM != null ? Kapisma.GM.current : null;
		if (g == null || "KATIL".equals(g.id())) return false;
		var ta = g.teamOf(a.getUUID());
		return ta != null && ta == g.teamOf(b.getUUID());
	}

	private static boolean canSee(ServerLevel lvl, ServerPlayer viewer, ServerPlayer target) {
		Vec3 eye = viewer.getEyePosition();
		AABB b = target.getBoundingBox().inflate(1.1, 0.25, 1.1);           // koseden cikmadan once gosterebilmek icin pay
		double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2, cy = (b.minY + b.maxY) / 2;
		Vec3[] pts = {
			new Vec3(cx, cy, cz), new Vec3(cx, b.maxY, cz), new Vec3(cx, b.minY, cz),
			new Vec3(b.minX, cy, b.minZ), new Vec3(b.maxX, cy, b.minZ), new Vec3(b.minX, cy, b.maxZ), new Vec3(b.maxX, cy, b.maxZ)
		};
		double s = 0.8;                                                      // bakan da kose donuyor olabilir
		Vec3[] origins = { eye, eye.add(s, 0, 0), eye.add(-s, 0, 0), eye.add(0, 0, s), eye.add(0, 0, -s) };
		for (Vec3 o : origins)
			for (Vec3 p : pts)
				if (!blocked(lvl, o, p)) return true;
		return false;
	}

	/** a -> b dogrusu uzerinde, uc hucreler haric, TAM OPAK kup blok var mi (yaprak/cam/slab/cit saydam sayilir). */
	public static boolean blocked(ServerLevel lvl, Vec3 a, Vec3 b) {
		int x = Mth.floor(a.x), y = Mth.floor(a.y), z = Mth.floor(a.z);
		int ex = Mth.floor(b.x), ey = Mth.floor(b.y), ez = Mth.floor(b.z);
		double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
		int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
		double tx = dx == 0 ? Double.POSITIVE_INFINITY : ((dx > 0 ? x + 1 - a.x : a.x - x) / Math.abs(dx));
		double ty = dy == 0 ? Double.POSITIVE_INFINITY : ((dy > 0 ? y + 1 - a.y : a.y - y) / Math.abs(dy));
		double tz = dz == 0 ? Double.POSITIVE_INFINITY : ((dz > 0 ? z + 1 - a.z : a.z - z) / Math.abs(dz));
		double ddx = dx == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
		double ddy = dy == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
		double ddz = dz == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		LevelChunk chunk = null;
		int cxk = Integer.MIN_VALUE, czk = Integer.MIN_VALUE;
		int minY = lvl.getMinBuildHeight(), maxY = lvl.getMaxBuildHeight();
		int guard = 0;
		while ((x != ex || y != ey || z != ez) && guard++ < 400) {
			double next = Math.min(tx, Math.min(ty, tz));
			if (tx <= next + 1e-9) { x += sx; tx += ddx; }
			if (ty <= next + 1e-9) { y += sy; ty += ddy; }
			if (tz <= next + 1e-9) { z += sz; tz += ddz; }
			if (x == ex && y == ey && z == ez) break;
			if (y < minY || y >= maxY) continue;
			int ck = x >> 4, cz2 = z >> 4;
			if (ck != cxk || cz2 != czk) { chunk = lvl.getChunkSource().getChunkNow(ck, cz2); cxk = ck; czk = cz2; }
			if (chunk == null) continue;
			pos.set(x, y, z);
			var st = chunk.getBlockState(pos);
			if (st.canOcclude() && st.isSolidRender(lvl, pos)) return true;
		}
		return false;
	}

	/** Her sunucu tiki: gorus hatti kararlarini periyodik yeniler (vanilla sadece 16 blok kutusu degisince yeniler). */
	public static void tick(MinecraftServer server) {
		if (Kapisma.GM == null) return;
		long t = server.getTickCount();
		wasActive.removeIf(level -> server.getLevel(level.dimension()) != level);
		for (ServerLevel lvl : server.getAllLevels()) {
			boolean act = active(lvl);
			boolean was = wasActive.contains(lvl);
			if (!act && !was) continue;
			if (act) wasActive.add(lvl); else wasActive.remove(lvl);   // act=false & was=true: son bir kez herkesi geri goster
			if (act && t % interval != 0) continue;
			refresh(lvl);
		}
		if (t % 1200 == 0) {
			int now = server.getTickCount();
			cache.entrySet().removeIf(en -> Math.abs(now - en.getValue()[1]) > 600);
			swings.keySet().removeIf(u -> server.getPlayerList().getPlayer(u) == null);
		}
	}

	public static void refresh(ServerLevel lvl) {
		var ps = lvl.players();
		if (ps.size() < 2) return;
		var map = ((ChunkMapAccessor) lvl.getChunkSource().chunkMap).kapisma$entityMap();
		for (ServerPlayer target : ps) {
			Object tr = map.get(target.getId());
			if (tr == null) continue;
			for (ServerPlayer viewer : ps)
				if (viewer != target) ((TrackedEntityAccessor) tr).kapisma$updatePlayer(viewer);
		}
	}

	/** Test: viewer su an target'i istemcisine gonderiyor mu (gercek paket duzeyi: seenBy). */
	public static boolean seenBy(ServerPlayer target, ServerPlayer viewer) {
		var map = ((ChunkMapAccessor) ((ServerLevel) target.level()).getChunkSource().chunkMap).kapisma$entityMap();
		Object tr = map.get(target.getId());
		return tr != null && ((TrackedEntityAccessor) tr).kapisma$seenBy().contains(viewer.connection);
	}

	// ------------------------------------------------------------------ 2) Dovus

	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, EntityHitResult hit) {
		if (level.isClientSide || !(player instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return InteractionResult.PASS;
		if (!active(level) || target instanceof EnderDragonPart) return InteractionResult.PASS;
		if (target.level() != level || target.isRemoved()) return flag("hedef", "geçersiz saldırı hedefi");
		// menzil
		Vec3 eye = sp.getEyePosition();
		AABB b = target.getBoundingBox().inflate(target.getPickRadius());
		Vec3 near = new Vec3(Mth.clamp(eye.x, b.minX, b.maxX), Mth.clamp(eye.y, b.minY, b.maxY), Mth.clamp(eye.z, b.minZ, b.maxZ));
		double reach = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) + 1.3;     // vanilla sunucu +3 payi verir; gecikme icin 1.3 yeter
		if (eye.distanceToSqr(near) > reach * reach) return flag("menzil", "menzil dışı vuruş");
		// duvar arkasi
		if (level instanceof ServerLevel sl && blocked(sl, eye, near) && blocked(sl, eye, b.getCenter())
				&& !hasReachableEdge(sl, eye, target.getBoundingBox(), reach))
			return flag("duvar", "duvarın arkasından vuruş");
		int now = sp.getServer().getTickCount();
		var q = swings.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
		while (!q.isEmpty() && now - q.peekFirst() >= 20) q.pollFirst();
		if (q.size() == 18) violations.merge("tiklama_gozlem", 1, Integer::sum);
		if (q.size() < 19) q.addLast(now);
		return InteractionResult.PASS;
	}

	/**
	 * A partially exposed target may be hit at an edge although its closest point
	 * and center are behind a full block. Test only points on the actual hitbox,
	 * within reach, and only when the two fast rays were both blocked.
	 * Conservative samples are not a proof of visibility for every possible ray.
	 */
	private static boolean hasReachableEdge(ServerLevel level, Vec3 eye, AABB box, double reach) {
		double[] xs = {box.minX + 1e-5, box.getCenter().x, box.maxX - 1e-5};
		double[] ys = {box.minY + 1e-5, box.getCenter().y, box.maxY - 1e-5};
		double[] zs = {box.minZ + 1e-5, box.getCenter().z, box.maxZ - 1e-5};
		double limit = reach * reach;
		for (double x : xs) for (double y : ys) for (double z : zs) {
			Vec3 point = new Vec3(x, y, z);
			if (eye.distanceToSqr(point) <= limit && !blocked(level, eye, point)) return true;
		}
		return false;
	}

	public static void onDisconnect(ServerPlayer p) {
		swings.remove(p.getUUID());
		cache.keySet().removeIf(key -> (int) (key >> 32) == p.getId() || (int) key.longValue() == p.getId());
	}

	private static InteractionResult flag(String kind, String text) {
		int n = violations.merge(kind, 1, Integer::sum);
		if (n == 1 || n % 50 == 0) Kapisma.LOG.info("[koruma] {} (toplam {} {})", text, n, kind);
		return InteractionResult.FAIL;
	}

	public static String status() {
		return "koruma: aktif(overworld)=" + (Kapisma.GM != null && active(Kapisma.GM.server.overworld())) + " zorla=" + force + " aralik=" + interval
			+ " degerlendirme=" + evals + " gizleme=" + hiddenDecisions + " ihlal=" + violations;
	}
}
