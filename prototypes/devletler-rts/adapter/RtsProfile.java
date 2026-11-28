package kapisma.rts;

import net.minecraft.class_3222;

/** The five state profiles selected during natural-world founding. */
enum RtsProfile {
    FARMING("Tarım", 18, 4, 16, "Tarla 18 sn · Cevher işi 4 sn"),
    MINING("Madencilik", 30, 2, 16, "Cevher işi 2 sn · Tarla 30 sn"),
    TRADE("Ticaret", 24, 3, 16, "Kervan ödemesi +1 altın · Karakol bedeli +1 kırık taş"),
    DEFENSE("Savunma", 24, 3, 16, "Karakol 46 can · Karakol sınırı 2"),
    EXPLORATION("Keşif", 24, 3, 24, "İşçi menzili 24 blok · Karakol 30 can");

    private static final RtsProfile[] OPTIONS = values();
    final String label;
    final int fieldSeconds;
    final int oreSeconds;
    final int workRadius;
    final String description;

    RtsProfile(String label, int fieldSeconds, int oreSeconds, int workRadius, String description) {
        this.label = label;
        this.fieldSeconds = fieldSeconds;
        this.oreSeconds = oreSeconds;
        this.workRadius = workRadius;
        this.description = description;
    }

    static RtsProfile of(class_3222 player) {
        int index = RtsContext.score("krts_profile", player) - 1;
        return index >= 0 && index < OPTIONS.length ? OPTIONS[index] : FARMING;
    }

    static String[] labels() {
        return java.util.Arrays.stream(OPTIONS).map(profile -> profile.label).toArray(String[]::new);
    }
}
