package tr.kapisma.seed;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import tr.kapisma.Kapisma;

/** Dogrulanmis seed havuzu (seeds.json). Kullanilan seed'ler tekrar secilmez. */
public final class SeedPool {
	public record Entry(long seed, int spawnX, int spawnZ, String biome, String cls) {}

	private final Map<String, List<Entry>> byClass = new LinkedHashMap<>();
	private final Map<String, String> names = new LinkedHashMap<>();
	private final Set<Long> used = new HashSet<>();
	private final Path usedFile;

	private SeedPool(Path usedFile) {
		this.usedFile = usedFile;
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("kapisma");
	}

	public static SeedPool load() {
		SeedPool p = new SeedPool(dir().resolve("used_seeds.json"));
		Path f = dir().resolve("seeds.json");
		try {
			if (!Files.exists(f)) {
				Kapisma.LOG.warn("seeds.json yok: {} -> seed havuzu bos, rastgele seed kullanilacak", f);
				return p;
			}
			JsonObject root = new Gson().fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
			JsonObject nm = root.getAsJsonObject("class_names");
			if (nm != null) for (var e : nm.entrySet()) p.names.put(e.getKey(), e.getValue().getAsString());
			for (var e : root.getAsJsonObject("classes").entrySet()) {
				if (!e.getValue().isJsonArray()) continue;
				List<Entry> list = new ArrayList<>();
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					try {
						JsonObject o = el.getAsJsonObject();
						JsonArray sp = o.getAsJsonArray("spawn");
						list.add(new Entry(Long.parseLong(o.get("seed").getAsString()), sp.get(0).getAsInt(), sp.get(1).getAsInt(),
							o.get("biome").getAsString(), e.getKey()));
					} catch (RuntimeException ex) {
						Kapisma.LOG.warn("Bozuk seed kaydı atlandı (sınıf {})", e.getKey());
					}
				}
				p.byClass.put(e.getKey(), list);
			}
			if (Files.exists(p.usedFile)) {
				for (JsonElement el : new Gson().fromJson(Files.readString(p.usedFile, StandardCharsets.UTF_8), JsonArray.class))
					p.used.add(Long.parseLong(el.getAsString()));
			}
			Kapisma.LOG.info("Seed havuzu: {} sinif, {} seed ({} kullanilmis)", p.byClass.size(), p.total(), p.used.size());
		} catch (Exception e) {
			Kapisma.LOG.error("seeds.json okunamadi", e);
		}
		return p;
	}

	public int total() {
		return byClass.values().stream().mapToInt(List::size).sum();
	}

	public List<String> classKeys() {
		List<String> r = new ArrayList<>();
		for (var e : byClass.entrySet()) if (!e.getValue().isEmpty()) r.add(e.getKey());
		return r;
	}

	public String displayName(String cls) {
		return "random".equals(cls) ? "Rastgele" : names.getOrDefault(cls, cls);
	}

	public int size(String cls) {
		return byClass.getOrDefault(cls, List.of()).size();
	}

	public boolean selectable(String cls) { return "random".equals(cls) || size(cls) > 0; }

	public boolean matches(String cls, long seed) {
		return "random".equals(cls) || byClass.getOrDefault(cls, List.of()).stream().anyMatch(e -> e.seed() == seed);
	}

	/** cls = "random" ise tum siniflardan; bos bir belirli sinif baska sinifa dusmez. */
	public Entry pick(String cls, Random rnd) {
		List<Entry> all = new ArrayList<>();
		if ("random".equals(cls)) byClass.values().forEach(all::addAll);
		else all.addAll(byClass.getOrDefault(cls, List.of()));
		if (all.isEmpty()) return null;
		List<Entry> fresh = all.stream().filter(e -> !used.contains(e.seed())).toList();
		if (fresh.isEmpty()) {            // hepsi kullanildi: bu siniflari yeniden ac
			all.forEach(e -> used.remove(e.seed()));
			fresh = all;
		}
		return fresh.get(rnd.nextInt(fresh.size()));
	}

	public void markUsed(long seed) {
		used.add(seed);
		try {
			Files.createDirectories(usedFile.getParent());
			JsonArray a = new JsonArray();
			used.forEach(s -> a.add(Long.toString(s)));
			Files.writeString(usedFile, a.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Kapisma.LOG.warn("used_seeds.json yazilamadi", e);
		}
	}
}
