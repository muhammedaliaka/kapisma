# Devletler RTS · Minecraft 1.21.1 Fabric

Devletler RTS, 2–8 kişinin ayrı devletleri tepeden yönettiği bir Kapışma oyunu. Her devlet bir milis, bir okçu, 6 altın ve bir başkentle başlar. Yayımlayıcının Hundred Years Warfare (HYW) modu kamera, birlik seçimi ve emirlerini sağlar. Kapışma veri paketi maçı yönetir. Sunucuya özel Kapışma RTS Rules JAR’ı Diplomasi’deki savaş kurallarını HYW hasarına ve ilişkilerine uygular. [Hundred Years Warfare](https://modrinth.com/mod/hundred-years-warfare)

## Arkadaş maçı

1. Her oyuncu Minecraft **1.21.1**, Fabric Loader **0.20.1**, Fabric API **0.116.18+1.21.1** ve HYW **0.8.0r-fix2-1.21.1-fabric** kullanır. Oyuncu profiline yalnızca Fabric API ve HYW JAR’larını koy. [HYW 1.21.1 sürümü](https://modrinth.com/mod/hundred-years-warfare/version/H1vcDdXt)
2. Herkes Tailscale üzerinden hosta erişebilmeli. Host, `_yedek/devletler-rts-prototip` içinden `baslat-tailscale-classic.cmd` ile Klasik’i ya da `baslat-tailscale-diplomacy.cmd` ile Diplomasi’yi açar. Minecraft adresi konsolun üstünde gösterilen Tailscale IPv4 adresi ve `25576` portudur. Hesap doğrulaması açıktır. Aynı bilgisayardaki ayrı yerel başlatıcı `baslat-prototip.cmd` ve adres `127.0.0.1:25576`. [Tailscale IP adresleri](https://tailscale.com/docs/concepts/tailscale-ip-addresses)
3. Host oyuncu bağlanınca konsola `tag <oyuncu> add krts.host` yaz. Oyuncu sohbetten `/trigger krts_menu` ile maç düğmelerini açar. 2–8 aktif oyuncu hazır olunca **Başlat** seçilir. Menüden takviye ve Diplomasi seçenekleri de açılır.
4. HYW kılavuzu ve kumanda asası maç başında verilir. HYW **Toggle RTS Mode** tuşu varsayılan **F4**. Minecraft Kontroller menüsünde **RTS** bölümünden değiştirebilirsin. Kamera görünümünde sol tıkla birlik seç, **Ctrl+A** ile kontrol edebildiğin birlikleri topluca seç, sağ tıkla hareket emri ver. Saldırı için **A** tuşuna basıp hedefe tıkla. Çizgi formasyonu için sağ tuşu basılı tutarak bir çizgi sürükle.
5. Sunucuyu kapatmak için konsola `stop` yaz. Çok oturumlu maça dönerken aynı başlatıcıyı aç ve en az iki kayıtlı oyuncu bağlanınca **Devam** seç. Başka kurallarla yeni oyun açmak için bitmiş maçı önce `function krts:reset` ile sıfırla.

Yerel ve Tailscale başlatıcıları aynı RTS dünyasını kullanır. İlk başlatıcının konsolu açıkken ikinci başlatıcı veya profil hazırlama işlemi dosyaları değiştirmeden durur.

## Doğal haritada Başkent maçı

1. Aynı Fabric istemci profiliyle bağlan. Host, `_yedek/devletler-rts-prototip` klasöründeki `baslat-dogal-yerel.cmd` başlatıcısını kullanabilir. Arkadaş maçı için `baslat-dogal-tailscale-classic.cmd` veya `baslat-dogal-tailscale-diplomacy.cmd` aynı protokol ve portu kullanır.
2. Konsoldan `tag <oyuncu> add krts.host` ver. Oyuncular doğal dünyanın başlangıç çevresine geldikten sonra host `/trigger krts_menu` içinden **Kuruluşu başlat** seçeneğini seçer. Menü, herkes için doğrulanmış bir bölge bulabilirse kuruluş başlar.
3. Üç dakika içinde bölge, köken ve profil seç. Diğer oyuncular aynı kökeni ve profili alabilir. Ayrılmış bölgeler başkasına verilmez. Konum seçmeyen devletler boş bölgelerden birini alır. Köken ve profil seçmeyenler Köylüler ve Tarım ile başlar.
4. Her devlet, iki birliğiyle kendi yükseltisindeki belediyede başlar. Belediye malzemeleri kökene göre değişir ve bayrağın rengi devleti gösterir. HYW kumanda asasıyla birliklere emir ver. Takviye menüsü, belediyenin yanına yeni milis veya okçu yerleştirir. Son ayakta kalan başkent maçı kazanır.

Doğal başlatıcılar `world-rts-natural` kaydını kullanır. İlk harita önceden taranan `5743893257777525637` seed’iyle açılır. Belediyeler yaklaşık 11 blok genişliğinde, kuleli ve devlet bayraklıdır. Model başkent çevresindeki açıklığa oturur, temel zemine göre uyarlanır. Saldırı hedefi aynı kuruluş bölgesindeki **Başkent** birimidir. Takviye ve belediye geliri bu profilde çalışır.

### Devlet profilleri

Kuruluşta her profilin etkisi seçim düğmelerinin altında görünür. Cevher ve tarla sürelerine işçinin yürüyüşü eklenir.

| Profil | Avantaj | Bedel |
| --- | --- | --- |
| **Tarım** | Tarla işi 24 yerine 18 saniye. | Demir ve altın cevheri işi 3 yerine 4 saniye. |
| **Madencilik** | Demir ve altın cevheri işi 3 yerine 2 saniye. | Tarla işi 24 yerine 30 saniye. |
| **Ticaret** | Tamamlanan her kervan siparişi 1 ek altın öder. | Yeni karakol 1 ek kırık taş tutar. |
| **Savunma** | Yeni karakol 46 cana sahip. | En fazla iki karakol kurulabilir. |
| **Keşif** | İşçinin çalışma yarıçapı 16 yerine 24 blok. | Yeni karakol 30 cana sahip. |

Her devletin bir işçisi var. Otomasyon başlangıçta kapalıdır. Kendi oyuncusu `/trigger krts_menu` üzerinden odun veya kırık taş görevini açabilir ve durdurabilir. İşçi belediyenin yakınında erişebildiği doğal kaynağa yürür ve malzemeyi depoya geri taşır. Gövde başına maçlık odun kotası 2, kaya başına kırık taş kotası 1. Depo her malzemeden 12 alır. İşçi açık kayaya ulaşamazsa yürüyebildiği zeminin en fazla dört blok altındaki doğal taşı arar. Ağaç ve kaya blokları yerinde kalır.

İşçiye elle emir vermek için menüde **Gövde seç** veya **Kaya seç** düğmesine bas ve HYW tepe kamerasından kaynağa sağ tıkla. Kırık taş için sığ damarın üzerindeki zemini de seçebilirsin. Bu seçim otomasyonu kapatır. İşçi geçerli rotada malzemeyi belediyeye taşır ve sağ tık normal birlik komutuna döner. **İş emrini iptal et** işçiyi durdurur.

### Demir ve talim

- **Demir toplamayı aç** işçiye belediyenin 16 blok çevresinde, Keşif profilinde 24 blok çevresinde, yüzeyin 12 blok altına kadar erişilebilir cevher aratır. **Demir damarı seç** ile tepe kamerasından görünen bir cevheri işaretleyebilirsin. Hem normal hem derin kayrak demir cevheri geçerli. Her cevher bloğu maç boyunca bir demir verir ve yerinde kalır. Depo sekiz demir alır.
- İşçi demiri belediyeye yürüyerek teslim eder. Ulaşılabilir cevher yoksa **Demir al · 3 altın** aynı depoya bir demir ekler. Bu işlem işçi otomasyonu kapalıyken de kullanılabilir.
- **Talimi aç**, yakındaki boşta askeri doğal zeminde bir toplanma noktasına gönderir. Her seviye tamamlandığında 1 demir ve 2 altın harcanır. En fazla üç seviyenin her biri 2 azami can ve 0,5 saldırı gücü ekler. Okçunun menzilli hasarı da artar. Her oyuncu kendi talimini işçiden bağımsız açıp kapatır.

### Talim Ocağı

1. **3 odun, 2 kırık taş ve 2 demir** biriktir. **Talim Ocağı yeri öner** veya bağlı toprağında 9×7 bir açıklık seç. Tepe kamerasından **Talim Ocağı kur** ile hedefi işaretle. Devlet başına bir ocak kurulabilir.
2. Talim açıkken yaklaşan boşta birlik, güvenli avluda **12 saniyede** bir seviyeyi tamamlar. Bir seviyenin bedeli 1 demir ve 2 altın. Belediyedeki talim 20 saniye, Talim Doktrini açıksa 15 saniye sürer.
3. Ocağın bağlantısı koparsa avluda hızlı eğitim durur. Daha önce alınan seviyeler birliklerde kalır. Saldırı hedefi **Talim Ocağı** adlı 34 canlı muhafızdır.

### Altın ve kervan siparişleri

- **Altın toplamayı aç** işçiye belediyenin 16 blok çevresinde, yüzeyin 32 blok altına kadar açık cevher aratır. **Altın damarı seç** ile görünen bir cevheri tepe kamerasından işaretleyebilirsin. Her normal veya derin kayrak altın cevheri blok başına maçlık 1 altın verir. Cevher yerinde kalır. Altın işçi belediyeye ulaştığında yazılır. Hazine tam o sırada dolmuşsa teslim edilen altın belediyede bekler.
- İlk kervan siparişi maç başladıktan 30 saniye sonra belediye menüsüne düşer. Teklif 45 saniye açık kalır. Sonraki ziyaret 30 saniye sonra gelir. Her devlet ziyaret başına bir satış yapabilir. Menüde kalan süre ve ödeme görünür. Satış için yeterli kaynak ve 60 altınlık hazinede yer gerekir.
- İlk denge ayarında kervan sırayla 4 oduna 3 altın, 4 kırık taşa 3 altın, 2 erzağa 4 altın ve 2 demire 4 altın teklif eder. Teslimat, kaynağı devletin deposundan çeker ve ödemeyi hazineye ekler. Maç durakladığında teklif süresi de durur.

### Hazine ve banka

- Doğal **Kurallı Diplomasi** maçında **Hazine ve banka** düğmesi borcu, geciken miktarı, dönem gelirini ve sonraki vadeye kalan süreyi gösterir. İlk denge ayarında tarafsız banka devlet başına bir açık kredi verir. **8 altın** alınır ve **10 altın** geri ödenir. Her **72 saniyede 2 altın** vadeye girer.
- Belediye geliri, hazineye ulaşan maden altını ve tamamlanan kervan satışları dönem gelirine eklenir. Vade gününde 2 altın temel ihtiyaç payı ayrılır ve kalan dönem gelirinin en fazla yarısı ödemeye gider. Otomatik ve erken ödemeler hazinede en az 2 altın bırakır.
- Vadesi geçen taksit istikrarsızlığı artırır. İlk gecikme 1, zaten istikrarsız devlette 2 puan ekler. **6 puanda** yeni savaş ilanı kilitlenir. Borç kapandıktan sonra belediye deposundan 1 erzak dağıtmak 1 puanı düşürür. Hasarlı belediyeyi 2 altına onarmak en fazla 4 can ve 1 puanlık toparlanma sağlar.
- Borç, erzak ve vade sayacı çok oturumlu maçta saklanır. Maç duraklatıldığında vade ilerlemez.

### İttifak ve yardım

- Doğal **Kurallı Diplomasi** maçında **İttifak kur** seçeneği diğer devlete bir teklif yollar. Karşı taraf kabul ettiğinde ittifak kurulur ve taraflardan biri bozuncaya ya da eleninceye kadar sürer.
- Müttefikine her işlemde **2 altın**, **1 erzak** veya **1 demir** gönderebilirsin. Gönderenin stoğu ve alıcının depo kapasitesi yeterli olmalı. Borçlu devlet altın yardımı yaparken temel ihtiyaç için 2 altın bırakır.
- Etkin savaşta **Savaş çağrısı** ile katılmaya uygun bir müttefikini davet edebilirsin. Müttefikin 30 saniye içinde kabul ederse kendi savaş bildirimi başlar. Reddederse 1 istikrarsızlık alır. Aynı müttefike aynı savaş için bir çağrı gönderilebilir. Cevapsız kalan çağrı süresi dolunca kapanır.
- **İttifakı boz ve savaş ilan et** seçeneği olağan savaş bedelini alır, iki devlet arasındaki ittifakı ve diğer anlaşmaları sona erdirir ve 3 istikrarsızlık ekler. En ağır istikrarsızlık düzeyinde bu işlem kilitlidir.

### Karakol

1. Depoda **3 odun ve 2 kırık taş** biriktir. Ticaret profili 3 kırık taş kullanır. Menüden **Karakol yeri öner** ile koordinat alabilir veya bir açıklık seçebilirsin.
2. **Karakol kur** düğmesine basıp tepe kamerasıyla zemine sağ tıkla. Karakol bağlı toprağının dışına bitişik olmalı. Üsleri, diğer yapıları ve başka devletlerin bölgelerini aşan yerleşimler reddedilir. Zeminde 7×7 açıklık gerekir.
3. Devlet başına en çok üç karakol kurulabilir. Savunma profili iki karakol kurabilir. Belediye ve bağlı karakollar bir alan oluşturur. Bağlantıdaki bir karakol yıkılırsa arkasındaki karakollar kalır ve bağlantı yeniden kurulana kadar toprak kazandırmaz. Saldırı hedefi **Karakol** adlı muhafız birimidir.

### Mahalle

1. Depoda **4 odun ve 3 kırık taş** biriktir. Menüde **Mahalle yeri öner** ile 9×7 doğal bir açıklık bulabilir veya kendi yerini seçebilirsin.
2. **Mahalle kur** düğmesine bas ve HYW tepe kamerasından zemine sağ tıkla. Merkez belediyeye bağlı toprak içinde kalmalı. Yapı rakip alana ve başka yapılara taşamaz.
3. En fazla iki mahalle kurulabilir. Bağlı her mahalle birlik sınırını **3** artırır. Bağlantı koparsa kapasite askıya alınır ve bağlantıyla birlikte geri gelir. Mahalle yıkılırsa ek kapasite kalkar. Elde bulunan birlikler kalır. Birlik sayısı yeni sınırın altına inene kadar takviye alınamaz. Saldırı hedefi avludaki **Mahalle** adlı muhafızdır.

### Tarla ve erzak

1. Devlet başına belediye deposunda **2 başlangıç erzağı** bulunur. **Erzak dağıtımını aç**, belediyenin 10 blok çevresindeki dinlenen yaralı askerlerini stoktan iyileştirir. Bir erzak 4 can verir. Saha Tıbbı bunu 6'ya çıkarır. Son hasardan sonra 8 saniye ve aynı birliğin iki ikmali arasında 15 saniye geçmesi gerekir. Düşman yaklaşınca dağıtım durur.
2. İlk denge ayarında **3 odun ve 2 kırık taş** biriktir. **Tarla yeri öner** veya kendi bağlı toprağında 7×7 doğal bir açıklık seç. Çiftçinin ambardan ekin sırasına yürüyebileceği zemin gerekir. **Tarla kur** düğmesiyle hedefi tepe kamerasından işaretle. Devlet başına bir tarla kurulabilir.
3. **Çiftçiyi çalıştır** üretimi başlatır. İşçi ekin sırasına yürür, tarlayı işler ve ürünle ambara döner. İlk ayarda ürün başına iş süresi 24 saniye, Tarım profilinde 18, Madencilik profilinde 30 saniye. Yürüyüş süresi buna eklenir. Depo en fazla 8 erzak alır. Ekinlerin büyüme görünümü değişirken doğal zemin yerinde kalır.
4. Çiftçi ve erzak dağıtımı ayrı ayrı açılır. Tarla bağlantısı koparsa veya maç duraklarsa üretim bekler. Tarla yıkıldığında eldeki belediye stoğu kalır. Saldırı hedefi tarladaki **Tarla** muhafızıdır.

| Konsol komutu | Sonuç |
| --- | --- |
| `function krts:start` | Hazırlığı başlatır. |
| `function krts:status` | Maç aşamasını ve kalan saniyeyi gösterir. |
| `function krts:pause` | Birlik yapay zekâsını, süreyi, altını ve takviye alımını durdurur. |
| `function krts:resume` | En az iki kayıtlı ve aktif oyuncuyla son aşamadan sürer. |
| `function krts:reset` | Başkent ve birlikleri kaldırır. İzleyiciler lobiye geri döner. |

## Oyun kuralları

- **Ortak kurallar.** Üsler 192 blok genişliğindeki düz arenanın merkezine yaklaşık eşit uzaklıktadır. Tek bir devlet merkezde birlik tutuyorsa saniyede 1 puan ve 1 altın alır. Merkez çekişmeliyse bu gelir durur. Çevrimiçi ve aktif devletler ayrıca varsayılan olarak 12 saniyede 1 altın alır. Milis 3, okçu 4 altın. Başlangıç birimleri dahil devlet başına varsayılan 12 birim bulunabilir. Oyuncunun eylem çubuğu başkent canını, altını ve süreyi gösterir.
- **Klasik.** Hazırlık bitince rakip ordular savaşabilir. Oyuncular aralarında konuşarak strateji kurabilir. Varsayılan 60 canlı başkent yıkılınca devlet elenir, kalan birlikleri kaldırılır ve oyuncu izleyiciye geçer. Son ayakta kalan başkent kazanır. Süre dolduğunda birden fazla başkent kalırsa veya tüm başkentler aynı kontrol aralığında yıkılırsa maç berabere biter. Host isterse puanla biten `regions` zafer kuralını seçebilir.
- **Diplomasi.** Devletler tarafsız başlar. Savaş ilanı varsayılan olarak 4 altın ve 15 saniye bildirim gerektirir. Savaş ilk 30 saniye boyunca sürer. Sonrasında iki taraf kabul ederse 45 saniye ateşkes uygulanır. Aynı iki devlet 60 saniyelik ticareti, 100 saniyelik saldırmazlıkla veya ateşkesle birlikte yürütebilir. Her anlaşmanın ayrı teklifi ve süresi vardır. Ticaret her gelir döngüsünde iki ortağa da 1 ek altın kazandırır. Savaş bildirimi ve savaş sırasında ticaret kapalıdır. İki devlet arasında geçerli bir anlaşma varken savaş ilanı verilemez. Bir devlet elenirse ona ait anlaşmalar kapanır.
- **Teknoloji.** Diplomasi’de **Lojistik** 8 altına açılır ve her gelir döngüsünde 1 ek altın verir. Ardından 9 altına **Saha Tıbbı** açılabilir. Bu araştırma bir ekmeğin yenilediği canı 4’ten 6’ya çıkarır. **Okçuluk** 7 altına açılır ve yeni okçu üretimini etkinleştirir. Ardından 9 altına **Talim Doktrini** açılabilir. Doktrin talim süresini 20 saniyeden 15 saniyeye indirir. Başlangıç okçusu her iki kuralda da verilir. `/trigger krts_menu` içindeki **Teknoloji** düğmesi araştırma bedellerini ve önkoşulları gösterir.
- **Oturum türü.** `persistent` ayarında aktif maç sunucu açılınca duraklatılmış hâlde bekler. `single` ayarında sunucu yeniden başladığında önceki maç kapanır. İki ayarda da maç sırasında çevrimiçi ve aktif katılımcı ikinin altına düştüğünde saat durur. Sonradan gelen kayıtsız oyuncular maç sürerken izler.
- **Komuta yetkisi.** Maç aktifken her oyuncu kendi devletinin birliklerine emir verebilir. Hazırlıkta, duraklatmada ve izleyici modunda emir uygulanmaz. Başkent yalnız savunulacak hedeftir. HYW’nin paylaşımlı kontrol ayarı devletler arasında birlik komutası vermez.

## Düz arenada akıllı NPC’ler

Klasik ve Diplomasi’de oyuncu `/trigger krts_menu` ile yalnız kendi devletinin Akıllı NPC ayarını yönetir. **Aç** ve **Kapat** otomasyonu yönetir. **Demir**, **Talim** ve **Erzak** ayrı ayrı veya birlikte seçilebilir. Yeni maç kapalı başlar. Host da katılımcı olduğunda kendi devletinin ayarlarını değiştirebilir.

- **Madenci** açıldığında üs yanında bir atölye kurulur. İşçi depodan kazmayı alır, ulaşabileceği demir damarına yürür ve ham demiri fırına taşır. Fırından çıkan külçeyi depoya getirir. Kazma kırılırsa depodaki yedeği kullanır. Depo sekiz demire ulaşınca üretimi bekletir. Tükenen damarlar işçi aktifken yenilenir. Kaybolan bir işçi menüden 4 altına yeniden alınabilir.
- **Talim** önce düşük seviyeli, uygun bir askeri seçer ve yürüyerek toplanma noktasına gönderir. Aynı anda bir asker çalışır. İlk nokta kapalıysa yedek nokta kullanılır. Oyuncu emri, üs savunması veya yakındaki düşman talimi durdurur. Asker yeniden uygun olduğunda kaldığı yerden devam edebilir. Her seviye varsayılan olarak 20 saniye sürer. Seviye tamamlandığında 1 demir ve 2 altın düşülür. Üç seviyeye kadar her seviye 2 azami can ve 0,5 saldırı gücü ekler. Okçunun menzilli hasarı da artar. Madenciliği kapalı tutan oyuncu **Talim demiri al** ile kendi atölyesine 3 altına 1 demir ekleyebilir.
- **Erzak** seçildiğinde çiftçi buğday hasat ederek yeniden eker. Her üç buğday bir ekmek üretir. Erzak deposu sekiz ekmek tutar. Çiftçi yakın çevrede en çok can kaybetmiş, ulaşabileceği ve boşta duran kendi askerine öncelik verir. Bir erzak varsayılan olarak 4 can yeniler. Aynı birlik tekrar erzak almak için 15 saniye bekler. Kaybolan çiftçi menüden 3 altına yeniden alınabilir.
- **Savunma** ayrı bir oyuncu tercihidir. **Üssü koru** boşta olan bir birliği üs yanındaki mevziye yollar. **Devriye** birliği üs çevresindeki dört noktada dolaştırır ve kapalı noktaları atlar. Birlik oyuncudan emir aldığında, bekle emrindeyken veya çatışırken otomatik rota seçilmez. Düşman birliği üs merkezine 16 blok yaklaşınca menüde uyarı görünür. Diplomasi’de bu uyarı yalnız savaş başladıktan sonra açılır. **Savunmayı kapat** ve **Üretim ve talimi kapat** ile iki görev grubu ayrı yönetilir.
- **Kayıt** çok oturumlu maçta oyuncunun tercihini, depoları, ekipmanı ve talim seviyesini korur. Duraklatmada işçiler ve devriye durur, talim ilerlemez. O anda yanmakta olan fırın Minecraft’ın kendi süresini kullanır.

## Host ayarları ve hazırlama

Doğal dünya, kuruluş seçimleri ve yeni ekonomi için onaylanan kararlar ayrı tasarım notunda tutulur. [Devletler RTS oyun tasarımı](../../docs/DEVLETLER_RTS_TASARIMI.md)

`rules.json` içinde hazırlık ve maç süresini, oturum türünü, zafer kuralını, başkent canını, ekonomi ve birim sınırlarını değiştirebilirsin. Diplomasi grubunda savaş bedeli, bildirim, zorunlu savaş süresi, anlaşma süreleri, teklif süresi ve teknoloji bedelleri ayrı ayarlanır. Bir maçı sıfırlayıp sunucuyu kapattıktan sonra değişiklikleri uygula.

```powershell
py -3 prototypes/devletler-rts/prepare.py
```

Hazırlayıcı, Java 21 ve Python 3.11 veya üzeri kullanır. Önceden başlatılmış Fabric 1.21.1 / Loader 0.20.1 çalışma profilini `--runtime <klasör>`, yayımlayıcının HYW JAR’ını `--warfare <dosya>` ile gösterebilirsin. Betik HYW dosyasının SHA-512 özetini doğrular ve ayrı RTS profilini üretir. HYW yayımlayıcısının bağımlılığıdır ve kendi lisansıyla edinilir.

HYW’nin doğal yapı üretimi, rastgele asker parşömenleri, genel asker alımı ve kuşatma yıkımı bu profilde kapalıdır. Vanilla yaratık üretimi ve oyuncular arası doğrudan vuruş da kapalıdır. Başkent ve birlikler HYW savaş sistemiyle çalışır.

## Doğrulama

- Doğal Klasik ve Kurallı Diplomasi’de profil etkilerini, kervan ödemesini ve Talim Ocağı’nı sınamak için proje kökünden `py -3 _yedek/devletler-rts-prototip/verify_npc.py --specialization-proof` çalıştır. Yerel laboratuvar Java 21, kurulu RTS profili, Minecraft 1.21.1 Fabric istemcisi ve Carpet test bağımlılıklarını kullanır.
- Botlu başkent maçlarında doğal milis hasarı, başkent yıkımı, elenme, izleyiciye geçiş, beraberlik ve son başkent zaferi çalıştı.
- Klasik senaryosunda host yetkisi, merkezden puan, çekişmeli merkez, ücretli milis üretimi ve başkent zaferi doğrulandı. Üç devletli Diplomasi senaryosu, ticaretin saldırmazlıkla ve ateşkesle birlikte yürüdüğünü doğruladı. Ayrı süreler, ortaklara özel gelir, eski teklif ve anlaşma aktarımı, elenme temizliği ve süresi dolan kabul düğmesi sınandı. Tarafsızlıkta ve savaş bildiriminde hasar engellendi, savaşta açıldı. Zorunlu savaş süresi ve Okçuluk da geçti.
- Sunucunun HYW komuta denetimi hazırlık, aktif maç, duraklatma, devam ve izleyici durumlarında sınandı. Aktif maçta yalnız birlik sahibi izin aldı. HYW üzerinden paylaşımlı kontrol verilmiş rakip ve kendi başkentini seçen oyuncu reddedildi.
- NPC testinde hostun kendi düğmesi diğer oyuncunun ayarını değiştirmedi. İşçi kapalı yolu atladı, kazması aşındı, fırından bir demir alıp depoya taşıdı ve tükenen damar yenilendi. Milis talim noktasına yürüdü ve bekle emrinden sonra ilerlemesini korudu. Seviye tamamlanınca tam 1 demir ve 2 altın harcandı, milisin azami canı 20’den 22’ye yükseldi. Oyuncu seçimi ve talim seviyesi sunucu yeniden açıldığında geri geldi.
- Diplomasi erzak denemesinde çiftçi gerçek buğday hasadından ekmek üretti. Yakında iki devletin yaralı milisleri varken kendi devletinin milisine ulaştı ve canını 10’dan 14’e çıkardı. Rakip milis 10 candaydı. Erzak ayarı maç sırasında kapatıldı ve sıfırlama çiftliği temizledi.
- Teknoloji denemesinde iki araştırmanın önkoşulları ve altın bedelleri doğrulandı. Saha Tıbbı bir ekmekle kendi askerinin canını 10’dan 16’ya çıkardı. Talim Doktrini 15’inci saniyede seviye verdi ve normal seviye maliyetini korudu.
- Savunma denemesinde devriye kapalı noktayı atladı ve açık rotanın turunu tamamladı. Oyuncunun bekle emri verilen asker görevden çıktı ve diğer boşta birlik mevziye yürüdü. Klasik’te rakip yaklaşınca uyarı açıldı. Diplomasi’de uyarı savaş başladığında açılıp ateşkesle kapandı. Duraklatma devriyeyi durdurdu.
- Sekiz botlu maçta sekiz devlet ve 16 başlangıç birimi oluşturuldu. Önceki çok oturumlu testlerde birlikler, başkent hasarı, puan ve altın yeniden başlatmadan sonra korundu.
- Tek oturumlu maçta veri paketi yeniden yüklendiğinde maç kaldığı yerden sürdü. Sunucu kapatılıp yeniden açıldığında tek oturum temizlendi. Çok oturumlu maçın açılışta durakladığı ve yalnız iki kayıtlı oyuncu dönünce host komutuyla sürdüğü doğrulandı.
- Windows başlatıcıları doğrulandı. Tailscale profili TCP 25576’yı Tailscale IPv4 adresinde, yerel profil ise 127.0.0.1 üzerinde açtı. Hesap doğrulaması açık kaldı. İkinci başlatıcı, ilk sunucu hazır olmadan da engellendi. `stop` sonrasında yerel ayarlar geri yüklendi. Ayrı bir cihazdan arkadaş maçı henüz denenmedi.
- İki gerçek Minecraft istemcisi yerel Klasik maçına aynı anda bağlandı. Birinci istemci tepeden kamera açtı, kendi okçusunu seçip merkeze gönderdi ve puan aldı. Kadrajda rakip okçu varken toplu seçim yalnız birinci oyuncunun okçusunu aldı. Başkente istemciden verilen saldırı emri hedefi yıktı, rakip elendi ve kazanan iki istemciye de bildirildi. Kontrollü deneme için başkent kamera alanına yaklaştırıldı ve başlangıç canı 14 yapıldı.
- İkinci denemede gerçek istemci bir milisle bir okçuyu birlikte seçip hareket ettirdi. Otomatik bir Minecraft istemcisi denemesinde HYW’nin yerleşik sürükleme emri ağa gönderildi. Sunucu iki birime aynı `dragged` formasyon kimliğini ve ayrı mevzi sıraları verdi. Milis ön sırada, okçu yaklaşık bir blok geride durdu. Fiziksel fare sürüklemesinin uçtan uca teyidi ve iki cihazdan karşılıklı komuta açık. İlk maçtan sonra aynı Windows makinesindeki ikinci pencere öne alınırken GLFW yerel kodunda çöktü. Minecraft sunucusu ilk istemciyle çalışmaya devam etti.
- 0.7.0’da `laboratuvar/verify_formation.py --with-npc`, talim ve devriye etkin durumdayken gerçek Minecraft istemcisinden HYW’nin yerleşik formasyon emrini gönderdi. İki asker hâlâ üs yakınındayken otomatik görevlerini bıraktı ve aynı formasyonda farklı mevzilere ulaştı. İstemci ve sunucu kayıtlarında hata satırı yoktu. Bu denemede formasyon emri istemcide otomatik tetiklendi.

## Başlatmadan önce kontrol (26 Kasım 2026)

Windows PowerShell ile proje kökünden:

```powershell
powershell.exe -NoProfile -File prototypes/devletler-rts/verify_launcher.ps1
```

Betik Java 21 seçimini, gerekli dosyaları, arşiv yapısını, profil kilidini ve port çakışmasını denetler; Minecraft'ı başlatmaz, dünya kaydına yazmaz. Tailscale kapalıysa yalnız yerel denetim için `-SkipTailnet` eklenebilir. Kurulu başlatıcı Java 8 veya yanlış port/dünya ayarıyla sunucu başlatmak yerine hata verir. Başlatıcı kabulü 0.7.0 adaptör hash'i değişmeden geçti. Gerçek iki cihazlı oyun ve fiziksel fare sürükleme ayrı kabul adımlarıdır.
