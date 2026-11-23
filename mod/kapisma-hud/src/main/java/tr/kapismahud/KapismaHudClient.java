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
import org.lwjgl.glfw.GLFW;

/** Kapisma HUD: sunucudan gelen veriyi sol ustte cizer. H tusu goster/gizle. */
public final class KapismaHudClient implements ClientModInitializer {
	public static volatile HudPayload state;
	public static volatile MapPayload map;
	public static volatile TargetPayload target;
	public static volatile boolean disguise;
	private static boolean visible = true;
	private static KeyMapping toggleKey;

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
		ClientPlayNetworking.registerGlobalReceiver(DisguisePayload.TYPE, (payload, context) -> disguise = payload.on);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { state = null; map = null; target = null; disguise = false; });

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.kapisma_hud.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.kapisma_hud"));
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
}
