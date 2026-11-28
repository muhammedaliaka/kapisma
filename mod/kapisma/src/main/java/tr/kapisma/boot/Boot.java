package tr.kapisma.boot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.util.UUID;
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
 *  1) kapisma_next.json varsa (Manhunt yeni dünya isteği): eski dünyayı arşivler, yeni seed'i server.properties'e yazar.
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
		Path root = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		boolean restartRequested = Files.exists(root.resolve("kapisma_next.json"), LinkOption.NOFOLLOW_LINKS);
		try {
			prepare(root);
		} catch (Exception t) {
			if (restartRequested) throw new IllegalStateException("Kapışma: yeni dünya işlemi güvenli biçimde tamamlanamadı.", t);
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
		if (Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
			if (!Files.isRegularFile(next, LinkOption.NOFOLLOW_LINKS))
				throw new IOException("Restart request is not a regular file");
			String request = Files.readString(next, StandardCharsets.UTF_8);
			String seed = null, cls = null;
			try {
				JsonObject j = new Gson().fromJson(request, JsonObject.class);
				if (j == null || !j.has("seed") || !j.has("class")) throw new IllegalArgumentException("missing seed or class");
				seed = j.get("seed").getAsString();
				cls = j.get("class").getAsString();
				if (!cls.matches("[a-zA-Z0-9_\\-]+")) throw new IllegalArgumentException("invalid class");
				if (!seed.isEmpty()) Long.parseLong(seed);
				else if (!"random".equals(cls)) throw new IllegalArgumentException("empty seed for fixed class");
			} catch (RuntimeException ex) {
				Files.move(next, root.resolve("kapisma-next-rejected-" + UUID.randomUUID() + ".json"), StandardCopyOption.ATOMIC_MOVE);
				System.err.println("[Kapisma] Yeni dunya istegi reddedildi; mevcut dunya korundu: " + ex);
				seed = null;
			}
			if (seed != null) {
				// Never resolve an unsafe level-name to an unrelated default world.
				Path requested = root.resolve(levelName).normalize();
				if (!requested.equals(world) || !root.equals(world.getParent())
						|| Path.of(levelName).isAbsolute() || Files.isSymbolicLink(world)
						|| world.getFileName().toString().startsWith("kapisma-world-backup-"))
					throw new IOException("Unsafe world directory: " + levelName);
				if (Files.exists(world, LinkOption.NOFOLLOW_LINKS)
						&& (!Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)
							|| !world.toRealPath().getParent().equals(root.toRealPath())
							|| !Files.isRegularFile(world.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)))
					throw new IOException("World directory is not a verified Minecraft save: " + world);
				set(props, "level-seed", seed);
				commitRestart(root, world, next, propsFile, props);
				changed = false;
				System.out.println("[Kapisma] Yeni dunya: seed=" + seed + " sinif=" + cls);
			}
		} else if (!Files.exists(world) && get(props, "level-seed", "").isBlank()) {
			String seed = pickFromPool(root);
			if (seed != null) {
				set(props, "level-seed", seed);
				changed = true;
				System.out.println("[Kapisma] Ilk acilis seed'i havuzdan secildi: " + seed);
			}
		}

		// ---- 3) izinli oyuncular (offline-mode sunucuda baskasinin adini kullanmayi engeller: ad listesi) ----
		changed |= applyAllowList(root, props);

		if (changed) Files.write(propsFile, props, StandardCharsets.ISO_8859_1);
	}

	/** izinli_oyuncular.txt'te (satir basina bir ad, # yorum) en az bir ad varsa whitelist.json yazilir ve beyaz liste acilir. */
	private static boolean applyAllowList(Path root, List<String> props) throws IOException {
		Path f = root.resolve("izinli_oyuncular.txt");
		if (!Files.exists(f)) return false;
		JsonArray arr = new JsonArray();
		Set<String> seen = new HashSet<>();
		for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
			String n = raw.replace("﻿", "").trim();
			if (n.isEmpty() || n.startsWith("#")) continue;
			if (!n.matches("[A-Za-z0-9_]{3,16}")) { System.err.println("[Kapisma] izinli_oyuncular.txt: gecersiz ad atlandi: " + n); continue; }
			if (!seen.add(n.toLowerCase(Locale.ROOT))) continue;
			JsonObject o = new JsonObject();
			o.addProperty("uuid", java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8)).toString());
			o.addProperty("name", n);
			arr.add(o);
		}
		if (arr.size() == 0) return false;
		Files.writeString(root.resolve("whitelist.json"), new Gson().toJson(arr), StandardCharsets.UTF_8);
		boolean ch = !"true".equals(get(props, "white-list", "false")) || !"true".equals(get(props, "enforce-whitelist", "false"));
		set(props, "white-list", "true");
		set(props, "enforce-whitelist", "true");
		System.out.println("[Kapisma] Izinli oyuncu listesi uygulandi: " + arr.size() + " oyuncu (beyaz liste acik)");
		return ch;
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

	/** A pending request survives an interrupted commit; the old world is never recursively deleted. */
	private static void commitRestart(Path root, Path world, Path next, Path propsFile, List<String> props) throws IOException {
		Path stage = Files.createTempFile(root, ".kapisma-properties-", ".tmp");
		Path backup = root.resolve("kapisma-world-backup-" + UUID.randomUUID());
		boolean archived = false;
		try {
			Files.write(stage, props, StandardCharsets.ISO_8859_1);
			if (Files.exists(world, LinkOption.NOFOLLOW_LINKS)) {
				Files.move(world, backup, StandardCopyOption.ATOMIC_MOVE);
				archived = true;
			}
			try {
				Files.move(stage, propsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ex) {
				if (archived) {
					try { Files.move(backup, world, StandardCopyOption.ATOMIC_MOVE); }
					catch (IOException rollback) { ex.addSuppressed(rollback); }
				}
				throw ex;
			}
			// Consume only after the new properties and archived old save are durable in the directory.
			Files.move(next, root.resolve("kapisma-next-applied-" + UUID.randomUUID() + ".json"), StandardCopyOption.ATOMIC_MOVE);
			if (archived) System.out.println("[Kapisma] Eski dunya arsivlendi: " + backup.getFileName());
		} finally {
			Files.deleteIfExists(stage);
		}
	}
}
