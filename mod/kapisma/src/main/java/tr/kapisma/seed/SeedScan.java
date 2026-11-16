package tr.kapisma.seed;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.server.MinecraftServer;
import tr.kapisma.Kapisma;
import tr.kapisma.game.GameWorld;
import tr.kapisma.game.Knowledge;

/**
 * Yonetici araci: config/kapisma/seedscan_in.json ([{seed,x,z}...]) icindeki seed'leri gercek sunucuda gecici dunyada acip
 * (oyundaki ureteci), yapilarini tarar ve SeedScore ile puanlar. Sonuc seedscan_out.jsonl'e satir satir yazilir.
 * Her tikte bir seed. Komut: /kapisma debug seedscan [yaricap]
 */
public final class SeedScan {
	private SeedScan() {}

	private static final Queue<JsonObject> QUEUE = new ArrayDeque<>();
	private static Path out;
	private static int radius = 1000, done, total;

	public static boolean running() {
		return !QUEUE.isEmpty();
	}

	public static String progress() {
		return done + "/" + total;
	}

	public static String start(int r) {
		try {
			Path dir = SeedPool.dir();
			Path in = dir.resolve("seedscan_in.json");
			if (!Files.exists(in)) return "Girdi yok: " + in;
			JsonArray arr = new Gson().fromJson(Files.readString(in, StandardCharsets.UTF_8), JsonArray.class);
			QUEUE.clear();
			out = dir.resolve("seedscan_out.jsonl");
			java.util.Set<String> already = new java.util.HashSet<>();          // devam: onceden taranmis seed'ler atlanir
			if (Files.exists(out)) {
				for (String line : Files.readAllLines(out, StandardCharsets.UTF_8)) {
					try { already.add(new Gson().fromJson(line, JsonObject.class).get("seed").getAsString()); } catch (Exception ignored) {}
				}
			}
			arr.forEach(e -> { if (!already.contains(e.getAsJsonObject().get("seed").getAsString())) QUEUE.add(e.getAsJsonObject()); });
			radius = r;
			done = 0;
			total = QUEUE.size();
			return total + " seed taranacak (yaricap " + r + ").";
		} catch (Exception e) {
			return "Hata: " + e;
		}
	}

	public static void tick(MinecraftServer server) {
		JsonObject job = QUEUE.poll();
		if (job == null) return;
		long seed = Long.parseLong(job.get("seed").getAsString());
		int x = job.get("x").getAsInt(), z = job.get("z").getAsInt();
		GameWorld w = null;
		try {
			w = GameWorld.open(server, seed, x, z, new GameWorld.Options(true, false, true, false));
			var f = Knowledge.scan(w.level, x, z, radius);
			var res = SeedScore.score(f);
			JsonObject o = new JsonObject();
			o.addProperty("seed", Long.toString(seed));
			o.addProperty("x", x);
			o.addProperty("z", z);
			o.add("q", res.toJson());
			o.add("biomes", SeedScore.biomes(f));
			Files.writeString(out, o + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (Exception e) {
			Kapisma.LOG.warn("seedscan {} basarisiz: {}", seed, e.toString());
		} finally {
			if (w != null) {
				try { w.delete(); } catch (Exception ignored) {}
			}
			done++;
			if (QUEUE.isEmpty()) Kapisma.LOG.info("seedscan bitti: {} seed", done);
		}
	}
}
