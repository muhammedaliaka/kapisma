# Kapışma MOD — 8 prototip entegrasyon devir belgesi
26 Kasım 2026 · Minecraft Java 1.21.1 / Fabric Loader 0.20.1

## Karar
Sekiz modun ayrı laboratuvar prototipleri mevcuttur. Bu, ana Kapışma release'inin sekiz modu içerdiği anlamına gelmez. KOD alanına, normal sunucuya ve canlı dünyaya yazılmamıştır. KOD ajanının gerçek maç yöneticisi entegrasyonu ve kullanıcı canlı kurulum onayı ayrı aşamalardır.

## Çalışan altyapılar
| Mod | Kaynak / çalışma alanı | Üretim bağımlılığı | Kabul kanıtı |
|---|---|---|---|
| Devletler RTS | prototypes/devletler-rts, _yedek/devletler-rts-prototip | HYW 0.8.0r-fix2 (ARR, özgün JAR), RTS rules 0.7.0, Fabric API | verify_npc 60 koşul, formation native, launcher kontrolleri; gerçek çok cihaz açık |
| Kırık Telsiz | prototypes/kirik-telsiz | vanilla metin mekanikleri; Analog 1.2.2+1.21 ve SVC 2.6.27 ses için | 91 koşu kontrolü, gerçek sohbet ve sentetik ses RED/GREEN; üretim guard açık |
| Dönen Karakol | prototypes/donen-karakol | vanilla datapack prototipi | v0.3 31+15+16 PASS; doğal oyun, tüm regresyon ve gerçek PvP/kayıt |
| Kervan Baskını | prototypes/kervan-baskini | vanilla datapack prototipi | 28+20 PASS; doğal iki tur, rol geçişi/restart |
| Yaşayan Harita | prototypes/yasayan-harita | vanilla datapack prototipi | 22+7 PASS; pusula zinciri ve özel gerçek istemci mesajları |
| İnşa Et, Savun | prototypes/insa-et-savun | vanilla datapack prototipi | 20 PASS; gerçek blok yerleştirme/kırma |
| Kit İhalesi | prototypes/kit-ihalesi | vanilla datapack prototipi; sgui üretim UI için mevcut KOD bağımlılığı | 23+6 PASS; jeton, gerçek istemci gizlilik |
| Alçak İrtifa | prototypes/alcak-irtifa | vanilla Elytra; Do a Barrel Roll gerekmiyor | 18 PASS; gerçek istemci glide paketi ve 4 geçit, QA teleport |

Carpet yalnız test bağımlılığıdır; flightqa.jar ve kt-voiceqa.jar yalnız QA fixture, üretim mod listesine eklenmez. Altı yeni vanilla datapack için ek üçüncü taraf oyun modu gerekmedi. Analog, HYW veya diğer ajan release JAR'ları değiştirilmedi.

## Entegrasyon güvenlik sınırı
- Datapack `load` işlevleri dünya gamerule ayarlarını değiştirir; arena `fill` ve oyuncu `clear` sabit koordinat/bağlamlıdır. Bunları normal dünya datapacks klasörüne topluca kopyalamak güvenli değildir. Aynı anda iki farklı moda ait load/maintain fonksiyonu çalıştırılmamalı.
- KOD ajanı mevcut WorldGame/TeamService ve maç yöneticisine, her maç için ayrı dünya/arena ve UUID üyelik kaydıyla kuralları taşımalı. Oyuncu listesi, skor, epoch, zaman, sonuç, çevrimdışı rol geçişi ve temizleme maç kimliğiyle ayrılmalı; farklı modların global skor/gamerule değişiklikleri engellenmeli.
- Maça girişte lobby envanterinin saklanması ve çıkışta geri yüklenmesi, op komutlarının oyuncu erişiminden ayrılması, güvenilir UUID/online-mode veya açık izinli Tailscale politikası, join/rejoin/death/teleport olaylarının tek sunucu iş parçacığında işlenmesi, dünya hazırlama ve temizlemenin güvenli yapılması şart. Prototipler gerçek kullanıcı envanterlerini saklamaz; sadece sentetik QA oyuncularında denenmiştir.
- Dönen Karakol: capture tick, eşit kit ve spawn, contested=0, 30/5/40 değerleri parametre; aktif maç kayıt testi ve bot/gerçek PvP kanıtı. Kervan: server-owned progress/cargo ve iki rol, offline leg revision, eşitlik kuralı. Yaşayan Harita: pusula bileşenini sadece mevcut hedefe üret, sunucuda konum/epoch/tekrar kontrol, aynı tik iki aday birlikte sonuçlanmalı. İnşa: build alanına yerleştirme izinleri, gerçek saldırgan kırma olayında hedef kimliği ve süre kontrolü, disconnect pause inşayı da kilitler. Kit: maç jetonları ayrı, kapalı teklifler, atomik tek ödül/ödeme, güvenli UI, gerçek para yok. Alçak İrtifa: gerçek FallFlying + sıralı geometrik geçit; sürekli hareket için sunucu konum geçmişi ve yüksek hızda geçit kesişim kontrolü üretimde eklenmeli.
- Analog ses engeli: mevcut KapismaVoice null-sender plugin sesi seyirciye geçirebiliyor. QA 40/40 vs 40/0 kanıtı var. Üretimde null-sender locational için güvenilir maç snapshot'ı; mevcut spectator→spectator static relay bozulmamalı; sabit transmitter/receiver/playback ve lobby/maçlar arası ayrım yeniden test edilmeli. Bu olmadan canlı ses entegrasyonu bloke.
- HYW ayrı profil, orijinal ARR mod JAR'ı değiştirilmemeli. RTS'nin kendi bağımsız save/host/resume sözleşmesi normal WorldGame ile aynı sayılmamalı. İki gerçek Tailscale kullanıcı, fiziksel kamera/komut ve maç geçişi kabulü açık.

## Kabul seviyeleri
1. İzole mekanik: tüm sekiz modda gerçek Minecraft/Fabric kanıtı.
2. Gerçek istemci: RTS formation, Kırık Telsiz ses/özel metin, Dönen Karakol PvP/respawn, Yaşayan Harita/Kit özel sohbet, Alçak İrtifa glide. Kervan ve İnşa Carpet/komut kontrollü; fiziksel insan UX yok.
3. Nihai entegrasyon: henüz yapılmadı. Mevcut KOD ajanı adayının yeni build'i ve kullanıcı onayı gerekir. Canlı dünyaya hiçbir prototip doğrudan kurulmamalı.
4. Üretim kabulü: 2–8 insan, 3–8 tek/çift sayı adaleti, gerçek Android 3/6 GB yük profili, ağ gecikmesi, maçlar arası temizleme, fiziksel cihaz/Tailscale, anti-cheat, güvenlik ve geri alma. Test sayıları bu kapsamın tamamlandığına kanıt değildir.

## Test çalıştırma
Her prototipin README/prepare.py/verify.py ve acceptance*.log dosyaları. Aynı MOD lab `.mctest.lock` ile tek işlem, loopback 25578; eski profile geçmeden önce kalıcı snapshot + iki config hash geri yükleme. Normal sunucu ve KOD test dizinleri kullanılmaz. Test scriptleri Java21 ve mevcut Python runtime'ı kullanır. Prototip denge değerleri geçicidir; insan testi olmadan nihai balans değildir.
Son doğrulama: 7 veri paketinin 180 işlevi kaynakla birebir, 0.3 tam regresyon/lifecycle tekrar Exit 0, MOD test portları kapalı. Bu tur 252 başarılı koşu kontrolü (örtüşmeler dahil). Ayrıntılar AI_MOD_KOD_YANIT.md.
