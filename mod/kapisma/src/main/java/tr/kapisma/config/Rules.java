package tr.kapisma.config;

import java.util.List;
import net.minecraft.world.item.Items;

/** Oyun modlarinin kural kataloglari. Host menuden/komutla degistirir, Settings'te saklanir. */
public final class Rules {
	private Rules() {}

	public static final List<RuleDef> UHC = List.of(
		RuleDef.flag("naturalRegen", "Doğal can yenilenmesi", "Kapalıyken can sadece elma/golden head/iksirle gelir.", false, Items.GOLDEN_APPLE),
		RuleDef.flag("goldenHead", "Golden Head", "Ölen oyuncu kafa düşürür; yiyince hızlı can + emilim verir.", true, Items.PLAYER_HEAD),
		RuleDef.flag("cutClean", "Cut-clean", "Demir/altın/bakır ve etler pişmiş düşer.", true, Items.IRON_INGOT),
		RuleDef.num("pvpGraceMin", "PvP kapalı süre (dk)", "Başlangıçta PvP kapalı kalacak süre.", 10, 0, 30, 1, Items.SHIELD),
		RuleDef.num("borderStart", "Başlangıç sınırı (yarıçap)", "Dünya sınırı başlangıç yarıçapı (blok).", 1000, 200, 1000, 100, Items.MAP),
		RuleDef.num("scatterRadius", "Dağılım yarıçapı", "Takımlar başta merkezden bu yarıçap içinde rastgele (birbirinden uzak) dağıtılır; sınırdan büyük olamaz. 100 = 200x200 alan.", 500, 100, 700, 100, Items.COMPASS),
		RuleDef.flag("glowFinal", "Final parlama", "Sınır son yarıçapa ulaşınca herkes parlar: saklanma biter, final kapışması çıkar.", true, Items.GLOWSTONE_DUST),
		RuleDef.flag("minimap", "Mini harita", "HUD modu olanlara sağ üstte sınır/daralma/takım haritası; herkese başta harita verilir.", true, Items.FILLED_MAP),
		RuleDef.num("compass", "Pusula", "0 kapalı · 1 takım arkadaşı + sınır merkezi/başlangıç noktası · 2 ek olarak en yakın/en uzak rakip. Herkese başta pusula verilir; sağ tık hedefi değiştirir, elinde tutunca mesafe canlı yazar.", 2, 0, 2, 1, Items.COMPASS),
		RuleDef.flag("showHealth", "Can göstergesi", "Tab listesinde ve isim etiketinin altında oyuncuların kalpleri görünür (doğal yenilenme kapalıyken en çok işe yarar).", true, Items.APPLE),
		RuleDef.flag("sidebar", "Yan panel", "Sağ ortada süre, PvP, sınır, kalan takım/oyuncu ve en çok öldürenler; altta merkez yönü okunur.", true, Items.OAK_SIGN),
		RuleDef.num("borderEnd", "Son sınır (yarıçap)", "Daralma sonunda sınır yarıçapı.", 50, 10, 500, 10, Items.BARRIER),
		RuleDef.num("shrinkStartMin", "Daralma başlangıcı (dk)", "Sınır bu dakikadan sonra daralmaya başlar.", 20, 5, 90, 5, Items.CLOCK),
		RuleDef.num("shrinkEndMin", "Daralma bitişi (dk)", "Sınır bu dakikada son yarıçapa ulaşır.", 60, 10, 150, 5, Items.CLOCK),
		RuleDef.flag("friendlyFire", "Takım ateşi", "Açıkken takım arkadaşına da vurulabilir.", false, Items.IRON_SWORD),
		RuleDef.flag("pvp", "PvP", "Kapalıyken oyuncular birbirine hiç vuramaz (süre dolsa bile).", true, Items.DIAMOND_SWORD),
		RuleDef.flag("mobSpawn", "Yaratık doğuşu", "Kapalıyken düşman yaratık doğmaz.", true, Items.ZOMBIE_HEAD),
		RuleDef.num("earlyBuffSec", "Başlangıç koruması (sn)", "Oyun başında hız + direnç süresi.", 60, 0, 180, 30, Items.SUGAR),
		RuleDef.num("startFood", "Başlangıç yemeği (biftek)", "Her oyuncuya verilecek biftek sayısı.", 8, 0, 32, 8, Items.COOKED_BEEF),
		RuleDef.flag("lockDay", "Gündüz sabit", "Zaman hep gündüz kalır.", false, Items.SUNFLOWER),
		RuleDef.num("timeLimitMin", "Süre limiti (dk, 0=yok)", "Süre dolunca en çok yaşayan/sağlıklı takım kazanır.", 0, 0, 180, 10, Items.REPEATER),
		RuleDef.flag("antiCheat", "Hile koruması: oyuncular", "Duvarın/zeminin arkasındaki oyuncular (ESP, wallhack, radar) istemciye hiç gönderilmez; menzil aşımı, duvar arkasından vurma ve aşırı tıklama engellenir; gerçek seed gizlenir. Oyun başlayınca devreye girer.", true, Items.TINTED_GLASS),
		RuleDef.num("antiXray", "Hile koruması", "0 kapalı · 1 gömülü cevherler gizlenir, yerine sahte (halüsinasyon) cevher gösterilir · 2 ek olarak yeraltı boşlukları ve yapılar düz taş görünür; yaklaşınca/kırınca gerçeği açılır.", 2, 0, 2, 1, Items.ENDER_EYE),
		RuleDef.flag("teamVoice", "Takım ses kanalı", "Takım arkadaşların (Simple Voice Chat) mesafeden bağımsız seni duyar; yakındaki herkesle normal konuşma da sürer.", true, Items.NOTE_BLOCK)
	);

	public static final List<RuleDef> BINGO = List.of(
		RuleDef.num("lines", "Tombala çizgisi", "Bu kadar satır/sütun/çapraz tamamlayan takım kazanır (lockout).", 3, 1, 5, 1, Items.NETHER_STAR),
		RuleDef.num("goal", "Eşya hedefi (0=kapalı)", "Çizgiye ek: bu kadar eşya toplayan da kazanır.", 0, 0, 25, 1, Items.CHEST),
		RuleDef.num("timeLimitMin", "Süre limiti (dk, 0=yok)", "Süre dolunca en çok eşyası olan kazanır.", 60, 0, 180, 10, Items.CLOCK),
		RuleDef.flag("sidebar", "Yan panel", "Sağ ortadaki skor paneli. Sol üst HUD modu olmayanlar için yedek; modu olanlar kapatabilir.", true, Items.OAK_SIGN),
		RuleDef.flag("hints", "Eşya ipuçları", "Kâğıtta eksik eşyaların tarifi / nasıl bulunacağı ve çizgi önerileri gösterilir.", true, Items.KNOWLEDGE_BOOK),
		RuleDef.flag("minimap", "Mini harita", "HUD modu olanlara sağ üstte sınır/takım haritası; herkese başta harita verilir.", true, Items.FILLED_MAP),
		RuleDef.num("compass", "Pusula", "0 kapalı · 1 takım arkadaşı + başlangıç noktası · 2 ek olarak en yakın/en uzak rakip. Herkese başta pusula verilir; sağ tık hedefi değiştirir, elinde tutunca mesafe canlı yazar.", 2, 0, 2, 1, Items.COMPASS),
		RuleDef.flag("biomeInfo", "Biyom bilgisi", "Altta bulunduğun biyom yazar; kartta 'bölge biyomları' düğmesi çıkar (koordinat verilmez).", true, Items.SPYGLASS),
		RuleDef.num("borderStart", "Sınır (yarıçap)", "Dünya sınırı yarıçapı (blok). Herkes aynı noktada başlar, kimseye avantaj yok.", 1000, 200, 1000, 100, Items.MAP),
		RuleDef.flag("showHealth", "Can göstergesi", "Tab listesinde ve isim etiketinin altında oyuncuların kalpleri görünür (doğal yenilenme kapalıyken en çok işe yarar).", false, Items.APPLE),
		RuleDef.num("tasks", "Görev kareleri", "Kartta eşya yerine kaç başarı görevi (ilerleme) çıksın. Görevler seed'e göre yapılabilir olanlardan seçilir.", 3, 0, 8, 1, Items.WRITABLE_BOOK),
		RuleDef.num("difficulty", "Kart zorluğu", "1 kolay, 2 normal, 3 zor. Zorluk, eşyanın bu seed'de ne kadar uzakta/zor bulunduğundan hesaplanır.", 2, 1, 3, 1, Items.NETHER_STAR),
		RuleDef.flag("friendlyFire", "Takım ateşi", "Açıkken takım arkadaşına da vurulabilir.", false, Items.IRON_SWORD),
		RuleDef.flag("pvp", "PvP", "Kapalıyken oyuncular birbirine vuramaz.", true, Items.DIAMOND_SWORD),
		RuleDef.num("pvpGraceMin", "PvP kapalı süre (dk)", "Başta herkes sakin eşya toplasın: bu süre boyunca PvP kapalı. 0 = baştan açık.", 5, 0, 30, 1, Items.SHIELD),
		RuleDef.num("startFood", "Başlangıç yemeği (biftek)", "Her oyuncuya verilecek biftek sayısı.", 8, 0, 32, 8, Items.COOKED_BEEF),
		RuleDef.flag("antiCheat", "Hile koruması: oyuncular", "Duvarın/zeminin arkasındaki oyuncular (ESP, wallhack, radar) istemciye hiç gönderilmez; menzil aşımı, duvar arkasından vurma ve aşırı tıklama engellenir; gerçek seed gizlenir. Oyun başlayınca devreye girer.", true, Items.TINTED_GLASS),
		RuleDef.num("antiXray", "Hile koruması", "0 kapalı · 1 gömülü cevherler gizlenir, yerine sahte (halüsinasyon) cevher gösterilir · 2 ek olarak yeraltı boşlukları ve yapılar düz taş görünür; yaklaşınca/kırınca gerçeği açılır.", 2, 0, 2, 1, Items.ENDER_EYE),
		RuleDef.flag("teamVoice", "Takım ses kanalı", "Takım arkadaşların (Simple Voice Chat) mesafeden bağımsız seni duyar; yakındaki herkesle normal konuşma da sürer.", true, Items.NOTE_BLOCK)
	);

	public static final List<RuleDef> MANHUNT = List.of(
		RuleDef.num("runners", "Runner sayısı", "Rastgele seçilecek runner sayısı.", 1, 1, 4, 1, Items.LEATHER_BOOTS),
		RuleDef.num("headStartSec", "Runner avantajı (sn)", "Hunter'lar bu süre boyunca donuk bekler. Varsayılan 0: herkes aynı yerde, aynı anda, kıyak yok.", 0, 0, 120, 15, Items.CLOCK),
		RuleDef.flag("friendlyFire", "Takım ateşi", "Açıkken takım arkadaşına da vurulabilir.", false, Items.IRON_SWORD),
		RuleDef.flag("pvp", "PvP", "Kapalıyken oyuncular birbirine vuramaz (runner sadece ejderhayla/çevreyle ölür).", true, Items.DIAMOND_SWORD),
		RuleDef.flag("showHealth", "Can göstergesi", "Tab listesinde ve isim etiketinin altında oyuncuların kalpleri görünür (doğal yenilenme kapalıyken en çok işe yarar).", true, Items.APPLE),
		RuleDef.num("compassIntervalSec", "Pusula gecikmesi (sn)", "Hunter pusulası runner konumunu bu aralıkla günceller. 0 = canlı (saniyede 4 kez). Sağ tık: runner / en yakın / en uzak / takım arkadaşı seçimi.", 0, 0, 10, 1, Items.COMPASS),
		RuleDef.flag("hunterRespawn", "Hunter yeniden doğsun", "Kapalıyken ölen hunter seyirci olur.", true, Items.TOTEM_OF_UNDYING),
		RuleDef.num("startFood", "Başlangıç yemeği (biftek)", "Her oyuncuya verilecek biftek sayısı.", 8, 0, 32, 8, Items.COOKED_BEEF),
		RuleDef.flag("antiCheat", "Hile koruması: oyuncular", "Duvarın/zeminin arkasındaki oyuncular (ESP, wallhack, radar) istemciye hiç gönderilmez; menzil aşımı, duvar arkasından vurma ve aşırı tıklama engellenir; gerçek seed gizlenir. Oyun başlayınca devreye girer.", true, Items.TINTED_GLASS),
		RuleDef.num("antiXray", "Hile koruması", "0 kapalı · 1 gömülü cevherler gizlenir, yerine sahte (halüsinasyon) cevher gösterilir · 2 ek olarak yeraltı boşlukları ve yapılar düz taş görünür; yaklaşınca/kırınca gerçeği açılır.", 2, 0, 2, 1, Items.ENDER_EYE),
		RuleDef.flag("teamVoice", "Takım ses kanalı", "Takım arkadaşların (Simple Voice Chat) mesafeden bağımsız seni duyar; yakındaki herkesle normal konuşma da sürer.", true, Items.NOTE_BLOCK)
	);

	public static final List<RuleDef> KATIL = List.of(
		RuleDef.num("killers", "Katil sayısı", "Rastgele seçilecek katil sayısı (herkes aynı skin'i giyer, kimse bilmez).", 1, 1, 2, 1, Items.IRON_SWORD),
		RuleDef.num("graceSec", "Başlangıç koruması (sn)", "Başta herkes için PvP kapalıdır (kimseye donma/etki yok: katil ele verilmez). Herkes 200x200 alanda rastgele doğar.", 60, 0, 300, 30, Items.CLOCK),
		RuleDef.flag("friendlyFire", "Masumlar birbirine vurabilir", "Kapalıyken masum masuma vuramaz; vuran 'hasar yok' görüp kimin katil olduğunu anlar (önerilmez).", true, Items.IRON_SWORD),
		RuleDef.flag("natureEliminates", "Doğal ölüm de eler", "Açıkken masum düşme/canavar/lav ölümüyle de elenir (ölüm nedeni yazılmaz). Kapalıyken sadece öldürülen elenir.", true, Items.SKELETON_SKULL),
		RuleDef.flag("compass", "Yakınlık pusulası", "Herkese pusula verilir: sağ tık ile en yakın / en uzak oyuncu seçilir; isim yazmaz, sadece mesafe ve yön.", true, Items.COMPASS),
		RuleDef.flag("voice", "Sesli sohbet", "Kapalıyken oyun sırasında ses iletilmez (Simple Voice Chat). Oyun içinde değiştirilebilir.", true, Items.NOTE_BLOCK),
		RuleDef.flag("deepVoice", "Kalın ses", "Açıkken herkesin sesi aynı kalın sese çevrilir, kim konuşuyor anlaşılmaz. Kapalıyken kendi sesin.", true, Items.BELL),
		RuleDef.num("startFood", "Başlangıç yemeği (biftek)", "Her oyuncuya verilecek biftek sayısı.", 8, 0, 32, 8, Items.COOKED_BEEF),
		RuleDef.flag("antiCheat", "Hile koruması: oyuncular", "Duvarın/zeminin arkasındaki oyuncular (ESP, wallhack, radar) istemciye hiç gönderilmez; menzil aşımı, duvar arkasından vurma ve aşırı tıklama engellenir; gerçek seed gizlenir. Oyun başlayınca devreye girer.", true, Items.TINTED_GLASS),
		RuleDef.num("antiXray", "Hile koruması", "0 kapalı · 1 gömülü cevherler gizlenir, yerine sahte (halüsinasyon) cevher gösterilir · 2 ek olarak yeraltı boşlukları ve yapılar düz taş görünür; yaklaşınca/kırınca gerçeği açılır.", 2, 0, 2, 1, Items.ENDER_EYE)
	);

	public static List<RuleDef> of(String game) {
		return switch (game) {
			case "UHC" -> UHC;
			case "BINGO" -> BINGO;
			case "KATIL" -> KATIL;
			default -> MANHUNT;
		};
	}
}
