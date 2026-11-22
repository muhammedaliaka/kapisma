package tr.kapismahud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** /hud: istemci tarafi komut; HUD kart/harita boyutu ve gorunurlugu. */
final class HudCommands {
	private HudCommands() {}

	private static int say(FabricClientCommandSource s, String text) {
		s.sendFeedback(Component.literal(text).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	private static String onoff(boolean b) { return b ? "açık" : "kapalı"; }

	private static String sc(float v) { return v > 0 ? String.format("%.2f", v) : "otomatik"; }

	private static int status(FabricClientCommandSource s) {
		say(s, "Kapışma HUD: kart " + onoff(HudConfig.card) + " (boyut " + sc(HudConfig.cardScale) + "), harita " + onoff(HudConfig.map)
			+ " (boyut " + sc(HudConfig.mapScale) + "), harita açıklaması " + onoff(HudConfig.legend));
		s.sendFeedback(Component.literal("/hud boyut <0.4-1.5|otomatik>  hem kart hem harita\n"
			+ "/hud kart <boyut|ac|kapat>   sol üstteki Tombala kartı\n"
			+ "/hud harita <boyut|ac|kapat>   sağ üstteki mini harita\n"
			+ "/hud aciklama <ac|kapat>   haritanın altındaki renk açıklaması\n"
			+ "/hud sifirla   hepsini varsayılana (otomatik boyut) döndür.  H tuşu: hepsini gizle/göster").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static float clampScale(float v) { return Math.max(0.4f, Math.min(1.5f, v)); }

	static void register(CommandDispatcher<FabricClientCommandSource> d) {
		var scaleArg = ClientCommandManager.argument("boyut", FloatArgumentType.floatArg(0.4f, 1.5f));
		d.register(ClientCommandManager.literal("hud")
			.executes(c -> status(c.getSource()))
			.then(ClientCommandManager.literal("boyut")
				.then(ClientCommandManager.literal("otomatik").executes(c -> {
					HudConfig.cardScale = 0; HudConfig.mapScale = 0; HudConfig.save();
					return say(c.getSource(), "HUD boyutu otomatik (ekran boyutuna göre).");
				}))
				.then(scaleArg.executes(c -> {
					float v = clampScale(FloatArgumentType.getFloat(c, "boyut"));
					HudConfig.cardScale = v; HudConfig.mapScale = v; HudConfig.save();
					return say(c.getSource(), "HUD boyutu: " + sc(v));
				})))
			.then(ClientCommandManager.literal("kart")
				.then(ClientCommandManager.literal("ac").executes(c -> { HudConfig.card = true; HudConfig.save(); return say(c.getSource(), "Kart açık."); }))
				.then(ClientCommandManager.literal("kapat").executes(c -> { HudConfig.card = false; HudConfig.save(); return say(c.getSource(), "Kart kapalı."); }))
				.then(ClientCommandManager.literal("otomatik").executes(c -> { HudConfig.cardScale = 0; HudConfig.save(); return say(c.getSource(), "Kart boyutu otomatik."); }))
				.then(ClientCommandManager.argument("boyut", FloatArgumentType.floatArg(0.4f, 1.5f)).executes(c -> {
					HudConfig.cardScale = clampScale(FloatArgumentType.getFloat(c, "boyut")); HudConfig.save();
					return say(c.getSource(), "Kart boyutu: " + sc(HudConfig.cardScale));
				})))
			.then(ClientCommandManager.literal("harita")
				.then(ClientCommandManager.literal("ac").executes(c -> { HudConfig.map = true; HudConfig.save(); return say(c.getSource(), "Harita açık."); }))
				.then(ClientCommandManager.literal("kapat").executes(c -> { HudConfig.map = false; HudConfig.save(); return say(c.getSource(), "Harita kapalı."); }))
				.then(ClientCommandManager.literal("otomatik").executes(c -> { HudConfig.mapScale = 0; HudConfig.save(); return say(c.getSource(), "Harita boyutu otomatik."); }))
				.then(ClientCommandManager.argument("boyut", FloatArgumentType.floatArg(0.4f, 1.5f)).executes(c -> {
					HudConfig.mapScale = clampScale(FloatArgumentType.getFloat(c, "boyut")); HudConfig.save();
					return say(c.getSource(), "Harita boyutu: " + sc(HudConfig.mapScale));
				})))
			.then(ClientCommandManager.literal("aciklama")
				.then(ClientCommandManager.literal("ac").executes(c -> { HudConfig.legend = true; HudConfig.save(); return say(c.getSource(), "Harita açıklaması açık."); }))
				.then(ClientCommandManager.literal("kapat").executes(c -> { HudConfig.legend = false; HudConfig.save(); return say(c.getSource(), "Harita açıklaması kapalı."); })))
			.then(ClientCommandManager.literal("sifirla").executes(c -> {
				HudConfig.card = true; HudConfig.map = true; HudConfig.legend = true; HudConfig.cardScale = 0; HudConfig.mapScale = 0; HudConfig.save();
				return say(c.getSource(), "HUD ayarları sıfırlandı.");
			})));
	}
}
