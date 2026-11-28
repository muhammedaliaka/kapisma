package tr.kapisma.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import tr.kapisma.Kapisma;
import tr.kapisma.game.Game;

/**
 * Simple Voice Chat eklentisi (sadece o mod yukluyse calisir): oyun icinde elenenler (seyirciler) sadece
 * birbirleriyle konusur, hayatta olanlar onlari duymaz. Hayattakiler arasinda mesafe tabanli (yakin) ses varsayilan kalir.
 */
public final class KapismaVoice implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return "kapisma";
	}

	private static volatile VoicechatServerApi api;
	private static volatile VoicePolicy policy = VoicePolicy.LOBBY;

	// Only the Minecraft server thread reads mutable game/player state.
	static void refresh(net.minecraft.server.MinecraftServer server) {
		var gm = Kapisma.GM;
		var g = gm == null ? null : gm.current;
		if (g == null || g.isClosed()) { policy = VoicePolicy.LOBBY; return; }
		var spectators = new java.util.HashSet<>(g.eliminated);
		for (var p : server.getPlayerList().getPlayers())
			if (g.contains(p.getUUID()) && p.isSpectator()) spectators.add(p.getUUID());
		boolean killer = "KATIL".equals(g.id());
		policy = new VoicePolicy(new java.util.HashSet<>(g.players), spectators, killer,
			!killer || gm.settings.flag("KATIL", "voice"), killer && gm.settings.flag("KATIL", "deepVoice"));
	}
	/** takim kimligi -> ses grubu (acik tur: grup disindakiler de yakindan duyar). */
	private static final Map<String, Group> groups = new ConcurrentHashMap<>();

	@Override
	public void registerEvents(EventRegistration reg) {
		reg.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
		reg.registerEvent(MicrophonePacketEvent.class, this::onMic);
		reg.registerEvent(EntitySoundPacketEvent.class, this::isolateSpectator);
		reg.registerEvent(LocationalSoundPacketEvent.class, this::isolateSpectator);
		reg.registerEvent(StaticSoundPacketEvent.class, this::isolateSpectator);
	}

	private void isolateSpectator(SoundPacketEvent<?> event) {
		var sender = event.getSenderConnection();
		var receiver = event.getReceiverConnection();
		VoicePolicy snapshot = policy;
		if (sender != null && receiver != null && sender.getPlayer() != null && receiver.getPlayer() != null
			&& !snapshot.permits(sender.getPlayer().getUuid(), receiver.getPlayer().getUuid())) event.cancel();
	}

	/** Oyuncunun sesli sohbet istemcisi bagli mi? */
	static boolean hasVoice(UUID id) {
		var a = api;
		if (a == null) return false;
		try {
			VoicechatConnection c = a.getConnectionOf(id);
			return c != null && c.isInstalled();
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * Takimlari (>=2 uyeli) acik ses grubuna koyar: takim arkadaslari mesafeden bagimsiz duyar, ayrica yakindaki herkesle
	 * normal (mesafe tabanli) konusma da surer. Tekrar cagrilabilir (eksik uyeleri tamamlar).
	 */
	static void applyGroups(Game g) {
		var a = api;
		if (a == null) return;
		for (var team : g.teams) {
			if (team.members.size() < 2) continue;
			Group grp = groups.computeIfAbsent(team.id, k -> a.groupBuilder().setName(team.name).setType(Group.Type.OPEN).setPersistent(false).build());
			for (UUID m : team.members) {
				VoicechatConnection c = a.getConnectionOf(m);
				if (c == null || !c.isInstalled()) continue;
				if (policy.spectator(m)) {
					Group current = c.getGroup();
					if (current != null && groups.values().stream().anyMatch(owned -> owned.getId().equals(current.getId()))) c.setGroup(null);
					continue;
				}
				if (c.getGroup() == null || !grp.getId().equals(c.getGroup().getId())) c.setGroup(grp);
			}
		}
	}

	static void clearGroups(Game g) {
		var a = api;
		if (a == null) return;
		if (g != null) {
			for (var team : g.teams) for (UUID m : team.members) {
				try {
					VoicechatConnection c = a.getConnectionOf(m);
					Group cur = c == null ? null : c.getGroup();
					if (cur != null && groups.values().stream().anyMatch(owned -> owned.getId().equals(cur.getId()))) c.setGroup(null);
				} catch (Throwable ignored) {}
			}
		}
		for (Group grp : groups.values()) try { a.removeGroup(grp.getId()); } catch (Throwable ignored) {}
		groups.clear();
	}

	/** Konusan basina Opus cozucu/kodlayici + perde dusurucu (akis durumu tutar). */
	private static final class Fx {
		final OpusDecoder dec;
		final OpusEncoder enc;
		final PitchShift shift = new PitchShift(0.62f);

		Fx(VoicechatServerApi api) {
			dec = api.createDecoder();
			enc = api.createEncoder();
		}

		void reset() {
			dec.resetState();
			enc.resetState();
			shift.reset();
		}
	}

	private final Map<UUID, Fx> fx = new ConcurrentHashMap<>();

	private void deepen(MicrophonePacketEvent e, UUID id) {
		var packet = e.getPacket();
		byte[] data = packet.getOpusEncodedData();
		Fx f = fx.computeIfAbsent(id, k -> new Fx(e.getVoicechat()));
		if (data == null || data.length == 0) { f.reset(); return; }       // konusma bitti
		try {
			short[] pcm = f.dec.decode(data);
			packet.setOpusEncodedData(f.enc.encode(f.shift.process(pcm)));
		} catch (Exception ex) {
			f.reset();
		}
	}

	private void onMic(MicrophonePacketEvent e) {
		VoicechatConnection sender = e.getSenderConnection();
		if (sender == null || sender.getPlayer() == null) return;
		VoicePolicy snapshot = policy;
		UUID id = sender.getPlayer().getUuid();
		if (!snapshot.micEnabled(id)) { e.cancel(); return; }
		if (snapshot.deepen(id)) deepen(e, id);
		if (!snapshot.spectator(id)) return;
		// seyirci konusuyor: varsayilan dagitimi iptal et, sadece diger seyircilere gonder
		e.cancel();
		VoicechatServerApi api = e.getVoicechat();
		var packet = e.getPacket().toStaticSoundPacket();
		for (UUID other : snapshot.spectators()) {
			if (other.equals(id)) continue;
			VoicechatConnection c = api.getConnectionOf(other);
			if (c != null && c.isConnected()) api.sendStaticSoundPacketTo(c, packet);
		}
	}
}
