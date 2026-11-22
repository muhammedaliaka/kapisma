package tr.kapismahud;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * HUD ayarlari (config/kapisma-hud.properties). Olcek 0 = otomatik: kucuk ekranda kart ve harita kendiliginden kuculur.
 * Oyun icinde /hud komutuyla degistirilir.
 */
final class HudConfig {
	private HudConfig() {}

	static boolean card = true, map = true, legend = true;
	static float cardScale = 0f, mapScale = 0f;     // 0 = otomatik

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kapisma-hud.properties");
	}

	static void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			Properties p = new Properties();
			p.load(r);
			card = !"false".equals(p.getProperty("card"));
			map = !"false".equals(p.getProperty("map"));
			legend = !"false".equals(p.getProperty("legend"));
			cardScale = parse(p.getProperty("cardScale"));
			mapScale = parse(p.getProperty("mapScale"));
		} catch (IOException | RuntimeException e) {
			// bozuk dosya: varsayilanlar
		}
	}

	static void save() {
		Properties p = new Properties();
		p.setProperty("card", String.valueOf(card));
		p.setProperty("map", String.valueOf(map));
		p.setProperty("legend", String.valueOf(legend));
		p.setProperty("cardScale", String.valueOf(cardScale));
		p.setProperty("mapScale", String.valueOf(mapScale));
		try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
			p.store(w, "Kapisma HUD ayarlari. olcek 0 = otomatik. Oyun icinde: /hud");
		} catch (IOException e) {
			// yazilamazsa bu oturumluk gecerli kalir
		}
	}

	private static float parse(String s) {
		try {
			float v = Float.parseFloat(s);
			return v == 0f ? 0f : Math.max(0.3f, Math.min(2f, v));
		} catch (RuntimeException e) {
			return 0f;
		}
	}

	/** Ekran yuksekligine gore otomatik olcek: kucuk ekranda kucuk. */
	static float auto(Minecraft mc) {
		float h = mc.getWindow().getGuiScaledHeight();
		return Math.max(0.5f, Math.min(0.9f, h / 600f));
	}

	static float cardScale(Minecraft mc) {
		return cardScale > 0 ? cardScale : auto(mc);
	}

	static float mapScale(Minecraft mc) {
		return mapScale > 0 ? mapScale : auto(mc);
	}
}
