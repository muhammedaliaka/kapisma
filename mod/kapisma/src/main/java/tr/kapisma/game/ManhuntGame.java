package tr.kapisma.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.Heightmap;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;
import tr.kapisma.lobby.Lobby;
import tr.kapisma.team.TeamService;
import tr.kapisma.team.TeamService.GameTeam;

/**
 * Manhunt: runner(lar) Ender Dragon'u oldurur, hunter'lar runner'lari avlar.
 * Nether/End gerektigi icin ana dunyada oynanir; her Manhunt TAZE bir dunya ister
 * (kullanilmis dunyada yeni dunya icin sunucu yeniden baslatilir, bkz. GameManager.requestNewWorld).
 */
public final class ManhuntGame extends WorldGame {
	private GameTeam runnerTeam, hunterTeam;
	private final List<UUID> runnerIds = new ArrayList<>();

	/** true: Katil Kim (gizli katil, ayni skin, masum oluyorsa elenir); false: klasik Manhunt. */
	private final boolean katil;

	public ManhuntGame(GameManager mgr, boolean katil) {
		super(mgr);
		this.katil = katil;
	}

	@Override
	public String id() { return katil ? "KATIL" : "MANHUNT"; }

	@Override
	public String title() { return katil ? "Katil Kim" : "Manhunt"; }

	@Override
	public boolean globalChat() { return katil; }

	private void sendDisguise(ServerPlayer p, boolean on) {
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.DisguisePayload.TYPE)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new tr.kapisma.net.DisguisePayload(on));
		}
	}

	@Override
	public void onJoin(ServerPlayer p) {
		super.onJoin(p);
		if (katil && contains(p.getUUID()) && phase != Phase.ENDED) sendDisguise(p, true);
	}

	private void buildKatilTeams(List<ServerPlayer> participants) {
		List<ServerPlayer> pool = new ArrayList<>(participants);
		Collections.shuffle(pool, rnd);
		int n = Math.max(1, Math.min((int) st.num("KATIL", "killers"), Math.max(1, pool.size() / 3)));
		List<ServerPlayer> killers = new ArrayList<>(pool.subList(0, Math.min(n, pool.size())));
		List<ServerPlayer> innocents = new ArrayList<>(pool);
		innocents.removeAll(killers);
		teams = TeamService.assignRolesHidden(server, innocents, killers, friendlyFire());
		runnerTeam = teams.get(0);
		hunterTeam = teams.get(1);
		for (ServerPlayer r : innocents) runnerIds.add(r.getUUID());
		Kapisma.LOG.info("Katil Kim: {} katil secildi", killers.size());       // isim yazilmaz: host da oyuncu
	}

	@Override
	protected GameWorld.Options worldOptions() { return null; }

	@Override
	protected double borderRadius() { return 0; }

	@Override
	protected boolean eliminationMode() { return false; }

	@Override
	protected ServerLevel gameLevel() { return server.overworld(); }

	@Override
	public int xrayMode(net.minecraft.world.level.Level level) {
		int m = (int) st.num(id(), "antiXray");
		if (level == server.overworld()) return m;
		if (level.dimension() == net.minecraft.world.level.Level.NETHER) return Math.min(m, 1);
		return 0;
	}

	@Override
	public boolean cheatGuard(net.minecraft.world.level.Level level) {
		if (phase != Phase.RUNNING || !st.flag(id(), "antiCheat")) return false;
		var d = level.dimension();
		return level == server.overworld() || d == net.minecraft.world.level.Level.NETHER || d == net.minecraft.world.level.Level.END;
	}

	public static Path usedFlag(net.minecraft.server.MinecraftServer s) {
		return s.getWorldPath(LevelResource.ROOT).resolve("kapisma_manhunt_used");
	}

	public static boolean worldUsed(net.minecraft.server.MinecraftServer s) {
		return Files.exists(usedFlag(s));
	}

	@Override
	protected String openWorld() {
		if (worldUsed(server)) return "Bu dünya daha önce Manhunt için kullanıldı. Yeni dünya gerekiyor.";
		try {
			Files.writeString(usedFlag(server), "used\n");
		} catch (Exception e) {
			Kapisma.LOG.warn("Manhunt bayragi yazilamadi", e);
		}
		mgr.seeds.markUsed(server.overworld().getSeed());
		Lobby.applyNormalRules(server);
		Kapisma.LOG.info("Manhunt ana dunyada: seed={}", server.overworld().getSeed());
		return null;
	}

	@Override
	protected void buildTeams(List<ServerPlayer> participants) {
		if (katil) { buildKatilTeams(participants); return; }
		List<ServerPlayer> pool = new ArrayList<>(participants);
		Collections.shuffle(pool, rnd);
		List<ServerPlayer> runners = new ArrayList<>();
		for (ServerPlayer p : pool) {
			if (st.runnerNames.stream().anyMatch(n -> n.equalsIgnoreCase(p.getGameProfile().getName()))) runners.add(p);
		}
		// Host secimi: en az bir hunter kalmali. Secim yoksa rastgele (kural: runner sayisi).
		if (runners.size() >= pool.size() && pool.size() > 1) runners = new ArrayList<>(runners.subList(0, pool.size() - 1));
		if (runners.isEmpty()) {
			int want = (int) st.num("MANHUNT", "runners");
			int n = Math.max(1, Math.min(want, pool.size() > 1 ? pool.size() - 1 : 1));
			runners = new ArrayList<>(pool.subList(0, n));
		}
		List<ServerPlayer> hunters = new ArrayList<>(pool);
		hunters.removeAll(runners);
		teams = TeamService.assignRoles(server, runners, hunters, friendlyFire());
		runnerTeam = teams.get(0);
		hunterTeam = teams.get(1);
		for (ServerPlayer r : runners) runnerIds.add(r.getUUID());
		if (!st.runnerNames.isEmpty()) Msg.broadcast(server, "Runner'ları host seçti.");
		Msg.broadcast(server, Msg.prefix().append(Component.literal("Runner: ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(String.join(", ", runners.stream().map(p -> p.getGameProfile().getName()).toList())).withStyle(ChatFormatting.WHITE)));
	}

	@Override
	protected boolean pregen() { return false; }

	@Override
	protected void placePlayers() {
		ServerLevel ow = server.overworld();
		BlockPos sp = ow.getSharedSpawnPos();
		if (katil) {
			placeKatil(ow, sp);
			return;
		}
		ow.getChunk(sp.getX() >> 4, sp.getZ() >> 4);
		int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
		BlockPos pos = new BlockPos(sp.getX(), y, sp.getZ());
		for (GameTeam t : teams) {
			for (UUID id : t.members) {
				ServerPlayer p = player(id);
				if (p != null) placeOne(p, pos, t);
			}
		}
	}

	private boolean isRunner(UUID id) {
		return runnerIds.contains(id);
	}

	@Override
	protected List<Compass.Opt> compassOptions(ServerPlayer p) {
		if (katil) {
			if (!st.flag("KATIL", "compass")) return List.of();
			return List.of(
				new Compass.Opt("near", "En yakın oyuncu", h -> { var o = othersNear(h); return o.isEmpty() ? null : spotOf(o.get(0)); }),
				new Compass.Opt("far", "En uzak oyuncu", h -> { var o = othersNear(h); return o.isEmpty() ? null : spotOf(o.get(o.size() - 1)); }));
		}
		if (isRunner(p.getUUID())) return List.of();
		List<Compass.Opt> r = new ArrayList<>();
		r.add(new Compass.Opt("near", "En yakın runner", h -> { Trace tr = traceFor(h); return tr == null ? null : new Compass.Spot(tr.pos(), h.level().dimension()); }));
		r.add(new Compass.Opt("far", "En uzak runner", h -> spotOf(farthestRunner(h))));
		for (UUID id : runnerIds) r.add(new Compass.Opt("run:" + id, "Runner: " + names.getOrDefault(id, "?"), h -> runnerSpot(id, h)));
		r.addAll(mateOpts(p));
		return r;
	}

	@Override
	protected int compassIntervalTicks() {
		return katil ? 10 : Math.max(5, (int) st.num("MANHUNT", "compassIntervalSec") * 20);
	}

	/** Belirli runner: ayni boyutta canli konum, degilse o boyuttaki son izi (portal). */
	private Compass.Spot runnerSpot(UUID id, ServerPlayer h) {
		if (eliminated.contains(id)) return null;
		ServerPlayer r = player(id);
		if (r != null && !r.isSpectator() && r.level() == h.level()) return spotOf(r);
		var m = lastSeen.get(id);
		BlockPos b = m == null ? null : m.get(h.level().dimension());
		return b == null ? null : new Compass.Spot(b, h.level().dimension());
	}

	@Override
	protected void onStart() {
		setupBoard();
		if (katil) {
			var rules = server.getGameRules();
			rules.getRule(net.minecraft.world.level.GameRules.RULE_SHOWDEATHMESSAGES).set(false, server);   // olum mesaji katili ele verir
			for (ServerPlayer p : onlinePlayers()) sendDisguise(p, true);
		}
		int hs = katil ? 0 : (int) st.num(id(), "headStartSec");        // Katil Kim'de kimse farkli davranmaz (ele verir)
		if (hs > 0) {
			for (UUID id : hunterTeam.members) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, hs * 20, 255, false, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.JUMP, hs * 20, 128, false, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, hs * 20, 0, false, false, false));
			}
		}
		if (katil) {
			for (UUID id : hunterTeam.members) {
				ServerPlayer k = player(id);
				if (k == null) continue;
				Msg.title(k, "KATİLSİN", "Masumları öldür; Ejderha'yı koruma!", 5, 80, 20);
				Msg.tell(k, "Sen KATİLSİN. Kimse bilmiyor. Masumlar Ejderha'yı öldürmeden hepsini avla. Ölürsen yeniden doğarsın." + killerNote(id) + (st.flag("KATIL", "compass") ? " Pusulan en yakın oyuncuyu gösterir (isim yok)." : ""));
				Msg.sound(k, net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 0.8f);
			}
			for (UUID id : runnerTeam.members) {
				ServerPlayer r = player(id);
				if (r == null) continue;
				Msg.title(r, "MASUMSUN", "Ejderha'yı öldür, katili bul!", 5, 80, 20);
				Msg.tell(r, "Sen MASUMSUN. Aranızda gizli " + hunterTeam.members.size() + " katil var (herkes aynı skin'de, isim etiketi yok). Bir kez ölürsen elenirsin. Hedef: katil hepsini öldürmeden Ender Dragon'u öldür. Kimin katil olduğunu davranışlarından çıkar.");
			}
			katilBegin();
			return;
		}
		for (UUID id : runnerTeam.members) { var rp = player(id); if (rp != null) Msg.sound(rp, net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 1.2f); }
		Msg.broadcast(server, "Manhunt başladı! Runner'lar " + hs + " sn önde. Hedef: Ender Dragon'u öldür!");
	}

	@Override
	protected List<String> summaryExtra() {
		if (katil) return List.of("Katil: " + String.join(", ", hunterTeam.members.stream().map(id -> names.getOrDefault(id, "?") + " (" + kills.getOrDefault(id, 0) + " öldürme)").toList()));
		return List.of("Runner'lar: " + String.join(", ", runnerIds.stream().map(id -> { var p = player(id); return p != null ? p.getGameProfile().getName() : "?"; }).toList()));
	}

	@Override
	protected void onRunningTick() {
		if (katil) katilTick();
		if (ticks % 10 == 0) trackRunners();
		if (ticks % 20 == 0) offlineCheck();
		if (ticks % 20 == 0) hud();
	}

	private ServerPlayer farthestRunner(ServerPlayer h) {
		ServerPlayer best = null;
		double bd = -1;
		for (UUID id : runnerIds) {
			if (eliminated.contains(id)) continue;
			ServerPlayer r = player(id);
			if (r == null || r.level() != h.level()) continue;
			double d = r.distanceToSqr(h);
			if (d > bd) { bd = d; best = r; }
		}
		return best;
	}

	private ServerPlayer nearestRunner(ServerPlayer h) {
		ServerPlayer best = null;
		double bd = Double.MAX_VALUE;
		for (UUID id : runnerIds) {
			if (eliminated.contains(id)) continue;
			ServerPlayer r = player(id);
			if (r == null || r.level() != h.level()) continue;
			double d = r.distanceToSqr(h);
			if (d < bd) { bd = d; best = r; }
		}
		return best;
	}

	/** p'ye gore ayni boyuttaki hayatta diger oyuncular (isim yok, sadece mesafe icin). */
	private List<ServerPlayer> othersNear(ServerPlayer p) {
		List<ServerPlayer> r = new ArrayList<>();
		for (UUID id : players) {
			if (id.equals(p.getUUID()) || eliminated.contains(id)) continue;
			ServerPlayer o = player(id);
			if (o != null && !o.isSpectator() && o.level() == p.level()) r.add(o);
		}
		r.sort(java.util.Comparator.comparingDouble(o -> o.distanceToSqr(p)));
		return r;
	}

	// ---------------- yan panel: runner / hunter durumu ----------------
	private net.minecraft.world.scores.Objective board;
	private List<String> boardLines;

	private void setupBoard() {
		var sb = server.getScoreboard();
		var old = sb.getObjective("kp_manhunt");
		if (old != null) sb.removeObjective(old);
		board = sb.addObjective("kp_manhunt", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
			Component.literal(katil ? "KATİL KİM" : "MANHUNT").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
			net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false,
			net.minecraft.network.chat.numbers.BlankFormat.INSTANCE);
		sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR, board);
		updateBoard();
	}

	private static String dimLabel(ServerPlayer p) {
		var d = p.level().dimension();
		if (d == net.minecraft.world.level.Level.NETHER) return "Nether";
		if (d == net.minecraft.world.level.Level.END) return "End";
		return "Dünya";
	}

	private String row(UUID id, boolean runner) {
		String nm = names.getOrDefault(id, "?");
		var p = player(id);
		if (eliminated.contains(id)) return "§8✖ " + nm;
		if (p == null) return "§7? " + nm + " (çevrimdışı)";
		if (runner) return "§a" + nm + " §c❤" + (int) Math.ceil(p.getHealth() / 2) + " §7" + dimLabel(p);
		return "§c" + nm + " §7" + dimLabel(p);
	}

	private void updateBoard() {
		if (board == null) return;
		var sb = server.getScoreboard();
		List<String> lines = new ArrayList<>();
		lines.add("§7Süre §f" + Msg.mmss(ticks / 20));
		if (katil) {
			lines.add("§a§lHAYATTA §7(" + runnerIds.stream().filter(this::isAlive).count() + "/" + runnerIds.size() + " masum)");
			for (UUID id : players) {
				if (lines.size() >= 15) break;
				lines.add(eliminated.contains(id) ? "§8✖ " + names.getOrDefault(id, "?") : "§f" + names.getOrDefault(id, "?"));
			}
			applyBoard(sb, lines);
			return;
		}
		lines.add("§a§lRUNNER §7(" + runnerIds.stream().filter(this::isAlive).count() + "/" + runnerIds.size() + ")");
		for (UUID id : runnerIds) lines.add(row(id, true));
		long ha = hunterTeam.members.stream().filter(this::isAlive).count();
		lines.add("§c§lHUNTER §7(" + ha + "/" + hunterTeam.members.size() + ")");
		for (UUID id : hunterTeam.members) { if (lines.size() >= 15) break; lines.add(row(id, false)); }
		applyBoard(sb, lines);
	}

	private void applyBoard(net.minecraft.server.ServerScoreboard sb, List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			String l = lines.get(i);
			for (int k = 0; k < i; k++) if (lines.get(k).equals(l)) { l += "§r"; k = -1; }
			lines.set(i, l);
		}
		if (lines.equals(boardLines)) return;
		if (boardLines != null) for (String l : boardLines) sb.resetSinglePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(l), board);
		for (int i = 0; i < lines.size(); i++) sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(lines.get(i)), board).set(lines.size() - i);
		boardLines = lines;
	}

	private void hud() {
		updateBoard();
		if (katil) {
			long alive = runnerIds.stream().filter(this::isAlive).count();
			boolean cmp = st.flag("KATIL", "compass");
			for (ServerPlayer p : onlinePlayers()) {
				if (p.isSpectator()) continue;
				String txt = "⏱ " + Msg.mmss(ticks / 20) + "   Hayatta masum: " + alive + "/" + runnerIds.size();
				if (cmp) {
					var near = othersNear(p);
					txt += near.isEmpty() ? "   Yakında kimse yok"
						: "   En yakın: " + (int) Math.sqrt(near.get(0).distanceToSqr(p)) + " blok · en uzak: " + (int) Math.sqrt(near.get(near.size() - 1).distanceToSqr(p)) + " blok";
				}
				Msg.actionbar(p, Component.literal(txt).withStyle(ChatFormatting.WHITE));
			}
			return;
		}
		for (UUID id : hunterTeam.members) {
			ServerPlayer h = player(id);
			if (h == null || h.isSpectator()) continue;
			ServerPlayer r = nearestRunner(h);
			Trace tr = traceFor(h);
			String txt = r != null ? "En yakın runner: " + (int) Math.sqrt(r.distanceToSqr(h)) + " blok"
				: tr != null ? "Runner başka boyutta · son iz (portal): " + (int) Math.sqrt(tr.pos().distSqr(h.blockPosition())) + " blok"
				: "Runner başka boyutta, iz yok";
			ServerPlayer far = farthestRunner(h);
			if (r != null && far != null && far != r) txt += " · en uzak: " + (int) Math.sqrt(far.distanceToSqr(h)) + " blok";
			Msg.actionbar(h, Component.literal("⏱ " + Msg.mmss(ticks / 20) + "   " + txt).withStyle(ChatFormatting.RED));
		}
		for (UUID id : runnerTeam.members) {
			ServerPlayer r = player(id);
			if (r != null && !r.isSpectator()) {
				Msg.actionbar(r, Component.literal("⏱ " + Msg.mmss(ticks / 20) + "   Hayatta runner: " + runnerIds.stream().filter(this::isAlive).count())
					.withStyle(ChatFormatting.GREEN));
			}
		}
	}

	@Override
	public boolean allowDeath(ServerPlayer p, DamageSource src) {
		if (katil) return katilDeath(p, src);
		if (!contains(p.getUUID()) || phase != Phase.RUNNING) return true;
		boolean runner = isRunner(p.getUUID());
		boolean spectate = runner || (!katil && !st.flag("MANHUNT", "hunterRespawn"));
		if (!spectate) return true;                           // hunter normal oluyor, yeniden dogar
		if (eliminated.contains(p.getUUID())) return false;
		eliminated.add(p.getUUID());
		p.getInventory().dropAll();
		p.setHealth(p.getMaxHealth());
		p.setGameMode(GameType.SPECTATOR);
		if (katil) {
			Msg.broadcast(server, Component.literal("☠ " + p.getGameProfile().getName() + " öldü ve elendi. Kalan masum: "
				+ runnerIds.stream().filter(this::isAlive).count()).withStyle(ChatFormatting.RED));
			for (ServerPlayer o : onlinePlayers()) Msg.sound(o, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
		} else {
			Msg.broadcast(server, src.getLocalizedDeathMessage(p).copy().withStyle(ChatFormatting.RED));
		}
		if (runnerIds.stream().noneMatch(this::isAlive)) finish(hunterTeam, katil ? "Tüm masumlar öldü: katil kazandı" : "Tüm runner'lar öldü");
		else if (teamOf(p.getUUID()) == hunterTeam && hunterTeam.members.stream().noneMatch(this::isAlive)) finish(runnerTeam, "Hunter kalmadı");
		return false;
	}

	@Override
	public void onEntityDeath(LivingEntity e, DamageSource src) {
		if (phase == Phase.RUNNING && e instanceof EnderDragon) finish(runnerTeam, katil ? "Masumlar Ender Dragon'u öldürdü!" : "Ender Dragon öldürüldü!");
	}

	@Override
	public boolean allowDamage(LivingEntity e, DamageSource src, float amount) {
		if (e instanceof ServerPlayer t && contains(t.getUUID())) {
			if (phase == Phase.COUNTDOWN) return false;
			if (katil && phase == Phase.RUNNING) {
				if (ticks < graceTicks && src.getEntity() instanceof ServerPlayer a && a != t && contains(a.getUUID())) return false;
			}
			if (!katil && src.getEntity() instanceof ServerPlayer a && a != t && contains(a.getUUID()) && !st.flag("MANHUNT", "pvp")) return false;
		}
		return true;
	}

	@Override
	public void onRespawn(ServerPlayer p) {
		super.onRespawn(p);
	}

	@Override
	protected void onClose() {
		if (katil) {
			server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SHOWDEATHMESSAGES).set(true, server);
			for (ServerPlayer p : onlinePlayers()) sendDisguise(p, false);
		}
		if (board != null) { server.getScoreboard().removeObjective(board); board = null; }
		var wb = server.overworld().getWorldBorder();
		wb.setCenter(0, 0);
	}

	// ================= Katil Kim ve ortak izleme =================
	private int graceTicks;
	private int killersGone, innocentsGone;                      // cevrimdisi saniye sayaclari
	private final Map<UUID, Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, BlockPos>> lastSeen = new HashMap<>();

	private record Trace(BlockPos pos) {}

	/** Herkes 200x200'luk alanda (spawn merkezli) rastgele, birbirinden uzak dogar. */
	private void placeKatil(ServerLevel ow, BlockPos sp) {
		List<int[]> taken = new ArrayList<>();
		int half = 100;
		double sep = GameWorld.minSepFor(half, players.size());
		for (GameTeam t : teams) {
			for (UUID id : t.members) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				BlockPos pos = null;
				for (int tries = 0; tries < 80 && pos == null; tries++) {
					int x = sp.getX() + rnd.nextInt(2 * half + 1) - half, z = sp.getZ() + rnd.nextInt(2 * half + 1) - half;
					double s2 = sep * (tries < 40 ? 1.0 : tries < 60 ? 0.6 : 0.2);
					boolean far = true;
					for (int[] q : taken) { double dx = q[0] - x, dz = q[1] - z; if (dx * dx + dz * dz < s2 * s2) { far = false; break; } }
					if (!far) continue;
					pos = GameWorld.safeSurface(ow, x, z);
					if (pos != null) taken.add(new int[] {x, z});
				}
				if (pos == null) pos = new BlockPos(sp.getX(), ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ()), sp.getZ());
				placeOne(p, pos, t);
			}
		}
	}

	private String killerNote(UUID self) {
		List<String> others = hunterTeam.members.stream().filter(id -> !id.equals(self)).map(id -> names.getOrDefault(id, "?")).toList();
		return others.isEmpty() ? "" : " Diğer katil: " + String.join(", ", others) + ".";
	}

	private void katilBegin() {
		graceTicks = (int) st.num("KATIL", "graceSec") * 20;
		List<String> missing = new ArrayList<>();
		for (ServerPlayer p : onlinePlayers()) {
			if (!isBot(p) && !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.DisguisePayload.TYPE)) missing.add(p.getGameProfile().getName());
		}
		Msg.broadcast(server, "Katil Kim başladı! Herkes 200x200 alanda rastgele doğdu." + (graceTicks > 0 ? " İlk " + graceTicks / 20 + " sn kimse kimseye vuramaz." : ""));
		if (!missing.isEmpty()) {
			Msg.broadcast(server, "⚠ kapisma-hud modu yok: " + String.join(", ", missing) + ". Bu oyuncuların skin'i gizlenmez, kim olduğu belli olur; modu kurmalarını söyle.");
		}
	}

	private void katilTick() {
		if (graceTicks > 0 && ticks == graceTicks) Msg.broadcast(server, "Başlangıç koruması bitti: artık PvP açık.");
	}

	private void eliminateNow(ServerPlayer p) {
		eliminated.add(p.getUUID());
		p.getInventory().dropAll();
		p.setHealth(p.getMaxHealth());
		p.setGameMode(GameType.SPECTATOR);
		Msg.title(p, "Elendin", "Seyirci olarak izleyebilirsin", 5, 50, 15);
	}

	/** Masum: bir kez olunce elenir (olum nedeni yazilmaz, katil belli olmaz). Katil: yeniden dogar. */
	private boolean katilDeath(ServerPlayer p, DamageSource src) {
		UUID id = p.getUUID();
		if (!contains(id) || phase != Phase.RUNNING) return true;
		if (eliminated.contains(id)) return false;
		if (!isRunner(id)) return true;                                                 // katil yeniden dogar
		boolean byPlayer = src.getEntity() instanceof ServerPlayer a && a != p && contains(a.getUUID());
		if (!byPlayer && !st.flag("KATIL", "natureEliminates")) return true;            // kural: dogal olum eletmez
		eliminateNow(p);
		Msg.broadcast(server, Component.literal("☠ " + p.getGameProfile().getName() + " öldü. Kalan masum: "
			+ runnerIds.stream().filter(this::isAlive).count()).withStyle(ChatFormatting.RED));
		for (ServerPlayer o : onlinePlayers()) Msg.sound(o, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
		if (runnerIds.stream().noneMatch(this::isAlive)) finish(hunterTeam, "Tüm masumlar öldü: katil kazandı");
		return false;
	}

	/** Runner'larin her boyuttaki son bilinen konumu (portal izi). */
	private void trackRunners() {
		for (UUID id : runnerIds) {
			ServerPlayer r = player(id);
			if (r == null || r.isSpectator() || eliminated.contains(id)) continue;
			lastSeen.computeIfAbsent(id, k -> new HashMap<>()).put(r.level().dimension(), r.blockPosition());
		}
	}

	/** Hunter icin en yakin iz: runner ayni boyutta ise canli konum, degilse o boyuttaki son konumu (portal). */
	private Trace traceFor(ServerPlayer h) {
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (UUID id : runnerIds) {
			if (eliminated.contains(id)) continue;
			ServerPlayer r = player(id);
			BlockPos p;
			if (r != null && !r.isSpectator() && r.level() == h.level()) p = r.blockPosition();
			else { var m = lastSeen.get(id); p = m == null ? null : m.get(h.level().dimension()); }
			if (p == null) continue;
			double d = p.distSqr(h.blockPosition());
			if (d < bd) { bd = d; best = p; }
		}
		return best == null ? null : new Trace(best);
	}

	/** Bir taraf 3 dakika boyunca tamamen cevrimdisi kalirsa oyun digerinin lehine biter. */
	private void offlineCheck() {
		if (phase != Phase.RUNNING) return;
		boolean killerOn = hunterTeam.members.stream().anyMatch(id -> isAlive(id) && player(id) != null);
		boolean innocentOn = runnerIds.stream().anyMatch(id -> isAlive(id) && player(id) != null);
		killersGone = killerOn ? 0 : killersGone + 1;
		innocentsGone = innocentOn ? 0 : innocentsGone + 1;
		if (killersGone >= 180 && hunterTeam.members.stream().anyMatch(this::isAlive)) finish(runnerTeam, katil ? "Katil oyundan ayrıldı" : "Hunter'lar oyundan ayrıldı");
		else if (innocentsGone >= 180 && runnerIds.stream().anyMatch(this::isAlive)) finish(hunterTeam, katil ? "Masumlar oyundan ayrıldı" : "Runner oyundan ayrıldı");
	}
}
