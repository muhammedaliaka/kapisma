package tr.kapisma.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import tr.kapisma.Msg;
import tr.kapisma.config.Settings;
import tr.kapisma.lobby.Lobby;
import tr.kapisma.origin.OriginService;
import tr.kapisma.seed.SeedPool;
import tr.kapisma.ui.MenuService;

/** Lobi + calisan oyun yonetimi. Host: sunucuya ilk giren (veya /kapisma host ile atanan). */
public final class GameManager {
	public final MinecraftServer server;
	public final Settings settings;
	public final SeedPool seeds;
	public final MenuService menus;
	public Game current;
	/** Son biten oyun (istatistik icin). */
	public Game last;
	/** Lobide secilen takim rengi/ismi (oyuncu basina, sunucu acik kaldikca hatirlanir). */
	public final java.util.Map<UUID, tr.kapisma.team.TeamService.Pref> teamPrefs = new java.util.HashMap<>();

	public tr.kapisma.team.TeamService.Pref pref(UUID id) {
		return teamPrefs.computeIfAbsent(id, k -> new tr.kapisma.team.TeamService.Pref());
	}

	private final List<UUID> joinOrder = new ArrayList<>();
	private UUID hostOverride;
	private int restartTicks = -1;
	private final java.util.Map<UUID, Integer> pendingOriginReset = new java.util.HashMap<>();
	private final java.util.Map<UUID, Integer> pendingLobby = new java.util.HashMap<>();

	public GameManager(MinecraftServer server) {
		this.server = server;
		this.settings = Settings.load();
		this.seeds = SeedPool.load();
		this.menus = new MenuService(this);
	}

	// ---- host ----
	public ServerPlayer host() {
		if (hostOverride != null) {
			ServerPlayer h = server.getPlayerList().getPlayer(hostOverride);
			if (h != null) return h;
		}
		for (UUID id : joinOrder) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) return p;
		}
		return null;
	}

	public void setHost(ServerPlayer p) {
		hostOverride = p.getUUID();
		lastHost = p.getUUID();          // komutla devirde watchHost ikinci kez duyurmasin
	}

	public boolean canAdmin(ServerPlayer p) {
		ServerPlayer h = host();
		return (h != null && h.getUUID().equals(p.getUUID())) || p.hasPermissions(2);
	}

	// ---- olaylar ----
	public void onJoin(ServerPlayer p) {
		if (!joinOrder.contains(p.getUUID())) joinOrder.add(p.getUUID());
		if (current != null && current.contains(p.getUUID())) {
			current.onJoin(p);
			return;
		}
		pendingLobby.put(p.getUUID(), 3);            // katilma olayi bitmeden dunya degistirme: birkac tik sonra
		pendingOriginReset.put(p.getUUID(), 8);      // oyuncu listeye eklendikten sonra
		ServerPlayer h = host();
		if (h != null && h.getUUID().equals(p.getUUID())) {
			Msg.tell(p, "Sen hostsun! Menü için Nether Yıldızı'na sağ tık veya /oyun yaz.");
		} else {
			Msg.tell(p, "Hoş geldin! Oyun menüsü: Nether Yıldızı. Ayarları host belirler. Komutlar: /yardim");
		}
	}

	public void onDisconnect(ServerPlayer p) {
		tr.kapisma.lobby.Parkour.forget(p);
		if (current != null && current.contains(p.getUUID())) current.onDisconnect(p);
	}

	private UUID lastHost;

	private void watchHost() {
		ServerPlayer h = host();
		UUID cur = h != null ? h.getUUID() : null;
		if (cur != null && lastHost != null && !cur.equals(lastHost)) {
			Msg.broadcast(server, "Yeni host: " + h.getGameProfile().getName());
			Msg.tell(h, "Artık hostsun! Ayarlar için Nether Yıldızı veya /oyun.");
		}
		if (cur != null) lastHost = cur;
	}

	public void tick() {
		if (server.getTickCount() % 20 == 0) watchHost();
		if (restartTicks > 0 && --restartTicks == 0) {
			for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers()))
				p.connection.disconnect(net.minecraft.network.chat.Component.literal("Yeni dünya için sunucu yeniden başlıyor, ~40 sn sonra tekrar bağlan."));
			server.halt(false);
		}
		if (!pendingLobby.isEmpty()) {
			var it = pendingLobby.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (e.setValue(e.getValue() - 1) <= 1) {
					ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
					if (p != null && (current == null || !current.contains(p.getUUID()))) Lobby.send(server, p);
					it.remove();
				}
			}
		}
		if (!pendingOriginReset.isEmpty()) {
			var it = pendingOriginReset.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (e.setValue(e.getValue() - 1) <= 1) {
					ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
					if (p != null && !Boolean.getBoolean("kapisma.noJoinReset") && (current == null || !current.contains(p.getUUID()))) OriginService.reset(server, p);
					it.remove();
				}
			}
		}
		if (tr.kapisma.seed.SeedScan.running() && current == null) tr.kapisma.seed.SeedScan.tick(server);
		if (current != null) {
			current.tick();
			if (current != null && current.isClosed()) { last = current; current = null; }
		}
	}

	// ---- oyun kontrolu ----
	public String start() {
		if (current != null && !current.isClosed()) return "Zaten bir oyun çalışıyor.";
		List<ServerPlayer> ps = new ArrayList<>(server.getPlayerList().getPlayers());
		if (ps.isEmpty()) return "Oyuncu yok.";
		int min = "BINGO".equals(settings.game) ? 1 : "KATIL".equals(settings.game) ? 3 : 2;
		boolean dragonGame = "MANHUNT".equals(settings.game) || "KATIL".equals(settings.game);
		if (ps.size() < min) {
			return ("KATIL".equals(settings.game)
				? "Katil Kim için en az 3 oyuncu gerekir (1 katil + 2 masum); 5-6 oyuncu önerilir."
				: "MANHUNT".equals(settings.game)
				? "Manhunt için en az 2 oyuncu gerekir (1 runner + 1 hunter); 3 ve üzeri önerilir."
				: "UHC için en az 2 oyuncu gerekir (tek kişiyle kazanan hemen belli olur).")
				+ " Şu an " + ps.size() + " oyuncu var.";
		}
		if (dragonGame && ManhuntGame.worldUsed(server)) {
			requestNewWorld();
			return null;
		}
		Game g = switch (settings.game) {
			case "UHC" -> new UhcGame(this);
			case "BINGO" -> new BingoGame(this);
			case "MANHUNT" -> new ManhuntGame(this, false);
			case "KATIL" -> new ManhuntGame(this, true);
			default -> null;
		};
		if (g == null) return "Bu oyun modu henüz hazır değil: " + settings.game;
		String err;
		try {
			err = g.prepare(ps);
		} catch (Exception e) {
			tr.kapisma.Kapisma.LOG.error("Oyun hazirlanamadi", e);
			g.stop("Hata: " + e.getMessage());
			return "Oyun başlatılamadı: " + e;
		}
		if (err != null) return err;
		current = g;
		Msg.broadcast(server, g.title() + " hazırlanıyor... (" + ps.size() + " oyuncu)");
		return null;
	}

	/**
	 * Manhunt ana dunyada oynanir ve kullanilmis dunya tekrar kullanilamaz: yeni seed secilir,
	 * kapisma_next.json yazilir ve sunucu kapanir; start.bat dunyayi silip yeni seed ile acar.
	 */
	public void requestNewWorld() {
		SeedPool.Entry e = seeds.pick(settings.seedClass, new java.util.Random());
		try {
			var o = new com.google.gson.JsonObject();
			o.addProperty("seed", e != null ? Long.toString(e.seed()) : "");
			o.addProperty("class", e != null ? e.cls() : "random");
			java.nio.file.Files.writeString(server.getServerDirectory().resolve("kapisma_next.json"), o.toString());
		} catch (Exception ex) {
			Msg.broadcast(server, "Yeni dünya isteği yazılamadı: " + ex.getMessage());
			return;
		}
		restartTicks = 20 * 8;
		Msg.broadcast(server, "Bu oyun için yeni dünya hazırlanıyor. Sunucu 8 sn içinde yeniden başlayacak; ~40 sn sonra tekrar bağlanın.");
	}

	public void stop(String reason) {
		if (current != null) {
			current.stop(reason);
			last = current;
			current = null;
		}
	}
}
