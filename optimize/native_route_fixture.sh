# Controlled native movement lane, ONLY in the newly created isolated UHC arena.
# Setup teleports are explicitly outside the measured walk window. No later teleports.
native_route_setup(){
  local n z i x off deadline current mark dimension ready
  [[ "$native_route" == 1 && "$run_id" =~ ^kapisma-ultra-test-[0-9]{8}-r[0-9]+$ && "$PWD" == "$HOME/$run_id" ]] || exit 2
  route_barrier(){
    local mark="$1" start now i
    say "say $mark"
    start=$(uptime_ms)
    for i in $(seq 1 90); do
      assert_alive; host_abort
      if ((i%5==0)); then thermal_check native_route_setup; fi
      if grep -Fq "[Server] $mark" "$log"; then return 0; fi
      now=$(uptime_ms); ((now-start<90000)) || break
      sleep 1
    done
    abort_reason="route_barrier_$mark"; exit 7
  }
  native_positions
  route_barrier KAPISMA_NATIVE_ROUTE_DIMENSION
  dimension=$(grep -E 'KodNativeQA1 has the following entity data: "fantasy:[a-z0-9_/-]+"$' "$log" | tail -1 | sed -E 's/.*"(fantasy:[a-z0-9_/-]+)"/\1/')
  [[ "$dimension" =~ ^fantasy:[a-z0-9_/-]+$ ]] || { abort_reason=route_dimension; exit 7; }
  for n in 1 2 3 4; do
    grep -Fq "KodNativeQA$n has the following entity data: \"$dimension\"" "$log" || { abort_reason=route_player_dimension; exit 7; }
  done
  # A 224x48 block sky platform: 14x3 chunks, 42 explicit temporary tickets.
  # No changes to natural spawning, protection rules, simulation or actual production world.
  say "execute in $dimension run forceload query"
  route_barrier KAPISMA_NATIVE_ROUTE_EMPTY
  grep -Fq "No force loaded chunks were found in $dimension" "$log" || { abort_reason=route_existing_tickets; exit 7; }
  native_route_dimension="$dimension"
  native_route_ticket=1
  say "execute in $dimension run forceload add -64 -64 159 -17"
  route_barrier KAPISMA_NATIVE_ROUTE_TICKETS
  # Wait for every chunk, not just corners.
  ready=0
  for i in $(seq 1 20); do
    off=$(wc -c < "$log")
    for ((x=-64;x<160;x+=16)); do
      for z in -64 -48 -32; do
        say "execute in $dimension if loaded $((x+8)) 240 $((z+8)) run say KAPISMA_ROUTE_LOADED_${x}_${z}"
      done
    done
    route_barrier "KAPISMA_ROUTE_LOAD_POLL_$i"
    if [[ $(tail -c +$((off+1)) "$log" | grep -Fc '[Server] KAPISMA_ROUTE_LOADED_' || true) == 42 ]]; then ready=1; break; fi
    sleep 2
  done
  [[ "$ready" == 1 ]] || { abort_reason=route_chunks_unready; exit 7; }
  # All four walk east; separation 12 blocks, non-team guard can evaluate.
  # Solid floor, clear three-block headroom, no mob or AI modifications.
  say "execute in $dimension run fill -64 239 -64 159 239 -17 minecraft:stone"
  say "execute in $dimension run fill -64 240 -64 159 242 -17 minecraft:air"
  # Two long opaque separating walls, leaving other pairs visible.
  for z in -51 -27; do
    say "execute in $dimension run fill -64 240 $z 159 243 $z minecraft:stone"
  done
  say "execute in $dimension run fill 159 240 -64 159 243 -17 minecraft:stone"
  route_barrier KAPISMA_NATIVE_ROUTE_BUILT
  # Full geometry check by vanilla blocks comparison to an independently filled
  # reference segment is unnecessary; command result plus all per-chunk lane
  # samples are required, and the movement evaluator still rejects stalls.
  for ((x=-64;x<160;x+=16)); do
    for z in -57 -45 -33 -21; do
      say "execute in $dimension if block $((x+8)) 239 $z minecraft:stone if block $((x+8)) 240 $z minecraft:air if block $((x+8)) 241 $z minecraft:air run say KAPISMA_ROUTE_LANE_${x}_${z}"
    done
  done
  route_barrier KAPISMA_NATIVE_ROUTE_VERIFIED
  [[ $(grep -Fc '[Server] KAPISMA_ROUTE_LANE_' "$log" || true) == 56 ]] || { abort_reason=route_geometry; exit 7; }
  for n in 1 2 3 4; do
    z=$((-57+(n-1)*12))
    say "execute in $dimension run tp KodNativeQA$n -59.5 240 $z.5 -90 0"
  done
  route_barrier KAPISMA_NATIVE_ROUTE_PLACED
  # Negative z literal .5 is inside previous block; verify exact intended position.
  for n in 1 2 3 4; do
    z=$((-57+(n-1)*12))
    say "execute in $dimension positioned -59.5 240 $z.5 if entity @a[name=KodNativeQA$n,gamemode=survival,distance=..1] run say KAPISMA_ROUTE_PLAYER_$n"
  done
  route_barrier KAPISMA_NATIVE_ROUTE_PLAYERS
  for n in 1 2 3 4; do
    grep -Fq "[Server] KAPISMA_ROUTE_PLAYER_$n" "$log" || { abort_reason=route_player_setup; exit 7; }
  done
  # Release all setup tickets before measuring, allowing only normal player tickets.
  say "execute in $dimension run forceload remove -64 -64 159 -17"
  say "execute in $dimension run forceload query"
  route_barrier KAPISMA_NATIVE_ROUTE_RELEASED
  [[ $(grep -Fc "No force loaded chunks were found in $dimension" "$log" || true) == 2 ]] || { abort_reason=route_ticket_release; exit 7; }
  native_route_ticket=0
  # The Java process owns this log while running. Use its console channel,
  # not an independent append that may be overwritten by the JVM file offset.
  route_barrier "HARNESS_NATIVE_ROUTE_SETUP dimension=$dimension lanes=4 chunks=42 setup_teleports=4 tickets_released=true"
}
