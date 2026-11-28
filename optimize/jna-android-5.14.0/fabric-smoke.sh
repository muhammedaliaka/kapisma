#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root="$HOME/kapisma-jna-smoke-20261126"
stage=/data/local/tmp/kapisma-jna-smoke-20261126
cd "$root"
export KAPISMA_ANDROID_JNA=1
export KAPISMA_FIXED_HEAP_MIB=2048
export KAPISMA_CLEANUP_ADB_SERIAL=127.0.0.1:44247
[[ ! -e smoke.log && ! -e smoke.fifo ]] || exit 2
tr -d '\r' < server.properties | grep -qx 'server-ip=127.0.0.1'
tr -d '\r' < server.properties | grep -qx 'server-port=25577'
mkfifo smoke.fifo
exec 8<>smoke.fifo
pid=
scenario_complete=0
finish(){
  rc=$?
  trap - EXIT
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    printf 'stop\n' >&8 || true
    for i in $(seq 1 90); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$pid" 2>/dev/null; then echo STOP_TIMEOUT >> smoke.log; rc=5; fi
  fi
  if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then wait "$pid" || rc=5; fi
  if ! grep -Fq 'All dimensions are saved' smoke.log || ! grep -Fq 'server_mode=restored' smoke.log; then rc=5; fi
  if grep -Eq 'UnsatisfiedLinkError|NoClassDefFoundError.*com.sun.jna.Native|Segmentation fault|OutOfMemoryError|STOP_TIMEOUT' smoke.log; then rc=6; fi
  if [[ "$rc" == 0 && "$scenario_complete" == 1 ]]; then printf 'SMOKE_SCENARIO_COMPLETE\n' >> smoke.log; fi
  printf 'exit=%s\n' "$rc" >> smoke.log
  adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push smoke.log "$stage/" >/dev/null || true
  exit "$rc"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
printf '%s  %s\n' '036c95af4796283ff46a2ce5026980863b50b6310f45c2a8e92e3673f40f4341' mods/kapisma-0.1.0.jar | sha256sum -c -
bash "$PWD/ultra_termux.sh" --launch "$PWD" <&8 >smoke.log 2>&1 &
pid=$!
for i in $(seq 1 100); do
  if grep -Fq 'Done (' smoke.log; then break; fi
  kill -0 "$pid" 2>/dev/null || exit 3
  sleep 2
done
grep -Fq 'Done (' smoke.log || exit 3
grep -Fq 'android_jna=5.14.0 verified' smoke.log || exit 3
sleep 8
printf 'list\ntick query\n' >&8
sleep 5
grep -Fq 'There are 0 of a max of 4 players online' smoke.log || exit 4
grep -Fq 'Average time per tick:' smoke.log || exit 4
scenario_complete=1
exit 0
