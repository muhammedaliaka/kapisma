package tr.kapisma.seed;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tr.kapisma.game.Knowledge;

/**
 * Seed kalitesi: sadece "koy var mi" degil, merkez cevresinde ne kadar cesitli ve YAKIN onemli yapi (koy, karakol, tapinak,
 * iglo, gemi, harabe portal, deneme odasi...) oldugu ve bunlarin kumeler halinde (koyun yaninda baska yapi) bulunup bulunmadigi.
 * Gercek dunya ureteciyle (Knowledge.scan) hesaplanir; cubiomes/chunkbase tahmininden daha guvenilirdir.
 */
public final class SeedScore {
	private SeedScore() {}

	private static final Map<String, Double> W = new LinkedHashMap<>();
	static {
		W.put("village", 10.0); W.put("pillager_outpost", 7.0); W.put("desert_pyramid", 8.0); W.put("jungle_pyramid", 8.0);
		W.put("mansion", 9.0); W.put("ocean_monument", 7.0); W.put("trial_chambers", 6.0); W.put("ancient_city", 5.0);
		W.put("igloo", 4.0); W.put("shipwreck", 4.0); W.put("swamp_hut", 3.0); W.put("ruined_portal", 3.0);
		W.put("buried_treasure", 3.0); W.put("trail_ruins", 2.0); W.put("ocean_ruin", 2.0); W.put("mineshaft", 1.5);
	}
	/** Cesitlilik/kume sayarken mineshaft (her yerde var) ve su alti harabesi sayilmaz. */
	private static final Set<String> COMMON = Set.of("mineshaft", "ocean_ruin");

	public record Result(double score, int types, double nearest, int cluster, String bestCluster, boolean ok, Map<String, Integer> counts) {
		public JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("score", Math.round(score * 10) / 10.0);
			o.addProperty("types", types);
			o.addProperty("nearest", Math.round(nearest));
			o.addProperty("cluster", cluster);
			o.addProperty("best_cluster", bestCluster);
			o.addProperty("ok", ok);
			JsonObject c = new JsonObject();
			counts.forEach(c::addProperty);
			o.add("counts", c);
			return o;
		}
	}

	public static Result score(Knowledge.Facts f) {
		Map<String, Integer> counts = new TreeMap<>();
		for (var p : f.places) counts.merge(p.type(), 1, Integer::sum);
		double sc = 0;
		int types = 0;
		for (var e : counts.entrySet()) {
			double w = W.getOrDefault(e.getKey(), 1.0);
			sc += w * (1 + 0.4 * (Math.min(e.getValue(), 3) - 1));
			if (!COMMON.contains(e.getKey())) types++;
		}
		sc += 5.0 * Math.min(types, 7);
		double nearest = 1e9;
		for (var p : f.places) {
			if (COMMON.contains(p.type())) continue;
			nearest = Math.min(nearest, Math.hypot(p.x() - f.cx, p.z() - f.cz));
		}
		if (nearest < 1e9) sc += Math.max(0, 15 - nearest / 60.0);
		// kume: bir yapinin 300 blok icinde kac FARKLI baska onemli tur var
		int best = 0;
		String bestName = "";
		for (var a : f.places) {
			if (COMMON.contains(a.type())) continue;
			Set<String> near = new java.util.TreeSet<>();
			for (var b : f.places) {
				if (b == a || COMMON.contains(b.type()) || b.type().equals(a.type())) continue;
				if (Math.hypot(a.x() - b.x(), a.z() - b.z()) <= 300) near.add(b.type());
			}
			if (near.size() > best || (near.size() == best && a.type().equals("village") && !bestName.startsWith("village"))) {
				best = near.size();
				bestName = a.type() + (near.isEmpty() ? "" : " + " + String.join(" + ", near));
			}
		}
		sc += 6.0 * Math.min(best, 3);
		boolean ok = types >= 3 && best >= 1 && nearest <= 600;
		return new Result(sc, types, nearest == 1e9 ? -1 : nearest, best, bestName, ok, counts);
	}

	/** Biyom listesi json. */
	public static JsonArray biomes(Knowledge.Facts f) {
		JsonArray a = new JsonArray();
		f.biomes.stream().sorted().forEach(a::add);
		return a;
	}
}
