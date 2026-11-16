# Kapışma — Kullanım Rehberi

UHC, Tombala (Bingo), Manhunt, Katil Kim. En fazla 8 oyuncu. Minecraft 1.21.1 + Fabric. **Herkes sunucu kurabilir**: Windows, Linux, Mac ve Android tablet (Termux).

## 0. En kolay yol: kurulum sihirbazı (Windows)
[`dist/`](dist) içindeki **`kapisma-kurulum-vX.zip`** dosyasını indir, klasöre çıkar, **`Kapisma-Kurulum.bat`**'a çift tıkla. Açılan pencerede iki düğme var:
- **Oyuncuyum:** modları doğru `.minecraft\mods` klasörüne kurar (eskileri silmez, `_kapisma_eski`'ye taşır), Fabric 1.21.1 sürümünü (yoksa) ekler, resmi launcher için profil ekler, sesli sohbet modunu SHA-512 doğrulamalı indirir, istersen sunucuyu "Çoklu Oyuncu" listesine yazar. Sonra TLauncher → Fabric 1.21.1 → Oyna.
- **Sunucu kuracağım:** sunucuyu istediğin klasöre kurar, **oyuncu adlarını beyaz listeye yazar** (kimse başkasının adıyla giremez), RAM seçtirir, EULA onayını sorar, güvenlik duvarını açar, sunucuyu başlatır ve arkadaşlarının yazacağı adresi gösterir.

Sihirbaz çalışmazsa (PowerShell kapalı/engelli) aşağıdaki elle yollar aynı işi yapar. Linux/Mac/Android için `start.sh` (aşağıda).

## 1. Sunucuyu kur ve aç (host)
Elle yol — hazır paket: [`dist/`](dist) klasöründeki **`kapisma-sunucu-vX.zip`** (GitHub'da dosyaya tıkla → **Download raw file**; varsa [Releases](../../releases) sayfasından da inebilir). Kaynaktan derlemek için bkz. §5. Bütünlük: `dist/SHA256SUMS.txt`.

| Cihaz | Nasıl |
|---|---|
| **Windows** | Zip'i klasöre çıkar → `start.bat`'a çift tıkla (EULA'ya `E` de) → bir kere `guvenlik_duvari_ac.bat` → `adres_goster.bat` adresi gösterir |
| **Linux / Mac** | `unzip kapisma-sunucu-*.zip && cd kapisma-sunucu && bash start.sh` |
| **Android tablet** | F-Droid'den **Termux** → `pkg install -y openjdk-21 curl unzip` → zip'i Termux ana klasörüne kopyala/aç → `bash start.sh` (ayrıntı pakettin `BENI_OKU.txt` dosyasında) |

`start.bat` / `start.sh` her şeyi kendisi halleder: Mojang EULA onayı, **Java 21** (yoksa indirir/kurar), sesli sohbet modu (Modrinth'ten, SHA-512 doğrulamalı), cihazın **RAM'ine göre bellek ayarı** ve Manhunt'ın "yeni dünya" yeniden başlatma döngüsü. İlk açılış 1–3 dk sürer (Mojang sunucu dosyaları indirilir). `Done` yazısı görününce hazır; kapatmak için konsola `stop`.

**Zayıf cihaz (tablet, eski dizüstü):** bellek ≤ 3,5 GB, ≤ 2 çekirdek ya da sınırlı ARM cihazda sunucu kendini **LITE** profile alır (görüş mesafesi 6, simülasyon 4, hile korumasının açma maliyeti düşük; oyun kuralları ve hile koruması aynı). `ram_mb.txt` (tek satır MB) ve `profil.txt` (`lite`/`normal`) ile elle değiştirilebilir. En az 4 GB RAM önerilir.

**Seed ve yeni dünya** artık Java içinde (Fabric `preLaunch`) yapılır; PowerShell/bash betiği gerekmez: ilk açılışta seed havuzundan kullanılmamış bir seed seçilir; Manhunt yeni dünya isterse sunucu kapanır, yeniden açılırken eski dünya silinip yeni seed yazılır.

## 2. Arkadaşlar nasıl bağlanır (Tailscale)
Sunucu internete açılmaz; sadece [Tailscale](https://tailscale.com) ağındakiler girebilir (korsan/TLauncher hesapları sunucuya `online-mode=false` ile girdiği için en güvenli yol budur). **Ad taklidi:** bu modda sunucu kimlik doğrulamaz, yani biri başkasının (ya da host'un) adıyla girebilir. Bunu kapatmak için sunucu klasöründeki **`izinli_oyuncular.txt`** dosyasına izinli adları yaz (satır başına bir ad, sen dahil; sihirbaz bunu kendisi yapar): sunucu açılırken beyaz liste otomatik açılır ve yalnızca bu adlar girebilir.

**Host:** Tailscale uygulaması → bu cihazın satırı → **Share…** → arkadaşın e-postası. (Paylaşım yalnızca bu cihazı gösterir.) `adres_goster.bat` Tailscale IP'ni (100.x.x.x) yazar.

**Arkadaş:**
1. Tailscale'i kurup (ücretsiz) giriş yapar, paylaşım davetini kabul eder.
2. `dist/` içindeki **`kapisma-istemci-vX.zip`** dosyasını indirir, çıkarır, `kurulum.bat`'a çift tıklar (Fabric API + Origins + `kapisma-hud` + Simple Voice Chat'i `.minecraft\mods`'a kurar; eskileri `_kapisma_eski`'ye taşır).
3. TLauncher → sürüm **Fabric 1.21.1** → Çoklu oyuncu → Sunucu ekle → hostun Tailscale adresi.
4. Oyunda **V** tuşu: mikrofon/ses ayarları.

Windows Güvenlik Duvarı bağlantıyı engellerse (zaman aşımı): `guvenlik_duvari_ac.bat` (oyun TCP 25565 + ses UDP 24454). Elle:
```
New-NetFirewallRule -DisplayName "Kapisma Oyun" -Direction Inbound -Protocol TCP -LocalPort 25565 -Action Allow
New-NetFirewallRule -DisplayName "Kapisma Ses" -Direction Inbound -Protocol UDP -LocalPort 24454 -Action Allow
```

## 3. Oyun nasıl başlar
- Sunucuya **ilk giren** oyuncu host olur (kuralları o ayarlar). Oyuncular lobide (boş bir dünyada yüzen aydınlık platform; altında dünya yoktur) toplanır.
- Menü: envanterdeki **Nether Yıldızı**'na sağ tık, ya da `/kapisma menu`.
- Ana menü sade: oyun modu (UHC / Tombala / Manhunt / Katil Kim), takım (Solo / Duo), **Ayarlar**, **Başlat**. Irk modu, yasaklı ırklar, kurallar, seed sınıfı ve host devri **Ayarlar** alt menüsündedir.
- Manhunt'ta menüde **Runner seç** düğmesi çıkar (`/kapisma runner <oyuncu|temizle>`). **Host devret** düğmesi hostluğu başkasına verir; host çıkarsa en eski oyuncu otomatik host olur.
- Oyun içi sohbet: normal yazı sadece takıma gider. `/tc <mesaj>` takım, `/herkes <mesaj>` herkes, `/takim <takım> <mesaj>` seçilen takım, `/ozel <oyuncu> <mesaj>` tek kişi. `/koord` koordinatını takıma gönderir. Elenenler sadece diğer seyircilere yazabilir. Lobide yazılan herkese gider.
- Takım rengi/adı: `/takimim renk <renk>`, `/takimim isim <ad>`, `/takimim sifirla` (menüde "Takımım").
- Yardım: `/yardim` (tıklanabilir komut listesi). Yanlış kullanılan komut doğru kullanımını yazar.
- Oyun bitince sonuç ekranı (sıralama, öldürme/ölüm, ırklar) sohbete yazılır.
- Oyunlarda **PvP** kuralı vardır (UHC, Tombala, Manhunt): kapatılırsa oyuncular birbirine vuramaz. Menü → Ayarlar → Kurallar ya da `/kapisma rule <oyun> pvp 0`.
- Komutlar: `/istatistik [oyuncu]`, `/puan`, `/parkur`, `/kapisma status`, `/kapisma start`, `/kapisma stop`, `/kapisma rules`, `/kapisma rule <oyun> <kural> <değer>`, `/kapisma ban|unban <ırk>`, `/kart` (Tombala kartı).

### Lobi: parkur ve puan
- Meydanın doğusundaki kemerden geçip yeşil platforma çıkınca **parkur** başlar: tek rota, 4 bölüm (KOLAY, ORTA, ZOR, ÇOK ZOR), altın platformlar checkpoint, bölüm sonları 5x5 beyaz platform. Rotadaki her zıplama fizik simülasyonuyla geçilebilir doğrulanmıştır (`tools/parkour_gen.py --check`).
- Düşersen son checkpoint'e dönersin. Çantadaki öğeler: yeşil boya = son checkpoint'ten devam, kırmızı boya = baştan başla, kapı = parkurdan çık (komutlar: `/parkur checkpoint|bas|cik`). Her bölüm sonunda aynı üç seçenek sohbette tıklanabilir çıkar.
- Puan: bölüm ilk bitirişte 10 / 20 / 40 / 80. Kapışma: katılım 5, öldürme 10, galibiyet 100 (en az 2 takım). İlk 3 meydandaki panolarda, kendi puanın `/puan` ile. Kayıt: `server\config\kapisma\scores.json`.
- **Tırmanma kuleleri** (her bölümde): merdivene ileri tuşuna basılı tutarak çık. **Kızıltaş köprüleri** (ORTA, ZOR, ÇOK ZOR): kızıl platformlar sırayla belirip kaybolur (bitmeden 0,5 sn önce cama döner). Önünde ve ardında checkpoint vardır; sırayı takip edip atlarsan geçilir. Zorluk: platform küçülür, açık kalma süresi kısalır, adım sayısı artar.
- Platform ve parkur boş dünyada olduğu için lobi çok az RAM kullanır; Manhunt dışında ana dünya kullanılmaz.

## 4. Modlar
| Mod | Sınır | Kazanma |
|---|---|---|
| UHC | ±1000 (kural: 200–1000), zamanla daralır, Nether kapalı | Son takım ayakta. Başlangıçta takımlar merkezden `scatterRadius` (varsayılan 500, en çok 700) içinde rastgele ve birbirinden uzak doğar |
| Tombala | ±1000 | **Herkes aynı noktada başlar (kıyak yok).** 5x5 kartta 3 çizgi (satır/sütun/çapraz) tamamlayan takım; lockout: eşyayı ilk alan takım sayar. Kartta eşya + başarı görevi karesi bulunur |
| Manhunt | Sınırsız, ana dünya (Nether/End açık) | **Herkes aynı noktada, aynı anda başlar.** Runner ejderhayı öldürürse runner, runner ölürse hunter'lar kazanır. Runner'ı host menüden seçer (seçmezse rastgele). Pusula Nether/End geçişinde de runner'ın son bilinen izini gösterir |
| Katil Kim | Sınırsız, ana dünya (Nether/End açık) | Masumlar **katil hepsini öldürmeden Ender Ejderhası'nı** öldürürse kazanır; katil tüm masumları elerse kazanır. 1 gizli katil (6+ oyuncuda 2'ye kadar), herkes aynı skin'de, isim etiketi yok. Herkes başta 200x200'lük alanda rastgele doğar |

### Tombala: ulaşılabilir eşya ve konum bilgisi
- Kart rastgele değil "bu 1000 blokluk alanda gerçekten bulunabilir" eşyalardan çıkar. Oyun başlarken dünya üreteci taranır (biyomlar + yapılar), resmî oyun verisinden (`tools/mcknow.py` → `knowledge.json`) hangi biyom/yapıda hangi blok, yaratık düşüşü, sandık eşyası olduğu bilinir; tarif zinciriyle üretilebilen eşyalar da dahil edilir. Alanda olmayan eşya (ör. kaktüs için çöl yoksa) karta girmez. End ve Nether eşyaları kart dışıdır.
- **Zorluk puanlaması (yıldız):** her eşya için bu seed'de "ne kadar uğraşla elde edilir" hesaplanır (`ItemCost`): kaynağın (blok/yaratık/sandık/balık tutma) en yakın biyom ya da yapıya **uzaklığı** (220 blok ≈ 1 dk), kazma seviyesi ve derinlik, yaratığın avlanma zorluğu, nadir düşüş/sandık şansı ve tarif zincirinin eklediği zorluk toplanır. Yapı malzemesi kolay olsa bile en yakın kaynağı uzaksa pahalı sayılır; demir/elmas gibi cevherler kazılarak elde edilir (ipeksi dokunuşla cevher bloğu almak sayılmaz). Kartta eşyanın açıklamasında ★ sayısı ve tahmini süre görünür. Kural `difficulty` (1 kolay, 2 normal, 3 zor) kolay/orta/zor eşya oranını belirler; kart aynı ailenin (renk/ağaç türü) tekrarını ve fazla alet/zırh/yemek yığılmasını engeller.
- **Görev kareleri** (kural `tasks`, 0–8, varsayılan 3): kartta eşya yerine başarı görevi çıkabilir (ok at, hayvan üret/evcilleştir, balık tut, köylüyle ticaret, lav kovası, obsidyen, büyüleme, uyku, kalkanla ok savurma, toz karda yürüme...). Sadece bu seed'de yapılabilir olanlar seçilir; oyun başında herkesin bu ilerlemeleri sıfırlanır; tamamlayan takım kareyi alır.
- Konum bilgisi (hile değil): altta bulunduğun **biyom** yazar; karttaki ipuçlarında "şu biyomlarda / sandıkta: …" bilgisi çıkar; kartta **Bölge biyomları** düğmesi bu alandaki biyom ve yapı türlerini listeler. Koordinat verilmez. Kural: `biomeInfo`.

### UHC / Tombala mini harita
- `kapisma-hud` modu olanlara sağ üstte arazisiz harita: beyaz = başlangıç sınırı, kırmızı = şimdiki sınır, yeşil = son sınır (UHC), sarı çizgili nokta = sen, renkli noktalar = takım arkadaşların. Altında koordinatın. `H` tuşu HUD'u gizler. Mod olmayanlara oyun başında 2048 blokluk biyom önizlemeli harita verilir. Kural: `minimap`.

### Katil Kim kuralları (oyun akışı)
- **Amaç:** masumlar katil(ler) hepsini öldürmeden Ender Ejderhası'nı öldürürse kazanır. Katil(ler) tüm masumları elerse kazanır. Oylama/toplantı yoktur: kimin katil olduğunu davranıştan, ses ve pusuladan çıkarırsın.
- **Başlangıç:** herkes ortak noktanın çevresinde **200x200 bloklık** alanda rastgele ve birbirinden uzak doğar (aynı noktada yığılma yok, kimseye kıyak yok).
- **Başlangıç koruması** (`graceSec`, 60 sn): kimse kimseye vuramaz; katile özel donma/etki yoktur, böylece kimse ele verilmez. Katil, diğer katilin adını sadece kendisine söylenen mesajdan öğrenir.
- **Ölüm:** katil bir oyuncu tarafından öldürülürse yeniden doğar (sınırsız). Masum öldürülürse elenir; "☠ X öldü. Kalan masum: n" yazılır, kimin yaptığı yazılmaz. Düşme/canavar/lav gibi doğal ölümde de masum elenir (kural `natureEliminates`; kapalıysa yeniden doğar).
- **Dürüst hasar:** masumlar birbirine vurabilir (varsayılan açık); kapatılırsa vuran kişi "hasar yok" görüp katili bulabilir (önerilmez). Oyuncu çıkarsa: tüm katiller 3 dk yoksa masumlar, tüm masumlar yoksa katil kazanır.
- **Mod uyarısı:** `kapisma-hud` modu olmayan oyuncunun skin'i gizlenmez; oyun başında herkese bu oyuncular uyarı olarak yazılır. En az 3 oyuncu gerekir, 5-6 önerilir.

### Başlangıç yerleri (özet)
| Mod | Nasıl doğulur |
|---|---|
| UHC | Takımlar `scatterRadius` (100–700, varsayılan 500) içinde rastgele, birbirinden uzak; sınırdan büyük olamaz |
| Katil Kim | Ortak noktanın ±100 bloğunda (200x200) rastgele |
| Tombala, Manhunt | Hepsi aynı noktada, aynı anda (kıyak yok) |

### UHC oyun içi ekran
Sağ ortada **yan panel** (kural `sidebar`): süre, PvP durumu/geri sayım, sınır yarıçapı (daralıyorsa hedefi de), kalan takım/oyuncu, **en çok öldürenler**. Altta eylem çubuğunda **merkez yönü oku + mesafe** (`Merkez: 340 ↗`; sınır dışındaysan kırmızı uyarı) ve kendi öldürme sayın. **Can göstergesi** (kural `showHealth`, UHC/Manhunt açık, Tombala kapalı): Tab listesinde ve isim etiketinin altında kalpler — doğal yenilenme kapalı UHC'de rakibin canını görmek için. Takım arkadaşın ölünce sana **ölüm konumu** yazılır (eşyaları orada). Katil Kim'de kimlik gizli olduğu için can gösterimi yoktur.

### Hile koruması ("halüsinasyon")
Kural `antiXray` (UHC, Tombala, Manhunt, Katil Kim; 0 kapalı, 1 cevher koruması, 2 tam koruma — varsayılan 2). Sunucu, istemciye giden dünya verisini değiştirir; oyun mantığı gerçek dünyayla çalışır:
- **Gömülü cevherler gizlenir:** havaya açık olmayan kömür, demir, bakır, altın, kızıltaş, lapis, elmas, zümrüt (ve Nether'de altın, kuvars, antik kalıntı) yerine taş görünür; gerçek yeri istemciye hiç gönderilmez.
- **Sahte cevherler (halüsinasyon):** gerçek cevherin yerine aynı bölümde başka, tamamen taşla çevrili yerlere sahte cevher konur. Sahte yerler her chunk için sabittir (iki kez istenip karşılaştırılarak ayıklanamaz). Sahteyi kırarsan taş çıkar.
- **Boşluk düzleştirme (mod 2, sadece Overworld):** yüzeyin 6 blok altındaki mağara/tünel boşlukları, yapı blokları (tuğla, tahta, ray, sandık, spawner...) düz taş gösterilir; sandık/spawner verisi pakete konmaz. X-ray ile mağara ya da gizli yapı görülemez.
- **Yaklaşınca açılır:** bağlı havada yürürken (~10 blok), blok kırınca ya da patlayınca gerçek hâli blok güncellemesiyle gönderilir. Normal oyuncu hiçbir fark görmez; mağaralar yürüdükçe açılır.
- Nether ve End'de boşluk gizleme yoktur (ufuk görüşü oyunun parçası); Nether'de sadece cevher koruması çalışır.
- Test: `/kapisma debug xray audit` (op): chunk paketini istemci gibi çözer, sızıntı/belirsizlik/açılma doğrular; `... status`, `... simulate`, `... tpcave`.
- Bilinen sınır: yaratıklar (ESP) ve ışık verisi korunmaz; yapı sandığı gibi şeyler yalnızca "yeraltında ve açığa çıkmamışsa" saklanır.

### Oyuncu hile koruması (`antiCheat`, UHC / Tombala / Manhunt / Katil Kim; varsayılan açık)
Oyun **başlayınca** devreye girer (lobide kapalı), sunucu tarafında çalışır:
- **ESP / wallhack / oyuncu radarı:** duvar, zemin ya da dağ gibi **tam opak blok** arkasındaki oyuncu, bakan oyuncunun istemcisine **hiç gönderilmez** (paket seviyesinde; istemci o oyuncunun var olduğunu bilmez). Köşeyi dönerken göz kırpma olmasın diye görüş hattı geniş payla (köşeden ~1 blok önce) ve 0,8 sn bekletmeyle hesaplanır; cam/yaprak/yarım blok/çit görüşü engellemez. Muaf: 8 blok yakın, takım arkadaşı, parlayan (final) oyuncu, seyirci. 144 bloktan uzaktakiler hiç gönderilmez. Maliyet: oyuncu çifti başına 4 tikte bir birkaç ışın; LITE profilde 8 tikte bir.
- **Vuruş denetimi:** menzil aşımı (normal erişim + 1,3 blok; vanilla sunucu +3 payı verir), katı blok arkasından vuruş ve aşırı tıklama (1 sn'de 18'den fazla) engellenir; olaylar sunucu günlüğüne `[koruma]` olarak yazılır.
- **Seed gizleme:** istemciye giden "hashed seed" uydurma bir değerle değiştirilir; hile istemcisi seed'i doğrulayamaz. Gerçek seed'i (ve bununla yapı/biyom konumlarını) istemci hiçbir yoldan öğrenemez.
- Komutlar (op): `/kapisma debug guard status|force 0/1/-1|probe <bakan> <hedef>|hit <bakan> <hedef> <adet>`.
- **Bu yapılamaz (dürüst sınırlar):** istemcide çalışan hileler sunucudan engellenemez — fullbright/gece görüşü, kendi ekranında freecam, damage/FPS göstergesi, otomatik totem/pot, yardımcı nişan (aimbot), Baritone gibi yol botları. Vanilla sunucu zaten uçmayı, aşırı hızı ve blok erişim menzilini denetler (`allow-flight=false`). Birine güvenmiyorsan oynatma ya da beyaz listeyi kullan; hile istemcisi bulunduğunda `ban` komutu var.

### Ses (sesli sohbet) her modda açık
- Sesli sohbet için sunucuya ve herkesin istemcisine **Simple Voice Chat 2.6.27 (Fabric 1.21.1)** kurulur (sunucu ve istemci paketinde hazır). Ses UDP **24454** portundan gider.
- **Her modda** (lobi, UHC, Tombala, Manhunt, Katil Kim) yakın mesafe sesi (48 blok) varsayılan olarak açıktır; Discord vb. gerekmez. Elenenler (seyirciler) sadece birbirini duyar, canlılar duymaz.
- **Takım ses kanalı** (kural `teamVoice`, Katil Kim hariç hepsinde varsayılan açık): takım arkadaşların mesafeden bağımsız seni duyar (UHC'de 500 blok açıdan bile). Grup "açık" türdedir: yakındaki herkesle normal konuşma da sürer. Manhunt'ta avcılar kendi grubunda, runner(lar) kendi grubundadır. Elle grup kurmak/katılmak istersen oyunda `G` tuşu.
- **Katil Kim:** grup yoktur (kimlik gizli). `voice` kuralı oyun içi sesi açar/kapar (varsayılan açık), `deepVoice` açıkken herkesin sesi aynı kalın tona çevrilir (kim konuştuğu anlaşılmaz), kapalıyken kendi sesin. SVC "konuşuyor" simgesi konumu gösterir: Katil Kim'de SVC ayarlarından simgeyi kapat.
- Sesli sohbeti olmayan/bağlanamayan oyuncuya girişten yarım dakika sonra kısa yardım yazılır.
- Windows güvenlik duvarı (yönetici PowerShell): `New-NetFirewallRule -DisplayName "Kapisma Voice" -Direction Inbound -Protocol UDP -LocalPort 24454 -Action Allow`

## 4b. Testler
`bash tools/run_all.sh` canlı sunucuya dokunmadan `server_test\` kopyasında (port 25570) 19 senaryoyu çalıştırır (UHC, Tombala, Manhunt, Katil Kim, ırklar, parkur, hile koruması, ses grupları) ve ayrıca 3 senaryoyu zayıf cihaz (LITE) profilinde yeniden koşar; sonuç `tools\run_all.out`. Canlı sunucu açıkken test çalıştırma. Yeni jar kopyalama, test kopyası ve parkur senaryosu üretimi script içindedir.

Manhunt "temiz" ana dünya ister. Dünya daha önce Manhunt'ta kullanıldıysa **Başlat**'a basınca sunucu otomatik kapanıp yeni seed'le açılır (~40 sn sonra tekrar bağlanılır). Bunun için sunucu mutlaka `start.bat` / `start.sh` ile açılmış olmalı (yeniden açma döngüsü onlarda). Elle de istenebilir: `/kapisma newworld`.

## 5. Dosyalar, derleme ve paketleme
- `PLAN.md` plan ve kararlar, `docs\ORIGINS_BALANCE.md` ırk dengesi (13 ırk), `docs\DENEY_PLANI.md` adım adım deney listesi.
- `mod\kapisma\` sunucu modu kaynağı (derleme: `cd mod/kapisma && ./gradlew build`, Java 21), `mod\kapisma-hud\` istemci modu.
- `pack\server`, `pack\client`, `pack\wizard` paketlerin başlatıcı/kurulum betikleri ve belgeleri; **`python tools/make_release.py`** `release\` klasörüne `kapisma-sunucu-vX.zip`, `kapisma-istemci-vX.zip`, `kapisma-kurulum-vX.zip` (sihirbaz + ikisi) ve `SHA256SUMS.txt` üretir; `python tools/test_wizard.py [--boot]` sihirbazı otomatik dener (ses jar'sız olanlar ayrıca `dist\`'e kopyalanır ve depoya eklenir) (jar'lar zip içinde olduğu gibi kalır). Simple Voice Chat lisansı (All Rights Reserved) yüzünden pakete **konmaz**, ilk çalıştırmada doğrulamalı indirilir; kendi cihazların arasında dağıtmak için `--full` ile jar'lı `-tam` paketler üretilir (herkese açık yere yükleme).
- `python tools/test_pack.py` sunucu paketini temiz klasöre açıp gerçek `start.bat` ile açar, seed seçimini, ses modunu, yeni dünya döngüsünü ve temiz kapanışı doğrular (`--sh` start.sh, `--lite` zayıf cihaz).
- `knowledge\`, `tools\mcknow.py` Minecraft bilgi kütüphanesi (resmî veriden üretilir). `tools\` ayrıca seed tarama ve test araçlarını içerir. `seeds.json` doğrulanmış seed havuzu.
- Yerel çalışma kopyası: `server\` canlı sunucu, `server_test\` test kopyası, `client-pack\` eski elle derlenmiş istemci jar'ları (artık `release\` kullanılır).

## 6. Sorun giderme
- Arkadaş bağlanamıyor: Tailscale açık mı, paylaşım kabul edildi mi, güvenlik duvarı kuralı eklendi mi (`guvenlik_duvari_ac.bat`), host sunucusu `Done` yazdı mı?
- Ses yok: UDP 24454 açık mı, oyuncuda voicechat modu kurulu mu (istemci paketi kurar), oyunda V tuşundan mikrofon seçili mi?
- Tablet çok yavaş: LITE profil açık mı (açılışta `Donanim profili` yazar), arka plan uygulamalarını kapat, `ram_mb.txt` ile belleği artır, 4–5 oyuncuyla dene.
- "Mismatched mods" / bağlanırken hata: istemcide Fabric API ve Origins jar'ları client-pack'tekiyle aynı sürüm olmalı.
- Oyun dünyası açılırken 4–7 sn takılma normaldir (dünya üretiliyor).
