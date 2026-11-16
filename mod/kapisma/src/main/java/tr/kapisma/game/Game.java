package tr.kapisma.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import tr.kapisma.config.Settings;
import tr.kapisma.team.TeamService.GameTeam;

/** Calisan bir oyunun (UHC / Tombala / Manhunt) ortak tabani. */
public abstract class Game {
	public enum Phase { PREP, SELECTING, COUNTDOWN, RUNNING, ENDED }

	protected final GameManager mgr;
	protected final MinecraftServer server;
	protected final Settings st;
	protected final Random rnd = new Random();

	public final List<UUID> players = new ArrayList<>();
	public final Set<UUID> eliminated = new HashSet<>();
	public List<GameTeam> teams = List.of();
	public Phase phase = Phase.PREP;
	/** Oyuncu adlari (cikmis olsalar da istatistikte gorunsun). */
	public final java.util.Map<UUID, String> names = new java.util.HashMap<>();
	/** katil -> (kurban -> kez) */
	public final java.util.Map<UUID, java.util.Map<UUID, Integer>> killMatrix = new java.util.HashMap<>();
	/** kurban -> (oyuncu olmayan nedenler -> kez): yaratik adi, dusme, lav... */
	public final java.util.Map<UUID, java.util.Map<String, Integer>> envDeaths = new java.util.HashMap<>();
	public final java.util.Map<UUID, Integer> kills = new java.util.HashMap<>();
	public final java.util.Map<UUID, Integer> deaths = new java.util.HashMap<>();
	/** Gecerli fazin basindan beri gecen tick. */
	protected int ticks;

	protected Game(GameManager mgr) {
		this.mgr = mgr;
		this.server = mgr.server;
		this.st = mgr.settings;
	}

	/** "UHC" | "BINGO" | "MANHUNT" */
	public abstract String id();

	public abstract String title();

	/** Oyunu hazirlar (dunya, takimlar, irklar...). Hata varsa mesaj doner, yoksa null. */
	public abstract String prepare(List<ServerPlayer> participants);

	public abstract void tick();

	/** Oyunu erkenden bitirir, herkesi lobiye gonderir. */
	public abstract void stop(String reason);

	/** Lobiye donus tamamlandiginda true. */
	public abstract boolean isClosed();

	// ---- olaylar (varsayilan: karisma) ----
	public boolean allowDeath(ServerPlayer p, DamageSource src) { return true; }

	public boolean allowDamage(LivingEntity e, DamageSource src, float amount) { return true; }

	public void onJoin(ServerPlayer p) {}

	public void onDisconnect(ServerPlayer p) {}

	public void onUseItem(ServerPlayer p, ItemStack stack) {}

	public void onEntityLoad(Entity e, ServerLevel level) {}

	public void onRespawn(ServerPlayer p) {}

	public void onEntityDeath(LivingEntity e, DamageSource src) {}

	/** Olum istatistigi (oyun calisirken, ALLOW_DEATH'ten once cagrilir). */
	public void recordDeath(ServerPlayer victim, DamageSource src) {
		if (!contains(victim.getUUID()) || phase != Phase.RUNNING || eliminated.contains(victim.getUUID())) return;
		deaths.merge(victim.getUUID(), 1, Integer::sum);
		names.put(victim.getUUID(), victim.getGameProfile().getName());
		if (src.getEntity() instanceof ServerPlayer k && k != victim && contains(k.getUUID())) {
			kills.merge(k.getUUID(), 1, Integer::sum);
			names.put(k.getUUID(), k.getGameProfile().getName());
			killMatrix.computeIfAbsent(k.getUUID(), x -> new java.util.HashMap<>()).merge(victim.getUUID(), 1, Integer::sum);
		} else {
			envDeaths.computeIfAbsent(victim.getUUID(), x -> new java.util.HashMap<>()).merge(causeLabel(src, victim), 1, Integer::sum);
		}
	}

	private static String causeLabel(DamageSource src, ServerPlayer victim) {
		if (src.getEntity() instanceof ServerPlayer k && k == victim) return "kendi kendine";
		if (src.getEntity() != null) return src.getEntity().getType().getDescription().getString();
		return switch (src.getMsgId()) {
			case "fall" -> "düşme";
			case "drown" -> "boğulma";
			case "lava" -> "lav";
			case "inFire", "onFire", "hotFloor" -> "yanma";
			case "outOfWorld" -> "dünya dışı";
			case "starve" -> "açlık";
			case "explosion", "explosion.player" -> "patlama";
			case "cactus", "sweetBerryBush" -> "diken";
			case "freeze" -> "donma";
			default -> src.getMsgId();
		};
	}

	/** Bu seviyede Nether/End portali kapali mi (gecici UHC/Tombala dunyalari)? */
	public boolean blocksPortals(net.minecraft.world.level.Level level) { return false; }

	/** Oyuncu hile korumasi (gorus hatti, vurus denetimi) bu dunyada simdi acik mi. */
	public boolean cheatGuard(net.minecraft.world.level.Level level) { return false; }

	/** Hile korumasi modu bu seviyede: 0 kapali, 1 cevher gizle + sahte cevher, 2 + yeralti bosluklari duz tas. */
	public int xrayMode(net.minecraft.world.level.Level level) { return 0; }

	/** Oyun icinde normal yazi herkese mi gitsin (varsayilan: sadece takima)? */
	public boolean globalChat() { return false; }

	/** Alt sinif irk bilgisi verir (yoksa null). */
	public String originOf(UUID id) { return null; }

	// ---- yardimcilar ----
	public boolean contains(UUID id) {
		return players.contains(id);
	}

	public GameTeam teamOf(UUID id) {
		for (GameTeam t : teams) if (t.members.contains(id)) return t;
		return null;
	}

	public boolean isAlive(UUID id) {
		return players.contains(id) && !eliminated.contains(id);
	}

	protected ServerPlayer player(UUID id) {
		return server.getPlayerList().getPlayer(id);
	}

	protected List<ServerPlayer> onlinePlayers() {
		List<ServerPlayer> r = new ArrayList<>();
		for (UUID id : players) {
			ServerPlayer p = player(id);
			if (p != null) r.add(p);
		}
		return r;
	}

	/** Hayatta uye bulunan takimlar. */
	public List<GameTeam> aliveTeams() {
		List<GameTeam> r = new ArrayList<>();
		for (GameTeam t : teams) if (t.members.stream().anyMatch(this::isAlive)) r.add(t);
		return r;
	}
}
