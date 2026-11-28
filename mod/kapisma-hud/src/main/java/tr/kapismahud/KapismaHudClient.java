package tr.kapismahud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.component.DataComponents;
import org.lwjgl.glfw.GLFW;

/** Kapisma HUD: sunucudan gelen veriyi sol ustte cizer. H tusu goster/gizle. */
public final class KapismaHudClient implements ClientModInitializer {
	public static volatile HudPayload state;
	public static volatile MapPayload map;
	public static volatile TargetPayload target;
	public static volatile boolean disguise;
	public static final PlayerSkin DISGUISE_SKIN = DefaultPlayerSkin.get(new java.util.UUID(0L, 0L));
	private static boolean visible = true;
	private static KeyMapping toggleKey;
	private static KeyMapping killerKey;
	private static boolean usePressed;

	@Override
	public void onInitializeClient() {
		HudConfig.load();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> HudCommands.register(dispatcher));
		PayloadTypeRegistry.playS2C().register(HudPayload.TYPE, HudPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) ->
			context.client().execute(() -> state = payload.active ? payload : null));
		PayloadTypeRegistry.playS2C().register(MapPayload.TYPE, MapPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(MapPayload.TYPE, (payload, context) ->
			context.client().execute(() -> map = payload.active ? payload : null));
		PayloadTypeRegistry.playS2C().register(TargetPayload.TYPE, TargetPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(TargetPayload.TYPE, (payload, context) ->
			context.client().execute(() -> target = payload.has ? payload : null));
		PayloadTypeRegistry.playS2C().register(DisguisePayload.TYPE, DisguisePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GameActionPayload.TYPE, GameActionPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(DisguisePayload.TYPE, (payload, context) -> disguise = payload.on);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { state = null; map = null; target = null; disguise = false; });

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.kapisma_hud.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.kapisma_hud"));
		killerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.kapisma_hud.killer", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.kapisma_hud"));
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			boolean pressed = mc.options.keyUse.isDown();
			while (killerKey.consumeClick()) {
				if (disguise && mc.screen == null && ClientPlayNetworking.canSend(GameActionPayload.TYPE)) {
					mc.options.keySocialInteractions.consumeClick();
					ClientPlayNetworking.send(new GameActionPayload(GameActionPayload.TOGGLE_KILLER));
				}
			}
			if (pressed && !usePressed && mc.screen == null && mc.player != null && mc.player.isSpectator()
				&& ClientPlayNetworking.canSend(GameActionPayload.TYPE)) {
				var data = mc.player.getMainHandItem().get(DataComponents.CUSTOM_DATA);
				if (data != null && data.copyTag().getBoolean("kapisma_spectator_compass")) {
					ClientPlayNetworking.send(new GameActionPayload(GameActionPayload.SPECTATE));
				}
			}
			usePressed = pressed;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (toggleKey.consumeClick()) visible = !visible;
		});

		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
			Minecraft mc = Minecraft.getInstance();
			HudPayload s = state;
			if (!visible || mc.options.hideGui || mc.player == null) return;
			if (s != null && HudConfig.card) {
				float cs = HudConfig.cardScale(mc);
				graphics.pose().pushPose();
				graphics.pose().scale(cs, cs, 1f);
				HudRenderer.render(graphics, mc, s);
				graphics.pose().popPose();
			}
			MapPayload m = map;
			if (m != null && HudConfig.map) {
				float ms = HudConfig.mapScale(mc);
				graphics.pose().pushPose();
				graphics.pose().scale(ms, ms, 1f);
				MiniMap.render(graphics, mc, m, Math.round(mc.getWindow().getGuiScaledWidth() / ms));
				graphics.pose().popPose();
			}
		});
	}

	public static boolean handleKillerKey(int key, int scancode, int action) {
		if (!disguise || killerKey == null || Minecraft.getInstance().screen != null
			|| !killerKey.matches(key, scancode) || !ClientPlayNetworking.canSend(GameActionPayload.TYPE)) return false;
		if (action == GLFW.GLFW_PRESS) ClientPlayNetworking.send(new GameActionPayload(GameActionPayload.TOGGLE_KILLER));
		return true;
	}
}
