# Devletler RTS · altın ve devlet borcu

## Karar ve mevcut durum

Kurallı Diplomasi’de borç ve istikrarsızlık birleştiğinde kriz ağırlaşır. Toparlanmanın sırası bütçeyi işler hâle getirmek, borcu ödemek ve ardından halkın memnuniyetini iyileştirmektir. Reddedilen geçerli müttefik yardım çağrısının hafif, müttefike saldırmanın ağır sonuçları ve en ağır istikrarsızlıkta yeni savaş ilanının kilitlenmesi önceki kararlardır.

Mevcut düz profilde devlet 6 altınla başlar. Taban gelir 12 saniyede 1 altındır. Merkez tek devletin elindeyken saniyede 1 altın verdiğinden, merkez bir gelir dönemi boyunca tutulursa taban gelirin 12 katını üretir. Ticaret ve Lojistik de altın sağlar. Doğal dünya ekonomisinde merkezin tek başına gelir yarışını belirlememesi gerekir. [Mevcut gelir kuralları](../prototypes/devletler-rts/prepare.py) · [Varsayılan fiyatlar](../prototypes/devletler-rts/rules.json)

## Yayımlanmış örnekler

- **Age of Empires IV.** Köylüler altın damarlarına gönderilebilir. Pazar, müttefik ve tarafsız noktalarla ticaret açar. 7.0.5861 değişikliğinde tüccar altını pazara veya ticaret noktasına vardığında getirir ve önceki noktaya olan uzaklık geliri etkiler. Ekonomik gelişim ve askerî araştırma da altın harcatır. Buradan çıkan fikir, altın için hem haritada ulaşılması gereken kaynaklar hem de birbiriyle yarışan harcamalar oluşturmaktır. [Başlangıç kılavuzu](https://www.ageofempires.com/news/age-of-empires-iv-tips-to-help-you-get-started/) · [Ticaret güncellemesi](https://www.ageofempires.com/news/age-of-empires-iv-season-five-update-7-0-5861/) · [Ekonomi ve askerî teknoloji fiyatları](https://www.ageofempires.com/news/ageiv_seasonfour_update_60878/)
- **Age of Empires II: Definitive Edition.** Pazar odun, yiyecek ve taş fazlasını altına çevirebilir. Alım ve satım fiyatları değişir. Takım pazarları arasında ticaret arabaları çalışır. Saklanan kutsal emanetler de altın getirir. Buradan çıkan fikir, damarı olmayan bir devletin gerçek mal fazlası veya bir ticaret yoluyla gelir üretebilmesidir. [Pazar ve kutsal emanet dersleri](https://www.ageofempires.com/learn-to-play/military-economy-xbox/#lesson-8-the-market)
- **Victoria 3.** Paradox, vergi sisteminin hem hazinenin dengesini hem halkın geçimini etkilediğini anlatır. Geliştiricinin yapay zekâ örneğinde mali sıkıntıda düşük öncelikli harcamalar önce kesilir, kritik hizmetler daha uzun korunur. Kapışma için çıkarım, borç menüsünde yalnız ödeme düğmesi yerine oyuncunun gideri azaltabileceği ve temel hizmeti koruyabileceği seçenekler sunmaktır. [Vergi ve geçim](https://www.paradoxinteractive.com/games/victoria-3/news/dev-diary-57-the-journey-so-far) · [Harcama öncelikleri](https://www.paradoxinteractive.com/games/victoria-3/news/victoria-3-dev-diary-59-ai)

- **Against the Storm.** Eremite Games, sürekli ticaret akışını istenen malı, ödemeyi ve taşıma süresini gösteren siparişlere çevirdi. Tek kaynak yığıp tüccarın tüm stoğunu almayı caydırmak için doğrudan satış gelirini azalttı. Kapışma’da değişen talepler oyuncuyu farklı kaynaklar toplamaya teşvik edebilir. [Time is Money güncellemesi](https://eremitegames.com/time-is-money-update/)

## Minecraft içerik örnekleri

- **YusufTe.** Aramızda Bir HAİN Var videosunda arkadaşlar ortak bir Minecraft hedefi etrafında oynuyor. Video açıklaması sosyal çıkarım temasını doğruluyor. İncelediğim kanal sonuçlarında devlet borcunu veya altın ekonomisini gösteren bir RTS videosu doğrulayamadım. Kapışma için çıkarım, Serbest oyundaki güven ve sözlerin oyuncular arasında kalmasıdır. [YusufTe kanalı](https://www.youtube.com/channel/UCGy07ovQByevRfBFIrWMmmg) · [Video](https://www.youtube.com/watch?v=yurG8u09vMg)
- **ish.** Rich & Poor etkinliğinin başlangıcı, Minecraft’taki iki adanın kaynak erişimi arasındaki büyük farkı kullanıyor. Kapışma için çıkarım, 2–8 oyuncuya aynı asgari geçimi sağlayıp başlangıç bölgelerinin hangi malları kolay ürettiğini çeşitlendirmektir. [Yapımcının etkinliği](https://www.youtube.com/watch?v=ef568d0CrRY)

## Kullanıcının seçtiği ekonomi

İlk doğal dünya denemesi küçük belediye geliri, sınırlı doğal altın damarları ve değişen kervan siparişlerine dayanacak. Devletler arası ödeme mevcut diplomasi hedeflerinden biridir.

| Altın nereden gelir? | Oyuncunun kararı |
| --- | --- |
| **Belediye geliri** | Ayakta kalan her devlete düşük ve düzenli gelir. Zor durumdaki ülke temel bakım ve borç ödemesine tekrar başlayabilir. |
| **Doğrulanmış altın damarı** | İşçi ve taşıma zamanı ayrılır. Yalnız erişilebilir ve sınırlı gerçek damarlar sayılır. Cevher hazineye teslim edilince altına dönüşür. |
| **Kervan ve pazar** | Mevcut erzak, odun veya maden fazlası satılabilir. Sınırlı teklif ve gerçekleşen teslimat altın getirir. Kervandan acil silah almak yüksek bedelli bir alternatiftir. |
| **Devletler arası ödeme** | Yardım ve ticaret altını bir hazineden diğerine aktarır. İki tarafın karşılıksız gelir kazanacağı anlaşmaların üst üste birikmesi önlenir. |

Altın için üç grup aynı hazineye talip olur. Devlet bakımı ve borç taksitleri, araştırma ve özel takviyeler, diplomatik ödemeler ve acil kervan alımları. Ev ve temel bina yapımında kırık taş ile odun, özel yapılarda demir kullanılmaya devam eder. Altın yönetim, sözleşme ve hızlı erişim bedeli olabilir. Her çiftçilik veya yapı hareketine ayrı altın bedeli eklemek ekonomiyi gereksiz yere tek kaynağa bağlar.

Başlangıç bölgelerinde erişilebilir altın garanti edilecekse gerçek cevheri ve kara yolunu kontrol etmek gerekir. Böyle bir garanti seçilmezse her oyuncu en az bir başka gelir yoluna erişebilmeli. Kaynak taramasındaki yüzeye yakın taş sayısı, altın damarının doğrulandığı anlamına gelmez. [Doğal dünya araştırması](DEVLETLER_RTS_DOGAL_DUNYA_ARASTIRMASI.md)

## Borç ve istikrarsızlık için seçilen akış

1. **Borç al.** Belediye menüsü alınacak altını, toplam geri ödemeyi ve bir sonraki taksidi gösterir. Tarafsız banka devlet başına tek açık kredi verir. Tutar, toplam geri ödeme ve taksitler baştan bellidir.
2. **Bütçeyi dengele.** Gelir, bakım ve taksit ayrı görünür. Oyuncu ertelenebilir masrafı düşürebilir, fazlasını satabilir veya yeni gelir rotası açabilir. Taksit gecikirse ve devlet zaten istikrarsızsa ceza daha ağır olur. Bir vade bir kez işlenir. Üst sınırda yeni savaş ilanı engellenir.
3. **Borcu öde.** Önce temel gıda ve bakım payı ayrılır. Kalan gelirin bir kısmı takside gider. Ödeme borç bakiyesini azaltır.
4. **Halkı yeniden kazan.** Borç kapanınca erzak dağıtımı ve belediye onarımı istikrarı toparlar. Borç varken temel gıda ve bakım için ayrılan pay korunur.

İlk denemede kesin tutarları belirlemeden önce merkez geliri, iki ve sekiz oyuncuda ticaret yoğunluğu ve temel bakım maliyeti ölçülecek. Hızlı kervan satışı ve art arda borçlanmanın sınırsız altın yaratmadığı, anlaşma durumunun kayıttan geri geldiği ve geç ödemelerin tekrar yazılmadığı doğrulanacak.

## Yerel banka denemesi

Proje kökünden Python ile `_yedek/devletler-rts-prototip/verify_npc.py --bank-only` çalıştırılır. Bu Minecraft laboratuvarında Kurallı Diplomasi’nin ayrı banka veri paketi kullanılır. Test betiği mevcut Java, Fabric ve HYW dosyalarıyla çalışır.

- **Kredi.** Devlet 8 altın alır ve toplam 10 altın öder. Her 72 saniyede en fazla 2 altın yeni taksit olarak vadeye gelir ancak geciken miktar kalan toplam borcu aşmaz.
- **Ödeme.** Önce o dönemin gelirinden 2 altınlık temel ihtiyaç payı ayrılır. Kalan dönem gelirinin yarısı vadesi gelen borca ayrılabilir. Erken ödeme de kasada 2 altınlık ihtiyaç payı bırakır. Oynanış fiyatları ilk deneme değerleridir.
- **Gecikme.** İlk gecikmede istikrarsızlık 1 artar. Devlet zaten istikrarsızsa gecikme 2 puan ekler. Ölçek 6’da durur ve bu düzeyde yeni savaş açılamaz.
- **Toparlanma.** Borç kapandıktan sonra depodaki bir ekmek halka dağıtılarak istikrarsızlık 1 azaltılabilir. Başkent hasarlıysa onarım 2 altın karşılığı en fazla 4 can kazandırır ve istikrarsızlık 1 azalır.

Doğal Kurallı Diplomasi’de aynı kredi tutarları uygulanır. Dönem geliri, hazineye geçmiş belediye gelirini, maden teslimatlarını ve kervan ödemelerini kapsar. Halk yardımı, birlik ikmali ve kervan satışlarının kullandığı ortak erzak stoğundan düşer. [Doğal maç kılavuzu](../prototypes/devletler-rts/README.md)
