package tr.kapisma.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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

	private void sendMinimap() {
		if (!minimapEnabled() || world == null) return;
		var wb = world.level.getWorldBorder();
		if (mapStartRadius <= 0) mapStartRadius = Math.max(borderRadius(), world.borderRadius());
		for (ServerPlayer p : onlinePlayers()) {
			if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.MapPayload.TYPE)) continue;
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
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, m);
		}
	}

	private void clearMinimap() {
		for (UUID id : players) {
			ServerPlayer p = player(id);
			if (p != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, tr.kapisma.net.MapPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, tr.kapisma.net.MapPayload.off());
			}
		}
	}

	/** Oyun basinda oyunculara bolgenin (2048 blokluk) bolge haritasi: bos harita degil, biyom onizlemeli. */
	protected void giveWorldMap() {
		try {
			var wb = world.level.getWorldBorder();
			ItemStack map = net.minecraft.world.item.MapItem.create(world.level, (int) wb.getCenterX(), (int) wb.getCenterZ(), (byte) 4, true, false);
			net.minecraft.world.item.MapItem.renderBiomePreviewMap(world.level, map);
			map.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Bölge Haritası").withStyle(x -> x.withItalic(false).withColor(ChatFormatting.GREEN)));
			for (ServerPlayer p : onlinePlayers()) p.getInventory().add(map.copy());
		} catch (Exception e) {
			Kapisma.LOG.warn("Harita verilemedi: {}", e.toString());
		}
	}

	protected void giveStartKit(ServerPlayer p) {
		int food = (int) st.num(id(), "startFood");
		if (food <= 0) return;
		// Avian et yiyemez (vegetarian gucu): esdeger doygunlukta altin havuc ver.
		if ("avian".equals(chosenOrigins.get(p.getUUID()))) {
			p.getInventory().add(new ItemStack(Items.GOLDEN_CARROT, Math.max(1, food * 3 / 4)));
		} else {
			p.getInventory().add(new ItemStack(Items.COOKED_BEEF, food));
		}
	}

	@Override
	public boolean blocksPortals(net.minecraft.world.level.Level level) {
		return world != null && level == world.level;
	}

	@Override
	public int xrayMode(net.minecraft.world.level.Level level) {
		return world != null && level == world.level ? (int) st.num(id(), "antiXray") : 0;
	}

	@Override
	public boolean cheatGuard(net.minecraft.world.level.Level level) {
		return phase == Phase.RUNNING && world != null && level == world.level && st.flag(id(), "antiCheat");
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

	/** Dunyayi acar. Hata mesaji veya null. */
	protected String openWorld() {
		seedEntry = mgr.seeds.pick(st.seedClass, rnd);
		long seed = seedEntry != null ? seedEntry.seed() : rnd.nextLong();
		int cx = seedEntry != null ? seedEntry.spawnX() : 0;
		int cz = seedEntry != null ? seedEntry.spawnZ() : 0;
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

	protected final void placeAt(List<BlockPos> pts) {
		for (GameTeam t : teams) {
			BlockPos base = pts.get(t.index);
			int k = 0;
			for (UUID id : t.members) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				BlockPos alt = k == 0 ? null : world.safeSurface(base.getX() + 2 * k, base.getZ());
				BlockPos pos = alt != null ? alt : base;
				k++;
				placeOne(p, pos, t);
			}
		}
	}

	/** true: baslangic chunk'lari arka planda yuklenir (sunucu donmaz). Manhunt kapatir. */
	protected boolean pregen() { return true; }

	private static final class Cand {
		int x, z, tries;
		BlockPos pos;
	}

	private List<Cand> cands;
	private int loadTicks;

	private Cand newCand(int teamIndex, int tries) {
		Cand c = new Cand();
		c.tries = tries;
		List<int[]> taken = new ArrayList<>();
		if (cands != null) for (int j = 0; j < cands.size(); j++) if (j != teamIndex) taken.add(new int[] {cands.get(j).x, cands.get(j).z});
		int[] xz = world.candidate(scatterRadius(), rnd, taken, GameWorld.minSepFor(scatterRadius(), teams.size()));
		c.x = xz != null ? xz[0] : world.centerX;
		c.z = xz != null ? xz[1] : world.centerZ;
		world.force(c.x, c.z, true);
		return c;
	}

	private void startLoading() {
		cands = new ArrayList<>();
		loadTicks = 0;
		for (int i = 0; i < teams.size(); i++) cands.add(newCand(i, 0));
		Msg.broadcast(server, "Dünya hazırlanıyor, birkaç saniye...");
	}

	private void loadingTick() {
		if (cands == null) return;
		loadTicks++;
		if (loadTicks > 60 * 20) {          // zaman asimi: eski yontemle anlik uret
			for (Cand c : cands) world.force(c.x, c.z, false);
			cands = null;
			Kapisma.LOG.warn("Chunk yukleme zaman asimi, anlik yerlestirme");
			placePlayers();
			phase = Phase.COUNTDOWN;
			ticks = 0;
			return;
		}
		if (loadTicks % 4 != 0) return;
		boolean done = true;
		for (int i = 0; i < cands.size(); i++) {
			Cand c = cands.get(i);
			if (c.pos != null) continue;
			if (!world.loaded(c.x, c.z)) { done = false; continue; }
			BlockPos pos = world.safeSurface(c.x, c.z);
			world.force(c.x, c.z, false);
			if (pos != null) { c.pos = pos; continue; }
			if (c.tries >= 10) {            // cok denendi: merkeze dus
				BlockPos m = world.safeSurface(world.centerX, world.centerZ);
				c.pos = m != null ? m : new BlockPos(world.centerX, world.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, world.centerX, world.centerZ), world.centerZ);
				continue;
			}
			cands.set(i, newCand(i, c.tries + 1));
			done = false;
		}
		if (!done) return;
		List<BlockPos> pts = new ArrayList<>();
		for (Cand c : cands) pts.add(c.pos);
		cands = null;
		placeAt(pts);
		phase = Phase.COUNTDOWN;
		ticks = 0;
	}

	/** Tek oyuncuyu hazirlar ve isinlar. */
	protected void placeOne(ServerPlayer p, BlockPos pos, GameTeam t) {
		spawnOf.put(p.getUUID(), pos);
		Lobby.resetPlayer(p);
		p.closeContainer();
		p.setGameMode(GameType.SURVIVAL);
		p.teleportTo(gameLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360f, 0f);
		p.setRespawnPosition(gameLevel().dimension(), pos, 0f, true, false);
		giveStartKit(p);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12 * 20, 255, false, false, false));
		p.addEffect(new MobEffectInstance(MobEffects.JUMP, 12 * 20, 128, false, false, false));
		String o = chosenOrigins.get(p.getUUID());
		Msg.tell(p, "Takımın: " + t.name + (o != null ? " | Irkın: " + OriginService.label(o) : ""));
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
						p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
						p.removeEffect(MobEffects.JUMP);
						Msg.title(p, "BAŞLA!", title(), 0, 30, 10);
						Msg.sound(p, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 1f);
					}
					phase = Phase.RUNNING;
					ticks = 0;
					setupHealth();
					onStart();
				}
			}
			case RUNNING -> {
				ticks++;
				runTicks = ticks;
				trackOffline();
				onRunningTick();
				if (ticks % 10 == 0) sendMinimap();
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
		for (UUID id : players) {
			if (eliminated.contains(id)) continue;
			if (player(id) == null) {
				int n = offlineTicks.merge(id, 1, Integer::sum);
				if (n >= 3 * 60 * 20) {
					eliminated.add(id);
					Msg.broadcast(server, "Bağlantısı kopan oyuncu elendi.");
					checkWinner();
				}
			} else {
				offlineTicks.remove(id);
			}
		}
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
		p.setGameMode(GameType.SPECTATOR);
		Msg.broadcast(server, src.getLocalizedDeathMessage(p).copy().withStyle(ChatFormatting.RED));
		Msg.soundAll(server, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1f);
		Msg.title(p, "Elendin", "Seyirci olarak izleyebilirsin", 5, 50, 15);
		checkWinner();
		return false;       // gercek olumu iptal et: oyuncu seyirci olarak kalir
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
		for (UUID id : players) {
			ServerPlayer p = player(id);
			if (p != null) {
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
		offlineTicks.remove(p.getUUID());
		if (eliminated.contains(p.getUUID())) p.setGameMode(GameType.SPECTATOR);
	}

	@Override
	public void onRespawn(ServerPlayer p) {
		if (!contains(p.getUUID()) || phase == Phase.ENDED) return;
		ServerLevel lvl = gameLevel();
		BlockPos pos = spawnOf.getOrDefault(p.getUUID(), lvl.getSharedSpawnPos());
		if (p.level() != lvl) p.teleportTo(lvl, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
	}

	@Override
	public String originOf(UUID id) { return chosenOrigins.get(id); }

	protected List<ServerPlayer> everyone() {
		return new ArrayList<>(server.getPlayerList().getPlayers());
	}
}
