# Kapışma ULTRA / optimize — D001 (2026-11-25)

KOD ajanının yeni birincil görevi: Fabric 1.21.1 / Java 21, dört eşzamanlı gerçek oyuncu; 3 GB fiziksel RAM Android zor hedef, 6 GB Android ve 12–16+ GB PC ana kabul platformları. Sunucu ayrı cihazda; aynı cihazda istemci çalışması ayrı test. 3 GB cihazların tamamı için garanti yok: SoC, kullanılabilir RAM, Android LMK, depolama, ısı ve ağ belirleyici. Mod ajanının canlı modları/RTS alanı değiştirilmeyecek.

## Gerçek başlangıç kanıtı
_yedek/stress_tablet2.txt: eski 2560 MiB heap, ARM64 dört çekirdek, 7 bot; RSS zirve 1661 MB, keşif ortalama 93.1 MSPT, PvP 59.1 MSPT, 11 Can't keep up, 8 hata satırı (JNA/auth dahil). Bu test 3 GB fiziksel cihazı veya dört gerçek istemciyi kanıtlamaz. Yeni aday JAR SHA256 036C95AF4796283FF46A2CE5026980863B50B6310F45C2A8E92E3673F40F4341. Önceki log başka paketle üretilmiş; aynı seed/ayar ile taze baseline gerekli.

## D001 uygulanmış
profile.py: saf, test edilebilir bellek bütçesi; heap dışında native/OS rezervi, yetersiz fiziksel RAM durumunda fail-closed, G1 başlangıç bayrakları ve 4 oyuncu/4-3 mesafe üst sınırı. Yalnız JSON çıkarır, canlı yapılandırmaya yazmaz. Örnek: python profile.py --total-mib 3072 --available-mib 2700 --cpus 4 --platform android
3 GB için 1024–1280 MiB heap deney adayı; çok düşük kullanılabilir RAM'de başlatmayı reddeder. 6 GB Android için 1536–3072 MiB; PC için 4096 MiB üst sınır. Rezervler hipotezdir, ölçüme göre ayarlanır.

## Araştırma kaynakları
- Oracle Java 21 CLI ve ergonomi: https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html ; https://docs.oracle.com/en/java/javase/21/gctuning/ergonomics1.html
- Android düşük bellek süreç öldürme: https://developer.android.com/topic/performance/issues/lmk
- Termux Java 21: https://github.com/termux/termux-packages/blob/master/packages/openjdk-21/build.sh
- Minecraft özellikleri: https://minecraft.wiki/w/Server.properties
- Lithium: https://github.com/CaffeineMC/lithium ; FerriteCore: https://github.com/malte0811/FerriteCore
- ModernFix Fabric 1.21.1: https://modrinth.com/mod/modernfix/version/VEsmlnhN
- C2ME: https://github.com/RelativityMC/C2ME-fabric
- Modrinth önerileri: https://support.modrinth.com/en/articles/13025809-server-optimization

## Yol haritası / öncelik
D002: mevcut JAR + yeni profil ile dört bot baseline, GC logu, RSS/PSS, p50/p95/p99 MSPT, chunk sayısı ve GC pause ölçümü. Aynı seed, rota, modlar, süre; her test en az 3 tekrar. Eski stress_phone.py varsayılan 7 bot ve yıkıcı deploy içeriyor: mevcut kullanıcı dünyasında çalıştırma; yeni güvenli ölçüm yolu gerekli. Android ADB bağlı değil (25 Kasım ölçümü).
D003: Termux başlatıcıda MemAvailable ölçümünü gerçek rezervle bağla; eski 1280 alt sınırı düşük RAM'de zorla başlatıyor; ram_mb.txt üst sınırı atlıyor. Windows/Linux/macOS ile ayrı platform testleri. EULA/kimlik doğrulama/whitelist değişmeyecek.
D004: Mod ajanı ile Lithium, FerriteCore, ModernFix sürüm ve hash sabitleme; her biri tek başına A/B sonra kombinasyon, Origins, voice, Fantasy, anti-xray, portal ve loot regresyonu. C2ME yalnız ayrı deney; daha çok worker/RAM tüketebilir. Sodium istemci optimizasyonu sunucu çözümü değildir.
D005: chunk yükü. GameWorld.force referans sayımlı asenkron biletleri mevcut. Lobi zorlanmış chunkları, Manhunt senkron getChunk, Bingo tarama bütçesi ve UHC başlangıç biletleri ölçülmeli. Sınırlı ön üretim; maç başlatmadan güvenli spawn/rota chunkları, aynı anda az bilet; iptal/timeout/cleanup. Ana thread dışından güvenli olmayan world API çağrısı yok. Bütün haritayı körlemesine pregen etme; disk/ısı/süre ölç.
D006: gerçek oyun yükü. HUD paketleri değişim bazlı/batch, scoreboard, anti-xray ve guard profiling. Anti-cheat kontrol sıklığını düşürerek sahte performans kazanımı yasak. Kurallar, mob/loot/tarif, ses, kayıt ve maç izolasyonu korunmalı.
D007: dinamik kontrol: load ölç, hysteresis + cooldown; yalnız profilin görüş/mesafe aralığında, oyuncuya bildir; maç kurallarını değiştirme. Geri dönüş ve kaynak temizleme.
D008: gerçek 3GB/6GB ARM64 ve 12/16GB PC cihazlar, 4 gerçek istemci, iki ayrı maç döngüsü ve minimum 2 saat soak + yeniden başlatma. 3 GB cihaz 32-bit ise ayrı destek kararı, 64-bit Termux/OpenJDK21 şart. Termux foreground/wake lock Android LMK garantisi değildir; termal kısıtlamayı devre dışı bırakma.

## Kabul kapısı (hedef, elde edilmiş sonuç değil)
- Stabil oyun 20 TPS hedefi: 1 dakikalık TPS >=19.5, p95 MSPT <=50, p99 <=100 ms; yeni chunk stresinde p95 <=50 hedefi; yükleme aşaması ayrı ölçülür. Yanıt gelmeyen örnekler başarısız sayılır.
- Dört gerçek oyuncu dağınık keşif, PvP, portal, Bingo kaynak kanıtı, Manhunt ve UHC, yeniden bağlanma, maç kapanış/başlatma; normal profil ile kural eşdeğerliği. HYW RTS ayrı mod ajanı uyumluluk kapısı.
- OOM/LMK, watchdog, veri kaybı, crash veya doğrulanmamış oyun davranışı: başarısız. RSS/PSS zirvesi, heap kullanımı, boş RAM, sıcaklık, disk, GC ve CPU kaydedilecek. 3 GB hedef RSS <=1700 MiB, PSS/LMK ayrıca ölçülecek; cihaz boş RAM rezervi sürekli >=512 MiB hedefi. Başarısızsa desteklenmeyen cihaz açıkça belirtilir.
- Her build için proje KESIN_OKU_BUILD_KURALLARI geçerli. Bu aşama yeni JAR/ZIP oluşturmadı. Mevcut 15 aday ZIP kullanıcı izni olmadan silinmedi; yeni release öncesi saklama durumu açıklığa kavuşturulmalı.
## D001 doğrulama sonucu
14/14 Python testi PASS (7 profil + 7 kabul denetimi). check_stress.py eski tablet logunda 39 örnek, 2 yanıtsız, 25 p95>50 ms, 28 p99>100 ms buldu; beklenen FAIL, RSS 1661 MiB. Bu yalnız bot logu kabul denetimi, sertifika değil. CLI 3GB/2700 MiB kullanılabilir senaryoda 1280 MiB heap; 1800 MiB kullanılabilir senaryoda güvenli red (exit 2). Yeni JAR yok; tek mevcut derlenmiş çalışma JAR'ı ve sources JAR değişmedi. Disk başlangıç ölçümü 108737671168 bayt. ADB cihazı yok. Sonraki somut iş: güvenli izole baseline runner, mevcut JAR ile dört bot ve JVM/mesafe A/B.
