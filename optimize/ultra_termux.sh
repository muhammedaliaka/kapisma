#!/usr/bin/env bash
# Android ARM64 / Java 21 candidate launcher. No automatic installs or config edits.
set -euo pipefail
fail() { printf 'ULTRA: %s\n' "$*" >&2; exit 2; }
mode="${1:---plan}"
case "$mode" in --plan|--launch) ;; *) fail 'usage: ultra_termux.sh --plan OR --launch SERVER_DIR';; esac
[[ "$#" -le 2 ]] || fail 'too many arguments'
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ "$mode" == --launch ]]; then
  [[ $# == 2 ]] || fail 'server directory required'
  [[ "$(uname -m)" == aarch64 ]] || fail 'ARM64 required for this candidate'
  [[ -f "$2/fabric-server-launch.jar" && -f "$2/server.properties" && -f "$2/eula.txt" ]] || fail 'missing server files'
  grep -Eq '^eula=true[[:space:]]*$' "$2/eula.txt" || fail 'Mojang EULA must be accepted by user'
  command -v java >/dev/null || fail 'Java not installed'
  version=$(java -version 2>&1) || fail 'Java launch failed'
  [[ "$version" == *'version "21.'* && "$version" == *'64-Bit'* ]] || fail 'Java 21 64-bit required'
  # Native Android JNA is opt-in, pinned to the exact server dependency.
  # Validate before any application state is changed; do not fall back to glibc.
  jna_args=()
  case "${KAPISMA_ANDROID_JNA:-0}" in
    1)
      jna_dir="$HOME/jna-android-5.14.0"
      jna_jar="$2/libraries/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"
      [[ -f "$jna_dir/libjnidispatch.so" && -f "$jna_jar" ]] || fail 'verified Android JNA files missing'
      printf '%s  %s\n' '3730ce014f7a6807ae74af78c3b9f5a3cba1f5886316b3d3858493df923efe96' "$jna_dir/libjnidispatch.so" | sha256sum -c - || fail 'Android JNA native hash mismatch'
      printf '%s  %s\n' '34ed1e1f27fa896bca50dbc4e99cf3732967cec387a7a0d5e3486c09673fe8c6' "$jna_jar" | sha256sum -c - || fail 'JNA Java dependency mismatch'
      jna_args=("-Djna.boot.library.path=$jna_dir" -Djna.nosys=true -Djna.noclasspath=true)
      printf 'android_jna=5.14.0 verified\n';;
    0) ;;
    *) fail 'KAPISMA_ANDROID_JNA must be 0 or 1';;
  esac
  command -v flock >/dev/null || fail 'flock required to prevent duplicate launch'
  exec 9>"$2/.kapisma-ultra.lock"; flock -n 9 || fail 'ULTRA server already running in this directory'
  # Existing, explicitly user-authorized local ADB connection only.
  # Never enable wireless debugging, pair, root, or escalate automatically.
  serial="${KAPISMA_CLEANUP_ADB_SERIAL:-}"
  [[ "$serial" =~ ^127\.0\.0\.1:[0-9]{1,5}$ ]] || fail 'prelaunch RAM cleanup requires an already authorized local ADB endpoint'
  command -v adb >/dev/null || fail 'Android adb client missing'
  command -v timeout >/dev/null || fail 'timeout command missing'
  [[ -r "$script_dir/android_prelaunch_cleanup.sh" ]] || fail 'cleanup helper missing'
  [[ "$(timeout 8 adb -s "$serial" get-state)" == device ]] || fail 'ADB not authorized'
  # Dedicated mode temporarily disables only verified third-party packages.
  # A persistent device journal permits manual recovery after an interrupted launch.
  server_mode_active=0
  locked=0
  cleanup(){
    rc=$?
    trap - EXIT
    if ((locked)); then termux-wake-unlock || true; fi
    if ((server_mode_active)); then
      if ! timeout 120 adb -s "$serial" shell sh -s -- --leave < "$script_dir/android_server_mode.sh"; then
        printf 'ULTRA: restore incomplete; use --leave after reconnecting local ADB\n' >&2
        rc=3
      fi
    fi
    exit "$rc"
  }
  trap cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM HUP
  case "${KAPISMA_SERVER_MODE:-1}" in
    1)
      [[ -r "$script_dir/android_server_mode.sh" ]] || fail 'dedicated mode helper missing'
      timeout 120 adb -s "$serial" shell sh -s -- --enter < "$script_dir/android_server_mode.sh" || fail 'dedicated mode failed; check --status and recover if needed'
      server_mode_active=1;;
    0) ;;
    *) fail 'KAPISMA_SERVER_MODE must be 0 or 1';;
  esac
  cleanup_mode=--apply
  case "${KAPISMA_RAM_DEEP:-1}" in
    1) cleanup_mode=--apply-deep;;
    0) ;;
    *) fail 'KAPISMA_RAM_DEEP must be 0 or 1';;
  esac
  timeout 45 adb -s "$serial" shell sh -s -- "$cleanup_mode" < "$script_dir/android_prelaunch_cleanup.sh" || fail 'RAM cleanup failed; server not launched'
fi
mem=/proc/meminfo
# Explicit fixture mode is permitted for planning only, never launching.
if [[ "$mode" == --plan && $# == 2 ]]; then mem="$2"; fi
[[ -r "$mem" ]] || fail 'memory statistics unavailable'
read -r total avail < <(LC_ALL=C awk '{sub(/^\357\273\277/,"")} /^MemTotal:/ {t=int($2/1024)} /^MemAvailable:/ {a=int($2/1024)} END {print t+0,a+0}' "$mem")
[[ "$total" =~ ^[0-9]+$ && "$avail" =~ ^[0-9]+$ ]] || fail 'invalid memory statistics'
(( total > 0 && avail > 0 && avail <= total )) || fail 'invalid memory range'
reserve=896; native=768; limit=3072; minimum=1536; view=6; sim=4; threads=6; profile=mobile6
if (( total <= 4096 || avail < 3072 )); then native=512; limit=1280; minimum=1024; view=4; sim=3; threads=4; profile=ultra3; fi
budget=$((avail-reserve-native)); heap=$((total*48/100))
(( heap <= limit )) || heap=$limit
(( heap <= budget )) || heap=$budget
(( heap >= 0 )) || heap=0
heap=$((heap/64*64))
# Optional exact lower heap for reproducible comparisons. Never raise the safe budget.
fixed_heap="${KAPISMA_FIXED_HEAP_MIB:-}"
if [[ -n "$fixed_heap" ]]; then
  [[ "$fixed_heap" =~ ^[1-9][0-9]{3}$ ]] || fail 'invalid fixed heap'
  (( fixed_heap >= minimum && fixed_heap <= heap && fixed_heap % 64 == 0 )) || fail 'fixed heap exceeds safe budget or alignment'
  heap=$fixed_heap
fi
printf 'profile=%s total_mib=%s available_mib=%s heap_mib=%s max_players=4 view=%s simulation=%s\n' "$profile" "$total" "$avail" "$heap" "$view" "$sim"
(( heap >= minimum )) || fail 'insufficient available RAM; close unused apps and retry'
[[ "$mode" == --launch ]] || exit 0
[[ $# == 2 ]] || fail 'server directory required'
[[ "$(uname -m)" == aarch64 ]] || fail 'ARM64 required for this candidate'
cd -- "$2" || fail 'server directory not found'
[[ -f fabric-server-launch.jar && -f server.properties && -f eula.txt ]] || fail 'missing server files'
grep -Eq '^eula=true[[:space:]]*$' eula.txt || fail 'Mojang EULA must be accepted by user'
command -v java >/dev/null || fail 'Java not installed'
version=$(java -version 2>&1) || fail 'Java launch failed'
[[ "$version" == *'version "21.'* && "$version" == *'64-Bit'* ]] || fail 'Java 21 64-bit required'
check_cap() {
  local key="$1" max="$2" min="$3" value
  value=$(awk -F= -v k="$key" '$1==k {n++; v=$2} END {if(n==1){gsub(/\r/,"",v);print v}else exit 1}' server.properties) || fail "missing or duplicate $key"
  [[ "$value" =~ ^[0-9]{1,3}$ ]] || fail "invalid $key"
  value=$((10#$value))
  (( value >= min && value <= max )) || fail "$key must be $min..$max; no automatic edit"
}
check_cap max-players 4 1; check_cap view-distance "$view" 2; check_cap simulation-distance "$sim" 2
cpus=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)
[[ "$cpus" =~ ^[0-9]+$ ]] && (( cpus >= 1 )) || cpus=4
(( threads <= cpus )) || threads=$cpus
# Wake lock only while this launch owns it; does not bypass Android LMK/thermal management.
if command -v termux-wake-lock >/dev/null && termux-wake-lock; then locked=1; fi
export KAPISMA_PROFILE=lite
java "${jna_args[@]}" -Xms512M "-Xmx${heap}M" -XX:+UseG1GC "-XX:ActiveProcessorCount=$threads" -XX:MaxGCPauseMillis=150 -XX:+ParallelRefProcEnabled -Dfile.encoding=UTF-8 '-Xlog:gc*:file=ultra-gc.log:time,uptime,level,tags:filecount=3,filesize=4M' -jar fabric-server-launch.jar nogui
