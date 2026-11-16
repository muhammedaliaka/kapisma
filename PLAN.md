# KAPIŞMA — UHC / Tombala (Bingo) / Manhunt Sunucu Planı

> Hedef: 8 kişiye kadar arkadaşla oynanan, Origins ırklı, kaliteli seed'li, menüden yönetilen kapışmalı oyun sunucusu.
> Durum: PLAN (kod yok). Tarih: 2026-11-15. Hazırlayan: Claude, soru-cevap turlarıyla.

---

## 1. Kesinleşen kararlar (senin cevapların)

| Konu | Karar |
|---|---|
| Oyun modları | **UHC**, **Tombala (Bingo)**, **Manhunt** |
| Oyuncu sayısı | En fazla **8** |
| Sunucu | **Kendi PC'n** (i9-12900H, 32 GB RAM). Zorlanırsak ücretsiz host (Falix / AxentHost) |
| Bağlantı | Tailscale (kurulu) veya Playit.gg; internet 70 Mbps (upload sayıldı, doğrulanacak) |
| Seed | **Hazır doğrulanmış seed havuzu** (PC'de cubiomes ile taranır), oyun başında sınıfa göre rastgele seçilir |
| Mesafe kuralı | **UHC ve Tombala: spawn'dan 1000 blok yarıçap (world border).** Manhunt: sınırsız |
| Irklar | Origins'in 10 ırkı. Mod başına ayrı ayar: UHC rastgele, Manhunt seçmeli gibi. **Yasaklı ırk listesi** tutulur |
| Irk dengesi | **1-10 ölçeği**, Human dahil. En iyi ile en kötü ırk arasında **en fazla 1 puan** fark |
| Takım | Oyun başında **Solo veya Duo** seçilir (rastgele veya elle). 8 kişi = en fazla 4 takım |
| Menü | **Oyun içi GUI** (sandık menüsü): mod, solo/duo, ırk modu, seed sınıfı, kurallar, başlat |
| Mimari | **Hazır modlar + kendi "şef" Fabric modumuz** |
| UHC kuralları | Host'un ayarlayabildiği **kural kataloğu** (host = sunucuya ilk giren). Çok kural eklenecek |
| UHC sınır | 1000 yarıçap başlar, **zamanla daralır**, **Nether kapalı** |
| Manhunt | Klasik: Runner Ender Dragon'u öldürürse Runner kazanır, Hunter hepsini öldürürse Hunter kazanır. Nether/End açık, sınır yok |
| Tombala | **Lockout**, 5x5 kart, **13 eşya toplayan kazanır** (bkz. açık soru A) |
| Mod ekleme | Hazır mod varsa indir, yoksa kendimiz yaz |
| MCP | `mcmodding` Claude Desktop config'ine **eklendi** (bkz. §9) |

---

## 2. Mevcut kurulum (makinede bulunanlar)

- Launcher: **TLauncher**, profiller: Fabric 1.21.1, Fabric 1.20.1, Forge 1.20.1. Oyun klasörü `.minecraft`.
- Hedef sürüm: **Fabric 1.21.1** (Origins 1.13.0-pre.3 ve hazır modların çoğu bunu destekliyor).
- `mods/` içindekiler:
  - `fabric-api-0.116.18+1.21.1` (gerekli)
  - `Origins-1.13.0-pre.3+mc.1.21.1` (**ırk modu**; Apoli, Calio, PAL, Cardinal Components dahili görünüyor, test edilecek)
  - `Gamma-Utils` (istemci: parlaklık, sunucuya gerekmez)
  - `skinrestorer` + `tl_skin_cape` (skin/pelerin; TLauncher hesapları için)
- Java: Sistem Java'sı **8** (eski). Minecraft'ın kendi **Java 21** runtime'ı var (`.minecraft\runtime\java-runtime-delta`, 21.0.7) → sunucu bununla çalışacak.
- Disk: C: üzerinde sadece **15 GB boş**. Her oyun dünyası ~0.3-1 GB; eski dünyalar otomatik silinecek.
- Origins'in 10 ırkı (jar'dan okundu): Arachnid, Avian, Blazeborn, Elytrian, Enderian, Feline, Human, Merling, Phantom, Shulk.

---

## 3. Mimari

```
          ┌──────────────────────────────────────────────┐
          │  Fabric 1.21.1 sunucu (Java 21, 6 GB RAM)    │
          │                                              │
          │  Kapisma (ŞEF MOD, kendi yazacağız)          │
          │   ├─ Lobi + GUI menü                         │
          │   ├─ Seed havuzu okuyucu (seeds.json)        │
          │   ├─ Dünya yöneticisi (seed'li oyun dünyası) │
          │   ├─ Takım (solo/duo) + host sistemi         │
          │   ├─ Irk atama + yasaklı liste + denge       │
          │   ├─ UHC motoru (kural kataloğu, sınır)      │
          │   └─ Mod adaptörleri (Bingo, Manhunt)        │
          │                                              │
          │  Hazır modlar: Yet Another Bingo,            │
          │  Manhunt (manhunt_pack / Compass Manhunt),   │
          │  Origins + denge datapack'i, UHC yardımcıları│
          └──────────────────────────────────────────────┘
               ▲                         ▲
   seeds.json  │                         │ Tailscale / Playit
   (cubiomes   │                         │
    tarayıcı)  └── Seed Tarayıcı (araç)  └── 8 oyuncu
```

### 3.1 Hazır modlar (indirilecek) ve kendi yazılacaklar

| İhtiyaç | Seçim | Durum |
|---|---|---|
| Tombala | **Yet Another Bingo** (Modrinth `yet-another-minecraft-bingo`, 1.21.x, istemciye mod gerekmez) | Hazır. Takım, lockout, kart, hazır sayacı var |
| Manhunt | **Manhunt** (`manhunt_pack`, MIT, 1.21.1, mod+datapack, Nether/Overworld takip) veya **Compass Manhunt** (server-side Fabric) | Hazır. Faz 0'da ikisi denenecek |
| UHC oyun motoru | Fabric 1.21.1 için hazır UHC oyun modu **bulunamadı** | **Kendi yazacağız** (Kapisma içinde) |
| UHC parçaları | `uhc-gapples` (4 altın elma), `golden-heads-continued`, `uhcnh`, `uhc-crafts` | Kural kataloğunda gerekirse kendi kodumuz da yazar (bağımlılık azalır) |
| Irk dengesi | Kendi datapack'imiz. `OriginsTweaks` (298k indirme) denge referansı olarak incelenecek | Faz 3 |
| Takım sohbeti / ses | `Simple Voice Chat` (isteğe bağlı) | Sonra |

### 3.2 Seed ile dünya oluşturma, en büyük teknik risk

Her oyun için yeni seed'li dünya gerekiyor. İki yol var:

- **Yol A: Fantasy (NucleoidMC) kütüphanesi.** Çalışırken seed'li geçici dünya açar, oyuncuyu ışınlar, bitince siler. En şık yol. **Doğrulanmadı:** Modrinth'te bulunamadı (maven.nucleoid.xyz'de olabilir), 1.21.1 uyumu ve Origins ile uyumu Faz 0'da denenecek.
- **Yol B: Yeniden başlatma sarmalayıcısı.** `start.bat` döngüsü: menüden seçim → `server.properties` seed'i değişir → dünya klasörü silinir → sunucu 20-40 sn'de kendini yeniden başlatır. Basit ve sağlam, çok güvenilir.

**Karar:** Faz 0 sonunda A çalışırsa A, çalışmazsa B. B her koşulda yedek.

Not: Yet Another Bingo kendi oyun dünyasını yönetebilir. Seed havuzu ile çakışıyorsa Bingo'nun dünyasını Kapisma açacak, YAB sadece kart/skor yapacak (Faz 0'da test).

---

## 4. Seed sistemi

### 4.1 Araç
- **cubiomes** (C kütüphanesi, biyom/yapı üretimini taklit eder) tabanlı bir tarayıcı: PC'de çalışır, seed'leri tarar, kriterlere uyanları `seeds.json`'a yazar.
- Sürüm riski: upstream cubiomes "1.21'e kadar" diyor; 1.21.1 dünya üretimi 1.21 ile aynı olmalı ama **her seed oyunda `/locate` ile örneklem testine** sokulacak (doğrulanmış kayıt = tarayıcı + oyun içi onay).
- Alternatif: xpple'ın cubiomes fork'u (1.21.1-1.21.11 destekli), SeedMapper (Fabric mod).

### 4.2 Seed sınıfları ("Minecraft sınıflandırmasına göre rastgele")
Spawn noktasının biyom ailesine göre havuzlanır; oyun başında menüden **sınıf seçilir veya "Rastgele"**:

| Sınıf | Örnek biyomlar |
|---|---|
| Ova | Plains, Sunflower Plains, Meadow |
| Orman | Forest, Birch Forest, Dark Forest, Flower Forest |
| Taiga/Kar | Taiga, Snowy Taiga, Snowy Plains |
| Çöl/Mesa | Desert, Badlands |
| Savan/Cangıl | Savanna, Jungle, Bamboo |
| Dağ | Windswept Hills, Grove, Jagged Peaks (spawn dağın eteğinde) |

Spawn'da **yasak**: okyanus, mantar adası, derin su, gölde doğma.

### 4.3 Kalite kriterleri (UHC ve Tombala: 1000 blok yarıçap içinde, dünya spawn'ı merkez)

Zorunlu:
1. Spawn kara üzerinde, ilk 150 blokta ağaç var.
2. Alan içinde en az **1 köy** (yoksa demir kaynağı: açık demir damarı/mağara girişi).
3. Alan içinde **tatlı su** (göl/nehir) ve **lav** (mağara veya yüzey lavı) var.
4. Alanın %25'inden azı okyanus/derin su.
5. Elmaya erişim: ağaç yoğunluğu ve/veya orman biyomu (altın elma/yemek).
6. Yeterli çeşit: en az 3 farklı biyom.
7. UHC için: yüzeyde fazla dik uçurum/ölümcül tuzak yok (yüksek yükselti farkı kısıtlı).

Puan (sıralama için): yapı sayısı (köy, harabe, terk edilmiş maden, ruined portal), mağara/cevher erişimi, biyom çeşidi, dengeli yön dağılımı (bir köşe boş kalmasın), Bingo için biyom çeşitliliği (eşya kartı: balık, kum, kar, çiçek vb.).

**Manhunt:** sınır yok, ama spawn'a yakın köy + 1 ruined portal/fortress yönü aranır; çok kısa Nether yolu avantajı sınırlanır.

Hedef havuz: sınıf başına **≥50-100 doğrulanmış seed**, toplam ~500. Kullanılan seed işaretlenir, tekrar etmez.

### 4.4 Border merkezi
Border merkezi dünyanın gerçek spawn'ı olmalı (rastgele spawn noktası ±1000'e uygun hesaplanır). Spawn'ın cubiomes ile tahmini oyundakiyle farklı çıkabilir → border merkezini oyun başında sunucu mevcut spawn'dan alacak, tarama ise spawn çevresine toleranslı değerlendirecek.

---

## 5. Irk (Origins) sistemi

### 5.1 Ayarlar (mod başına, menüden)
- Dağıtım: **Rastgele** / **Seçmeli** / **Karışık** (örn. host seçer)
- **Yasaklı ırk listesi** (host menüsünden aç/kapa, kalıcı)
- Rastgele modda **aynı takımda tekrar etmeme** veya **herkes farklı ırk** seçeneği
- Varsayılan: UHC = rastgele, Manhunt = seçmeli, Tombala = rastgele (değiştirilebilir)

### 5.2 Denge hedefi
- Ölçek 1-10, Human referans (orta nokta). **Maks fark: en iyi - en kötü ≤ 1 puan.**
- Yöntem: her ırkı güç/zayıflık maddeleriyle puanla, sonra güç ayarlarıyla (Apoli power JSON'larını datapack ile override) dengele.

Mevcut ırklar ve ilk **taslak** puanlar (benim tahminim, oynayarak düzeltilecek; ham = değişiklik öncesi):

| Irk | Origins "etki" | Ham | Notlar (güç / zayıflık) | Denge fikri |
|---|---|---|---|---|
| Human | 0 | 5.0 | Güç yok | Referans; küçük bonus gerekebilir |
| Arachnid | düşük | 5.5 | Tırmanma, ağ; kırılgan, etçil | Hafif kısıt yok |
| Avian | düşük | 5.0 | Yavaş düşme, hava | Hafif güç ekle |
| Blazeborn | yüksek | 4.0 | Ateş bağışıklığı; su hasarı, Nether doğuşu | UHC'de Nether yok → Overworld doğuşu, su hasarı hafiflet |
| Elytrian | düşük | 6.5 | Uçuş; claustrophobia, hafif zırh | Uçuş hızı/yüksekliği kısıtla |
| Enderian | orta | 6.0 | Pearl, menzil; su hasarı | Pearl bekleme süresi |
| Feline | orta | 5.5 | Düşme bağışıklığı, sıçrama; zayıf kollar | Küçük güç |
| Merling | yüksek | 6.5 | Su soluma, yüzme; karada zayıf | Karada az güçlendir |
| Phantom | yüksek | 6.5 | Hayalet formu, görünmezlik; güneşte yanar, açlık | UHC'de PvP görünmezlik kısıtı |
| Shulk | düşük | 6.0 | Zırh, depo; ağır | Zırh azalt |

> Ham puanlar sadece başlangıç tahmini. Gerçek dengeleme Faz 3'te oyun testi ve istatistikle (kazanma oranı) yapılacak.

- **Mod bazlı ayar:** Her mod için ayrı denge profili (UHC'de Nether yok, Manhunt'ta Nether/End var, Bingo'da eşya toplama hızı).
- Teknik yol: Origins datapack override (`data/origins/powers/*.json`) + ek mod bazlı sağlayıcı. Origins ayarı `origins_server.json`.
- **Elytrian:** `origins:elytra` gücü Bingo/Manhunt'ta büyük avantaj; ilk dengeleme adayı.

### 5.3 Teknik detay
- Origin katmanı: `data/origins/origin_layers/origin.json` (tek katman). Rastgele atama Kapisma'nın kendi koduyla (Apoli `setOrigin` API'si), Origins Randomiser modu gerekmez (1.21.1 uyumu belirsiz).
- Kapisma, oyun başında herkese ırk atar; oyun bitince ırkları sıfırlar.

---

## 6. Kapisma (şef mod) özellikleri

1. **Host sistemi:** Sunucuya ilk giren host. Host menüde ayar yapar; host çıkarsa devir.
2. **Lobi:** Oyuncular lobide bekler; bir eşyaya sağ tık ile menü açılır.
3. **GUI menü:** Oyun seç (UHC/Tombala/Manhunt), takım modu (Solo/Duo, rastgele/elle), ırk modu, seed sınıfı, ayarlar, **Başlat**.
4. **Dünya yönetimi:** seed seç → dünya oluştur (Yol A/B) → oyuncuları dağıt → oyun bitince lobiye dön ve eski dünyayı sil.
5. **Takım sistemi:** Duo eşleştirme (rastgele/elle), takım rengi, takım sohbeti, dost ateşi kuralı.
6. **UHC motoru:** kural kataloğu (aşağıda), border, geri sayım, kazanan tespiti, öldürme sıralaması, ölüm sonrası seyirci.
7. **Adaptörler:** Bingo ve Manhunt modlarını başlatma/bitirme komutlarıyla yönet (aynı menüden).
8. **Kayıt:** Sonuçlar JSON'a yazılır (ırk, kazanan, süre). Denge ayarı için veri.

### 6.1 UHC kural kataloğu (host ayarlar; hepsi aç/kapa veya değer)
- Doğal can yenilenmesi: kapalı/açık
- Golden Head: aç/kapa (kafadan düşer, 4 altın elma)
- Altın elma maliyeti: 8 / 4
- Cut-clean: cevher/yemek pişmiş düşer
- Ağaçtan elma şansı: 0.5% (varsayılan) → ayarlanabilir
- PvP başlangıç kapalı süresi: 0-30 dk
- Sınır: başlangıç yarıçap (≤1000), daralma aralığı, son yarıçap, hız, uyarı süresi
- Nether: kapalı (UHC varsayılan), açık seçeneği
- Takım ateşi (friendly fire)
- Başlangıç kiti / erken buff (hız, hasar direnci 60 sn)
- Yaratık spawn: normal / azaltılmış / kapalı
- Ölüm: kafa düşürme, eşyalar yere, seyirci
- Gece/gündüz sabit: opsiyonel
- Elytra/Totem/Pearl yasaklama
- Sınır dışı hasar
- Süre limiti ve final düello
- Sürpriz olaylar (airdrop, kuşatma): ileride

---

## 7. Tombala (Bingo) kuralı

- YAB lockout: 5x5 = 25 eşya. **13 eşya toplayan kazanır** (çoğunluk; lockout'ta rakip o eşyayı artık alamaz).
- Eşya havuzu özelleştirilir: **UHC/Tombala 1000 blok sınırı içinde ulaşılabilir** eşyalar (Nether yok), ırk avantajı (Merling/Elytrian) bozmayan eşyalar.
- Süre limiti seçenekleri, çıkış kuralı ve beraberlik kuralı menüden.
- **Açık soru A:** "3 farklı tombala yapan" sözünü "13 eşya" ile aynı anlamda aldım. Ayrıca klasik satır/sütun tombala (3 sıra) isteniyorsa menüye ek seçenek eklenir.

---

## 8. Sunucu kaynakları (8 oyuncu)

Araştırma kaynaklarına göre yaklaşık değerler (hosting rehberleri, kesin değil):

| Kaynak | Tahmin | Senin PC'n |
|---|---|---|
| RAM | Fabric + Origins ile **4-6 GB** (8 oyuncu, view-distance 8-10) | Sunucu için **6 GB** ayır (32 GB'ın yanında rahat) |
| CPU | Tek çekirdek hızı önemli; 8 oyuncu için 2-3 çekirdek yeter | i9-12900H güçlü; oyunla birlikte çalışırken PC'yi yorabilir |
| Upload | Oyuncu başına ~0.1-0.3 Mbps normal, chunk yükleme sırasında artar; 8 oyuncuda **~2-10 Mbps** | 70 Mbps çok yeter |
| Disk | Dünya başına 0.3-1 GB, bitince silinir | **15 GB boş: dar**, otomatik temizlik şart |

Öneri ayarlar: `view-distance=8`, `simulation-distance=6`, JVM: `-Xms4G -Xmx6G` + Aikar bayrakları.
Not: Sunucu PC'de oyun oynayan sen de varsan RAM 6 GB sunucu + 4-6 GB istemci = rahat.

### 8.1 Hosting karşılaştırması (Aternos alternatifleri)
| Host | Fabric | Artı | Eksi |
|---|---|---|---|
| **Kendi PC'n** | Evet | Sınırsız, hızlı, tam kontrol | PC açık kalmalı, internet ve disk senin |
| Falix (FalixNodes) | Evet, modpack kurulumu | Ücretsiz, slot sınırsız | Reklam, kuyruk olabilir, Kapisma'nın otomasyonu kısıtlı olabilir |
| AxentHost | Evet | Ücretsiz 2-16 GB, uyumaz | Krediyle çalışır, kaynak sınırı |
| Oracle Cloud Always Free | Evet (VPS) | 7/24, güçlü | Linux kurulum bilgisi, hesap onayı |
| Aternos | Evet | Bilinen | Kuyruk, uyku; seed/mod otomasyonu zayıf |

Kaynaklar sağlayıcıların kendi sayfaları olduğu için **tarafsız değil**; kullanmadan önce güncel ücretsiz plan şartları kontrol edilecek. Bu proje seed/dünya otomasyonu istediği için **kendi PC'n** ilk tercih.

### 8.2 Güvenlik ve hesap notu
- Arkadaşların TLauncher/çevrimdışı hesap kullanıyorsa sunucu `online-mode=false` olmak zorunda; bu durumda **herkes başkası adına girebilir**. Önlem: **whitelist**, sunucuya sadece **Tailscale** üzerinden erişim (portu internete açma), SkinRestorer ile skin.
- Sunucu internete açılacaksa Playit.gg yerine Tailscale tercih edilir (özel ağ).

---

## 9. MCP ve araçlar

**Eklendi:** Claude Desktop yapılandırmasına `mcmodding` (npm `mcmodding-mcp@0.5.0`, MIT): Fabric/NeoForge dokümantasyonunu Claude'a sunar (yerel araç, projeye ait değil).

**Eklenmedi (neden):** canlı oyunu kontrol eden MCP'ler (Fabric API MCP Server `chapmanjw`, `minecraft-mcp`, MCPFabric, MMCP) **1.21.1'i desteklemiyor** (1.21.11 ve 26.x veya belirsiz). Seçenek: test için ayrı bir 1.21.11 profili açmak; ama Origins 1.21.1 sürümü ile uyum sorunu olur. Gerekirse sonra değerlendiririz.

Kullanılacak diğer araçlar: Gradle + Fabric Loom (mod derleme), JDK 21, cubiomes (C derleyici: MSVC/MinGW gerekebilir), Node 24 zaten kurulu.

---

## 10. Yol haritası

**Faz 0: Doğrulama sürümü (önce risk kapat)**
- Fabric 1.21.1 sunucu kur, 4 mod (Fabric API, Origins, YAB, Manhunt) birlikte çalışıyor mu?
- Fantasy (Yol A) deneme; çalışmazsa Yol B (yeniden başlatma döngüsü).
- YAB'ın kendi dünyası ile Kapisma çakışması.
- Tailscale ile arkadaş bağlantısı, SkinRestorer çalışması.
- cubiomes'un 1.21.1 doğruluğu (5 seed ile `/locate` karşılaştırması).

**Faz 1: Seed Tarayıcı**
- cubiomes aracı, kriterler (§4), sınıf havuzları, `seeds.json`. Hedef ≥300 seed.

**Faz 2: Kapisma çekirdeği**
- Host, lobi, GUI menü, takım (solo/duo), dünya yöneticisi (A veya B).

**Faz 3: Irk sistemi**
- Rastgele/seçmeli atama, yasaklı liste, denge puan tablosu, datapack override, test.

**Faz 4: Oyun modları**
- UHC motoru + kural kataloğu, Bingo adaptörü (13 eşya), Manhunt adaptörü.

**Faz 5: Oyun testleri ve denge**
- 4-8 kişilik testler, kazanma istatistikleri, ırk puanlarının güncellenmesi.

**Faz 6: Paketleme**
- Tek tık `start.bat`, mod paketi (istemci için `.mrpack`/zip), yedek ve temizlik, kullanım rehberi.

---

## 11. Riskler

| Risk | Etki | Önlem |
|---|---|---|
| Fantasy/dünya değişimi 1.21.1 + Origins ile uyumsuz | Dünya sistemi geri | Yol B (yeniden başlat) |
| cubiomes 1.21.1 sapması | Seed'ler beklenenden farklı | Tüm havuz oyunda örneklemle doğrulanır |
| Origins dengesi | Bir ırk her oyunu kazanır | Puan tablosu + oyun verisi |
| Disk 15 GB | Dünyalar dolar | Otomatik temizlik, yeni dünya oluşturunca eskiyi sil |
| PC'de hem sunucu hem oyun | Performans | 6 GB sunucu, view-distance 8 |
| Çevrimdışı hesaplar | Kimlik sahteciliği | Whitelist + Tailscale |
| Hazır modların güncellenmesi | Sürüm kayması | Sürümler sabitlenir (pin), yedek mod klasörü |

---

## 12. Açık sorular (cevap verirsen plana işlerim)

- **A.** Tombala'da "3 farklı tombala" tam olarak ne? (13 eşya yeterli mi, yoksa 3 satır da mı?)
- **B.** İnternet "70 mb" = **70 Mbps** mı (megabit), yoksa 70 MB/s mi? Upload hızını da kontrol edelim.
- **C.** Arkadaşlar premium hesap mı, TLauncher mı? (Güvenlik ayarını belirler.)
- **D.** Manhunt'ta Runner sayısı kaç? Duo takımda iki runner mı olacak?
- **E.** Irk modunda "seçmeli" mod: oyuncular birbirinin ırkını görebilsin mi, yoksa gizli seçim mi?
- **F.** Dünya ve oyun sonrası: sonuç ekranı/istatistik istiyor musun?

---

## 13. FAZ 0 SONUÇLARI (2026-11-15)

Sunucu: `.minecraft\kapisma\server\` (Fabric Loader 0.20.1, MC 1.21.1, Java 21.0.7). Başlatmak için `start.bat`.

| Test | Sonuç |
|---|---|
| Fabric 1.21.1 sunucu açılışı | **Başarılı**, açılış 4.2 sn, 72 mod yüklendi, hata yok |
| Fabric API + Origins + Yet Another Bingo 2.9.6 + Manhunt (pack-18) birlikte | **Başarılı**, çakışma yok |
| Origins kayıtları | 10 ırk, 80 güç, 1 katman okundu |
| Rastgele ırk | `/origin random [hedefler]` komutu **hazır var** → rastgele atamayı kendi kodumuza gerek kalmadan çağırabiliriz |
| Bingo komutları | `/bingo goal <sayı\|full_card>`, `mode lockout\|inventory\|hidden_items`, `timelimit`, `shuffleteams`, `start`, `lobby` var → **"13 eşya" kuralı doğrudan destekleniyor** (`/bingo goal 13` + lockout) |
| Manhunt | `manhunt:start_game` fonksiyonu çalıştı, hata yok (gerçek oyunla test sonra) |
| Fantasy (Yol A) | Maven'de **`0.6.3+1.21` / `0.6.4+1.21`** sürümleri var (1.21.1 ile uyumlu olması beklenir). Henüz kodda denenmedi → Faz 2'de |
| cubiomes | Kaynakta **`MC_1_21_1` açıkça var** → 1.21.1 doğrudan destekli |
| C derleyicisi | **Yok** (gcc/cl/clang bulunamadı). Faz 1 için MSYS2/MinGW veya `zig cc` kurulumu gerekecek (kurmadan önce sana soracağım). JDK 21 ve Rust **var** |

**Bulgular ve plana etkisi**
1. Bingo "lobby mode" (`bingo.zip` datapack'iyle kendi lobisini/dünyasını yönetebiliyor) var. Bizim lobi ile çakışmaması için Bingo'yu `isLobbyMode=false` (şu anki hâli) çalıştırıp dünyayı Kapisma açacak. Doğrulandı: bu modda Bingo lobisini kurmuyor.
2. Kapanışta Bingo `getScope invoked, but the server scope does not exist!` ERROR logu veriyor; zararsız görünüyor, oyun testinde izlenecek.
3. Bingo jar'ı 32 MB (içinde kaynak dosyaları var), diskte sorun değil.
4. Test dünyası silindi; sunucu klasörü ~200 MB (çoğu Fabric ve Minecraft kütüphaneleri).

**Henüz yapılmadı (senin katılımın gerekli)**
- İstemciden bağlanma testi (Fabric 1.21.1 profiliyle `localhost`). Hesabın TLauncher ise sunucuda `online-mode=false` gerekir (açık soru C).
- Tailscale ile arkadaş bağlantısı testi.
- Origins ve skin modlarının istemcide birlikte yüklenmesi.

---

## 14. FAZ 1 HAZIRLIK: Derleyici ve cubiomes doğrulaması (2026-11-15)

**Kurulan:** WinLibs MinGW-w64 (gcc 16.2.0, UCRT, winget `BrechtSanders.WinLibs.POSIX.UCRT`). Yeni terminallerde `gcc` PATH'te.
**Kaynak:** cubiomes (MIT) → `tools\vendor\cubiomes` (commit e61f905, `MC_1_21_1` destekli). Test programı: `tools\seedscan\probe.c` (+ `probe.exe`). Hız: 5 seed ~74 ms.

**Gerçek sunucuyla karşılaştırma (`/locate`, 1.21.1):**

| Seed | cubiomes en yakın köy | Oyun (`/locate`) | Sonuç |
|---|---|---|---|
| 99999 | (176, -544) | (176, -544) | **Birebir aynı** |
| 8675309 | (-272, 352) | en yakın (-304, -320); (-272, 352) yok | **cubiomes yanlış pozitif verdi** |

**Sonuç:** cubiomes hızlı ön eleme için iyi ama köy konumlarında hata payı var (bir seed'de olmayan köyü raporladı). Bu yüzden seed hattı **iki aşamalı** olacak:
1. **Aşama 1 (cubiomes, saniyede binlerce seed):** biyom/köy/su/lav ön filtresi, adaylar.
2. **Aşama 2 (gerçek sunucu doğrulaması, seed başına ~8 sn):** adayları sunucuda açıp `/locate` ile köy, harabe portal vb. konumları 1000 blok içinde teyit et; sadece teyitli olanlar `seeds.json`'a girer. 300 seed ≈ 40 dk.

**Not:** `probe.c` spawn biyomunu 3B örnekliyor (bir seed'de `lush_caves` çıktı). Yüzey biyomu için yüksek y veya 2B örnekleme kullanılacak.

Test sonrası sunucu temizlendi: test dünyası silindi, `level-seed` boş.

## 15. FAZ 1–2 SONUÇLARI: Mod ve sunucu (2026-11-15)

**Durum:** UHC, Tombala ve Manhunt motorları yazıldı; başsız (Carpet botlu) testlerden geçti. Gerçek istemciyle test henüz yok.

- **Seed havuzu:** 294 doğrulanmış seed (`seeds.json`), sınıflar: ova, orman, taiga_kar, savan_cangil, dağ (col_mesa boş). Seed artık öncelik değil.
- **Mod:** `mod/kapisma` (Fabric 1.21.1, Loom 1.16.3, Java 21). Fantasy (geçici seed'li dünya) ve sgui (sunucu taraflı menüler) jar-in-jar; istemcide mod gerekmez (Origins hariç, bkz. §8).
- **Komutlar:** `/kapisma menu|status|start|stop|host|game|team|seedclass|origins|ban|unban|rule|rules|newworld`, `/oyun`, `/kart`. Menü: Nether Star.
- **UHC:** daralan sınır, PvP koruması, golden head, cut-clean, elenince seyirci, süre limiti. Kurallar menüden.
- **Tombala:** 5x5 kart (8 kolay / 10 orta / 7 zor), lockout, hedef varsayılan 13, erken bitiş.
- **Manhunt:** ana dünya, runner/hunter, pusula takibi, ejderha ölünce runner kazanır; yeni dünya için `kapisma_next.json` + `start.bat` döngüsü.
- **Irk dengesi:** `docs/ORIGINS_BALANCE.md` (fark ≤ 1 puan, datapack override). Avian'a et yerine altın havuç.
- **Başlatma:** `server/start.bat` (Java 21 bulur, seed'i havuzdan seçer, Manhunt yeni dünya döngüsü).
- **Test:** `tools/mctest.py` + `tools/scenarios/*.txt`; Carpet yalnızca `tools/test-mods/` altında, testte geçici yüklenir (üretim `mods/`: fabric-api, Origins, kapisma).

**Doğrulanmadı / bilinen:** GUI görünümü, TLauncher/online-mode, Tailscale, gerçek ejderha öldürme, sınır daralma süresi, Fantasy dünya açılırken 4–7 sn takılma.

### 15.1 Karar: erişim = Tailscale + node paylaşımı (2026-11-15)
- Hesaplar TLauncher → sunucu `online-mode=false`, `enforce-secure-profile=false`, `max-players=8`.
- Beyaz liste şimdilik kapalı (arkadaş adları bilinmiyor). Güvenlik: sunucu internete açılmaz, sadece Tailscale. Tailscale host bilgisayarda kurulu ve girişli. Arkadaşlar "Share" (node paylaşımı) ile davet edilir; telefonlar/diğer cihazlar görünmez.
- Alternatifler elendi: port yönlendirme (offline-mode'da herkes açık hedef), playit.gg/ngrok (üçüncü taraf tünel, ek risk), Hamachi (eski/yavaş).
- Arkadaş paketi: `client-pack\mods` (Fabric API + Origins). Rehber: `README.md`.

### 15.2 Performans düzeltmesi: oyun açılırken takılma
- Sebep: başlangıç noktası ararken chunk'lar ana thread'de senkron üretiliyordu (2 takımda ~6 sn; 8 takımda çok daha fazla).
- Çözüm: jeneratör yükseklik tahmini ile ön eleme + asenkron bölge bileti (`TicketType.FORCED`) ile arka planda yükleme; chunk hazır olunca ışınlama. Takılma ~6 sn → ~2,5 sn. `setChunkForced` kullanılmaz (senkron yükler).
- Test koşucusu artık her koşuda ayar/istek dosyalarını temizler (testler üretim ayarlarını kirletiyordu).

## 16. Kullanıcı kararları ve eklenenler (2026-11-15)
Cevaplanan açık sorular:
- **Tombala kuralı:** 3 çizgi (satır/sütun/çapraz), lockout. Kural `lines` (1–5, varsayılan 3); isteğe bağlı `goal` (eşya, 0=kapalı). Bir çizgi, içinde başka takımın eşyası olduğu an o takım için imkânsızlaşır; kimse çizgi tamamlayamazsa en önde olan kazanır.
- **Manhunt runner:** host seçer (menü: Runner seç, `/kapisma runner <oyuncu|temizle>`); seçim yoksa rastgele.
- **Irk seçimi (seçmeli mod):** aynı ırk tekrarlanabilir, seçimler açık duyurulur; oyun başında tüm ırklar sohbete yazılır.

Yeni özellikler:
- Bitiş skor ekranı: süre, takım sıralaması, ırk, öldürme/ölüm, oyuna özel satırlar (Tombala çizgi/eşya, Manhunt runner'lar).
- Host devri: host çıkınca en eski oyuncu otomatik host (duyuruyla); menüden "Host devret".
- `/tc` takım sohbeti, `/koord` koordinat paylaşımı, elenenler sadece seyircilere yazar.
- Ses/başlık: geri sayım, başlangıç, PvP açılış ve 1 dk uyarısı, sınır daralma uyarısı, elenme, kazanma, Tombala eşya alma.

Testler: `tools/scenarios/` (uhc_basic, uhc_kill, bingo_basic, bingo_lines, manhunt_basic, manhunt_runner, origin_join, balance_check, avian_kit) hepsi geçti. Notlar: Carpet botlarının profil sorgusu bazen ağ hatası yığını basar (yalnızca test); `bingo_basic` ve `uhc_kill` bu yüzden "hata satırı=3" gösterebilir.
Hata düzeltmesi: ikinci çapraz çizgi yorum satırına karışıp kartın ilk eşyasıyla bir çizgi sayılıyordu; testle yakalandı, düzeltildi.

## 17. Lobi, parkur, puan, ırk temizliği (2026-11-15/16)
**Irklar:** Phantom ve Elytrian kaldırıldı (duvardan geçme/görünmezlik ve ücretsiz uçuş arena kurallarını bozuyor, istemci tuşu gerekiyor); Origins katmanı `replace:true` ile 13 ırka indirildi. Blazeborn `nether_spawn` etkisiz yapıldı; `frost_chill` JSON hatası düzeltildi. 5 yeni ırkın seçim ölçütleri ve tablo: `docs/ORIGINS_BALANCE.md`.

**Lobi:** ana dünyadaki y=250 platformu kaldırıldı. Yerine Fantasy ile açılan geçici **boş (void) dünya**: sabit öğle, hava/yaratık yok, barışçıl zorluk, rastgele tick kapalı. Platform y=100'de, yarıçap 16: havuz + çeşme, çiçek yatakları, 4 kiraz ağacı, bank, lamba, kemer; altı açık renkli kademeli ters koni. Görünmez/bariyer blok yok. Aşağı düşen (y<86) meydana döner. Meydan 16 chunk sürekli yüklü; parkur chunk'ları oyuncu gelince yüklenir (RAM tasarrufu). Ana dünya yalnızca Manhunt'ta kullanılır.

**Parkur:** `tools/parkour_gen.py` kare spiral rota üretir (113 platform, y 0→45) ve Minecraft 1.21 hareket fiziğini (koşu/zıplama/sürtünme) simüle ederek her zıplamayı ≥0.35 blok payla doğrular; sonuç `data/kapisma/parkour.json`. Bölümler: KOLAY 3x3, ORTA 2x2, ZOR 1x1/2x1/4x1 bantlar, ÇOK ZOR 1x1 ve 3 blok boşluk (yalnızca koşu mesafesi olan platformdan). Altın platform = checkpoint, 5x5 beyaz = bölüm sonu. Seçenekler: son checkpoint'ten devam / baştan / çık (öğe + komut + tıklanabilir sohbet).

**Puan:** `config/kapisma/scores.json`. Parkur 10/20/40/80 (bölüm ilk bitiriş) + en iyi süre; kapışma katılım 5, öldürme 10, galibiyet 100 (≥2 takım). Bot (test) oyuncuları oyun puanı almaz. İlk 3 meydandaki text_display panolarında; `/puan` kişisel özet.

**Testler:** `tools/run_all.sh` tüm senaryoları çalıştırır (yeni: `lobby_parkour`). Carpet botu yer çekimi uygulamadığı için parkurda düşme davranışı gerçek oyuncuyla denenmeli (docs/DENEY_PLANI.md B3).


## 18. Menü, PvP, harita, Tombala bilgisi, Katil Kim, parkur v2 (2026-11-16)
- **Menü:** ana menüde sadece oyun/takım/Ayarlar/Başlat; ırk modu, yasaklar, kurallar, seed, host devri → Ayarlar. Irk seçimi döngüsü (SELECT modunda her tikte yeniden "Irklar" başlatması) `finishSelection()` içinde fazı PREP'e almakla kapatıldı; `uhc_select` senaryosu regresyon testi.
- **PvP kuralı:** UHC, Tombala, Manhunt (ve Katil Kim'de her zaman açık). Manhunt'ta runner/hunter panosu.
- **Bilgi kütüphanesi:** `tools/mcknow.py` sunucu jar'ından loot table, biyom özellikleri/yaratıkları, yapı kümeleri çıkarır (`knowledge.json`). `Knowledge.scan` dünya üretecinden ±yarıçap biyom ve yapı yerleşimini tahmin eder, `obtainable` tarif zinciriyle ulaşılabilir eşya kümesini verir. Tombala havuzu (31/50/40 aday) buna göre süzülür. Neden resmî veri: web/wiki bilgisi hatalı olabiliyor; üreteçten alınan veri o tohum için kesin.
- **Konum bilgisi:** biyom + ipucu, koordinat yok (karar: kullanıcı).
- **Mini harita:** özel HUD paketi `kapisma:minimap` (v1), istemcide `MiniMap`; ayrıca 2048 bloklu biyom önizlemeli harita eşyası.
- **Katil Kim:** `ManhuntGame(katil=true)`; gizli takımlar (aynı renk, isim etiketi yok, ölüm mesajı kapalı), istemci mixin ile ortak skin (`kapisma:disguise`), katil sayısı kuralı. Sesli sohbet için araştırma: Simple Voice Chat 2.6.27 Fabric 1.21.1 mevcut (sunucu+istemci, UDP 24454, API 2.6.27); kendi sesli sohbetimizi yazmak yerine onun eklenti API'siyle (`KapismaVoice`) seyirci izolasyonu eklendi. Karar gerekçesi: ses kodlama/UDP/şifreleme yeniden yazılmaz.
- **Parkur v2:** tırmanma kuleleri (merdiven), zamanlı kızıltaş köprüleri (kademeli faz, açık süre ≥ 50 tik, ardışık platform örtüşmesi ≥ 30 tik); üretici bunları ayrıca doğrular, 12 farklı seed'de rota üretildi.
- **Bilinen sınırlar:** botlar yer çekimi uygulamaz → merdivene tırmanma ve köprüde atlama gerçek oyuncuyla denenmeli. Ses ve skin gizleme gerçek istemci ister (otomatik test yok). Voice chat için güvenlik duvarında UDP 24454.

### 18.x Anonimlik, ses, seed kriteri, optimizasyon (2026-11-16)
- Katil Kim: pusula/action bar'da isim yok, sadece en yakın / en uzak mesafe (`compass`). Ses: `voice` (aç/kapa) ve `deepVoice` (herkes aynı kalın ses). Kalın ses `voice/PitchShift.java` ile (çift okuma noktalı gecikme hattı, oran 0.62, Opus çöz → kaydır → kodla); kare uzunluğu değişmez.
- Seed havuzu 480 seed (6 sınıf x 80): gerçek dünya üretecinde (`/kapisma debug seedscan`) taranıp `SeedScore` ile puanlandı. Köy zorunlu değil: merkeze <=600 blokta önemli yapı, >=3 farklı önemli yapı türü ve en az bir yapı kümesi şart.
- Optimizasyon: Aikar G1 JVM bayrakları (6 GB), view 8 / simulation 6, lz4 bölge sıkıştırma, boş lobide parkur/pano işi yok, oyun ve lobi dünyalarında spawn chunk 0, lobi yan panosu 1 sn'de bir güncellenir.

### 18.y Katil Kim tasarım analizi (revize: 2026-11-16 akşam)
İlk sürümde toplantı/oylama, katile 2 can ve zorunlu oylama eklenmişti; kullanıcı bunun modun amacına (**katil masumların hepsini öldürmeden Ejderha'yı öldürmek**) aykırı olduğunu belirtti. Geri alındı:
1. Toplantı, oylama, `killerLives`, `autoVoteMin` ve ses/yazı mesafesizliği kaldırıldı. Katil sınırsız yeniden doğar; masum öldürülünce (varsayılan: doğal ölümde de) elenir.
2. Başlangıç: herkes ortak noktanın ±100 bloğunda (200x200) rastgele ve birbirinden uzak doğar; aynı noktada yığılma yok.
3. Korunan kararlar: başlangıç koruması (PvP kapalı), katil donmaz/kör olmaz, masum-masum ateşi varsayılan açık, katile diğer katilin adı söylenir, pusula/action bar isimsiz (en yakın/en uzak), host konsolda katili okuyamaz, skin gizleme modu eksik oyuncuya uyarı, çıkan oyuncuya 3 dk süre.
Bilinen sınır: kalın ses ve SVC "konuşuyor" simgesi konumu gösterir; simgeyi SVC ayarlarından kapat.

## 19. Büyük tur: başlangıç yerleri, Tombala modeli, hile koruması, ses, paketleme (2026-11-16)
- **Başlangıç kuralları:** UHC `scatterRadius` (100–700, varsayılan 500); Katil ±100; Tombala/Manhunt aynı nokta (`headStartSec` 0). `GameWorld.scatter(n,radius,rnd)` minimum ayrım hesaplar.
- **Portal kapatma:** UHC/Tombala geçici dünyalarında Nether/End portalı mixin (`EntityPortalMixin`) ile kapalı; portal oyuncuyu ortak dünyalara taşıyordu.
- **Manhunt pusulası:** runner'ın boyut başına son bilinen konumu (`lastSeen`); Nether/End geçişinde pusula portalı, aynı boyutta runner'ı gösterir. Çevrimdışı runner için 180 sn süre.
- **Tombala maliyet modeli (`ItemCost`):** biyom uzaklığı (`Knowledge.biomeDist`), yapı uzaklığı, kazma seviyesi, yaratık zorluğu, nadir düşüş/sandık cezası, tarif zinciri. Cevher bloğu eşyası (ipeksi dokunuş) pahalı sayılır (+12); aksi hâlde demir/elmas eşyalar "kolay" çıkıyordu (test: kart dağılımı). Yüzdelik dilimlerle kolay/orta/zor, `difficulty` kuralı. Görev kareleri `BingoTasks` (ilerleme tabanlı, seed'de yapılabilir olanlar).
- **Hile koruması (`xray/Xray.java`):** `ClientboundLevelChunkPacketData` kurucusuna mixin; bölüm bölüm `PalettedContainer` kopyası: gömülü cevher → ev sahibi taş, yer değiştirmiş deterministik sahte cevher, yeraltı boşluk/yapı blokları → taş, blok varlığı süzme (`BEInfoAccessor`). Oyuncu başına izleme (`PlayerChunkSenderMixin`), blok değişimi (`ServerChunkCacheMixin`), BFS ile bağlı havada gerçek durumu açma (`ClientboundSectionBlocksUpdatePacket`), 30 sn paket önbelleği. Doğrulama: `xray_audit`/`xray_nether` senaryoları paketi gerçek kodekle çözüp sızıntı, belirsizlik, mağara açılması ve blok varlığı süzmeyi denetler; hepsi temiz.
- **Ses:** `voice/VoiceBridge` + `KapismaVoice`: takım ses grupları (açık tür), yardım mesajı; Katil Kim hariç. `voicechat-server.properties` sunucu paketinde hazır.
- **Paketleme:** `release/` altında sunucu ve istemci paketleri (bkz. §20).

## 20. Herkes sunucu kurabilsin: tablet profili, paketler, GitHub (2026-11-16)
- **Başlatıcılar** (`pack/server`): `start.bat` (Windows) ve `start.sh` (Linux, Mac, Android/Termux). Mojang EULA onayı → Java 21 bul/indir (Adoptium JRE; Termux'ta `pkg install openjdk-21`) → ses modunu Modrinth'ten SHA-512 doğrulamalı indir → RAM'in %55'i (en çok 6 GB, Android'de `MemAvailable`'ın %85'i ile sınırlı) → Manhunt yeni dünya döngüsü. `ram_mb.txt` ve `profil.txt` ile elle değiştirme.
- **PowerShell bağımlılığı kalktı:** seed seçimi ve yeni dünya uygulaması `tr.kapisma.boot.Boot` (Fabric `preLaunch`) içinde, Minecraft sınıflarına dokunmadan: `kapisma_next.json` varsa dünya klasörü silinir ve `level-seed` yazılır; dünya yoksa seed havuzundan kullanılmamış seed seçilir. Eski `kapisma-scripts/*.ps1` ve start.bat'taki çağrıları kaldırıldı (çift uygulama seed'i ezerdi).
- **Donanım profili (LITE):** yığın < 3,5 GB, ≤ 2 çekirdek ya da ARM + yığın < 5 GB ise (`KAPISMA_PROFILE`/`-Dkapisma.profile` ya da `profil.txt` ile zorlanabilir): `view-distance` ≤ 6, `simulation-distance` ≤ 4, `entity-broadcast-range` ≤ 70; hile koruması `revealRadius` 8, `bfsCap` 1200 (koruma ayrıntısı aynı). Testler `--xmx 4G` (NORMAL) ve ayrıca `--xmx 3G` (LITE) ile koşar.
- **Paketler** (`python tools/make_release.py`): `kapisma-sunucu-vX.zip`, `kapisma-istemci-vX.zip` (+ yalnızca yerel dağıtım için `--full` ile ses jar'lı `-tam` sürümler), `SHA256SUMS.txt`. Jar'lar zip içinde olduğu gibi kalır (yeniden paketlenmez). Fabric API (Apache-2.0), Origins (MIT), `fabric-server-launch.jar` (Fabric Installer 1.1.2, Apache-2.0) pakete girer; **Simple Voice Chat "All Rights Reserved" olduğu için pakete girmez**, adres + SHA-512 betiklere gömülüdür (`make_release` Modrinth API ile eşleştiğini doğrular). Carpet pakette yok. `LICENSES/NOTICE.txt` bileşenleri listeler (Fantasy/sgui LGPL-3.0, jar-in-jar, değiştirilmemiş).
- **Doğrulama:** `tools/test_pack.py` sunucu zip'ini temiz klasöre açar, gerçek `start.bat` (ya da `--sh`) ile açar: EULA, Java bulma, ses modu indirme + özet, ilk seed seçimi, LITE (`--lite`), `/kapisma newworld` ile yeniden açma döngüsü ve temiz kapanış. Bu test `cmd`'nin `for /f` içinde tırnaklı komutta ilk/son tırnağı silme tuzağını yakaladı (Java sürümü artık geçici dosyaya yazılıp okunuyor).
- **Test tuzağı (çözüldü):** Carpet sahte oyuncusunun kayıtlı verisi (yerçekimi 0, izleyici modu) bir sonraki senaryoyu bozuyordu (parkur testi). `mctest.py` her koşuda `world/playerdata`, `stats`, `advancements` temizler.
- **Lisans:** MIT (`LICENSE`); `mod/kapisma/LICENSE` içindeki CC0 şablonu MIT ile değiştirildi (fabric.mod.json zaten MIT diyordu).
- **GitHub:** public depo; kaynak + belgeler + betikler + seed havuzu depoda, zip'ler **Releases** varlığı olarak. `.gitignore` çalışma kopyalarını, Mojang verisinden üretilen `knowledge/`'i, büyük test çıktılarını ve Carpet jar'ını dışarıda bırakır. Kişisel veriler (Tailscale IP, kullanıcı adı, bilgisayar adı) belgelerden temizlendi.

## 21. Son tur: oyuncu hile koruması, UHC ekranı, kurulum sihirbazı (2026-11-16)
- **Oyuncu hile koruması** (`guard/AntiCheat`, kural `antiCheat`, oyun RUNNING iken): (1) görüş hattı — `ServerPlayerVisMixin` `ServerPlayer.broadcastToPlayer`'a girer; duvar arkasındaki oyuncu `ChunkMap.TrackedEntity`'den çıkarılır (istemciye entity hiç gitmez). Vanilla takibi yalnızca 16 blok kutusu değişince yeniler, bu yüzden `AntiCheat.tick` her 4 tikte (LITE 8) `updatePlayer`'ı `TrackedEntityAccessor` ile yeniden çağırır; ChunkMap `entityMap`'ine `ChunkMapAccessor` ile erişilir. Görüş: tam opak küp bloklara karşı DDA ışını, bakan 5 çıkış noktası × 7 hedef noktası, şişirilmiş kutu (köşeden önce gösterme), 16 tik bekletme; muaf: <8 blok, takım, GLOWING, seyirci. >144 blok hiç gönderilmez. Oyun bitince tek seferlik geri gösterme turu. (2) `AttackEntityCallback`: menzil (+1,3), duvar arkası (iki ışın), 20 tikte >18 vuruş. (3) `CommonPlayerSpawnInfoMixin`: login/respawn paketindeki hashed seed `fakeHashedSeed()` ile değişir (her zaman). Test: `player_guard` senaryosu (opak duvar → `seenBy=false`, cam → görünür, duvar kalkınca geri; menzil/duvar/tıklama; kapalıyken geçer; seed paketi "UYDURMA"). Sahte oyuncu chunk onayı vermediği için test komutu `guard ack` ile onaylatır.
- **UHC ekranı:** `SideBoard` (yan panel yardımcısı), UHC yan paneli, eylem çubuğunda merkez oku + mesafe + kendi öldürme sayısı, can göstergesi (`showHealth`: Tab + isim altı, `WorldGame.setupHealth`), takım arkadaşı ölüm konumu bildirimi.
- **İzinli oyuncular:** `Boot.applyAllowList` — `izinli_oyuncular.txt` doluysa `whitelist.json` (offline UUID) yazılır ve `white-list`/`enforce-whitelist` açılır (offline-mode'da ad taklidine karşı).
- **Kurulum sihirbazı** (`pack/wizard`, `kapisma-kurulum-vX.zip`): `Kapisma-Kurulum.bat` → Windows PowerShell WinForms (`kurulum.ps1`, BOM'lu UTF-8). Oyuncu: mods + eski modları kenara alma + Fabric 1.21.1 sürüm JSON'u (yoksa) + resmi launcher profili + ses modu (SHA-512) + servers.dat (yoksa). Sunucu: kopyalama (ev sahibi ayarlarını ezmez), izinli oyuncular, RAM, EULA kutusu, güvenlik duvarı, adresler, başlatma. `-Headless -Role ...` ve `-SelfTest` ile `tools/test_wizard.py` otomatik dener. Sınır: TLauncher ve UAC otomatik denenemez (DENEY_PLANI P2/P3).
