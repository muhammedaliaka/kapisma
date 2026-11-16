# tools

| Dosya | Ne işe yarar |
|---|---|
| `run_all.sh` | Tüm test senaryolarını (`scenarios/`) canlı sunucudan ayrı `server_test` kopyasında çalıştırır (NORMAL + LITE profil), sonuç `run_all.out` |
| `mctest.py` | Tek senaryo koşucusu (konsol komutları + `expect`); Carpet sahte oyuncusunu geçici yükler |
| `test_pack.py` | Sunucu zip'ini temiz klasöre açıp gerçek `start.bat` / `start.sh` ile doğrular |
| `make_release.py` | `release/` altına sunucu ve istemci zip'lerini üretir |
| `serve.py` | Etkileşimli deneme sunucusu (bot ekleme vb.) |
| `mcknow.py` | Minecraft jar'ından bilgi kütüphanesi (`knowledge/`) üretir; **sen kendi jar'ından üret**, Mojang verisi depoya eklenmez |
| `seedscan/`, `vendor/cubiomes` | Seed havuzu üretimi. cubiomes için: `git clone https://github.com/Cubitect/cubiomes tools/vendor/cubiomes` |
| `test-mods/` | Test için Carpet jar'ı (bkz. `test-mods/README.txt`) |

Test kurulumu (bir kere): `server_test/` klasörüne `pack/server` içeriği + `kapisma` jar'ı + Fabric API + Origins + Simple Voice Chat kur (ya da sunucu paketini aç), `tools/test-mods/`'a Carpet jar'ını koy, `bash tools/run_all.sh`.
