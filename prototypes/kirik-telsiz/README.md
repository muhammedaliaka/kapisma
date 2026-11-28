# Kırık Telsiz — 0.2 prototip (26.11.2026)

Bağımsız, yazılı iletişimle de çözülebilen eşli şifre oyunu. Ana Kapışma ile birleşik veya canlı kurulum değildir.

## Oyun
- Konsol: `function kt:start` (2–8 seyirci olmayan katılımcı; eşli A/B rolleri). Oyuncuların rolleri sırayla atanır.
- A ilk iki, B son iki rakamı özel tellraw mesajında alır.
- Oyuncu: `/trigger kt_hint` ipucunu yeniden gösterir; `/trigger kt_code set 1234` tahmin gönderir.
- Üç tur; her tur 90 sunucu saniyesi, üç yanlış tahmin hakkı. Ortak iki saniyelik tahmin aralığı. Her iki rolün çevrimiçi aktif temsilcisi gereklidir.
- Konsol: `function kt:reset` maç durumunu sıfırlar.
- Rol ipuçlarının iki gerçek Java istemcisine özel sohbet mesajıyla iletilmesi doğrulandı; fiziksel oyuncu etkileşimi henüz sınanmadı. Oyuncuların kendi mesajları/harici iletişimi denetlenmez.
- Tur değişimi, duraklama, maç sonu ve sıfırlama sırasında çevrimdışı oyuncular dahil eski tahmin ve ipucu trigger skorları temizlenir. Rol eksikliğinde bekleme bildirimi gösterilir. Eski maçın epoch değeriyle dönen oyuncu yeni maça dahil edilmez.
- 2–8 aktif Carpet botu kapasitelerinin her biri ayrı ayrı sınandı. 1/9 oyuncu sınır reddi, ayrılma/yeniden bağlanma, süre bitimi (sayaç 1 saniyeye ayarlanarak), eşzamanlı tahmin ve aktif maçın temiz sunucu kapanışından sonra sürdürülmesi doğrulandı. Doğal süre ve kayıt noktası sonrası zorla kapanış ayrıca sınandı. İki gerçek Java istemcisinde özel sohbet ipuçları doğrulandı; insanla oyun hâlâ açık; sentetik sesin gerçek istemcilere iletimi ayrıca doğrulandı.

## Doğrulama
`prepare.py` veri paketi üretir; `verify.py` mevcut MOD uyumluluk laboratuvarı ve ayrı kirik-telsiz-lab dünyasını kullanır. Java 21 ve Python 3.11+; yerel TCP 25578, UDP 25579. Test offline ve loopback ile sınırlıdır. Mevcut modların SHA-256 değerleri doğrulanır. Test sonunda server.properties ve voicechat-server.properties özgün baytlarına döner.

`acceptance.log`: gerçek Fabric 0.20.1 / Minecraft 1.21.1, Analog 1.2.2+1.21, Simple Voice Chat 1.21.1-2.6.27, Fabric API 0.116.18+1.21.1, Carpet (bot testine özel). 11 oyun kontrolü, açılış/kapanış ve ek ayar/hash bütünlüğü başarılı. İlk koşu bot adlarının küçük harfe çevrilmesi ve log okuyucu kapanışı nedeniyle başarısızdı; `acceptance_initial.log` korunmuştur. İkinci koşuda ERROR/Exception satırı yok.

`verify.py --extended` (0.2): 30 açılış/oyun/kapanış kontrolü + 1 profil/hash bütünlüğü PASS, Exit 0. İlk genişletilmiş koşuda asenkron Carpet girişleri yüzünden erken başlatma oldu; bot girişleri artık tek tek bekleniyor. İlk koşunun günlüğü `acceptance_extended_initial.log` olarak saklandı; eski tahmin kusuru bu başarısız koşuda yeniden üretilemedi. Düzeltilmiş koşu `acceptance_extended.log`, SHA-256 `6453028CE7D960559C730796B65713A9B0445D8EDF452BFA0AB18CB1FC071512`; hata taraması temiz. Kurulu 14 mcfunction dosyası üretici çıktısıyla birebir eşleşti.

`verify_lifecycle.py`: iki bağımsız Java sunucu süreci, aynı dünya kaydı; 19/19 PASS, Exit 0. 4–7 kişi rol dengesi, ikinci turda iki kalan tahmin hakkı, şifre/süre/epoch/phase kalıcılığı, eski skor kuyruğunun temizliği, tek kayıtlı rol ve kayıt dışı oyuncunun süreyi başlatamaması, kayıtlı iki oyuncu ile üçüncü tura geçiş ve kazanma sınandı. Kanıt `acceptance_lifecycle.log`, SHA-256 `7D955495DCDD3C3CA10C7283425CBAF6750A752B3B492CE2A3B4FC0AA1AD4B03`. Önceki 31 ile birlikte iki koşuda toplam 50 kontrol geçmiştir; bu sayı örtüşen kontrolleri de kapsar. Oyun üreticisi değiştirilmedi.

`verify_recovery.py`: doğal süre, 88.813 s / 1785 tik; sayaç hızlandırılmadı. Açık save-all flush kayıt noktası, yalnız QA Java sürecinin zorla öldürülmesi (Exit 1), yeni süreç, kayıtlı durumun devamı ve kaydedilmemiş işaretin geri alınması; 21 PASS, Exit 0. İlk koşu slime nedeniyle oyuncu kaybında doğal durakladı; ikinci koşu test regex hatası, ikisi de günlüklerde saklandı. `acceptance_recovery.log` SHA-256 `14405873EC8318CEF9A134D1D84DC546122956C9A87DBF6CF0A2CB501FB099DF`. Son kayıttan sonraki ilerleme kaybolabilir.

`verify_client.py`: iki gerçek Java 1.21.1 istemcisi, ayrı QA oyun dizinleri, 127.0.0.1 TCP. A/B özel ipuçları iki turda birbirine sızmadı; seyirciye yeni ipucu gitmedi. Komutlar sunucudan yürütüldü, fiziksel tuş veya mikrofon test edilmedi. İkinci koşu 10 PASS, Exit 0; `acceptance_client.log` SHA-256 `4C7BF115C434A54E203E74FEE59E70566EE130CA1C6C8BC392072375C352E6DA`. İlk koşu oyun kontrollerini geçti ancak `before` değişkeni gölgelendiği için ayarları geri yükleyemedi. İlk günlükler `_initial.log` olarak korundu. `restore_smoke_profile.py` özgün smoke-world NBT ve kardeş profil ile ayarları yeniden kurdu; orijinal server.properties baytları kesin olarak kurtarılamadı. Doğrulanan yeniden oluşturulmuş hash `A8B270EA73373A050174D5F8FF1BC7D9BA3163AF4B783CE5D5B29929123BCFCB`, ses ayarı `C7D07215BA07733D73D051A0CEC93783A12F071328D5C0CFAFFA40FAA7B3F2E3`. Dört test betiği `profile_guard.py` ile işlem öncesi diskte hash doğrulamalı ayar yedeği alır. `restore_profile_snapshot.py` varsayılan kuru kontrol; `--apply` ayrı, açık işlem gerektirir. Kuru kontrol geçti, ikinci istemci testi iki ayarı birebir yeniden oluşturulmuş başlangıca geri yükledi. Yeni JAR yok.

`verify_voice_boundary.py`: Analog/SVC JAR'larına salt okunur javap analizi. Direct plugin gönderiminde sender null olabilir; mevcut Kapışma kaynak kodunun izolasyon şartı sender != null gerektirir. Dolayısıyla gerçek ses, kanal/rol izolasyonu ve seyirci sızıntısı test edilmeden Analog canlı entegrasyonu yapılmaz. Bu bir statik risk kanıtıdır, gözlenmiş ses sızıntısı değildir.

Ana Kapışma ve RTS profili değiştirilmez. Yeni JAR derlenmemiştir.

## Analog çalışma zamanı güvenlik testi (26.11.2026)
`verify_voice_runtime.py` ve yalnız laboratuvar için üretilen `voiceqa/kt-voiceqa.jar`: 10 PASS / Exit 0. SVC AudioSender sentetik 40 Opus kareyi doğal Analog mikrofon olayından gönderdi. RED aktif 40/seyirci 40, GREEN aktif 40/seyirci 0, POSTRED 40/40, yanlış kanal 0/0. Seyircide telsiz yoktu. Kanıt `acceptance_voice_runtime.log` SHA256 `E50BEBF1B7DA6674BE3F3BE71F854F2118003C498BA322DFD34D8CC23A242B09`. QA guard sadece deneme içindir; üretim ses politikası, seyirci→seyirci static relay, sabit verici/alıcı ve gerçek insan testleri açık. Canlı entegrasyon yok. Birinci/ikinci başarısız koşu günlükleri saklandı. Ayrıntılar AI_MOD_KOD_YANIT.md.
