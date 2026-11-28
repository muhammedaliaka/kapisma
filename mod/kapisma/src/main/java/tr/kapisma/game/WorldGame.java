package tr.kapisma.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;
import tr.kapisma.lobby.Lobby;
import tr.kapisma.origin.OriginService;
import tr.kapisma.seed.SeedPool;
import tr.kapisma.team.TeamService;
import tr.kapisma.team.TeamService.GameTeam;

/**
 * Ortak oyun akisi: dunya -> takimlar -> irk -> dagit -> geri sayim -> oyun -> bitis -> lobiye donus.
 * Varsayilan dunya: Fantasy ile acilan seed'li gecici overworld (UHC, Tombala).
 * Manhunt ana dunyayi kullanmak icin openWorld/placePlayers/gameLevel'i degistirir.
 */
public abstract class WorldGame extends Game {
	protected GameWorld world;
	protected SeedPool.Entry seedEntry;
	protected final Map<UUID, BlockPos> spawnOf = new HashMap<>();
	protected final Map<UUID, String> chosenOrigins = new HashMap<>();
	private final Map<UUID, Integer> offlineTicks = new HashMap<>();
	private boolean closed;
	private int selectLimit;
	private GameTeam winner;
	private final Map<UUID, Vec3> frozenAt = new HashMap<>();
	private final java.util.Set<UUID> placed = new java.util.HashSet<>();
	private final java.util.Set<UUID> outsideBorder = new java.util.HashSet<>();
	private static final ResourceLocation START_FREEZE = ResourceLocation.fromNamespaceAndPath("kapisma", "start_freeze");
	private static final String SPECTATOR_COMPASS = "kapisma_spectator_compass";
	private final Map<UUID, net.minecraft.core.GlobalPos> deathSpots = new HashMap<>();
	private final Map<UUID, net.minecraft.core.GlobalPos> disconnectedSpots = new HashMap<>();
	private final Map<UUID, net.minecraft.core.GlobalPos> lastSeenSpots = new HashMap<>();

	protected WorldGame(GameManager mgr) {
		super(mgr);
	}

	// ---- alt siniflarin doldurdugu noktalar ----
	protected abstract GameWorld.Options worldOptions();

	protected abstract double borderRadius();

	/** Elenme modu: olen oyuncu seyirci olur, son takim kazanir. */
	protected abstract boolean eliminationMode();

	protected abstract void onStart();

	protected abstract void onRunningTick();

	// ---- can gostergesi (tab listesi + isim etiketinin alti) ----
	private static final String HP_OBJ = "kp_hp";
	private boolean healthShown;

	private void setupHealth() {
		boolean has = tr.kapisma.config.Rules.of(id()).stream().anyMatch(r -> r.key().equals("showHealth"));
		if (!has || !st.flag(id(), "showHealth")) return;
		var sb = server.getScoreboard();
		var old = sb.getObjective(HP_OBJ);
		if (old != null) sb.removeObjective(old);
		var o = sb.addObjective(HP_OBJ, net.minecraft.world.scores.criteria.ObjectiveCriteria.HEALTH, Component.literal("❤").withStyle(ChatFormatting.RED),
			net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.HEARTS, true, null);
		sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST, o);
		sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.BELOW_NAME, o);
		healthShown = true;
	}

	private void clearHealth() {
		if (!healthShown) return;
		healthShown = false;
		var sb = server.getScoreboard();
		var o = sb.getObjective(HP_OBJ);
		if (o == null) return;
		sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST, null);
		sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.BELOW_NAME, null);
		sb.removeObjective(o);
	}

	// ---- mini harita (kapisma-hud) ----
	/** Alt sinif: bu oyunda mini harita acik mi? */
	protected boolean minimapEnabled() { return false; }

	/** Daralma varsa son yaricap, yoksa 0. */
	protected double minimapEndRadius() { return 0; }

	private double mapStartRadius;
	private static final boolean HUD_DEDUP = "1".equals(System.getenv("KAPISMA_HUD_DEDUP"));
	private record MapState(boolean active,double cx,double cz,float start,float current,float end,
		int x,int z,float yaw,List<tr.kapisma.net.MapPayload.Mate> mates) {
		static MapState of(tr.kapisma.net.MapPayload p) {
			return p.active ? new MapState(true,p.centerX,p.centerZ,p.startRadius,p.curRadius,p.endRadius,
				p.selfX,p.selfZ,p.selfYaw,List.copyOf(p.mates)) : new MapState(false,0,0,0,0,0,0,0,0,List.of());
		}
	}
	private record TargetState(boolean has,int x,int z,String label) {
		static TargetState of(tr.kapisma.net.TargetPayload p) {
			return p.has ? new TargetState(true,p.x,p.z,p.label) : new TargetState(false,0,0,"");
		}
	}
	private final HudSendGate<MapState> mapGate = new HudSendGate<>(40);
	private final HudSendGate<TargetState> targetGate = new HudSendGate<>(40);
	private void forgetHud(UUID id) { mapGate.forget(id); targetGate.forget(id); }

	private void sendMinimap() {
		if (!minimapEnabled() || world == null) return;
		var wb = world.level.getWorldBorder();
		if (mapStartRadius <= 0) mapStartRadius = Math.max(borderRadius(), world.borderRadius());
		for (ServerPlayer p : onlinePlayers()) {
			if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.MapPayload.TYPE)) {
				forgetHud(p.getUUID());
				continue;
			}
			var m = new tr.kapisma.net.MapPayload();
			m.active = p.level() == world.level;
			m.centerX = wb.getCenterX();
			m.centerZ = wb.getCenterZ();
			m.startRadius = (float) mapStartRadius;
			m.curRadius = (float) (wb.getSize() / 2);
			m.endRadius = (float) minimapEndRadius();
			m.selfX = p.getBlockX();
			m.selfZ = p.getBlockZ();
			m.selfYaw = p.getYRot();
			GameTeam t = teamOf(p.getUUID());
			if (t != null) {
				for (UUID id : t.members) {
					ServerPlayer o = player(id);
					if (o == null || o == p || o.level() != world.level || o.isSpectator()) continue;
					Integer c = t.color.getColor();
					m.mates.add(new tr.kapisma.net.MapPayload.Mate(o.getGameProfile().getName(), c != null ? c : 0xFFFFFF, o.getBlockX(), o.getBlockZ()));
				}
			}
			sendTarget(p);
			if (!HUD_DEDUP) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, m);
			else {
				var snapshot = MapState.of(m);
				if (mapGate.shouldSend(p.getUUID(),p.connection,snapshot,ticks)) {
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, m);
					mapGate.markSent(p.getUUID(),p.connection,snapshot,ticks);
				}
			}
		}
	}

	/** Pusula hedefini haritaya (yeni HUD modu varsa) gonderir; hedef yoksa temizler. */
	private void sendTarget(ServerPlayer p) {
		if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.TargetPayload.TYPE)) {
			targetGate.forget(p.getUUID());
			return;
		}
		var t = new tr.kapisma.net.TargetPayload();
		if (Compass.has(p)) {
			var o = compass.selected(p, compassOptions(p));
			var sp = o == null ? null : o.finder().find(p);
			if (sp != null && sp.dim() == p.level().dimension()) {
				t.has = true;
				t.x = sp.pos().getX();
				t.z = sp.pos().getZ();
				t.label = o.displayLabel(sp);
			}
		}
		if (!HUD_DEDUP) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, t);
		else {
			var snapshot = TargetState.of(t);
			if (targetGate.shouldSend(p.getUUID(),p.connection,snapshot,ticks)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, t);
				targetGate.markSent(p.getUUID(),p.connection,snapshot,ticks);
			}
		}
	}

	private void clearMinimap() {
		mapGate.clear(); targetGate.clear();
		for (UUID id : players) {
			ServerPlayer p = player(id);
			if (p != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.MapPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, tr.kapisma.net.MapPayload.off());
				if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.TargetPayload.TYPE))
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new tr.kapisma.net.TargetPayload());
			}
		}
	}

	/** Oyun basinda oyunculara bolgenin (2048 blokluk) bolge haritasi: bos harita degil, biyom onizlemeli. */
	protected void giveWorldMap() { giveWorldMap(null); }

	/** A completed preview may be supplied by a mode that prepares it before countdown. */
	protected void giveWorldMap(ItemStack prepared) {
		try {
			var wb = world.level.getWorldBorder();
			ItemStack map = prepared != null ? prepared : GameWorld.centeredMap(world.level, (int) wb.getCenterX(), (int) wb.getCenterZ(), (byte) 4, false);
			MapItemSavedData data = MapItem.getSavedData(map, world.level);
			if (prepared != null && (data == null || data.dimension != world.level.dimension() || data.scale != 4
				|| data.centerX != (int) wb.getCenterX() || data.centerZ != (int) wb.getCenterZ()))
				throw new IllegalStateException("prepared region map identity mismatch");
			if (data != null) {
				drawMapBorder(data, wb.getCenterX(), wb.getCenterZ(), wb.getSize() / 2, MapColor.COLOR_RED);
				if (minimapEndRadius() > 0) drawMapBorder(data, wb.getCenterX(), wb.getCenterZ(), minimapEndRadius(), MapColor.COLOR_GREEN);
				world.level.setMapData(map.get(DataComponents.MAP_ID), data.locked());
			}
			map.set(DataComponents.CUSTOM_NAME, Component.literal("Bölge Haritası").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GREEN)));
			for (ServerPlayer p : onlinePlayers()) {
				ItemStack copy = map.copy();
				GameTeam team = teamOf(p.getUUID());
				if (team != null) copy.set(DataComponents.LORE, new ItemLore(List.of(team.display())));
				p.getInventory().add(copy);
			}
		} catch (Exception e) {
			if (prepared != null) throw new IllegalStateException("prepared region map delivery failed", e);
			Kapisma.LOG.warn("Harita verilemedi: {}", e.toString());
		}
	}

	private static void drawMapBorder(MapItemSavedData data, double x, double z, double radius, MapColor color) {
		int scale = 1 << data.scale;
		int left = (int) Math.floor((x - radius - data.centerX) / scale) + 64;
		int right = (int) Math.floor((x + radius - data.centerX) / scale) + 64;
		int top = (int) Math.floor((z - radius - data.centerZ) / scale) + 64;
		int bottom = (int) Math.floor((z + radius - data.centerZ) / scale) + 64;
		byte packed = color.getPackedId(MapColor.Brightness.HIGH);
		for (int i = Math.max(0, left); i <= Math.min(127, right); i++) {
			if (top >= 0 && top < 128) data.setColor(i, top, packed);
			if (bottom >= 0 && bottom < 128) data.setColor(i, bottom, packed);
		}
		for (int i = Math.max(0, top); i <= Math.min(127, bottom); i++) {
			if (left >= 0 && left < 128) data.setColor(left, i, packed);
			if (right >= 0 && right < 128) data.setColor(right, i, packed);
		}
	}

	protected void giveStartKit(ServerPlayer p) {
		if (!Compass.has(p) && !compassOptions(p).isEmpty()) p.getInventory().add(Compass.item());
		int food = (int) st.num(id(), "startFood");
		if (food <= 0) return;
		// Avian et yiyemez (vegetarian gucu): esdeger doygunlukta altin havuc ver.
		if ("avian".equals(chosenOrigins.get(p.getUUID()))) {
			p.getInventory().add(new ItemStack(Items.GOLDEN_CARROT, Math.max(1, food * 3 / 4)));
		} else {
			p.getInventory().add(new ItemStack(Items.COOKED_BEEF, food));
		}
	}

	// ---- pusula (bkz. Compass) ----
	protected final Compass compass = new Compass();

	/** Pusula hedefleri (oyuna gore). Bos liste: bu modda pusula yok. */
	protected java.util.List<Compass.Opt> compassOptions(ServerPlayer p) { return java.util.List.of(); }

	/** Pusula ibresini yenileme araligi (tik). */
	protected int compassIntervalTicks() { return 5; }

	protected final Compass.Spot spotOf(ServerPlayer o) {
		return o == null || o.isSpectator() ? null : new Compass.Spot(o.blockPosition(), o.level().dimension(), o.getGameProfile().getName());
	}

	/** Takim arkadaslari: her biri ayri hedef. */
	protected final java.util.List<Compass.Opt> mateOpts(ServerPlayer p) {
		java.util.List<Compass.Opt> r = new ArrayList<>();
		GameTeam t = teamOf(p.getUUID());
		if (t == null) return r;
		for (UUID id : t.members) {
			if (id.equals(p.getUUID()) || eliminated.contains(id)) continue;
			String nm = names.getOrDefault(id, "?");
			r.add(new Compass.Opt("mate:" + id, "Takım: " + nm, h -> spotOf(player(id))));
		}
		return r;
	}

	/** Rakipler: baska takimdan, hayatta, ayni boyutta cevrimici oyuncular; p'ye yakindan uzaga. */
	protected final java.util.List<ServerPlayer> rivalsOf(ServerPlayer p) {
		GameTeam mine = teamOf(p.getUUID());
		java.util.List<ServerPlayer> r = new ArrayList<>();
		for (UUID id : players) {
			if (id.equals(p.getUUID()) || eliminated.contains(id)) continue;
			if (mine != null && mine.members.contains(id)) continue;
			ServerPlayer o = player(id);
			if (o != null && !o.isSpectator() && o.level() == p.level()) r.add(o);
		}
		r.sort(java.util.Comparator.comparingDouble(o -> o.distanceToSqr(p)));
		return r;
	}

	protected final java.util.List<Compass.Opt> rivalOpts(ServerPlayer p) {
		var opts = new ArrayList<>(java.util.List.of(
			new Compass.Opt("near", "En yakın rakip", h -> { var r = rivalsOf(h); return r.isEmpty() ? null : spotOf(r.get(0)); }),
			new Compass.Opt("far", "En uzak rakip", h -> { var r = rivalsOf(h); return r.isEmpty() ? null : spotOf(r.get(r.size() - 1)); })));
		GameTeam mine = teamOf(p.getUUID());
		for (GameTeam team : teams) {
			if (team == mine) continue;
			for (UUID id : team.members) {
				if (eliminated.contains(id)) continue;
				opts.add(new Compass.Opt("rival:" + id, names.getOrDefault(id, "Rakip"), h -> spotOf(player(id))));
			}
		}
		return opts;
	}

	protected final Compass.Opt spawnOpt(String label) {
		return new Compass.Opt("spawn", label, h -> {
			BlockPos b = spawnOf.get(h.getUUID());
			return b == null ? null : new Compass.Spot(b, gameLevel().dimension());
		});
	}

	/** Pusula hedefini kaydirir (esyaya sag tik ya da /pusula). Doner: kullanilabildi mi. */
	public boolean cycleCompass(ServerPlayer p, int dir) {
		if (phase != Phase.RUNNING || !contains(p.getUUID())) return false;
		var opts = compassOptions(p);
		if (opts.isEmpty()) return false;
		compass.cycle(p, opts, dir);
		compass.update(java.util.List.of(p), this::compassOptions, true);
		return true;
	}

	@Override
	public void onUseItem(ServerPlayer p, ItemStack stack) {
		if (Compass.isCompass(stack)) cycleCompass(p, p.isShiftKeyDown() ? -1 : 1);
	}

	@Override
	public boolean blocksPortals(net.minecraft.world.level.Level level) {
		return arenaLevel(level);
	}

	protected boolean arenaLevel(net.minecraft.world.level.Level level) { return world != null && level == world.level; }

	@Override
	public int xrayMode(net.minecraft.world.level.Level level) {
		return arenaLevel(level) ? (int) st.num(id(), "antiXray") : 0;
	}

	@Override
	public boolean cheatGuard(net.minecraft.world.level.Level level) {
		return phase == Phase.RUNNING && arenaLevel(level) && st.flag(id(), "antiCheat");
	}

	protected boolean friendlyFire() {
		return st.flag(id(), "friendlyFire");
	}

	/** Takimlari kurar (varsayilan: solo/duo). */
	protected void buildTeams(List<ServerPlayer> participants) {
		boolean duo = "DUO".equals(st.teamMode);
		teams = TeamService.assign(server, participants, duo, friendlyFire(), rnd, mgr.teamPrefs);
	}

	/** Oyunun gectigi seviye. */
	protected ServerLevel gameLevel() {
		return world.level;
	}

	protected BlockPos arenaCenter(int x, int z) { return new BlockPos(x, 0, z); }

	/** Dunyayi acar. Hata mesaji veya null. */
	protected String openWorld() {
		seedEntry = mgr.seeds.pick(st.seedClass, rnd);
		if (seedEntry == null && !"random".equals(st.seedClass))
			return "Seçilen seed sınıfında doğrulanmış dünya yok: " + mgr.seeds.displayName(st.seedClass);
		long seed = seedEntry != null ? seedEntry.seed() : rnd.nextLong();
		BlockPos center = arenaCenter(seedEntry != null ? seedEntry.spawnX() : 0, seedEntry != null ? seedEntry.spawnZ() : 0);
		int cx = center.getX(), cz = center.getZ();
		try {
			world = GameWorld.open(server, seed, cx, cz, worldOptions());
		} catch (Exception e) {
			Kapisma.LOG.error("Oyun dunyasi acilamadi", e);
			return "Dünya açılamadı: " + e.getMessage();
		}
		world.setBorder(borderRadius());
		if (seedEntry != null) mgr.seeds.markUsed(seed);
		Kapisma.LOG.info("{} dunyasi acildi: seed={} merkez=({},{}) biyom={}", id(), seed, cx, cz, seedEntry != null ? seedEntry.biome() : "?");
		return null;
	}

	/** Takimlari baslangic noktalarina dagitir (varsayilan: sinir icinde esit aralikli, anlik uretimle). */
	protected void placePlayers() {
		placeAt(world.scatter(teams.size(), scatterRadius(), rnd));
	}

	/** Takimlarin dagilacagi yaricap (blok). 0: herkes ayni noktada baslar (kimseye kiyak yok). */
	protected double scatterRadius() { return 0; }

	protected boolean sharedSpawn() { return false; }

	protected final void placeAt(List<BlockPos> pts) {
		for (GameTeam t : teams) {
			BlockPos base = pts.get(sharedSpawn() ? 0 : t.index);
			int k = 0;
			for (UUID id : t.members) {
				ServerPlayer p = player(id);
				int x = base.getX() + 2 * k;
				BlockPos alt = k == 0 || sharedSpawn() || !world.loaded(x, base.getZ())
					|| !world.level.getWorldBorder().isWithinBounds(x + 0.5, base.getZ() + 0.5) ? null : world.safeSurface(x, base.getZ());
				BlockPos pos = alt != null ? alt : base;
				k++;
				spawnOf.put(id, pos);
				if (p != null) placeOne(p, pos, t);
			}
		}
	}

	/** true: baslangic chunk'lari arka planda yuklenir (sunucu donmaz). Manhunt kapatir. */
	protected boolean pregen() { return true; }

	/** Oyuncu başlangıçları yüklenirken haritaya oyuna özel içerik eklenebilir. */
	protected boolean prepareArena() { return true; }

	/** Optional bounded work, advanced once per loading tick on the server thread. */
	protected void advanceArenaWork() {}

	protected void onArenaTimeout() {}

	private static final class Cand {
		int x, z, tries;
		boolean estimatedLand;
		BlockPos pos;
	}

	private List<Cand> cands;
	// Opt-in until integrated UHC/device acceptance.
	private static final boolean INCREMENTAL_SPAWNS = "1".equals(System.getenv("KAPISMA_INCREMENTAL_SPAWNS"));
	private int initialCount;
	private SpawnCandidateWork initialSearch;
	private SpawnCandidateWork retrySearch;
	private int retryIndex = -1;
	private int loadTicks;
	private long loadStartNs;
	/** Dunya hazirlama siniri (gercek saniye): zayif cihazda uzak bolgelerin uretimi uzun surebilir. */
	private static final int LOAD_TIMEOUT_S = 240;

	private List<int[]> takenExcept(int teamIndex) {
		List<int[]> taken = new ArrayList<>();
		if (cands != null) for (int j = 0; j < cands.size(); j++) if (j != teamIndex) taken.add(new int[] {cands.get(j).x, cands.get(j).z});
		return taken;
	}

	private Cand newCand(int teamIndex, int tries) {
		int[] xz = world.candidate(scatterRadius(), rnd, takenExcept(teamIndex), GameWorld.minSepFor(scatterRadius(), teams.size()));
		return registerCand(xz, tries);
	}

	private Cand registerCand(int[] xz, int tries) {
		Cand c = new Cand();
		c.tries = tries;
		c.estimatedLand = xz != null;
		c.x = xz != null ? xz[0] : world.centerX;
		c.z = xz != null ? xz[1] : world.centerZ;
		world.force(c.x, c.z, true);
		return c;
	}

	private void startLoading() {
		cands = new ArrayList<>();
		loadTicks = 0;
		loadStartNs = System.nanoTime();
		world.force(world.centerX, world.centerZ, true);   // merkez chunk once: zaman asiminda yedek nokta hazir olsun
		initialCount = sharedSpawn() ? 1 : teams.size();
		initialSearch = null;
		cancelRetry();
		long timing = GameWorld.timingStart();
		try {
			if (!INCREMENTAL_SPAWNS)
				for (int i = 0; i < initialCount; i++) cands.add(newCand(i, 0));
			else Kapisma.LOG.info("ULTRA_SPAWN_MODE incremental=true teams={}", initialCount);
		} finally {
			GameWorld.timingEnd("arena.initial_candidates", timing, 5);
		}
		Msg.broadcast(server, "Dünya hazırlanıyor, birkaç saniye...");
	}

	private void advanceInitialCandidates() {
		if (initialSearch == null)
			initialSearch = GameWorld.candidateWork(world.centerX, world.centerZ, scatterRadius(), rnd,
				takenExcept(cands.size()), GameWorld.minSepFor(scatterRadius(), teams.size()));
		GameWorld.advanceCandidate(world.level, initialSearch);
		if (initialSearch.complete()) {
			cands.add(registerCand(initialSearch.result(), 0));
			initialSearch = null;
			if (cands.size() == initialCount)
				Kapisma.LOG.info("ULTRA_SPAWN_COMPLETE teams={}", initialCount);
		}
	}

	private void cancelRetry() {
		if (retrySearch != null) retrySearch.cancel();
		retrySearch = null;
		retryIndex = -1;
	}

	private void scheduleRetry(int index) {
		if (retrySearch != null) throw new IllegalStateException("retry already pending");
		retrySearch = GameWorld.candidateWork(world.centerX, world.centerZ, scatterRadius(), rnd,
			takenExcept(index), GameWorld.minSepFor(scatterRadius(), teams.size()));
		retryIndex = index;
	}

	private void advanceRetry() {
		GameWorld.advanceCandidate(world.level, retrySearch);
		if (!retrySearch.complete()) return;
		Cand old = cands.get(retryIndex);
		// Keep the old pin until a replacement is registered, including same-chunk retries.
		Cand next = registerCand(retrySearch.result(), old.tries + 1);
		cands.set(retryIndex, next);
		world.force(old.x, old.z, false);
		Kapisma.LOG.info("ULTRA_SPAWN_RETRY team={} attempt={}", retryIndex, next.tries);
		cancelRetry();
	}

	/** Unique unresolved player chunks; loaded and shared chunks do not consume admission capacity. */
	protected int pendingSpawnChunks() {
		if (cands == null) return 0;
		var unique = new java.util.HashSet<Long>();
		for (Cand c : cands) if (c.pos == null && !world.loaded(c.x, c.z))
			unique.add(net.minecraft.world.level.ChunkPos.asLong(c.x >> 4, c.z >> 4));
		return unique.size();
	}

	private void loadingTick() {
		if (cands == null) return;
		loadTicks++;
		long waited = (System.nanoTime() - loadStartNs) / 1_000_000_000L;
		if (waited > LOAD_TIMEOUT_S) {      // zaman asimi: hazir olmayanlar merkeze (sinirli, senkron uretim YOK)
			if (cands.size() != initialCount) {
				if (initialSearch != null) { initialSearch.cancel(); initialSearch = null; }
				stop("Başlangıç konumları zamanında hesaplanamadı.");
				return;
			}
			cancelRetry();
			onArenaTimeout();
			if (closed) return;
			if (!world.loaded(world.centerX, world.centerZ)) {
				stop("Başlangıç parçası zamanında yüklenemedi.");
				return;
			}
			List<BlockPos> pts = new ArrayList<>();
			for (Cand c : cands) {
				pts.add(c.pos != null ? c.pos : world.centerSpot());
			}
			Kapisma.LOG.warn("Dunya hazirlama zaman asimi ({} sn): hazir olmayan baslangiclar merkeze alindi", waited);
			beginCountdown(pts);
			return;
		}
		if (loadTicks % 200 == 0) {
			int ready = 0;
			for (Cand c : cands) if (c.pos != null) ready++;
			Msg.broadcast(server, "Dünya hazırlanıyor... (" + ready + "/" + cands.size() + " başlangıç hazır, " + waited + " sn)");
		}
		if (cands.size() < initialCount) {
			try { advanceInitialCandidates(); }
			catch (RuntimeException ex) {
				if (initialSearch != null) { initialSearch.cancel(); initialSearch = null; }
				Kapisma.LOG.error("Incremental spawn candidate failed", ex);
				stop("Başlangıç konumları hesaplanamadı.");
			}
			return; // no arena preview or retry shares this tick's search budget
		}
		if (retrySearch != null) {
			try { advanceRetry(); }
			catch (RuntimeException ex) {
				cancelRetry();
				Kapisma.LOG.error("Incremental spawn retry failed", ex);
				stop("Başlangıç konumu yeniden hesaplanamadı.");
			}
			return; // retry search owns this tick's budget
		}
		advanceArenaWork();
		if (closed || loadTicks % 4 != 0) return;
		long timing = GameWorld.timingStart();
		boolean done;
		try {
			done = prepareArena();
		} finally {
			GameWorld.timingEnd("arena.prepare", timing, 20);
		}
		if (closed) return;
		for (int i = 0; i < cands.size(); i++) {
			Cand c = cands.get(i);
			if (c.pos != null) continue;
			if (!world.loaded(c.x, c.z)) { done = false; continue; }
			long surfaceTiming = GameWorld.timingStart();
			BlockPos pos;
			try {
				pos = GameWorld.safeSurfaceLoaded(world.level, c.x, c.z, c.estimatedLand);
			} finally {
				GameWorld.timingEnd("arena.safe_surface", surfaceTiming, 20);
			}
			if (pos != null) { c.pos = pos; continue; }
			if (c.tries >= 10) {            // cok denendi: merkeze dus
				if (!world.loaded(world.centerX, world.centerZ)) { done = false; continue; }
				c.pos = world.centerSpot();
				continue;
			}
			if (INCREMENTAL_SPAWNS) {
				try { scheduleRetry(i); }
				catch (RuntimeException ex) {
					cancelRetry();
					Kapisma.LOG.error("Incremental spawn retry setup failed", ex);
					stop("Başlangıç konumu yeniden hesaplanamadı.");
				}
				return;
			}
			world.force(c.x, c.z, false);
			cands.set(i, newCand(i, c.tries + 1));
			done = false;
		}
		if (!done) return;
		List<BlockPos> pts = new ArrayList<>();
		for (Cand c : cands) pts.add(c.pos);
		beginCountdown(pts);
	}

	private void beginCountdown(List<BlockPos> pts) {
		placeAt(pts);
		for (Cand c : cands) world.force(c.x, c.z, false);
		cands = null;
		world.force(world.centerX, world.centerZ, false);
		phase = Phase.COUNTDOWN;
		ticks = 0;
	}

	/** Tek oyuncuyu hazirlar ve isinlar. */
	protected void placeOne(ServerPlayer p, BlockPos pos, GameTeam t) {
		spawnOf.put(p.getUUID(), pos);
		placed.add(p.getUUID());
		Lobby.resetPlayer(p);
		p.closeContainer();
		p.setGameMode(GameType.SURVIVAL);
		p.teleportTo(gameLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360f, 0f);
		p.setRespawnPosition(gameLevel().dimension(), pos, 0f, true, false);
		giveStartKit(p);
		freeze(p);
		String o = chosenOrigins.get(p.getUUID());
		Msg.tell(p, "Takımın: " + t.name + (o != null ? " | Irkın: " + OriginService.label(o) : ""));
	}

	protected final void freeze(ServerPlayer p) {
		frozenAt.putIfAbsent(p.getUUID(), p.position());
		var lock = new AttributeModifier(START_FREEZE, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		p.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(lock);
		p.getAttribute(Attributes.JUMP_STRENGTH).addOrUpdateTransientModifier(lock);
	}

	protected final void unfreeze(ServerPlayer p) {
		frozenAt.remove(p.getUUID());
		p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(START_FREEZE);
		p.getAttribute(Attributes.JUMP_STRENGTH).removeModifier(START_FREEZE);
	}

	private void holdFrozenPlayers() {
		for (var entry : frozenAt.entrySet()) {
			ServerPlayer p = player(entry.getKey());
			if (p == null || p.isSpectator()) continue;
			Vec3 anchor = entry.getValue();
			p.fallDistance = 0;
			if (p.level() != gameLevel() || p.position().distanceToSqr(anchor) > 0.04) {
				p.setDeltaMovement(Vec3.ZERO);
				p.teleportTo(gameLevel(), anchor.x, anchor.y, anchor.z, p.getYRot(), p.getXRot());
			}
		}
	}

	private void warnOutsideBorder() {
		for (ServerPlayer p : onlinePlayers()) {
			if (p.isSpectator() || (world != null && !arenaLevel(p.level()))) continue;
			UUID id = p.getUUID();
			if (p.level().getWorldBorder().isWithinBounds(p.getX(), p.getZ())) {
				outsideBorder.remove(id);
			} else if (outsideBorder.add(id)) {
				Msg.tell(p, "Dünya sınırının dışındasın. Hasar almamak için bariyerin içine dön!");
				Msg.title(p, "SINIR DIŞINDASIN", "Bariyerin içine dön", 0, 45, 10);
			}
		}
	}

	// ---- akis ----
	@Override
	public String prepare(List<ServerPlayer> participants) {
		if (participants.isEmpty()) return "Oyuncu yok.";
		for (ServerPlayer p : participants) { players.add(p.getUUID()); names.put(p.getUUID(), p.getGameProfile().getName()); }

		String err = openWorld();
		if (err != null) {
			players.clear();
			return err;
		}
		buildTeams(participants);
		if (!"KATIL".equals(id())) {
			for (GameTeam team : teams) {
				if (team.members.isEmpty()) continue;
				Msg.broadcast(server, Msg.prefix().append(team.display()).append(Component.literal("  " +
					String.join(", ", team.members.stream().map(names::get).toList()))));
			}
		}

		String om = st.originMode.getOrDefault(id(), "RANDOM");
		switch (om) {
			case "RANDOM" -> {
				chosenOrigins.putAll(OriginService.assignRandom(server, participants, st, rnd));
				launch();
			}
			case "SELECT" -> {
				phase = Phase.SELECTING;
				ticks = 0;
				selectLimit = 45 * 20;
				Msg.broadcast(server, "Irkını seç! (45 sn) Seçmezsen rastgele verilir.");
				List<String> pool = OriginService.allowed(st);
				for (ServerPlayer p : participants) {
						if (isBot(p)) {          // test botlari (Carpet) menu acamaz: otomatik rastgele
							chosenOrigins.put(p.getUUID(), pool.get(rnd.nextInt(pool.size())));
							continue;
						}
						mgr.menus.openOriginPick(p, pool, o -> {
						chosenOrigins.put(p.getUUID(), o);
						Msg.broadcast(server, p.getGameProfile().getName() + " seçti: " + OriginService.label(o));
					});
					}
			}
			default -> {
				for (ServerPlayer p : participants) OriginService.reset(server, p);
				launch();
			}
		}
		return null;
	}

	/** Carpet sahte oyuncusu gibi gercek istemcisi olmayan oyuncu. */
	protected static boolean isBot(ServerPlayer p) {
		return p.getClass().getName().toLowerCase().contains("fake");
	}

	private void finishSelection() {
		phase = Phase.PREP;                 // aksi halde SELECTING fazinda her tik yeniden cagrilir
		List<String> pool = OriginService.allowed(st);
		for (ServerPlayer p : onlinePlayers()) {
			String o = chosenOrigins.get(p.getUUID());
			if (o == null) o = pool.get(rnd.nextInt(pool.size()));
			chosenOrigins.put(p.getUUID(), o);
			OriginService.set(server, p, o);
			p.closeContainer();
		}
		launch();
	}

	private void announceOrigins() {
		if (chosenOrigins.isEmpty()) return;
		StringBuilder sb = new StringBuilder("Irklar: ");
		for (ServerPlayer p : onlinePlayers()) {
			String o = chosenOrigins.get(p.getUUID());
			if (o != null) sb.append(p.getGameProfile().getName()).append('=').append(OriginService.label(o)).append("  ");
		}
		Msg.broadcast(server, sb.toString());
	}

	private void launch() {
		announceOrigins();
		if (!pregen()) {
			placePlayers();
			phase = Phase.COUNTDOWN;
			ticks = 0;
			return;
		}
		startLoading();        // faz PREP kalir; loadingTick bitince COUNTDOWN'a gecer
	}

	@Override
	public void tick() {
		if (closed) return;
		if (!frozenAt.isEmpty()) holdFrozenPlayers();
		switch (phase) {
			case PREP -> loadingTick();
			case SELECTING -> {
				ticks++;
				boolean all = onlinePlayers().stream().allMatch(p -> chosenOrigins.containsKey(p.getUUID()));
				if (all || ticks >= selectLimit) finishSelection();
			}
			case COUNTDOWN -> {
				ticks++;
				int left = 10 - ticks / 20;
				if (ticks % 20 == 0 && left > 0) {
					for (ServerPlayer p : onlinePlayers()) {
						Msg.title(p, String.valueOf(left), title() + " başlıyor", 0, 25, 5);
						if (left <= 5) Msg.sound(p, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, left <= 3 ? 1.5f : 1f);
					}
				}
				if (ticks >= 200) {
					for (ServerPlayer p : onlinePlayers()) {
						unfreeze(p);
						Msg.title(p, "BAŞLA!", title(), 0, 30, 10);
						Msg.sound(p, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 1f);
					}
					frozenAt.clear();
					phase = Phase.RUNNING;
					ticks = 0;
					setupHealth();
					onStart();
				}
			}
			case RUNNING -> {
				ticks++;
				runTicks = ticks;
				// A disconnect callback may arrive after a mod has already removed the
				// player. Keep a server-thread last-known in-arena position as a fallback.
				for (ServerPlayer p : onlinePlayers()) {
					if (isAlive(p.getUUID()) && !p.isSpectator() && spectatorLevel(p.level())) {
						var old = lastSeenSpots.get(p.getUUID());
						var pos = p.blockPosition();
						if (old == null || old.dimension() != p.level().dimension() || !old.pos().equals(pos))
							lastSeenSpots.put(p.getUUID(), net.minecraft.core.GlobalPos.of(p.level().dimension(), pos));
					}
				}
				trackOffline();
				if (phase != Phase.RUNNING) return;
				long timing = GameWorld.timingStart();
				try { onRunningTick(); }
				finally { GameWorld.timingEnd("running.mode", timing, 2); }
				if (phase != Phase.RUNNING) return;
				if (ticks % 20 == 0) warnOutsideBorder();
				if (ticks % 10 == 0) {
					timing = GameWorld.timingStart();
					try { sendMinimap(); }
					finally { GameWorld.timingEnd("running.minimap", timing, 2); }
				}
				if (ticks % compassIntervalTicks() == 0) {
					timing = GameWorld.timingStart();
					try { compass.update(onlinePlayers(), this::compassOptions, true); }
					finally { GameWorld.timingEnd("running.compass", timing, 2); }
				}
			}
			case ENDED -> {
				ticks++;
				if (ticks >= 15 * 20) close();
			}
			default -> {}
		}
	}

	private void trackOffline() {
		if (!eliminationMode()) return;
		boolean eliminatedNow = false;
		for (UUID id : players) {
			if (eliminated.contains(id)) continue;
			if (player(id) == null) {
				int n = offlineTicks.merge(id, 1, Integer::sum);
				if (n >= 3 * 60 * 20) {
					eliminated.add(id);
					var spot = disconnectedSpots.getOrDefault(id, lastSeenSpots.get(id));
					if (spot != null) deathSpots.putIfAbsent(id, spot);
					eliminatedNow = true;
					Msg.broadcast(server, "Bağlantısı kopan oyuncu elendi.");
				}
			} else {
				offlineTicks.remove(id);
			}
		}
		if (eliminatedNow) checkWinner();
	}

	// ---- elenme ----
	@Override
	public boolean allowDeath(ServerPlayer p, DamageSource src) {
		if (!contains(p.getUUID()) || phase != Phase.RUNNING || !eliminationMode()) return true;
		if (eliminated.contains(p.getUUID())) return false;
		onEliminated(p, src);
		eliminated.add(p.getUUID());
		p.getInventory().dropAll();
		p.setHealth(p.getMaxHealth());
		enterSpectator(p);
		Msg.broadcast(server, src.getLocalizedDeathMessage(p).copy().withStyle(ChatFormatting.RED));
		Msg.soundAll(server, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1f);
		Msg.title(p, "Elendin", "Seyirci olarak izleyebilirsin", 5, 50, 15);
		checkWinner();
		return false;       // gercek olumu iptal et: oyuncu seyirci olarak kalir
	}

	protected final void enterSpectator(ServerPlayer p) {
		var previous = disconnectedSpots.remove(p.getUUID());
		if (previous != null) deathSpots.putIfAbsent(p.getUUID(), previous);
		else if (spectatorLevel(p.level()))
			deathSpots.putIfAbsent(p.getUUID(), net.minecraft.core.GlobalPos.of(p.level().dimension(), p.blockPosition()));
		p.setGameMode(GameType.SPECTATOR);
		tr.kapisma.voice.VoiceBridge.refresh(server);
		ItemStack compass = new ItemStack(Items.COMPASS);
		compass.set(DataComponents.CUSTOM_NAME, Component.literal("İzleyici Pusulası"));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(SPECTATOR_COMPASS, true);
		compass.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		p.getInventory().setItem(0, compass);
		p.getInventory().selected = 0;
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
		Msg.tell(p, "İzleyici pusulasına sağ tıkla veya /izle yaz.");
	}

	public final boolean openSpectatorMenu(ServerPlayer p) {
		if (closed || mgr.current != this || !contains(p.getUUID()) || !p.isSpectator()) return false;
		mgr.menus.openSpectators(p, this);
		return true;
	}

	private boolean spectatorLevel(net.minecraft.world.level.Level level) {
		return world != null ? arenaLevel(level) : level == server.overworld()
			|| level == server.getLevel(net.minecraft.world.level.Level.NETHER)
			|| level == server.getLevel(net.minecraft.world.level.Level.END);
	}

	private net.minecraft.core.GlobalPos eliminatedSpot(UUID id) {
		return deathSpots.getOrDefault(id, disconnectedSpots.getOrDefault(id, lastSeenSpots.get(id)));
	}

	public final boolean spectatorCanVisit(UUID id) {
		if (closed || !contains(id)) return false;
		if (eliminated.contains(id)) {
			var spot = eliminatedSpot(id);
			return spot != null && server.getLevel(spot.dimension()) != null && spectatorLevel(server.getLevel(spot.dimension()));
		}
		ServerPlayer target = player(id);
		return target != null && !target.isSpectator() && target.isAlive() && spectatorLevel(target.level());
	}

	public final boolean visitSpectatorTarget(ServerPlayer viewer, UUID id) {
		if (closed || mgr.current != this || !viewer.isSpectator() || !contains(viewer.getUUID()) || !spectatorCanVisit(id)) return false;
		if (eliminated.contains(id)) {
			var spot = eliminatedSpot(id);
			ServerLevel level = server.getLevel(spot.dimension());
			viewer.teleportTo(level, spot.pos().getX() + 0.5, spot.pos().getY() + 1, spot.pos().getZ() + 0.5, viewer.getYRot(), viewer.getXRot());
		} else {
			ServerPlayer target = player(id);
			viewer.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
		}
		return true;
	}

	@Override
	public void onDisconnect(ServerPlayer p) {
		forgetHud(p.getUUID());
		if (!closed && phase == Phase.RUNNING && isAlive(p.getUUID()) && spectatorLevel(p.level()))
			disconnectedSpots.put(p.getUUID(), net.minecraft.core.GlobalPos.of(p.level().dimension(), p.blockPosition()));
	}

	/** Alt sinif: ek islem (kafa dusurme vb.). */
	protected void onEliminated(ServerPlayer p, DamageSource src) {}

	protected void checkWinner() {
		if (phase != Phase.RUNNING || !eliminationMode()) return;
		List<GameTeam> alive = aliveTeams();
		if (alive.size() <= 1) finish(alive.isEmpty() ? null : alive.get(0), "Son takım kaldı");
	}

	protected void finish(GameTeam winnerTeam, String why) {
		if (phase == Phase.ENDED) return;
		winner = winnerTeam;
		phase = Phase.ENDED;
		ticks = 0;
		Component msg = winnerTeam == null
			? Component.literal("Oyun bitti: kazanan yok.").withStyle(ChatFormatting.GRAY)
			: Component.literal("KAZANAN: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(winnerTeam.display());
		Msg.broadcast(server, Msg.prefix().append(msg));
		for (ServerPlayer p : onlinePlayers()) {
			Msg.title(p, winnerTeam == null ? "Oyun bitti" : winnerTeam.name + " kazandı!", why, 5, 80, 20);
			p.setInvulnerable(true);
			GameTeam mine = teamOf(p.getUUID());
			boolean won = winnerTeam != null && mine == winnerTeam;
			Msg.sound(p, won ? net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1f, 1f);
		}
		sendSummary(winnerTeam, why);
		awardScores(winnerTeam);
		Kapisma.LOG.info("{} bitti: {} ({})", id(), winnerTeam == null ? "kazanan yok" : winnerTeam.name, why);
	}

	/** Kalici puan: katilim + oldurme + (en az 2 takimli oyunda) galibiyet. Bot (test) oyunculari sayilmaz. */
	private void awardScores(GameTeam winnerTeam) {
		var sc = tr.kapisma.lobby.Lobby.scores;
		if (sc == null) return;
		for (GameTeam t : teams) {
			for (UUID id : t.members) {
				ServerPlayer p = player(id);
				String nm = names.getOrDefault(id, id.toString().substring(0, 6));
				if ((p != null && isBot(p)) || nm.matches("Bot[0-9]*")) continue;       // test botlari puan almaz
				boolean won = winnerTeam != null && t == winnerTeam && teams.size() >= 2;
				int pts = sc.gameResult(id.toString(), nm, kills.getOrDefault(id, 0), won);
				if (p != null) p.sendSystemMessage(Component.literal("Kapışma puanı: +" + pts + (won ? " (galibiyet dahil)" : "")).withStyle(ChatFormatting.GREEN));
			}
		}
		tr.kapisma.lobby.Lobby.boardsDirty = true;
	}

	/** Alt sinif: ozet ekranina eklenecek oyuna ozel satirlar. */
	protected List<String> summaryExtra() { return List.of(); }

	private void sendSummary(GameTeam winnerTeam, String why) {
		Msg.broadcast(server, Component.literal("━━━━━ SONUÇ ━━━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		Msg.broadcast(server, Component.literal(title() + " | süre " + Msg.mmss(ticks0() / 20) + " | " + why).withStyle(ChatFormatting.YELLOW));
		List<GameTeam> order = new ArrayList<>(teams);
		order.sort((a, b) -> Integer.compare(teamKills(b) + (b == winnerTeam ? 1000 : 0), teamKills(a) + (a == winnerTeam ? 1000 : 0)));
		int rank = 1;
		for (GameTeam t : order) {
			net.minecraft.network.chat.MutableComponent line = Component.literal((rank++) + ". ").withStyle(ChatFormatting.GOLD)
				.append(t.display()).append(Component.literal("  "));
			for (UUID id : t.members) {
				String nm = names.getOrDefault(id, id.toString().substring(0, 6));
				String o = chosenOrigins.get(id);
				line.append(Component.literal(nm).withStyle(s -> s.withColor(t.color).withUnderlined(true)
					.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/istatistik " + nm))
					.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
						Component.literal("Tıkla: " + nm + " kimi öldürdü, kim öldürdü")))));
				line.append(Component.literal((o != null ? " (" + OriginService.label(o) + ")" : "") + " öldürme " + kills.getOrDefault(id, 0)
					+ " ölüm " + deaths.getOrDefault(id, 0) + (eliminated.contains(id) ? " ✖" : "") + "   ").withStyle(ChatFormatting.WHITE));
			}
			Msg.broadcast(server, line);
		}
		for (String extra : summaryExtra()) Msg.broadcast(server, Component.literal(extra).withStyle(ChatFormatting.AQUA));
		Msg.broadcast(server, Component.literal("İsme tıkla ya da /istatistik [oyuncu]: kim kimi kaç kez öldürdü").withStyle(ChatFormatting.DARK_GRAY));
	}

	private int teamKills(GameTeam t) {
		int n = 0;
		for (UUID id : t.members) n += kills.getOrDefault(id, 0);
		return n;
	}

	/** Oyunun (RUNNING fazi) suresi: ENDED'e gecerken ticks sifirlandigi icin ayri sayac. */
	private int runTicks;
	protected int ticks0() { return runTicks; }

	public GameTeam winner() {
		return winner;
	}

	// ---- kapanis ----
	/** Alt sinif: kapanista ek temizlik (dunya kurallari vb.). */
	protected void onClose() {}

	private void close() {
		if (closed) return;
		closed = true;
		if (initialSearch != null) { initialSearch.cancel(); initialSearch = null; }
		cancelRetry();
		for (UUID id : players) {
			ServerPlayer p = player(id);
			if (p != null) {
				unfreeze(p);
				Lobby.send(server, p);
				OriginService.reset(server, p);
			}
		}
		clearMinimap();
		TeamService.clear(server);
		clearHealth();
		onClose();
		if (world != null) world.delete();
		Kapisma.LOG.info("{} kapandi, lobiye donuldu", id());
	}

	@Override
	public void stop(String reason) {
		if (closed) return;
		Msg.broadcast(server, "Oyun durduruldu: " + reason);
		close();
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public void onJoin(ServerPlayer p) {
		if (!contains(p.getUUID())) return;
		forgetHud(p.getUUID());
		offlineTicks.remove(p.getUUID());
		if (!eliminated.contains(p.getUUID())) disconnectedSpots.remove(p.getUUID());
		else {
			var spot = eliminatedSpot(p.getUUID());
			if (spot != null) deathSpots.putIfAbsent(p.getUUID(), spot);
		}
		BlockPos spawn = spawnOf.get(p.getUUID());
		if (world != null && spawn != null && !arenaLevel(p.level())) {
			if (!eliminated.contains(p.getUUID()) && !placed.contains(p.getUUID()) && phase != Phase.ENDED) {
				placeOne(p, spawn, teamOf(p.getUUID()));
				if (phase == Phase.RUNNING) unfreeze(p);
			} else p.teleportTo(gameLevel(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
		}
		if (eliminated.contains(p.getUUID())) enterSpectator(p);
		if (phase == Phase.COUNTDOWN && frozenAt.containsKey(p.getUUID())) freeze(p);
		else if (phase != Phase.RUNNING) unfreeze(p);
	}

	@Override
	public void onRespawn(ServerPlayer p) {
		if (closed || !contains(p.getUUID())) return;
		if (eliminated.contains(p.getUUID())) { enterSpectator(p); return; }
		if (phase == Phase.ENDED) return;
		ServerLevel lvl = gameLevel();
		BlockPos pos = spawnOf.getOrDefault(p.getUUID(), lvl.getSharedSpawnPos());
		if (p.level() != lvl && !arenaLevel(p.level())) p.teleportTo(lvl, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		if (phase == Phase.RUNNING && !eliminated.contains(p.getUUID()) && !Compass.has(p) && !compassOptions(p).isEmpty()) p.getInventory().add(Compass.item());
	}

	@Override
	public String originOf(UUID id) { return chosenOrigins.get(id); }

	protected List<ServerPlayer> everyone() {
		return new ArrayList<>(server.getPlayerList().getPlayers());
	}
}
