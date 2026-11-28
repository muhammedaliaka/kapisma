# Kervan Baskını — bağlayıcı kullanıcı kararları (27.11.2026)
Kullanıcının bu turdaki açık kararları, eski prototip ve önceki geçici kararların üstündedir.
1. Kazanan öncelikle teslim edilen sandık sayısına göre belirlenir.
2. Saldırgan sandığı kendi üssüne fiziksel olarak götürmelidir; salt ele alma puan değildir.
3. Kervan güzergâhı yalnız yol ayrımında değiştirilebilir.
4. Ölen oyuncu 10 saniye sonra kendi üssünde yeniden doğar.
5. Elde tutulan haritada oyuncunun kendi konumu, kervan ve güzergâhlar görünür. Askerî oyunların taktik/topografik harita görünümüne yakın grafik dil tercih edilir; navigasyon uygulamasını birebir kopyalamak şart değildir.
6. Önceki kayıtlı kurallar: sabit klasik sürüş hızı, güvenli/riskli yol, riskli yol saldırgan doğumuna yakın, dağ/vadi/tünel/geçit, iki takımın atları, tekil taşınabilen sandık, kervan arabası çalınmaz, taşıyan yavaşlar, savunmacı geri alabilir; kanca 30 sn, geçici 300 blok, host tırmanış seçeneği.

## Uygulama yorumu ve açık ayrıntılar
- Eskort teslim sayısı yalnız hedefe varan arabada gerçekten mevcut sandıklar; düşmüş, taşınan veya çalınan sandık teslim sayılmaz. Rakip üs teslimi ayrıca sayılır. İki raund rol değiştirir. Önerilen eşitlik sırası: daha az rakip üs teslimi, normalize rota ilerlemesi, kalan süre; tam eşitlik beraberlik. Bu eşitlik sırası kullanıcı tarafından ayrıca belirlenmedi.
- Yol ayrımı, rotaların ortak fiziksel düğümü olup alternatif ileri yolların ayrıldığı konumdur. Araç tam orada durup sürücü seçiminden sonra devam eder; ortada rota değiştirme/ışınlanma yok. Başlangıç da seçim düğümü olabilir. Dallanma sonrası tekrar birleşme ve sonraki ayrımlar desteklenmelidir.
- 10 saniye = 200 aktif sunucu tick'i (20 TPS); duraklatılan maçta sayaç ilerlemez. Ölüm anında sandık bırakılır, sürücülük ve hareket etkisi kaldırılır; bekleme sırasında işlem yok; bağlanma beklemeyi sıfırlamaz.
- Harita: kişiye özel kopya, kuzey üstte, sade zeytin/koyu yeşil/toprak tonları, topoğrafik konturlar ve koordinat ızgarası, güvenli/riskli hatları farklı stil, seçili rota, belirgin kendi konumu ve kervan. Rakip/takım arkadaşı konumları eklenmez. 128x128 vanilla harita ile başla; gerçek dünya yükseklik örneklemesi, görsel okunabilirlik ve performans ayrı kabul. Grafik kaynak indirilmez.
- Uygulama aşamaları: (A) skor ve rota düğümü çekirdeği, (B) yeniden doğma zamanlayıcısı ve yaşam döngüsü, (C) kişisel taktik harita, (D) gerçek araba/sandık/at/arazi/etkileşim, (E) sunucu+native istemci iki raund ve kötüye kullanım testleri, (F) üretim R38 uyum ve insan/Android.
- Mobil veri: indirme yok. RTS, canlı ve diğer ajanın çalışma alanı korunur; ayrı mevcut aday klasöründe ilerle, birleşimden önce koordinasyon.
