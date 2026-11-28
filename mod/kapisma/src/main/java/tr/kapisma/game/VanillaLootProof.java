package tr.kapisma.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import tr.kapisma.Kapisma;

/**
 * The hard-coded lower bounds in BingoSurvey are only valid for vanilla tables.
 * Fail closed for a replaced data-pack resource or a runtime-modified table.
 * Scoped to one survey/round so a resource reload cannot leave a stale cache.
 */
final class VanillaLootProof {
	private final MinecraftServer server;
	private final Map<Block, Boolean> cache = new HashMap<>();
	VanillaLootProof(MinecraftServer server) { this.server = server; }

	boolean accepts(Block block) { return cache.computeIfAbsent(block, this::check); }

	private boolean check(Block block) {
		var key = block.getLootTable();
		var id = key.location();
		try {
			if (!"minecraft".equals(id.getNamespace())) return false;
			var path = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "loot_table/" + id.getPath() + ".json");
			var res = server.getResourceManager().getResource(path).orElse(null);
			if (res == null || !res.knownPackInfo().map(pack -> pack.isVanilla()).orElse(false)) {
				Kapisma.LOG.info("Kaynak ganimet kanıtı reddedildi: {} (vanilla dışı veya eksik tablo)", id);
				return false;
			}
			var ops = server.reloadableRegistries().get().createSerializationContext(JsonOps.INSTANCE);
			JsonElement expected;
			try (var reader = res.openAsReader()) {
				var table = LootTable.DIRECT_CODEC.parse(ops, JsonParser.parseReader(reader)).result().orElse(null);
				if (table == null) return false;
				expected = LootTable.DIRECT_CODEC.encodeStart(ops, table).result().orElse(null);
			}
			var active = server.reloadableRegistries().getLootTable(key);
			var actual = LootTable.DIRECT_CODEC.encodeStart(ops, active).result().orElse(null);
			boolean ok = expected != null && expected.equals(actual);
			if (!ok) Kapisma.LOG.info("Kaynak ganimet kanıtı reddedildi: {} (etkin tablo değişmiş)", id);
			return ok;
		} catch (Exception ex) {
			Kapisma.LOG.warn("Kaynak ganimet kanıtı okunamadı: {}", id, ex);
			return false;
		}
	}
}
