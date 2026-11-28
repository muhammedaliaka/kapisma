# Devletler RTS oyun tasarımı

## Hedef ve geliştirme sırası

Oyuncunun isteği, 2–8 arkadaşın Minecraft arazisinde yukarıdan yönetilen ayrı devletler kurması. Konum, kaynaklar, birlik emirleri ve anlaşmalar savaşın sonucunu değiştirmeli. Başkent kaybedilince devlet elenmeli. Tek oturum ve hostun yeniden açtığı kayıtlı oturum seçenekleri bulunmalı. Akıllı NPC kullanımını her oyuncu kendi devleti için açıp kapatabilmeli.

Doğal arazide üç dakikalık kuruluş, bölge rezervasyonu, dört belediye görünümü, beş devlet profili, HYW birlik komutası, takviye ve başkent elemesi oynanabiliyor. Bölgeden ayrılan oyuncunun dönüşü ve maç temizliği sınandı. Sabit test dünyasında sekiz devletli açılış doğrulandı. Düz test profilinde maden, talim, erzak ve savunma otomasyonu da kullanılabiliyor.

1. **Doğal ekonomi.** İşçi ulaşabildiği gövde, kaya veya cevhere gidip odun, kırık taş, demir ve altını belediyeye taşır. Dünya blokları yerinde kalır. Akıllı NPC kapalıyken oyuncu tepeden kaynağı seçip işçiye görev verir. Demir talimde harcanır ve doğal kaynağı olmayan devletler altınla demir alabilir. Belediye gelirine sınırlı doğal altın ve süreli kervan siparişleri eklenir. Kervan, her devletten ziyaret başına bir teslimat kabul eder. [Altın ekonomisi araştırması](DEVLETLER_RTS_ALTIN_VE_BORC.md)

   Doğal haritada her devlet iki erzakla başlar ve sürekli üretim için oyuncu bağlı toprağına tarla kurar. Çiftçi ürünü teslim ettiğinde belediye stoğu artar. Tarım profili tarla işini hızlandırır. Yakın çevresi güvenli belediye, erzak dağıtımını seçen oyuncunun dinlenen yaralı birliklerini iyileştirir. [Erzak araştırması](DEVLETLER_RTS_ERZAK_ARASTIRMASI.md)

2. **İnşaat ve toprak.** Normal karakol bedeli 3 odun ve 2 kırık taş. Yapı, belediyeye veya bağlı bir karakola bitişik 7×7 doğal açıklığa yerleştirilir. Belediye 12, bağlı karakol 8 blok yarıçapında alan sağlar. Bağlantı koptuğunda uç karakol alanını kaybeder. Mahalle 4 odun ve 3 kırık taşa kurulur. Bağlı kaldığı sürece 3 ek birlik kapasitesi sağlar. Bağlantı veya mahalle kaybedilince eldeki ordu kalır ve birlik sayısı yeni sınırın altına inene kadar takviye durur. Talim Ocağı bağlı toprağa 3 odun, 2 kırık taş ve 2 demirle kurulur. 9×7 avlu yakınındaki askerleri belediyenin 20 saniyesi yerine 12 saniyede eğitir.
3. **Köken, profil ve birlikler.** Dört kökenin belediye görünümü değişir. Beş profil ekonomi, çalışma süresi, işçi menzili ve karakol özelliklerine etki eder. Profil seçimi kökenden bağımsızdır. Oyuncu emirleri otonom görevden önce gelir. Kökenlere özel birlikler ile Gece Halkı’nın toprak ve güneş kuralları geliştirme sırasındadır.
4. **Diplomasi ve oturum.** Doğal Kurallı Diplomasi’de banka belediye, doğal altın ve kervan gelirini kullanır. Borç ve toparlanma belediye menüsünden yönetilir. Karşılıklı ittifaklar altın, erzak ve demir yardımına izin verir. Etkin savaşta müttefike çağrı yapılabilir. Geçerli çağrının reddi 1, ittifakı bozup savaş ilanı 3 istikrarsızlık getirir. [Altın ve devlet borcu](DEVLETLER_RTS_ALTIN_VE_BORC.md)

Elle kaynak emri HYW’nin tepe kamerasından gelen sağ tık hedefini kullanır. Oyuncu **Gövde seç** seçeneğini açtıktan sonra bir gövde seçer. Sunucu oyuncunun devletini, gövdenin kotasını ve işçinin ulaşabileceği rotayı doğrular. İşçi teslimatı mevcut belediye deposuna yapar. Gerçek Java istemcisiyle yanlış bloğun ve tükenmiş gövdenin reddedildiği, doğru hedefin otomasyon kapalıyken depoya odun kazandırdığı doğrulandı.

## Kullanıcının belirlediği oyun

- İlk üç dakika devlet kurma aşaması. Oyuncu devletini ve başkentinin yerini seçer. Orman, yükseklik, su, geçirilebilir yollar ve doğal kaynaklar oynanışı etkilemeli. Mevcut düz arena, geliştirme ve hareket denemelerinde kullanılabilir.
- Oyun iki diplomasi biçimi içerir. Serbest biçimde oyuncular kendi aralarında söz verir ve konuşur. Oyun verilen sözleri denetlemez. Kurallı Diplomasi’de yardım çağrısı reddedilebilir ve reddeden devlet hafif istikrarsızlık yaşar. Oyuncu müttefiğine saldırmayı seçerse ağır istikrarsızlık yaşar. En ağır durumda yeni savaş açamaz. Altın, erzak ve maden yardımı yapılabilmelidir.
- İlk sürümde dört köken olacak. Köylüler, Nether, Yağmacılar ve Gece Halkı. Beşinci köken daha sonra değerlendirilecek. Her oyuncu kökenini ve beş devlet profilinden birini ayrı seçer. Aynı köken ve profil birden fazla oyuncuya açıktır. Kökenler ve birlikler kendi işlerinde güçlü olmalı. Örneğin zombi ve iskelet farklı görevlerde öne çıkabilir. Ayrıca profil puanı avantajı değerlendirilecek.
- Gece Halkı kendi bölgesini gece olarak görürken diğer oyuncular gündüz görebilmeli. Belediye ve bağlı karakolların sahip olduğu bölgeler koruma sağlar. Bu alanların dışında güneş gören hassas birlikler etkilenir. Kişiye özel görünüm ile sunucunun uyguladığı güneş kuralları ayrı ele alınacak.
- Devlet profilleri Tarım, Madencilik, Ticaret, Savunma ve Keşif. Her profil kendi güçlü yönüne ve bir bedeline sahip olacak. Profil etkileri kökenlerden ayrı uygulanacak.
- Belediye binası, klan binaları, mahalleler ve karakollar isteniyor. Mahalle veya karakol kurmak bağlı bir alanı büyütmeli. Oyuncu gelişigüzel toprak satın alamamalı. Demir özel yapılar için, kırık taş temel yapılar ve evler için kullanılacak.
- Oyun altın, demir ve başka madenler, odun, yiyecek ve kırık taş akışlarını kapsar. Altın ortak ödeme ve anlaşma aracı. Arada gelen kervanlar daha yüksek altın bedeline ihtiyaç duyulan silahı sağlayabilir. İleride farklı kökenlerle aynı kervanın farklı ürün veya fayda sunması değerlendirilecek.

## Kuruluş ve fiziksel dünya

Doğal arazide aday bölgeler ölçülmeli. Her aday için ayakta durulabilir başkent noktası, yakın yüzey farkı, su, odun, yüzeye yakın taş ve rakip başkentlere mesafe kayda alınır. Başkent çekirdeğinde yeterli hareket alanı bulunmalı. Çevredeki yokuş, ağaç ve su korunur. Bina yerleştirme aşaması gerektiğinde yalnız binanın oturum alanını düzenler.

Yeni bir doğal dünya, mevcut düz dünya kaydından ayrı düşünülecek. Dünya türünü bir ayarda değiştirmek, daha önce üretilmiş düz parçaları yeniden üretmez. Mevcut maçları korumak için doğal mod adım adım ayrı laboratuvar dünyasında denenir.

Oyuncu haritadaki doğrulanmış doğal bölgelerden başkent yeri seçecek. Her bölgede su, ağaç, taş ve arazi bilgisi görünecek. İlk seçen oyuncu bölgeyi ayırır. İlk üç dakika bitene kadar boş bir bölgeye geçebilir ve önceki bölgeyi serbest bırakır. Süre bitince bölge seçmemiş oyunculara kalan doğrulanmış alanlardan birer tane atanır.

## Ekonomi ve binalar için ilk kurallar

| Unsur | Önerilen oyun işlevi |
| --- | --- |
| Belediye binası | İlk bina, devletin merkezi ve başkent yenilgi koşulu. |
| Ev ve mahalle | Temel yapı kapasitesi ve belediyeye bitişik gelişim. |
| Karakol | Bitişik bir sınır kesimini koruyan veya genişleten yapı. Yıkıldığında bölge sahipliği yeniden değerlendirilir. |
| Klan binası | Köken ya da devlet profilinin özel birimini veya üretim seçeneğini açan yapı. |
| Maden ve odun üretimi | Kaynağa ulaşılabilen noktaya bağlı toplama. Depo kapasitesi ve taşıma rotası önem taşır. |
| Kervan | Haritada görülebilen, belli aralıklarla gelen pahalı takviye ve özel ürün kaynağı. |
| Yardım | Alıcı devlet seçilir. Altın, erzak veya maden gönderenin deposundan düşülür ve aynı işlemde alıcıya geçer. |

İlk profil dengesinde Tarım tarla işini hızlandırır ve cevheri daha yavaş toplar. Madencilik cevheri hızlandırır ve tarla işini uzatır. Ticaret kervan siparişinden bir altın fazla alır, karakola bir kırık taş fazla verir. Savunma daha dayanıklı iki karakol kurabilir. Keşif işçi menzilini 16 bloktan 24’e çıkarır, karakolunun canı düşer. Bunlar doğal maç için ilk denge değerleridir. [Uzmanlaşma ve askerî yapı araştırması](DEVLETLER_RTS_UZMANLASMA_ARASTIRMASI.md)

Kurallı Diplomasi’de istikrarsızlık oyun tarafından doğrulanabilen olaylardan doğar. Sohbet metinleri ve oyuncu niyetleri mekanik tarafından yorumlanmaz. Bütçe açığı, bakım, erzak ve toparlanma kararları için üç ayrı öneri araştırma notunda bulunur. [İstikrar ve hazine araştırması](DEVLETLER_RTS_ISTIKRAR_ARASTIRMASI.md)

Borç ve istikrarsızlık birlikte krizi ağırlaştırır. Tarafsız bankadan devlet başına tek kredi alınır. Toplam geri ödeme ve taksitler baştan bellidir. Gelirden önce temel gıda ve bakım payı ayrılır, ardından borç ödenir. Borç bitince erzak ve belediye onarımıyla halkın memnuniyeti toparlanır. Doğal dünya ekonomisi küçük belediye geliri, sınırlı altın damarları ve değişen kervan siparişleri kullanır. [Altın ve devlet borcu](DEVLETLER_RTS_ALTIN_VE_BORC.md)

## Oynanışta sıradaki geliştirmeler

| Alan | Mevcut özellik | Sıradaki çalışma |
| --- | --- | --- |
| Kuruluş ve arazi | 3 dakika, doğal bölgeler, kaynak özeti, hazır bölge yoksa yeniden tarama | Farklı doğal seed’lerde başkent açıklığı ve kaynak erişimini 2–8 devletle doğrula. |
| Kökenler ve askerî oyun | Dört belediye görünümü, milis ve okçu, talim ve Talim Ocağı | Önce Gece Halkı için bağlı bölgede güneş koruması ve farklı rollerde zombi ile iskelet. Ardından diğer üç köken için aynı bütçede ayırt edilebilir birlikler. |
| Ticaret ve inşaat | Menüden kervan siparişi, bağlı toprak, karakol, mahalle, tarla, Talim Ocağı | Görünür kervan ve kökene göre değişen sınırlı satış kataloğu. İlk özel birlikler geldikten sonra klan binaları. |
| Diplomasi ve oturum | İttifak, yardım, savaş çağrısı, borç, istikrar ve kayıtlı maç | Gerçek arkadaş maçında karar sürelerini gözle ve bileşik anlaşmaları talebe göre genişlet. |

İlk köken denemesinde Gece Halkı için ön hattı tutan zombi ve geriden ateş eden iskelet tasarlanabilir. Güneş koruması belediye ile aynı bağlı toprak hesabından gelir. Böylece karakolun kaybı gündüz güvenli rotayı da değiştirir. Köylüler için ikmal ve savunma, Nether için kısa menzilde ağır birlikler, Yağmacılar için hızlı baskın ve geri çekilme denenebilir. Birliklerin fiyatı ve karşı hamlesi birlikte değerlendirilir. Yayımlayıcının Fabric 1.21.1 sürümündeki mevcut birliklerin seçilme, sahiplik ve hareket komutları ilk uyumluluk denemesine temel olur. [HYW sürüm listesi](https://www.curseforge.com/minecraft/mc-mods/hundred-years-warfare/files/all) · [Age of Empires IV birlik rolleri](https://www.ageofempires.com/news/age-of-empires-iv-tips-to-help-you-get-started/)

## İlk doğal arazi denemesi

Mevcut HYW ve Minecraft Overworld üretimiyle ayrı laboratuvar kaydı açıldı. Host menüsündeki **Araziyi tara** seçeneği çevrede yüklenmiş parçaları okur. Aday için dünya sınırı içinde 5×5 yürünebilir ayak izi ve iki blok baş boşluğu arar. Bölgeler en az 40 blok ayrılır. Menü biyomu, yüksekliği ve örneklenen sütunlarda bulunan suyu, doğal odunu ve yüzeye yakın taşı gösterir. Kaynak sayısı gerçek örneklerdir. Gömülü cevher ve rota garantisi ayrı doğrulama gerektirir.

Kuruluş denemesinde doğrulanmış bölgeler ortak listede üç dakika boyunca seçilebilir. Köken ve devlet profili ayrı düğmelerle seçilir. Tek gerçek Minecraft istemcisi ve iki botla ilk komutun önceliği, bölge değişimi, önceki bölgenin boşalması, aynı köken ve profil seçimleri ve süre sonunda boş bölgeye otomatik yerleşme geçti. Süre dolduğunda bağlantısı kesilmiş oyuncu da aynı oturumda geri dönünce ayrılan bölgeye ulaştı. Normal taramanın yetmediği durumda daha sık tarama yapılır. Bu yolla sabit test haritasında sekiz farklı başkent açıldı.

Doğal Başkent maçını doğrulamak için proje kökünde Python 3.11 veya üzeriyle `_yedek/devletler-rts-prototip/laboratuvar/verify_terrain.py --match` çalıştırılır. Betik mevcut yerel Fabric bileşenleriyle geçici adaptörü derler, laboratuvardaki `world-terrain-qa` kaydını kullanır ve test sonunda sunucu özelliklerini geri yükler.

Kuruluşta seçilen bölge, maçta başkent ve takviye noktası olur. Belediye çatısı yaklaşık 11 blok genişliğinde, bayrak direği temel üstünden 11 blok yüksektedir. Yapı başkent çevresinde ağaç ve su engeli daha az olan bir açıklığa yerleşir. Temelin yüksekliği çevredeki zemine uyarlanır. Köken yapı malzemesini ve cephe işaretini, devlet rengi bayrağı belirler. Minecraft blok görünümleri kullanılır. Hasar aynı kuruluş bölgesindeki görünür başkent birimine uygulanır. Maç sıfırlanınca görünümler ve birlikler kaldırılır, üs parçalarının yükleme biletleri bırakılır. Yerel doğal profilde, gerçek istemciden verilen hareket emrini milis ve okçunun uyguladığı doğrulandı. [Yapı ve hazır model araştırması](DEVLETLER_RTS_YAPI_ARASTIRMASI.md)

Arazi sorgularında kullanılan sürüme özel Minecraft ve hazır mod seçenekleri araştırma notunda kayıtlıdır. [Doğal dünya araştırması](DEVLETLER_RTS_DOGAL_DUNYA_ARASTIRMASI.md)
