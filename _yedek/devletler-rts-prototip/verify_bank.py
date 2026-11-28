"""Exercise the bank in the existing loopback world with the trial rules enabled."""

from verify_local import run_server
from verify_npc import LAB, OWNER, PEER, check, spawn

CAPITAL = "@e[tag=krts.capital,tag=krts.s1,limit=1]"


def complete_bank(candidate):
    with run_server("bank_trial.log", directory=LAB, extra_mods=(candidate,)) as server:
        server.send("function krts:reset")
        spawn(server, OWNER)
        spawn(server, PEER)
        server.send("function krts:start")
        server.expect("Hazırlık başladı.")
        server.send("scoreboard players set #left krts_ctrl 1")
        server.expect("Merkez açık.")
        check(server, f"if score {OWNER} krts_slot matches 1 if score {OWNER} krts_bank matches 0",
              "BANK_OWNER_MENU_READY", attempts=5)
        server.send("scoreboard players set #income_left krts_ctrl 900")
        server.send("scoreboard players operation #bank_test_left krts_ctrl = #left krts_ctrl")
        server.send("scoreboard players set #left krts_ctrl 15")
        server.send(f"execute as {OWNER} run trigger krts_bank set 1")
        check(server, f"if score {OWNER} krts_bank matches 0 if score #g1 krts_ctrl matches 6 "
              "if score #debt1 krts_ctrl matches 0", "BANK_LOAN_REQUIRES_TIME_FOR_FIRST_PAYMENT", attempts=5)
        server.send("scoreboard players operation #left krts_ctrl = #bank_test_left krts_ctrl")
        server.send(f"execute as {OWNER} run trigger krts_bank set 1")
        check(server, "if score #g1 krts_ctrl matches 14 if score #debt1 krts_ctrl matches 10 "
              "if score #debt2 krts_ctrl matches 0", "BANK_ONE_STATE_LOAN", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_bank set 1")
        check(server, f"if score {OWNER} krts_bank matches 0 if score #g1 krts_ctrl matches 14 "
              "if score #debt1 krts_ctrl matches 10", "BANK_SECOND_LOAN_REJECTED", attempts=5)

        server.send("scoreboard players set #g1 krts_ctrl 0")
        server.send("function krts:earn/1")
        server.send("function krts:earn/1")
        server.send("scoreboard players set #debt_t1 krts_ctrl 1")
        check(server, "if score #due1 krts_ctrl matches 2 if score #debt1 krts_ctrl matches 10 "
              "if score #g1 krts_ctrl matches 2 if score #unstable1 krts_ctrl matches 1",
              "BANK_PRESERVES_RESERVE_AND_RECORDS_MISSED_PAYMENT", attempts=5)

        for _ in range(6):
            server.send("function krts:earn/1")
        server.send("scoreboard players set #debt_t1 krts_ctrl 1")
        check(server, "if score #due1 krts_ctrl matches 2 if score #debt1 krts_ctrl matches 8 "
              "if score #g1 krts_ctrl matches 6 if score #unstable1 krts_ctrl matches 3",
              "BANK_PAYS_HALF_AFTER_RESERVE_AND_COMPOUNDS_INSTABILITY", attempts=5)
        server.send("scoreboard players set #unstable1 krts_ctrl 5")
        server.send("scoreboard players set #debt_t1 krts_ctrl 1")
        check(server, "if score #unstable1 krts_ctrl matches 6", "BANK_INSTABILITY_STOPS_AT_MAXIMUM", attempts=5)
        server.send("scoreboard players set #g1 krts_ctrl 10")
        server.send(f"execute as {OWNER} run trigger krts_war set 2")
        check(server, f"if score {OWNER} krts_war matches 0 if score #pact12 krts_ctrl matches 0 "
              "if score #g1 krts_ctrl matches 10", "BANK_SEVERE_INSTABILITY_PREVENTS_WAR", attempts=5)

        server.send(f"execute as {OWNER} run trigger krts_npc set 1")
        server.send(f"execute as {OWNER} run trigger krts_job set 4")
        check(server, "if block -8 -60 -62 chest", "BANK_FOOD_STORAGE_READY", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_npc set 2")
        check(server, f"if score {OWNER} krts_auto matches 0", "BANK_FARM_PAUSED_FOR_FIXTURE", attempts=5)
        server.send("item replace block -8 -60 -62 container.0 with bread 2")
        server.send(f"execute as {OWNER} run trigger krts_bank set 3")
        check(server, f"if score {OWNER} krts_bank matches 0 if score #unstable1 krts_ctrl matches 6 "
              'if data block -8 -60 -62 Items[{Slot:0b,count:2}]', "BANK_RELIEF_WAITS_FOR_DEBT_CLEARANCE", attempts=5)
        server.send(f"data merge entity {CAPITAL} {{Health:58.5d}}")
        server.send(f"execute as {OWNER} run trigger krts_bank set 5")
        check(server, f"if score {OWNER} krts_bank matches 0 if entity {CAPITAL[:-1]},nbt={{Health:58.5d}}] "
              "if score #g1 krts_ctrl matches 10", "BANK_REPAIR_WAITS_FOR_DEBT_CLEARANCE", attempts=5)
        server.send("function krts:pause")
        server.expect("Puan, süre ve satın alma durdu.")
        server.send("scoreboard players set #debt_t1 krts_ctrl 20")
        check(server, "if score #debt_t1 krts_ctrl matches 20 if score #debt1 krts_ctrl matches 8",
              "BANK_SAVES_PENDING_INSTALLMENT")

    with run_server("bank_persistence.log", directory=LAB, extra_mods=(candidate,)) as server:
        spawn(server, OWNER)
        spawn(server, PEER)
        check(server, "if score #phase krts_ctrl matches 4 if score #debt1 krts_ctrl matches 8 "
              "if score #debt_t1 krts_ctrl matches 20 if score #unstable1 krts_ctrl matches 6",
              "BANK_PERSISTED_WITHOUT_ADVANCING_WHILE_PAUSED")
        server.send("function krts:resume")
        server.expect("Maç devam ediyor.")
        check(server, f"if score {OWNER} krts_bank matches 0", "BANK_RESUME_ENABLES_NEW_ACTIONS", attempts=5)
        for index, outstanding in enumerate((6, 4, 2, 0), 1):
            server.send(f"execute as {OWNER} run trigger krts_bank set 2")
            check(server, f"if score #debt1 krts_ctrl matches {outstanding} "
                  f"if score #g1 krts_ctrl matches {outstanding + 2}", f"BANK_EARLY_PAYMENT_{index}", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_bank set 3")
        check(server, "if score #unstable1 krts_ctrl matches 5 if score #debt1 krts_ctrl matches 0 "
              "if data block -8 -60 -62 Items[{Slot:0b,count:1}]", "BANK_RELIEF_CONSUMES_ONE_REAL_RATION", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_bank set 3")
        check(server, "if score #unstable1 krts_ctrl matches 4 unless data block -8 -60 -62 Items[{Slot:0b}]",
              "BANK_LAST_RATION_CLEARS_SLOT", attempts=5)
        server.send(f"execute as {OWNER} run trigger krts_bank set 5")
        check(server, f"if score {OWNER} krts_bank matches 0 if entity {CAPITAL[:-1]},nbt={{Health:58.5d}}] "
              "if score #g1 krts_ctrl matches 2", "BANK_REPAIR_PRESERVES_NEEDS_RESERVE", attempts=5)
        server.send("function krts:earn/1")
        server.send("function krts:earn/1")
        server.send(f"execute as {OWNER} run trigger krts_bank set 5")
        check(server, f"if entity {CAPITAL[:-1]},nbt={{Health:60.0d}}] "
              "if score #g1 krts_ctrl matches 2 if score #unstable1 krts_ctrl matches 3",
              "BANK_REPAIR_CAPS_HEALTH_AND_RESTORES_STABILITY", attempts=5)
        server.send("function krts:earn/1")
        server.send("function krts:earn/1")
        server.send(f"execute as {OWNER} run trigger krts_bank set 5")
        check(server, f"if score {OWNER} krts_bank matches 0 if score #g1 krts_ctrl matches 4 "
              "if score #unstable1 krts_ctrl matches 3", "BANK_FULL_CAPITAL_CANNOT_REPEAT_REPAIR", attempts=5)
        server.send("function krts:reset")
        server.expect("Prototip maçı sıfırlandı.")
