package tr.kapisma.lobby;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import tr.kapisma.Kapisma;

/**
 * Kalici puan tablosu (config/kapisma/scores.json): parkur ve kapisma (oyun) puanlari.
 *
 * Parkur: bir bolumu ilk kez bitirmek 10/20/40/80 puan; tum parkuru bitirme suresi en iyi sure olarak tutulur.
 * Kapisma: oyuna katilim 5, her oldurme 10, kazanan takimin her uyesine 100 (en az 2 takimli oyunda).
 */
public final class Scores {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final int[] LEVEL_POINTS = {10, 20, 40, 80};
	public static final int WIN = 100;
	public static final int KILL = 10;
	public static final int PLAY = 5;

	public static final class Entry {
		public String name = "";
		public int parkourPoints;
		public int levelMask;                 // bit i = bolum i bitirildi
		public int bestTimeSec = -1;          // tum parkuru bitirme (sn), -1 = yok
		public int parkourFinishes;
		public int gamePoints;
		public int wins;
		public int kills;
		public int games;
	}

	public final Map<String, Entry> players = new LinkedHashMap<>();
	private final transient Path file;

	private Scores(Path file) {
		this.file = file;
	}

	public static Scores load() {
		Path f = FabricLoader.getInstance().getConfigDir().resolve("kapisma").resolve("scores.json");
		Scores s = new Scores(f);
		try {
			if (Files.exists(f)) {
				Scores loaded = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Scores.class);
				if (loaded != null && loaded.players != null) s.players.putAll(loaded.players);
			}
		} catch (Exception e) {
			Kapisma.LOG.warn("scores.json okunamadi", e);
		}
		return s;
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Kapisma.LOG.warn("scores.json yazilamadi", e);
		}
	}

	public Entry of(String uuid, String name) {
		Entry e = players.computeIfAbsent(uuid, k -> new Entry());
		e.name = name;
		return e;
	}

	/** Bolum ilk kez bitti mi? Evetse puan eklenir ve puan doner, degilse 0. */
	public int parkourLevelCleared(String uuid, String name, int level) {
		Entry e = of(uuid, name);
		if ((e.levelMask & (1 << level)) != 0) return 0;
		e.levelMask |= 1 << level;
		e.parkourPoints += LEVEL_POINTS[level];
		Kapisma.LOG.info("PUAN {} parkur bolum {} +{} (toplam {})", name, level, LEVEL_POINTS[level], e.parkourPoints);
		save();
		return LEVEL_POINTS[level];
	}

	/** Tum parkur bitti: en iyi sure guncellenir. Doner: yeni rekor mu. */
	public boolean parkourFinished(String uuid, String name, int seconds) {
		Entry e = of(uuid, name);
		e.parkourFinishes++;
		boolean best = e.bestTimeSec < 0 || seconds < e.bestTimeSec;
		if (best) e.bestTimeSec = seconds;
		save();
		return best;
	}

	/** Oyun sonu: katilim + oldurme (+ galibiyet). */
	public int gameResult(String uuid, String name, int kills, boolean won) {
		Entry e = of(uuid, name);
		int pts = PLAY + kills * KILL + (won ? WIN : 0);
		e.gamePoints += pts;
		e.kills += kills;
		e.games++;
		if (won) e.wins++;
		save();
		return pts;
	}

	public List<Entry> topParkour(int n) {
		List<Entry> l = new ArrayList<>();
		for (Entry e : players.values()) if (e.parkourPoints > 0) l.add(e);
		l.sort(Comparator.<Entry>comparingInt(e -> -e.parkourPoints)
			.thenComparingInt(e -> e.bestTimeSec < 0 ? Integer.MAX_VALUE : e.bestTimeSec));
		return l.subList(0, Math.min(n, l.size()));
	}

	public List<Entry> topGames(int n) {
		List<Entry> l = new ArrayList<>();
		for (Entry e : players.values()) if (e.gamePoints > 0) l.add(e);
		l.sort(Comparator.<Entry>comparingInt(e -> -e.gamePoints).thenComparingInt(e -> -e.wins));
		return l.subList(0, Math.min(n, l.size()));
	}
}
