package tr.kapismahud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Sag ustte arazisiz mini harita: sinir karesi (baslangic, simdiki, son), kendin ve takim arkadaslari. Kuzey yukari. */
final class MiniMap {
	private MiniMap() {}

	private static final int SIZE = 84;

	private static int argb(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	private static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1 + 1, y0 + 1, color);
		g.fill(x0, y1, x1 + 1, y1 + 1, color);
		g.fill(x0, y0, x0 + 1, y1 + 1, color);
		g.fill(x1, y0, x1 + 1, y1 + 1, color);
	}

	static void render(GuiGraphics g, Minecraft mc, MapPayload m) {
		int w = mc.getWindow().getGuiScaledWidth();
		int x0 = w - SIZE - 6, y0 = 6;
		g.fill(x0 - 2, y0 - 2, x0 + SIZE + 2, y0 + SIZE + 12, 0xA0101010);
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
			g.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF000000);
			g.fill(mx, my, mx + 1, my + 1, argb(t.rgb, 0xFF));
			g.fill(mx - 1, my - 1, mx + 2, my + 2, argb(t.rgb, 0xFF));
		}
		// kendin: beyaz nokta + bakis yonunde sari cizgi
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
		String info = String.format("X %d  Z %d  ±%d", m.selfX, m.selfZ, Math.round(m.curRadius));
		g.drawString(mc.font, info, x0 + (SIZE - mc.font.width(info)) / 2, y0 + SIZE + 2, 0xFFE0E0E0, true);
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
