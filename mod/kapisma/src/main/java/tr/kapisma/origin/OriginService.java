package tr.kapisma.origin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import tr.kapisma.Kapisma;
import tr.kapisma.config.Settings;

/** Origins (irk) atama. Origins'in /origin komutu uzerinden calisir: Origins'e derleme bagimliligi yok. */
public final class OriginService {
	private OriginService() {}

	/** Origins'in 8 irki (Hayalet ve Elytrian dengesiz/uyumsuz oldugu icin cikarildi) + Kapisma'nin veri paketiyle eklediği 5 irk. */
	public static final List<String> ALL = List.of(
		"arachnid", "avian", "blazeborn", "enderian", "feline", "human", "merling", "shulk",
		"golem", "fox", "dwarf", "frostborn", "wolf");

	private static final java.util.Set<String> KAPISMA_NS = java.util.Set.of("golem", "fox", "dwarf", "frostborn", "wolf");

	/** Origin kimligi: "origins:feline" veya "kapisma:golem". */
	public static String fullId(String id) {
		return (KAPISMA_NS.contains(id) ? "kapisma:" : "origins:") + id;
	}

	public static final Map<String, Item> ICONS = Map.ofEntries(
		Map.entry("arachnid", Items.COBWEB), Map.entry("avian", Items.FEATHER), Map.entry("blazeborn", Items.BLAZE_POWDER),
		Map.entry("elytrian", Items.ELYTRA), Map.entry("enderian", Items.ENDER_PEARL), Map.entry("feline", Items.COD),
		Map.entry("human", Items.PLAYER_HEAD), Map.entry("merling", Items.NAUTILUS_SHELL), Map.entry("phantom", Items.PHANTOM_MEMBRANE),
		Map.entry("shulk", Items.SHULKER_SHELL), Map.entry("golem", Items.IRON_BLOCK), Map.entry("fox", Items.SWEET_BERRIES),
		Map.entry("dwarf", Items.IRON_PICKAXE), Map.entry("frostborn", Items.SNOWBALL), Map.entry("wolf", Items.BONE));

	private static final Map<String, String> LABELS = Map.of("golem", "Golem", "fox", "Tilki", "dwarf", "Cüce", "frostborn", "Buz Ruhu", "wolf", "Kurt");

	public static String label(String id) {
		return LABELS.getOrDefault(id, Character.toUpperCase(id.charAt(0)) + id.substring(1));
	}

	public static List<String> allowed(Settings st) {
		List<String> a = new ArrayList<>(ALL);
		a.removeAll(st.bannedOrigins);
		if (a.isEmpty()) a.add("human");
		return a;
	}

	public static void set(MinecraftServer server, ServerPlayer p, String origin) {
		run(server, "origin set " + p.getGameProfile().getName() + " origins:origin " + fullId(origin));
	}

	public static void reset(MinecraftServer server, ServerPlayer p) {
		set(server, p, "human");
	}

	private static void run(MinecraftServer server, String cmd) {
		CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
		try {
			int r = server.getCommands().getDispatcher().execute(cmd, src);
			if (r == 0) Kapisma.LOG.warn("Origin komutu 0 dondu: {}", cmd);
		} catch (CommandSyntaxException e) {
			Kapisma.LOG.warn("Origin komutu basarisiz: {} -> {}", cmd, e.getMessage());
		}
	}

	/**
	 * Rastgele dagitim. Takim bazinda tekrarsizlik: yeterli irk varsa herkese farkli irk
	 * (uniqueOrigins), yoksa havuz bittiginde yeniden karistirilir.
	 */
	public static Map<UUID, String> assignRandom(MinecraftServer server, List<ServerPlayer> players, Settings st, Random rnd) {
		List<String> pool = allowed(st);
		List<String> bag = new ArrayList<>();
		Map<UUID, String> out = new HashMap<>();
		for (ServerPlayer p : players) {
			if (bag.isEmpty()) {
				bag.addAll(pool);
				Collections.shuffle(bag, rnd);
			}
			String o = st.uniqueOrigins ? bag.remove(bag.size() - 1) : pool.get(rnd.nextInt(pool.size()));
			set(server, p, o);
			out.put(p.getUUID(), o);
			Kapisma.LOG.info("Irk: {} -> {}", p.getGameProfile().getName(), o);
		}
		return out;
	}
}
