package tr.kapismahud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Sag ustte arazisiz mini harita: sinir karesi (baslangic, simdiki, son), kendin, takim arkadaslari ve pusula hedefi. Kuzey yukari.
 * Altinda isteyince renk aciklamasi. Cizim koordinatlari olcekli uzaydadir: screenW = ekran genisligi / olcek.
 */
final class MiniMap {
	private MiniMap() {}

	private static final int SIZE = 84;
	private static final int LEGEND_W = 104;
	private static final int GOLD = 0xFFFFD700;

	private static int argb(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	private static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1 + 1, y0 + 1, color);
		g.fill(x0, y1, x1 + 1, y1 + 1, color);
		g.fill(x0, y0, x0 + 1, y1 + 1, color);
		g.fill(x1, y0, x1 + 1, y1 + 1, color);
	}

	private static void legendRow(GuiGraphics g, Minecraft mc, int x, int y, int color, boolean outline, String text) {
		if (outline) rect(g, x, y + 1, x + 6, y + 7, color);
		else g.fill(x + 1, y + 2, x + 6, y + 7, color);
		g.drawString(mc.font, text, x + 10, y, 0xFFC8C8C8, false);
	}

	static void render(GuiGraphics g, Minecraft mc, MapPayload m, int screenW) {
		boolean legend = HudConfig.legend;
		int pw = legend ? LEGEND_W : SIZE + 4;
		int px0 = screenW - pw - 4;
		int x0 = px0 + (pw - SIZE) / 2, y0 = 6;
		TargetPayload tg = KapismaHudClient.target;
		boolean hasTarget = tg != null && tg.has;
		int rows = 0;
		if (legend) rows = 2 + (m.endRadius > 0 ? 1 : 0) + (m.mates.isEmpty() ? 0 : 1) + (hasTarget ? 1 : 0);
		int bottom = y0 + SIZE + 12 + (hasTarget ? 10 : 0) + (legend ? rows * 9 + 3 : 0);
		g.fill(px0, y0 - 2, px0 + pw, bottom, 0xA0101010);
		g.fill(x0, y0, x0 + SIZE, y0 + SIZE, 0xC0202820);

		float half = Math.max(m.startRadius, 50) * 1.04f;
		float scale = SIZE / (2f * half);
		int cx = x0 + SIZE / 2, cy = y0 + SIZE / 2;

		// baslangic siniri (soluk), son sinir (yesil), simdiki sinir (kirmizi)
		rect(g, px(cx, m.centerX, m.centerX, m.startRadius, scale, -1), py(cy, m.centerZ, m.centerZ, m.startRadius, scale, -1),
			px(cx, m.centerX, m.centerX, m.startRadius, scale, 1), py(cy, m.centerZ, m.centerZ, m.startRadius, scale, 1), 0x60FFFFFF);
		if (m.endRadius > 0) {
			rect(g, px(cx, m.centerX, m.centerX, m.endRadius, scale, -1), py(cy, m.centerZ, m.centerZ, m.endRadius, scale, -1),
				px(cx, m.centerX, m.centerX, m.endRadius, scale, 1), py(cy, m.centerZ, m.centerZ, m.endRadius, scale, 1), 0xFF50E070);
		}
		int bx0 = px(cx, m.centerX, m.centerX, m.curRadius, scale, -1), by0 = py(cy, m.centerZ, m.centerZ, m.curRadius, scale, -1);
		int bx1 = px(cx, m.centerX, m.centerX, m.curRadius, scale, 1), by1 = py(cy, m.centerZ, m.centerZ, m.curRadius, scale, 1);
		// guvenli alan hafif yesil dolgu
		g.fill(Math.max(bx0, x0), Math.max(by0, y0), Math.min(bx1, x0 + SIZE), Math.min(by1, y0 + SIZE), 0x2050E070);
		rect(g, bx0, by0, bx1, by1, 0xFFFF4040);

		// takim arkadaslari
		for (MapPayload.Mate t : m.mates) {
			int mx = clamp(cx + Math.round((float) (t.x - m.centerX) * scale), x0 + 1, x0 + SIZE - 2);
			int my = clamp(cy + Math.round((float) (t.z - m.centerZ) * scale), y0 + 1, y0 + SIZE - 2);
			g.fill(mx - 1, my - 1, mx + 2, my + 2, argb(t.rgb, 0xFF));
			g.fill(mx, my, mx + 1, my + 1, 0xFF000000);
		}
		// pusula hedefi: altin baklava (harita disinda ise kenara yapisir)
		if (hasTarget) {
			int tx = clamp(cx + Math.round((float) (tg.x - m.centerX) * scale), x0 + 2, x0 + SIZE - 3);
			int ty = clamp(cy + Math.round((float) (tg.z - m.centerZ) * scale), y0 + 2, y0 + SIZE - 3);
			g.fill(tx - 2, ty - 2, tx + 3, ty + 3, 0xFF000000);
			g.fill(tx - 1, ty - 1, tx + 2, ty + 2, GOLD);
		}
		// kendin: beyaz arti + bakis yonunde sari cizgi
		int sx = clamp(cx + Math.round((float) (m.selfX - m.centerX) * scale), x0 + 2, x0 + SIZE - 3);
		int sy = clamp(cy + Math.round((float) (m.selfZ - m.centerZ) * scale), y0 + 2, y0 + SIZE - 3);
		double yaw = Math.toRadians(m.selfYaw);
		for (int i = 2; i <= 5; i++) {
			int lx = sx + (int) Math.round(-Math.sin(yaw) * i), ly = sy + (int) Math.round(Math.cos(yaw) * i);
			if (lx > x0 && lx < x0 + SIZE && ly > y0 && ly < y0 + SIZE) g.fill(lx, ly, lx + 1, ly + 1, 0xFFFFE040);
		}
		g.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF000000);
		g.fill(sx, sy - 1, sx + 1, sy + 2, 0xFFFFFFFF);
		g.fill(sx - 1, sy, sx + 2, sy + 1, 0xFFFFFFFF);

		g.drawString(mc.font, "K", x0 + SIZE / 2 - 2, y0 + 1, 0xFFB0B0B0, false);
		int ty = y0 + SIZE + 2;
		String info = String.format("X %d  Z %d  ±%d", m.selfX, m.selfZ, Math.round(m.curRadius));
		g.drawString(mc.font, info, px0 + (pw - mc.font.width(info)) / 2, ty, 0xFFE0E0E0, true);
		ty += 10;
		if (hasTarget) {
			String t = "◆ " + tg.label;
			if (mc.font.width(t) > pw - 6) t = mc.font.plainSubstrByWidth(t, pw - 6);
			g.drawString(mc.font, t, px0 + (pw - mc.font.width(t)) / 2, ty, GOLD, true);
			ty += 10;
		}
		if (legend) {
			int lx = px0 + 5;
			ty += 1;
			legendRow(g, mc, lx, ty, 0xFFFF4040, true, "sınır (daralır)");
			ty += 9;
			if (m.endRadius > 0) { legendRow(g, mc, lx, ty, 0xFF50E070, true, "son sınır"); ty += 9; }
			legendRow(g, mc, lx, ty, 0xFFFFFFFF, false, "sen (sarı çizgi: yön)");
			ty += 9;
			if (!m.mates.isEmpty()) { legendRow(g, mc, lx, ty, m.mates.get(0).rgb | 0xFF000000, false, "takım arkadaşı"); ty += 9; }
			if (hasTarget) legendRow(g, mc, lx, ty, GOLD, false, "pusula hedefi");
		}
	}

	private static int px(int cx, double ox, double center, float radius, float scale, int sign) {
		return cx + Math.round((float) (center - ox + sign * radius) * scale);
	}

	private static int py(int cy, double oz, double center, float radius, float scale, int sign) {
		return cy + Math.round((float) (center - oz + sign * radius) * scale);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
