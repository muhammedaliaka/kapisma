"""Exercise physical wheat, stock and owner-only deliveries in the Diplomacy preset."""
import time

from verify_local import run_server
from verify_npc import LAB, OWNER, PEER, MILITIA, check, spawn

FARMER = "@e[limit=1,tag=krts.farmer,tag=krts.s1]"
ENEMY = "@e[tag=krts.unit,tag=krts.s2,type=hundred_years_war:militia,limit=1]"


def complete_supply(candidate):
    with run_server("npc_supply_test.log", directory=LAB, extra_mods=(candidate,)) as server:
        server.send("function krts:reset")
        server.send("scoreboard players set #iron1 krts_ctrl 7")
        server.send("scoreboard players set #bread1 krts_ctrl 8")
        spawn(server, OWNER)
        spawn(server, PEER)
        server.send("function krts:start")
        server.expect("Hazırlık başladı.")
        check(server, f"if score #strict krts_ctrl matches 1 if score {OWNER} krts_slot matches 1", "SUPPLY_DIPLOMACY")
        check(server, "if score #iron1 krts_ctrl matches 0 if score #bread1 krts_ctrl matches 0", "SUPPLY_NEW_MATCH_STOCK_RESET")
        time.sleep(1.3)
        server.send(f"execute as {OWNER} run trigger krts_job set 4")
        check(server, f"if score {OWNER} krts_focus matches 4", "SUPPLY_PLAYER_CHOICE", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_npc set 1")
        check(server, f"if entity {FARMER} unless entity @e[tag=krts.worker,tag=krts.s1] "
                      "unless entity @e[tag=krts.farm,tag=krts.s2]", "SUPPLY_CREATES_OWN_FARM_ONLY", attempts=5)
        server.send("scoreboard players set #left krts_ctrl 1")
        server.expect("Merkez açık.")
        check(server, "if score #bread1 krts_ctrl matches 1.. if block -8 -60 -62 chest",
              "SUPPLY_HARVESTED_AND_STORED", attempts=40)
        server.send("data get block -8 -60 -62 Items")
        server.expect('id: "minecraft:bread"')
        server.send("scoreboard players set #testRipe krts_ctrl 0")
        for x in (-11, -13):
            for z in (-65, -64, -63):
                server.send(f"execute if block {x} -60 {z} wheat[age=7] run scoreboard players add #testRipe krts_ctrl 1")
        check(server, "if score #testRipe krts_ctrl matches ..3 if block -11 -61 -64 farmland",
              "SUPPLY_REAL_WHEAT_HARVESTED")
        check(server, "if score #bread1 krts_ctrl matches 2", "SUPPLY_BREAD_STOCK_ACCUMULATES", attempts=35)
        server.send("gamerule randomTickSpeed 0")
        for x in (-11, -13):
            for z in (-65, -64, -63):
                server.send(f"setblock {x} -60 {z} wheat[age=0]")
        server.send(f"scoreboard players set {FARMER} krts_crop 0")
        server.send(f"tp {MILITIA} -5.5 -60 -65.5")
        server.send(f"tp {ENEMY} -6.5 -60 -67.5")
        server.send(f"data merge entity {MILITIA} {{Health:10.0d,NoAI:1b}}")
        server.send(f"data merge entity {ENEMY} {{Health:10.0d,NoAI:1b}}")
        try:
            check(server, f"if score {MILITIA} krts_ration matches 1..", "SUPPLY_RATION_DELIVERED", attempts=18)
            check(server, "if score #bread1 krts_ctrl matches 1", "SUPPLY_ONE_BREAD_CONSUMED")
        finally:
            server.send("gamerule randomTickSpeed 3")
        check(server, 'if entity ' + MILITIA.replace('limit=1]', 'limit=1,nbt={Health:14.0d}]')
              + ' if entity ' + ENEMY.replace('limit=1]', 'limit=1,nbt={Health:10.0d}]'),
              "SUPPLY_HEALS_OWN_ONLY")
        server.send("scoreboard players set #income_left krts_ctrl 900")
        server.send("scoreboard players set #g1 krts_ctrl 17")
        server.send(f"execute as {OWNER} run trigger krts_tech set 3")
        check(server, f"if score {OWNER} krts_tech matches 0 if score #medicine1 krts_ctrl matches 0 "
              "if score #g1 krts_ctrl matches 17", "TECH_MEDICINE_REQUIRES_LOGISTICS", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_tech set 1")
        check(server, "if score #log1 krts_ctrl matches 1 if score #g1 krts_ctrl matches 9",
              "TECH_LOGISTICS_PURCHASED", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_tech set 3")
        check(server, "if score #medicine1 krts_ctrl matches 1 if score #medicine2 krts_ctrl matches 0 "
              "if score #g1 krts_ctrl matches 0", "TECH_MEDICINE_OWNER_ONLY", attempts=5)
        server.send(f"data merge entity {MILITIA} {{Health:10.0d}}")
        server.send(f"scoreboard players set {MILITIA} krts_ration 0")
        check(server, 'if entity ' + MILITIA.replace('limit=1]', 'limit=1,nbt={Health:16.0d}]')
              + ' if entity ' + ENEMY.replace('limit=1]', 'limit=1,nbt={Health:10.0d}]')
              + ' if score #bread1 krts_ctrl matches 0', "TECH_MEDICINE_HEALS_SIX_FOR_ONE_BREAD", attempts=18)
        server.send(f"execute as {OWNER} run trigger krts_job set 7")
        check(server, f"if score {OWNER} krts_focus matches 7 if entity {FARMER} "
              "if entity @e[tag=krts.worker,tag=krts.s1] unless entity @e[tag=krts.farmer,tag=krts.s2]",
              "SUPPLY_CAN_COMBINE_OWN_JOBS", attempts=5)
        server.send("function krts:pause")
        server.expect("Puan, süre ve satın alma durdu.")
        check(server, "if entity " + FARMER.replace("krts.s1]", "krts.s1,nbt={NoAI:1b}]"),
              "SUPPLY_FARMER_PAUSED", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_npc set 2")
        check(server, f"if score {OWNER} krts_auto matches 0", "SUPPLY_PLAYER_CAN_DISABLE", attempts=5)
        server.send("function krts:reset")
        server.expect("Prototip maçı sıfırlandı.")
        check(server, "unless entity @e[tag=krts.farmer] unless entity @e[tag=krts.farm] "
              "if block -8 -60 -62 air if block -13 -61 -64 grass_block", "SUPPLY_CLEANUP")
