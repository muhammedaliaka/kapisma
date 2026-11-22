#!/bin/bash
# Tum senaryolari sirayla CANLI SUNUCUDAN AYRI test kopyasinda (server_test, port 25570) calistirir;
# ozet satirlarini tools/run_all.out'a yazar. Once guncel jar + ses modu kopyalanir.
cd "$(dirname "$0")/.."
OUT=tools/run_all.out
: > $OUT
cp mod/kapisma/build/libs/kapisma-0.1.0.jar server_test/mods/ || exit 1
python tools/gen_parkour_scenario.py > /dev/null
for s in lobby_board compass_pvp player_guard uhc_basic uhc_kill uhc_select bingo_basic bingo_lines bingo_tasks manhunt_basic manhunt_runner katil_basic katil_rules katil_scatter origin_join balance_check avian_kit new_races lobby_parkour xray_audit xray_nether voice_groups; do
  echo "### $s" >> $OUT
  PYTHONIOENCODING=utf-8 timeout 420 python tools/mctest.py tools/scenarios/$s.txt --dir server_test --xmx 4G > tools/last_$s.log 2>&1
  grep -a "OZET\|BEKLENEN YOK" tools/last_$s.log >> $OUT
  case $s in xray_*) echo "xray SORUN satiri: $(grep -a -c " SORUN" tools/last_$s.log), TEMIZ satiri: $(grep -a -c " TEMIZ" tools/last_$s.log)" >> $OUT;; esac
done
# Zayif cihaz (LITE profil, Xmx3G): hile korumasi ve temel oyun akislari
for s in xray_audit uhc_basic bingo_basic; do
  echo "### $s (LITE profil)" >> $OUT
  PYTHONIOENCODING=utf-8 timeout 420 python tools/mctest.py tools/scenarios/$s.txt --dir server_test --xmx 3G > tools/last_${s}_lite.log 2>&1
  grep -a "OZET\|BEKLENEN YOK" tools/last_${s}_lite.log >> $OUT
  grep -a -q "Donanim profili: LITE" tools/last_${s}_lite.log && echo "profil LITE dogrulandi" >> $OUT || echo "!! LITE profil secilmedi" >> $OUT
done
echo "### BITTI" >> $OUT
