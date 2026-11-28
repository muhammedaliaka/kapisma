#!/usr/bin/env bash
# Reuse only stopped R36 runtime. Preserve prior world, evidence, settings and
# every file not explicitly shared as a same-JAR runtime dependency. No deletion.
set -euo pipefail
old="$HOME/kapisma-ultra-test-20261126-r36"
new="$HOME/kapisma-ultra-test-20261126-r37"
stage=/data/local/tmp/kapisma-ultra-test-20261126-r37
serial=127.0.0.1:44247
[[ -d "$old" && ! -e "$new" && -f "$old/stress.log" && ! -e "$old/r36-evidence" ]]
grep -Fqx 'exit=7' "$old/stress.log"
grep -Fqx 'server_mode=restored' "$old/stress.log"
if pgrep -x java >/dev/null; then echo 'Java still running'; exit 3; fi
[[ "$(sha256sum "$old/mods/kapisma-0.1.0.jar" | awk '{print $1}')" == 0e2326996ed44829a736cd3aa7517d3bcf5d00ea473bdee999949261ec1f5843 ]]
# Avoid copying immutable runtime folders; archive stopped run state in place.
file_count=$(find "$old" -type f | wc -l)
disk_kib=$(du -sk "$old" | cut -f1)
old_log_sha=$(sha256sum "$old/stress.log" | awk '{print $1}')
mv -- "$old" "$new"
cd "$new"
mkdir r36-evidence
shopt -s dotglob nullglob
for f in *; do
  case "$f" in
    r36-evidence|libraries|versions|mods|fabric-server-launch.jar|eula.txt|.fabric) continue;;
  esac
  [[ ! -e "r36-evidence/$f" ]]
  mv -- "$f" "r36-evidence/$f"
done
printf 'old=%s\nnew=%s\narchive=%s/r36-evidence\nprior_files=%s\nprior_allocated_kib=%s\nkept=libraries,versions,mods,fabric-server-launch.jar,eula.txt,.fabric_if_present\n' "$old" "$new" "$new" "$file_count" "$disk_kib" > reuse-manifest.txt
[[ "$(sha256sum r36-evidence/stress.log | awk '{print $1}')" == "$old_log_sha" ]]
[[ ! -e "$old" && -f r36-evidence/stress.log && ! -e stress.log && ! -e metrics.tsv && ! -e cmds ]]
for f in config server.properties ultra_termux.sh android_server_mode.sh android_prelaunch_cleanup.sh android_thermal_guard.sh android_thread_probe.sh android_uptime_ms.sh run_android_stress.sh native_client_fixture.sh native_route_fixture.sh; do
  adb -s "$serial" pull "$stage/$f" "./$f" >/dev/null
done
[[ "$(sha256sum run_android_stress.sh | awk '{print $1}')" == c196b0a659fb91ac03f5462f2c0870f5ff5f3760905702200986ef1dcd6b494f ]]
[[ "$(sha256sum native_route_fixture.sh | awk '{print $1}')" == 14771192eccc79274e7ecad748b21821f6d45161a918a8a8b761f7659507b174 ]]
[[ "$(sha256sum server.properties | awk '{print $1}')" == 9b7f0e9a56d134e337f23753705ee79f1814016bf97dbb12926a7093246e0f12 ]]
sha256sum r36-evidence/stress.log mods/kapisma-0.1.0.jar server.properties run_android_stress.sh >> reuse-manifest.txt
adb -s "$serial" push reuse-manifest.txt "$stage/" >/dev/null
printf 'REUSE_READY\n'
