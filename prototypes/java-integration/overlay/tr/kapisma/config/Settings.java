package tr.kapisma.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import tr.kapisma.Kapisma;

/** Kalici ayarlar: config/kapisma/settings.json. Host menuden degistirdikce kaydedilir. */
public class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String hostUuid = "";
	public String game = "UHC";                 // UHC | BINGO | MANHUNT | KATIL
	public String seedClass = "random";         // random | ova | orman | ...
	public String teamMode = "SOLO";            // SOLO | DUO
	public Map<String, String> originMode = new LinkedHashMap<>();   // oyun -> RANDOM | SELECT | OFF
	public List<String> bannedOrigins = new ArrayList<>();           // "arachnid", "phantom" ...
	public boolean uniqueOrigins = true;        // RANDOM modunda herkese farkli irk (yeterliyse)
	public List<String> runnerNames = new ArrayList<>();             // Manhunt: host'un sectigi runner adlari (bos = rastgele)
	/** Hile korumasi sahte cevher tuzu (ilk acilista rastgele uretilir, sabit kalir). */
	public long xraySalt = 0;
	public Map<String, Map<String, Double>> rules = new LinkedHashMap<>();   // oyun -> kural -> deger

	public Settings() {
		originMode.put("UHC", "RANDOM");
		originMode.put("BINGO", "RANDOM");
		originMode.put("ESYA", "OFF");
		originMode.put("DONEN_KARAKOL", "OFF");
		originMode.put("KERVAN_BASKINI", "OFF");
		originMode.put("YASAYAN_HARITA", "OFF");
		originMode.put("INSA_ET_SAVUN", "OFF");
		originMode.put("KIT_IHALESI", "OFF");
		originMode.put("ALCAK_IRTIFA", "OFF");
		originMode.put("KIRIK_TELSIZ", "OFF");
		originMode.put("MANHUNT", "SELECT");
		originMode.put("KATIL", "OFF");
	}

	public double num(String game, String key) {
		Map<String, Double> m = rules.get(game);
		Double saved = m == null ? null : m.get(key);
		for (RuleDef d : Rules.of(game)) {
			if (!d.key().equals(key)) continue;
			return saved == null || !Double.isFinite(saved) ? d.def() : Math.clamp(saved, d.min(), d.max());
		}
		throw new IllegalArgumentException("bilinmeyen kural: " + game + "." + key);
	}

	public boolean flag(String game, String key) {
		return num(game, key) >= 0.5;
	}

	public void set(String game, String key, double v) {
		rules.computeIfAbsent(game, g -> new LinkedHashMap<>()).put(key, v);
	}

	public void reset(String game) {
		rules.remove(game);
	}

	// ---- dosya ----
	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kapisma").resolve("settings.json");
	}

	public static Settings load() {
		Path f = file();
		try {
			if (Files.exists(f)) {
				Settings s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Settings.class);
				if (s != null) {
					if (s.rules == null) s.rules = new LinkedHashMap<>();
					if (s.originMode == null) s.originMode = new LinkedHashMap<>();
					if (s.runnerNames == null) s.runnerNames = new ArrayList<>();
					if (s.bannedOrigins == null) s.bannedOrigins = new ArrayList<>();
					for (String g : new String[] {"UHC", "BINGO", "MANHUNT"}) s.originMode.putIfAbsent(g, "RANDOM");
					s.originMode.putIfAbsent("KATIL", "OFF");
					s.originMode.putIfAbsent("ESYA", "OFF");
					s.originMode.putIfAbsent("DONEN_KARAKOL", "OFF");
					s.originMode.putIfAbsent("KERVAN_BASKINI", "OFF");
					s.originMode.putIfAbsent("YASAYAN_HARITA", "OFF");
					s.originMode.putIfAbsent("INSA_ET_SAVUN", "OFF");
					s.originMode.putIfAbsent("KIT_IHALESI", "OFF");
					s.originMode.putIfAbsent("ALCAK_IRTIFA", "OFF");
					s.originMode.putIfAbsent("KIRIK_TELSIZ", "OFF");
					return s;
				}
			}
		} catch (Exception e) {
			Kapisma.LOG.error("settings.json okunamadi, varsayilanlar kullaniliyor", e);
		}
		Settings s = new Settings();
		s.save();
		return s;
	}

	public void save() {
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Kapisma.LOG.error("settings.json yazilamadi", e);
		}
	}
}
