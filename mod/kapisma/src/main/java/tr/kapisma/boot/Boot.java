package tr.kapisma.boot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Sunucu Minecraft'i açmadan ÖNCE çalışır (Fabric "preLaunch"). İşletim sisteminden bağımsızdır
 * (Windows, Linux, macOS, Android/Termux), PowerShell/bash gerekmez:
 *  1) kapisma_next.json varsa (Manhunt yeni dünya isteği): eski dünya klasörünü siler, yeni seed'i server.properties'e yazar.
 *  2) Dünya yoksa (ilk açılış): seed havuzundan kullanılmamış bir seed seçer.
 *  3) Donanım profili: az RAM'li / yavaş cihazda (tablet, eski dizüstü) görüş mesafesini ve hile koruması
 *     maliyetini düşürür. Not: bu sınıf Minecraft sınıflarına dokunmaz (henüz yüklenmedi).
 */
public final class Boot implements PreLaunchEntrypoint {
	public enum Profile { LITE, NORMAL }

	public static volatile Profile PROFILE = Profile.NORMAL;
	public static volatile String PROFILE_WHY = "";

	@Override
	public void onPreLaunch() {
		try {
			prepare(FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize());
		} catch (Throwable t) {
			System.err.println("[Kapisma] onyukleme hatasi (devam ediliyor): " + t);
		}
	}

	static void prepare(Path root) throws IOException {
		Path propsFile = root.resolve("server.properties");
		List<String> props = Files.exists(propsFile) ? new ArrayList<>(Files.readAllLines(propsFile, StandardCharsets.ISO_8859_1)) : new ArrayList<>();
		boolean changed = false;

		// ---- 1) donanim profili ----
		detectProfile();
		System.out.println("[Kapisma] Donanim profili: " + PROFILE + " (" + PROFILE_WHY + ")");
		if (PROFILE == Profile.LITE) {
			changed |= cap(props, "view-distance", 6);
			changed |= cap(props, "simulation-distance", 4);
			changed |= cap(props, "entity-broadcast-range-percentage", 70);
		}

		// ---- 2) yeni dunya istegi / ilk acilis ----
		String levelName = get(props, "level-name", "world");
		Path world = root.resolve(levelName).normalize();
		if (!world.startsWith(root) || world.equals(root)) world = root.resolve("world");   // yol disina tasmasin
		Path next = root.resolve("kapisma_next.json");
		if (Files.exists(next)) {
			String seed = "";
			String cls = "?";
			try {
				JsonObject j = new Gson().fromJson(Files.readString(next, StandardCharsets.UTF_8), JsonObject.class);
				if (j != null && j.has("seed")) seed = j.get("seed").getAsString();
				if (j != null && j.has("class")) cls = j.get("class").getAsString();
			} catch (Exception e) {
				System.err.println("[Kapisma] kapisma_next.json okunamadi: " + e);
			}
			if (Files.exists(world)) deleteTree(world);
			set(props, "level-seed", seed);
			changed = true;
			Files.deleteIfExists(next);
			System.out.println("[Kapisma] Yeni dunya: seed=" + seed + " sinif=" + cls);
		} else if (!Files.exists(world)) {
			String seed = pickFromPool(root);
			if (seed != null) {
				set(props, "level-seed", seed);
				changed = true;
				System.out.println("[Kapisma] Ilk acilis seed'i havuzdan secildi: " + seed);
			}
		}

		if (changed) Files.write(propsFile, props, StandardCharsets.ISO_8859_1);
	}

	// ------------------------------------------------------------------ profil
	private static void detectProfile() {
		String ov = System.getProperty("kapisma.profile");
		if (ov == null || ov.isBlank()) ov = System.getenv("KAPISMA_PROFILE");
		ov = ov == null ? "auto" : ov.trim().toLowerCase(Locale.ROOT);
		long maxMb = Runtime.getRuntime().maxMemory() >> 20;
		int cpus = Runtime.getRuntime().availableProcessors();
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		boolean arm = arch.contains("aarch64") || arch.contains("arm");
		String info = maxMb + " MB yigin, " + cpus + " cekirdek, " + arch;
		if (ov.equals("lite")) { PROFILE = Profile.LITE; PROFILE_WHY = "elle secildi; " + info; return; }
		if (ov.equals("normal")) { PROFILE = Profile.NORMAL; PROFILE_WHY = "elle secildi; " + info; return; }
		if (maxMb < 3500) { PROFILE = Profile.LITE; PROFILE_WHY = "az bellek; " + info; return; }
		if (cpus <= 2) { PROFILE = Profile.LITE; PROFILE_WHY = "az cekirdek; " + info; return; }
		if (arm && maxMb < 5000) { PROFILE = Profile.LITE; PROFILE_WHY = "ARM cihaz + sinirli bellek; " + info; return; }
		PROFILE = Profile.NORMAL;
		PROFILE_WHY = info;
	}

	// ------------------------------------------------------------------ seed havuzu
	private static String pickFromPool(Path root) {
		try {
			Path seeds = root.resolve("config").resolve("kapisma").resolve("seeds.json");
			if (!Files.exists(seeds)) return null;
			JsonObject pool = new Gson().fromJson(Files.readString(seeds, StandardCharsets.UTF_8), JsonObject.class);
			List<String> all = new ArrayList<>();
			for (var e : pool.getAsJsonObject("classes").entrySet())
				for (JsonElement el : e.getValue().getAsJsonArray()) all.add(el.getAsJsonObject().get("seed").getAsString());
			if (all.isEmpty()) return null;
			Set<String> used = new HashSet<>();
			Path usedFile = root.resolve("config").resolve("kapisma").resolve("used_seeds.json");
			if (Files.exists(usedFile)) {
				JsonArray u = new Gson().fromJson(Files.readString(usedFile, StandardCharsets.UTF_8), JsonArray.class);
				if (u != null) for (JsonElement el : u) used.add(el.getAsString());
			}
			List<String> fresh = new ArrayList<>();
			for (String s : all) if (!used.contains(s)) fresh.add(s);
			if (fresh.isEmpty()) fresh = all;
			return fresh.get(new java.util.Random().nextInt(fresh.size()));
		} catch (Exception e) {
			System.err.println("[Kapisma] seed havuzu okunamadi (rastgele seed kullanilacak): " + e);
			return null;
		}
	}

	// ------------------------------------------------------------------ yardimcilar
	private static String get(List<String> lines, String key, String def) {
		for (String l : lines) if (l.startsWith(key + "=")) return l.substring(key.length() + 1).trim();
		return def;
	}

	private static void set(List<String> lines, String key, String value) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(key + "=")) { lines.set(i, key + "=" + value); return; }
		}
		lines.add(key + "=" + value);
	}

	/** Değer sınırdan büyükse (ya da yoksa varsayılan sınırın üstündeyse) sınıra çeker. */
	private static boolean cap(List<String> lines, String key, int max) {
		String cur = get(lines, key, null);
		try {
			if (cur == null || Integer.parseInt(cur) > max) { set(lines, key, Integer.toString(max)); return true; }
		} catch (NumberFormatException e) {
			set(lines, key, Integer.toString(max));
			return true;
		}
		return false;
	}

	private static void deleteTree(Path dir) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
				try { Files.delete(f); } catch (IOException e) { f.toFile().setWritable(true); Files.delete(f); }
				return FileVisitResult.CONTINUE;
			}
			@Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
