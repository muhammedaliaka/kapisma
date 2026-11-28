"""Check ownership, physical production, training, pause and persistence in the RTS lab."""
from pathlib import Path
import argparse
import msvcrt
import os
import shutil
import socket
import sys
import tempfile
import time

from verify_local import ROOT, PROJECT, run_server, scenario
from laboratuvar.settings import LAB_PORT

LAB = ROOT / "laboratuvar/server"
PACK = LAB / "world/datapacks/devletler-rts.zip"
ADAPTER = ROOT / "mods/kapisma-rts-rules.jar"
OWNER = "NpcOne"
PEER = "NpcTwo"
WORKER_DISABLED = "@e[tag=krts.worker,tag=krts.s1,limit=1,nbt={NoAI:1b}]"
WORKER = "@e[tag=krts.worker,tag=krts.s1,limit=1]"
MILITIA = "@e[tag=krts.unit,tag=krts.s1,type=hundred_years_war:militia,limit=1]"
ARCHER = "@e[tag=krts.unit,tag=krts.s1,type=hundred_years_war:archer,limit=1]"


def check(server, command, marker, *, attempts=1):
    for attempt in range(attempts):
        server.send(f"execute {command} run say {marker}")
        server.send(f"say {marker}_PROBE_END")
        server.expect(f"{marker}_PROBE_END")
        latest = Path(server.log.name).read_text(encoding="utf-8")
        if any(line.endswith(f"[Server] {marker}") for line in latest.splitlines()):
            print(f"PASS {marker}", flush=True)
            return
        if attempt + 1 < attempts:
            time.sleep(1)
    raise AssertionError(f"Beklenen oyun durumu oluşmadı: {marker}")


def spawn(server, player):
    server.send(f"player {player} spawn")
    server.expect(f"{player} joined the game")
    server.send(f"gamemode adventure {player}")


def complete_match(candidate, *, with_defense=False):
    mods = (candidate,)
    with run_server("npc_playable_test.log", directory=LAB, extra_mods=mods) as server:
        server.send("function krts:reset")
        spawn(server, OWNER)
        spawn(server, PEER)
        server.send(f"tag {PEER} add krts.host")
        server.send("function krts:start")
        server.expect("Hazırlık başladı.")
        check(server, f"if score {OWNER} krts_slot matches 1 if score {PEER} krts_slot matches 2", "NPC_REGISTERED")
        check(server, f"if score {OWNER} krts_auto matches 0 if score {PEER} krts_auto matches 0", "NPC_DEFAULT_OFF")
        time.sleep(1.3)
        server.send(f"execute as {OWNER} run trigger krts_npc set 1")
        check(server, f"if score {OWNER} krts_auto matches 1 if score {PEER} krts_auto matches 0", "NPC_OWNER_ONLY", attempts=4)
        server.send(f"execute as {PEER} run trigger krts_npc set 2")
        check(server, f"if score {OWNER} krts_auto matches 1 if score {PEER} krts_auto matches 0 "
              f"if score {PEER} krts_npc matches 0", "NPC_HOST_CAN_ONLY_SET_SELF", attempts=4)
        server.send(f"execute as {OWNER} run trigger krts_job set 1")
        check(server, f"if score {OWNER} krts_focus matches 1 if entity {WORKER_DISABLED}",
              "NPC_PREPARATION_STOPPED", attempts=4)
        server.send("setblock 11 -60 -67 barrier")
        server.send("scoreboard players set #left krts_ctrl 1")
        server.expect("Merkez açık.")
        check(server, "if score #iron1 krts_ctrl matches 1.. if block 8 -60 -63 chest",
              "NPC_MINED_SMELTED_STORED", attempts=50)
        check(server, "unless data block 8 -60 -62 Items[{Slot:2b}]",
              "NPC_FURNACE_OUTPUT_REMOVED_ON_PICKUP")
        server.send(f"data get entity {WORKER} HandItems")
        server.expect('id: "minecraft:iron_pickaxe"')
        check(server, f'if data entity {WORKER} HandItems[0].components."minecraft:damage"', "NPC_PICKAXE_WORN")
        check(server, "if block 12 -60 -67 iron_ore", "NPC_SKIPPED_BLOCKED_VEIN")
        server.send("data get block 8 -60 -63 Items")
        server.expect('id: "minecraft:iron_ingot"')
        check(server, "unless entity @e[tag=krts.yard,tag=krts.s2]", "NPC_PEER_YARD_UNCHANGED")
        server.send("setblock 11 -60 -64 barrier")
        server.send("setblock 12 -60 -64 air")
        server.send("scoreboard players set #renew1 krts_ctrl 29")
        check(server, "if block 12 -60 -64 iron_ore if block 12 -60 -67 iron_ore",
              "NPC_DEPLETED_VEIN_RENEWED_DESPITE_BLOCKED_VEIN", attempts=17)
        server.send("function krts:pause")
        server.expect("Puan, süre ve satın alma durdu.")
        check(server, f"if entity {WORKER_DISABLED}", "NPC_WORKER_PAUSED", attempts=4)
        server.send("function krts:resume")
        server.expect("Maç devam ediyor.")

        server.send("scoreboard players set #income_left krts_ctrl 150")
        server.send(f"data merge entity {ARCHER} {{CommandHold:1b}}")
        server.send(f"tp {MILITIA} 0.5 -60 -63.5")
        server.send(f"execute as {OWNER} run trigger krts_job set 2")
        check(server, f"if score {OWNER} krts_focus matches 2 if entity {WORKER_DISABLED}",
              "NPC_MINER_STOPS_ON_DRILL_MODE", attempts=4)
        server.send("scoreboard players operation #afterDrillGold krts_ctrl = #g1 krts_ctrl")
        server.send("scoreboard players remove #afterDrillGold krts_ctrl 2")
        server.send("scoreboard players operation #afterDrillIron krts_ctrl = #iron1 krts_ctrl")
        server.send("scoreboard players remove #afterDrillIron krts_ctrl 1")
        server.send(f"execute store result score #base_hp krts_ctrl run attribute {MILITIA} minecraft:generic.max_health get 100")
        check(server, f"if score {MILITIA} krts_training matches 1 if score {MILITIA} krts_drill matches 2..10 "
              "if entity @e[tag=krts.unit,tag=krts.s1,type=hundred_years_war:militia,"
              "x=6.5,y=-60,z=-60.5,distance=..1.35,limit=1] "
              f"unless score {ARCHER} krts_training matches 1", "DRILL_WALKED_TO_MUSTER", attempts=16)
        server.send(f"data merge entity {MILITIA} {{CommandHold:1b}}")
        check(server, f"if score {MILITIA} krts_training matches 0", "DRILL_EXPLICIT_ORDER_RELEASES_TRAINEE", attempts=5)
        server.send(f"scoreboard players operation #pausedDrill krts_ctrl = {MILITIA} krts_drill")
        time.sleep(2.2)
        check(server, f"if score {MILITIA} krts_drill = #pausedDrill krts_ctrl if score {MILITIA} krts_drill matches 2..",
              "DRILL_HOLD_PRESERVES_PROGRESS")
        server.send(f"data merge entity {MILITIA} {{CommandHold:0b,FormationHold:0b}}")
        check(server, f"if score {MILITIA} krts_rank matches 1..", "NPC_UNIT_TRAINED", attempts=30)
        server.send(f"execute store result score #trained_hp krts_ctrl run attribute {MILITIA} minecraft:generic.max_health get 100")
        check(server, "if score #trained_hp krts_ctrl > #base_hp krts_ctrl", "NPC_TRAINING_ATTRIBUTE_INCREASED")
        check(server, "if score #g1 krts_ctrl = #afterDrillGold krts_ctrl "
              "if score #iron1 krts_ctrl = #afterDrillIron krts_ctrl", "DRILL_EXACT_RANK_COST")

        server.send(f"execute as {OWNER} run trigger krts_npc set 2")
        check(server, f"if score {OWNER} krts_auto matches 0 if score {PEER} krts_auto matches 0",
              "NPC_OWNER_SWITCHED_OFF", attempts=4)
        server.send(f"data merge entity {ARCHER} {{CommandHold:0b,FormationHold:0b}}")

    with run_server("npc_persistence_test.log", directory=LAB, extra_mods=mods) as server:
        server.expect("Puan, süre ve satın alma durdu.")
        spawn(server, OWNER)
        spawn(server, PEER)
        check(server, f"if score {OWNER} krts_auto matches 0 if score {OWNER} krts_focus matches 2 "
              f"if score {MILITIA} krts_rank matches 1.. if block 8 -60 -63 chest", "NPC_STATE_PERSISTED")
        if with_defense:
            from verify_defense import check_classic_defense
            check_classic_defense(server)
        server.send("function krts:reset")
        server.expect("Prototip maçı sıfırlandı.")
        check(server, "unless entity @e[tag=krts.worker] unless entity @e[tag=krts.yard] if block 8 -60 -63 air",
              "NPC_MATCH_CLEANUP")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--with-supply", action="store_true")
    parser.add_argument("--supply-only", action="store_true")
    parser.add_argument("--with-defense", action="store_true")
    parser.add_argument("--defense-only", action="store_true")
    parser.add_argument("--with-diplomacy", action="store_true")
    parser.add_argument("--with-natural", action="store_true")
    parser.add_argument("--natural-only", action="store_true")
    parser.add_argument("--hall-image", action="store_true")
    parser.add_argument("--manual-proof", action="store_true")
    parser.add_argument("--outpost-proof", action="store_true")
    parser.add_argument("--district-proof", action="store_true")
    parser.add_argument("--iron-proof", action="store_true")
    parser.add_argument("--field-proof", action="store_true")
    parser.add_argument("--field-only", action="store_true", help="Doğal tarla ve ikmal akışını iki kuralda sınar.")
    parser.add_argument("--economy-only", action="store_true", help="Doğal altını ve kervan siparişlerini iki kuralda sınar.")
    parser.add_argument("--diplomacy-only", action="store_true")
    parser.add_argument("--bank-only", action="store_true")
    parser.add_argument("--natural-bank", action="store_true", help="Doğal Kurallı Diplomasi bankasını ve kayıttan dönüşü sınar.")
    parser.add_argument("--alliances-proof", action="store_true", help="Doğal ittifak, yardım ve savaş çağrılarını sınar.")
    parser.add_argument("--specialization-proof", action="store_true", help="Beş profili ve Talim Ocağı'nı sınar.")
    options = parser.parse_args()
    focused = (options.field_only or options.economy_only or options.natural_bank
               or options.alliances_proof or options.specialization_proof)
    natural = options.with_natural or options.natural_only or focused
    field = options.field_proof or options.field_only
    outpost = options.outpost_proof or options.district_proof
    wood = not focused or outpost or options.manual_proof
    economy = options.economy_only
    if (options.hall_image or options.manual_proof or outpost or options.iron_proof or field) and not natural:
        parser.error("Screenshot and gameplay proofs require a natural match")
    if options.bank_only and options.install:
        parser.error("--bank-only is an isolated trial")
    sys.path.insert(0, str(PROJECT / "prototypes/devletler-rts"))
    from prepare import build_adapter, make_pack, read_rules, write_launchers

    try:
        connection = socket.create_connection(("127.0.0.1", LAB_PORT), timeout=0.5)
    except OSError:
        pass
    else:
        connection.close()
        raise RuntimeError("Önce çalışan RTS laboratuvarını kapatın.")

    with tempfile.TemporaryDirectory(prefix="kapisma-rts-npc-") as directory:
        temp = Path(directory)
        candidate = temp / ADAPTER.name
        build_adapter(next((ROOT / "mods").glob("Hundred*.jar")), candidate)
        rules = read_rules()
        for preset in ("classic", "diplomacy"):
            make_pack(temp / f"preset-{preset}.zip", {**rules, "ruleset": preset})
        make_pack(temp / "devletler-rts.zip", rules)
        if not (options.supply_only or options.defense_only or options.diplomacy_only or options.bank_only
                or options.natural_only or focused):
            shutil.copy2(temp / "preset-classic.zip", PACK)
            complete_match(candidate, with_defense=options.with_defense)
        if options.with_supply or options.supply_only:
            from verify_supply import complete_supply
            shutil.copy2(temp / "preset-diplomacy.zip", PACK)
            complete_supply(candidate)
        if options.with_defense or options.defense_only:
            from verify_defense import complete_defense
            shutil.copy2(temp / "preset-diplomacy.zip", PACK)
            complete_defense(candidate)
        if options.with_diplomacy or options.diplomacy_only:
            shutil.copy2(temp / "preset-diplomacy.zip", PACK)
            scenario("diplomacy_game", directory=LAB, extra_mods=(candidate,), suffix="combinations")
        if options.bank_only or options.natural_bank:
            from verify_bank import complete_bank
            make_pack(temp / "bank-trial.zip", {**rules, "ruleset": "diplomacy"}, banking_trial=True)
            previous_pack = PACK.read_bytes()
            try:
                shutil.copy2(temp / "bank-trial.zip", PACK)
                complete_bank(candidate)
            finally:
                PACK.write_bytes(previous_pack)
        if natural:
            sys.path.insert(0, str(ROOT / "laboratuvar"))
            from verify_terrain import verify

            for preset in ("classic", "diplomacy"):
                make_pack(temp / f"preset-natural-{preset}.zip",
                          {**rules, "ruleset": preset, "victory_mode": "capital"}, natural_match=True)
            qa_jar = None
            if options.hall_image or options.manual_proof or outpost or options.iron_proof or field or economy or options.specialization_proof:
                from verify_formation import build

                qa_jar = build(temp)
            verify(candidate, temp / "preset-natural-classic.zip", match=True, full_roster=True, wood=wood,
                   hall_jar=qa_jar if options.hall_image else None,
                   manual_jar=qa_jar if options.manual_proof or outpost or options.iron_proof or field or economy or options.specialization_proof else None,
                   outpost=outpost, district=options.district_proof, iron=options.iron_proof, field=field, economy=economy,
                   bank=options.natural_bank, alliances=options.alliances_proof, specialization=options.specialization_proof)
            verify(candidate, temp / "preset-natural-diplomacy.zip", match=True, diplomacy=True, wood=wood,
                   manual_jar=qa_jar if outpost or options.iron_proof or field or economy or options.specialization_proof else None, outpost=outpost,
                   district=options.district_proof, iron=options.iron_proof, field=field, economy=economy,
                   bank=options.natural_bank, alliances=options.alliances_proof, specialization=options.specialization_proof)
        if options.install:
            with (ROOT / "rts-launch.lock").open("a+b") as lock:
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
                for preset in ("classic", "diplomacy"):
                    os.replace(temp / f"preset-{preset}.zip", ROOT / f"preset-{preset}.zip")
                os.replace(temp / "devletler-rts.zip", ROOT / "world/datapacks/devletler-rts.zip")
                os.replace(candidate, ADAPTER)
                if natural:
                    for preset in ("classic", "diplomacy"):
                        name = f"preset-natural-{preset}.zip"
                        os.replace(temp / name, ROOT / name)
                    write_launchers()
            print("PASS Doğrulanan NPC sürümü RTS profiline uygulandı.", flush=True)


if __name__ == "__main__":
    main()
