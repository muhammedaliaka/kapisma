# Deney Planı (2 kişi: sen + Carpet botu)

Amaç: her özelliği sırayla, aynı adımlarla denemek. Her satırda **ne yap → ne görmelisin**. Görmediğin yerde ✗ işaretle, bana yaz.

Hazırlık
1. `python tools/serve.py` (arka planda) → sunucu açılır, bot ekleme komutu: `tools/serve_cmds.txt` dosyasına `!bot Bot1 <oyuncu-adın>` yaz.
2. Minecraft 1.21.1 Fabric istemcisi (Fabric API + Origins + `kapisma-hud` modu). Adres: `adres_goster.bat` (paketteki) veya Tailscale IP'n.
3. Sen host'sun (ilk giren). Test için op: `ops.json` içinde oyuncu adın var, gerçek oyundan önce kaldır (`deop <oyuncu-adın>`).

## A. Lobi (boş dünya)
| # | Yap | Görmelisin |
|---|---|---|
| A1 | Sunucuya gir | Yüzen, aydınlık platform. Altına bakınca dünya yok (gökyüzü/boşluk). Ortada havuz + çeşme, çiçek yatakları, 4 kiraz ağacı, bankler, lambalar |
| A2 | Kuzeye, batıya yürü | Kuzey duvarında "PARKUR İLK 3", batıda "KAPIŞMA İLK 3" panosu; güneyde "NASIL OYNANIR" |
| A3 | Platformun kenarından atla | Düşünce (y<86) meydanın başlangıç noktasına dönersin, hasar almazsın |
| A4 | Görünmez/geçilmez blok ara (kenarlarda, ağaçların altında) | Hiç yok; yalnızca görünen bloklar |
| A5 | Nether Yıldızı'na sağ tık | Oyun menüsü açılır |

## B. Parkur
| # | Yap | Görmelisin |
|---|---|---|
| B1 | Doğudaki kemerden geç, yeşil başlangıç platformuna çık | "Parkur başladı" mesajı, çantada yeşil boya (son checkpoint), kırmızı boya (baştan), kapı (çık) |
| B2 | İlk bölümü (KOLAY, 3x3 platformlar) oyna | Altın platformlarda "Checkpoint!" + ses |
| B3 | Bilerek düş | Son checkpoint'e ışınlanırsın ("Düştün!") |
| B4 | KOLAY'ın sonundaki 5x5 beyaz platforma çık | Başlık "KOLAY bitti +10 puan", sohbette 3 tıklanabilir seçenek: son checkpoint'ten devam / baştan başla / parkurdan çık |
| B5 | Aynı bölümü tekrar bitir | Tekrar puan yok ("puanını almıştın") |
| B6 | Sırayla ORTA (2x2) → ZOR (1x1, 2x1) → ÇOK ZOR (1x1, 3 blok boşluk) | Bölüm puanları 20 / 40 / 80; hepsi geçilebilir olmalı. Her bölümde bir merdiven kulesi, ORTA/ZOR/ÇOK ZOR'da kızıltaş köprüsü |
| B7 | Tüm parkuru bitir | Herkese duyuru + süre, 10 sn sonra meydana dönüş, panoda isim |
| B8 | `/parkur cik`, `/parkur bas`, `/parkur checkpoint` | Aynı işlevler; lobi dışında kullanılamaz |

Parkurda geçilemeyen bir yer bulursan: platform numarasını (gerekirse koordinat) yaz. `tools/parkour_gen.py --check` tüm zıplamaları fizik simülasyonuyla yeniden doğrular.

## C. Puan
| # | Yap | Görmelisin |
|---|---|---|
| C1 | `/puan` | Parkur ve kapışma puanların, ilk 3 |
| C2 | Bir oyun bitir (en az 2 takım) | Sohbette "Kapışma puanı: +X"; panoda güncelleme ≤ 1 sn |
| C3 | Bot puan almamalı | `scores.json` içinde `Bot1` yok (oyun puanı); parkur testinde bot parkurdan puan alabilir, test sonrası dosya silinir |

Puan kuralı: oyuna katılım 5, her öldürme 10, kazanan takıma 100 (en az 2 takımlı oyunda). Parkur bölümü ilk bitiriş: 10 / 20 / 40 / 80.

## D. Tombala (HUD + kağıt)
| # | Yap | Görmelisin |
|---|---|---|
| D1 | Menü → Tombala → `lines 1` (veya kural menüsü) → Başlat | Sol üstte kart (5x5), takım adı/rengi, rakipler |
| D2 | `H` tuşu | HUD gizlenir/gösterilir |
| D3 | `/kart` | Kağıt: hedef seçimi + tarif ipuçları (kural açıksa) |
| D4 | Eşya topla | Hücre takım renginde ✓; rakip alırsa kırmızı X |

## E. Takım, sohbet, istatistik
| # | Yap | Görmelisin |
|---|---|---|
| E1 | `/takimim renk kirmizi`, `/takimim isim Ates` | Takım rengi/adı değişir (oyun başlamadan) |
| E2 | Oyun içinde normal yaz | Sadece takımına gider |
| E3 | `/herkes selam`, `/ozel Bot1 selam`, `/takim Ates selam` | Beklenen alıcılara gider |
| E4 | Oyun sonu özetinde isme tıkla | `/istatistik` kim kimi kaç kez öldürdü |

## F. Manhunt
| # | Yap | Görmelisin |
|---|---|---|
| F1 | Menü → Manhunt → runner seç | Host seçimi; en az 2 oyuncu yeterli |
| F2 | Başlat | Ana dünyada oynanır (lobi ayrı dünya olduğu için karışmaz) |
| F3 | Ender Ejderhası'nı öldür veya runner öl | Bitiş + puan |

## G. Irklar
`/kapisma ban <ırk>` ile yasakla, menüden aç/kapat. 13 ırk listesi: `docs/ORIGINS_BALANCE.md`. Phantom ve Elytrian listede olmamalı.

## H. Menü ve kurallar
| # | Yap | Görmelisin |
|---|---|---|
| H1 | Nether Yıldızı → ana menü | Sadece oyunlar, takım modu, Ayarlar, Başlat (ırk ayarı burada yok) |
| H2 | Ayarlar | Irk modu, yasaklı ırklar, kurallar, seed sınıfı, host devri |
| H3 | Kurallar → PvP kapat, oyunu başlat, bota vur | Hasar yok (UHC, Tombala, Manhunt) |

## I. Tombala bilgi sistemi ve harita
| # | Yap | Görmelisin |
|---|---|---|
| I1 | Tombala başlat, kartı aç | Eşya ipuçlarında "biyom: …" / "sandık/yapı: …"; hiçbir kart eşyası alanda imkânsız olmamalı (örn. çöl yoksa kaktüs yok) |
| I2 | Kartta sol alttaki dürbün (Bölge biyomları) | Alandaki biyom ve yapı listesi, koordinat yok |
| I3 | Yürürken alt çubuk | Takım durumu + `⌖ biyom adı` |
| I4 | HUD modu ile UHC/Tombala | Sağ üstte mini harita: kırmızı sınır karesi, sen ve takım arkadaşı; UHC'de daralma/yeşil son sınır |
| I5 | Çantada "Bölge Haritası" | Biyom önizlemeli harita |

## J. Katil Kim
| # | Yap | Görmelisin |
|---|---|---|
| J1 | Menüden Katil Kim, 2+ oyuncu, Başlat | Rastgele biri "KATİLSİN", diğerleri "MASUMSUN" başlığı; kimse kimin katil olduğunu yazıdan göremez |
| J2 | HUD modu açıkken | Herkesin skin'i aynı; isim etiketi yok |
| J3 | Masum öldür | "☠ X öldü ve elendi. Kalan masum: n"; ölen seyirci olur, katil kim yazılmaz |
| J4 | Katil öl | Yeniden doğar, seyirci olmaz |
| J5 | Seyirci yazı/ses | Sadece diğer seyirciler duyar; canlılar duymaz |
| J6 | Tüm masumlar ölür / Ejderha ölür | Katil kazanır / masumlar kazanır; skin'ler eski haline döner, katil adı sonuçta yazılır |
| J7 | Simple Voice Chat | Yakındaki canlıları duyarsın, uzaktakileri duymazsın |
| J8 | Ayarlar'da `compass` açık | Çantada Yakınlık Pusulası; action bar'da isimsiz `En yakın: N blok · en uzak: M blok` |
| J9 | `voice` kapat | Oyun içinde kimse kimseyi duymaz (seyirciler hariç değil: seyirci de kapalıdır) |
| J10 | `deepVoice` aç, iki kişi konuşsun | İkisi de aynı kalın tonda duyulur; kapatınca kendi sesleri |
| J11 | Başlangıçta 60 sn koruma | Kimse kimseye hasar veremez; katil donmaz/kör olmaz, kimse farklı davranmaz |
| J12 | Başlangıç | Herkes ortak noktanın ±100 bloğunda (200x200) rastgele, birbirinden uzak doğar |
| J13 | Masumu öldür (kılıçla) | Elenir, seyirci; "☠ X öldü. Kalan masum: n". Aynı kişi lavdan ölürse de elenir (`natureEliminates` kapalıysa yeniden doğar) |
| J14 | Katili öldür | Yeniden doğar, elenmez; oyun devam eder |
| J15 | Ejderhayı öldür | Masumlar kazanır; katil adı sonuçta yazılır, skin'ler eski haline döner |
| J16 | Tüm masumlar elenir | Katil kazanır |
| J17 | Masum masuma vursun | Normal hasar (katil ele verilmez); kural kapatılırsa hasar yok (önerilmez) |
| J18 | HUD modu olmayan bir oyuncu | Oyun başında herkese "kapisma-hud modu yok" uyarısı |

## L. Hile koruması (gerçek istemci ile)
| # | Yap | Görmelisin |
|---|---|---|
| L1 | UHC'de x-ray doku paketiyle yeraltına bak | Cevher görürsün ama kırınca taş çıkar (sahte); gerçek cevherler gömülüyken görünmez |
| L2 | Bir tünel kaz, yan duvarda cevher olsun | Tünele açılan cevher gerçek ve kazılır |
| L3 | Mağaraya gir | Yürüdükçe ~10 blok ötesi açılır; duvara/zemine çarpma, görünmez duvar olmaz |
| L4 | Yüzeyden mağara ağzına bak | İlk birkaç blok görünür, derini yürüdükçe açılır |
| L5 | `antiXray` 0 yap, oyunu başlat | Dünya tamamen normal görünür |
| L6 | Nether'de x-ray | Sadece cevher koruması: antik kalıntı gerçek yerinde görünmez |
| L7 | Çok hızlı düş (derin boşluk) | Zeminde takılma/geri çekilme yok |

## M. Tombala
| # | Yap | Görmelisin |
|---|---|---|
| M1 | Kart: elmas/demir eşyalar | Demir ★★–★★★, elmas aletler ★★★+ ; yapı malzemesi kolay ama uzaksa yıldız artar |
| M2 | `tasks` 3 | Kartta "Görev: …" kareleri; ilerleme tamamlanınca kare alınır |
| M3 | `difficulty` 1 / 3 | Kolay eşya oranı artar / azalır |
| M4 | Herkes aynı noktada | Başlangıçta kimse avantajlı değil |

## N. Ses
| # | Yap | Görmelisin |
|---|---|---|
| N1 | Lobide ve her modda yakın mesafe | Yakındakileri duyarsın |
| N2 | UHC/Tombala duo, takım arkadaşı 300 blok uzakta | `teamVoice` açıkken yine duyulur; yakındaki düşmanı da duyarsın |
| N3 | Elen | Seyirci olarak sadece diğer seyircileri duyarsın |
| N4 | Katil Kim `deepVoice` | Herkes aynı kalın tonda |

## K. Parkur v2
| # | Yap | Görmelisin |
|---|---|---|
| K1 | Her bölümde merdiven kulesi | Merdivene ileri basılı tutarak çıkılır, üstte platform |
| K2 | Kızıltaş köprüsü (ORTA, ZOR, ÇOK ZOR) | Platformlar sırayla belirir/kaybolur; 0,5 sn önce cam |

## L2. Oyuncu hile koruması (gerçek istemci ile, 2 kişi)
| # | Yap | Görmelisin |
|---|---|---|
| L8 | UHC başlasın; arkadaşın bir dağın/duvarın arkasında dursun (20+ blok), sen bak | Onu hiç görmezsin (isim etiketi, gölge, kılıç sallama yok); duvar kalkınca/köşeyi dönünce yanına varmadan belirir, göz kırpmaz |
| L9 | Aynı yerde arkadaşın cam/yaprak arkasında | Görünür kalır (cam ve yaprak görüşü kesmez) |
| L10 | Final parlama (`glowFinal`) açılınca duvar arkasındakiler | Parlayanlar duvardan görünür |
| L11 | Seyirci (elenmiş) olarak herkese bak | Herkes görünür |
| L12 | `/kapisma debug guard status` (op) | `aktif=true`, gizleme sayacı artıyor; vuruş ihlali yok (normal oyunda `[koruma]` satırı çıkmamalı) |
| L13 | Yakın dövüş, bol tıklama, kenarlarda vur-kaç | Hasar normal işler; yanlışlıkla "engellendi" olmaz (olursa `[koruma]` günlüğünü gönder, payı artırırız) |

## Otomatik testler (ben çalıştırırım)
`bash tools/run_all.sh` → `tools/run_all.out`: player_guard, uhc_basic, uhc_kill, uhc_select, bingo_basic, bingo_lines, bingo_tasks, manhunt_basic, manhunt_runner, katil_basic, katil_rules, katil_scatter, origin_join, balance_check, avian_kit, new_races, lobby_parkour, xray_audit, xray_nether, voice_groups (20) + xray_audit, uhc_basic, bingo_basic LITE profilde (3). Canlı sunucudan ayrı `server_test` kopyasında çalışır. Beklenen: hepsinde "hata satırı = 0", xray satırlarında "SORUN satırı: 0" (authlib "Failed to read value" satırları kimlik sorgusu gürültüsüdür, zararsız).

## Bilinen sınırlar
- Carpet botu yer çekimi uygulamaz: parkurda "düşme" davranışı sadece gerçek oyuncuyla denenebilir (B3).
- Lobi dünyası her sunucu açılışında yeniden kurulur (geçici dünya): lobi önceki oturumdan kalan bir şey saklamaz, puanlar `config/kapisma/scores.json` içinde kalıcı.

## O. Paket ve zayıf cihaz (tablet)
| # | Yap | Görmelisin |
|---|---|---|
| O1 | `python tools/make_release.py`, çıkan sunucu zip'ini temiz klasöre aç, `start.bat` | EULA sorusu → Java bulunur/indirilir → ses modu indirilip doğrulanır → `Done`, `Donanim profili: NORMAL`, `Ilk acilis seed'i havuzdan secildi` |
| O2 | Aynı pakette `/kapisma newworld` (konsol) | 8 sn sonra kapanıp yeniden açılır; `Yeni dunya: seed=...`; `level-seed` değişir |
| O3 | `profil.txt` içine `lite` yaz, aç | `Donanim profili: LITE`, `view-distance=6`, `simulation-distance=4` |
| O4 | `python tools/test_pack.py` (+ `--sh`, `--lite`) | "PAKET TESTI: BASARILI" |
| O5 | İstemci zip'i: `kurulum.bat` | mods klasörüne 4 jar; eski sürümler `_kapisma_eski`'ye taşınır |
| O6 | Android tablet (Termux): `pkg install openjdk-21 curl unzip`, zip'i aç, `bash start.sh` | Aynı sunucu; arkadaşlar tabletin Tailscale IP'sine bağlanır |
| O7 | Telefonda 7 sahte oyunculu stres (2026-11-17, Dimensity 7200, LITE, 3 GB): `python tools/stress_phone.py --key <anahtar> --eula` | Ölçüm: boş lobi MSPT ~20, UHC doğuş/koşu ~35-55 (TPS düşmeye başlar), toplu PvP ~25; açılış 11-14 sn; sunucu ana parçacığında kapışma modu ~0 örnek (yük: dünya üretimi, Origins, Carpet). Sahte oyuncu üretilmemiş bölgeye koşunca senkron chunk beklemesiyle 10-80 sn takılma olur (gerçek oyuncuda olmaz). Tablette aynı komut sonucu karşılaştır |
| O8 | Tablet sınıfı simülasyonu (6 GB cihaz: 2,5 GB yığın, 4 yavaş çekirdek, profil otomatik → LITE): `python tools/stress_phone.py --key <anahtar> --eula --profile auto --ram 2560 --cores 0-3` | Yeni dünyada ilk açılış ~104 sn (sunucu 44 sn). UHC doğuş artık ortalama ~19 ms; "Dünya hazırlanıyor" ~70 sn sürer ve tek donma ≤15 sn (düzeltme öncesi 60 sn zaman aşımı sonrası senkron üretimle 34 sn donma → gerçek oyuncular düşerdi). Dağınık keşifte 7 oyuncuyla MSPT ~100-130 (TPS ~8): zayıf tablette 7 kişi geniş keşif kaldırmaz; 4-5 oyuncu veya sınırı küçült |

## P. Kurulum pencereleri (Windows)
| # | Yap | Görmelisin |
|---|---|---|
| P1 | `python tools/make_release.py --full --offline` sonra `python tools/test_wizard.py --boot` | "KURULUM PENCERELERI TESTI: BASARILI": düğmeye tıklayınca tek kurulum, ikinci tıklamada/ikinci açılışta kapalı; modlar `<.minecraft>\mods` içinde, eski sürüm `_kapisma_eski`'de, yabancı modlara dokunulmamış; ikinci çalıştırma hiçbir dosyayı değiştirmiyor; ayarlanan sunucu açılıp beyaz listeyi uyguluyor |
| P2 | (GERÇEK, bir kere) `dist/kapisma-oyuncu-v0.1.0.zip` → çıkar → `Kapisma-Oyuncu-Kur.bat` | Büyük **KUR** düğmesi; basınca günlükte "Doğrulandı: 4 mod doğru klasörde" ve "KURULUM TAMAM"; düğme **KURULDU**. Pencereyi kapatıp tekrar aç: düğme baştan KURULDU (kapalı). TLauncher'da sürüm listesinde Fabric 1.21.1 → Oyna → Mods'ta Kapışma HUD / Origins / Voice Chat |
| P3 | (GERÇEK) host: `kapisma-sunucu-v0.1.0.zip` → çıkar → `Sunucu-Ayarla.bat` | Adlar + RAM + EULA kutusu → **KUR VE BAŞLAT**: güvenlik duvarı (UAC'a Evet), sunucu penceresi açılır, adresler yazılır; listede olmayan bir adla girmek reddedilir |
Sınır: TLauncher'ın kendisi ve UAC penceresi otomatik denenemez; P2/P3 gerçek bilgisayarda bir kere elle doğrulanmalıdır. TLauncher, eklenen sürümün kütüphanelerini ilk açılışta indirir (internet gerekir). TLauncher'da oyun klasörünü özel bir yere ayarladıysan pencerenin altındaki "değiştir"le o klasörü seç. PowerShell engelliyse pencere açılmaz; `elle_kurulum\kurulum.bat` (oyuncu) / `start.bat` (host) aynı işi yapar.
