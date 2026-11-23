package tr.kapisma;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.kapisma.game.GameManager;
import tr.kapisma.lobby.Lobby;

public class Kapisma implements ModInitializer {
	public static final String ID = "kapisma";
	public static final Logger LOG = LoggerFactory.getLogger(ID);

	/** Sunucu acikken tek yonetici. */
	public static GameManager GM;

	@Override
	public void onInitialize() {
		// Istemci HUD modu icin paket tipi (modu olmayan istemciye gonderilmez)
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(tr.kapisma.net.HudPayload.TYPE, tr.kapisma.net.HudPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(tr.kapisma.net.MapPayload.TYPE, tr.kapisma.net.MapPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(tr.kapisma.net.TargetPayload.TYPE, tr.kapisma.net.TargetPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(tr.kapisma.net.DisguisePayload.TYPE, tr.kapisma.net.DisguisePayload.CODEC);
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> Cmds.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			GM = new GameManager(server);
			if (GM.settings.xraySalt == 0) {
				GM.settings.xraySalt = new java.security.SecureRandom().nextLong() | 1L;
				GM.settings.save();
			}
			tr.kapisma.xray.Xray.salt = GM.settings.xraySalt;
			if (tr.kapisma.boot.Boot.PROFILE == tr.kapisma.boot.Boot.Profile.LITE) {
				// Tablet / eski cihaz: hile korumasinin aciga cikarma maliyeti kucultulur (koruma ayni kalir)
				tr.kapisma.xray.Xray.revealRadius = 8;
				tr.kapisma.xray.Xray.bfsCap = 1200;
				tr.kapisma.guard.AntiCheat.interval = 8;
			}
			LOG.info("Donanim profili: {} ({})", tr.kapisma.boot.Boot.PROFILE, tr.kapisma.boot.Boot.PROFILE_WHY);
			Lobby.ensureBuilt(server);
			Lobby.applyNormalRules(server);
			LOG.info("Kapisma hazir: lobi kuruldu, seed havuzu {} seed", GM.seeds.total());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (GM != null) GM.stop("Sunucu kapanıyor");
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (GM != null) GM.tick();
			Lobby.tick(server);
			tr.kapisma.xray.Xray.tick(server);
			tr.kapisma.guard.AntiCheat.tick(server);
			tr.kapisma.voice.VoiceBridge.tick(server);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			tr.kapisma.voice.VoiceBridge.onJoin(handler.getPlayer(), server);
			if (GM != null) GM.onJoin(handler.getPlayer());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			tr.kapisma.xray.Xray.onDisconnect(handler.getPlayer());
			tr.kapisma.voice.VoiceBridge.onDisconnect(handler.getPlayer());
			if (GM != null) GM.onDisconnect(handler.getPlayer());
		});

		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(tr.kapisma.guard.AntiCheat::onAttack);

		UseItemCallback.EVENT.register((player, world, hand) -> {
			var stack = player.getItemInHand(hand);
			if (GM != null && player instanceof ServerPlayer sp && !world.isClientSide) {
				String pk = tr.kapisma.lobby.Parkour.itemCode(stack);
				if (pk != null) {
					tr.kapisma.lobby.Parkour.action(sp, pk);
					return InteractionResultHolder.success(stack);
				}
				if (Lobby.isMenuItem(stack)) {
					GM.menus.openMain(sp);
					return InteractionResultHolder.success(stack);
				}
				if (GM.current != null) GM.current.onUseItem(sp, stack);
			}
			return InteractionResultHolder.pass(stack);
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (GM == null || !(entity instanceof ServerPlayer p) || GM.current == null) return true;
			GM.current.recordDeath(p, source);
			return GM.current.allowDeath(p, source);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (GM == null) return true;
			if (entity instanceof ServerPlayer p && inLobby(p)) return false;
			return GM.current == null || GM.current.allowDamage(entity, source, amount);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (GM != null && GM.current != null) GM.current.onEntityDeath(entity, source);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (GM != null && GM.current != null) GM.current.onEntityLoad(entity, level);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
			if (GM != null && GM.current != null) GM.current.onRespawn(newP);
		});

		// Oyun icinde komutsuz yazi sadece takima gider; elenenler sadece seyircilere yazar (bkz. ChatService).
		net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (GM == null) return true;
			return tr.kapisma.chat.ChatService.handlePlain(sender, message.signedContent());
		});

		LOG.info("Kapisma yuklendi");
	}

	private static boolean inLobby(ServerPlayer p) {
		if (GM == null) return false;
		boolean inGame = GM.current != null && GM.current.contains(p.getUUID());
		return !inGame && Lobby.inLobby(p);
	}
}
