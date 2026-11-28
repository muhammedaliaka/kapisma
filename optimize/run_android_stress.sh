#!/usr/bin/env bash
# Isolated on-device Carpet simulation. Not actual human/network client test.
set -euo pipefail
run_id="${1:-kapisma-ultra-test-20261126-r4}"
[[ "$run_id" =~ ^kapisma-ultra-test-[0-9]{8}-r[0-9]+$ ]] || exit 2
cd "$HOME/$run_id"
export KAPISMA_CLEANUP_ADB_SERIAL="${KAPISMA_CLEANUP_ADB_SERIAL:-127.0.0.1:44247}"
stage="/data/local/tmp/$run_id"
log="$PWD/stress.log"; metrics="$PWD/metrics.tsv"
[[ ! -e "$log" && ! -e "$metrics" && ! -e cmds ]] || { echo "test already used"; exit 2; }
mkfifo cmds
exec 8<>cmds
pid=
scenario_complete=0
parity_complete=0
route_complete=0
probe_count=0
movement_probe_complete=0
carpet_profile_complete=0
fixed_entity_complete=0
native_complete=0
native_route_ticket=0
native_route_dimension=
fixed_ticket_requested=0
mobcap_changed=0
preload_complete=0
preload_dimension=
preload_requested=0
preload_released=0
preload_xs=(16 104 16 -72); preload_zs=(-136 -48 40 -48)
thermal_log="$PWD/thermal.tsv"
abort_reason=
finish(){
  rc=$?
  trap - EXIT
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    # Only remove our own explicitly requested test tickets, in the verified arena.
    if [[ "$preload_released" == 0 && "$preload_requested" -gt 0 && "$preload_dimension" =~ ^fantasy:[a-z0-9_/-]+$ ]]; then
      for ((j=0;j<preload_requested;j++)); do
        printf 'execute in %s run forceload remove %s %s\n' "$preload_dimension" "${preload_xs[j]}" "${preload_zs[j]}" >&8 || true
      done
      printf 'execute in %s run forceload query\n' "$preload_dimension" >&8 || true
    fi
    if [[ "$native_route_ticket" == 1 && "$native_route_dimension" =~ ^fantasy:[a-z0-9_/-]+$ ]]; then
      printf 'execute in %s run forceload remove -64 -64 159 -17\nexecute in %s run forceload query\n' "$native_route_dimension" "$native_route_dimension" >&8 || true
    fi
    if [[ "$fixed_ticket_requested" == 1 ]]; then
      printf 'execute in minecraft:overworld run forceload remove 0 -64 31 -33\nexecute in minecraft:overworld run forceload query\n' >&8 || true
    fi
    if [[ "$mobcap_changed" == 1 ]]; then printf 'spawn mobcaps set 70\n' >&8 || true; fi
    printf 'stop\n' >&8 || true
    for i in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$pid" 2>/dev/null; then echo "STOP TIMEOUT; manual recovery required" >> "$log"; rc=5; fi
  fi
  if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then wait "$pid" || rc=5; fi
  if [[ -n "$abort_reason" ]]; then printf 'HARNESS_ABORT reason=%s\n' "$abort_reason" >> "$log"; fi
  if [[ "$rc" == 0 && "$parity_complete" == 1 ]]; then
    printf 'HARNESS_MAP_PARITY_COMPLETE treasure=4 region=1 performance_certified=false\n' >> "$log"
  fi
  if [[ "$rc" == 0 && "$preload_complete" == 1 ]]; then printf 'HARNESS_PRELOAD_COMPLETE centers=4 route_steps=1 performance_certified=false\n' >> "$log"; fi
  if [[ "$rc" == 0 && "$route_complete" == 1 ]]; then printf 'HARNESS_ROUTE_COMPLETE first=12 revisit=12 performance_certified=false\n' >> "$log"; fi
  if [[ "$rc" == 0 && "$native_complete" == 1 ]]; then printf 'HARNESS_NATIVE_COMPLETE players=4 exploration_samples=12 performance_certified=false\n' >> "$log"; fi
  if [[ "$rc" == 0 && "$fixed_entity_complete" == 1 ]]; then printf 'HARNESS_FIXED_ENTITY_COMPLETE mobs=64 players=4 performance_certified=false\n' >> "$log"; fi
  if [[ "$rc" == 0 && "$carpet_profile_complete" == 1 ]]; then printf 'HARNESS_CARPET_PROFILE_COMPLETE health_ticks=100 entities_ticks=100 performance_certified=false\n' >> "$log"; fi
  if [[ "$rc" == 0 && "$movement_probe_complete" == 1 ]]; then printf 'HARNESS_MOVEMENT_PROBE_COMPLETE samples=12 performance_certified=false\n' >> "$log"; fi
  if [[ "$rc" == 0 && "$scenario_complete" == 1 ]]; then printf 'HARNESS_QUERY_LATENCY_V1\nHARNESS_ACTIVITY_TRACE_V2\nHARNESS_SCENARIO_COMPLETE\n' >> "$log"; fi
  printf 'exit=%s\n' "$rc" >> "$log"
  adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push "$log" "$metrics" "$stage/" || true
  for evidence in profile.jfr jfr-preflight.txt ultra-gc.log thermal.tsv query-latency.tsv thread-probe-1.txt thread-probe-2.txt preload.tsv; do
    if [[ -f "$evidence" ]]; then adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push "$evidence" "$stage/" || true; fi
  done
  # Preserve vanilla preparation profiling for attribution, if a report exists.
  # Only this isolated run's bounded debug output; never scan other worlds.
  for evidence in debug/profile-results-*.txt; do
    if [[ -f "$evidence" && "$(wc -c < "$evidence")" -le 4194304 ]]; then
      adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push "$evidence" "$stage/" || true
    fi
  done
  exit "$rc"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
printf 'phase\ttimestamp\tavailable_kib\trss_kib\tbattery_deci_c\tresponse\n' > "$metrics"
printf 'phase\tstart_uptime_ms\telapsed_ms\tresult\n' > query-latency.tsv
# Bounded profiling on test JVM only. Probe support without modifying installed launcher.
if [[ "${KAPISMA_JFR:-0}" == 1 ]] && timeout 25 java -XX:StartFlightRecording=filename=probe.jfr,settings=default,dumponexit=true -version > jfr-preflight.txt 2>&1 && [[ -s probe.jfr ]]; then
  # Only profile the actual server JVM, not the launcher's java -version probes.
  export KAPISMA_REAL_JAVA="$(command -v java)"
  mkdir jfr-bin
  cat > jfr-bin/java <<'WRAPPER'
#!/data/data/com.termux/files/usr/bin/bash
set -eu
for arg in "$@"; do
  if [[ "$arg" == -jar ]]; then
    exec "$KAPISMA_REAL_JAVA" -XX:StartFlightRecording=filename=profile.jfr,settings=profile,dumponexit=true,maxsize=64m "$@"
  fi
done
exec "$KAPISMA_REAL_JAVA" "$@"
WRAPPER
  chmod 700 jfr-bin/java
  export PATH="$PWD/jfr-bin:$PATH"
  echo 'JFR available' >> jfr-preflight.txt
else
  echo 'JFR disabled or unavailable; use vanilla debug and GC telemetry' >> jfr-preflight.txt
fi
adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push jfr-preflight.txt "$stage/" >/dev/null
# One-variable experiment: cap Minecraft's background pool, not JVM CPU or GC.
# The default is unchanged. Never use this setting for an actual live server yet.
bg_threads="${KAPISMA_TEST_BG_THREADS:-}"
if [[ -n "$bg_threads" ]]; then
  [[ "$bg_threads" =~ ^[1-4]$ ]] || { echo 'invalid background pool cap'; exit 2; }
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Dmax.bg.threads=$bg_threads"
fi
# Record exact experimental inputs. Optional fixed heap must pass launcher budget validation.
map_mode="${KAPISMA_INCREMENTAL_MAPS:-0}"
[[ "$map_mode" == 0 || "$map_mode" == 1 ]] || { echo 'invalid incremental map mode'; exit 2; }
export KAPISMA_INCREMENTAL_MAPS="$map_mode"
spawn_mode="${KAPISMA_INCREMENTAL_SPAWNS:-0}"
[[ "$spawn_mode" == 0 || "$spawn_mode" == 1 ]] || { echo 'invalid incremental spawn mode'; exit 2; }
export KAPISMA_INCREMENTAL_SPAWNS="$spawn_mode"
chunk_mode="${KAPISMA_ADAPTIVE_CHUNKS:-0}"
[[ "$chunk_mode" == 0 || "$chunk_mode" == 1 ]] || { echo 'invalid adaptive chunk mode'; exit 2; }
export KAPISMA_ADAPTIVE_CHUNKS="$chunk_mode"
jna_mode="${KAPISMA_ANDROID_JNA:-0}"
[[ "$jna_mode" == 0 || "$jna_mode" == 1 ]] || { echo 'invalid Android JNA mode'; exit 2; }
export KAPISMA_ANDROID_JNA="$jna_mode"
xray_empty_skip="${KAPISMA_XRAY_EMPTY_SKIP:-0}"
[[ "$xray_empty_skip" == 0 || "$xray_empty_skip" == 1 ]] || exit 2
export KAPISMA_XRAY_EMPTY_SKIP="$xray_empty_skip"
timing="${KAPISMA_ULTRA_TIMING:-0}"
[[ "$timing" == 0 || "$timing" == 1 ]] || { echo 'invalid timing mode'; exit 2; }
export KAPISMA_ULTRA_TIMING="$timing"
proof="${KAPISMA_MAP_PARITY_PROOF:-0}"
[[ "$proof" == 0 || "$proof" == 1 ]] || { echo 'invalid parity mode'; exit 2; }
[[ "$proof" == 0 || "$map_mode" == 1 ]] || { echo 'parity proof requires incremental mode'; exit 2; }
export KAPISMA_MAP_PARITY_PROOF="$proof"
route_mode="${KAPISMA_TEST_ROUTE:-0}"
[[ "$route_mode" == 0 || "$route_mode" == 1 ]] || exit 2
[[ "$route_mode" == 0 || "$proof" == 0 ]] || { echo 'route and parity modes are exclusive'; exit 2; }
thread_probe="${KAPISMA_THREAD_PROBE:-0}"
[[ "$thread_probe" == 0 || "$thread_probe" == 1 ]] || exit 2
if [[ "$thread_probe" == 1 ]]; then
  [[ "$proof" == 0 && "${KAPISMA_JFR:-0}" == 0 && -f android_thread_probe.sh ]] || exit 2
fi
carpet_profile="${KAPISMA_CARPET_PROFILE:-0}"
[[ "$carpet_profile" == 0 || "$carpet_profile" == 1 ]] || exit 2
if [[ "$carpet_profile" == 1 ]]; then
  [[ "$route_mode" == 0 && "$proof" == 0 && "$thread_probe" == 0 && "${KAPISMA_JFR:-0}" == 0 ]] || exit 2
fi
profile_mobcap="${KAPISMA_PROFILE_MOBCAP:-70}"
[[ "$profile_mobcap" == 70 || "$profile_mobcap" == 35 ]] || exit 2
[[ "$profile_mobcap" == 70 || "$carpet_profile" == 1 ]] || exit 2
fixed_entity="${KAPISMA_FIXED_ENTITY_TEST:-0}"
[[ "$fixed_entity" == 0 || "$fixed_entity" == 1 ]] || exit 2
if [[ "$fixed_entity" == 1 ]]; then
  [[ "$carpet_profile" == 1 && "$profile_mobcap" == 70 && -f fixed_entity_fixture.sh ]] || exit 2
fi
native_route="${KAPISMA_NATIVE_ROUTE:-0}"
[[ "$native_route" == 0 || "$native_route" == 1 ]] || exit 2
native_mode="${KAPISMA_NATIVE_TEST:-0}"
if [[ "$native_route" == 1 ]]; then [[ "$native_mode" == 1 && -f native_route_fixture.sh ]] || exit 2; fi
[[ "$native_mode" == 0 || "$native_mode" == 1 ]] || exit 2
if [[ "$native_mode" == 1 ]]; then
  [[ "$carpet_profile" == 0 && "$route_mode" == 0 && "$proof" == 0 && "$thread_probe" == 0 && "$fixed_entity" == 0 && "$profile_mobcap" == 70 && "$map_mode" == 1 && "$spawn_mode" == 1 && "$chunk_mode" == 1 && -f native_client_fixture.sh ]] || exit 2
fi
preload_mode="${KAPISMA_TEST_PRELOAD:-0}"
[[ "$preload_mode" == 0 || "$preload_mode" == 1 ]] || exit 2
if [[ "$preload_mode" == 1 ]]; then
  [[ "$route_mode" == 1 && "$thread_probe" == 0 ]] || exit 2
fi
reply_limit=15
if [[ "$proof" == 1 ]]; then reply_limit=45; fi
printf 'bg_threads=%s\njfr=%s\nmap_mode=%s\nspawn_mode=%s\nchunk_mode=%s\nandroid_jna=%s\ntiming=%s\nfixed_heap_mib=%s\nmap_parity_proof=%s\n' "${bg_threads:-default}" "${KAPISMA_JFR:-0}" "$map_mode" "$spawn_mode" "$chunk_mode" "$jna_mode" "$timing" "${KAPISMA_FIXED_HEAP_MIB:-dynamic}" "$proof" > experiment.txt
printf 'route_mode=%s\n' "$route_mode" >> experiment.txt
if [[ "$xray_empty_skip" == 1 ]]; then printf 'xray_empty_skip=1\n' >> experiment.txt; fi
if [[ "$thread_probe" == 1 ]]; then printf 'thread_probe=1\n' >> experiment.txt; fi
if [[ "$preload_mode" == 1 ]]; then printf 'preload_mode=1\n' >> experiment.txt; fi
if [[ "$carpet_profile" == 1 ]]; then printf 'carpet_profile=1\nprofile_mobcap=%s\n' "$profile_mobcap" >> experiment.txt; fi
if [[ "$route_mode" == 1 || "$thread_probe" == 1 || "$carpet_profile" == 1 || "$native_mode" == 1 ]]; then
  # Capture actual input files, not launcher profile upper bounds.
  props=$(tr -d '\r' < server.properties)
  property(){
    local key="$1"
    printf '%s\n' "$props" | awk -F= -v key="$key" '$1==key {n++; v=substr($0,length(key)+2)} END{if(n==1)print v;else exit 1}'
  }
  view=$(property view-distance); simulation=$(property simulation-distance)
  [[ "$view" =~ ^[34]$ && "$simulation" == 3 && "$(property max-players)" == 4 &&
     "$(property server-ip)" == 127.0.0.1 && "$(property server-port)" == 25577 ]] || { echo 'invalid route fixture properties'; exit 2; }
  if [[ "$fixed_entity" == 1 ]]; then
    [[ ( "$(property level-type)" == 'minecraft\:flat' || "$(property level-type)" == 'minecraft:flat' ) && "$(property generate-structures)" == false && "$(property level-seed)" == 20261126 && "$(property online-mode)" == false ]] || { echo 'invalid fixed entity fixture properties'; exit 2; }
    printf 'fixed_entity=1\nfixture_world_seed=20261126\nfixture_level_type=flat\nfixture_sha256=%s\n' "$(sha256sum fixed_entity_fixture.sh | awk '{print $1}')" >> experiment.txt
  fi
  if [[ "$native_mode" == 1 ]]; then
    [[ "$(property online-mode)" == false && ( "$(property level-type)" == 'minecraft\:normal' || "$(property level-type)" == 'minecraft:normal' ) ]] || exit 2
    printf 'native_mode=1\nnative_fixture_sha256=%s\n' "$(sha256sum native_client_fixture.sh | awk '{print $1}')" >> experiment.txt
    if [[ "$native_route" == 1 ]]; then printf 'native_route=1\nnative_route_sha256=%s\n' "$(sha256sum native_route_fixture.sh | awk '{print $1}')" >> experiment.txt; fi
  fi
  candidate=$(sha256sum mods/kapisma-0.1.0.jar | awk '{print $1}')
  config_hash=$(printf '%s\n' "$props" | grep -v '^view-distance=' | sha256sum | awk '{print $1}')
  mod_hash=$(sha256sum mods/*.jar | LC_ALL=C sort | sha256sum | awk '{print $1}')
  script_hash=$(sha256sum "$PWD/run_android_stress.sh" | awk '{print $1}')
  printf 'candidate_sha256=%s\nconfig_except_view_sha256=%s\nmodset_sha256=%s\nscript_sha256=%s\nview=%s\nsimulation=%s\nroute_version=1\narena_seed=5743893257777525637\narena_center=16,-48\n' "$candidate" "$config_hash" "$mod_hash" "$script_hash" "$view" "$simulation" >> experiment.txt
fi
adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push experiment.txt "$stage/" >/dev/null
# Thermal checks run before launch and during the scenario, including boot.
# A failed check exits through finish(), which requests normal server stop.
[[ -f "$PWD/android_thermal_guard.sh" ]] || { echo 'thermal helper missing'; exit 2; }
printf 'timestamp\tphase\tstatus\n' > "$thermal_log"
thermal_check(){
  local phase="$1" out rc=0
  out=$(bash "$PWD/android_thermal_guard.sh" --device "$KAPISMA_CLEANUP_ADB_SERIAL") || rc=$?
  local status="${out#thermal_status=}"
  [[ "$status" =~ ^[0-6]$ ]] || { status=UNKNOWN; rc=6; }
  printf '%s\t%s\t%s\n' "$(date +%s)" "$phase" "$status" >> "$thermal_log"
  if ((rc != 0 || status >= 3)); then abort_reason="thermal_guard_${phase}_${status}"; exit 6; fi
}
thermal_check prelaunch
[[ -f "$PWD/android_uptime_ms.sh" ]] || { abort_reason=monotonic_helper_missing; exit 2; }
clock_first=$(bash "$PWD/android_uptime_ms.sh" "$KAPISMA_CLEANUP_ADB_SERIAL") || { abort_reason=monotonic_clock_unavailable; exit 2; }
sleep 1
clock_second=$(bash "$PWD/android_uptime_ms.sh" "$KAPISMA_CLEANUP_ADB_SERIAL") || { abort_reason=monotonic_clock_unavailable; exit 2; }
[[ "$clock_first" =~ ^[0-9]+$ && "$clock_second" =~ ^[0-9]+$ ]] && (( clock_second>clock_first )) || { abort_reason=monotonic_clock_invalid; exit 2; }
# Use the launcher shipped with this isolated test directory, not a mutable global script.
[[ -f "$PWD/ultra_termux.sh" ]] || { echo 'isolated launcher missing'; exit 2; }
bash "$PWD/ultra_termux.sh" --launch "$PWD" <&8 > "$log" 2>&1 &
pid=$!
for i in $(seq 1 180); do
  if grep -Fq 'Done (' "$log"; then break; fi
  kill -0 "$pid" 2>/dev/null || { echo 'boot failed'; exit 3; }
  if (( i % 5 == 0 )); then thermal_check boot; fi
  sleep 2
done
grep -Fq 'Done (' "$log" || { echo 'boot timeout'; exit 3; }
if [[ "$jna_mode" == 1 ]] && ! grep -Fq 'android_jna=5.14.0 verified' "$log"; then
  printf 'HARNESS_ABORT reason=jna_mode_not_verified\n' | tee -a "$log" >&2
  exit 7
fi
say(){ printf '%s\n' "$*" >&8; }
assert_alive(){
  kill -0 "$pid" 2>/dev/null || { echo 'launcher stopped'; exit 4; }
  if grep -Eq 'Segmentation fault|OutOfMemoryError|A fatal error has been detected' "$log"; then
    echo 'JVM fatal error observed'; exit 4
  fi
}
uptime_ms(){ bash "$PWD/android_uptime_ms.sh" "$KAPISMA_CLEANUP_ADB_SERIAL"; }
sample(){
  local phase offset response avail rss temp i sent now
  assert_alive
  phase=$1; thermal_check "$phase"; offset=$(wc -c < "$log")
  sent=$(uptime_ms) || { abort_reason=monotonic_clock_unavailable; exit 2; }
  say 'tick query'
  response=''
  for i in $(seq 1 "$reply_limit"); do
    sleep 1
    assert_alive
    if (( i % 5 == 0 )); then thermal_check "$phase"; fi
    response=$(tail -c +$((offset+1)) "$log" | grep -E 'Average time per tick:|P50:|TPS:' | tail -3 | tr '\n\t' '  ' || true)
    if [[ "$response" == *"P95:"* ]]; then break; fi
    if [[ "$thread_probe" == 1 && ( "$phase" == route_first || "$phase" == exploration ) && "$i" == 5 && "$probe_count" -lt 2 ]]; then
      probe_count=$((probe_count+1))
      bash "$PWD/android_thread_probe.sh" "$pid" "$phase" "$probe_count" || true
    fi
    now=$(uptime_ms) || { abort_reason=monotonic_clock_unavailable; exit 2; }
    if (( now-sent >= reply_limit*1000 )); then break; fi
  done
  now=$(uptime_ms) || { abort_reason=monotonic_clock_unavailable; exit 2; }
  if [[ "$response" != *"P95:"* ]] || (( now-sent > reply_limit*1000 )); then
    printf '%s\t%s\t%s\ttimeout\n' "$phase" "$sent" "$((now-sent))" >> query-latency.tsv
    abort_reason="tick_response_timeout phase=$phase limit_seconds=$reply_limit"
    echo "$abort_reason" >&2
    exit 7
  fi
  printf '%s\t%s\t%s\tok\n' "$phase" "$sent" "$((now-sent))" >> query-latency.tsv
  avail=$(awk '/^MemAvailable:/{print $2}' /proc/meminfo)
  rss=$(adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" shell ps -A -o PID,RSS,NAME | tr -d '\r' | awk '$3=="java"{n++;rss=$2} END{if(n==1)print rss}' || true)
  temp=$(adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" shell dumpsys battery | awk '/temperature:/{print $2; exit}' | tr -d '\r')
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$phase" "$(date +%s)" "$avail" "${rss:-UNKNOWN}" "${temp:-UNKNOWN}" "$response" >> "$metrics"
  tail -1 "$metrics"
  [[ "$temp" =~ ^[0-9]+$ ]] && (( temp < 460 )) || exit 6
  (( avail >= 524288 )) || exit 6
  kill -0 "$pid" 2>/dev/null || exit 4
  # High MSPT can coexist with a quick command reply. In diagnostic mode only,
  # take an additional bounded snapshot while bots are still moving.
  if [[ "$thread_probe" == 1 && "$phase" == exploration && "$probe_count" -lt 2 ]] &&
     printf '%s\n' "$response" | awk 'match($0,/Average time per tick: [0-9.]+ms/){v=substr($0,RSTART,RLENGTH);sub(/^Average time per tick: /,"",v);sub(/ms$/,"",v);if(v+0>=65)ok=1}END{exit !ok}'; then
    probe_count=$((probe_count+1))
    bash "$PWD/android_thread_probe.sh" "$pid" "$phase" "$probe_count" || true
  fi
  adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push "$log" "$metrics" "$stage/" >/dev/null 2>&1 || true
}
# Allow the tick statistics ring to warm up after the Done marker.
sleep 6
sample idle
if [[ "$native_mode" == 1 ]]; then
  source "$PWD/native_client_fixture.sh"
  native_client_fixture
  exit 0
fi
for i in 1 2 3 4; do say "player UltraBot$i spawn"; sleep 2; done
say list
sample lobby
if [[ "$fixed_entity" == 1 ]]; then
  source "$PWD/fixed_entity_fixture.sh"
  fixed_entity_fixture
  exit 0
fi
say 'kapisma game UHC'; sleep 2
say 'kapisma origins off'
say 'kapisma rule UHC pvpGraceMin 0'
say 'kapisma rule UHC treasureChests 4'
say 'kapisma rule UHC minimap 1'
if [[ "$profile_mobcap" == 35 ]]; then
  # Isolated diagnostic only; affects all natural spawn categories, NOT a live optimization.
  mobcap_changed=1
  say 'spawn mobcaps set 35'
  for poll in $(seq 1 15); do
    assert_alive; sleep 1
    if grep -Fq 'Mobcaps for hostile mobs changed to 35, other groups will follow' "$log"; then break; fi
  done
  grep -Fq 'Mobcaps for hostile mobs changed to 35, other groups will follow' "$log" || { abort_reason=mobcap_not_verified; exit 7; }
fi
sleep 2
say 'debug start'
say 'kapisma start'
# Never label world preparation as exploration. Require explicit start evidence.
for i in $(seq 1 35); do
  sample spawn
  if grep -Fq 'UHC başladı!' "$log"; then break; fi
  sleep 5
done
grep -Fq 'UHC başladı!' "$log" || { echo 'UHC never started'; exit 7; }
if [[ "$spawn_mode" == 1 ]] &&
   { ! grep -Fq 'ULTRA_SPAWN_MODE incremental=true' "$log" ||
     ! grep -Fq 'ULTRA_SPAWN_COMPLETE teams=' "$log"; }; then
  printf 'HARNESS_ABORT reason=spawn_mode_not_verified\n' | tee -a "$log" >&2
  exit 7
fi
if [[ "$chunk_mode" == 1 ]] && ! grep -Eq 'ULTRA_CHUNK_ADMISSION cap=[1-4] pending_spawn=' "$log"; then
  printf 'HARNESS_ABORT reason=chunk_mode_not_verified\n' | tee -a "$log" >&2
  exit 7
fi
if [[ "$map_mode" == 1 ]]; then
  if ! grep -Fq 'ULTRA_MAP_MODE incremental=true' "$log" ||
     ! grep -Fq 'ULTRA_MAP_COMPLETE kind=treasure' "$log"; then
    printf 'HARNESS_ABORT reason=map_mode_not_verified\n' | tee -a "$log" >&2
    exit 7
  fi
fi
say 'debug stop'
sleep 2
# Correctness-only run: do not report vanilla shadow rendering as performance.
if [[ "$proof" == 1 ]]; then
  treasure_matches=$(grep -Fc 'ULTRA_MAP_PARITY pixels=16384 scale=2 matched=true' "$log" || true)
  region_matches=$(grep -Fc 'ULTRA_MAP_PARITY pixels=16384 scale=4 matched=true' "$log" || true)
  if [[ "$treasure_matches" != 4 || "$region_matches" != 1 ]] ||
     ! grep -Fq 'ULTRA_MAP_COMPLETE kind=treasure index=4' "$log" ||
     ! grep -Fq 'ULTRA_MAP_COMPLETE kind=region' "$log"; then
    printf 'HARNESS_ABORT reason=parity_evidence_incomplete\n' | tee -a "$log" >&2
    exit 7
  fi
  parity_complete=1
  exit 0
fi
# Separate synthetic chunk-demand test. Only the isolated test world is used.
# Fixed cardinal routes around the known arena center; no human or PvP claim.
if [[ "$route_mode" == 1 ]]; then
  grep -Fq 'seed=5743893257777525637 merkez=(16,-48)' "$log" || { abort_reason=route_seed_mismatch; exit 7; }
  for i in 1 2 3 4; do
    say "player ultrabot$i stop"
    say "effect give ultrabot$i minecraft:slow_falling 600 0 true"
  done
  # Diagnostic only: vanilla forceload synchronously generates the target chunk.
  # Measure preparation cost separately; never use this as a production prefetch.
  if [[ "$preload_mode" == 1 ]]; then
    printf 'target\tx\tz\tprepare_ms\tresult\n' > preload.tsv
    for j in 0 1 2 3; do
      thermal_check preload
      x="${preload_xs[j]}"; z="${preload_zs[j]}"
      sent=$(uptime_ms) || { abort_reason=preload_clock; exit 2; }
      preload_requested=$((j+1))
      # Obtain the actual owned Fantasy dimension from the first force-load acknowledgement.
      if [[ "$j" == 0 ]]; then say "execute at ultrabot1 run forceload add $x $z"
      else say "execute in $preload_dimension run forceload add $x $z"; fi
      say "say KAPISMA_PRELOAD_ADD_BARRIER target=$j"
      ready=0
      attempt=0
      outstanding=0
      for poll in $(seq 1 45); do
        assert_alive
        if (( poll % 5 == 0 )); then thermal_check preload; fi
        sleep 1
        now=$(uptime_ms) || { abort_reason=preload_clock; exit 2; }
        (( now-sent < 45000 )) || break
        if ! grep -Fq "[Server] KAPISMA_PRELOAD_ADD_BARRIER target=$j" "$log"; then continue; fi
        if [[ "$j" == 0 && -z "$preload_dimension" ]]; then
          preload_dimension=$(sed -nE 's/.*Marked chunk \[[^]]+\] in (fantasy:[a-z0-9_/-]+) to be force loaded.*/\1/p' "$log" | tail -1)
          [[ "$preload_dimension" =~ ^fantasy:[a-z0-9_/-]+$ ]] || { abort_reason=preload_dimension_unverified; exit 7; }
        fi
        if grep -Fq "[Server] KAPISMA_PRELOAD_READY target=$j x=$x z=$z" "$log"; then ready=1; break; fi
        # Never enqueue another readiness probe until the previous one has executed.
        if (( outstanding == 1 )); then
          if ! grep -Fq "[Server] KAPISMA_PRELOAD_POLL target=$j attempt=$attempt" "$log"; then continue; fi
          outstanding=0
        fi
        attempt=$((attempt+1))
        say "execute in $preload_dimension if loaded $x 180 $z run say KAPISMA_PRELOAD_READY target=$j x=$x z=$z"
        say "say KAPISMA_PRELOAD_POLL target=$j attempt=$attempt"
        outstanding=1
      done
      now=$(uptime_ms) || { abort_reason=preload_clock; exit 2; }
      if (( ready != 1 )); then
        printf '%s\t%s\t%s\t%s\ttimeout\n' "$j" "$x" "$z" "$((now-sent))" >> preload.tsv
        abort_reason="preload_target_timeout_$j"; exit 7
      fi
      printf '%s\t%s\t%s\t%s\tready\n' "$j" "$x" "$z" "$((now-sent))" >> preload.tsv
    done
    # Readiness checks again immediately before teleport: all four must hold.
    say "execute in $preload_dimension if loaded 16 180 -136 if loaded 104 180 -48 if loaded 16 180 40 if loaded -72 180 -48 run say KAPISMA_PRELOAD_ALL_READY"
    sleep 2
    grep -Fq '[Server] KAPISMA_PRELOAD_ALL_READY' "$log" || { abort_reason=preload_not_all_ready; exit 7; }
  fi
  for pass in first revisit; do
    for step in $(seq 1 12); do
      radius=$((64+step*24))
      say "say KAPISMA_ROUTE_STEP pass=$pass step=$step"
      # Explicit decimals prevent Minecraft's integer X/Z centering (+0.5).
      say "tp ultrabot1 16.0 180.0 $((-48-radius)).0"
      say "tp ultrabot2 $((16+radius)).0 180.0 -48.0"
      say "tp ultrabot3 16.0 180.0 $((-48+radius)).0"
      say "tp ultrabot4 $((16-radius)).0 180.0 -48.0"
      for i in 1 2 3 4; do
        say "data get entity ultrabot$i Pos"
        say "data get entity ultrabot$i playerGameType"
      done
      sleep 5
      sample "route_$pass"
      if [[ "$preload_mode" == 1 ]]; then
        for j in 0 1 2 3; do
          say "execute in $preload_dimension run forceload remove ${preload_xs[j]} ${preload_zs[j]}"
        done
        say "execute in $preload_dimension run forceload query"
        for poll in $(seq 1 15); do
          assert_alive
          sleep 1
          if grep -Fq "No force loaded chunks were found in $preload_dimension" "$log"; then preload_released=1; break; fi
        done
        [[ "$preload_released" == 1 ]] || { abort_reason=preload_release_unverified; exit 7; }
        preload_complete=1
        exit 0
      fi
      if [[ "$thread_probe" == 1 && "$pass" == first && "$step" == 2 ]]; then
        # Diagnostic run, never a complete route comparison.
        exit 0
      fi
    done
  done
  route_complete=1
  exit 0
fi
# Save vanilla diagnostic report independently of JFR support.
if [[ -d debug ]]; then adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push debug "$stage/" >/dev/null || true; fi
# Named cardinal directions avoid Carpet version-specific pitch/yaw ordering.
dirs=(north east south west)
for i in 1 2 3 4; do say "player ultrabot$i look ${dirs[i-1]}"; say "player ultrabot$i sprint"; say "player ultrabot$i move forward"; say "player ultrabot$i jump continuous"; done
positions(){
  local i
  for i in 1 2 3 4; do
    say "data get entity ultrabot$i Pos"
    say "data get entity ultrabot$i Rotation"
    say "data get entity ultrabot$i playerGameType"
  done
}
positions
if [[ "$carpet_profile" == 1 ]]; then
  # Two sequential 100-tick profiles, never overlap or mix with JFR/jcmd.
  # Report completion must be observed, not inferred from a sleep.
  say 'execute at ultrabot1 run spawn mobcaps'
  say 'kapisma debug xray status'
  say 'kapisma debug guard status'
  for kind in health entities; do
    thermal_check "profile_$kind"
    offset=$(wc -c < "$log")
    say "say KAPISMA_PROFILE_BEGIN kind=$kind ticks=100"
    say "profile $kind 100"
    marker='The Rest, whatever that might be:'
    if [[ "$kind" == entities ]]; then marker='Top 10 CPU hogs:'; fi
    ready=0
    sent=$(uptime_ms) || { abort_reason=profile_clock; exit 2; }
    for poll in $(seq 1 60); do
      assert_alive
      if (( poll % 5 == 0 )); then thermal_check "profile_$kind"; fi
      sleep 1
      now=$(uptime_ms) || { abort_reason=profile_clock; exit 2; }
      (( now-sent <= 60000 )) || break
      if tail -c +$((offset+1)) "$log" | grep -F "$marker" >/dev/null; then ready=1; break; fi
    done
    [[ "$ready" == 1 ]] || { abort_reason="carpet_profile_${kind}_timeout"; exit 7; }
    say "say KAPISMA_PROFILE_END kind=$kind"
    sleep 2
    sample exploration
    positions
  done
  for i in 1 2 3 4; do say "player ultrabot$i stop"; done
  say 'execute at ultrabot1 run spawn mobcaps'
  say 'kapisma debug xray status'
  say 'kapisma debug guard status'
  if [[ "$mobcap_changed" == 1 ]]; then
    say 'spawn mobcaps set 70'
    for poll in $(seq 1 15); do
      assert_alive; sleep 1
      if grep -Fq 'Mobcaps for hostile mobs changed to 70, other groups will follow' "$log"; then mobcap_changed=0; break; fi
    done
    [[ "$mobcap_changed" == 0 ]] || { abort_reason=mobcap_restore_unverified; exit 7; }
  fi
  say 'say KAPISMA_MOVEMENT_END'
  for poll in $(seq 1 15); do
    assert_alive; sleep 1
    if grep -Fq '[Server] KAPISMA_MOVEMENT_END' "$log"; then carpet_profile_complete=1; break; fi
  done
  [[ "$carpet_profile_complete" == 1 ]] || { abort_reason=profile_end_unverified; exit 7; }
  exit 0
fi
for i in $(seq 1 12); do
  sample exploration
  if (( i % 3 == 0 )); then positions; fi
  sleep 5
done
positions
for i in 1 2 3 4; do say "player ultrabot$i stop"; done
if [[ "$thread_probe" == 1 ]]; then
  say 'say KAPISMA_MOVEMENT_END'
  for poll in $(seq 1 15); do
    assert_alive
    sleep 1
    if grep -Fq '[Server] KAPISMA_MOVEMENT_END' "$log"; then movement_probe_complete=1; break; fi
  done
  [[ "$movement_probe_complete" == 1 ]] || { abort_reason=movement_end_unverified; exit 7; }
  exit 0
fi
for i in 2 3 4; do say "tp ultrabot$i ultrabot1"; done
for i in 1 2 3 4; do say "player ultrabot$i attack continuous"; done
positions
for i in $(seq 1 4); do sample pvp; sleep 5; done
say list
say 'kapisma status'
scenario_complete=1
echo "scenario commands completed"
