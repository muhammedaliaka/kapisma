"""Survey a fixed seed through a real local client and validate the returned sites."""

import argparse
import math
import os
import re
import socket
import subprocess
import sys
import tempfile
import time
from functools import partial
from itertools import combinations
from pathlib import Path

from settings import LAB_PORT
from verify_formation import LAB, RTS, check, send, wait_for

SITE = re.compile(r"Kuruluş noktası · X (-?\d+) · Y (-?\d+) · Z (-?\d+) · (minecraft:\w+)"
                  r" · su (\d+) · odun (\d+) · taş (\d+) · yükseklik farkı (\d+)")


def verify(adapter, pack, *, match=False, diplomacy=False, full_roster=False, formation_jar=None, wood=False,
           hall_jar=None, manual_jar=None, outpost=False, district=False, iron=False, field=False, economy=False, bank=False,
           alliances=False, specialization=False):
    suffix = "-terrain-diplomacy" if diplomacy else "-terrain"
    if wood:
        suffix += "-wood"
    if manual_jar:
        suffix += "-manual"
    if outpost:
        suffix += "-outpost"
    if district:
        suffix += "-district"
    if iron:
        suffix += "-iron"
    if field:
        suffix += "-field"
    if economy:
        suffix += "-economy"
    if bank:
        suffix += "-bank"
    if alliances:
        suffix += "-alliances"
    if specialization:
        suffix += "-specialization"
    if hall_jar:
        suffix += "-hall"
    elif formation_jar and not manual_jar:
        suffix += "-orders"
    server_log, client_log = (LAB / f"{profile}{suffix}.out" for profile in ("server", "RtsQA1"))
    with socket.socket() as port:
        port.bind(("127.0.0.1", LAB_PORT))
    previous_log = server_log.stat().st_mtime_ns if server_log.is_file() else 0
    properties = LAB / "server/server.properties"
    original_properties = properties.read_bytes()
    environment = {**os.environ, "KRTS_SERVER_QA_ADAPTER": str(adapter), "KRTS_SERVER_QA_PACK": str(pack)}
    if formation_jar:
        environment.update(KRTS_FORMATION_QA_JAR=str(formation_jar), KRTS_FORMATION_QA_TERRAIN="true")
    if hall_jar:
        environment.update(KRTS_FORMATION_QA_JAR=str(hall_jar), KRTS_HALL_QA="true")
    if manual_jar:
        environment.update(KRTS_FORMATION_QA_JAR=str(manual_jar), KRTS_WOOD_QA="true")
    launch = [sys.executable, "-B", LAB / "start_single.py", "--terrain"]
    if diplomacy:
        launch.append("--diplomacy")
    if wood:
        launch.append("--wood")
    if hall_jar:
        launch.append("--hall")
    if manual_jar:
        launch.append("--manual")
    if outpost:
        launch.append("--outpost")
    if district:
        launch.append("--district")
    if iron:
        launch.append("--iron")
    if field:
        launch.append("--field")
    if economy:
        launch.append("--economy")
    if bank:
        launch.append("--bank")
    if alliances:
        launch.append("--alliances")
    if specialization:
        launch.append("--specialization")
    process = subprocess.Popen(launch, env=environment)

    def restart_bank():
        nonlocal process, server_log, client_log
        stop_terrain_process(process)
        server_log, client_log = (LAB / f"{profile}{suffix}-rejoin.out" for profile in ("server", "RtsQA1"))
        previous = server_log.stat().st_mtime_ns if server_log.is_file() else 0
        process = subprocess.Popen([*launch, "--bank-rejoin"], env=environment)
        wait_terrain_boot(server_log, process, previous)
        return partial(check, server_log, process), process, server_log, client_log

    try:
        wait_terrain_boot(server_log, process, previous_log)
        if match:
            send("function krts:reset")
            check(server_log, process, "if score #found_left krts_ctrl matches 0", "RTS_NATURAL_LOBBY_READY")
        send("tag RtsQA1 add krts.host", "scoreboard players set RtsQA1 krts_survey_cd 0", "function krts:start")
        wait_for(server_log, "Doğal maç için /trigger krts_found_start set 1 kullan." if match
                 else "Bu dünya kuruluş bölgelerini ölçmek için açık.", process)
        check(server_log, process, "if score #phase krts_ctrl matches 0", "RTS_TERRAIN_SCOUTING_ONLY")
        send("execute as RtsQA1 run trigger krts_menu")
        wait_for(client_log, "[Araziyi tara]", process)
        send("execute as RtsQA1 run trigger krts_survey set 1")
        wait_for(client_log, "Arazi taraması ·", process)
        send("say RTS_TERRAIN_RESULTS_COMPLETE")
        snapshot = wait_for(client_log, "RTS_TERRAIN_RESULTS_COMPLETE", process)
        summary = re.search(r"Arazi taraması · (\d+) kuruluş noktası\.", snapshot)
        sites = [
            ((int(x), int(y), int(z)), tuple(map(int, (water, wood, stone, relief))))
            for x, y, z, _, water, wood, stone, relief in SITE.findall(snapshot)
        ]
        assert summary and len(sites) == int(summary[1]) and sites, sites
        assert all(y > 0 for (_, y, _), _ in sites), sites
        assert any(wood > 0 and stone > 0 and relief > 0 for _, (_, wood, stone, relief) in sites), sites
        assert all(math.hypot(a[0] - b[0], a[2] - b[2]) >= 40
                   for (a, _), (b, _) in combinations(sites, 2)), sites
        print(f"PASS Gerçek istemci {len(sites)} ayrık doğal bölge aldı.", flush=True)
        send("execute as RtsQA1 run trigger krts_survey set 1")
        wait_for(client_log, "Yeniden taramak için", process)
        print("PASS Arazi taraması bekleme süresi doğrulandı.", flush=True)
        test = partial(check, server_log, process)
        for index, ((x, y, z), _) in enumerate(sites, 1):
            test(f"unless block {x} {y - 1} {z} air",
                  f"RTS_TERRAIN_FOOTING_{index}")
        send("player RtsThird spawn")
        wait_for(server_log, "rtsthird joined the game", process)
        send("execute as RtsQA1 run trigger krts_found_start set 1")
        wait_for(client_log, "Kuruluş başladı · 180 saniye.", process)
        wait_for(client_log, "[Köylüler] [Nether] [Yağmacılar] [Gece Halkı]", process)
        wait_for(client_log, "[Tarım] [Madencilik] [Ticaret] [Savunma] [Keşif]", process)
        check(server_log, process, "if score #found_left krts_ctrl matches 173..180", "RTS_FOUNDING_THREE_MINUTES")
        send("execute as rtsdummy run trigger krts_found set 3", "execute as RtsQA1 run trigger krts_found set 3")
        check(server_log, process, "if score rtsdummy krts_found_site matches 3 "
              "if score RtsQA1 krts_found_site matches 0", "RTS_FOUNDING_FIRST_VALID_COMMAND_WINS")
        send("execute as RtsQA1 run trigger krts_found set 1",
             "execute as RtsQA1 run trigger krts_found_origin set 4",
             "execute as RtsQA1 run trigger krts_found_profile set 5")
        check(server_log, process, "if score RtsQA1 krts_found_site matches 1 "
              "if score RtsQA1 krts_origin matches 4 if score RtsQA1 krts_profile matches 5",
              "RTS_FOUNDING_INDEPENDENT_CHOICES")
        send("execute as rtsdummy run trigger krts_found set 1",
             "execute as rtsdummy run trigger krts_found_origin set 4",
             "execute as rtsdummy run trigger krts_found_profile set 5")
        check(server_log, process, "if score rtsdummy krts_found matches 0 if score rtsdummy krts_found_site matches 3 "
              "if score rtsdummy krts_origin matches 4 if score rtsdummy krts_profile matches 5",
              "RTS_FOUNDING_RESERVATION_AND_SHARED_PROFILES")
        send("execute as RtsQA1 run trigger krts_found set 2")
        check(server_log, process, "if score RtsQA1 krts_found_site matches 2", "RTS_FOUNDING_CAN_MOVE_TO_EMPTY_REGION")
        send("execute as rtsdummy run trigger krts_found set 1")
        check(server_log, process, "if score rtsdummy krts_found_site matches 1", "RTS_FOUNDING_PREVIOUS_REGION_RELEASED")
        send("player RtsThird kill")
        wait_for(server_log, "rtsthird left the game", process)
        send("scoreboard players set #found_left krts_ctrl 1")
        wait_for(client_log, "Kuruluş seçim süresi doldu.", process)
        send("execute as RtsQA1 run trigger krts_found set 3")
        check(server_log, process, "if score #found_left krts_ctrl matches 0 if score RtsQA1 krts_found_site matches 2",
              "RTS_FOUNDING_DEADLINE_LOCKS_SELECTION")
        send("player RtsThird spawn")
        x, y, z = sites[2][0]
        entry_z = z - 1.5 if match else z + 0.5
        check(server_log, process, f"if score rtsthird krts_found_site matches 3 "
              f"if entity @a[name=rtsthird,x={x + 0.5},y={y},z={entry_z},distance=..2]",
              "RTS_FOUNDING_OFFLINE_PLAYER_ARRIVES_AT_FREE_SITE_ON_REJOIN")
        if match:
            if formation_jar:
                verify_orders(test, process, client_log, sites[1][0])
            if hall_jar:
                capture_hall(process, server_log, client_log, sites[1][0])
            verify_capitals(test, process, sites, server_log, client_log, diplomacy=diplomacy, wood=wood,
                            manual=bool(manual_jar), outpost=outpost, district=district, iron=iron, field=field,
                            economy=economy, bank=bank, restart_bank=restart_bank, alliances=alliances,
                            specialization=specialization)
            if full_roster:
                verify_full_roster(partial(check, server_log, process), process, server_log, field=field)
    except Exception:
        if match and process.poll() is None:
            send("function krts:reset")
        raise
    finally:
        stop_terrain_process(process)
        if properties.read_bytes() != original_properties:
            properties.write_bytes(original_properties)
            raise RuntimeError("Laboratuvar ayarı temiz kapanışla geri yüklenemedi.")


def wait_terrain_boot(log, process, previous):
    for _ in range(100):
        if log.is_file() and log.stat().st_mtime_ns != previous:
            break
        time.sleep(0.2)
    else:
        raise RuntimeError("Doğal arazi test sunucusu açılamadı.")
    wait_for(log, "RTS_QA_COMMAND_INBOX_READY", process, timeout=140)


def stop_terrain_process(process):
    if process.poll() is not None:
        return
    send("stop")
    try:
        process.wait(timeout=55)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait(timeout=10)


def verify_orders(test, process, client_log, site):
    send('tellraw RtsQA1 {"text":"RTS_TERRAIN_ORDER_READY"}')
    wait_for(client_log, "RTS_FORMATION_HYW_TWO_OWNED_UNITS_SELECTED", process)
    wait_for(client_log, "RTS_FORMATION_HYW_ORDER_SENT", process)
    x, y, z = site
    units = 'tag=krts.unit,tag=krts.s1'
    send(f'execute as @e[{units}] run data get entity @s Pos')
    row = f'{units},x={x-2.5},y={y-0.5},z={z+0.25},dx=5,dy=1,dz=3'
    test(f'if entity @e[type=hundred_years_war:militia,{row}] '
         f'if entity @e[type=hundred_years_war:archer,{row}]', "RTS_NATURAL_HYW_UNITS_FOLLOW_MOVE_ORDER")


def capture_hall(process, server_log, client_log, site):
    send("data get storage krts:arena h1", "say RTS_HALL_STORAGE_READY")
    storage = wait_for(server_log, "RTS_HALL_STORAGE_READY", process)
    hall_x = int(re.search(r"hall_x: (-?\d+)", storage)[1])
    hall_y = int(re.search(r"hall_y: (-?\d+)", storage)[1])
    hall_z = int(re.search(r"hall_z: (-?\d+)", storage)[1])
    x, y, z = site
    screenshot = LAB / "RtsQA1/screenshots/rts-belediye-0.11.1.png"
    previous = screenshot.stat().st_mtime_ns if screenshot.exists() else 0
    send("gamemode spectator RtsQA1", f"tp RtsQA1 {hall_x-6} {hall_y+20} {hall_z-10} -24 44",
         'tellraw RtsQA1 {"text":"RTS_HALL_CAPTURE"}')
    wait_for(client_log, "RTS_HALL_SCREENSHOT", process)
    assert screenshot.is_file() and screenshot.stat().st_mtime_ns != previous
    send("gamemode adventure RtsQA1", f"tp RtsQA1 {x+0.5} {y} {z-1.5}")
    print(f"PASS Büyük belediye gerçek istemci ekran görüntüsü {screenshot.name}", flush=True)


def verify_capitals(test, process, sites, server_log, client_log, *, diplomacy, wood, manual, outpost, district, iron,
                    field, economy, bank, restart_bank, alliances, specialization):
    test("if score #phase krts_ctrl matches 2 if score #slot krts_ctrl matches 3 "
         "if score #arena_style krts_ctrl matches 2 if score #capital_mode krts_ctrl matches 1 "
         f"if score #strict krts_ctrl matches {int(diplomacy)}",
         "RTS_NATURAL_CAPITAL_MATCH_STARTED")
    test("if score RtsQA1 krts_origin matches 4 if score RtsQA1 krts_profile matches 5 "
         "if score rtsthird krts_origin matches 1 if score rtsthird krts_profile matches 1",
         "RTS_NATURAL_CHARTERS_PRESERVED_AND_DEFAULTED")
    for slot, (player, site_index) in enumerate((("RtsQA1", 1), ("rtsdummy", 0), ("rtsthird", 2)), 1):
        x, y, z = sites[site_index][0]
        marker = f"RTS_NATURAL_ARRIVES_{player.upper()}"
        send(f"data get entity {player} Pos", f"data get entity {player} playerGameType",
             f"scoreboard players get {player} krts_slot", f"say {marker}_READ")
        snapshot = wait_for(server_log, f"{marker}_READ", process)
        data = re.findall(rf"{player} has the following entity data: (.+)", snapshot, re.IGNORECASE)
        position = re.fullmatch(r"\[([\d.-]+)d, ([\d.-]+)d, ([\d.-]+)d\]", data[-2]) if len(data) >= 2 else None
        registered = re.findall(rf"{player} has (\d+) \[krts_slot\]", snapshot, re.IGNORECASE)
        expected = (x + 0.5, y, z - 1.5)
        assert position and math.dist(tuple(map(float, position.groups())), expected) <= 5, (player, expected, data)
        assert data[-1] == "2" and registered and 1 <= int(registered[-1]) <= 3, (player, data, registered)
        print(f"PASS {marker}", flush=True)
        test(f"if entity @e[tag=krts.capital,x={x+0.5},y={y},z={z-0.5},distance=..0.3] "
             f"if entity @e[type=minecraft:block_display,tag=krts.hall_foundation,tag=krts.s{slot}]",
             f"RTS_NATURAL_BUILDING_{player.upper()}")
        test(f"if entity @e[type=hundred_years_war:militia,tag=krts.unit,x={x+0.5},y={y},z={z-0.5},distance=..8] "
             f"if entity @e[type=hundred_years_war:archer,tag=krts.unit,x={x+0.5},y={y},z={z-0.5},distance=..8]",
             f"RTS_NATURAL_ARMY_{player.upper()}")
    test('if entity @e[type=minecraft:block_display,tag=krts.townhall,'
         'nbt={block_state:{Name:"minecraft:polished_blackstone_bricks"}}] '
         'if entity @e[type=minecraft:block_display,tag=krts.townhall,'
         'nbt={block_state:{Name:"minecraft:stone_bricks"}}]', "RTS_NATURAL_DISTINCT_ORIGIN_HALLS")
    test('if data storage krts:arena h1.hall_y if data storage krts:arena h1.foundation_height '
         'if entity @e[type=minecraft:block_display,tag=krts.hall_roof,tag=krts.s1,'
         'nbt={transformation:{scale:[10.5f,0.45f,9.0f]}}] '
         'if entity @e[type=minecraft:block_display,tag=krts.hall_flag,tag=krts.s1]',
         "RTS_NATURAL_LARGE_HALL_FOOTPRINT_AND_FLAG")
    test("if items entity RtsQA1 hotbar.* hundred_years_war:command_staff", "RTS_NATURAL_STAFF_EQUIPPED")
    if diplomacy:
        send("execute as RtsQA1 run trigger krts_tech set 5")
        menu = wait_for(client_log, "[Okçuluk · 7 altın]", process)
        assert "[Saha Tıbbı" not in menu and "[Talim Doktrini" not in menu
    send("scoreboard players operation #qa_slot krts_ctrl = RtsQA1 krts_slot")
    x, y, z = sites[1][0]
    send("execute as RtsQA1 run trigger krts_buy set 2")
    test(f"if entity @e[type=hundred_years_war:militia,tag=krts.unit,x={x+2.5},y={y},z={z+0.5},distance=..0.45]",
         "RTS_NATURAL_RECRUIT_USES_SITE_HEIGHT")
    send(f"damage @e[tag=krts.capital,x={x+0.5},y={y},z={z-0.5},distance=..0.3,limit=1] 3 minecraft:generic")
    send(f"execute store result score #qa_health krts_ctrl run data get entity "
         f"@e[tag=krts.capital,x={x+0.5},y={y},z={z-0.5},distance=..0.3,limit=1] Health 100")
    test("if score #qa_health krts_ctrl matches 1..5999",
         "RTS_NATURAL_CAPITAL_TAKES_DAMAGE_AFTER_FOUNDING")
    if wood:
        verify_wood(test, process, server_log, client_log, sites[1][0], manual=manual)
    if outpost:
        verify_outpost(test, process, client_log, sites[1][0], diplomacy, district=district)
    elif district:
        find_site, place = construction_orders(test, process, client_log)
        verify_district(test, process, client_log, sites[1][0], None, find_site, place, diplomacy)
    if iron:
        iron_sources = verify_iron(test, process, server_log, client_log, sites[1][0])
    if field:
        verify_field(test, process, client_log, sites[1][0], diplomacy, outpost)
    if economy:
        gold_sources = verify_gold(test, process, server_log, client_log, sites[1][0])
        verify_caravan(test, process, server_log, client_log)
    if bank and diplomacy:
        test, process, server_log, client_log = verify_natural_bank(test, process, server_log, client_log, restart_bank)
    elif bank:
        test("if score #bank_trial krts_ctrl matches 0 unless score RtsQA1 krts_bank matches 0",
             "RTS_BANK_CLASSIC_HAS_NO_ACTIVE_CREDIT")
    if alliances and diplomacy:
        verify_alliances(test, process, server_log, client_log)
    elif alliances:
        send("execute as RtsQA1 run trigger krts_ally set 2")
        test("unless score RtsQA1 krts_ally matches 0 unless score #allied12 krts_ctrl matches 1",
             "RTS_ALLIANCE_CLASSIC_HAS_NO_TREATY_TRIGGER")
    if specialization:
        verify_specialization(test, process, server_log, client_log, sites[1][0], diplomacy)
    for site_index in (0, 2):
        ox, oy, oz = sites[site_index][0]
        send(f"kill @e[tag=krts.capital,x={ox+0.5},y={oy},z={oz-0.5},distance=..0.3]")
    wait_for(server_log, "kazandı. Son başkent ayakta.", process)
    test("if score #phase krts_ctrl matches 3 if score #last_capital krts_ctrl = #qa_slot krts_ctrl "
         "if entity @a[name=rtsdummy,gamemode=spectator] if entity @a[name=rtsthird,gamemode=spectator]",
         "RTS_NATURAL_ELIMINATION_AND_WINNER")
    send("function krts:reset")
    test("if score #phase krts_ctrl matches 0 if score #slot krts_ctrl matches 0 "
         "unless entity @e[tag=krts.townhall] unless entity @e[tag=krts.capital] unless entity @e[tag=krts.unit]",
         "RTS_NATURAL_RESET_CLEARS_RUNTIME_ENTITIES")
    if wood:
        test("if score #wood1 krts_ctrl matches 0 unless entity @e[tag=krts.woodworker] "
             "unless entity @e[tag=krts.wood_depot]", "RTS_NATURAL_WOOD_RESET")
    if outpost:
        test("if score #stone1 krts_ctrl matches 0 if score #posts1 krts_ctrl matches 0 "
             "if score #linked1 krts_ctrl matches 0 unless entity @e[tag=krts.post_part] "
             "unless entity @e[tag=krts.post_ticket]", "RTS_OUTPOST_RESET_RELEASES_TERRITORY")
    if district:
        test("if score #districts1 krts_ctrl matches 0 if score #housing1 krts_ctrl matches 0 "
             "unless entity @e[tag=krts.district_part]", "RTS_DISTRICT_RESET_REMOVES_CAPACITY_AND_STRUCTURES")
    if iron:
        test("if score #iron1 krts_ctrl matches 0 unless score RtsQA1 krts_nat_drill matches 1 "
             + " ".join(f"unless score {source} krts_iron_used matches 1.." for source in iron_sources),
             "RTS_NATURAL_IRON_RESET")
    if field:
        test("if score #bread1 krts_ctrl matches 0 if score #fields1 krts_ctrl matches 0 "
             "unless entity @e[tag=krts.field_part] unless score RtsQA1 krts_nat_farming matches 1",
             "RTS_FIELD_MATCH_RESET_CLEARS_STOCK_AND_MODEL")
    if economy:
        test("if score #minedGold1 krts_ctrl matches 0 if score #goldPending1 krts_ctrl matches 0 "
             "if score #caravanOffer krts_ctrl matches 0 if score #caravanDone1 krts_ctrl matches 0 "
             + " ".join(f"unless score {source} krts_gold_used matches 1.." for source in gold_sources),
             "RTS_GOLD_AND_CARAVAN_MATCH_RESET")
    if bank and diplomacy:
        test("if score #debt1 krts_ctrl matches 0 if score #due1 krts_ctrl matches 0 "
             "if score #debt_income1 krts_ctrl matches 0 if score #unstable1 krts_ctrl matches 0",
             "RTS_BANK_RESET_CLEARS_ALL_BALANCES")
    if alliances and diplomacy:
        test("if score #allied12 krts_ctrl matches 0 if score #allyOffer12 krts_ctrl matches 0 "
             "if score #allyCall12 krts_ctrl matches 0 unless data storage krts:alliance calls "
             "unless data storage krts:alliance offers", "RTS_ALLIANCE_RESET_CLEARS_TREATIES_AND_REQUESTS")
    if specialization:
        test("if score #academies1 krts_ctrl matches 0 if score #academy_linked1 krts_ctrl matches 0 "
             "unless entity @e[tag=krts.academy_part]", "RTS_ACADEMY_RESET_CLEARS_BUILDINGS_AND_CONNECTION")
    for index, ((x, y, z), _) in enumerate(sites, 1):
        test(f"unless block {x} {y-1} {z} air if block {x} {y} {z} air "
             f"unless block {x} {y-1} {z-1} gilded_blackstone", f"RTS_NATURAL_UNCHANGED_SITE_{index}")
    send("forceload query", "say RTS_NATURAL_TICKETS_CHECKED")
    snapshot = wait_for(server_log, "RTS_NATURAL_TICKETS_CHECKED", process)
    assert "No force loaded chunks were found" in snapshot, "Maçın parça biletleri kaldı."
    assert "Failed to load function" not in snapshot and "Failed to execute function" not in snapshot
    print("PASS Doğal maçın parça biletleri ve işlev hataları kontrol edildi.", flush=True)


def verify_wood(test, process, server_log, client_log, site, *, manual):
    worker = 'tag=krts.woodworker,tag=krts.s1'
    test(f'if entity @e[{worker},nbt={{NoAI:1b}}] if score #wood1 krts_ctrl matches 0 '
         'if score rtsdummy krts_auto matches 0', "RTS_NATURAL_WOOD_OWNER_STARTS_OFF")
    send("execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[Oduncuyu aç] [Oduncuyu kapat]", process)
    wait_for(client_log, "Belediye deposu · Odun 0/12", process)
    send("execute as RtsQA1 run trigger krts_npc set 1")
    test('if score RtsQA1 krts_auto matches 1 if score rtsdummy krts_auto matches 0',
         "RTS_NATURAL_WOOD_TOGGLE_IS_PER_PLAYER")
    test('if score #wood1 krts_ctrl matches 3..12 if score #wood2 krts_ctrl matches 0',
         "RTS_NATURAL_WOOD_DELIVERED_BY_WALKING", attempts=75)
    send("execute as RtsQA1 run trigger krts_npc set 2", "scoreboard players list", "say RTS_WOOD_LEDGER_READY")
    log = wait_for(server_log, "RTS_WOOD_LEDGER_READY", process)
    accounted = sorted(set(re.findall(r"wood\.(-?\d+)", log)))
    assert len(accounted) >= 2, "Oduncu en az iki doğal kaynak kullanmalı."
    for index, value in enumerate(accounted, 1):
        packed = int(value)
        x = signed(packed >> 38, 26)
        y = signed(packed, 12)
        z = signed(packed >> 12, 26)
        test(f"if score wood.{value} krts_wood_used matches 1..2 if block {x} {y} {z} #minecraft:logs",
             f"RTS_NATURAL_WOOD_SOURCE_LIMIT_AND_PRESERVATION_{index}")
    test(f'if entity @e[{worker},nbt={{NoAI:1b}}] if score RtsQA1 krts_auto matches 0',
         "RTS_NATURAL_WOOD_STOPS_ON_REQUEST")
    if manual:
        verify_manual_wood(test, process, server_log, client_log, accounted, site)


def verify_manual_wood(test, process, server_log, client_log, accounted, site):
    send(*(f"scoreboard players get wood.{value} krts_wood_used" for value in accounted),
         "say RTS_WOOD_MANUAL_LEDGER")
    ledger = wait_for(server_log, "RTS_WOOD_MANUAL_LEDGER", process)
    available = re.search(r"wood\.(-?\d+) has 1 \[krts_wood_used\]", ledger)
    exhausted = re.search(r"wood\.(-?\d+) has 2 \[krts_wood_used\]", ledger)
    assert available and exhausted, "Elle seçim için tükenmiş ve kotası kalan gövde gerekli."

    def click(x, y, z):
        send(f'tellraw RtsQA1 {{"text":"RTS_WOOD_PICK {x} {y} {z}"}}')

    def trunk(packed):
        return signed(packed >> 38, 26), signed(packed, 12), signed(packed >> 12, 26)

    send("execute as RtsQA1 run trigger krts_job set 1")
    wait_for(client_log, "Üstten kamera açıkken seçtiğin gövdeye sağ tıkla.", process)
    x, y, z = site
    click(x, y - 1, z)
    wait_for(client_log, "Odun kaynağı olarak bir ağaç gövdesi seç.", process)
    click(*trunk(int(exhausted[1])))
    wait_for(client_log, "Bu gövdenin kotası dolmuş veya oduncunun yolu kapalı.", process)
    send("scoreboard players operation #qa_wood_before krts_ctrl = #wood1 krts_ctrl")
    click(*trunk(int(available[1])))
    wait_for(client_log, "Oduncu seçtiğin kaynağa gidiyor.", process)
    test("if score RtsQA1 krts_wood_task matches 1 if score RtsQA1 krts_auto matches 0 "
         "unless score rtsdummy krts_wood_task matches 1", "RTS_WOOD_NATIVE_RIGHT_CLICK_ASSIGNS_ONLY_OWNER")
    test(f"if score #wood1 krts_ctrl > #qa_wood_before krts_ctrl if score wood.{available[1]} krts_wood_used matches 2 "
         "if score RtsQA1 krts_auto matches 0 if score #wood2 krts_ctrl matches 0",
         "RTS_WOOD_MANUAL_DELIVERY_WITH_AUTOMATION_OFF", attempts=45)


def signed(value, bits):
    return (value + (1 << (bits - 1))) % (1 << bits) - (1 << (bits - 1))


def exposed_ore_pads(site, marker, process, server_log):
    x, y, z = site
    candidates = [(x + dx, y + dy - 1, z + dz) for dx, dz in ((4, 0), (4, 2), (1, 2), (3, 2), (0, 2))
                  for dy in (0, 1, -1)]
    original_types = ("grass_block[snowy=false]", "dirt", "stone", "podzol[snowy=false]")
    send(*(f"execute if block {bx} {by} {bz} {original} if block {bx} {by+1} {bz} air "
           f"if block {bx} {by+2} {bz} air run say {marker} {index} {original}"
           for index, (bx, by, bz) in enumerate(candidates) for original in original_types),
         f"say {marker}S_READY")
    log = wait_for(server_log, f"{marker}S_READY", process)
    pads = [(candidates[int(index)], original)
            for index, original in re.findall(rf"\[Server\] {marker} (\d+) (\S+)", log)]
    assert len(pads) >= 2, "İki açık test cevheri alanı bulunamadı."
    return pads[:2]


def native_source_click(pos):
    send('tellraw RtsQA1 {"text":"RTS_WOOD_PICK %d %d %d"}' % pos)


def ore_holder(material, pos):
    bx, by, bz = pos
    packed = ((bx & 0x3FFFFFF) << 38) | ((bz & 0x3FFFFFF) << 12) | (by & 0xFFF)
    return f"{material}.{signed(packed, 64)}"


def verify_iron(test, process, server_log, client_log, site):
    send("execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[Demir toplamayı aç] [Demir damarı seç] [Demir al · 3 altın]", process)
    wait_for(client_log, "Demir 0/8", process)
    send("scoreboard players set #income_left krts_ctrl 120",
         "scoreboard players set #g1 krts_ctrl 2",
         "execute as RtsQA1 run trigger krts_npc set 5")
    wait_for(client_log, "Bir talim demiri için 3 altın gerekir.", process)
    test("if score #iron1 krts_ctrl matches 0 if score #g1 krts_ctrl matches 2", "RTS_IRON_UNAFFORDABLE_SAFE")
    send("scoreboard players set #g1 krts_ctrl 8", "execute as RtsQA1 run trigger krts_npc set 5")
    test("if score #iron1 krts_ctrl matches 1 if score #g1 krts_ctrl matches 5 "
         "if score #iron2 krts_ctrl matches 0 unless score RtsQA1 krts_nat_drill matches 1",
         "RTS_IRON_CAN_BUY_WITH_AUTOMATION_OFF")
    send("scoreboard players set #iron1 krts_ctrl 8", "execute as RtsQA1 run trigger krts_npc set 5")
    wait_for(client_log, "Demir deposunda yer yok.", process)
    test("if score #iron1 krts_ctrl matches 8 if score #g1 krts_ctrl matches 5", "RTS_IRON_FULL_DEPOT_SAFE")
    send("scoreboard players set #iron1 krts_ctrl 0")

    # Use two exposed blocks in this isolated world and restore their exact previous states.
    x, y, z = site
    (target, original), (other, second_original) = exposed_ore_pads(site, "RTS_IRON_PAD", process, server_log)
    target_id, other_id = (ore_holder("iron", pos) for pos in (target, other))
    target_pos, other_pos = ("%d %d %d" % pos for pos in (target, other))
    try:
        send(f"setblock {target_pos} minecraft:iron_ore", "execute as RtsQA1 run trigger krts_job set 4")
        wait_for(client_log, "Üstten kamera açıkken görünen demir cevherine sağ tıkla.", process)
        native_source_click((x, y - 1, z))
        wait_for(client_log, "Bir demir cevheri bloğu seç.", process)
        native_source_click(target)
        wait_for(client_log, "İşçi seçtiğin demir damarına gidiyor.", process)
        test("if score RtsQA1 krts_auto matches 0 if score RtsQA1 krts_wood_task matches 3",
             "RTS_IRON_NATIVE_CLICK_WITH_AUTOMATION_OFF")
        test(f"if score #iron1 krts_ctrl matches 1 if score #iron2 krts_ctrl matches 0 "
             f"if score {target_id} krts_iron_used matches 1 if block {target_pos} iron_ore",
             "RTS_IRON_WORKER_DELIVERS_AND_ORE_STAYS", attempts=70)
        wait_for(client_log, "Demir damarı tükenmiş veya rotası kapanmış.", process)
        send("execute as RtsQA1 run trigger krts_npc set 6")
        trainee = "@e[tag=krts.unit,tag=krts.s1,scores={krts_rank=1},limit=1]"
        test("if score RtsQA1 krts_nat_drill matches 1 unless score rtsdummy krts_nat_drill matches 1 "
             "if entity @e[tag=krts.unit,tag=krts.s1,scores={krts_training=1}]",
             "RTS_NATURAL_DRILL_OWNER_STARTS", attempts=35)
        test(f"if entity {trainee} if score #g1 krts_ctrl matches 3 if score #iron1 krts_ctrl matches 0",
             "RTS_NATURAL_DRILL_SPENDS_MINED_IRON_AND_GOLD", attempts=110)
        send(f"execute store result score #qa_drill_hp krts_ctrl run attribute {trainee} minecraft:generic.max_health get")
        test("if score #qa_drill_hp krts_ctrl matches 22", "RTS_NATURAL_DRILL_APPLIES_REAL_ATTRIBUTES")

        send(f"setblock {other_pos} minecraft:deepslate_iron_ore", "execute as RtsQA1 run trigger krts_npc set 7")
        wait_for(client_log, "Talim durduruldu.", process)
        send("execute as RtsQA1 run trigger krts_npc set 4")
        test(f"if score #iron1 krts_ctrl matches 1..8 if score {other_id} krts_iron_used matches 1 "
             f"if block {other_pos} deepslate_iron_ore if score {target_id} krts_iron_used matches 1 "
             "if score RtsQA1 krts_auto matches 1 if score rtsdummy krts_auto matches 0 "
             "if score RtsQA1 krts_nat_drill matches 0",
             "RTS_IRON_AUTOMATION_SKIPS_EXHAUSTED_AND_MINES_DEEPSLATE", attempts=85)
        send("execute as RtsQA1 run trigger krts_npc set 2")
        test("if score RtsQA1 krts_auto matches 0", "RTS_IRON_AUTO_STOPS")
    finally:
        send(f"setblock {target_pos} {original}", f"setblock {other_pos} {second_original}",
             "scoreboard players operation #income_left krts_ctrl = #income krts_ctrl")
    return target_id, other_id


def verify_gold(test, process, server_log, client_log, site):
    worker = "@e[tag=krts.woodworker,tag=krts.s1,limit=1]"
    (first, original), (second, other_original) = exposed_ore_pads(site, "RTS_GOLD_PAD", process, server_log)
    first_id, second_id = (ore_holder("gold", pos) for pos in (first, second))
    first_pos, second_pos = ("%d %d %d" % pos for pos in (first, second))
    send("scoreboard players set #income_left krts_ctrl 600",
         "scoreboard players set #g1 krts_ctrl 5", "execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[Altın toplamayı aç] [Altın damarı seç]", process)
    try:
        send(f"setblock {first_pos} minecraft:gold_ore", "execute as RtsQA1 run trigger krts_job set 5")
        wait_for(client_log, "Üstten kamera açıkken görünen altın cevherine sağ tıkla.", process)
        x, y, z = site
        native_source_click((x, y - 1, z))
        wait_for(client_log, "Bir altın cevheri bloğu seç.", process)
        native_source_click(first)
        wait_for(client_log, "İşçi seçtiğin altın damarına gidiyor.", process)
        test("if score RtsQA1 krts_wood_task matches 4 if score RtsQA1 krts_auto matches 0",
             "RTS_GOLD_HYW_MANUAL_ASSIGNMENT")
        test(f"if score #g1 krts_ctrl matches 6 if score #minedGold1 krts_ctrl matches 1 "
             f"if score #minedGold2 krts_ctrl matches 0 if score {first_id} krts_gold_used matches 1 "
             f"if block {first_pos} gold_ore", "RTS_GOLD_DELIVERY_CREDITS_OWNER_AND_PRESERVES_ORE", attempts=70)
        wait_for(client_log, "Altın damarı tükenmiş veya rotası kapanmış.", process)
        send(f"setblock {second_pos} minecraft:deepslate_gold_ore", "execute as RtsQA1 run trigger krts_npc set 12")
        test(f"if score #g1 krts_ctrl matches 7 if score #minedGold1 krts_ctrl matches 2 "
             f"if score {first_id} krts_gold_used matches 1 if score {second_id} krts_gold_used matches 1 "
             f"if block {second_pos} deepslate_gold_ore if score rtsdummy krts_auto matches 0",
             "RTS_GOLD_AUTOMATION_SKIPS_USED_SOURCE_AND_MINES_DEEPSLATE", attempts=82)

        # Reproduce a treasury filled by other income while a worker was already carrying gold.
        send("scoreboard players set #g1 krts_ctrl 60", f"scoreboard players set {worker} krts_wood_cargo 4")
        test(f"if score #g1 krts_ctrl matches 60 if score #goldPending1 krts_ctrl matches 1 "
             f"if score #minedGold1 krts_ctrl matches 2 if score {worker} krts_wood_cargo matches 0",
             "RTS_GOLD_FULL_TREASURY_PRESERVES_DELIVERY")
        send("execute as RtsQA1 run trigger krts_npc set 2")
        test("if score RtsQA1 krts_auto matches 0", "RTS_GOLD_AUTO_STOPPED")
        send("scoreboard players set #g1 krts_ctrl 59")
        test("if score #g1 krts_ctrl matches 60 if score #goldPending1 krts_ctrl matches 0 "
             "if score #minedGold1 krts_ctrl matches 3", "RTS_GOLD_RESERVE_CREDITS_ONCE_WHEN_SPACE_OPENS")
    finally:
        send(f"setblock {first_pos} {original}", f"setblock {second_pos} {other_original}")
    return first_id, second_id


def verify_caravan(test, process, server_log, client_log):
    def open_order(index, cycle=1):
        send("scoreboard players set #caravanTime krts_ctrl 1")
        test(f"if score #caravanOffer krts_ctrl matches {index}", f"RTS_CARAVAN_ORDER_{index}_ARRIVES_{cycle}")
        marker = f"RTS_CARAVAN_TOKEN_{index}_{cycle}_READ"
        send("scoreboard players get #caravanRound krts_ctrl", f"say {marker}")
        snapshot = wait_for(server_log, marker, process)
        return int(re.findall(r"#caravanRound has (\d+) \[krts_ctrl\]", snapshot)[-1])

    def close_order(index):
        send("scoreboard players set #caravanTime krts_ctrl 1")
        test("if score #caravanOffer krts_ctrl matches 0", f"RTS_CARAVAN_ORDER_{index}_EXPIRES")

    def accept(token):
        send(f"execute as RtsQA1 run trigger krts_caravan set {token}")

    send("function krts:caravan/init", "scoreboard players set #g1 krts_ctrl 10",
         "scoreboard players set #wood1 krts_ctrl 3")
    token = open_order(1)
    send("execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[Kervana 4 odun teslim et · 3 altın]", process)
    accept(token)
    wait_for(client_log, "Sipariş için 4 odun gerekir.", process)
    test("if score #wood1 krts_ctrl matches 3 if score #g1 krts_ctrl matches 10",
         "RTS_CARAVAN_INSUFFICIENT_STOCK_UNCHANGED")
    send("scoreboard players set #wood1 krts_ctrl 8")
    accept(token)
    wait_for(client_log, "4 odun teslim edildi. Hazineye 3 altın eklendi.", process)
    test("if score #wood1 krts_ctrl matches 4 if score #g1 krts_ctrl matches 13 "
         "if score #caravanDone1 krts_ctrl = #caravanRound krts_ctrl if score #caravanDone2 krts_ctrl matches 0",
         "RTS_CARAVAN_ONE_EXACT_OWNER_PAYMENT")
    accept(token)
    wait_for(client_log, "Bu kervana siparişini zaten teslim ettin.", process)
    test("if score #wood1 krts_ctrl matches 4 if score #g1 krts_ctrl matches 13",
         "RTS_CARAVAN_REPEAT_DOES_NOT_DEBIT")
    close_order(1)
    stone_token = open_order(2)
    assert token != stone_token
    send("scoreboard players set #stone1 krts_ctrl 4")
    accept(token)
    wait_for(client_log, "Kervan siparişi değişti.", process)
    test("if score #stone1 krts_ctrl matches 4 if score #g1 krts_ctrl matches 13",
         "RTS_CARAVAN_STALE_ORDER_DOES_NOT_SELL_NEW_STOCK")

    send("function krts:pause")
    test("if score #phase krts_ctrl matches 4", "RTS_CARAVAN_MATCH_PAUSED")
    send("scoreboard players operation #qa_caravanTime krts_ctrl = #caravanTime krts_ctrl")
    accept(stone_token)
    time.sleep(2)
    test("if score #qa_caravanTime krts_ctrl = #caravanTime krts_ctrl if score #stone1 krts_ctrl matches 4 "
         "if score #g1 krts_ctrl matches 13", "RTS_CARAVAN_PAUSE_STOPS_TIMER_AND_SALES")
    send("function krts:resume", "scoreboard players set #g1 krts_ctrl 58")
    test("if score #phase krts_ctrl matches 2 if score #stone1 krts_ctrl matches 4 if score RtsQA1 krts_caravan matches 0",
         "RTS_CARAVAN_RESUMED_WITHOUT_QUEUED_SALE")
    accept(stone_token)
    wait_for(client_log, "Kervan ödemesi hazineye sığmıyor.", process)
    test("if score #stone1 krts_ctrl matches 4 if score #g1 krts_ctrl matches 58",
         "RTS_CARAVAN_FULL_TREASURY_DOES_NOT_DEBIT")
    send("scoreboard players set #g1 krts_ctrl 10")
    accept(stone_token)
    test("if score #stone1 krts_ctrl matches 0 if score #g1 krts_ctrl matches 13",
         "RTS_CARAVAN_SECOND_VISIT_ACCEPTS_STONE")
    close_order(2)
    bread_token = open_order(3)
    send("scoreboard players set #bread1 krts_ctrl 2")
    accept(bread_token)
    test("if score #bread1 krts_ctrl matches 0 if score #g1 krts_ctrl matches 17 "
         "if score #bread2 krts_ctrl matches 2", "RTS_CARAVAN_TRADE_CONSUMES_REAL_FOOD_STOCK")
    close_order(3)
    iron_token = open_order(4)
    send("scoreboard players set #iron1 krts_ctrl 2")
    accept(iron_token)
    test("if score #iron1 krts_ctrl matches 0 if score #g1 krts_ctrl matches 21",
         "RTS_CARAVAN_TRADE_CONSUMES_REAL_IRON_STOCK")
    close_order(4)
    assert open_order(1, cycle=2) > iron_token
    send("scoreboard players operation #income_left krts_ctrl = #income krts_ctrl")


def verify_natural_bank(test, process, server_log, client_log, restart):
    capital = "@e[tag=krts.capital,tag=krts.s1,limit=1]"
    worker = "@e[tag=krts.woodworker,tag=krts.s1,limit=1]"

    def action(value):
        send(f"execute as RtsQA1 run trigger krts_bank set {value}")

    send("scoreboard players set #income_left krts_ctrl 900",
         "scoreboard players set #g1 krts_ctrl 6", "scoreboard players set #bread1 krts_ctrl 2",
         "execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[Hazine ve banka]", process)
    test("if score #bank_trial krts_ctrl matches 1 if score RtsQA1 krts_bank matches 0 "
         "if score #debt1 krts_ctrl matches 0", "RTS_BANK_NATURAL_DIPLOMACY_READY")
    action(4)
    wait_for(client_log, "[Kredi al · 8 altın, toplam 10]", process)
    action(1)
    wait_for(client_log, "8 altın alındı. Toplam 10 ödenecek.", process)
    test("if score #g1 krts_ctrl matches 14 if score #debt1 krts_ctrl matches 10 "
         "if score #debt2 krts_ctrl matches 0 if score #debt_income1 krts_ctrl matches 0",
         "RTS_BANK_GRANTS_ONE_LOAN_WITHOUT_COUNTING_AS_INCOME")
    action(1)
    wait_for(client_log, "Bir açık kredin var.", process)
    test("if score #g1 krts_ctrl matches 14 if score #debt1 krts_ctrl matches 10", "RTS_BANK_REPEAT_LOAN_REJECTED")

    # Credit one municipal income and one worker delivery through their usual entry points.
    send("scoreboard players set #debt_t1 krts_ctrl 300", "function krts:earn/1",
         f"scoreboard players set {worker} krts_wood_cargo 4",
         "execute as RtsQA1 run trigger krts_npc set 12")
    test("if score #g1 krts_ctrl matches 16 if score #debt_income1 krts_ctrl matches 2 "
         f"if score {worker} krts_wood_cargo matches 0", "RTS_BANK_COUNTS_MUNICIPAL_AND_MINING_INCOME")
    send("execute as RtsQA1 run trigger krts_npc set 2", "function krts:caravan/init",
         "scoreboard players set #wood1 krts_ctrl 4", "scoreboard players set #caravanTime krts_ctrl 1")
    test("if score #caravanOffer krts_ctrl matches 1 if score RtsQA1 krts_caravan matches 0", "RTS_BANK_CARAVAN_ORDER_READY")
    send("scoreboard players get #caravanRound krts_ctrl", "say RTS_BANK_CARAVAN_TOKEN_READ")
    snapshot = wait_for(server_log, "RTS_BANK_CARAVAN_TOKEN_READ", process)
    token = int(re.findall(r"#caravanRound has (\d+) \[krts_ctrl\]", snapshot)[-1])
    send(f"execute as RtsQA1 run trigger krts_caravan set {token}")
    test("if score #wood1 krts_ctrl matches 0 if score #g1 krts_ctrl matches 19 "
         "if score #debt_income1 krts_ctrl matches 5", "RTS_BANK_COUNTS_CARAVAN_SALE_AS_INCOME")
    send("scoreboard players set #debt_t1 krts_ctrl 1")
    wait_for(client_log, "Vadesi geçen taksit istikrarsızlığı artırdı.", process)
    test("if score #g1 krts_ctrl matches 18 if score #debt1 krts_ctrl matches 9 if score #due1 krts_ctrl matches 1 "
         "if score #debt_income1 krts_ctrl matches 0 if score #unstable1 krts_ctrl matches 1",
         "RTS_BANK_PAYS_HALF_OF_INCOME_AFTER_RESERVE")

    send("scoreboard players set #g1 krts_ctrl 2", "scoreboard players set #debt_t1 krts_ctrl 1")
    test("if score #g1 krts_ctrl matches 2 if score #debt1 krts_ctrl matches 9 if score #due1 krts_ctrl matches 3 "
         "if score #unstable1 krts_ctrl matches 3", "RTS_BANK_MISSED_PAYMENT_PRESERVES_RESERVE_AND_COMPOUNDS")
    send("scoreboard players set #unstable1 krts_ctrl 5", "scoreboard players set #debt_t1 krts_ctrl 1")
    test("if score #unstable1 krts_ctrl matches 6 if score #due1 krts_ctrl matches 5", "RTS_BANK_INSTABILITY_CAP")
    send("scoreboard players set #g1 krts_ctrl 11", "execute as RtsQA1 run trigger krts_war set 2")
    wait_for(client_log, "İstikrarsızlık en yüksek düzeyde.", process)
    test("if score #pact12 krts_ctrl matches 0 if score #g1 krts_ctrl matches 11", "RTS_BANK_CRISIS_REJECTS_WAR_WITHOUT_DEBIT")
    action(3)
    wait_for(client_log, "Halkın istikrarını toparlamadan önce borcu kapat.", process)
    send(f"data merge entity {capital} {{Health:58.5d}}")
    action(5)
    wait_for(client_log, "Onarımdan önce borcu kapat.", process)
    test("if score #bread1 krts_ctrl matches 2 if score #unstable1 krts_ctrl matches 6 "
         "if entity @e[tag=krts.capital,tag=krts.s1,nbt={Health:58.5d}] if score #g1 krts_ctrl matches 11",
         "RTS_BANK_DEBT_BLOCKS_RELIEF_AND_REPAIR")
    send("function krts:pause", "scoreboard players set #debt_t1 krts_ctrl 40")
    test("if score #phase krts_ctrl matches 4 if score #debt1 krts_ctrl matches 9 if score #debt_t1 krts_ctrl matches 40",
         "RTS_BANK_PAUSED_BALANCE_BEFORE_RESTART")
    test, process, server_log, client_log = restart()
    send("player RtsThird spawn")
    wait_for(server_log, "rtsthird joined the game", process)
    test("if score #phase krts_ctrl matches 4 if score #debt1 krts_ctrl matches 9 if score #due1 krts_ctrl matches 5 "
         "if score #debt_t1 krts_ctrl matches 40 if score #unstable1 krts_ctrl matches 6 "
         "if score #bread1 krts_ctrl matches 2 if score #g1 krts_ctrl matches 11",
         "RTS_BANK_RESTART_PRESERVES_DEBT_TIMER_STOCK_AND_STABILITY")
    action(2)
    send("function krts:resume")
    test("if score #phase krts_ctrl matches 2 if score RtsQA1 krts_bank matches 0 "
         "if score #debt1 krts_ctrl matches 9 if score #g1 krts_ctrl matches 11",
         "RTS_BANK_PAUSED_ACTION_NOT_REPLAYED_AFTER_RESUME")
    for outstanding in (7, 5, 3, 1, 0):
        action(2)
        test(f"if score #debt1 krts_ctrl matches {outstanding} if score #g1 krts_ctrl matches {outstanding + 2}",
             f"RTS_BANK_EARLY_PAYMENT_LEAVES_{outstanding}")
    action(3)
    test("if score #bread1 krts_ctrl matches 1 if score #bread2 krts_ctrl matches 2 if score #unstable1 krts_ctrl matches 5",
         "RTS_BANK_RELIEF_USES_OWN_MUNICIPAL_FOOD")
    action(5)
    wait_for(client_log, "Temel ihtiyaç payı dışında onarım altını yetersiz.", process)
    test("if score #g1 krts_ctrl matches 2 if entity @e[tag=krts.capital,tag=krts.s1,nbt={Health:58.5d}]",
         "RTS_BANK_REPAIR_RESPECTS_TREASURY_RESERVE")
    send("function krts:earn/1", "function krts:earn/1")
    action(5)
    test("if score #g1 krts_ctrl matches 2 if score #unstable1 krts_ctrl matches 4 "
         "if entity @e[tag=krts.capital,tag=krts.s1,nbt={Health:60.0d}]",
         "RTS_BANK_REPAIR_CAPS_HEALTH_AND_RESTORES_STABILITY")
    send("function krts:earn/1", "function krts:earn/1")
    action(5)
    wait_for(client_log, "Belediyenin canı zaten tam.", process)
    test("if score #g1 krts_ctrl matches 4 if score #unstable1 krts_ctrl matches 4", "RTS_BANK_FULL_CAPITAL_NO_REPEAT_REPAIR")
    return test, process, server_log, client_log


def verify_alliances(test, process, server_log, client_log):
    owner, ally, enemy = "RtsQA1", "rtsdummy", "rtsthird"

    def trigger(player, goal, value):
        send(f"execute as {player} run trigger {goal} set {value}")

    def token(score, label):
        send(f"scoreboard players get {score} krts_ctrl", f"say {label}")
        snapshot = wait_for(server_log, label, process)
        return int(re.findall(rf"{score} has (\d+) \[krts_ctrl\]", snapshot)[-1])

    send("scoreboard players set #income_left krts_ctrl 900",
         "scoreboard players set #g1 krts_ctrl 14",
         "scoreboard players set #bread1 krts_ctrl 2", "scoreboard players set #bread2 krts_ctrl 2",
         "scoreboard players set #iron2 krts_ctrl 0", "scoreboard players set #debt_income2 krts_ctrl 0",
         "scoreboard players set #unstable1 krts_ctrl 0", "scoreboard players set #unstable2 krts_ctrl 0",
         "execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[İttifak kur]", process)
    trigger(owner, "krts_aid_gold", 3)
    wait_for(client_log, "Yardım için etkin ittifak gerekir.", process)
    test("if score #g1 krts_ctrl matches 14", "RTS_ALLY_UNALLIED_GIFT_REFUSED")
    trigger(owner, "krts_ally", 3)
    test("if score #allyOffer13 krts_ctrl matches 1..", "RTS_ALLY_OFFER_TO_THIRD_CREATED")
    expired = token("#allyOffer13", "RTS_ALLY_EXPIRED_OFFER_TOKEN")
    send("scoreboard players set #allyOfferTime13 krts_ctrl 1")
    test("if score #allyOffer13 krts_ctrl matches 0 unless data storage krts:alliance offers.o13",
         "RTS_ALLY_OFFER_EXPIRES")
    trigger(enemy, "krts_ally_reply", expired)
    test("if score #allied13 krts_ctrl matches 0 if score rtsthird krts_ally_reply matches 0",
         "RTS_ALLY_EXPIRED_OFFER_CANNOT_CREATE_TREATY")
    trigger(owner, "krts_ally", 2)
    test("if score #allyOffer12 krts_ctrl matches 1.. if score #allied12 krts_ctrl matches 0",
         "RTS_ALLY_INVITATION_AWAITS_PEER")
    offer = token("#allyOffer12", "RTS_ALLY_LIVE_OFFER_TOKEN")
    trigger(owner, "krts_ally_reply", offer)
    test("if score #allyOffer12 krts_ctrl matches 1.. if score #allied12 krts_ctrl matches 0 "
         "if score RtsQA1 krts_ally_reply matches 0", "RTS_ALLY_REQUESTER_CANNOT_ACCEPT_OWN_OFFER")
    trigger(ally, "krts_ally_reply", offer)
    wait_for(client_log, "Devlet 1 ve Devlet 2 ittifak kurdu.", process)
    test("if score #allied12 krts_ctrl matches 1 if score #allyOffer12 krts_ctrl matches 0",
         "RTS_ALLY_MUTUAL_TREATY_CREATED")
    send("execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[2 altın gönder] [1 erzak gönder] [1 demir gönder]", process)
    trigger(owner, "krts_war", 2)
    wait_for(client_log, "Bu devlet müttefikin.", process)
    send("damage @e[tag=krts.capital,tag=krts.s2,limit=1] 4 minecraft:mob_attack by RtsQA1")
    test("if entity @e[tag=krts.capital,tag=krts.s2,nbt={Health:60.0d}] if score #pact12 krts_ctrl matches 0 "
         "if score #g1 krts_ctrl matches 14", "RTS_ALLY_NORMAL_WAR_AND_DAMAGE_BLOCKED")

    send("scoreboard players set #g2 krts_ctrl 59")
    trigger(owner, "krts_aid_gold", 2)
    wait_for(client_log, "Müttefik depoda yeterli yer yok.", process)
    test("if score #g1 krts_ctrl matches 14 if score #g2 krts_ctrl matches 59", "RTS_ALLY_FULL_TARGET_UNCHANGED")
    send("scoreboard players set #g2 krts_ctrl 6", "scoreboard players set #debt2 krts_ctrl 1",
         "scoreboard players set #debt_t2 krts_ctrl 300", "scoreboard players set #iron1 krts_ctrl 2")
    trigger(owner, "krts_aid_gold", 2)
    trigger(owner, "krts_aid_food", 2)
    trigger(owner, "krts_aid_iron", 2)
    test("if score #g1 krts_ctrl matches 12 if score #g2 krts_ctrl matches 8 if score #debt_income2 krts_ctrl matches 0 "
         "if score #bread1 krts_ctrl matches 1 if score #bread2 krts_ctrl matches 3 "
         "if score #iron1 krts_ctrl matches 1 if score #iron2 krts_ctrl matches 1",
         "RTS_ALLY_THREE_TRANSFERS_MOVE_EXACT_STOCK")
    send("scoreboard players set #g2 krts_ctrl 3")
    trigger(ally, "krts_aid_gold", 1)
    time.sleep(2)
    test("if score #g1 krts_ctrl matches 12 if score #g2 krts_ctrl matches 3",
         "RTS_ALLY_DONOR_DEBT_RESERVE_PROTECTED")

    trigger(owner, "krts_war", 3)
    test("if score #pact13 krts_ctrl matches 1", "RTS_ALLY_ORIGINAL_WAR_NOTICE")
    send("scoreboard players set #clock13 krts_ctrl 1")
    test("if score #pact13 krts_ctrl matches 2 if score #allyWar13 krts_ctrl matches 1", "RTS_ALLY_ORIGINAL_WAR_ACTIVE")
    trigger(owner, "krts_ally_call", 23)
    test("if score #allyCall12 krts_ctrl matches 1.. if data storage krts:alliance calls.c1_2.enemy",
         "RTS_ALLY_VALID_CALL_CREATED")
    call = token("#allyCall12", "RTS_ALLY_FIRST_CALL_TOKEN")
    trigger(enemy, "krts_ally_reply", -call)
    test("if score #allyCall12 krts_ctrl matches 1.. if score #unstable2 krts_ctrl matches 0 "
         "if score rtsthird krts_ally_reply matches 0", "RTS_ALLY_THIRD_PARTY_CANNOT_ANSWER_CALL")
    trigger(ally, "krts_ally_reply", -call)
    wait_for(client_log, "Devlet 2 savaş çağrını reddetti.", process)
    test("if score #allyCall12 krts_ctrl matches 0 if score #unstable2 krts_ctrl matches 1 if score #pact23 krts_ctrl matches 0",
         "RTS_ALLY_REFUSAL_ONE_PENALTY")
    trigger(owner, "krts_ally_call", 23)
    wait_for(client_log, "bu savaş için zaten çağrı gönderdin.", process)
    trigger(ally, "krts_ally_reply", -call)
    test("if score #allyCall12 krts_ctrl matches 0 if score #unstable2 krts_ctrl matches 1 "
         "if score rtsdummy krts_ally_reply matches 0", "RTS_ALLY_REPEAT_AND_REPLAY_HAVE_NO_PENALTY")

    send("scoreboard players set #clock13 krts_ctrl 0")
    trigger(owner, "krts_peace", 3)
    test("if score #offer13 krts_ctrl matches 11", "RTS_ALLY_OLD_WAR_PEACE_OFFER")
    trigger(enemy, "krts_peace", -1)
    test("if score #pact13 krts_ctrl matches 3", "RTS_ALLY_OLD_WAR_TRUCE")
    send("scoreboard players set #clock13 krts_ctrl 1")
    test("if score #pact13 krts_ctrl matches 0", "RTS_ALLY_TRUCE_EXPIRES")
    trigger(owner, "krts_war", 3)
    test("if score #pact13 krts_ctrl matches 1", "RTS_ALLY_SECOND_WAR_NOTICE")
    send("scoreboard players set #clock13 krts_ctrl 1")
    test("if score #pact13 krts_ctrl matches 2 if score #allyWar13 krts_ctrl matches 2", "RTS_ALLY_NEW_WAR_CAN_REQUEST_AGAIN")
    trigger(owner, "krts_ally_call", 23)
    test("if score #allyCall12 krts_ctrl matches 1..", "RTS_ALLY_SECOND_CALL_CREATED")
    accepted = token("#allyCall12", "RTS_ALLY_SECOND_CALL_TOKEN")
    assert accepted > call
    send("function krts:pause")
    test("if score #phase krts_ctrl matches 4 if score #allyCall12 krts_ctrl matches 1..", "RTS_ALLY_PAUSE_WITH_PENDING_CALL")
    send("scoreboard players operation #qa_ally_time krts_ctrl = #allyCallTime12 krts_ctrl")
    trigger(ally, "krts_ally_reply", accepted)
    time.sleep(2)
    test("if score #allyCallTime12 krts_ctrl = #qa_ally_time krts_ctrl if score #pact23 krts_ctrl matches 0",
         "RTS_ALLY_PAUSE_PRESERVES_CALL_AND_BLOCKS_REPLY")
    send("function krts:resume")
    test("if score #phase krts_ctrl matches 2 if score rtsdummy krts_ally_reply matches 0", "RTS_ALLY_RESUME_ENABLES_REPLY")
    trigger(ally, "krts_ally_reply", accepted)
    test("if score #pact23 krts_ctrl matches 1 if score #allyCall12 krts_ctrl matches 0 "
         "if score #g2 krts_ctrl matches 3 if score #unstable2 krts_ctrl matches 1",
         "RTS_ALLY_ACCEPTANCE_STARTS_OWN_WAR_NOTICE")
    send("scoreboard players set #clock23 krts_ctrl 1")
    test("if score #pact23 krts_ctrl matches 2", "RTS_ALLY_JOINED_WAR_BECOMES_ACTIVE")

    send("scoreboard players set #trade12 krts_ctrl 60", "scoreboard players set #unstable1 krts_ctrl 6",
         "scoreboard players set #g1 krts_ctrl 12")
    trigger(owner, "krts_betray", 2)
    wait_for(client_log, "İstikrarsızlık en yüksek düzeyde.", process)
    test("if score #allied12 krts_ctrl matches 1 if score #g1 krts_ctrl matches 12 if score #pact12 krts_ctrl matches 0",
         "RTS_ALLY_CRISIS_BLOCKS_BETRAYAL")
    send("scoreboard players set #unstable1 krts_ctrl 2")
    trigger(owner, "krts_betray", 2)
    wait_for(client_log, "İttifak ihlali. İstikrarsızlık 3 puan arttı.", process)
    test("if score #allied12 krts_ctrl matches 0 if score #pact12 krts_ctrl matches 1 if score #g1 krts_ctrl matches 8 "
         "if score #trade12 krts_ctrl matches 0 if score #unstable1 krts_ctrl matches 5",
         "RTS_ALLY_BETRAYAL_BREAKS_AGREEMENTS_AND_CHARGES_PENALTY")


def verify_specialization(test, process, server_log, client_log, home, diplomacy):
    x, y, z = home
    find_site, place = construction_orders(test, process, client_log, prefix="RTS_SPECIAL")

    def profile(value):
        send(f"scoreboard players set RtsQA1 krts_profile {value}")

    def stock(wood, stone, iron):
        send(*(f"scoreboard players set #{name}1 krts_ctrl {amount}"
               for name, amount in (("wood", wood), ("stone", stone), ("iron", iron))))

    for text in ("Tarla 18 sn · Cevher işi 4 sn", "Cevher işi 2 sn · Tarla 30 sn",
                 "Kervan ödemesi +1 altın", "Karakol 46 can", "İşçi menzili 24 blok"):
        wait_for(client_log, text, process)
    send("scoreboard players set #income_left krts_ctrl 1200", "scoreboard players set #g1 krts_ctrl 57",
         "scoreboard players set #wood1 krts_ctrl 4", "function krts:caravan/init",
         "scoreboard players set #caravanTime krts_ctrl 1")
    profile(3)
    test("if score #caravanOffer krts_ctrl matches 1", "RTS_PROFILE_TRADE_ORDER_READY")
    send("execute as RtsQA1 run trigger krts_menu", "scoreboard players get #caravanRound krts_ctrl",
         "say RTS_PROFILE_TRADE_TOKEN_READY")
    wait_for(client_log, "[Kervana 4 odun teslim et · 4 altın]", process)
    log = wait_for(server_log, "RTS_PROFILE_TRADE_TOKEN_READY", process)
    token = int(re.findall(r"#caravanRound has (\d+) \[krts_ctrl\]", log)[-1])
    send(f"execute as RtsQA1 run trigger krts_caravan set {token}")
    wait_for(client_log, "Kervan ödemesi hazineye sığmıyor.", process)
    test("if score #g1 krts_ctrl matches 57 if score #wood1 krts_ctrl matches 4", "RTS_PROFILE_TRADE_FULL_TREASURY_NO_DEBIT")
    send("scoreboard players set #g1 krts_ctrl 10")
    send(f"execute as RtsQA1 run trigger krts_caravan set {token}")
    test("if score #g1 krts_ctrl matches 14 if score #wood1 krts_ctrl matches 0", "RTS_PROFILE_TRADE_EXACT_BONUS")
    send("execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "[Karakol kur · 3 odun + 3 kırık taş]", process)
    post = find_site()
    stock(3, 2, 0)
    place(post)
    wait_for(client_log, "Karakol için 3 odun ve 3 kırık taş gerekir.", process)
    test("if score #wood1 krts_ctrl matches 3 if score #stone1 krts_ctrl matches 2 unless entity @e[tag=krts.post_core]",
         "RTS_PROFILE_TRADE_POST_SURCHARGE")
    stock(3, 3, 0)
    place(post)
    test("if score #posts1 krts_ctrl matches 1 if score #stone1 krts_ctrl matches 0 "
         "if entity @e[tag=krts.post_core,tag=krts.s1,nbt={Health:36.0d}]",
         "RTS_PROFILE_TRADE_PAYS_EXACT_BUILD_COST")
    send("damage @e[tag=krts.post_core,tag=krts.s1,limit=1] 100 minecraft:generic")
    test("if score #posts1 krts_ctrl matches 0", "RTS_PROFILE_TRADE_POST_REMOVED")

    profile(4)
    send("execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "Karakol 0/2 · Bağlı", process)
    for index in (1, 2):
        target = find_site()
        stock(3, 2, 0)
        place(target)
        test(f"if score #posts1 krts_ctrl matches {index} "
             f"if entity @e[tag=krts.post_core,tag=krts.post{index},tag=krts.s1,nbt={{Health:46.0d}}]",
             f"RTS_PROFILE_DEFENSE_POST_{index}_HAS_46_HEALTH")
    stock(3, 2, 0)
    place(post)
    wait_for(client_log, "Karakol sınırına ulaştın (2/2).", process)
    test("if score #posts1 krts_ctrl matches 2 if score #stone1 krts_ctrl matches 2 "
         "if score #wood1 krts_ctrl matches 3", "RTS_PROFILE_DEFENSE_THIRD_POST_REJECTED")
    send("execute as @e[tag=krts.post_core,tag=krts.s1] run damage @s 100 minecraft:generic")
    test("if score #posts1 krts_ctrl matches 0", "RTS_PROFILE_DEFENSE_POSTS_REMOVED")

    profile(1)
    source = (x + 22, y - 1, z)
    send("execute as RtsQA1 run trigger krts_job set 5")
    wait_for(client_log, "Üstten kamera açıkken görünen altın cevherine sağ tıkla.", process)
    native_source_click(source)
    wait_for(client_log, "Bu kaynak işçinin 16 blokluk çalışma alanı dışında.", process)
    profile(5)
    native_source_click(source)
    wait_for(client_log, "Bir altın cevheri bloğu seç.", process)
    send("execute as RtsQA1 run trigger krts_job set 2")
    test("if score RtsQA1 krts_wood_aim matches 0", "RTS_PROFILE_EXPLORATION_ACCEPTS_LONGER_REACH")
    stock(3, 2, 0)
    place(post)
    test("if entity @e[tag=krts.post_core,tag=krts.s1,nbt={Health:30.0d}]",
         "RTS_PROFILE_EXPLORATION_POST_HAS_30_HEALTH")
    send("damage @e[tag=krts.post_core,tag=krts.s1,limit=1] 100 minecraft:generic")
    test("if score #posts1 krts_ctrl matches 0", "RTS_PROFILE_EXPLORATION_POST_REMOVED")

    profile(2)
    field = "@e[tag=krts.field_anchor,tag=krts.s1,limit=1]"
    stock(3, 2, 0)
    place(find_site("Tarla"), "Tarla")
    test(f"if entity {field} if score #fields_linked1 krts_ctrl matches 1", "RTS_PROFILE_FIELD_BUILT")
    send("scoreboard players set #bread1 krts_ctrl 2",
         f"scoreboard players set {field} krts_field_work 24", "execute as RtsQA1 run trigger krts_npc set 8")
    test(f"if score {field} krts_field_work matches 25..29 if score #bread1 krts_ctrl matches 2",
         "RTS_PROFILE_MINING_FIELD_REQUIRES_LONGER_WORK")
    test("if score #bread1 krts_ctrl matches 3", "RTS_PROFILE_MINING_FIELD_DELIVERS", attempts=35)
    send("execute as RtsQA1 run trigger krts_npc set 9")
    wait_for(client_log, "Çiftçi durduruldu.", process)
    profile(1)
    send(f"scoreboard players set {field} krts_field_work 18", "execute as RtsQA1 run trigger krts_npc set 8")
    test("if score #bread1 krts_ctrl matches 4", "RTS_PROFILE_FARMING_FIELD_DELIVERS_AT_18", attempts=5)
    send("damage @e[tag=krts.field_core,tag=krts.s1,limit=1] 100 minecraft:generic")
    test("if score #fields1 krts_ctrl matches 0", "RTS_PROFILE_FIELD_REMOVED")

    academy = "@e[tag=krts.academy_anchor,tag=krts.s1,limit=1]"
    site = find_site("Talim Ocağı")
    stock(3, 2, 1)
    place(site, "Talim Ocağı")
    wait_for(client_log, "Talim Ocağı için 3 odun ve 2 kırık taş ve 2 demir gerekir.", process)
    test("if score #iron1 krts_ctrl matches 1 if score #wood1 krts_ctrl matches 3 "
         "if score #stone1 krts_ctrl matches 2 unless entity @e[tag=krts.academy_anchor]",
         "RTS_ACADEMY_IRON_SHORTAGE_NO_DEBIT")
    stock(3, 2, 2)
    place(site, "Talim Ocağı")
    test(f"if entity {academy} if score #academy_linked1 krts_ctrl matches 1 "
         "if score #wood1 krts_ctrl matches 0 if score #stone1 krts_ctrl matches 0 if score #iron1 krts_ctrl matches 0 "
         "if entity @e[type=block_display,tag=krts.academy_visual]",
         "RTS_ACADEMY_MODEL_AND_EXACT_BUILD_COST")
    if diplomacy:
        send("damage @e[tag=krts.academy_core,tag=krts.s1,limit=1] 4 minecraft:mob_attack by rtsdummy")
        test("if entity @e[tag=krts.academy_core,tag=krts.s1,nbt={Health:34.0d}]",
             "RTS_ACADEMY_NEUTRAL_DAMAGE_BLOCKED")
    trainee = "@e[tag=krts.qa_academy_trainee,limit=1]"
    send("tag @e[type=hundred_years_war:militia,tag=krts.s1,tag=krts.unit,limit=1] add krts.qa_academy_trainee",
         "execute as @e[tag=krts.unit,tag=krts.s1,tag=!krts.qa_academy_trainee] run data merge entity @s {CommandHold:1b}",
         f"execute at {academy} positioned ~-2 ~ ~3 positioned over motion_blocking_no_leaves run tp {trainee} ~ ~ ~",
         "scoreboard players set #iron1 krts_ctrl 1", "scoreboard players set #g1 krts_ctrl 10",
         "scoreboard players operation #qa_before_talim krts_ctrl = #left krts_ctrl",
         "execute as RtsQA1 run trigger krts_npc set 6")
    test(f"if score {trainee} krts_rank matches 1 if score #iron1 krts_ctrl matches 0 "
         "if score #g1 krts_ctrl matches 8", "RTS_ACADEMY_TRAINING_COST_AND_RANK", attempts=30)
    send("scoreboard players operation #qa_before_talim krts_ctrl -= #left krts_ctrl")
    test("if score #qa_before_talim krts_ctrl matches 9..17", "RTS_ACADEMY_FASTER_LOCAL_DRILL")
    send("damage @e[tag=krts.academy_core,tag=krts.s1,limit=1] 100 minecraft:generic")
    test(f"if score #academy_linked1 krts_ctrl matches 0 if score {trainee} krts_rank matches 1 "
         "unless entity @e[tag=krts.academy_part]", "RTS_ACADEMY_DESTRUCTION_PRESERVES_TRAINED_RANK")


def construction_orders(test, process, client_log, *, prefix="RTS"):
    def find_site(label="Karakol"):
        suggested = re.compile(rf"{label} yeri · X (-?\d+) · Y (-?\d+) · Z (-?\d+)")
        before = len(suggested.findall(client_log.read_text(encoding="utf-8")))
        action = {"Karakol": 3, "Mahalle": 5, "Tarla": 7, "Talim Ocağı": 9}[label]
        send(f"execute as RtsQA1 run trigger krts_build set {action}")
        until = time.monotonic() + 20
        while time.monotonic() < until:
            lines = suggested.findall(client_log.read_text(encoding="utf-8"))
            if len(lines) > before:
                return tuple(map(int, lines[-1]))
            if process.poll() is not None:
                break
            time.sleep(0.25)
        raise AssertionError(f"{label} için yer önerisi bulunamadı")

    def place(site, label="Karakol"):
        action, aim = {"Karakol": (1, 1), "Mahalle": (4, 2), "Tarla": (6, 3), "Talim Ocağı": (8, 4)}[label]
        send(f"execute as RtsQA1 run trigger krts_build set {action}")
        marker = {"Tarla": "FIELD_BUILD_CLICK_ARMED", "Talim Ocağı": "ACADEMY_BUILD_CLICK_ARMED"}.get(
            label, "BUILD_CLICK_ARMED")
        test(f"if score RtsQA1 krts_post_aim matches {aim}", f"{prefix}_{marker}_{next(attempt)}")
        x, y, z = site
        send(f'tellraw RtsQA1 {{"text":"RTS_WOOD_PICK {x} {y} {z}"}}')

    attempt = iter(range(1, 21))
    return find_site, place


def verify_field(test, process, client_log, home, diplomacy, outpost):
    x, y, z = home
    home_depot = f"{x+2.5} {y} {z+1.5}"
    field = "@e[tag=krts.field_anchor,tag=krts.s1,limit=1]"
    core = "@e[tag=krts.field_core,tag=krts.s1,limit=1]"
    farmer = "@e[tag=krts.farmer,tag=krts.field_part,tag=krts.s1,limit=1]"
    patient = "@e[tag=krts.qa_patient,limit=1]"
    send("execute as RtsQA1 run trigger krts_menu")
    wait_for(client_log, "Erzak 2/8", process)
    test("if score #bread1 krts_ctrl matches 2 if score #bread2 krts_ctrl matches 2 "
         "if score #bread3 krts_ctrl matches 2 unless entity @e[tag=krts.field_part] "
         "unless score RtsQA1 krts_nat_rations matches 1", "RTS_FIELD_TWO_INITIAL_RATIONS_WITHOUT_FIELD")
    send(f"tag @e[tag=krts.unit,tag=krts.s1,type=hundred_years_war:militia,limit=1] add krts.qa_patient",
         f"tp {patient} {home_depot}", f"data merge entity {patient} {{Health:10.0f,CommandHold:1b}}",
         f"damage {patient} 1 minecraft:generic",
         f"execute store result score #qa_before_ration krts_ctrl run data get entity {patient} Health 100",
         "scoreboard players operation #qa_after_ration krts_ctrl = #qa_before_ration krts_ctrl",
         "scoreboard players add #qa_after_ration krts_ctrl 400", "execute as RtsQA1 run trigger krts_npc set 10")
    wait_for(client_log, "Belediye dinlenen yaralı birliklere erzak verecek.", process)
    test(f"if score #bread1 krts_ctrl matches 2 if score {patient} krts_ration_hit matches 1..",
         "RTS_RATIONS_RECENT_DAMAGE_BLOCKS_HEALING")
    test(f"if score #bread1 krts_ctrl matches 1 if score #bread2 krts_ctrl matches 2 "
         f"if score {patient} krts_ration matches 1..", "RTS_RATIONS_ONE_STOCK_ONLY_FOR_OWNER", attempts=24)
    send(f"execute store result score #qa_current_ration krts_ctrl run data get entity {patient} Health 100")
    test("if score #qa_current_ration krts_ctrl = #qa_after_ration krts_ctrl", "RTS_RATIONS_EXACT_HEAL")
    send("execute as RtsQA1 run trigger krts_npc set 11")
    wait_for(client_log, "Erzak dağıtımı durduruldu.", process)

    find_site, place = construction_orders(test, process, client_log)
    site = find_site("Tarla")
    send("scoreboard players set #wood1 krts_ctrl 2", "scoreboard players set #stone1 krts_ctrl 2")
    place(site, "Tarla")
    wait_for(client_log, "Tarla için 3 odun ve 2 kırık taş gerekir.", process)
    test("if score #wood1 krts_ctrl matches 2 if score #stone1 krts_ctrl matches 2 "
         "unless entity @e[tag=krts.field_anchor]", "RTS_FIELD_INSUFFICIENT_STOCK_UNCHANGED")
    send("scoreboard players set #wood1 krts_ctrl 3")
    place(site, "Tarla")
    wait_for(client_log, "Tarla kuruldu.", process)
    test(f"if entity {farmer} if entity {core} if score {field} krts_post_connected matches 1 "
         "if score #wood1 krts_ctrl matches 0 if score #stone1 krts_ctrl matches 0 "
         "if score #bread1 krts_ctrl matches 1 unless score RtsQA1 krts_nat_farming matches 1",
         "RTS_FIELD_NATIVE_BUY_CREATES_FARM_AND_FARMER")
    place(site, "Tarla")
    wait_for(client_log, "Tarla sınırına ulaştın (1/1).", process)
    test("if score #fields1 krts_ctrl matches 1 if score #wood1 krts_ctrl matches 0",
         "RTS_FIELD_SECOND_FIELD_REJECTED")
    if diplomacy:
        send(f"damage {core} 4 minecraft:mob_attack by rtsdummy")
        test("if entity @e[tag=krts.field_core,tag=krts.s1,nbt={Health:26.0d}]",
             "RTS_FIELD_NEUTRAL_ATTACK_BLOCKED")
    send("execute as RtsQA1 run trigger krts_npc set 8")
    test(f"if score {field} krts_field_work matches 4.. "
         'if entity @e[tag=krts.field_crop,tag=krts.s1,nbt=!{block_state:{Properties:{age:"0"}}}]',
         "RTS_FIELD_CROPS_UPDATE_WHILE_FARMER_WORKS", attempts=30)
    if not outpost:
        # Move only the isolated QA capital's territory marker and return it to the original block.
        marker = "@e[tag=krts.wood_depot,tag=krts.s1,limit=1]"
        dx = -16 if site[0] >= x + 2 else 16
        dz = -16 if site[2] >= z + 1 else 16
        try:
            send(f"execute at {marker} run tp {marker} ~{dx} ~ ~{dz}")
            test(f"if score {field} krts_post_connected matches 0 if score #fields1 krts_ctrl matches 1 "
                 "if entity @e[tag=krts.farmer,tag=krts.s1,nbt={NoAI:1b}]",
                 "RTS_FIELD_CONNECTION_LOSS_STOPS_LIVE_FARM")
            send(f"scoreboard players operation #qa_field_cut krts_ctrl = {field} krts_field_work")
            time.sleep(2)
            test(f"if score {field} krts_field_work = #qa_field_cut krts_ctrl if score #bread1 krts_ctrl matches 1",
                 "RTS_FIELD_DISCONNECTED_PROGRESS_HELD")
        finally:
            send(f"tp {marker} {home_depot}")
        test(f"if score {field} krts_post_connected matches 1", "RTS_FIELD_CONNECTION_RESTORED")
    send("function krts:pause")
    test(f"if score #phase krts_ctrl matches 4 if entity @e[tag=krts.farmer,tag=krts.s1,nbt={{NoAI:1b}}]",
         "RTS_FIELD_FARMER_PAUSES")
    send(f"scoreboard players operation #qa_field_work krts_ctrl = {field} krts_field_work")
    time.sleep(2)
    test(f"if score {field} krts_field_work = #qa_field_work krts_ctrl if score #bread1 krts_ctrl matches 1",
         "RTS_FIELD_PAUSE_PRESERVES_PROGRESS_AND_STOCK")
    send("function krts:resume")
    test("if score #bread1 krts_ctrl matches 2 if score #bread2 krts_ctrl matches 2 "
         f"if score {field} krts_field_work matches 0..2", "RTS_FIELD_FARMER_DELIVERS_ONE_AFTER_RESUME", attempts=60)
    send("scoreboard players set #bread1 krts_ctrl 8")
    test("if entity @e[tag=krts.farmer,tag=krts.s1,nbt={NoAI:1b}] if score #bread1 krts_ctrl matches 8",
         "RTS_FIELD_FULL_DEPOT_STOPS_WORK")
    send(f"damage {core} 100 minecraft:generic")
    test("if score #fields1 krts_ctrl matches 0 if score #fields_linked1 krts_ctrl matches 0 "
         "if score #bread1 krts_ctrl matches 8 unless entity @e[tag=krts.field_part,tag=krts.s1]",
         "RTS_FIELD_DESTRUCTION_STOPS_PRODUCTION_AND_KEEPS_STOCK")


def verify_outpost(test, process, client_log, home, diplomacy, *, district=False):
    find_site, place = construction_orders(test, process, client_log)
    first = find_site()
    place(first)
    wait_for(client_log, "Karakol için 3 odun ve 2 kırık taş gerekir.", process)
    test("if score #wood1 krts_ctrl matches 3.. if score #stone1 krts_ctrl matches 0 "
         "unless entity @e[tag=krts.post_anchor]", "RTS_OUTPOST_NO_DEBIT_WHEN_UNAFFORDABLE")
    send("execute as RtsQA1 run trigger krts_npc set 3")
    test("if score #stone1 krts_ctrl matches 2.. if score #stone2 krts_ctrl matches 0",
         "RTS_OUTPOST_REAL_STONE_DELIVERED", attempts=90)
    send("execute as RtsQA1 run trigger krts_npc set 2")
    test("if score RtsQA1 krts_auto matches 0", "RTS_OUTPOST_STONE_WORK_STOPPED")
    x, y, z = home
    place((x, y - 1, z))
    wait_for(client_log, "Karakol bağlı sınırına bitişik olmalı", process)
    test("unless entity @e[tag=krts.post_anchor]", "RTS_OUTPOST_REJECTS_ALREADY_OWNED_GROUND")
    send("scoreboard players operation #qa_post_wood krts_ctrl = #wood1 krts_ctrl",
         "scoreboard players operation #qa_post_stone krts_ctrl = #stone1 krts_ctrl",
         "scoreboard players remove #qa_post_wood krts_ctrl 3",
         "scoreboard players remove #qa_post_stone krts_ctrl 2")
    place(first)
    wait_for(client_log, "Karakol kuruldu. Devletin bağlı toprağı genişledi.", process)
    test("if score #posts1 krts_ctrl matches 1 if score #linked1 krts_ctrl matches 1 "
         "if score #wood1 krts_ctrl = #qa_post_wood krts_ctrl if score #stone1 krts_ctrl = #qa_post_stone krts_ctrl "
         "if entity @e[tag=krts.post_core,tag=krts.s1,tag=krts.post1,nbt={Health:30.0d}] "
         "if entity @e[tag=krts.post_visual,tag=krts.s1,tag=krts.post1]", "RTS_OUTPOST_NATIVE_PURCHASE_AND_CLAIM")
    if diplomacy:
        # A neutral state cannot damage a live outpost through the registered HYW owner.
        send("damage @e[tag=krts.post_core,tag=krts.s1,limit=1] 4 minecraft:mob_attack by rtsdummy")
        test("if entity @e[tag=krts.post_core,tag=krts.s1,nbt={Health:30.0d}]", "RTS_OUTPOST_NEUTRAL_ATTACK_BLOCKED")
    home_depot = x + 2, z + 1
    second = find_site()
    if math.hypot(second[0] - home_depot[0], second[2] - home_depot[1]) <= 20:
        # This world has a buildable municipal frontier. Keep the second test on real terrain
        # while isolating graph behavior from another round of resource gathering.
        # Try a second game with farther territory elsewhere if the chosen seed has no chain plot.
        raise AssertionError(("Bu bölgede karakol zinciri zemini bulunamadı", first, second))
    send("scoreboard players set #wood1 krts_ctrl 3", "scoreboard players set #stone1 krts_ctrl 2")
    place(second)
    test("if score #posts1 krts_ctrl matches 2 if score #linked1 krts_ctrl matches 2 "
         "if score #wood1 krts_ctrl matches 0 if score #stone1 krts_ctrl matches 0",
         "RTS_OUTPOST_CONNECTED_CHAIN")
    send("damage @e[tag=krts.post_core,tag=krts.s1,tag=krts.post1,limit=1] 100 minecraft:generic")
    test("if score #posts1 krts_ctrl matches 1 if score #linked1 krts_ctrl matches 0 "
         "unless entity @e[tag=krts.post_visual,tag=krts.s1,tag=krts.post1] "
         "if entity @e[tag=krts.post_anchor,tag=krts.s1,tag=krts.post2,scores={krts_post_connected=0}]",
         "RTS_OUTPOST_CHAIN_BREAK_LOSES_UNCONNECTED_LAND")
    send("scoreboard players set #wood1 krts_ctrl 3", "scoreboard players set #stone1 krts_ctrl 2")
    place(first)
    test("if score #posts1 krts_ctrl matches 2 if score #linked1 krts_ctrl matches 2 "
         "if entity @e[tag=krts.post_anchor,tag=krts.s1,tag=krts.post2,scores={krts_post_connected=1}]",
         "RTS_OUTPOST_REBUILD_RESTORES_CONNECTION")
    if district:
        verify_district(test, process, client_log, home, first, find_site, place, diplomacy)


def verify_district(test, process, client_log, home, relay, find_site, place, diplomacy):
    units = "@e[tag=krts.unit,tag=krts.s1]"

    def count_units(holder):
        send(f"scoreboard players set {holder} krts_ctrl 0",
             f"execute as {units} run scoreboard players add {holder} krts_ctrl 1")

    district = find_site("Mahalle")
    count_units("#qa_district_base")
    send("scoreboard players operation #qa_district_limit_before krts_ctrl = #limit krts_ctrl",
         "scoreboard players operation #limit krts_ctrl = #qa_district_base krts_ctrl",
         "scoreboard players set #income_left krts_ctrl 1200", "scoreboard players set #g1 krts_ctrl 100",
         "scoreboard players set RtsQA1 krts_cool 0", "execute as RtsQA1 run trigger krts_buy set 2")
    wait_for(client_log, "Birlik sınırına ulaştın.", process)
    test("if score #g1 krts_ctrl matches 100 if score #housing1 krts_ctrl matches 0", "RTS_DISTRICT_BASE_CAP_BLOCKS_PURCHASE")
    send("scoreboard players set #wood1 krts_ctrl 3", "scoreboard players set #stone1 krts_ctrl 3")
    place(district, "Mahalle")
    wait_for(client_log, "Mahalle için 4 odun ve 3 kırık taş gerekir.", process)
    test("if score #wood1 krts_ctrl matches 3 if score #stone1 krts_ctrl matches 3 "
         "unless entity @e[tag=krts.district_anchor]", "RTS_DISTRICT_INSUFFICIENT_STOCK_UNCHANGED")
    send("scoreboard players set #wood1 krts_ctrl 4")
    place(district, "Mahalle")
    wait_for(client_log, "Mahalle kuruldu.", process)
    test("if score #districts1 krts_ctrl matches 1 if score #housing1 krts_ctrl matches 3 "
         "if score #housing2 krts_ctrl matches 0 if score #wood1 krts_ctrl matches 0 if score #stone1 krts_ctrl matches 0 "
         "if entity @e[tag=krts.district_visual,tag=krts.s1] if entity @e[tag=krts.district_core,tag=krts.s1,nbt={Health:32.0d}]",
         "RTS_DISTRICT_PURCHASE_ADDS_OWNER_CAPACITY")
    send("scoreboard players operation #qa_district_gold krts_ctrl = #g1 krts_ctrl",
         "scoreboard players operation #qa_district_gold krts_ctrl -= #militia_price krts_ctrl",
         "execute as RtsQA1 run trigger krts_buy set 2")
    test("if score #g1 krts_ctrl = #qa_district_gold krts_ctrl", "RTS_DISTRICT_NEW_RECRUIT_COST_CHARGED")
    count_units("#qa_district_live")
    test("if score #qa_district_live krts_ctrl > #qa_district_base krts_ctrl", "RTS_DISTRICT_ACTUAL_UNIT_OVER_OLD_LIMIT")
    if diplomacy:
        send("damage @e[tag=krts.district_core,tag=krts.s1,limit=1] 4 minecraft:mob_attack by rtsdummy")
        test("if entity @e[tag=krts.district_core,tag=krts.s1,nbt={Health:32.0d}]", "RTS_DISTRICT_NEUTRAL_ATTACK_BLOCKED")
    x, y, z = home
    detached = ("if score #housing1 krts_ctrl matches 0 if score #districts1 krts_ctrl matches 1 "
                "if entity @e[tag=krts.district_anchor,tag=krts.s1,scores={krts_post_connected=0}]")
    restored = ("if score #housing1 krts_ctrl matches 3 if score #districts1 krts_ctrl matches 1 "
                "if entity @e[tag=krts.district_anchor,tag=krts.s1,scores={krts_post_connected=1}]")
    if relay and math.hypot(district[0] - x - 2, district[2] - z - 1) > 12:
        send("damage @e[tag=krts.post_core,tag=krts.s1,tag=krts.post1,limit=1] 100 minecraft:generic")
        test(detached, "RTS_DISTRICT_CONNECTION_LOSS_SUSPENDS_CAPACITY")
        send("scoreboard players set #wood1 krts_ctrl 3", "scoreboard players set #stone1 krts_ctrl 2")
        place(relay)
    else:
        # The naturally clear district on this seed sits inside the municipal claim. Move only
        # the QA claim marker to exercise the same link calculation on the live construction.
        depot = "@e[tag=krts.wood_depot,tag=krts.s1,limit=1]"
        home_x, home_z = x + 2.5, z + 1.5
        dx, dz = home_x - district[0] - 0.5, home_z - district[2] - 0.5
        away_x = home_x + (16 if dx >= 0 else -16)
        away_z = home_z + (16 if dz >= 0 else -16)
        send(f"tp {depot} {away_x} {y} {away_z}")
        try:
            test(detached, "RTS_DISTRICT_CONNECTION_LOSS_SUSPENDS_CAPACITY")
        finally:
            send(f"tp {depot} {home_x} {y} {home_z}")
    test(restored, "RTS_DISTRICT_RELAY_RESTORES_CAPACITY")
    send("damage @e[tag=krts.district_core,tag=krts.s1,limit=1] 100 minecraft:generic")
    test("if score #housing1 krts_ctrl matches 0 if score #districts1 krts_ctrl matches 0 "
         "unless entity @e[tag=krts.district_visual]", "RTS_DISTRICT_DESTRUCTION_REMOVES_CAPACITY")
    send("scoreboard players set RtsQA1 krts_cool 0", "execute as RtsQA1 run trigger krts_buy set 2")
    # Wait for the trigger to be consumed by the normal purchase loop.
    test("if score RtsQA1 krts_buy matches 0 if score #g1 krts_ctrl = #qa_district_gold krts_ctrl",
         "RTS_DISTRICT_OVER_LIMIT_RECRUITMENT_REJECTED")
    count_units("#qa_district_after")
    test("if score #qa_district_after krts_ctrl = #qa_district_live krts_ctrl", "RTS_DISTRICT_EXISTING_UNITS_SURVIVE_LOSS")
    send("scoreboard players operation #limit krts_ctrl = #qa_district_limit_before krts_ctrl")


def verify_full_roster(test, process, server_log, *, field=False):
    send(*(f"player Founding{i} spawn" for i in range(4, 9)))
    wait_for(server_log, "founding8 joined the game", process)
    send("execute as RtsQA1 run trigger krts_found_start set 1")
    test("if score #found_left krts_ctrl matches 169..180", "RTS_NATURAL_EIGHT_PLAYER_DRAFT")
    send("execute as founding4 run trigger krts_found_origin set 2",
         "execute as founding5 run trigger krts_found_origin set 3")
    test("if score founding4 krts_origin matches 2 if score founding5 krts_origin matches 3",
         "RTS_NATURAL_OTHER_ORIGINS_AVAILABLE")
    send("scoreboard players set #found_left krts_ctrl 1")
    test("if score #phase krts_ctrl matches 2 if score #slot krts_ctrl matches 8 "
         "if score #a8 krts_ctrl matches 1 if entity @e[tag=krts.capital,tag=krts.s8]",
         "RTS_NATURAL_EIGHT_CAPITALS")
    if field:
        test(" ".join(f"if score #bread{slot} krts_ctrl matches 2" for slot in range(1, 9)),
             "RTS_FIELD_EIGHT_STATES_HAVE_EQUAL_STARTING_STOCK")
    test('if entity @e[tag=krts.townhall,nbt={block_state:{Name:"minecraft:nether_bricks"}}] '
         'if entity @e[tag=krts.townhall,nbt={block_state:{Name:"minecraft:cobblestone"}}]',
         "RTS_NATURAL_NETHER_AND_RAIDER_HALLS")
    send("function krts:reset")
    test("if score #phase krts_ctrl matches 0 unless entity @e[tag=krts.capital]",
         "RTS_NATURAL_EIGHT_PLAYER_RESET")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--match", action="store_true", help="Kuruluştan sonra doğal Başkent maçını da doğrula.")
    parser.add_argument("--diplomacy", action="store_true", help="Doğal maçı Kurallı Diplomasi ile doğrula.")
    parser.add_argument("--full-roster", action="store_true", help="Sekiz devletli doğal açılışı da doğrula.")
    parser.add_argument("--with-orders", action="store_true", help="Java istemcisinden HYW hareket emri gönder.")
    parser.add_argument("--with-wood", action="store_true", help="Doğal oduncunun kaynaktan depoya çalışmasını doğrula.")
    args = parser.parse_args()
    if (args.diplomacy or args.full_roster or args.with_orders or args.with_wood) and not args.match:
        parser.error("Doğal maç seçenekleri için --match gerekir.")
    sys.path.insert(0, str(RTS.parents[1] / "prototypes/devletler-rts"))
    from prepare import MOD, build_adapter, make_pack, read_rules

    with tempfile.TemporaryDirectory(prefix="kapisma-rts-terrain-") as temporary:
        adapter = Path(temporary) / "kapisma-rts-rules.jar"
        pack = Path(temporary) / "devletler-rts.zip"
        build_adapter(RTS / "mods" / MOD, adapter)
        rules = {**read_rules(), "ruleset": "diplomacy" if args.diplomacy else "classic"}
        make_pack(pack, rules, survey_only=not args.match, natural_match=args.match)
        formation_jar = None
        if args.with_orders:
            from verify_formation import build

            formation_jar = build(Path(temporary))
        verify(adapter, pack, match=args.match, diplomacy=args.diplomacy, full_roster=args.full_roster,
               formation_jar=formation_jar, wood=args.with_wood)
