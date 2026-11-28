# Android tablet — RAM ve Java uyumluluk denetimi
Kullanıcı isteği, 25 Kasım 2026. Tablet tam şarj edildiğinde uygulanacak; şu anda cihaz üzerinde işlem yapılmayacak.

## Amaç
Kapışma ULTRA için gerçek fiziksel RAM'i, Android sistem yükünü ve Java 21/Termux uyumluluğunu doğrulamak. Tüm erişilebilir RAM tüketim kaynaklarını ölçmek, nedenlerini açıklamak ve gerekli/isteğe bağlı/gereksiz/belirsiz olarak sınıflandırmak. Kullanıcı yalnızca analiz ve raporlama istedi; uygulama silme, devre dışı bırakma, root, sistem ayarı değişikliği yok.

## Sıralama
1. ADB bağlantısı ve yetkilendirme; cihaz modeli, SoC, ABI (32/64 bit), Android/kernel, fiziksel RAM, MemAvailable, swap/zRAM, depolama, pil ve sıcaklık.
2. Termux sürümü, mimarisi, mevcut java -version, mevcut paketler ve uygun OpenJDK 21 paketinin kullanılabilirliği. Termux'un çalışması tüm Java sürümlerinin desteklendiği anlamına gelmez. Minecraft 1.21.1 için Java 21 hedefi; uyumluluk kanıtlanmadan kurulum yapma.
3. ADB dumpsys meminfo, /proc/meminfo, erişilebilir süreç listesi ve paket bilgileriyle PSS/RSS/USS (mevcutsa), native/Java heap, kernel/cache, zRAM, sistem servisleri ve uygulamalar. RSS değerlerini doğrudan toplama; ortak sayfalar ve cache çift sayılmasın. Android izinleri nedeniyle görülemeyen kalemleri açıkça belirt.
4. Boşta kararlı ölçüm (tekrarlı), ekran açık/kapalı ve varsa sunucu yükü ayrı. Süreç/paket bazında bellek, işlev, zorunluluk, güvenli müdahale önerisi, olası yan etkiler ve kanıt/eminlik düzeyi. Sistem kritik servisleri, ağ, güvenlik, giriş, şarj/termal yönetimi ve LMK korunacak. Cache kullanımı tek başına gereksiz tüketim değildir.
5. Rapor: gerekli, isteğe bağlı, gereksiz olduğu doğrulanan, belirsiz kalemler; geri kazanılabilir RAM tahmini ve ölçüm sınırları. Kullanıcının ayrıca onayı olmadan hiçbir uygulama kaldırılmayacak veya ayar değiştirilmeyecek.
6. Sonuçlar optimize/README.md planına bağlanacak; 3 GB fiziksel RAM cihazı ve 4 oyuncu hedefi için gerçek kullanılabilir RAM'e göre profil değerlendirilecek. Gerçek cihaz testleri tamamlanana kadar performans garantisi yok.

Beklenen başlangıç: tablet şarjı tamamlandığında kullanıcı tekrar bağlayıp açacak. Önce salt okunur tanılama.
