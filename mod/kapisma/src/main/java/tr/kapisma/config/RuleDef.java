package tr.kapisma.config;

import net.minecraft.world.item.Item;

/** Menude otomatik gosterilen bir ayar (UHC kural katalogu vb.). */
public record RuleDef(String key, String label, String desc, boolean bool,
                      double def, double min, double max, double step, Item icon) {
	public static RuleDef flag(String key, String label, String desc, boolean def, Item icon) {
		return new RuleDef(key, label, desc, true, def ? 1 : 0, 0, 1, 1, icon);
	}

	public static RuleDef num(String key, String label, String desc, double def, double min, double max, double step, Item icon) {
		return new RuleDef(key, label, desc, false, def, min, max, step, icon);
	}
}
