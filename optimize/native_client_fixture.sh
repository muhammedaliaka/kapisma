# Sourced only for the isolated Android native-client diagnostic.
# No synthetic Carpet players and no manufactured ACKs.
native_client_fixture(){
  local i p sent now ready n
  local -a names=(KodNativeQA1 KodNativeQA2 KodNativeQA3 KodNativeQA4)
  host_abort(){
    if adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" shell "test -f $stage/native.abort"; then abort_reason=native_host_abort; exit 7; fi
  }
  native_positions(){
    for p in "${names[@]}"; do
      say "data get entity $p Pos"
      say "data get entity $p playerGameType"
      say "data get entity $p Dimension"
    done
  }
  native_status(){
    say list
    say 'kapisma debug xray status'
    say 'kapisma debug guard status'
    native_positions
  }
  # Host verifies a real join acknowledgement before each additional launch.
  printf 'HARNESS_NATIVE_READY players=4\n' >> "$log"
  adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push "$log" "$stage/" >/dev/null
  ready=0; sent=$(uptime_ms)
  for i in $(seq 1 360); do
    assert_alive; host_abort
    if ((i%5==0)); then
      thermal_check native_join
      adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push "$log" "$stage/" >/dev/null
    fi
    if adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" shell "test -f $stage/native.joined"; then
      ready=1
      for p in "${names[@]}"; do
        grep -Fqi "$p joined the game" "$log" || ready=0
      done
      if ((ready)); then break; fi
    fi
    now=$(uptime_ms); ((now-sent<360000)) || break
    sleep 1
  done
  [[ "$ready" == 1 ]] || { abort_reason=native_join_timeout; exit 7; }
  say list
  sample lobby
  grep -Fq 'There are 4 of a max of 4 players online:' "$log" || { abort_reason=native_join_count; exit 7; }
  say 'kapisma game UHC'; sleep 2
  # Keep Origins installed but abilities off, same UHC rule fixture as R28.
  say 'kapisma origins off'
  if [[ "$native_route" == 1 ]]; then
    say 'kapisma team solo'
    say 'kapisma rule UHC antiCheat 1'
  fi
  say 'kapisma rule UHC pvpGraceMin 0'
  say 'kapisma rule UHC treasureChests 4'
  say 'kapisma rule UHC minimap 1'
  say 'debug start'
  say 'kapisma start'
  for i in $(seq 1 35); do
    host_abort; sample spawn
    if grep -Fq 'UHC başladı!' "$log"; then break; fi
    sleep 5
  done
  grep -Fq 'UHC başladı!' "$log" || { abort_reason=native_uhc_start_failed; exit 7; }
  [[ $(grep -Fc 'ULTRA_MAP_COMPLETE kind=treasure index=' "$log" || true) == 4 ]] &&
    grep -Fq 'ULTRA_MAP_COMPLETE kind=region' "$log" || { abort_reason=native_map_evidence; exit 7; }
  say 'debug stop'
  if [[ "$native_route" == 1 ]]; then
    source "$PWD/native_route_fixture.sh"
    native_route_setup
  fi
  native_status
  say 'say KAPISMA_NATIVE_WALK_READY'
  sent=$(uptime_ms); ready=0
  for i in $(seq 1 30); do
    assert_alive; host_abort
    if ((i%5==0)); then thermal_check native_walk_wait; fi
    adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" push "$log" "$stage/" >/dev/null
    if adb -s "$KAPISMA_CLEANUP_ADB_SERIAL" shell "test -f $stage/native.walk"; then ready=1; break; fi
    now=$(uptime_ms); ((now-sent<30000)) || break
    sleep 1
  done
  [[ "$ready" == 1 ]] || { abort_reason=native_walk_timeout; exit 7; }
  say 'say KAPISMA_NATIVE_WALK_BEGIN'
  for i in $(seq 1 12); do
    host_abort
    native_positions
    sample exploration
    sleep 5
  done
  native_status
  say 'say KAPISMA_NATIVE_WALK_END'
  sent=$(uptime_ms); ready=0
  for i in $(seq 1 15); do
    assert_alive
    if grep -Fq '[Server] KAPISMA_NATIVE_WALK_END' "$log"; then ready=1; break; fi
    sleep 1
  done
  [[ "$ready" == 1 ]] || { abort_reason=native_end_marker; exit 7; }
  if [[ "$native_route" == 1 ]]; then
    # Separate diagnostic windows, not part of the twelve exploration samples.
    local phase offset marker
    for phase in health entities; do
      thermal_check "native_profile_$phase"
      offset=$(wc -c < "$log")
      say "say KAPISMA_PROFILE_BEGIN kind=$phase ticks=100"
      say "profile $phase 100"
      marker='The Rest, whatever that might be:'
      if [[ "$phase" == entities ]]; then marker='Top 10 CPU hogs:'; fi
      ready=0; sent=$(uptime_ms)
      for i in $(seq 1 90); do
        assert_alive; host_abort
        if ((i%5==0)); then thermal_check "native_profile_$phase"; fi
        sleep 1
        now=$(uptime_ms); ((now-sent<=90000)) || break
        if tail -c +$((offset+1)) "$log" | grep -F "$marker" >/dev/null; then ready=1; break; fi
      done
      [[ "$ready" == 1 ]] || { abort_reason="native_profile_${phase}_timeout"; exit 7; }
      route_barrier "KAPISMA_PROFILE_END kind=$phase"
    done
    native_status
    route_barrier KAPISMA_NATIVE_PROFILE_COMPLETE
  fi
  native_complete=1
}
