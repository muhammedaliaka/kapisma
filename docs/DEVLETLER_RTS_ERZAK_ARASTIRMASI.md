# Devletler RTS · çiftlik, erzak ve birlik toparlanması

Onaylanan başlangıçta her devlet belediyede küçük bir hazır stokla başlar. Sürekli üretim için oyuncu kendi tarlasını kurar. Düz prototipte çiftçi buğdayı hasat edip yeniden ekiyor. Üç buğday bir ekmek ediyor, depo sekiz ekmek tutuyor, bir ekmek varsayılan olarak dört can yeniliyor ve aynı birlik iki erzak arasında 15 saniye bekliyor. [Tasarım kararı](DEVLETLER_RTS_TASARIMI.md) · [Prototip kuralları](../prototypes/devletler-rts/README.md) · [Erzak kaynak kodu](../prototypes/devletler-rts/adapter/RtsSupply.java)

## Yayımlanmış oyun kuralları

| Kaynak | Mekanik ve Devletler için çıkarım |
| --- | --- |
| **Age of Empires II: Definitive Edition** | Ucuz birliklerin bir kısmı gıda ya da gıda ve odun ister. Gıda, oyuncuya ordunun geleceği hakkında karar verdirir. Resmî güncellemede köylülerin yalnız kendi tükenen tarlalarını yeniden ektiği belirtilir. Devletler’de iyileştirme, başlangıç için kendi başına gıdaya değer katar. Asker alımına zorunlu erzak bedeli koymadan önce bu değeri ölç. [Ordu ve ekonomi rehberi](https://www.ageofempires.com/learn-to-play/military-and-economy-aoe2/) · [Update 93001](https://www.ageofempires.com/news/age-of-empires-ii-definitive-edition-update-93001/) |
| **0 A.D.** | Eğitim senaryosu sınırlı yemişlerin ardından kalıcı ama daha yavaş bir kaynak olarak tarlayı kurdurur. Taşıma mesafesini kısaltmak için tarlayı gıda deposuna yakın yerleştirir. Tarla şablonu işçi sayısını sınırlar ve ek işçilerin marjinal verimini düşürür. Devletler’de işçi zamanı, yol uzunluğu ve stok tavanı yatırımı dengeler. [Eğitim senaryosu](https://github.com/0ad/0ad/blob/master/binaries/data/mods/public/maps/tutorials/starting_economy_walkthrough.js#L248-L305) · [Tarla şablonu](https://github.com/0ad/0ad/blob/master/binaries/data/mods/public/simulation/templates/template_structure_resource_field.xml#L44-L50) |
| **Age of Empires IV** | İngilizlerin daha ucuz tarlaları güvenilir bir ekonomi avantajı sağlar. Başka bir güncelleme, saldırıya uğrayıp karşılık vermeyen birlikleri de keşiş iyileştirmesi açısından çatışmada sayar. Devletler’de Tarım profili hasat hızında avantaj sağlayabilir. Birliğin hasar alması, boşta görünse bile ikmali ertelemeli. [İngiliz medeniyeti](https://www.ageofempires.com/games/age-of-empires-iv/civilizations/english/) · [Update 16.1.9737](https://www.ageofempires.com/news/age-of-empires-iv-update-16-1-9737-and-yue-feis-legacy-dlc-release-preview/) |
| **Warcraft III** | Healing Salve satın alınan, dükkânda stok sınırı olan bir toparlanma eşyası. Etkisi saldırıyla kesilebiliyor. Devletler’de yaralı birliği geri çekmenin ve güvenli yere ulaştırmanın değeri olsun. [Blizzard Voodoo Lounge rehberi](https://classic.battle.net/war3/orc/voodoolounge.shtml) |
| **Minecraft Java** | Mojang buğdayın su yakınındaki sürülmüş toprakta yetiştirilmesini ve üç buğdaydan ekmek yapılmasını belgeliyor. Java sürüm notları ekinlerin rastgele oyun güncellemelerinden etkilendiğini belirtiyor. Devletler’in hasat hızını ölçerken rotayı ve yerel tarla koşullarını hesaba kat. [Ekmek tarifi](https://www.minecraft.net/en-us/article/taking-inventory--bread) · [Java 1.20.2 sürüm notu](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-2) |

## Kapışma için ilk denge denemesi

Devletler’in özgün döngüsü gerçek Minecraft tarlası, belediye stoğu ve erzakla yaralı birlik toparlanmasını aynı ekonomide birleştirir. Aşağıdaki sayılar 15 dakikalık prototip için başlangıç ve ölçüm hedefidir.

| Karar | İlk deneme |
| --- | --- |
| **Açılış ve fiyat** | Devlet başına bir defa 2 ekmek. İlk tarlanın bedelini 3 odunla dene. Oyuncu tarlayı bağlı toprakta erişilebilir bir yere kurar ve mevcut işçiye çiftçilik emri verir. Böylece aynı işçi o sırada odun veya maden taşıyamaz. İlk tarlanın sağlayacağı verim o fırsat maliyetini karşılamalı. |
| **Üretimin karşılığı** | Güvenli ve kısa bir rotada, tarla kurulduktan sonra ilk ekmek 60–90 saniyede depoya ulaşsın. Depoda boş yer varken yerleşik bir tarla dakikada 1,5–2,5 ekmek üretsin. Bu, yaralı birimler ve uygun teslimat olduğunda dakikada 6–10 canlık kapasite demek. Erken yatırım üç dakika içinde başlangıçta verilen iki ekmeği aşabilmeli. İlk tarla gerekli birikimi bulmak için fazla gecikirse önce bedeli ve yer önerisini düzelt. |
| **Üretim sınırı** | Tarlayı Minecraft toprağı, su, ışık, olgun ekin ve yürünebilir yol üzerinden doğrula. İşçi 3 hasadı depoya ulaştırınca 1 ekmek yaz. Ortak devlet stokunu 8 ekmekte sınırla. Gerekirse büyüme hızını yalnız kayıtlı tarla ve çalışan işçi için ayarla. Bağlantı veya rota kopunca üretim durur. Menüde stok, son dakikanın teslimatı ve durma nedeni görünür. |
| **İkmal** | Kendi devletinin ulaşılabilir, en az 2 can kaybetmiş ve dinlenen askeri öncelikli. Son saldırı girişimi veya düşmandan alınan hasardan sonra 8–12 saniye çatışmasız kalmasını dene. Bir ekmek en fazla 4 can verir, uygulanmış iki ikmal arasında birlik başına en az 15 saniye geçer. Tarla işçisi veya bağlı ikmal noktası teslimatı tamamlar. Stok biterse birlik pozisyonunu koruyabilir, iyileştirme bekler. |
| **Değerin başka kullanımı** | Stok, toparlanma veya oyuncunun onayladığı erzak yardımı için kullanılabilir. Para ve erzak birbirine otomatik çevrilmez. Tarım profili avantajını stok tavanı veya ekmek başına cana uygulamak yerine üretim hızında dene. |

## Doğrulama

- **2, 4 ve 8 devlet.** Farklı doğal arazilerde ilk erişilebilir tarla, ilk teslimat, dakikadaki ekmek, tüketilen ekmek başına iyileşen gerçek can ve kesintiye uğrayan teslimatları ölç. 8 devletin başlangıç arzı toplam 16 ekmek, normal stok tavanı devlet başına 8. Toplam oyuncu sayısı devletin açılışını değiştirmez.
- **Tek işlem.** Teslim anında sahiplik, çatışma, erişim, can ve stok tekrar doğrulanır. Aynı ekmek iki birimi iyileştiremez. Teslim iptal olursa ayrılan ekmek bir defa depoya döner. Dövüşte, dolu canla, boş depoyla veya erişimsiz bir rotada ikmal gerçekleşmez.
- **Kalıcı oturum.** Başlangıç stoğu, çiftlik sahipliği, depo ve ayrılmış erzak maç kimliğiyle korunur. Yeniden bağlanma, üs onarımı ve kayıttan devam etme yeni başlangıç ekmeği üretmez. Maç duraklayınca üretim ve toparlanma da durur.
