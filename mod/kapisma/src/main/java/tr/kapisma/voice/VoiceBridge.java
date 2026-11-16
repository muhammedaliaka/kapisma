package tr.kapisma.voice;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import tr.kapisma.Kapisma;
import tr.kapisma.Msg;
import tr.kapisma.game.Game;

/**
 * Sesli sohbet koprusu (Simple Voice Chat yoksa hicbir sey yapmaz; API sinifi yuklenmez).
 * - Takim ses gruplari: oyun basinca takimlar acik gruba alinir, oyun bitince temizlenir (Katil Kim'de yok: kimlik gizli).
 * - Sesli sohbet baglanmayan oyuncuya kisa yardim mesaji.
 */
public final class VoiceBridge {
	private VoiceBridge() {}

	private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("voicechat");
	private static Game applied;
	private static final Map<UUID, Integer> joined = new HashMap<>();
	private static boolean failed;

	public static void onJoin(ServerPlayer p, MinecraftServer server) {
		joined.put(p.getUUID(), server.getTickCount());
	}

	public static void onDisconnect(ServerPlayer p) {
		joined.remove(p.getUUID());
	}

	public static void tick(MinecraftServer server) {
		if (!PRESENT || failed) return;
		int t = server.getTickCount();
		if (t % 20 != 7) return;
		try {
			Game g = Kapisma.GM == null ? null : Kapisma.GM.current;
			boolean want = g != null && g.phase == Game.Phase.RUNNING && !"KATIL".equals(g.id()) && Kapisma.GM.settings.flag(g.id(), "teamVoice");
			if (applied != null && (!want || applied != g)) {
				KapismaVoice.clearGroups(applied);
				applied = null;
			}
			if (want) {
				KapismaVoice.applyGroups(g);
				applied = g;
			}
			// baglanmayan oyunculara yardim (bir kez)
			Iterator<Map.Entry<UUID, Integer>> it = joined.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (t - e.getValue() < 600) continue;
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null && !KapismaVoice.hasVoice(e.getKey())) {
					Msg.tell(p, "Sesli sohbet bağlı değil. Simple Voice Chat modunu kur (istemci paketinde var), oyunda V tuşuyla mikrofonu seç; sunucuda UDP 24454 portu açık olmalı.");
				}
				it.remove();
			}
		} catch (Throwable ex) {
			failed = true;
			Kapisma.LOG.error("Ses koprusu hatasi, kapatildi", ex);
		}
	}
}
