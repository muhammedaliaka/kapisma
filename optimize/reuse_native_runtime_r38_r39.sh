#!/usr/bin/env bash
# Reuse same verified binary/dependency cache, archive unique R38 state.
set -euo pipefail
old="$HOME/kapisma-ultra-test-20261126-r38"
new="$HOME/kapisma-ultra-test-20261126-r39"
stage=/data/local/tmp/kapisma-ultra-test-20261126-r39
serial=127.0.0.1:44247
jar=9708483619c3dab78ff3a536d170a8c5c28c42564ba99045ef634dc4ff880463
oldlog=d5945272894cc54a3418509ed068af0497e7b00180e84de5984e618120c5656e
[[ -d "$old" && ! -e "$new" && ! -e "$old/r38-evidence" ]]
grep -Fqx 'exit=0' "$old/stress.log"
grep -Fqx 'server_mode=restored' "$old/stress.log"
grep -Fq 'HARNESS_NATIVE_COMPLETE players=4' "$old/stress.log"
! pgrep -x java >/dev/null
[[ "$(sha256sum "$old/mods/kapisma-0.1.0.jar" | awk '{print $1}')" == "$jar" ]]
[[ "$(sha256sum "$old/stress.log" | awk '{print $1}')" == "$oldlog" ]]
files=$(find "$old" -type f | wc -l)
kib=$(du -sk "$old" | cut -f1)
# If debug profiler actually left a report in this isolated run, record names
# and hash. Do not silently substitute the ordinary log for a profiler tree.
cd "$old"
find . -maxdepth 2 -type f -name 'profile-results-*' -print > r38-profile-paths.txt
while IFS= read -r f; do
  [[ "$f" == ./debug/* && -f "$f" ]] || continue
  if [[ $(wc -c < "$f") -le 4194304 ]]; then
    adb -s "$serial" push "$f" "$stage/" >/dev/null
  fi
done < r38-profile-paths.txt
mv -- "$old" "$new"
cd "$new"
mkdir r38-evidence
shopt -s dotglob nullglob
for f in *; do
  case "$f" in r38-evidence|r37-evidence|r36-evidence|libraries|versions|mods|fabric-server-launch.jar|eula.txt|.fabric) continue;; esac
  [[ ! -e "r38-evidence/$f" ]]
  mv -- "$f" "r38-evidence/$f"
done
[[ "$(sha256sum r38-evidence/stress.log | awk '{print $1}')" == "$oldlog" ]]
[[ ! -e "$old" && ! -e stress.log && ! -e metrics.tsv && ! -e cmds ]]
for f in config server.properties ultra_termux.sh android_server_mode.sh android_prelaunch_cleanup.sh android_thermal_guard.sh android_thread_probe.sh android_uptime_ms.sh run_android_stress.sh native_client_fixture.sh native_route_fixture.sh; do
  adb -s "$serial" pull "$stage/$f" "./$f" >/dev/null
done
[[ "$(sha256sum mods/kapisma-0.1.0.jar | awk '{print $1}')" == "$jar" ]]
[[ "$(sha256sum server.properties | awk '{print $1}')" == 9b7f0e9a56d134e337f23753705ee79f1814016bf97dbb12926a7093246e0f12 ]]
[[ "$(sha256sum run_android_stress.sh | awk '{print $1}')" == b9821743a71604f1b88ea135f22fe33cfaf880afbfd6d495ca94719d913a0f8c ]]
[[ "$(sha256sum native_route_fixture.sh | awk '{print $1}')" == ed9923517610d5b66e9a74e799e6fb5cfd1b64e37a3277072c6462045bb34a80 ]]
[[ "$(sha256sum native_client_fixture.sh | awk '{print $1}')" == 73372c35cc4eab8e2cac7188cd6f33999084ff650a24dd9f2cd2e69a3ee85cdc ]]
printf 'old=%s\nnew=%s\narchive=%s/r38-evidence\nprior_files=%s\nprior_allocated_kib=%s\n' "$old" "$new" "$new" "$files" "$kib" > reuse-manifest-r39.txt
sha256sum r38-evidence/stress.log mods/kapisma-0.1.0.jar server.properties run_android_stress.sh native_client_fixture.sh native_route_fixture.sh >> reuse-manifest-r39.txt
adb -s "$serial" push reuse-manifest-r39.txt r38-evidence/r38-profile-paths.txt "$stage/" >/dev/null
echo REUSE_READY
