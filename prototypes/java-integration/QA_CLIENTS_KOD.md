# Gerçek Java istemcisi — KOD ajanına devir (26.11.2026)
Kullanıcı RTS alanını kapsam dışı bıraktı. Bu çalışma yalnız normal Kapışma ve izole QA içindir.

## Kullanılabilir altyapı
- verify_runtime.py Client sınıfı: Minecraft 1.21.1 gerçek grafik istemcisi, Fabric Loader 0.20.1, synthetic ArenaDevQA1/2/3, offline UUID MD5("OfflinePlayer:"+name), dummy accessToken=0. Önceki iki/üç istemcili gerçek chat, ses ve maç lifecycle testleri başarılı. Sunucu portu MOD 25580. Bu betiği KOD sunucusuna yönlendirmeden önce ayır.
- qa_clients.py: importta yan etkisiz ayrı NativeClient sınıfı, açık port, sabit 127.0.0.1, oyun dizini identity marker, yeni log zorunlu, gerçek hesap/kimlik bilgisi yok. KOD kendi onaylı USB/loopback yönlendirmesinde kullanabilir. Bu yeni wrapper'ın gerçek bağlantısı ayrıca test edilmeli. MOD KOD portuna veya Android'e bağlanmadı.
- Client JAR SHA1 30c73b1c5da787909b2f73340419fdf13b9def88, Fabric API SHA256 1CB46442DDA6D3BE9979A2619610EB1C1F6641CD7ED7CB3EEC058589CE9EED46.
- 4 istemci için başlangıç önerisi: ayrı Windows PC, her istemci Xmx1500M, ActiveProcessorCount=2, 640x480/20 FPS/render distance4; heap toplamı 6 GiB, native/driver ve JVM ek bellek harici. 16 GiB PC minimum test önerisi, 32 GiB daha güvenli. Dört gerçek native client yükü henüz ölçülmedi. Android yalnız sunucu çalıştırır. Normal insan testini temsil etmez.
- Uçuş QA fixture: prototypes/alcak-irtifa/flightqa. Standart vanilla client input/rotation ve START_FALL_FLYING paketi; custom konum yazımı yok. Walk modu gerçek forward key state + yaw ve konum örnekleri. KOD doğal hareket öncesinde yalnız kendi izole test dünyasında kontrollü rotayı kurmalı. Bu yeni walk modunun runtime kabulü açık.

## Kabul matrisi
| Test | Kanıt | Başarısızlık |
|---|---|---|
| 4 gerçek istemci bağlantı | 4 ayrı Java PID, server join UUID, client [CHAT], TCP bağlantısı | Carpet bot yerine sayı sayılmaz |
| Normal yürüyüş | Sabit başlangıç/yaw, native key input, client her 20 tick XYZ ve server Pos 5s aralık, her istemci en az 3 ardışık ilerleme aralığı | Tek teleport veya tek toplam delta yeterli değil |
| Chunk/ACK | Server vanilla hareket/teleport kabul sayaçları, gönderilen chunk ve gerçek istemci yüklenme olayları, varsa ACK tanı sayaçları | Carpet tek başına yeterli değil, console spam/lag kaynaklı fark ayır |
| Anti-Xray | Gönderilen chunk/taranan blok >0, beklenen gizleme/açılma, server gerçek blok değişimi sıfır, normal client UI ve mevcut regresyon | Sıfır scan kabul değil; Xray hilesi kurulmaz |
| Origins | Dört normal oyuncu için gerçek ability/çarpışma senaryosu, sunucu ve istemci aynı sonucu, override warning kaydı | Apoli uyarısı tek başına kusur kanıtı değil |
| Android A/B | Aynı JAR/seed/rota/mob popülasyonu/heap, 4 native PC client, 60sn+ hareket; MSPT P95/P99, RSS, GC, sıcaklık, save/restore | Tek koşu ile nedensellik veya dört insan kabulü yok |
| Kapanış | Tüm test istemcileri yalnız kendi süreçleriyle kapanır, server save+exit0, USB yönlendirme geri alınır | Canlı profil veya diğer ajan süreçlerine dokunulmaz |

KOD kullanım örneği (yalnız kendi yetkilendirilmiş loopback portunda):
```python
from qa_clients import NativeClient
from pathlib import Path
clients=[]
try:
    for n in range(1,5):
        c=NativeClient(name=f"KodNativeQA{n}", port=25577,
            game=Path("qa-profiles")/str(n),log=Path("qa-logs")/f"client-{n}.log")
        clients.append(c)
        # Server gerçek join kaydını bekle, sonra sıradaki istemci.
finally:
    for c in clients:c.close()
```
Port 25577 örnektir, MOD tarafından kullanılmaz. KOD kendi run dizinini ve port yönlendirmesini doğrular. Bu örnek hareket yükü veya Android performans testi değildir.

## Son doğrulama — V2
Yeni bağımsız NativeClient wrapper dört gerçek ayrı JVM ve gerçek server Pos ile başarıyla test edildi. Dört oyuncu 4/4 pozitif hareket aralığı, her birinde 11 client sample. YASAYAN_HARITA arena şeritleri; başlangıç koordinatları server tarafından doğrulandı; ilk üç moved too quickly uyarısı konumlandırma sırasında. Normal chunk üretimi, anti-Xray, Origin, Android performans kabulü değil.
- NativeClient(...,walk=True,yaw=-90,ticks=220); dört oyuncu server join ve oyun/rotaya yerleştirme doğrulandıktan sonra her c.start_walk(). c.positions() örnekleri ve server Pos karşılaştır. Aynı profile farklı yeni log adıyla yeniden çalışılabilir, marker kimliği aynı olmalı; trigger log stem ile ayrılır.
- Son wrapper SHA256 42D117EEAB0032E69466BF642CA56F0E904AC8DC7F20B5186C241F3563BEE1D1. Flight QA JAR SHA256 3202B96CEA44313E89A465190377C4D8EEA4AFF72D110F0FCE806A0AFB671122. V2 server log A2384C97028CB0B1DCD5BD5F826264A2E750A5BB09B0E53823C1F7470CAB73A7; Exit0. İlk lobi dışı koşu başarısız: FCF182B4E22E26F1E318F3F1B25A4ED470D148DFBB19A0F0A4A666FCFBCC686A. KOD kendi test server ve portunu yönetir.

## MOD → KOD R35 — engelsiz doğal hareket fixture önerisi
Flight QA JAR değişmedi (SHA256 3202B96CEA44313E89A465190377C4D8EEA4AFF72D110F0FCE806A0AFB671122); mevcut adapter pinini güncellemek gerekmez. Önceki MOD V2 dört gerçek Java istemcisi YASAYAN_HARITA flat lane başlangıç x=-30.5 y301 z=-25.5,-23.5,23.5,25.5, yaw=-90 pitch0; her biri 4/4 ardışık pozitif hareket aralığı. Android için ayrı test dünya önerisi: aynı seed/config, düz ve önceden hazırlanmış 320 blok uzunluğunda dört şerit, merkezler z=0,8,16,24, 3 blok genişlik, solid tam blok zemin, en az 3 blok hava yüksekliği, mob/oyuncu çarpışması yok; x=0.5 başlangıç, +X yönü, normal Adventure/Survival yürüyüşü. 60 saniye için 240–260 blok beklenebilir (TPS ve hareket hızına bağlı), 320 blok son engel marjı. Ölçümden önce server Pos, yön, zemin, dört client ACK/bağlantı ve chunk hazırlığını doğrula, sonra mevcut start_walk() trigger 1200 client tick (maks2400); 5s aralıkla server Pos + her 20 client tick local Pos. Tek başlangıç TP dışında ölçüm sırasında teleport yok. Her oyuncuda en az 3 ardışık ve en az 12 örnekte ilerleme, client/server konum farkı ve kaçış/uyarı sayısı raporla; TPS düşükse istemci süre ve server tick ayrı kaydedilmeli. KOD kendi izole port/profil ve geçici fixture'ını yönetir. İlk deney pre-generated lane, ikinci ayrı fresh terrain/normal chunk streaming, sonuçlar birleştirilmemeli. Bu öneri Android'de uygulanmadı ve yeni chunk performansı garantisi değildir. MOD Android'e/KOD portuna bağlanmadı, mevcut flightqa fixture değiştirilmedi.
