"""Exercise owner-controlled patrol, manual command precedence and diplomatic alerts."""
import time

from verify_local import run_server
from verify_npc import LAB, OWNER, PEER, check, spawn

GUARD = "@e[tag=krts.unit,tag=krts.s1,scores={krts_guarding=1},limit=1]"
MANUAL = "@e[tag=krts.qa_manual,limit=1]"
ENEMY = "@e[tag=krts.unit,tag=krts.s2,type=hundred_years_war:militia,limit=1]"
POST = "@e[tag=krts.guardpost,tag=krts.s1]"


def check_classic_defense(server):
    server.send("function krts:resume")
    server.expect("Maç devam ediyor.")
    server.send(f"execute as {OWNER} run trigger krts_job set 8")
    server.send(f"execute as {OWNER} run trigger krts_watch set 1")
    server.send(f"execute as {OWNER} run trigger krts_npc set 1")
    check(server, f"if entity {GUARD} if score #alert1 krts_ctrl matches 0",
          "DEFENSE_CLASSIC_PATROL_AVAILABLE", attempts=8)
    server.send(f"tp {ENEMY} 11.5 -60 -64.5")
    server.send(f"data merge entity {ENEMY} {{NoAI:1b,Invulnerable:1b}}")
    check(server, "if score #alert1 krts_ctrl matches 1", "DEFENSE_CLASSIC_RECOGNIZES_RIVAL", attempts=5)


def complete_defense(candidate):
    with run_server("npc_defense_test.log", directory=LAB, extra_mods=(candidate,)) as server:
        server.send("function krts:reset")
        spawn(server, OWNER)
        spawn(server, PEER)
        server.send(f"tag {PEER} add krts.host")
        server.send("function krts:start")
        server.expect("Hazırlık başladı.")
        check(server, f"if score #strict krts_ctrl matches 1 if score {OWNER} krts_slot matches 1 "
                      f"if score {OWNER} krts_defense matches 0", "DEFENSE_DEFAULT_OFF")
        time.sleep(1.3)
        server.send(f"execute as {OWNER} run trigger krts_job set 8")
        server.send(f"execute as {OWNER} run trigger krts_watch set 2")
        check(server, f"if score {OWNER} krts_focus matches 0 if score {OWNER} krts_defense matches 2 "
                      f"if score {PEER} krts_defense matches 0 unless entity {POST}",
              "DEFENSE_OWNER_CHOICE_WITH_MASTER_OFF", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_npc set 1")
        check(server, f"if entity {POST} unless entity @e[tag=krts.guardpost,tag=krts.s2] "
                      "unless entity @e[tag=krts.yard] unless entity @e[tag=krts.farm] "
                      f"unless entity {GUARD}", "DEFENSE_PREPARATION", attempts=5)
        server.send(f"execute as {PEER} run trigger krts_watch set 3")
        check(server, f"if score {PEER} krts_watch matches 0 if score {OWNER} krts_defense matches 2",
              "DEFENSE_HOST_SETTING_LEAVES_PEER_UNCHANGED", attempts=5)
        server.send("fill -6 -60 -70 -4 -58 -68 barrier")
        server.send("scoreboard players set #left krts_ctrl 1")
        server.expect("Merkez açık.")
        try:
            check(server, f"if score {GUARD} krts_route matches 2", "DEFENSE_PATROL_SKIPPED_BLOCKED_WAYPOINT", attempts=22)
        finally:
            server.send("fill -6 -60 -70 -4 -58 -68 air")
        check(server, f"if score {GUARD} krts_route matches 0", "DEFENSE_PATROL_COMPLETES_ROUTE", attempts=22)
        server.send("execute store result score #qaGuards krts_ctrl if entity "
                    "@e[tag=krts.unit,tag=krts.s1,scores={krts_guarding=1}]")
        check(server, "if score #qaGuards krts_ctrl matches 1", "DEFENSE_ONE_ACTIVE_GUARD")

        server.send(f"tag {GUARD} add krts.qa_manual")
        server.send(f"data merge entity {MANUAL} {{CommandHold:1b}}")
        check(server, f"if score {MANUAL} krts_guarding matches 0 "
                      "if entity @e[tag=krts.qa_manual,nbt={CommandHold:1b}] "
                      "if entity @e[tag=krts.unit,tag=krts.s1,tag=!krts.qa_manual,scores={krts_guarding=1}]",
              "DEFENSE_MANUAL_HOLD_TAKES_PRIORITY", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_watch set 1")
        check(server, f"if score {OWNER} krts_defense matches 1 "
                      "if entity @e[tag=krts.s1,scores={krts_guarding=1},x=2.5,y=-60,z=-63.5,distance=..1.35]",
              "DEFENSE_WALKED_TO_FIXED_POST", attempts=15)

        server.send(f"tp {ENEMY} 11.5 -60 -64.5")
        server.send(f"data merge entity {ENEMY} {{NoAI:1b,Invulnerable:1b}}")
        time.sleep(1.3)
        check(server, "if score #alert1 krts_ctrl matches 0", "DEFENSE_NEUTRAL_UNIT_IGNORED")
        server.send("scoreboard players set #g1 krts_ctrl 16")
        server.send(f"execute as {OWNER} run trigger krts_war set 2")
        server.expect("savaş 15 saniye sonra başlar.")
        time.sleep(1.3)
        check(server, "if score #alert1 krts_ctrl matches 0", "DEFENSE_WARNING_UNIT_IGNORED")
        server.send("scoreboard players set #clock12 krts_ctrl 1")
        server.expect("artık savaşta.")
        check(server, "if score #alert1 krts_ctrl matches 1 if score #alert2 krts_ctrl matches 0",
              "DEFENSE_WAR_ALERT_OWNER_ONLY", attempts=5)
        server.send("scoreboard players set #clock12 krts_ctrl 1")
        time.sleep(2)
        server.send(f"execute as {OWNER} run trigger krts_peace set 2")
        server.send(f"execute as {PEER} run trigger krts_peace set 1")
        server.expect("45 saniyelik ateşkes yaptı.")
        check(server, "if score #alert1 krts_ctrl matches 0", "DEFENSE_TRUCE_CLEARS_ALERT", attempts=5)

        server.send("scoreboard players set #income_left krts_ctrl 150")
        server.send("scoreboard players set #g1 krts_ctrl 0")
        server.send(f"execute as {OWNER} run trigger krts_job set 2")
        check(server, f"if score {OWNER} krts_focus matches 2 if score #iron1 krts_ctrl matches 0",
              "DRILL_CAN_START_WITHOUT_MINING", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_npc set 5")
        check(server, f"if score {OWNER} krts_npc matches 0 if score {OWNER} krts_drill_buy matches 0 "
              "if score #g1 krts_ctrl matches 0 if score #iron1 krts_ctrl matches 0",
              "DRILL_CANNOT_BUY_WITHOUT_GOLD", attempts=5)
        server.send("scoreboard players set #g1 krts_ctrl 8")
        server.send(f"execute as {OWNER} run trigger krts_npc set 5")
        check(server, "if score #g1 krts_ctrl matches 5 if score #iron1 krts_ctrl matches 1 "
              "if score #iron2 krts_ctrl matches 0", "DRILL_PURCHASE_OWN_IRON_ONLY", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_npc set 5")
        check(server, "if score #g1 krts_ctrl matches 2 if score #iron1 krts_ctrl matches 2",
              "DRILL_PURCHASE_STACKS_TWO_INGOTS", attempts=5)
        server.send(f"data merge entity {MANUAL} {{CommandHold:0b,FormationHold:0b}}")
        check(server, f"if entity {GUARD} if score {MANUAL} krts_training matches 1 "
              f"unless score {GUARD} krts_training matches 1", "DRILL_AND_GUARD_ASSIGN_DIFFERENT_SOLDIERS", attempts=10)

        server.send("function krts:pause")
        server.expect("Puan, süre ve satın alma durdu.")
        check(server, f"unless entity {GUARD} if score {OWNER} krts_defense matches 1",
              "DEFENSE_PAUSE_RELEASES_GUARD", attempts=5)
        check(server, f"if score {MANUAL} krts_training matches 0 if score #iron1 krts_ctrl matches 2 "
              "if score #g1 krts_ctrl matches 2", "DRILL_PAUSE_RELEASES_WITHOUT_CHARGING", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_watch set 3")
        check(server, f"if score {OWNER} krts_defense matches 0 if score {OWNER} krts_auto matches 1",
              "DEFENSE_INDEPENDENT_SWITCH_OFF", attempts=5)
        server.send("scoreboard players set #g1 krts_ctrl 16")
        server.send("function krts:resume")
        server.expect("Maç devam ediyor.")
        server.send(f"execute as {OWNER} run trigger krts_tech set 4")
        check(server, f"if score {OWNER} krts_tech matches 0 if score #doctrine1 krts_ctrl matches 0 "
              "if score #g1 krts_ctrl matches 16", "TECH_DOCTRINE_REQUIRES_ARCHERY", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_tech set 2")
        check(server, "if score #bow1 krts_ctrl matches 1 if score #g1 krts_ctrl matches 9",
              "TECH_ARCHERY_PURCHASED", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_tech set 4")
        check(server, "if score #doctrine1 krts_ctrl matches 1 if score #doctrine2 krts_ctrl matches 0 "
              "if score #g1 krts_ctrl matches 0", "TECH_DOCTRINE_OWNER_ONLY", attempts=5)
        server.send(f"execute as @e[tag=krts.unit,tag=krts.s1,tag=!krts.qa_manual] run data merge entity @s {{CommandHold:1b}}")
        server.send(f"tp {MANUAL} 6.5 -60 -60.5")
        server.send(f"scoreboard players set {MANUAL} krts_drill 14")
        server.send("scoreboard players set #g1 krts_ctrl 2")
        check(server, f"if score {MANUAL} krts_rank matches 1 if score {MANUAL} krts_drill matches 0 "
              "if score #iron1 krts_ctrl matches 1 if score #g1 krts_ctrl matches 0",
              "TECH_DOCTRINE_RANKS_AT_FIFTEEN_WITH_NORMAL_COST", attempts=4)
        server.send("function krts:reset")
        server.expect("Prototip maçı sıfırlandı.")
        check(server, "unless entity @e[tag=krts.guardpost]", "DEFENSE_RESET_REMOVES_POST")
