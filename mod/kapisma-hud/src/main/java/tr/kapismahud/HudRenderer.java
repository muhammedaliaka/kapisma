package tr.kapismahud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Sol ustte 5x5 Tombala karti + takim durumu. Hucre rengi = alan takimin rengi. */
final class HudRenderer {
	private HudRenderer() {}

	private static final int CELL = 18;
	private static final int PAD = 4;

	private static int argb(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	static void render(GuiGraphics g, Minecraft mc, HudPayload s) {
		Font font = mc.font;
		int x0 = 4, y0 = 4;
		int gridW = CELL * 5;
		int rivalRows = Math.min(s.rivals.size(), 6);
		int width = Math.max(gridW + PAD * 2, 118);
		int height = PAD + 11 + (s.card.size() == 25 ? gridW + 3 : 0) + 11 + rivalRows * 10 + PAD;

		// panel
		g.fill(x0, y0, x0 + width, y0 + height, 0xA0101010);
		g.fill(x0, y0, x0 + width, y0 + 1, argb(s.teamRgb, 0xFF));          // ust cizgi: takim rengi
		int y = y0 + PAD;

		// baslik: mod + takim adi takim renginde
		g.drawString(font, s.title, x0 + PAD, y, 0xFFFFFFFF, true);
		int tw = font.width(s.title) + 6;
		g.drawString(font, s.teamName, x0 + PAD + tw, y, argb(s.teamRgb, 0xFF), true);
		y += 11;

		// kart
		if (s.card.size() == 25) {
			for (int i = 0; i < 25; i++) {
				HudPayload.Cell c = s.card.get(i);
				int cx = x0 + PAD + (i % 5) * CELL;
				int cy = y + (i / 5) * CELL;
				int bg;
				if (c.ownerRgb == -1) bg = 0x90303030;                                   // alinmadi
				else if (c.mine) bg = argb(c.ownerRgb, 0xB0);                           // benim takimim
				else bg = argb(c.ownerRgb, 0x80);                                       // rakip takim
				g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, bg);
				if (c.target && c.ownerRgb == -1) {                                      // hedef: sari cerceve
					g.fill(cx, cy, cx + CELL - 1, cy + 1, 0xFFFFD700);
					g.fill(cx, cy + CELL - 2, cx + CELL - 1, cy + CELL - 1, 0xFFFFD700);
					g.fill(cx, cy, cx + 1, cy + CELL - 1, 0xFFFFD700);
					g.fill(cx + CELL - 2, cy, cx + CELL - 1, cy + CELL - 1, 0xFFFFD700);
				}
				ResourceLocation rl = ResourceLocation.tryParse(c.itemId);
				Item it = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
				if (it != null) g.renderItem(new ItemStack(it), cx + 1, cy + 1);
				if (c.ownerRgb != -1 && !c.mine) g.drawString(font, "X", cx + 6, cy + 5, 0xFFFF3030, true);   // rakip aldi
				if (c.ownerRgb != -1 && c.mine) g.drawString(font, "✓", cx + 11, cy + 8, 0xFFFFFFFF, true); // benim
			}
			y += gridW + 3;
		}

		// ozet satiri
		String t = String.format("%d/%d çizgi  %d eşya  %s%02d:%02d", s.lines, s.need, s.items, s.countdown ? "-" : "", Math.abs(s.timeSec) / 60, Math.abs(s.timeSec) % 60);
		g.drawString(font, t, x0 + PAD, y, 0xFFE0E0E0, true);
		y += 11;

		// rakipler (herkesin durumu)
		for (int i = 0; i < rivalRows; i++) {
			HudPayload.Rival r = s.rivals.get(i);
			g.fill(x0 + PAD, y + 1, x0 + PAD + 5, y + 7, argb(r.rgb, 0xFF));
			g.drawString(font, r.name + "  " + r.lines + "ç " + r.items + "e", x0 + PAD + 8, y, 0xFFD0D0D0, true);
			y += 10;
		}
	}
}
