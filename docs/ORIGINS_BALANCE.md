# Irk Dengesi (Origins 1.13) — 1–10 ölçeği

Kural: en iyi ve en kötü ırk arasındaki fark **en fazla 1 puan**. Human dahil.
Puanlar elle yapılmış tahmindir (UHC/Tombala/Manhunt genel gücü, 1000 blokluk alan, Nether kapalı UHC varsayımı). Gerçek oyunda gözleme göre ayarlanmalı.

## Güncel liste: 13 ırk (8 Origins + 5 Kapışma)

| Irk | Puan | Rol | Not |
|---|---|---|---|
| Human | 5.0 | Referans | Güç yok |
| Avian | 5.0 | Hafif / hareketli | Et yiyemez → başlangıç kitinde biftek yerine altın havuç |
| Blazeborn | 5.0 | Ateş | `water_vulnerability` 2 → 1; `burning_wrath` +3 → +4; **Nether'da doğma gücü etkisiz** (aşağıya bak) |
| Arachnid | 5.5 | Tırmanış | Değişmedi |
| Feline | 5.5 | Çevik | Değişmedi |
| Enderian | 6.0 | Işınlanma | `water_vulnerability` 2 → 1; `extra_reach` +1.5 → +1.0 |
| Shulk | 6.0 | Zırh | Değişmedi |
| Merling | 6.0 | Su | `swim_speed` +1.5 → +1.0 |
| **Golem** | 5.5 | Tank | +3 kalp, geri savrulmaya %60 direnç, %12 yavaş |
| **Tilki (Fox)** | 5.5 | Koşucu / çevik | %12 hız, +%25 zıplama, düşme hasarı yok; −1 kalp |
| **Cüce (Dwarf)** | 5.0 | Madenci | Taşı %50 hızlı kırar, %15 küçük boy (vurması zor); −1 kalp |
| **Buz Ruhu (Frostborn)** | 5.5 | Kontrol | Donma hasarına bağışık, karda %25 hız, vurduğu 3 sn yavaşlar (5 sn bekleme); ateşten +%50 hasar |
| **Kurt (Wolf)** | 5.5 | Dövüşçü | %10 hız, sadece et yer; canı 5 kalbe inince +3 hasar |

Aralık: 5.0 – 6.0 → fark 1.0 (kural sağlanıyor).

## Kaldırılan ırklar (ve neden)

| Irk | Neden |
|---|---|
| **Phantom (Hayalet)** | Duvarlardan geçebiliyor ve görünmez olabiliyor. Border/arena kurallarını, Tombala'da eşya korumayı, Manhunt takibini ve PvP'yi bozuyor. Gücü açıp kapatmak için istemci tuşu gerekiyor (TLauncher arkadaşlara anlatmak zor), gündüz yanma + açlık güçleri de menü dışında ayrı ayarlanmalıydı. |
| **Elytrian** | Ücretsiz uçuş. 1000 blokluk Tombala alanını ve Manhunt kovalamacasını anlamsızlaştırıyor; fırlatma gücü yine istemci tuşuna bağlı. |

İkisi de `OriginService.ALL` listesinden ve Origins katmanından (`data/origins/origin_layers/origin.json`, `replace: true`) çıkarıldı. Geri eklemek için bu iki yeri geri almak yeterli.

## Düzeltilen uyumsuzluklar

| Sorun | Düzeltme |
|---|---|
| Blazeborn `origins:nether_spawn` — oyuncuyu Nether'a doğuruyor; UHC/Tombala'da Nether kapalı, oyun dünyaları geçici | Güç etkisiz bir güçle (`generic.luck` +0) değiştirildi |
| Avian et yiyemiyor ama kit biftekti | Kitte altın havuç |
| Yeni ırk `frostborn` yüklenmiyordu (`apply_effect` JSON'unda `id` eksikti) | `kapisma:frost_chill` düzeltildi, `new_races` testi hata satırı = 0 |
| Origins'in kendi 10 ırkı seçim ekranında hâlâ çıkıyordu | Katman `replace: true` ile 13 ırka indirildi |
| Menüler 10 ırka göre çizilmişti | 15 → 13 ırk, 9x4 menü |

## 5 yeni ırk hangi ölçüte göre seçildi?

1. **Rol boşluğu doldurmak.** Origins'in ırkları ağırlıkla özel arazi/yetenek ırkı (su, gökyüzü, Nether, tırmanış). Saf tank, koşucu, madenci, kontrol ve "geri dönüş" dövüşçüsü yoktu → Golem, Tilki, Cüce, Buz Ruhu, Kurt.
2. **Sadece Origins'in kendi güç türleri.** Özellik (attribute), hasar çarpanı, koşullu özellik. Yeni kod, istemci tuşu, ek istemci modu gerekmiyor; sunucudaki veri paketi yeterli ve TLauncher istemcileri değişmeden çalışıyor.
3. **Her ırkta bir bedel.** Güç + dezavantaj dengesi (Golem yavaş, Tilki/Cüce −1 kalp, Buz Ruhu ateşe zayıf, Kurt sadece et). Böylece 5.0–6.0 bandında kalmak kolay.
4. **Harita/mod bağımsız.** Su, Nether veya gökyüzü gerektirmiyor; UHC, Tombala ve Manhunt'ta aynı çalışıyor.
5. **Mevcut ırkların kopyası olmamak.** Başka bir uçan/su/ateş ırkı eklenmedi.

## Notlar
- Datapack override'ları `kapisma` modunun içinde; Origins jar'ına dokunulmadı. Silmek için `src/main/resources/data/origins/powers/` klasörünü temizlemek yeterli.
- Yasaklı ırk listesi menüden (ve `/kapisma ban|unban`) yönetilir; host zayıf/güçlü bulduğu ırkı kapatabilir. Eski ayar dosyalarında kalan `phantom`/`elytrian` kayıtları zararsızdır.
- Yeni ırkların istemcideki adları/açıklamaları `kapisma-hud` istemci modunun dil dosyasında; mod yoksa ham anahtar görünür (oyun yine çalışır).
- Sayılar doğrulanmış denge değil, makul başlangıç: gerçek oyun sonuçlarına göre ayarlanmalı.
- Olası ince ayar adayları: Shulk zırhı, Enderian ışınlanma bekleme süresi, Kurt hasar eşiği.
