# Devletler RTS · profiller ve orta oyun yatırımı

Devletler’de oyuncu kökenini ve devlet profilini ayrı seçiyor. Köken şimdilik belediye görünümünü belirliyor. Köken birlikleri ve özel koşulları tasarımda yer alıyor. Profil ise Tarım, Madencilik, Ticaret, Savunma veya Keşif üzerinden oyuncunun ekonomi ve genişleme kararını değiştiriyor. Belediye üzerinden bir demir 3 altına satın alınabiliyor. Talim bir seviye için 1 demir ve 2 altın harcıyor. [Tasarım](DEVLETLER_RTS_TASARIMI.md) · [Prototip kuralları](../prototypes/devletler-rts/README.md)

## Yayımlanmış oyun kuralları

| Kaynak | Oyun mekaniği ve uygulanabilir çıkarım |
| --- | --- |
| **Age of Empires IV · Delhi ve Abbasîler** | Delhi teknolojilerini kaynak harcamadan, daha uzun araştırma süresiyle açar. Abbasîlerin Bilgelik Evi farklı gelişim dalları sunar. Geliştirici, eski çağ atlama süresinin rakibe cevap vermeyi zorlaştırdığını ve süreyi bu nedenle kısalttığını açıklıyor. Devletler’de her profile oyuncunun hissedeceği bir bedel ver ve temel karşı hamleleri her profilde erişilebilir tut. [Delhi](https://www.ageofempires.com/games/age-of-empires-iv/civilizations/delhi-sultanate/) · [Abbasîler](https://www.ageofempires.com/games/age-of-empires-iv/civilizations/abbasid-dynasty/) · [Patch 11009](https://www.ageofempires.com/news/age-of-empires-iv-patch-11009) |
| **Age of Empires II ve IV · üretim binaları** | AoE II’de kışla, Okçuluk Alanı ve Ahır için önkoşul. Kuşatma Atölyesi daha sonraki çağda kullanılabiliyor. AoE IV’ün resmî rehberi de pazarı ve kuşatma atölyesini farklı aşamalara yerleştiriyor. Devletler’de başlangıç ordusu hemen erişilebilir kalabilir ve ek rol, oyuncunun haritadaki somut bir yapıya yatırım yapmasıyla açılabilir. [AoE II ilerleme rehberi](https://www.ageofempires.com/learn-to-play/advancing-aoe2/) · [AoE IV ordu rehberi](https://www.ageofempires.com/news/age-of-empires-iv-tips-to-help-you-get-started/) |
| **0 A.D. · Arsenal** | Alpha 27’de Makedonlar kasaba evresinde Arsenal kurabildiği için ok atar kuşatma birimlerine erken erişiyor. Kaynak şablonunda Arsenal ayrı bir askerî yapı. Devletler’de özel yapıya yatırım farklı bir birlik rolüne erişim verebilir. Bir rolün erken açılması tek başına saldırı gücü artışı gerektirmez. [Alpha 27 yayımlama notu](https://play0ad.com/new-release-0-a-d-alpha-27-agni/) · [Arsenal kaynak şablonu](https://github.com/0ad/0ad/blob/master/binaries/data/mods/public/simulation/templates/template_structure_military_arsenal.xml) |
| **StarCraft II · Terran üretimi** | Aynı üretim binasına bir anda tek eklenti takılır. Reaktör iki temel birimi aynı anda eğitebilir. Teknik laboratuvar daha gelişmiş seçenekler açar. Devletler’de daha fazla temel birlik ile özel bir rol hazırlamak aynı kaynak ve işçi zamanını paylaşırsa karar anlam kazanır. [Blizzard Terran eklentileri rehberi](https://news.blizzard.com/en-us/article/5740265/game-guide-terran-add-ons) |

## İlk profil önerileri

Başlangıç kaynakları, temel birlik erişimi ve mevcut köken kuralları ortak temeli oluşturur. Her profilin artısı ve bedeli profil seçiminde, işlemin kesin fiyatı ve bekleme süresi ilgili belediye menüsünde gösterilir. Aşağıdaki değerler ilk ölçüm önerileridir.

| Profil | Avantaj | Oyunda hissedilecek bedel |
| --- | --- | --- |
| **Tarım** | Mevcut tarla iş süresini 24 yerine 18 saniye tut. Yürüyüş ve stok sınırı geçerli. | Demir işçiliğinin kazı adımı bir miktar daha uzun sürer. Damardan çıkan demir sayısı ve satın alma yolu korunur. |
| **Madencilik** | Erişilebilir damarda kazı adımını hızlandır. Bir damarın maçlık kotasını, yürüme süresini veya depo tavanını değiştirme. | Tarla işi normal 24 saniyeden daha uzun sürer. Birlikler erzakla normal şekilde toparlanabilir. |
| **Ticaret** | Kervan ziyaretindeki uygun alış fiyatından 1 altın indir. İndirim alt sınırı 1 altın. Aynı kervanın sipariş ödemesi değişmez. | Yeni karakolun taş maliyetine 1 kırık taş eklenir. Erken toprak yatırımı daha pahalıdır. |
| **Savunma** | Karakola yaklaşık %20 ek azami can ver. Ön cephede görünen ve saldırılabilen yapı değer kazanır. | Yeni karakol kurulunca toprağı bağlamadan önce kısa bir hazırlık süresi ister. Kurulum sırasında yıkılırsa alan kazanılmaz. |
| **Keşif** | Belediye çevresinde daha geniş arazi taraması. Oyuncu yürünebilir açıklığı, suyu ve mevcut kurallarla ulaşılabilen kaynak yönünü daha erken görür. | Karakolun başlangıç dayanıklılığı düşer. Tarama gizli rakip birlikleri ve stoklarını açmaz. |

## Demiri değerli kılacak orta oyun yatırımı

**Klan Atölyesi.** Oyuncu bağlı toprakta saldırılabilir bir özel yapı kurar. İlk deneme 2 demir, 2 odun ve 2 kırık taş ile devlet başına bir etkin atölye. İki demir, mevcut talimde iki seviye işinin demir payına ya da pazardan alındığında 6 altına denk gelir. Böylece oyuncu gelişmiş bir role erişmekle mevcut birliklerine yatırım yapmak arasında seçim yapar. Yeri ve fiyatı belli olduktan sonra yerleşimle ödeme tek işlemde tamamlanır.

Atölye, kökeni değiştirmeden ilk ileri rolü açar. Örneğin birlik sınırından bir yer kullanan, yavaş hareket eden ve yalnız maçın karakol veya mahalle hedeflerine karşı avantajlı bir **Siper Kıran** denenebilir. Temel milis ve okçu belediyede erişilebilir kalır. Yapı yıkılırsa veya toprak bağlantısı koparsa yeni üretim askıya alınır. Elde bulunan birimler oyunda kalır. İleri rollerin demirle tek tek güçlendirilmesi, yapı yatırımından ayrı ölçülür.

İleri rolü hazırlamadan önce daha küçük kapsamlı bir demir gideri denenebilir. **Karakol tahkimatı**, bağlı bir karakolda tek basamaklı dayanıklılık artışı sağlar. Süreli işlem menüde gösterilir ve çatışma sırasında askıya alınır. Tamamlanınca karakolun aldığı mevcut hasar korunur. Bu yatırım sınırı savunmayı, atölye yeni birlik hazırlamayı destekler.

## İlk maçta bakılacak sonuçlar

- **2, 4 ve 8 devlet.** Kısa ve uzun işçi rotaları, yakın demir damarı ve damarsız başlangıç, geniş ve dar sınırlar ile kazanç ve bedeli ölç. Profillerin ilk beş dakikadaki teslimat sayısını, ilk karakol zamanını, kervan indiriminin harcanabildiği ziyaret sayısını ve keşfin getirdiği yeni geçerli hedefi kaydet.
- **Karşı hamle.** Erken saldırıda her profil başlangıç savunmasına ulaşabilmeli. Savunma profilinin uzattığı karakol çatışmasının sonunda ilerleme mümkün olmalı. Atölye kullanılmadan da temel birliklerle maç oynanabilmeli. Aynı kökeni farklı profillerle, aynı profili farklı kökenlerle karşılaştır.
- **Demir ve kayıt.** Talim ve inşaat aynı demiri harcayamaz. Geçersiz yapı yeri bedel düşürmez. Bağlantı kaybı ve yıkım yeni üretimi doğru durdurur. Yeniden bağlanma ve kayıttan sürdürme bitmiş harcamayı ya da verilen birlik erişimini çoğaltmaz.

## İlk uygulama

Mevcut kervan sipariş kabul ettiği için Ticaret profiline teslimat başına 1 altın ekleniyor. Savunmanın genişleme bedeli iki karakolluk sınırla, Keşif’in avantajı 24 blokluk işçi menziliyle ifade ediliyor. İlk özel yapı olarak Talim Ocağı seçildi. Yapı 2 demir kullanıyor ve bağlı toprağa inşa edilince yerel talim süresini kısaltıyor. İleri birlikler eklendiğinde Klan Atölyesi yeniden değerlendirilebilir. [Uygulanan kurallar](../prototypes/devletler-rts/README.md#devlet-profilleri)
