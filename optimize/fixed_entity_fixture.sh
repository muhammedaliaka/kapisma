# Sourced ONLY by the isolated Android diagnostic harness, after preflight.
# Fixed 4x16 mobs, closed flat-world chambers, four stationary creative bots.
# Not UHC survival, combat, worldgen parity or human acceptance.
fixed_entity_fixture(){
  local i j k x z species marker sent now offset ready poll phase
  local -a mobs=(zombie skeleton creeper bat)
  local -a bx=(0 16 0 16) bz=(-64 -64 -48 -48)
  fixture_wait(){
    local mark="$1" limit="$2" start current
    start=$(uptime_ms) || { abort_reason=fixture_clock; exit 2; }
    for ((poll=0;poll<limit;poll++)); do
      assert_alive
      if ((poll%5==0)); then thermal_check fixture_setup; fi
      if grep -Fq "[Server] $mark" "$log"; then return 0; fi
      current=$(uptime_ms) || { abort_reason=fixture_clock; exit 2; }
      ((current-start<limit*1000)) || break
      sleep 1
    done
    abort_reason="fixture_missing_marker_$mark"; exit 7
  }
  # Verify scope before touching the test world.
  [[ "$fixed_entity" == 1 && "$run_id" =~ ^kapisma-ultra-test-[0-9]{8}-r[0-9]+$ && "$PWD" == "$HOME/$run_id" ]] || exit 2
  say 'execute in minecraft:overworld run gamerule doMobSpawning false'
  say 'execute in minecraft:overworld run gamerule doDaylightCycle false'
  say 'execute in minecraft:overworld run gamerule doWeatherCycle false'
  say 'execute in minecraft:overworld run gamerule randomTickSpeed 0'
  say 'execute in minecraft:overworld run time set midnight'
  say 'execute in minecraft:overworld run weather clear'
  say 'difficulty normal'
  say 'execute in minecraft:overworld run forceload query'
  say 'say KAPISMA_FIXTURE_EMPTY_TICKETS'
  fixture_wait KAPISMA_FIXTURE_EMPTY_TICKETS 30
  grep -Fq 'No force loaded chunks were found in minecraft:overworld' "$log" || { abort_reason=fixture_existing_tickets; exit 7; }
  fixed_ticket_requested=1
  say 'execute in minecraft:overworld run forceload add 0 -64 31 -33'
  say 'say KAPISMA_FIXTURE_TICKET_BARRIER'
  fixture_wait KAPISMA_FIXTURE_TICKET_BARRIER 60
  # One outstanding poll; do not pile up commands if generation stalls.
  ready=0
  for i in $(seq 1 20); do
    say "execute in minecraft:overworld if loaded 8 100 -56 if loaded 24 100 -56 if loaded 8 100 -40 if loaded 24 100 -40 run say KAPISMA_FIXTURE_LOADED"
    say "say KAPISMA_FIXTURE_LOAD_POLL_$i"
    fixture_wait "KAPISMA_FIXTURE_LOAD_POLL_$i" 20
    if grep -Fq '[Server] KAPISMA_FIXTURE_LOADED' "$log"; then ready=1; break; fi
    sleep 1
  done
  [[ "$ready" == 1 ]] || { abort_reason=fixture_chunks_not_ready; exit 7; }
  # Four closed chambers, solid floor/roof, no AI disable, no natural spawning.
  for i in 0 1 2 3; do
    x="${bx[i]}"; z="${bz[i]}"
    say "execute in minecraft:overworld run fill $x 99 $z $((x+15)) 107 $((z+15)) minecraft:stone hollow"
    say "execute in minecraft:overworld if block $x 99 $z minecraft:stone if block $((x+15)) 107 $((z+15)) minecraft:stone if block $((x+8)) 100 $((z+8)) minecraft:air run say KAPISMA_FIXTURE_ROOM_$i"
    fixture_wait "KAPISMA_FIXTURE_ROOM_$i" 20
    # Keep creative observers physically separate from mobile entities.
    say "execute in minecraft:overworld run fill $((x+7)) 102 $((z+7)) $((x+9)) 105 $((z+9)) minecraft:glass hollow"
    say "execute in minecraft:overworld if block $((x+8)) 102 $((z+8)) minecraft:glass if block $((x+8)) 105 $((z+8)) minecraft:glass if block $((x+7)) 103 $((z+8)) minecraft:glass if block $((x+8)) 103 $((z+8)) minecraft:air if block $((x+8)) 104 $((z+8)) minecraft:air run say KAPISMA_FIXTURE_CAGE_$i"
    fixture_wait "KAPISMA_FIXTURE_CAGE_$i" 20
  done
  for i in 1 2 3 4; do
    x="${bx[i-1]}"; z="${bz[i-1]}"
    say "player ultrabot$i stop"
    say "gamemode creative ultrabot$i"
    # z is negative: -(n-0.5) is the center of block -n.
    say "execute in minecraft:overworld run tp ultrabot$i $((x+8)).5 103.0 $((z+9)).5"
  done
  say 'say KAPISMA_FIXTURE_PLAYERS_BARRIER'
  fixture_wait KAPISMA_FIXTURE_PLAYERS_BARRIER 30
  # Give player tickets and surrounding flat chunks time to settle before creating mobs.
  sleep 15
  for i in 0 1 2 3; do
    species="${mobs[i]}"; x="${bx[i]}"; z="${bz[i]}"
    for j in 3 6 9 12; do
      for k in 3 6 9 12; do
        say "execute in minecraft:overworld run summon minecraft:$species $((x+j)).5 100.0 $((z+k)).5 {PersistenceRequired:1b,Tags:[\"ultra_fixture\"]}"
      done
    done
    say "say KAPISMA_FIXTURE_SUMMON_$species"
    fixture_wait "KAPISMA_FIXTURE_SUMMON_$species" 30
  done
  say 'scoreboard objectives add ultraFixture dummy'
  fixture_counts(){
    local label="$1" m n bxx bzz
    for m in "${mobs[@]}"; do
      say "execute in minecraft:overworld positioned 16 103 -48 store result score ${m}_$label ultraFixture if entity @e[type=minecraft:$m,tag=ultra_fixture,distance=..48]"
      say "scoreboard players get ${m}_$label ultraFixture"
    done
    for n in 1 2 3 4; do
      bxx="${bx[n-1]}"; bzz="${bz[n-1]}"
      say "data get entity ultrabot$n Pos"
      say "data get entity ultrabot$n playerGameType"
      say "data get entity ultrabot$n Dimension"
      say "execute in minecraft:overworld positioned $((bxx+8)).5 103 $((bzz+9)).5 if entity @a[name=ultrabot$n,gamemode=creative,distance=..0.5] run say KAPISMA_FIXTURE_PLAYER_${label}_$n"
    done
    say "execute in minecraft:overworld positioned 16 103 -48 unless entity @e[type=!minecraft:player,tag=!ultra_fixture,distance=..48] run say KAPISMA_FIXTURE_NO_EXTRA_$label"
    say "say KAPISMA_FIXTURE_COUNTS_$label"
    fixture_wait "KAPISMA_FIXTURE_COUNTS_$label" 30
    for m in "${mobs[@]}"; do
      grep -Eq "${m}_$label has 16 (ultraFixture|\[ultraFixture\])\s*$" "$log" || { abort_reason="fixture_count_$m-$label"; exit 7; }
    done
    for n in 1 2 3 4; do
      grep -Fq "[Server] KAPISMA_FIXTURE_PLAYER_${label}_$n" "$log" || { abort_reason=fixture_player_moved; exit 7; }
    done
    grep -Fq "[Server] KAPISMA_FIXTURE_NO_EXTRA_$label" "$log" || { abort_reason=fixture_extra_entities; exit 7; }
  }
  sleep 10
  fixture_counts before
  for phase in health entities; do
    thermal_check "profile_$phase"
    offset=$(wc -c < "$log")
    say "say KAPISMA_PROFILE_BEGIN kind=$phase ticks=100"
    say "profile $phase 100"
    marker='The Rest, whatever that might be:'
    if [[ "$phase" == entities ]]; then marker='Top 10 CPU hogs:'; fi
    ready=0; sent=$(uptime_ms)
    for i in $(seq 1 60); do
      assert_alive
      if ((i%5==0)); then thermal_check "profile_$phase"; fi
      sleep 1
      now=$(uptime_ms)
      ((now-sent<=60000)) || break
      if tail -c +$((offset+1)) "$log" | grep -F "$marker" >/dev/null; then ready=1; break; fi
    done
    [[ "$ready" == 1 ]] || { abort_reason="fixture_profile_${phase}_timeout"; exit 7; }
    say "say KAPISMA_PROFILE_END kind=$phase"
    fixture_wait "KAPISMA_PROFILE_END kind=$phase" 20
    sample exploration
    fixture_counts "$phase"
  done
  say 'execute in minecraft:overworld run forceload remove 0 -64 31 -33'
  say 'execute in minecraft:overworld run forceload query'
  say 'say KAPISMA_FIXTURE_TICKETS_RELEASED'
  fixture_wait KAPISMA_FIXTURE_TICKETS_RELEASED 30
  # Require empty query AFTER the removal, not the preflight empty query.
  [[ $(grep -Fc 'No force loaded chunks were found in minecraft:overworld' "$log" || true) == 2 ]] || { abort_reason=fixture_ticket_release_unverified; exit 7; }
  fixed_ticket_requested=0
  fixed_entity_complete=1
  carpet_profile_complete=1
}
