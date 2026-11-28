# Kervan Baskını – bağımsız mekanik adayı (27.11.2026)
Java integration staging üzerinde eşzamanlı mağaza testi değişiklikleri görüldüğünden ayrı dizin seçildi. Ana staging kaynaklarına, canlıya, RTS'ye veya KOD build'ine yazılmadı. Otomatik merge/aktivasyon yapılmamalı.

## Gerçek doğrulama
Yerel JAVA_HOME javac --release 21, mevcut MatchRoster.java, dört aday kaynak; yalnız bu dizindeki build/ sınıf çıktısı. Java -ea çalıştırıldı; son Gangway terminal exit=0.
- CaravanCargoRulesTest: 32/32 PASS.
- CaravanRouteRulesTest: 21/21 PASS.
- Birleşik 53/53 kontrol. Bunlar saf Java testleri; gerçek Minecraft sunucusu, Android, istemci ağı veya fiziksel insan testi değildir.
- Kaynak SHA256 (yazım sonrası araç çıktısı): CargoRules ca7d29dcf7258aa5c479f91b24b37f8ec48401f8fceb3a65ffde05e21ec002ab; CargoRulesTest c5fd40a67369dc844013bcb7fd5bf637f9932b36284e49378e4b82a08a0b33f7; RouteRules a9c042086fd0a40f01ec6cfa4e2dec588a421974702fbbfc5f193827abe2be59; RouteRulesTest 80afc199ad6c5764b83fa848bd319ee8a885c4b8f6660934f724513632969314.
- İndirme, Gradle, ürün JAR/ZIP, sunucu başlatma yok.

## Mekanikler ve geçici kararlar
Tekil sandık kimliği, tek taşıyıcı, saldırganın arabadan alması, yere bırakma, ölüm/kopuşta son doğrulanmış konuma düşürme, savunmacının düşmüş sandığı alıp arabaya geri taşıması, saldırganın belirlenen teslim noktasına götürmesi. Sonuç terminal, kopya alma/replay reddi. Kervan ayrı varlık, çalınamaz. Rota çekirdeği sabit hız, iki ortak başlangıç/bitişli güzergah, kısa riskli rota, sürücü yetkisi ve değişimi, boş takımda pause ve tekrar edilen tick reddi. Rota yalnız hareket öncesi sürücüce seçilir; bu geçici tasarım kararıdır. Teslim noktası ve tek sandık/oyuncu kuralı da geçici uygulama kararları. Test hız/mesafe/sandık sayısı nihai ayar değil.

## Entegrasyon kapıları
- Ana NativeArenaGame / CaravanRules mevcut iki-leg skor sistemini yeni modellerle uzlaştır; henüz bu aday bağlı değil.
- Gerçek araba ve sandık entity/interaction, kimlik eşleme, item duplication / drop / pickup / teleport / death / reconnect / stale packet koruması. Her işlemden önce taze sunucu gözlemi şart; pure core stale observation veya sahte server-adapter verisini kendi başına doğrulayamaz. Güvenli zemin/arena bounds kontrolü, düşülen sandığın erişilebilir yerde kurtarılması ve harita/topoloji doğrulaması zorunlu.
- Sürücü intent'i gerçek Minecraft girdi/mount ve yetkili oyuncudan doğrulanmalı; client pozisyon/rota ilerlemesi kabul edilmemeli. Segmentlerde gerçek çarpışma, tepe/tünel açıklığı ve fizik; arazi ve riskli rotanın saldırgan spawn'a yakınlığı ayrı kabul.
- Elde canlı rota haritası, taşıyıcı hareket hızı değiştiricisi ve cleanup, atlar, görsel araba, kanca (30sn / 300 blok geçici limit, host tırmanış ayarı) henüz yapılmadı.
- Mod özel öğeleri ve YusufTe modları indirme yasağı nedeniyle ertelendi.
- İki takımın tamamlanmış doğal maçı, sandık teslim/geri alma ve ölüm/kopuş senaryoları gerçek server ile test edilmeli. Üretim R38 ABI ve güncel MOD overlay tam açılışı ayrı kabul.

## 27.11.2026 — maç orkestrasyonu ve harita adaptörü
- Tasarım kaynakları: ../../java-integration/README.md satır 104 (güncel kullanıcı kararları), ../../../docs/MOD_PROTOTIP_ENTEGRASYON_2026-11-26.md, ../../../docs/OYUN_MODU_ARASTIRMASI.md, ../README.md. Güncel kullanıcı kararları eski prototipten üstün.
- CaravanMatchRules: iki raund, rol değişimi, taze gözlem ve raund kontrolü, taşıyıcı/sürücü ayrımı, timeout, tüm sandıklar çalındığında bitiş, pause, beraberlik. Geçici skor sıralaması: varış > normalize ilerleme > korunmuş sandık > süre. Kullanıcı kesinleştirmeli; nihai denge değil.
- CaravanSpeedAdapter: gerçek Minecraft MOVEMENT_SPEED transient modifier, taşıyıcı hızının %65'i, taşıyıcının binekten indirilmesi, clear; ana lifecycle'a bağlanmadı.
- CaravanMapRaster ve CaravanMapAdapter: 128x128 vanilla FILLED_MAP, iki rota, seçili güzergah, başlangıç/bitiş, canlı kervan konumu, 5 tick güncelleme üst sınırı, kapatınca temizlenir. Ortak harita oyuncu konumu göstermez; kişisel konum ve topoğrafya henüz yok. Vanilla map ID yeniden kullanılmıyor; uzun süreli map saved-data yaşam döngüsü ayrı doğrulanmalı.
- verify_local.py: mevcut mapped Minecraft 1.21.1 + yerel Fabric cache, Java 21 11 kaynak PASS. İlk hız adaptörü derlemesi AttachmentTarget referansı eksikti, mevcut remapped_mods classpath eklenerek düzeltildi. Son Exit 0: Cargo 32 + Route 21 + Match 33 + Map 14 = 100/100 saf Java kontrolü. Minecraft adaptörleri compile-only; gerçek sunucu/istemci testleri yok.
- Aynı mevcut aday build klasörü (24 dosya / 126159 bayt, önceki 20 / 110837); ana MOD QA build ağacı ölçümde 753 dosya / 236770274 bayt, 10 release ZIP. C: boş alan 127757066240 -> 127756525568 bayt, eşzamanlı süreçler olabilir. Yeni release veya ürün JAR/ZIP, ayrı yeni build dizini, indirme, silme, canlı sunucu/RTS değişikliği yok. Bağımsız küçük javac class çıktısı entegrasyon sonrası saklama politikasıyla yeniden değerlendirilmeli; silme yetkisi varsayılmadı.
- Sonraki adım: gerçek araba/sandık temsilini ve etkileşim eşlemesini kur, güvenli düşüş/geri alma, atlar ve arazi, kanca, native lifecycle. Mağaza test oturumu ile çakışmadan ana entegrasyon. Önce kullanıcıya skor önceliği, sandık teslimi/geri çalma ve rota değişimi sorulacak; belirsiz kararlar geçici.

## 27.11.2026 — kullanıcı onaylı tasarım revizyonu
- Bağlayıcı kararlar ve aşamalı kabul planı: TASARIM_KARARLARI.md (SHA256 032a3cc503853e3b0c90eaffe75211596a1202cdd9e9feb81ec5129968e74c4d); eski geçici kuralların yerine geçer.
- A: Teslim sandığı birincil skor; yalnız hedefe varan arabada ON_WAGON olanlar. Rakip üsse teslim edilen sandık ayrı stolen sayısı. Eşitlikte daha az stolen, normalize ilerleme, kalan süre, tam eşitlik draw — ikincil kriterler geçici. Ortak rota düğümlerinde gerçek yol ayrımı kontrolü, tam düğümde duruş ve sürücü seçimi, mid-edge değişiklik yok; başlangıçta varsayılan SAFE, iç ayrımlarda explicit seçim. Karma rota toplam uzunluğu gerçek kat edilen + kalan mesafe.
- B: CaravanRespawnRules ve CaravanMatchRules bağlantısı: 200 aktif tick, death -> drop/driver release, bekleyen oyuncu eylemleri reddedilir, 1v1 ölü oyuncu bağlantıda ise maç durmaz, kopuş/duplicate death sayacı sıfırlamaz, 200. tick spawn hazır, başarılı spawn sonrası ack; pending timer raund geçişinde korunur, maç bitişinde temizlenir. Ana Minecraft death/spectator/base spawn hook'u henüz bağlı değil.
- C: kişiye özgü vanilla FILLED_MAP ID ve owner guard; kendi konumu, kervan, rotalar, kuzey işareti, ızgara, arazi yükseklik bantları ve 5 blokluk kontur ayrımları. 32x32 gerçek heightmap sampler, ortak sampler başına 32 örnek/tick, yalnız yüklü chunk, bilinmeyen yükseklik uydurulmaz. Renk/çizim aday; görsel kalite, kişiye özel map item transferinin engellenmesi, gerçek client paket, saved-data ve Android performansı kabul edilmedi. Map çizim testinde kayan nokta sınırında 1 piksel hata bulundu, örnek merkezi piksel içine kaydırılarak düzeltildi.
- Son verify_local.py Exit0: 14 Java kaynak mapped Minecraft 1.21.1/Fabric cache ile derlendi; Cargo32 + Map22 + Match42 + Respawn13 + Route29 = 138/138 saf Java assert. Bu 100 önceki +38 yeni; gerçek sunucu kabulü değil. Aynı küçük candidate build 27 dosya/149517 byte, önceki 24/126159; ana MOD QA ağacı 753 dosya/236770274 ve 10 release ZIP aynı ölçümde. C: başlangıç127751143424 -> bitiş127752613888 byte boş; eşzamanlı süreçlerden etkilenebilir. Yeni release, indirme, silme, yeni build dizini, canlı/RTS değişikliği yok.
- D açık: gerçek araba/sandık/at, güvenli zemin ve spawn, ana NativeArenaGame lifecycle ve map eşya güvenliği, fizik/terrain route, kanca; sonra izole gerçek Minecraft server + native client doğal iki raund, ölüm/bağlantı, tam harita görüntüsü ve Android. Ana MOD staging ile koordinasyon gereklidir. TASARIM_KARARLARI'na göre devam et; net olmayan tasarım tercihlerinde kullanıcıdan teyit al.

## 27.11.2026 — D1 dünya varlığı ve güvenli zemin adayı
- CaravanScenePlan (SHA256 8aab16ca1cf2a8ce79a9dd1d9667ed2ea524bd4a79a0af3c9391025b014ec981): maç/raund kaynaklı immutable entity görünümü, tek wagon + tek wagon cargo tıklama hedefi, yerde/taşınan sandık ayrı, taşıyıcı görseli tıklanmaz, eski görünüm/raund reddedilir. 25 pure scene assert.
- CaravanSceneAdapter (53f4d5977563b4bce0601249232c8366563920c493d527db300bdd79ce554061): gerçek vanilla BlockDisplay + Interaction türleri, entity UUID ve instance/level/LOS/mesafe kontrolü, taze sunucu roster observation, yalnız sahip olunan hedefler, doğrudan item/chest inventory yok. Görsel entity yeniden eşleme ve close/discard, raund geçişinde eski entity silinir. **Derleme kabulü, gerçek runtime değil.** Görsel araba bloklardan ilk prototip; fiziksel binek veya W tuşu/mount hareketi henüz yok.
- CaravanWorldSafety (34b3d8a9a5bd0a69909595ddeee3bef00be6c5ba32b9fb56e0347b6ac706d63c): yüklenmiş chunk, arena border, blok collision, zemin desteği, sıvı yok; 0.2 blok ara örnek ile wagon adımı; scene.tick -> gerçek collision gate. Düz/uygun basamaklı zemin adayı; rampada fizik kabulü açık. Nearest drop bounded ±3 yatay/-4..+1 düşey, 8 blok mutlak mesafe sınırı; relocateDropped yalnız DROPPED ve aynı raund/beklenen konum, sandık kimliği/puan korunur. Yer bulunamazsa exception, host güvenli stop/cleanup uygulamalı; bu host hook henüz yok.
- verify_local.py Exit0: 18 kaynak gerçek mapped Minecraft 1.21.1/Fabric cache derlemesi. Cargo32 + Map22 + Match42 + Respawn13 + Route32 + Scene25 = 166/166 saf Java assert (önceki138 +28). Scene25 içinde 4 safe drop testi, Route3 yeni gate testleri. Sunucu/istemci üzerinde entity spawn/use/LOS/çarpışma ve performans henüz koşulmadı.
- Build kuralı okundu. Aynı aday build 37 dosya/195936 byte (önceki 27/149517), ana MOD QA ağacı 753 dosya/236770274 byte, 10 release ZIP sabit. C: başlangıç127749431296 -> bitiş128739233792 byte boş; eşzamanlı işlemler var, fark MOD'a atfedilmez. Yeni ürün JAR/ZIP veya ayrı build dizini yok, silme/indirme yok. Canlı/RTS kaynaklarına yazılmadı.
- D2 sonraki: host lifecycle entegrasyonu (NativeArenaGame opt-in, UseEntity callback, gerçek sürüş input, fiziksel binek/at, güvenli üs spawn ve map item guard); arazi ve kanca; izole gerçek sunucu runtime. Mevcut NativeArenaGame okunmuş ama ortak dosya değiştirilmemiştir. TASARIM_KARARLARI.md bağlayıcı.

## 27.11.2026 — D2 ana maç entegrasyonu ve kesinti sonrası kanıt
- NativeArenaGame opt-in kapisma.experimentalCaravan2 (varsayılan kapalı), CaravanHostAdapter, CaravanMountAdapter, CaravanTerrainLayout, CaravanGrappleRules/HookAdapter; gerçek world, map, kit, horse, death/respawn, iki raund, dönüş. ArenaCommands gerçek UseEntity/UseItem event, /arena kervan guvenli|riskli|birak|teslim, host lobide kanca otomatik|manuel ayarı. Rota sürüşü sabit .075 blok/tick (geçici denge değeri), 20 sandık ve 6000 tick/raund geçici. 73x49 arazi, güvenli tünelli rota 72 blok, riskli daha kısa. 300 blok üst sınır, mevcut küçük arenada efektif menzil sınırla kısalır. Kanca 600 tick cooldown; manuel pulse mekanik kabul, otomatik gerçek runtime kabul. Harita sahibi ID ayrı, genel ekipman kilitleri uygulanıyor.
- İki FAIL korundu: ilk testte vanilla mount teleportu dış teleport sanılıp driver silindi; ikinci testte vanilla hareket tick'i zza sıfırladı. Düzeltme: yalnız server-owned mount transition sırasında teleport invalidation istisnası; ArenaTeleportMixin handlePlayerInput TAIL, owner/seat ve finite .1..1 ileri input, en fazla 5 tick tazelik. Test fixture gerçek ServerboundPlayerInputPacket işleyicisi, istemci ağından gelmiyor.
- verify.py Exit0: 74 kaynak, yeni Kervan 199/199 pure assertion (32+22+22+42+13+32+25+11); önceki MOD regresyon da geçti, KOD class hash guard PASS. verify.py SHA FA1536D0AC5A7B76889D47B93F268C4B8C8EC0762DA5F1BD7035BD409C9ABA9F.
- Üçüncü named Fabric gerçek sunucu koşusu (cmd-9e6cf4d04b640411) bağlantı kesilmesinden sonra da bitmiş. Diskteki kanıt incelendi: acceptance_runtime_caravan-0634cef44b4b.log SHA 9E6EA60415D0CAC03B6797F5D54F1A21B420ACACE899F1E536B3D74F300E3217; acceptance_caravan_console_d2_v3.log SHA 9C4BACA4241C4275763B2626B938B87B7C51C09188775637B0AAE110F1C63020. 22 PASS satırı, iki Carpet gerçek server-side fake player, gerçek blok raycast+motion, ölüm/drop/recover/respawn, farklı map ID ve at, riskli/güvenli fork, yürüyerek çalıntı sandık teslimi, doğal 19 vs20 BLUE win, iki oyuncunun lobi kit ve journal kapanışı, temiz stop/live-RTS hash guard. Bağlantı kopması nedeniyle command_session nihai exit kodu alınmadı; kayıtlı tamamlanma ve guard kanıtı var. İnsan veya native istemci kabulü değil.
- verify_runtime.py SHA 290A4B764C712EA8B0FCAB563EC0AC7E2D89B36505563FE05870DAAE75CBB3C8; caravan_runtime_acceptance.py SHA 2D2B9A1F60F4455F8A58650620FDC2C0B9AA059DB0C712874DDC92ECB5FCB6CC; NativeArenaGame SHA 2FBF58640695A7D02AACAD53901EFEE36327F3E09F764F2B92EE77C589548CB0; ArenaTeleportMixin SHA DABA8874AEC103A0016D82BCE51736A622F3C21D813BD7733E08EE227D0F4844.
- Bağlantı yeniden kurulduğunda C:127549284352 boş byte, ana MOD QA build 811 dosya/237168214 byte, bağımsız aday 42 dosya/224918 byte (önceki derlemenin küçük çıktısı), release 26 dosya/87534634 byte 10 ZIP. 25580 listener yok, journal yalnız başlık. İkinci küçük candidate build yeni teslim paketi değil; ana compile artık bu kaynakları içeriyor, ileride yalnız bağımsız compile gerektiğinde yeniden kullanılır, otomatik silme yok. Yeni indirme/ürün build yok.
- D3: kişisel map gerçek inventory/packet koruma, manuel kanca runtime, disconnect+respawn, iki native istemci gerçek input/render/harita, Android ve üretim remap. Arazi ve vagon görünümü ilk prototip, görsel kalite insan onayı açık. Kullanıcı internet sorununu çözdüğünü bildirdi; yine de indirme gerektirmeyen yerel test tercih edilir.
## 27.11.2026 — D3 güvenlik ve gerçek istemci yeniden bağlantı kabulü
- Harita projeksiyonu bütün 73x49 arena sınırını içeriyor, iki üs ve dört köşe self pikseli doğrulandı; arazi/grid koyu, self/wagon/seçili hat parlak. 6 yeni assert: Kervan 205/205, 74 kaynak. Harita 28/28. Genel ekipman kilidi üzerinden drop, THROW, cursor -999 gerçek handler denendi.
- İlk --caravan-security koşusu Carpet spawn normal boyutu zorladığından tekrar bağlanmada güvenli maç stop; başarısız kanıt korundu. İki gerçek Java JVM (ModNativeQA1/2) ile aynı test tamamlandı. Native klientler gerçek ağ bağlantısı; item/interaction ve envanter handler çağrıları konsol QA fixture, insan girişi değil.
- acceptance_runtime_caravan-4cb89557ebc1.log ve acceptance_caravan_security_native_console.log: 14 PASS, gerçek ayrı map pixel hash ve self pixels, harita drop/throw/cursor koruması, manuel kanca tekrar basma, disconnect cargo drop ve 1v1 freeze, reconnect map ID + cooldown korunumu, spectator timer bypass yok, iki lobby return, journal boş, temiz stop, live/RTS hash sabit. Dosya SHA256 kanıtı:
93ECE293311B5A79AFE4005C5B976EB711EF12425060D483CC3AE7E8972391E9 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-4cb89557ebc1.log
A7EDE52928D96FD80CDFCB8C8687A40322FF39BC5857A72EE12EB0E88658797D %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_security_native_console.log
4164BD20FDD2FAFE69BE989D52D9B9CCC9316A6DBE00C5ADC9036F35B2C18ADB %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan_security_acceptance.py

- Başlangıç C:127540518912 byte boş; ana QA build 819 dosya/237015072 byte, eski bağımsız compile 42/224918 byte, release26/87534634 byte 10 ZIP. 25580 boştu. Yeni product build veya indirme yok.
- D4: --caravan-native, iki native istemci vanilla W input (mevcut flightqa yürüyüş aracı) ve gerçek engel; mount/route konsol fixture, ileri input injection yok. Sonra usability/visual ve üretim/Android açık. Yeni QA script caravan_native_acceptance.py, verify_runtime.py option; test henüz tamamlanmadı.
## 27.11.2026 — D4 gerçek istemci iki raund, blok etkileşimi ve harita örtüşmesi
- --caravan-native cmd-9c8850f1fd3f59cf Exit0. 13 PASS satırı (iki native istemci W tuşu, ilk fork, gerçek taş engel ile 8.975 blokta sabit ilerleme, taş kaldırınca devam, riskli/güvenli sürüş, doğal iki raund 20/20 ve 20/20, lobi kit, boş journal, kapanış, live/RTS hash). Mount ve rota seçimi QA konsol çağrısı; forward fixture kullanılmadı. KOD/RTS/canlı korunmuş. İlk ve ikinci raund native input başarılı. SHA256 kanıt:
4FD16146D86B059CD9AD16E57F4C49A51C3B47B216E494DAC55ABDB2AB8D578F %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-604eaa11d035.log
103422AA61340D895E4EA4030CA1FBB77CACBC3C2EAD123F507A8FC8C7B4EF68 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_native_drive_console.log
0E6636ABCE80C82EA07EAF635F4D28B3394293E76C8F294A86ADDB21E22D1B82 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan_native_acceptance.py
761ADECDD50B77D1D17AC6ED9FFE9F5DAA43070DF4D54096EA3FBC2718538276 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\src\main\java\tr\kapisma\game\NativeArenaGame.java

- D4'te bulunan map selfPixels=0 (sürücü wagon ile örtüşüyor): CaravanMapRaster beyaz halka + altın wagon merkezi, 3 yeni test. NativeArenaGame.useBlock artık tagged hook'a gerçek raycast yönlendiriyor; normal block use deny korunuyor. verify.py Exit0, 74 kaynak, Kervan 208/208 (map31). Blok etkileşimi runtime tekrar kabul koşusu cmd-633cdb69d5e0859c açık.
- D5 plan: mevcut başarılı named adayla yalnız yeni güvenlik kanıtı tamamlanınca map marker gerçek pixel test, harita CPU yükü ölçüm/iyileştirme, fork kullanım kolaylığı, R38/KOD'a güncel kaynak ve manifest handoff; eski live-based intermediary QA JAR kullanılmayacak. Ürün remap/build KOD ile koordinasyon; Android fiziksel cihaz ölçümü ve insan görsel kabulü ayrı açık. Önceki 10 ZIP sayısı korunuyor; yeni teslim build yok.
## 27.11.2026 — D5 harita önbelleği ve sekiz oyuncu
- D4 security v2: 15/15 PASS, clean stop, live/RTS guard. D5 plan D5_UYGULAMA_PLANI.md. Shared terrain SAFE/RISKY cache, personalized frame copy, identical projected position/route/terrain skips full pixel scan, lookup palette and actual differing colors only. 1280 complete cached/reference frame matches = 20971520 pixels. Fork edge-trigger A SAFE/D RISKY, neutral at fork before choice; finite, owner, seated, leg, shift and junction checks; command fallback. 76 source, 227/227 Kervan assertions, KOD guard.
- First eight-player runtime failed strict idle assumption: 361 skip / 10 render / 60 writes; two players moved without fixture movement, overlapping spawn points suspected. Fixed team-local 1.8 block spawn spacing and same allocation at respawn. Second named Fabric test cmd-7192aa6e4a4e291a Exit0: 8 distinct maps/horse/4v4, 371 skip, 0 render, 0 writes, all 8 unchanged; 6078464 pixel iterations avoided. Mounted self marker visible; foreign sideways denied; actual server packet handler selects RISKY only at fork; 244 active ticks 18.300 blocks, all 8 maps visible; lobby and journal empty, clean stop and protected live/RTS hash. Carpet synthetic server-side players, not actual human or eight native clients. 12 PASS lines incl boot/check/return. No Android performance claim.
- Evidence SHA256:
0D75CC923AE4A434422C7A25AF94E9820C35D8B63C8A6D4B2B728B1121FDC729 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-aa2f87028012.log
C62CF22018081354C6F1DDBB53062919DFFAEC0E001F7F75B6C462F29CBFA82C %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_load_d5_v2_console.log
F8F384A9806B0658EC613E5EA12146582AEA44D8C7B10C5EF6B759D705C1C94B %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-67092e84cca5.log
C2DB8A04809D7942E938082D95915186A165BB5DD46D600277409AEB37BD994F %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan_load_acceptance.py

- D6 plan before edits: use existing local client/Fabric API to add a small isolated client test-only helper in current QA build; actual vanilla W/A/D keys, both route choices, no server forward/side input or route command, no production fixture. Inspect client-received map pixels if feasible. Keep current KOD sources/build and live/RTS untouched, no downloads. Then update KOD handoff; production remap coordinated with KOD (old live-based candidate invalid); human/Android gate remains. Existing 10 release ZIP unchanged; no new product build.
## 27.11.2026 — D6 native rota kontrolleri tamamlandı
- Yeni küçük QA helper caravanqa/CaravanClientQa.java, mevcut build/caravan-client-proof/ içindeki 2187 byte JAR SHA256 9BC5AE4FE15BBB5C4B234503B98B0C1A57447DF50D9CFAC0471B4BCD36F68D3C; yalnız normal istemci key-state W/WA/WD, özel packet/movement injection yok. qa_clients.py yeni opsiyonel caravan controller, eski walk default/pin değişmedi.
- cmd-3221ea53fa1fbf31 Exit0, 16 PASS. İki gerçek Java client, mount konsol fixture, gerçek W+D riskli ve W+A güvenli (route komutu yok), fork bekleme, gerçek taş engelde durma ve devam, iki doğal raund 20/20, iki lobi dönüşü, boş journal, disconnect/clean shutdown, live/RTS guard. İnsan testi değil. Sürücü selfPixels15 örneği gerçek sunucuda.
- acceptance_runtime_caravan-dc2585409124.log SHA D58BA9FAB67A6BE271550ED646062E3497BDC0F7DF607B703EDC70F33A31A99A; acceptance_caravan_native_keys_d6_console.log SHA 614EE916F0117F1C2E34BC12E56FF3A5C71E26AD6A85D9D678BAF5CD33B230E1. Script SHA F03C99B7875D3E74E45F85C6E3F75B9384BE611C2AB1D617802F43856845815A. D5 227/227 Java halen son kaynak kabulü; D6 product Java değişmedi.
- Üretim handoff caravan-production-handoff.json, AI_MOD_KOD_YANIT.md kaydı; 17 candidate production class source + 3 merge-review hash. Read-only mapping confirms handlePlayerInput intermediary class_3244.method_12067(class_2851), getters method_12372/12373/12370; not full remap proof. R38 SHA97084836... ve live BB92690D... değişmedi. Opt-in default OFF. KOD koordinasyonuyla güncel merge/remap/production boot gerekir; fiziksel Android, insan oyun/görsel kabulü açık. Son boş alan 127564398592 byte; ana QA build 890 dosya/237677050 byte (küçük test helper dahil), bağımsız compile 42/224918; release26/87534634, 10 ZIP değişmedi. Eski intermediary aday geçersiz, saklanan önceki QA çıktıları ayrı izinle ayırma gerektirir; hiçbir silme/taşıma/indirme yok, ürün build yok. Port25580 listener yok.
## 27.11.2026 — D7 istemci haritası, rehber ve at kabulü
- D7_UYGULAMA_PLANI.md önceden kaydedildi. D7 native map test helper mevcut QA build'de yeniden derlendi, 2641 byte SHA256 8AFA097AF5BE6A76D1595775ACE73B361BDA19DB6227A7A97C13E8DFC9ED643B. Normal hotbar map selection, vanilla client map data 16384 buffer ve Arrays.hashCode karşılaştırması. cmd-8d1bfa6d12d80d15 Exit0, iki ayrı map ID ve farklı hash, her iki sürücü fork'ta self marker >0 ve güncel client hash eşleşmesi, WA/WD, 20/20 iki doğal raund, lobby/journal, clean stop, live/RTS guard. Hash eşitliği, byte-by-byte SHA veya görsel insan kabulü değil. Kanıt:
839F4814CB8C6294198DE5031D8AE5B306128B20153938A2355564BA6CD078AC %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-7ed8bff0df5f.log
9E4828F0C9F132859A0A6BE6741521EC3E0A8ABF938AC113C55F3F66271BA412 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_native_map_d7_console.log
DB45E2AE90DD71EDC0F7F6D20BEB2E1EED7EB8463DB596CC90C3A23DC694B912 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan-drive-ModNativeQA1-93683e7b99bd.log
7F66ECF01D1989F997322E1EB2074D04CD6707506C117BEFD21429CE9ECAA52B %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan-drive-ModNativeQA2-eea08f459a2a.log
972E079B1EA97550E2A72A978BF3379D6D25056DF25EE647E06BAF8757300C58 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan_native_acceptance.py
PASS_COUNT=19

- Sonrasında staged product source üç dosya: MapAdapter yeni lore (4 renk rehberi), HostAdapter role-specific guide ve manualClimb, NativeArenaGame stage/valid reconnect guide. Respawn sırasında sürekli tekrar yok. 76 Java, 227/227 PASS, KOD class guard. 10sn, 30sn mevcut kural; taşıma/binek kanca yasağı geçici tasarım. Yeni --caravan-guide koşusu iki native istemcide role text, lore ve horse actual owner vanilla interact/foreign gate; cmd-8d22d8210d8cccc4 henüz açık.
- KOD'a önceki D6 manifesti değişen üç source ile güncelleniyor; üretim kabul/merge henüz yapılmadı. KOD build/live/RTS korunuyor.
- D7 guide runtime cmd-8d22d8210d8cccc4 Exit0, 10 PASS satırı: iki gerçek istemci her biri tek doğru role guide, manuel kanca, harita gerçek ItemLore 4 satır; actual vanilla horse interact owner mount, other player gate FAIL (console fixture uses event and vanilla interactOn, not mouse packet), lobby return and empty journal/stop/live-RTS guard. Son kanıt/disk:
C_FREE=127737663488
prototypes/java-integration/build count=898 bytes=237531988
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
KAPISMA_ARENA_RETURN_V1
PASS_COUNT=10
42A734D779A894BAE72CC9DC3D59C5CF3FEC63576A9B2A4EEA152E3E5CCB59C3 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-85b4752a55a9.log
81BAE79C465C753C5893CDF3DB14E7B01661874AEEFD7EC35EA11F45409D2142 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_guide_d7_console.log
E92CB4BAFBF4B4BA4451DD44A56987A67A45F587868A0B150398917F1AC116CA %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan_guide_acceptance.py
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

- Manifest 20/20 source SHA256 eşleşti. QA helper mevcut QA build içinde yeniden oluşturuldu, yeni product build yok. Üretim/Android/KOD koordinasyonu ve insan görsel kabulü açık; at sürüş sınır ve uzun stres henüz test edilmedi.
## 27.11.2026 — D8 horse lifecycle
- D8_UYGULAMA_PLANI.md önceden kaydedildi. CaravanHorseRules (raw finite boundary width + rider gate), MountAdapter remembers safe homes, authoritative recall dismount and zero velocity, same UUID, no replacement for non-destroyed removal reason; WorldSafety validates loaded safe horse home; HostAdapter recall on death/disconnect and bounds/inactive, rejects horse-mounted scene action; NativeArenaGame explicit disconnect. New 12 pure checks, initial harness nonfinite Position failure corrected by raw input policy; 78 source, 239/239 PASS.
- Real named server cmd-99856cd6703f6035 Exit0: out-of-bounds/falling horses recalled, same UUID/one entity each, mounted attacker cargo rejected, walking take works, carrier horse mount rejected, death/respawn and actual client disconnect/reconnect same horse home, lobby return/journal empty/clean shutdown/live+RTS guard. Boundary/death manipulations server console fixture; two actual native clients, not manual riding or human. Mounted boundary ejection and long-duration native horse driving separate open checks.
C_FREE=127730364416
prototypes/java-integration/build count=903 bytes=237565446
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=11
5D74C5B2FE8E37100B5DF279C6210F5ADCA79C732125DB14CD2CF8A18C6561B2 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-851d9fc84793.log
8E490D5E760FD46BEDCBEC5BCBF9F7F030D7F2D1FD380D5659FF56F1E8112D92 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_horse_d8_console.log
0A5AD9EE0042A59ADF829F7891E45DD5B4C4A33B481E8B0E9EAD5DDEF0581B14 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\caravan_horse_acceptance.py
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

- No downloads/deletions/production build. D8 manifest refresh and exact verification; KOD coordination, Android/visual and human gameplay remain.
## D9 — oriented wagon acceptance
- D9 plan recorded before changes. 14-piece wood cart, four wheels/hubs, rails/bench and single cargo display; same shared heading/transform for model, hit and seat. 1.85 square all-heading radius, 3.4 head clearance; last real segment heading (selection without movement unchanged). Official cached JOML 1.10.5 pinned (initial compile classpath missing fixed, no download). 80 source, 252/252, 21600 corner checks across 360 angles. Both native routes actual geometry and obstacle, map, two full legs, 21 PASS Exit0 cmd-6beb0af55c1045b6. Human render quality not yet accepted.
C_FREE=127721373696
prototypes/java-integration/build count=911 bytes=237788320
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=21
04303CFC3095FB7FDB443C8F32D4A2C9CDFC89FB632F11E5EF9669773440D20A %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-82fc6f54de68.log
80F6E059B59EDDE18FE72A47AFD895FC9C6524DADAE8FC349F67E172F81D3223 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_wagon_d9_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D10 — native horse and client framebuffer
- D10 plan recorded. QA-only helper captures its own Minecraft framebuffer and uses vanilla W/yaw; no desktop capture. cmd-81dabfa395eee844 Exit0: 3 actual native horse run/stop intervals, same UUID, mounted boundary recall eject and same horse home, clean stop and live/RTS guard. Two 640x480 PNG proofs inspected: wagon image largely obscured by 6 chat instructions; map rendered tiny on right due to offhand shield. Not visual acceptance. Both files under build/native-load-clients/1/screenshots/caravan-visual-ModNativeQA1-20b2a8ca1d41.trigger-shot-{wagon,map}.png; SHA wagon CF6466E69FFB651E4CAF728992CD2DF45B042E43CD79C46FC016C42B5E157262, map 073480093AC398787E67AB3823C56AB5FDF62E57190150EF3E7209F9228EF3F4.
PASS_COUNT=10
00162AE0CFEE424EC87FBA55F84390EABA13B79A6854A666547EC7CB62D78559 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-31aea33f0efa.log
7B46972FC859F7CC45DE5A36314AED4DED42FA27EB2819F33529976AEB530DCE %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_visual_d10_console.log

## D11 — visual UX remediation
- Plan D11_UYGULAMA_PLANI.md recorded before edit. Only experimental caravan2 shield starts hotbar slot6 (player slot 7); offhand empty, full vanilla two-handed map. Other modes unchanged. Short role-specific two chat messages, optional /arena kervan yardim, per-player 40 tick guard, full guide retains controls/cooldown/death; shield remains provided. 80 source, 252/252 pure Exit0. D11 repeat actual screenshots and horse/guide test cmd-12d3b6217cabd994 pending. No new product build; existing QA helper rebuilt in place.
- D11 actual Exit0 cmd-12d3b6217cabd994: both real clients short role guide, 7th hotbar shield + empty offhand, actual centered map and unobscured wagon after fade, native horse and mounted recall, request help, lobby/journal/clean stop/live+RTS. 640x480 PNGs build/native-load-clients/2/screenshots/caravan-visual-ModNativeQA2-a3f0a5dda6e0.trigger-shot-{wagon,map}.png: wagon 5D05A5AC012B1F05756FC142FD1D57BCA33476D407CBED80F5504366CA17519B, map 1E1D04D0BF6A4AB70E6F5BFB92060E73F354F2DBADD13A14B5D214CD064BC9D2. Visually confirmed centered map and visible body. Terrain color issue: coarse sampler reads world outside arena, distorts min/max height. D12 scope fix.
PASS_COUNT=13
96872E13C5C52147F60C521D175DE587E0651B427ABFF7C09A97806782D385A4 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-1930bf907756.log
B4EF9A83F752A1B8B2F7A69264547DEA357AA532828871D06C2877202AE07E19 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_visual_d11_console.log

## D12 — bounded arena topography
- Plan recorded before edits. X/Z only arena inclusive, no forced chunk load; stale unloaded columns cleared; heightmap fast path inside slab; if above slab, bounded actual blocking/fluid scan. 3 stable base-relative bands; contour 2 blocks. 80 Java, 268/268 incl 52 map tests and 1280 cache/reference comparisons. D12 actual native visual cmd-aaee431a4b663d64 Exit0: 532 known, 0 outside, low425/mid67/high40, y301–309. Real centered screenshot confirms green valley, contour-separated hills, dark outside, unchanged route markers. PNG build/native-load-clients/2/screenshots/caravan-visual-ModNativeQA2-b878b0eabe87.trigger-shot-map.png SHA256 61480CAE8962780E33D47B621260AB20EC92616F826049F74F3CA998244EA02C. Actual guide/horse/lobby repeated.
PASS_COUNT=14
AC17ED803E0308240144E3E920DDEDD20C6985C45C945FF385004221F4E0E0B1 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-59f34539e256.log
A4FF00ECF6CD451296408EE6BC539A67F603E4E1689B6620418D7FF489B2E60F %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_visual_d12_console.log

- Eight-player cache regression cmd-0eabc9c5d423c7f7 pending. D12 source manifest updated and 22/22 hash verified; production merge pending.
- D12 eight-player cmd-0eabc9c5d423c7f7 Exit0: 4v4/8 maps/8 horses, idle skip368/render2/write12/7 unchanged; 6,029,312 avoided pixel scans; mounted self marker; foreign input denied, risk route, 244 ticks/18.3 blocks; lobi/journal/clean shutdown/live/RTS. Not eight humans.
C_FREE=127380078592
prototypes/java-integration/build count=934 bytes=238575611
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=12
8FBDACD2859C1642782BE526947CE59568EDF52AD355498A05EC5D526DF9F99B %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-ca43e831b83c.log
C8B4E9DCD8D78EB71A8F8115D2A18867217CEB3D72E0EB285199D18E749CC109 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_load_d12_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D13 — map base and extraction symbols
- D13 plan before edits. Public fixed-role physical bases only, hollow 24px purple square escort and 16px orange diamond attack extraction; no player data. Markers render in shared static cached frames before player/wagon, overlap tested. Both route variants, six new map checks, 58/58 map and 274/274 Kervan, 80 Java sources. Actual two native client map symbols/lore, bounded terrain, both screenshots inspected, horse movement/recall, guide/lobby/clean shutdown. cmd-b95dc97a6d9e0b4d Exit0; 15 PASS. Actual map screenshot build/native-load-clients/1/screenshots/caravan-visual-ModNativeQA1-2d4bd6a5ca2f.trigger-shot-map.png SHA256 0EA68C35B6DC09DBE1AA6082BBF9BA86A11C6A897035E48BA9AB39A1BF291234. Actual rendered purple and orange symbols clear. No human gameplay verdict.
C_FREE=127372750848
prototypes/java-integration/build count=939 bytes=238851578
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=15
1DAA6FFEC134556C70A2E44D867FC19DB2FB62C92684AE8234DA248570185E87 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-eccbe486fa03.log
697FA7825AA2B50E95FF723458416513B69552C910D2305E500B52619D1C7393 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_visual_d13_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

- Existing QA output reused, no new product build, no downloads/deletions. Existing 10 ZIP and old unused build outputs retained (prior storage rule issue remains; no deletion/relocation authorized here). Production merge, remap and Android physical acceptance still pending KOD coordination. D13 manifest source hashes refreshed.
## D14 — result explanation, HUD and driver lifecycle
- Plan recorded before edits D14_UYGULAMA_PLANI.md. Exact round summaries once per side and one final winning criterion, including leg ended outside tick; existing delivered/stolen/progress/time scoring unchanged (tie-break provisional). HUD distinguishes pause, intermission, carrying, respawn and driver-only fork. Filter caravan-specific observations to connected players (spectators still count online); reset fork latch/forward intent on lifecycle and disconnected pause. First compile failed missing test static import; corrected, 80 sources and 283/283 checks, 49 match + 16 fork. Native acceptance uses actual W/A/D and attacker normal W walking with yaw, controlled mount/cargo callback and setup teleport. Both natural rounds 19 vs20, one native walking extraction, exact outcome and each score once on both clients; clean return/journal/server stop and live/RTS guards. cmd-96452242be05f5c9 Exit0. Human balance and physical Android not verified. Evidence/disk:
C_FREE=127124807680
prototypes/java-integration/build count=944 bytes=239115805
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=23
419CDFFD8EF5FD36E4BFDA7E6906BEC1EAF5E4856EB708457BE3BD01C07CF233 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-bb319c698e81.log
64DF18D65B86568A5B866E1744FBE5A78405D21F5D415FFF01A5D774A0793721 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_score_d14_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

- Existing named QA target reused, no downloads or new product build, no deletion/relocation. Prior 10 release ZIP and old unused outputs remain. Production integration/remap pending KOD; manifest refreshed.
## D15 — three consecutive matches, eight-player contested cargo and cleanup
- Plan D15_UYGULAMA_PLANI.md saved before edits. Only QA fixture/harness changed, no production Java changes. 80 sources, 283/283 pure, voice 9/9. Same Fabric JVM, same eight Carpet players, three starts and stops; not humans, not three full natural matches. In each cycle, same target four callbacks in one server tick => exactly one successful pickup, then four distinct carriers total, replay attempts denied. Deliberately stop with active driver and four carriers. Actual captured 30 entities per cycle all removed and unregistered, eight map buffers all blanked, forward fixture cleared, lobby inventories and empty journal. 24 distinct horse UUIDs and map IDs across cycles. cmd-c3bed28ea6c19fd9 Exit0, 18 PASS lines. Server clean stop, live/RTS guard. This proves captured entity cleanup, not heap leak absence; vanilla map IDs/data remain persisted and are not recycled for privacy. Long-duration heap/MSPT and persistent map growth measurement not done.
C_FREE=127126474752
prototypes/java-integration/build count=1000 bytes=238996793
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=18
4912C9BA4A243244191F5327B5242AD4A84D5050B9D72F3F6A75A6FFC6D5D7EC %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-4183fa07b243.log
8681DE29319CE6BB79D4627AA2CE5304766B432E1C26B66BB06182BAB762F850 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_repeat_d15_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

- Existing QA target reused; no new product artifact, no deletion/download. Existing 10 release ZIP and unused build outputs remain; production merge/remap and Android/human acceptance pending. D14 product hashes unchanged.
## D16 — ephemeral map storage
- Plan D16_UYGULAMA_PLANI.md before changes. New CaravanTransientMaps server-thread registry + ArenaTransientMapMixin HEAD getMapData on only registered active map IDs for owning level. Vanilla unique allocator kept; no setMapData for caravan, no saved-file deletion or reuse. Close blanks data then releases exact registered instance. Normal vanilla lookup fallback unchanged. 82 Java sources, 283/283 pure, KOD guard. D16 repeated actual server cmd-b43d88aec59625f2 Exit0, 24 distinct IDs, zero map files for these IDs after active and lobby save-all flush, all 24 old lookups null, registry zero each closure, vanilla control map persisted. 90/90 captured entities unregistered, 24 blank, journal empty, contested same target 1/4 each, 3 start/stop cycles eight Carpet players. Active mspt 1.441334/1.160977/1.173576, p95 2.1958/1.8728/1.5542 ms; heap used 587/418/593 MiB, lobby436/607/421 MiB, committed704 MiB. Short sampling not RSS or heap leak proof; map index increments and prior historic files remain. Test lookup of old ids may populate vanilla negative-cache entries; not persistent payload. Native full-match regression cmd-7281db6bdcdaa448 currently running; do not declare accepted until Exit0.
C7A2C8FB97477ABE76DB0537FCEE85DDADDFBEF138D947DCC4F663A8D64E21E9 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-16a3dec7b8aa.log
FE370E33EA52E860DAA0F3A5C364CB3CB9747010968FF6F88D8CD5ECE8487F80 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_transient_d16_console.log
PASS_COUNT=19

- D16 native full regression completed cmd-7281db6bdcdaa448 Exit0: 23 PASS, both actual Java clients distinct map frames and moving/fork map hash match, actual W/A/D, obstruction, actual walking extraction, both natural legs 19 vs20 and exact result once, clean lobby/journal/server. Product handoff 24 source hashes and mixin resource verified. Remaining gates KOD merge/intermediary remap/production boot, physical Android, human and longer heap soak; prior historic map files not removed, possible vanilla negative lookup cache and monotonic ID counter remain. No new product build, downloads or deletion; pre-existing storage rule issue retained.
C_FREE=127123763200
prototypes/java-integration/build count=1005 bytes=239179531
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=23
C1526211D430FAB7884177F20DB08BCFD499BB9872263EFB896C2691410E52DA %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-c1d955ef1008.log
F5533EC96CD1E616EF405690B68BA7A10D75D976DF985A301E6924020CA5F1EC %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_transient_native_d16_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D17 — sustained eight-actor workload
- D16 final manifest and coordination completed before changes. D17 plan D17_UYGULAMA_PLANI.md saved. Only QA Python repeat script changed (optional KAPISMA_QA_SOAK_SECONDS=45, default prior short test retained), syntax PASS, product classes unchanged. Real named Fabric same JVM, three 45.4s active periods, eight Carpet actors, four carrying crates, driver packet fixture selects risky fork and stops around 25.3 blocks; non-carrying escort repeatedly repositioned in corridor for map updates (console fixture, not native movement), others parked. No full natural match or 30-minute soak. Each sample verifies leg, four carriers and sixteen on wagon, mounted driver, original map ID and tick progression. 136.2s active, 30 samples, mean sampled MSPT1.065, max sampled mean1.687, max sampled P95 3.211; JVM heap used289–465MiB, committed497–506MiB. Lobby heap277/459/463MiB and sampled values include GC cycles; no definitive leak verdict. Each match same-target1/4, 30/30 captured entities removed/unregistered, 8/8 blank/released, registry0, 24 unique IDs no persistent map files after active and lobby saves, vanilla map persisted. Exit0 cmd-1c5964b791028d37. No forced GC or production edits. Actual tests and artifacts:
C_FREE=126774599680
prototypes/java-integration/build count=1006 bytes=239118572
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=23
933365D4D1B8C4BCF1C1286A14D95F50745E7F9BE535CDA2E5EA267BDF10A0BC %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-1e327f7df49f.log
29267537B5CA389087B6FE5F03E7F9A735549A240BEF45E06D46D3E106C1CF74 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_soak_d17_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

- Cached mappings: Level.getMapData method_17891(MapId class_9209)->MapItemSavedData class_22, ServerLevel class_3218; static mapping confirmation only, no production remap. Existing QA target reused, no download or product build. Previous 10 ZIP and old build outputs remain; KOD production merge, remap, physical Android and human acceptance still pending.
## D18 — physical objective readability / product quality
- Plan D18_UYGULAMA_PLANI.md before edits. Purple escort floor perimeter, orange extraction diamond within existing 3.5 reach, gold arrival stripe, green safe/red risky approach stripes. Floor-only full blocks; no new item/building, no route, timing or score change. Optional help explains all world colors. Initial terrain test sampled incorrect escort perimeter x=-32 rather than -33; fixed test, 82 Java, 288/288 pure incl terrain16/16. Actual native test cmd-683c4041002a247e Exit0: all five physical ground block colors/air clearance, two actual client framebuffers inspected, clear purple/orange markings; bases visually still plain and world border visible. Two natural legs 19vs20, actual W/A/D, obstacle and real walking extraction, exact results, lobby/journal and clean shutdown. Setup teleport/mount/cargo callbacks are fixtures. Actual PNGs under build/native-load-clients/{1,2}/screenshots/caravan-drive-ModNativeQA1-00d58dac45bf.trigger-shot-wagon.png (1, SHA E7F7B5B08863F6A882112F1E421B799FC36812ECE27C6766CDFBF432344EEDF3) and caravan-drive-ModNativeQA2-b38db459f5d4.trigger-shot-wagon.png (2, SHA 7CA16C0B83F80DA94B1FBCDF12ADFCB1A2E1CB1FAF883724ACDF9BAA695D4812). Visual quality not human accepted. Next product priority decorative role-specific base landmarks outside spawn/road/capture clearance, improve horizon/world border presentation without unfair cover. No new production build/download/deletion.
C_FREE=126611128320
prototypes/java-integration/build count=1010 bytes=239471652
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=26
53397F68304905843B34A6F7B179B2C4F9DE2AC9CC94A089AEF4B6818152DE2F %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-ea424e2332b0.log
B0EE5E13B160B5008CC2B88D0549206E87C407F0611328F0DD250585137F0B5A %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_objective_d18_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D19 — role-specific shelters
- Plan D19_UYGULAMA_PLANI.md before edits. Two open shelters, identical 48 full-block voxel layout per role, distinct spruce/purple and stone/dark oak/orange materials, behind bases outside road/spawn/extraction. 82 Java, 294/294 pure incl terrain22/22. Actual two native client match cmd-8cc57d7e58f8d5c6 Exit0: world materials/clear interiors, all objective marks, inspected actual two framebuffer images, two natural legs 19vs20, walking extraction, native input, clean return and stop. Actual screenshots under build/native-load-clients/1/screenshots/caravan-drive-ModNativeQA1-a45858e5df91.trigger-shot-wagon.png SHA C0ADF942D620A1124690BB515BFB15F909BAD72C93B13A228B3CA73A3BD4AF1D and /2/screenshots/caravan-drive-ModNativeQA2-8249182d38bd.trigger-shot-wagon.png SHA BFCA21EA1B29D2708B4B43211D08E8C5CB8219BD12B495E4BBB911CD6DEFD712. Visual assessment: open shelters match color marks, but plain rectangular appearance and visible world border remain. No human/Android acceptance. No new product build or downloads.
C_FREE=126424985600
prototypes/java-integration/build count=1014 bytes=239755057
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=28
9291F6E0D081E937E5B3BE69478B7BE2872E7F096EFF82D04233FB1ABFB292A7 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-11be5d932850.log
51C98D773E7983BF2E7D0C4BFA4A89AF629C16509CD447056D672E118362A597 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_landmarks_d19_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D20 — geometry analysis and masonry tunnel entries
- D20_TASARIM_ANALIZI.md contains baseline vs after. Safe 72 blocks /48s, risk49.911/33.274s, ground reach2706 both, attack mean33.417 vs20.627, escort29.056 vs35.667. Base-center LOS safe42/72→41/72, risky45/51. Conservative 4-neighbor ground and fixed-height LOS, not human win rate. 83 Java, 303/303 pure, terrain26/26 and analysis5/5. New portals with seven-block clear opening, actual 2 native clients 30 PASS Exit0 cmd-9ddd523acfad69c6; 19vs20 two natural legs, inspected tunnel framebuffer SHA EE797C6281581A3A7B960052BFB312B0926B78175B0D5CE6FADC0DDEC15ED9EB. Runtime SHA 978056267AB26752CD3D6DC77D462C2EAF6F04DD209F5EA01FCAC6AD68AC99FE. Android/human deferred by user, not complete.
## D21 — reproduced spectator teleport issue and fix
- Plan D21_UYGULAMA_PLANI.md saved. Eight Carpet actors; actual named Fabric QA read-only vanilla awaitingTeleport sequence reflection (test source only), direct server hurt damage sources (not client swing/projectile). Baseline cmd-05ed894541dde5aa Exit0: outer slot (-31.2,301,-10.5), delta24 over nominal 1s observation while stationary; base-center sphere excludes its own spawn. Fixed NativeArenaGame waiting spectator anchor to assigned spawnPoint; same scenario cmd-0211c4baf200d6d6 Exit0 delta0. Both runs: friendly damage denied, enemy/environment applied, waiting spectator invulnerable/self camera, forced escape corrected, natural respawn kit/map restored and enemy damage applied. Existing 200 tick timer unchanged. No spawn grace added; damage fixture does not prove spawn camping balance, no break or real bow/melee packet test yet. 83 Java,303/303 plus voice9/9. No product build, no deletion/download; existing extra ZIP/old outputs retained. Evidence:
C_FREE=126037458944
prototypes/java-integration/build count=1052 bytes=239935582
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
acceptance_caravan_combat_d21_baseline.log PASS_COUNT=11
acceptance_caravan_combat_d21_fixed.log PASS_COUNT=11
4BE089BEE141999597AFE39153DBD0D38491CF1F0520D7F54D8039674172173C %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-cf4b69c4fc7d.log
B7F080A8F19883BE35450CD1D8FD94A1BA80AA38E3945BDF369307EE42C29FD4 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-0780809c3376.log
10B51EF13A2CDEBF13E4A59E4B6EF72A6F0A5ABDFEDD6D01E72F7DF5E22C81B7 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_combat_d21_baseline.log
ACC4844CC365087497DAA5023E515F8A70FE75BBCDFBA75E9BDB93A5BCC3D821 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_combat_d21_fixed.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D22 — measured spawn cover and live collider acceptance
- D22 plan and D22_TASARIM_ANALIZI.md recorded. Three simulated screen arrangements before product edit; selected V1 (equal six columns per role, 2 blocks high, spruce/stone brick). Escort visible pairs2184/2448→1531/2448; attacker2312/2380→1506/2380; worst nearest exit4, 4 exit directions within12 steps; reachable2706→2694 solely occupied footprints. Route lengths/mean approach unchanged, attack-base center LOS safe12/72 vs risky19/51. No combat survival inference. 84 sources, 311/311 Kervan incl8 cover analysis, existing terrain26 and voice9. D22 first runtime startup blocked by clean-lobby inventory requirement (last actor join precedes kit initialization); previous session lost, incomplete proof retained. Fixed test harness only: wait1.5s and assert all eight lobby kits before starting; cmd-02e9e85452d91985 Exit0. Actual named Fabric eight Carpet actors, actual both screen materials and gaps, vanilla collider rays BLOCK/CLEAR/BLOCK/CLEAR, friendly damage denied, enemy/environmental applied, spectator stationary teleport delta0, escape constrained, natural respawn and lobby inventory/journal, clean stop/live/RTS. Direct damage fixture, no actual bow packets or human match; full D22 native match and screen framebuffer not yet verified. No new production build/download or deletion, preexisting extra outputs retained; Android/humans deferred. Evidence:
C_FREE=125481730048
prototypes/java-integration/build count=1055 bytes=239943909
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=15
24D92CFB51AA25A7028CB68FAA9ECB86EAEE09F96524EF30758ED9FE00E3D5AB %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-36643796edcc.log
D712DAA4EDAAD42D4B6263FC2A5670DD074C101C4C403AC15F65554203AA2C88 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_cover_d22_v2_console.log
0702BF157AB4F02BF8E4F73254572FFFA3D638FBF4CD04E06FBD5B6D2623868A %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-37c9bd8289ce.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D23 — vertical navigation, extraction and crate landing
- D23 plan saved. New pure CaravanVerticalAnalysisTest: full-block stepped surface graph, cardinal moves with up/down1 and headroom, extraction goals at radius3.5. 3702 standable surfaces, 3648 common reachable from both roles, 954 elevated, max relative feet9; 54 not reachable in model (not necessarily impossible with hook). 10944 standing and +1/+2 air landing samples, old search found landing all, but 5 summit standing cells denied direct placement due maxY10. Expanded generated terrain ceiling HEIGHT+5 bounded by max build height, no changes to search radius, crate identity, scoring, speed, player hook or movement. Safe mean extraction steps33.694/max43, risky21.098/max35; all123 sampled route centers have >=2 local onward choices (not independent full escape routes). Sight sample pairs from 6–20 horizontal blocks, ground y1 vs elevated y>=4: safe ground15159/19573, high2019/5194; risky ground11820/14695, high2687/4320. Geometry only, no actual bow physics/skill estimate. No route geometry change. Actual named Fabric cmd-088b252b555b01b9 Exit0 with eight Carpet actors, same real-world five summit positions: old direct denied5/relocated5, revised direct5/air landing5. QA directly invokes old/new safety instances with identical horizontal bounds; not a full carried crate death on summit. D22 cover and D21 damage/respawn regressions passed. 85 Java 318/318 Kervan incl vertical7, voice9. No claim all arbitrary drop failures eliminated; scene still stops on no safe landing, edge/air states beyond sampled range require further work. No product build/download/deletion. Android/human deferred; D22 native full match/visual and production merge remain unverified.
C_FREE=125468921856
prototypes/java-integration/build count=1057 bytes=239971219
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=16
3EB07BF4B76FBE625DF557DDF695095A08C08C0E63B4EA0B30BAC0ABF6993D7A %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-7fc44935286b.log
CE11EAFA9C4320E52E724212F196D45BEF37E762C9781AF78EFE371FC9027223 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_vertical_d23_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

## D24 — exceptional cargo landing recovery
- Plan D24_UYGULAMA_PLANI.md saved first. Normal bounded <=8 relocation unchanged. Server-only restoreDropped guarded by leg/state/ID/expected position, no client Action; last physically verified anchor or safe current wagon position, status remains DROPPED, no delivery credit. If neither is safe, keep authoritative crate and suppress unsafe interaction projection, retry expensive search no more than once per20 ticks. Clear per-leg and close; warning logged. Emergency may relocate farther than ordinary limit; provisional recovery policy, possible overlap between dropped crates at same anchor (not tested simultaneous retrieval), pending map marker can remain at unavailable location, arbitrary permanent terrain destruction can still prevent recovery until leg ends. No claim of all edge cases or balance. 85 Java, 330/330 Kervan pure incl cargo40/match53 and voice9; 12 new rule tests. First runtime attempt rejected artificial y335 by existing arena vertical boundary (summit death passed). Second attempt y318 successfully restored same crate after actual lethal damage but subsequent test damage fixture mistakenly selected a spectator; corrected QA actor filter. Preserved both failed logs. Third cmd-dd52db28e852cd9f Exit0: actual 8 Carpet actors, server-position fixture at summit and y318, actual pickup callbacks and lethal damage/death, both physical dropped entities same IDs, clock advances, no stolen credit. Separate live QA scene with synthetic roster and temporarily absent floor: no safe landing, no dropped hit, clock advanced; floor restored same command, after >20 tick retry created one hit at original safe anchor; scene closed. D22 cover/D23 vertical/D21 respawn and damage regression, clean lobby/ledger/stop. Native natural movement not exercised by this test; actual native full-match/visual D22+ remains pending, Android/humans deferred. No new product build/download/deletion, prior extra outputs remain.
C_FREE=125295128576
prototypes/java-integration/build count=1057 bytes=239972664
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=21
FAA2884185A02F89E4721E4E6987E3373E9E7ACDE385555FC9D7961D382D27EB %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-e2a9031fc7b6.log
E1637219CB3A00376387BB122DC86D16EC99A64E8DAF08C62652432E87C39B41 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_dropstress_d24_v3_console.log
84DB648297CB3AD0C97F6780DBD5A38A5509CFD3FC4927A5BA61D5E28C5B6F81 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-3e5a712b9ddc.log
901CB81327DBEE529F0FC08A0D80DBB3862AEF0F8DC42F86D03DB106C7EEFB85 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-0f15ab09d3a3.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
[23:06:22] [Server thread/WARN]: Caravan crate 2 recovered to last safe anchor in leg 1
[23:06:24] [Server thread/WARN]: Caravan crate 1 awaiting safe landing in leg 1
[23:06:25] [Server thread/WARN]: Caravan crate 1 recovered to last safe anchor in leg 1

## D25 — overlapping drop interaction projection
- D25 plan saved first. Deterministic smallest-ID representative for overlapping dropped hitboxes (strict axis thresholds .85 horizontal / 1 vertical), pending IDs excluded before grouping, action validates current same projection. Others remain DROPPED at original coordinates in authoritative ledger; next appears after take. Non-overlapping crates remain individual. No new delivery rule or cargo position change. Help explains sequential pickup. Actual map code reviewed: no dropped crate markers exist, D24 hypothetical concern resolved without adding any map data. 85 Java/339 Kervan tests incl scene34/34, voice9; nine new pure cases. Actual eight Carpet named Fabric cmd-a78b8afb6679f7e9 Exit0: two summit/air deaths at same location; only first target visible, first removed, second emerges, different actor takes second, both IDs carried separately. Prior cover/vertical/pending/respawn checks also pass. Console/actor positioning fixtures, no native human movement. Grouping is direct-overlap with visible representatives, not transitive clusters; barrel artwork may still overlap at near distances where .85 hitboxes do not. No explicit stack count display, no change to tactical map. D22+ full native match/visual still pending separately. No production build/download/deletion.
C_FREE=125302374400
prototypes/java-integration/build count=1057 bytes=239981537
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=25
BE3571097DC4594985F707688835A0F60539AB4F8099B8B8FF79F99E5BE26DDE %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-2f12268d501a.log
58F3C7A92F0B86946E6FE9428FA206EA0E035D59CA13166A43093C2578A4F145 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_group_d25_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar

### D25 final native regression
- cmd-9e12c11c5bafd89b Exit0: two real Java client JVMs driven by synthetic vanilla W/A/D input; controlled mount and position fixtures. D22–D25 full two-leg 19 vs20 result, obstacle stop/resume, actual walking extraction through central screen gap, map hash matches, exact round/winner messages once, empty ledger, clean stop. Two framebuffer proofs, attacker approach screenshot inspected (stone screens, open center, orange shelter; still flat empty horizon). No human/Android proof. Logs and counts:
C_FREE=125056180224
prototypes/java-integration/build count=1061 bytes=240467714
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
PASS_COUNT=30
EF3794CD04B2B4AE83E60CDEA9A807CB9B1540070E0634DA638716A77C1537E7 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_runtime_caravan-76701c7ddf43.log
CE4432E486B0FD8691A71635B64920D016BF531F8FB4B8B9BE8BCD767531719F %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\prototypes\java-integration\acceptance_caravan_native_d25_console.log
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
