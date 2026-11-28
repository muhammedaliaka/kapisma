"""Per-player automation controls and the physical workshop in each RTS base."""

import json

TRIGGERS = ("krts_npc", "krts_job", "krts_watch")
CONTROL_OBJECTIVES = ("krts_auto", "krts_focus", "krts_cargo", "krts_drill", "krts_rank",
                      "krts_crop", "krts_meal", "krts_tend", "krts_ration",
                      "krts_defense", "krts_guarding", "krts_route", "krts_training", "krts_drill_buy")


def generate(put, buttons):
    put("npc/player",
        'execute unless score @s krts_focus matches 0..7 run scoreboard players set @s krts_focus 3',
        'scoreboard players add @s krts_defense 0',
        'execute if score @s krts_npc matches 1 run scoreboard players set @s krts_auto 1',
        'execute if score @s krts_npc matches 1 run tellraw @s "[RTS] Akıllı NPC’lerin açık."',
        'execute if score @s krts_npc matches 2 run scoreboard players set @s krts_auto 0',
        'execute if score @s krts_npc matches 2 run tellraw @s "[RTS] Akıllı NPC’lerin kapalı."',
        'execute if score @s krts_npc matches 5 if score #phase krts_ctrl matches 2 run scoreboard players set @s krts_drill_buy 1',
        'execute if score @s krts_npc matches 5 unless score #phase krts_ctrl matches 2 run tellraw @s "[RTS] Demir alımı aktif maçta yapılır."',
        'execute if score @s krts_job matches 1..7 run scoreboard players operation @s krts_focus = @s krts_job',
        'execute if score @s krts_job matches 8 run scoreboard players set @s krts_focus 0',
        'execute if score @s krts_job matches 1..8 run tellraw @s "[RTS] NPC görevlerin güncellendi."',
        'execute if score @s krts_watch matches 1..2 run scoreboard players operation @s krts_defense = @s krts_watch',
        'execute if score @s krts_watch matches 3 run scoreboard players set @s krts_defense 0',
        'execute if score @s krts_watch matches 1..3 run tellraw @s "[RTS] Devletinin savunma emri güncellendi."',
        *[f'execute if score @s krts_slot matches {slot} in minecraft:overworld run function krts:npc/player/{slot}'
          for slot in range(1, 9)],
        *[f'scoreboard players set @s {trigger} 0\nscoreboard players enable @s {trigger}' for trigger in TRIGGERS])
    put("npc/menu",
        'execute if score @s krts_auto matches 1 run tellraw @s "[RTS] Kendi devletin · Akıllı NPC’ler açık."',
        'execute unless score @s krts_auto matches 1 run tellraw @s "[RTS] Kendi devletin · Akıllı NPC’ler kapalı."',
        "tellraw @s " + buttons({"Aç": "/trigger krts_npc set 1", "Kapat": "/trigger krts_npc set 2",
                                 "Madenci al · 4 altın": "/trigger krts_npc set 3",
                                 "Çiftçi al · 3 altın": "/trigger krts_npc set 4",
                                 "Talim demiri al · 3 altın": "/trigger krts_npc set 5"}),
        "tellraw @s " + buttons({"Demir": "/trigger krts_job set 1", "Talim": "/trigger krts_job set 2",
                                 "Demir ve talim": "/trigger krts_job set 3"}),
        "tellraw @s " + buttons({"Erzak": "/trigger krts_job set 4", "Demir ve erzak": "/trigger krts_job set 5",
                                 "Talim ve erzak": "/trigger krts_job set 6", "Tümü": "/trigger krts_job set 7",
                                 "Üretim ve talimi kapat": "/trigger krts_job set 8"}),
        "tellraw @s " + buttons({"Üssü koru": "/trigger krts_watch set 1", "Devriye": "/trigger krts_watch set 2",
                                 "Savunmayı kapat": "/trigger krts_watch set 3"}),
        *[f'execute if score @s krts_slot matches {slot} run function krts:npc/status/{slot}'
          for slot in range(1, 9)])
    put("npc/place", '$execute positioned $(x) -60 $(z) run function krts:npc/workshop/$(slot)')
    put("npc/worker", '$execute positioned $(x) -60 $(z) run function krts:npc/worker/$(slot)')
    put("npc/farm", '$execute positioned $(x) -60 $(z) run function krts:npc/farm/$(slot)')
    put("npc/farmer", '$execute positioned $(x) -60 $(z) run function krts:npc/farmer/$(slot)')
    put("npc/guardpost",
        '$execute positioned $(x) -60 $(z) run summon minecraft:marker ~ ~ ~ {Tags:["krts.guardpost","krts.s$(slot)"]}')
    put("npc/cleanup",
        'fill ~ ~ ~-4 ~5 ~ ~4 air',
        'fill ~ ~-1 ~-4 ~5 ~-1 ~4 grass_block',
        'kill @s')
    put("npc/cleanup_farm",
        'fill ~-6 ~ ~-2 ~2 ~ ~3 air',
        'fill ~-6 ~-1 ~-2 ~2 ~-1 ~3 grass_block',
        'kill @s')
    for slot in range(1, 9):
        yard = f'@e[tag=krts.yard,tag=krts.s{slot},limit=1]'
        worker = f'@e[tag=krts.worker,tag=krts.s{slot},limit=1]'
        farm = f'@e[tag=krts.farm,tag=krts.s{slot},limit=1]'
        farmer = f'@e[tag=krts.farmer,tag=krts.s{slot},limit=1]'
        guardpost = f'@e[tag=krts.guardpost,tag=krts.s{slot},limit=1]'
        trainee = f'@e[tag=krts.s{slot},scores={{krts_training=1,krts_drill=1..19}},limit=1]'
        workshop = f'with storage krts:arena h{slot}'
        put(f"npc/player/{slot}",
            f'execute if score @s krts_auto matches 1 if score @s krts_focus matches 1..7 unless score @s krts_focus matches 4 unless entity {yard} run function krts:npc/place {workshop}',
            f'execute if score @s krts_auto matches 1 if score @s krts_focus matches 4..7 unless entity {farm} run function krts:npc/farm {workshop}',
            f'execute if score @s krts_auto matches 1 if score @s krts_defense matches 1..2 unless entity {guardpost} run function krts:npc/guardpost {workshop}',
            f'execute if score @s krts_npc matches 3 if score #phase krts_ctrl matches 2 if score @s krts_auto matches 1 if entity {yard} run function krts:npc/rehire/{slot}',
            f'execute if score @s krts_npc matches 4 if score #phase krts_ctrl matches 2 if score @s krts_auto matches 1 if entity {farm} run function krts:npc/rehire_farmer/{slot}')
        put(f"npc/rehire/{slot}",
            f'execute if entity {worker} run return run tellraw @s "[RTS] Bu devletin bir işçisi var."',
            f'execute unless score #g{slot} krts_ctrl matches 4.. run return run tellraw @s "[RTS] İşçi almak için 4 altın gerekir."',
            f'execute store success score #npc_hired krts_ctrl run function krts:npc/worker {workshop}',
            f'execute if score #npc_hired krts_ctrl matches 1 run scoreboard players remove #g{slot} krts_ctrl 4')
        put(f"npc/workshop/{slot}",
            f'summon minecraft:marker ~8 ~ ~ {{Tags:["krts.yard","krts.s{slot}"]}}',
            'setblock ~8 ~-1 ~ polished_andesite',
            'setblock ~8 ~ ~1 chest{Lock:"Kapışma RTS atölye anahtarı"}',
            'item replace block ~8 ~ ~1 container.1 with minecraft:iron_pickaxe',
            'item replace block ~8 ~ ~1 container.2 with minecraft:iron_pickaxe',
            'setblock ~8 ~ ~2 furnace{Lock:"Kapışma RTS atölye anahtarı"}',
            'item replace block ~8 ~ ~2 container.1 with minecraft:coal 16',
            'setblock ~12 ~ ~-3 iron_ore', 'setblock ~12 ~ ~ iron_ore', 'setblock ~12 ~ ~3 iron_ore',
            f'scoreboard players set #iron{slot} krts_ctrl 0',
            f'scoreboard players set #renew{slot} krts_ctrl 0',
            f'function krts:npc/worker/{slot}')
        put(f"npc/worker/{slot}",
            f'return run summon minecraft:villager ~8 ~ ~-2 {{NoAI:1b,PersistenceRequired:1b,Silent:1b,'
            f'CustomName:\'{{"text":"Devlet {slot} · Madenci","color":"gold"}}\',CustomNameVisible:1b,'
            f'Tags:["krts.worker","krts.s{slot}"]}}')
        put(f"npc/farm/{slot}",
            f'summon minecraft:marker ~-8 ~ ~ {{Tags:["krts.farm","krts.s{slot}"]}}',
            'setblock ~-8 ~-1 ~ moss_block',
            'setblock ~-8 ~ ~2 chest{Lock:"Kapışma RTS erzak anahtarı"}',
            'setblock ~-14 ~-1 ~ water',
            *[f'setblock ~{x} ~-1 ~{z} farmland[moisture=7]' for x in (-11, -13) for z in (-1, 0, 1)],
            *[f'setblock ~{x} ~ ~{z} wheat[age=7]' for x in (-11, -13) for z in (-1, 0, 1)],
            f'scoreboard players set #bread{slot} krts_ctrl 0',
            f'function krts:npc/farmer/{slot}')
        put(f"npc/farmer/{slot}",
            f'return run summon minecraft:villager ~-8 ~ ~-1 {{NoAI:1b,PersistenceRequired:1b,Silent:1b,'
            f'CustomName:\'{{"text":"Devlet {slot} · Çiftçi","color":"green"}}\',CustomNameVisible:1b,'
            f'Tags:["krts.farmer","krts.s{slot}"]}}')
        put(f"npc/rehire_farmer/{slot}",
            f'execute if entity {farmer} run return run tellraw @s "[RTS] Bu devletin bir çiftçisi var."',
            f'execute unless score #g{slot} krts_ctrl matches 3.. run return run tellraw @s "[RTS] Çiftçi almak için 3 altın gerekir."',
            f'execute store success score #npc_hired krts_ctrl run function krts:npc/farmer {workshop}',
            f'execute if score #npc_hired krts_ctrl matches 1 run scoreboard players remove #g{slot} krts_ctrl 3')
        status = [{"text": f"Devlet {slot} · Depodaki demir "},
                  {"score": {"name": f"#iron{slot}", "objective": "krts_ctrl"}},
                  {"text": " · Erzak "}, {"score": {"name": f"#bread{slot}", "objective": "krts_ctrl"}}]
        drill_status = "tellraw @s " + json.dumps(
            [{"text": "[RTS] Talim "}, {"score": {"name": trainee, "objective": "krts_drill"}},
             {"text": "/20 saniye."}], ensure_ascii=False)
        fast_trainee = trainee.replace("1..19", "1..14")
        fast_status = "tellraw @s " + json.dumps(
            [{"text": "[RTS] Talim "}, {"score": {"name": fast_trainee, "objective": "krts_drill"}},
             {"text": "/15 saniye."}], ensure_ascii=False)
        put(f"npc/status/{slot}",
            f'execute unless score #iron{slot} krts_ctrl matches 0.. run scoreboard players set #iron{slot} krts_ctrl 0',
            f'execute unless score #bread{slot} krts_ctrl matches 0.. run scoreboard players set #bread{slot} krts_ctrl 0',
            "tellraw @s " + json.dumps(status, ensure_ascii=False),
            f'execute if entity {trainee} unless score #strict krts_ctrl matches 1 run {drill_status}',
            f'execute if entity {trainee} if score #strict krts_ctrl matches 1 unless score #doctrine{slot} krts_ctrl matches 1 run {drill_status}',
            f'execute if entity {fast_trainee} if score #strict krts_ctrl matches 1 if score #doctrine{slot} krts_ctrl matches 1 run {fast_status}',
            'execute if score @s krts_defense matches 1 run tellraw @s "[RTS] Savunma emri · Üssü koru."',
            'execute if score @s krts_defense matches 2 run tellraw @s "[RTS] Savunma emri · Devriye."',
            'execute if score @s krts_defense matches 0 run tellraw @s "[RTS] Savunma emri · Kapalı."',
            f'execute if score #alert{slot} krts_ctrl matches 1 run tellraw @s "[RTS] Üs çevresinde düşman birliği var."',
            f'execute if entity {yard} unless entity {worker} run tellraw @s "[RTS] Madenci kayıp. Menüden yeni işçi alabilirsin."',
            f'execute if entity {farm} unless entity {farmer} run tellraw @s "[RTS] Çiftçi kayıp. Menüden yeniden alabilirsin."')
