# Devletler RTS için hazır yapı seçenekleri

27 Kasım 2026 · Minecraft Java 1.21.1 / Fabric

## Yerel ölçüler

Kurulu 1.21.1 sunucu arşivindeki `data/minecraft/structure/village` yolundan dört şablonun NBT `size` alanı okundu. Ölçüler genişlik × yükseklik × derinlik sırasındadır. Oyun dosyaları yerel arşivde kaldı.

| Oyun içi şablon | Ölçü |
| --- | --- |
| Ova köy meydanı `plains_meeting_point_1` | 10 × 7 × 10 |
| Çöl köy meydanı `desert_meeting_point_1` | 17 × 6 × 9 |
| Savan köy meydanı `savanna_meeting_point_1` | 14 × 5 × 12 |
| Tayga tapınağı `taiga_temple_1` | 13 × 14 × 11 |

Mojang’ın yerleşik `/place template` komutu bir şablonu dünya blokları olarak yerleştirir. Bu şablonlar mevcut kurulumda kullanılabilir. Maç için doğrudan yerleştirme, örtülen arazi ve kaynak bloklarının sıfırlamada geri yüklenmesini gerektirir. Oyun dosyalarının dağıtımına Mojang’ın koşulları uygulanır. [Java 1.19 yerleştirme komutu](https://feedback.minecraft.net/hc/en-us/articles/6731464524941-Minecraft-Java-Edition-1-19) · [Minecraft Kullanım Koşulları](https://www.minecraft.net/en-us/eula)

## Harici seçenekler

- **Trek** ev, kule, kale ve köy yapıları içeren MIT lisanslı bir dünya üretim paketi. Yayımcı 1.21.x veri paketi ve Fabric seçenekleri listeliyor. Belirli bir yapı ileride seçilecekse paketin belirttiği üçüncü taraf yapı kredileri de incelenmeli. Dünya üretim paketi olarak kurulması yeni içerik indirmesi ve üretim ayarlarının denenmesini gerektirir. [Trek yayım sayfası](https://modrinth.com/datapack/trek) · [Trek yapı kredileri](https://trek-2.gitbook.io/trek#credits)
- **Reign of Nether** kendi vanilla bloklu binaları, hasar sistemi ve RTS kuralları olan GPLv3 projesi. Yayımlanan kurulum Forge 1.20.1 kullanıyor. Fabric 1.21.1 profiline uygulanabilirlik, bütün mod kurulumu yerine yapı verisinin ve lisans koşullarının ayrıca incelenmesine bağlı. [Reign of Nether kaynak deposu](https://github.com/SoLegendary/reignofnether)
- **MineFortress** MIT lisanslı Fabric RTS projesi. Yazarın kaynak dalında Minecraft 1.20.2 ve Fabric Loader 0.14.24 sabitlenmiş. 1.21.1 için hazır bir bağımlılık olarak değerlendirilirse sürüm uyarlaması ve yerel çalışma testi gerekir. [MineFortress lisansı](https://github.com/remmintan/minefortress/blob/master/LICENSE) · [MineFortress sürüm ayarları](https://github.com/remmintan/minefortress/blob/master/gradle.properties)

## Mevcut profile uygun yaklaşım

Belediyeyi yaklaşık 10 blok genişliğinde, en az 7 blok yüksekliğinde ve oyuncu boyunu referans alan kapı ve çatı silüetiyle tasarlamak yerel ölçülerle uyumlu. Mevcut vanilla `block_display` parçaları ölçeklenip aynı yapı etiketiyle temizlenebilir. Bu varlıklar çarpışmasız görseldir. Arazi, kaynak gövdeleri ve asker rotaları dünya blokları olarak kalır. Uzak görünürlük `view_range` ile ayarlanabilir ve gerçek mesafe istemcinin görüntü ayarlarına da bağlıdır. [Mojang görünüm varlıkları ve dönüşümler](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-19-4)
