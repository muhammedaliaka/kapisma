package tr.kapisma.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import tr.kapisma.Msg;
import tr.kapisma.team.TeamService;
import tr.kapisma.team.TeamService.GameTeam;

/** Süreli ve kişiye özel eşya görevleri. Her oyuncu kendi canı, puanı ve atlama hakkıyla yarışır. */
public final class ItemRaceGame extends WorldGame {
	private static final int START_LIVES = 5, POINTS_PER_SKIP = 5;
	private static final Map<Block, Item> COLLECTABLE = Map.ofEntries(
		Map.entry(Blocks.DIRT, Items.DIRT), Map.entry(Blocks.COARSE_DIRT, Items.COARSE_DIRT),
		Map.entry(Blocks.GRAVEL, Items.GRAVEL), Map.entry(Blocks.CLAY, Items.CLAY_BALL),
		Map.entry(Blocks.SAND, Items.SAND), Map.entry(Blocks.RED_SAND, Items.RED_SAND),
		Map.entry(Blocks.CACTUS, Items.CACTUS), Map.entry(Blocks.SUGAR_CANE, Items.SUGAR_CANE),
		Map.entry(Blocks.BAMBOO, Items.BAMBOO), Map.entry(Blocks.KELP, Items.KELP),
		Map.entry(Blocks.KELP_PLANT, Items.KELP), Map.entry(Blocks.PUMPKIN, Items.PUMPKIN));
	private final Map<UUID, Progress> progress = new HashMap<>();
	private int round, deadline, roundTicks, roundLimit, scoreGoal;
	private SideBoard board;

	private static final class Progress {
		int lives = START_LIVES, score, skips, baseline;
		Item item;
		boolean resolved, baselineKnown;
	}

	public ItemRaceGame(GameManager mgr) { super(mgr); }

	@Override public String id() { return "ESYA"; }
	@Override public String title() { return "Eşya Yarışı"; }
	@Override protected double borderRadius() { return st.num(id(), "borderStart"); }
	@Override protected boolean eliminationMode() { return false; }
	@Override protected boolean sharedSpawn() { return true; }
	@Override protected boolean minimapEnabled() { return st.flag(id(), "minimap"); }
	@Override protected GameWorld.Options worldOptions() { return new GameWorld.Options(true, true, true, st.flag(id(), "keepInventory")); }

	@Override
	protected void buildTeams(List<ServerPlayer> participants) {
		teams = TeamService.assign(server, participants, false, false, rnd, mgr.teamPrefs);
		players.forEach(id -> progress.put(id, new Progress()));
	}

	@Override
	protected void onStart() {
		roundTicks = (int) st.num(id(), "roundSec") * 20;
		roundLimit = (int) st.num(id(), "roundLimit");
		scoreGoal = (int) st.num(id(), "scoreGoal");
		if (minimapEnabled()) giveWorldMap();
		board = new SideBoard(server, "kp_item_race", "EŞYA YARIŞI");
		startRound();
	}

	private record RacePool(List<Item> targets, Map<Item, ItemCost.Plan> plans, Map<Item, Integer> raw) {}

	private void startRound() {
		RacePool verified = verifiedItems();
		List<UUID> alive = players.stream().filter(this::isAlive).toList();
		Map<UUID, Item> assigned = new HashMap<>();
		// All simultaneous targets must fit one physical resource budget.
		for (int attempt = 0; attempt < 4 && assigned.size() < alive.size(); attempt++) {
			assigned.clear();
			var ledger = new ResourceProof.Ledger<Item>(verified.raw());
			List<Item> pool = new ArrayList<>(verified.targets());
			Collections.shuffle(pool, rnd);
			for (UUID id : alive) {
				Progress state = progress.get(id);
				ServerPlayer player = player(id);
				// Stable sort preserves randomized choice within the same priority.
				pool.sort(Comparator.comparingInt(item -> item != state.item && itemCount(player, item) == 0 ? 0 : 1));
				Item next = null;
				for (Item candidate : pool) {
					if (ledger.reserve(List.of(new ResourceProof.Input<>(verified.plans().get(candidate).proof(), 1)))) {
						next = candidate;
						break;
					}
				}
				if (next == null) break;
				assigned.put(id, next);
				pool.remove(next);
			}
		}
		if (assigned.size() < alive.size()) {
			finish(round == 0 ? null : leader(), "Sınır içinde yeterli sayıda farklı eşya doğrulanamadı");
			return;
		}
		round++;
		deadline = ticks + roundTicks;
		Msg.broadcast(server, "Eşya Yarışı · Tur " + round + " başladı. Kendi görevini görmek için /gorev yaz.");
		for (UUID id : alive) {
			Progress state = progress.get(id);
			ServerPlayer player = player(id);
			state.item = assigned.get(id);
			state.baselineKnown = player != null && arenaLevel(player.level());
			state.baseline = state.baselineKnown ? itemCount(player, state.item) : 0;
			state.resolved = false;
			if (player != null) showTask(player);
		}
	}

	private static int itemCount(ServerPlayer p, Item item) {
		if (p == null) return 0;
		int count = 0;
		var inventory = p.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			var stack = inventory.getItem(i);
			if (stack.is(item)) count += stack.getCount();
		}
		return count;
	}

	public boolean showTask(ServerPlayer p) {
		Progress state = progress.get(p.getUUID());
		if (isClosed() || phase != Phase.RUNNING || state == null || !isAlive(p.getUUID()) || state.item == null) return false;
		p.sendSystemMessage(Msg.prefix().append(Component.literal(state.resolved ? "Bu turun hedefi: " : "Gizli görevin: "))
			.append(state.item.getDescription().copy().withStyle(ChatFormatting.GOLD))
			.append(Component.literal("  •  Can " + state.lives + "/5  •  Puan " + state.score + "  •  Atlama " + state.skips)));
		return true;
	}

	public boolean skip(ServerPlayer p) {
		Progress state = progress.get(p.getUUID());
		if (isClosed() || phase != Phase.RUNNING || state == null || !isAlive(p.getUUID()) || p.isSpectator()
				|| !arenaLevel(p.level()) || !state.baselineKnown || state.resolved || state.skips == 0) return false;
		state.skips--;
		state.resolved = true;
		Msg.tell(p, "Bu turu geçtin. Canın ve puanın korundu.");
		return true;
	}

	@Override
	protected void onRunningTick() {
		for (ServerPlayer p : onlinePlayers()) {
			UUID id = p.getUUID();
			Progress state = progress.get(id);
			if (!isAlive(id) || p.isSpectator() || !arenaLevel(p.level()) || !state.baselineKnown
				|| state.resolved || itemCount(p, state.item) <= state.baseline) continue;
			state.score++;
			state.resolved = true;
			Msg.tell(p, "Görev tamamlandı. +1 puan.");
			if (state.score % POINTS_PER_SKIP == 0) {
				state.skips++;
				Msg.tell(p, "Bir atlama hakkı kazandın. Kullanmak için /atla yaz.");
			}
			if (scoreGoal > 0 && state.score >= scoreGoal) {
				finish(teamOf(id), "Hedef puana ulaştı");
				return;
			}
		}
		boolean allResolved = players.stream().filter(this::isAlive).allMatch(id -> progress.get(id).resolved);
		if (ticks >= deadline || allResolved) finishRound();
		if (phase == Phase.RUNNING && ticks % 20 == 0) updateScoreboard();
	}

	private void finishRound() {
		for (UUID id : players) {
			Progress state = progress.get(id);
			if (!isAlive(id) || state.resolved) continue;
			state.lives--;
			state.score = 0;
			ServerPlayer p = player(id);
			if (p != null) Msg.tell(p, "Süre doldu. Kalan can " + state.lives + "/5. Puanın sıfırlandı.");
			if (state.lives == 0) {
				eliminated.add(id);
				if (p != null) enterSpectator(p);
				Msg.broadcast(server, names.get(id) + " elendi.");
			}
		}
		List<GameTeam> alive = aliveTeams();
		if (alive.isEmpty()) finish(null, "Canı kalan oyuncu yok");
		else if (players.size() > 1 && alive.size() == 1) finish(alive.getFirst(), "Son canlı oyuncu");
		else if (roundLimit > 0 && round >= roundLimit) finish(leader(), "Tur limiti doldu");
		else startRound();
	}

	private GameTeam leader() {
		var ranked = players.stream().filter(this::isAlive).sorted(Comparator.<UUID>comparingInt(id -> progress.get(id).score)
			.thenComparingInt(id -> progress.get(id).lives).reversed()).toList();
		if (ranked.isEmpty()) return null;
		Progress first = progress.get(ranked.getFirst());
		if (ranked.size() > 1) {
			Progress second = progress.get(ranked.get(1));
			if (first.score == second.score && first.lives == second.lives) return null;
		}
		return teamOf(ranked.getFirst());
	}

	private void updateScoreboard() {
		List<String> lines = new ArrayList<>();
		lines.add("§7Tur §f" + round + "  §7Süre §f" + Msg.mmss(Math.max(0, (deadline - ticks + 19) / 20)));
		for (UUID id : players) {
			Progress state = progress.get(id);
			lines.add((isAlive(id) ? "§f" : "§8") + names.get(id) + "  §e" + state.score + "  §c♥ " + state.lives);
			ServerPlayer p = player(id);
			if (p == null || p.isSpectator()) continue;
			Component task = state.resolved ? Component.literal("Tur tamamlandı") : state.item.getDescription();
			Msg.actionbar(p, task.copy().withStyle(ChatFormatting.GOLD).append(Component.literal("   Puan " + state.score
				+ "   Atlama " + state.skips).withStyle(ChatFormatting.WHITE)));
		}
		board.update(lines);
	}

	private void establishBaseline(ServerPlayer p) {
		Progress state = progress.get(p.getUUID());
		// Çevrimdışı başlayan tur veya envanter kaybedilen gerçek ölüm: yeni başlangıç miktarı.
		if (!isClosed() && phase == Phase.RUNNING && state != null && state.item != null && !state.baselineKnown
				&& isAlive(p.getUUID()) && arenaLevel(p.level()) && !p.isSpectator() && p.isAlive()) {
			state.baseline = itemCount(p, state.item);
			state.baselineKnown = true;
		}
	}

	@Override
	public boolean allowDeath(ServerPlayer p, DamageSource source) {
		boolean allowed = super.allowDeath(p, source);
		Progress state = progress.get(p.getUUID());
		if (allowed && !isClosed() && phase == Phase.RUNNING && state != null && !state.resolved
				&& arenaLevel(p.level()) && !p.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
			// Ölüm can/puan düşürmez; eski envanter miktarı artık geçerli değildir.
			state.baselineKnown = false;
		}
		return allowed;
	}

	@Override
	public void onRespawn(ServerPlayer p) {
		super.onRespawn(p);
		establishBaseline(p);
		showTask(p);
	}

	@Override
	public void onJoin(ServerPlayer p) {
		super.onJoin(p);
		establishBaseline(p);
		showTask(p);
	}

	/** Konsol regresyonu için salt okunur; oyuncuların gizli hedeflerini sohbete göndermez. */
	public String debugProgress(String name) {
		for (UUID id : players) {
			if (!name.equalsIgnoreCase(names.get(id))) continue;
			Progress state = progress.get(id);
			ServerPlayer p = player(id);
			return "race " + names.get(id) + " round=" + round + " target="
				+ (state.item == null ? "none" : BuiltInRegistries.ITEM.getKey(state.item))
				+ " known=" + state.baselineKnown + " baseline=" + state.baseline
				+ " count=" + (p == null || state.item == null ? -1 : itemCount(p, state.item))
				+ " score=" + state.score + " lives=" + state.lives + " skips=" + state.skips + " resolved=" + state.resolved;
		}
		return "race player not found: " + name;
	}

	@Override
	public boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer target) || !contains(target.getUUID())) return true;
		return phase == Phase.RUNNING && (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == target || st.flag(id(), "pvp"));
	}

	@Override
	protected void onClose() {
		if (board != null) board.close();
	}

	/** Actual recipes, vanilla loot witnesses and a bounded loaded-surface sample. */
	private RacePool verifiedItems() {
		long start = System.nanoTime();
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		addNearbyChunks(chunks, world.centerX >> 4, world.centerZ >> 4);
		for (ServerPlayer p : onlinePlayers()) if (!p.isSpectator() && arenaLevel(p.level()))
			addNearbyChunks(chunks, p.chunkPosition().x, p.chunkPosition().z);
		var survey = BingoSurvey.race(world, chunks, 180);
		var plans = ItemCost.compute(server, survey);
		List<Item> targets = plans.keySet().stream().filter(ItemRaceGame::raceTarget)
			.sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item))).toList();
		tr.kapisma.Kapisma.LOG.info("Eşya Yarışı kaynak kanıtı: {} parça, {} hedef, {} ms",
			survey.scanned, targets.size(), (System.nanoTime() - start) / 1_000_000);
		return new RacePool(targets, plans, ItemCost.rawCounts(survey));
	}

	private static boolean raceTarget(Item item) {
		if (COLLECTABLE.containsValue(item)) return true;
		if (List.of(Items.CRAFTING_TABLE, Items.CHEST, Items.BARREL, Items.LADDER, Items.BOWL,
				Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_HOE,
				Items.FURNACE, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SWORD, Items.STONE_SHOVEL,
				Items.STONE_HOE, Items.TORCH, Items.CHARCOAL, Items.COBBLESTONE_STAIRS,
				Items.IRON_PICKAXE, Items.IRON_SWORD, Items.IRON_AXE, Items.SHEARS, Items.BUCKET,
				Items.SHIELD, Items.GLASS, Items.GLASS_BOTTLE, Items.GLASS_PANE).contains(item)) return true;
		String id = BuiltInRegistries.ITEM.getKey(item).getPath();
		for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"))
			for (String suffix : List.of("_planks", "_stairs", "_slab", "_fence", "_fence_gate", "_door",
					"_trapdoor", "_boat", "_sign", "_button", "_pressure_plate"))
				if (id.equals(wood + suffix)) return true;
		return false;
	}

	private void addNearbyChunks(Set<ChunkPos> chunks, int cx, int cz) {
		var border = world.level.getWorldBorder();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			ChunkPos pos = new ChunkPos(cx + dx, cz + dz);
			if (border.isWithinBounds(pos.getMinBlockX(), pos.getMinBlockZ())
				&& border.isWithinBounds(pos.getMaxBlockX() + 0.99, pos.getMaxBlockZ() + 0.99)) chunks.add(pos);
		}
	}


}
