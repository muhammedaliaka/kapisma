package tr.kapisma.game;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import tr.kapisma.Kapisma;

/**
 * Bilgi kutuphanesi: Minecraft'in resmi verisinden uretilmis (tools/mcknow.py) "bu esya nerede bulunur" tablosu +
 * oyunun dunyasinda (tohum) gercekten bulunan biyom/yapi bilgisi (Facts) ile "bu esya bu 1000 blokluk alanda
 * elde edilebilir mi?" sorusunu yanitlar. Tombala kartinda ulasilamayacak esya cikmasin diye kullanilir.
 */
public final class Knowledge {
	private Knowledge() {}

	private static JsonObject data;

	static JsonObject data() {
		if (data == null) {
			try (var in = Knowledge.class.getResourceAsStream("/data/kapisma/knowledge.json")) {
				data = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			} catch (Exception e) {
				Kapisma.LOG.error("knowledge.json okunamadi", e);
				data = new JsonObject();
			}
		}
		return data;
	}

	public record Place(String type, int x, int z) {}

	/** Yapi kimligi -> kisa tur adi ("minecraft:village_plains" -> "village"). */
	public static String structType(String id) {
		String p = id.substring(id.indexOf(':') + 1);
		for (String t : new String[] {"village", "mineshaft", "ruined_portal", "shipwreck", "ocean_ruin", "trail_ruins", "trial_chambers",
			"desert_pyramid", "jungle_pyramid", "igloo", "pillager_outpost", "mansion", "ocean_monument", "buried_treasure", "ancient_city", "swamp_hut"}) {
			if (p.startsWith(t)) return t;
		}
		return p;
	}

	/** Bolgede bulunan biyomlar ve yapilar. */
	public static final class Facts {
		public final Set<String> biomes = new HashSet<>();                 // "plains"
		public final Map<String, Integer> structures = new LinkedHashMap<>();   // "minecraft:village_plains" -> adet
		public int radius, cx, cz;
		/** Biyom -> merkeze en yakin ornek noktanin uzakligi (blok). Yuzeyde bulunanlar gercek, yer altindakiler +30. */
		public final Map<String, Integer> biomeDist = new java.util.HashMap<>();
		/** Bolgedeki yapilar: tur ("village", "pillager_outpost"...) ve blok konumu. */
		public final java.util.List<Place> places = new java.util.ArrayList<>();

		public boolean hasWater() {
			for (String b : biomes) if (b.contains("ocean") || b.contains("river") || b.contains("swamp") || b.contains("beach")) return true;
			return false;
		}
	}

	private static String path(ResourceLocation rl) {
		return rl.getPath();
	}

	/** Dunya uretecinden, +-radius icindeki biyom ve yapilari tahmin eder (yaklasik 100-300 ms). */
	public static Facts scan(ServerLevel level, int cx, int cz, int radius) {
		Facts f = new Facts();
		f.radius = radius;
		f.cx = cx;
		f.cz = cz;
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		var src = gen.getBiomeSource();
		var sampler = rs.sampler();
		int[] ys = {64, 8, -40};
		for (int x = cx - radius; x <= cx + radius; x += 32) {
			for (int z = cz - radius; z <= cz + radius; z += 32) {
				int dist = (int) Math.hypot(x - cx, z - cz);
				for (int y : ys) {
					Holder<Biome> b = src.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler);
					final int dd = dist + (y == 64 ? 0 : 30);
					b.unwrapKey().ifPresent(k -> {
						String bn = path(k.location());
						f.biomes.add(bn);
						f.biomeDist.merge(bn, dd, Math::min);
					});
				}
			}
		}
		// yapilar: rastgele dagilimli yerlesim kurallari + biyom uygunlugu
		try {
			ChunkGeneratorStructureState st = level.getChunkSource().getGeneratorState();
			int rc = radius / 16 + 1, ccx = cx >> 4, ccz = cz >> 4;
			for (Holder<StructureSet> hs : st.possibleStructureSets()) {
				StructureSet set = hs.value();
				if (!(set.placement() instanceof RandomSpreadStructurePlacement p)) continue;
				int sp = p.spacing();
				for (int rx = Math.floorDiv(ccx - rc, sp) - 1; rx <= Math.floorDiv(ccx + rc, sp) + 1; rx++) {
					for (int rz = Math.floorDiv(ccz - rc, sp) - 1; rz <= Math.floorDiv(ccz + rc, sp) + 1; rz++) {
						ChunkPos cp = p.getPotentialStructureChunk(st.getLevelSeed(), rx * sp, rz * sp);
						if (Math.abs(cp.x - ccx) > rc || Math.abs(cp.z - ccz) > rc) continue;
						if (!p.isStructureChunk(st, cp.x, cp.z)) continue;
						int bx = cp.x * 16 + 8, bz = cp.z * 16 + 8;
						int h = gen.getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
						Holder<Biome> biome = src.getNoiseBiome(bx >> 2, h >> 2, bz >> 2, sampler);
						for (var e : set.structures()) {
							if (e.structure().value().biomes().contains(biome)) {
								e.structure().unwrapKey().ifPresent(k -> {
									f.structures.merge(k.location().toString(), 1, Integer::sum);
									f.places.add(new Place(structType(k.location().toString()), bx, bz));
								});
							}
						}
					}
				}
			}
		} catch (Exception e) {
			Kapisma.LOG.warn("Yapi taramasi basarisiz: {}", e.toString());
		}
		return f;
	}

	private static void addBlock(Set<Item> have, String id) {
		var b = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
		if (b.isPresent()) {
			Item it = b.get().asItem();
			if (it != Items.AIR) have.add(it);
		}
		addItem(have, id);          // bazi kimlikler hem blok hem esya (or. elmas cevheri)
	}

	private static void addItem(Set<Item> have, String id) {
		BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).ifPresent(have::add);
	}

	private static void addItems(Set<Item> have, JsonElement arr) {
		if (arr == null || !arr.isJsonArray()) return;
		for (JsonElement e : arr.getAsJsonArray()) addItem(have, e.getAsString());
	}

	/** Bolgede dogal olarak (dogrudan) bulunabilen esyalar: bloklar, dusenler, yaratik dususleri, yapi sandiklari. */
	public static Set<Item> naturalItems(Facts f) {
		JsonObject d = data();
		Set<Item> have = new HashSet<>();
		Set<String> blocks = new HashSet<>();
		Set<String> mobs = new HashSet<>();
		JsonObject biomes = d.getAsJsonObject("biomes");
		for (String b : f.biomes) {
			JsonObject bj = biomes.getAsJsonObject(b);
			if (bj == null) continue;
			for (JsonElement e : bj.getAsJsonArray("blocks")) blocks.add("minecraft:" + e.getAsString());
			for (JsonElement e : bj.getAsJsonArray("mobs")) mobs.add("minecraft:" + e.getAsString());
		}
		for (JsonElement e : d.getAsJsonArray("general")) blocks.add(e.getAsString());
		JsonObject bd = d.getAsJsonObject("blockDrops");
		for (String b : blocks) {
			addBlock(have, b);
			addItems(have, bd.get(b));
		}
		JsonObject md = d.getAsJsonObject("mobDrops");
		for (String m : mobs) addItems(have, md.get(m));
		JsonObject sl = d.getAsJsonObject("structureLoot");
		JsonObject sb = d.getAsJsonObject("structureBlocks");
		for (String s : f.structures.keySet()) {
			addItems(have, sl.get(s));
			JsonElement blks = sb.get(s);
			if (blks == null && s.startsWith("minecraft:village_")) blks = sb.get("minecraft:village_plains");
			if (blks != null) for (JsonElement e : blks.getAsJsonArray()) addBlock(have, e.getAsString());
		}
		addItems(have, sl.get("kapisma:dungeon"));               // zindanlar (monster_room) her biyomda olabilir
		if (f.hasWater()) addItems(have, d.get("fishing"));
		// dogadan gelen ozel durumlar
		if (mobs.contains("minecraft:chicken")) addItem(have, "minecraft:egg");
		if (mobs.contains("minecraft:cow")) addItem(have, "minecraft:leather");
		return have;
	}

	/** Dogal esyalardan tarif zinciriyle uretilebilen tum esyalar. */
	public static Set<Item> obtainable(MinecraftServer server, Facts f) {
		Set<Item> have = naturalItems(f);
		Set<String> mobs = new HashSet<>();
		JsonObject biomes = data().getAsJsonObject("biomes");
		for (String b : f.biomes) {
			JsonObject bj = biomes.getAsJsonObject(b);
			if (bj != null) for (JsonElement e : bj.getAsJsonArray("mobs")) mobs.add(e.getAsString());
		}
		// tarif zinciri
		var access = server.registryAccess();
		List<RecipeHolder<?>> recipes = new ArrayList<>(server.getRecipeManager().getRecipes());
		boolean changed = true;
		int guard = 0;
		while (changed && guard++ < 40) {
			changed = false;
			// ozel kurallar: kova ile sut/su/lav, ekim
			if (have.contains(Items.BUCKET)) {
				if (mobs.contains("cow") && have.add(Items.MILK_BUCKET)) changed = true;
				if (have.add(Items.WATER_BUCKET)) changed = true;
				if (have.add(Items.LAVA_BUCKET)) changed = true;
			}
			if (have.contains(Items.WHEAT_SEEDS) && have.add(Items.WHEAT)) changed = true;
			if (have.contains(Items.BEETROOT_SEEDS) && have.add(Items.BEETROOT)) changed = true;
			if (have.contains(Items.MELON_SEEDS) && have.add(Items.MELON_SLICE)) changed = true;
			if (have.contains(Items.PUMPKIN_SEEDS) && have.add(Items.PUMPKIN)) changed = true;
			for (RecipeHolder<?> h : recipes) {
				Recipe<?> r = h.value();
				Item result = r.getResultItem(access).getItem();
				if (result == Items.AIR || have.contains(result)) continue;
				var ings = r.getIngredients();
				if (ings.isEmpty()) continue;
				boolean ok = true;
				for (Ingredient ing : ings) {
					if (ing.isEmpty()) continue;
					boolean any = false;
					for (var st : ing.getItems()) if (have.contains(st.getItem())) { any = true; break; }
					if (!any) { ok = false; break; }
				}
				if (ok) { have.add(result); changed = true; }
			}
		}
		return have;
	}

	/** Bu esya dogada hangi biyomlarda bulunur? (yalnizca bolgede olanlar, en fazla n tane) */
	public static List<String> biomesFor(Item item, Facts f, int n) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		JsonObject d = data();
		JsonObject bd = d.getAsJsonObject("blockDrops");
		Set<String> srcBlocks = new HashSet<>();
		if (BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).isPresent()) srcBlocks.add(id);
		for (var e : bd.entrySet()) for (JsonElement x : e.getValue().getAsJsonArray()) if (x.getAsString().equals(id)) srcBlocks.add(e.getKey());
		Set<String> srcMobs = new HashSet<>();
		for (var e : d.getAsJsonObject("mobDrops").entrySet()) for (JsonElement x : e.getValue().getAsJsonArray()) if (x.getAsString().equals(id)) srcMobs.add(e.getKey());
		List<String> out = new ArrayList<>();
		JsonObject biomes = d.getAsJsonObject("biomes");
		for (String b : f.biomes) {
			JsonObject bj = biomes.getAsJsonObject(b);
			if (bj == null) continue;
			boolean hit = false;
			for (JsonElement e : bj.getAsJsonArray("blocks")) if (srcBlocks.contains("minecraft:" + e.getAsString())) { hit = true; break; }
			if (!hit) for (JsonElement e : bj.getAsJsonArray("mobs")) if (srcMobs.contains("minecraft:" + e.getAsString())) { hit = true; break; }
			if (hit) out.add(b);
		}
		return out.size() <= n ? out : out.subList(0, n);
	}

	/** Esyanin kaynak yapilari (sandik/blok): bolgede bulunanlar. */
	public static List<String> structuresFor(Item item, Facts f) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		List<String> out = new ArrayList<>();
		JsonObject sl = data().getAsJsonObject("structureLoot");
		for (String s : f.structures.keySet()) {
			JsonElement arr = sl.get(s);
			if (arr == null) continue;
			for (JsonElement x : arr.getAsJsonArray()) if (x.getAsString().equals(id)) { out.add(s); break; }
		}
		return out;
	}

	private static final Map<String, String> STRUCT_TR = new HashMap<>();
	static {
		STRUCT_TR.put("mineshaft", "terk edilmiş maden");
		STRUCT_TR.put("village", "köy");
		STRUCT_TR.put("desert_pyramid", "çöl tapınağı");
		STRUCT_TR.put("jungle_pyramid", "orman tapınağı");
		STRUCT_TR.put("igloo", "iglo");
		STRUCT_TR.put("pillager_outpost", "yağmacı karakolu");
		STRUCT_TR.put("ruined_portal", "yıkık portal");
		STRUCT_TR.put("shipwreck", "gemi enkazı");
		STRUCT_TR.put("ocean_ruin", "su altı harabesi");
		STRUCT_TR.put("mansion", "malikâne");
		STRUCT_TR.put("buried_treasure", "gömülü hazine");
		STRUCT_TR.put("ocean_monument", "okyanus anıtı");
		STRUCT_TR.put("ancient_city", "kadim şehir");
		STRUCT_TR.put("trial_chambers", "deneme odaları");
	}

	public static String structureName(String id) {
		String p = id.substring(id.indexOf(':') + 1);
		for (var e : STRUCT_TR.entrySet()) if (p.startsWith(e.getKey())) return e.getValue();
		return p;
	}

	public static JsonArray debugBiomes(Facts f) {
		JsonArray a = new JsonArray();
		for (String b : f.biomes) a.add(b);
		return a;
	}
}
