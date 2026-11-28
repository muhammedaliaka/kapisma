# KESİN OKU — BUILD / TARGET SAKLAMA HÜKMÜ (25.11.2026)

**Kullanıcı hükmü: Bu dosya işe başlamadan önce tamamen okunacaktır.** Bu hüküm, proje altında build üreten bütün yapay zekâ ajanları, geliştirici araçları ve otomasyonlar için geçerlidir. Her yeni oturumda işe başlamadan önce okuduğunu, mevcut build sayısını ve disk durumunu doğrula.

## 1. Saklama sınırı — zorunlu
- Varsayılan olarak **yalnız 1 güncel, kullanılabilir, doğrulanmış build** saklanır. Eski veya başarısız build aynı iş için ikinci kopya olarak tutulmaz.
- **En fazla 2 build** yalnız ciddi teknik gerekçe varsa saklanabilir: örneğin gerçek kullanımda olan release + debug doğrulaması, farklı zorunlu platform veya bir önceki doğrulanmış sürümün geri dönüş ihtiyacı. İkinci build gerekçesi, yolu, boyutu ve kaldırılma koşulu rapora yazılmalıdır.
- Gerekçesiz ikinci build veya üçüncü saklanan build **KURAL İHLALİDİR**. Yeni build doğrulanana kadar mevcut doğrulanmış build korunur. Geçici aday gerekiyorsa eski adayları önce güvenli biçimde ayır ve iki saklanan build sınırını aşma; teknik olarak mümkün değilse kullanıcı onayı iste.
- Cargo'nun `target/debug/build`, `deps`, `.fingerprint`, `incremental` altındaki crate çıktıları ayrı teslim build'leri değildir. Buna rağmen gereksiz/eskimiş önbellekler biriktirilemez. Aynı hedef üçlüsü ve profile tekrar tekrar farklı target klasörleri açma. `CARGO_TARGET_DIR`, `--target`, Unity build, Flutter/Gradle build, dist ve benzeri yolları kontrol et.
- Büyük build/target işlemi öncesinde C: boş alanını ölç; 10 GiB altında yeni büyük çıktı üretme, 5 GiB altında yalnız gerekli küçük analiz ve kullanıcı tarafından onaylanmış güvenli işlemler yap. Disk bütçesi yeterli değilse durumu bildir.

## 2. Önce denetim, sonra işlem
- İşe başlamadan önce canlı süreçleri, dosya kilitlerini, kullanılan executable/servis/başlatıcı yollarını, aktif başka ajanları, Git izlenen dosyaları, paket ve build manifestlerini, test kanıtlarını, ve proje içi özel runtime veri/ayar klasörlerini kontrol et.
- En son çalışan build'i sürüm tarihi, hash, boyut ve bilinen doğrulama sonucuyla belirle. Gerekli bağımlılıklarıyla birlikte sakla; sadece exe saymak yeterli değildir. İnceleme yapmadan `target` kökünü komple taşıma/silme.
- Kaynak kod, özel model/dataset, runtime config, kullanıcı profili, veri tabanı, lisans, release kurulum paketi, test raporu ve geri yükleme kaydına sırf target içinde diye dokunma.
- Aktif süreç, Unity editörü, Cargo/Gradle derlemesi veya başka ajan tarafından kullanılan yolları değiştirme. Şüpheli/erişilemeyen dosyayı koru; belirsizliği bildir.
- Aynı diskte taşımak alan açmaz. Hardlink, junction ve NTFS reparse dosyalarını iki kez sayma. Mantıksal/fiziksel boyutu ayır.

## 3. Temizlik yetkisi ve geri alma
- Kullanıcı açıkça silmeye izin vermedikçe hiçbir dosya silinmez; `cargo clean`, `git clean`, `Remove-Item`, cache prune, recycle boşaltma ve otomatik silen yöntemler dahil. Bu kural tek başına silme yetkisi vermez.
- Doğrulanmış yeniden üretilebilir artıklar yalnız kullanıcı izin verdiğinde ayrı **BUILD_SILME_ADAYLARI** klasörüne aynı disk üzerinde çakışmasız taşıma ile alınır. Normal yedeklerle karıştırılmaz. Kaynak ve hedef yolları, dosya sayısı, boyut, doğrulama ve geri alma bilgileri manifestte yer alır. Başarılı taşıma sonrası kaynak yok + hedef bütünlüğü doğrulanır. Kullanıcı silerse yalnız bu ayrı klasörü silebilir.
- Üç özel korumalı klasör: `Orkestra_Egitim_Dev`, `Orkestra_Sunucu_Kurtarma`, `Ajan_Mesaj_Sistemi`. Kullanıcı yeni açık izin vermedikçe bu klasörlerin altında inceleme, taşıma veya değişiklik yapma. Proje yetki sınırları ve daha sıkı güvenlik kuralları geçerlidir.
- Her iş bitişinde build sayısını, disk tüketimini ve ihlal olup olmadığını raporla. İhlali saklama veya yapay başarı ilan etme. Kullanıcı onayı olmadan ek build biriktirme.

Bu hüküm, diğer proje kurallarıyla birlikte uygulanır; yetki devri veya korumalı alana erişim izni değildir.
