#!/usr/bin/env bash
# Rotate a stopped isolated runtime. No deletion and no live path access.
set -euo pipefail
old="$HOME/kapisma-ultra-test-20261126-r37"
new="$HOME/kapisma-ultra-test-20261126-r38"
stage=/data/local/tmp/kapisma-ultra-test-20261126-r38
serial=127.0.0.1:44247
oldjar=0e2326996ed44829a736cd3aa7517d3bcf5d00ea473bdee999949261ec1f5843
newjar=9708483619c3dab78ff3a536d170a8c5c28c42564ba99045ef634dc4ff880463
[[ -d "$old" && ! -e "$new" && ! -e "$old/r37-evidence" ]]
grep -Fqx 'exit=0' "$old/stress.log"
grep -Fqx 'server_mode=restored' "$old/stress.log"
grep -Fq 'HARNESS_NATIVE_COMPLETE players=4' "$old/stress.log"
! pgrep -x java >/dev/null
[[ "$(sha256sum "$old/mods/kapisma-0.1.0.jar" | awk '{print $1}')" == "$oldjar" ]]
oldlog=$(sha256sum "$old/stress.log" | awk '{print $1}')
[[ "$oldlog" == a41b2817e9ea5d53ffa6dc9d6ee3d94d6b873a2f467b853c25f6f1cc1126a2cb ]]
files=$(find "$old" -type f | wc -l)
kib=$(du -sk "$old" | cut -f1)
mv -- "$old" "$new"
cd "$new"
mkdir r37-evidence
shopt -s dotglob nullglob
for f in *; do
  case "$f" in r37-evidence|r36-evidence|libraries|versions|mods|fabric-server-launch.jar|eula.txt) continue;; esac
  [[ ! -e "r37-evidence/$f" ]]
  mv -- "$f" "r37-evidence/$f"
done
# Preserve loader cache too: the previous remapped cache is not used by the new JAR.
mv -- mods/kapisma-0.1.0.jar r37-evidence/kapisma-0.1.0.jar
[[ "$(sha256sum r37-evidence/kapisma-0.1.0.jar | awk '{print $1}')" == "$oldjar" ]]
[[ "$(sha256sum r37-evidence/stress.log | awk '{print $1}')" == "$oldlog" ]]
[[ ! -e "$old" && ! -e stress.log && ! -e metrics.tsv && ! -e cmds ]]
for f in config server.properties ultra_termux.sh android_server_mode.sh android_prelaunch_cleanup.sh android_thermal_guard.sh android_thread_probe.sh android_uptime_ms.sh run_android_stress.sh native_client_fixture.sh native_route_fixture.sh mods/kapisma-0.1.0.jar; do
  adb -s "$serial" pull "$stage/$f" "./$f" >/dev/null
done
[[ "$(sha256sum mods/kapisma-0.1.0.jar | awk '{print $1}')" == "$newjar" ]]
[[ "$(sha256sum server.properties | awk '{print $1}')" == 9b7f0e9a56d134e337f23753705ee79f1814016bf97dbb12926a7093246e0f12 ]]
[[ "$(sha256sum native_route_fixture.sh | awk '{print $1}')" == ed9923517610d5b66e9a74e799e6fb5cfd1b64e37a3277072c6462045bb34a80 ]]
[[ "$(sha256sum native_client_fixture.sh | awk '{print $1}')" == 73372c35cc4eab8e2cac7188cd6f33999084ff650a24dd9f2cd2e69a3ee85cdc ]]
printf 'old=%s\nnew=%s\narchive=%s/r37-evidence\nprior_files=%s\nprior_allocated_kib=%s\n' "$old" "$new" "$new" "$files" "$kib" > reuse-manifest-r38.txt
sha256sum r37-evidence/stress.log r37-evidence/kapisma-0.1.0.jar mods/kapisma-0.1.0.jar server.properties run_android_stress.sh native_client_fixture.sh native_route_fixture.sh >> reuse-manifest-r38.txt
adb -s "$serial" push reuse-manifest-r38.txt "$stage/" >/dev/null
echo REUSE_READY
