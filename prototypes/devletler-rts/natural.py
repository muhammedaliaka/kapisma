"""Capital matches, buildings and worker controls for surveyed terrain."""

import json

MATCH_OBJECTIVES = ("krts_wood_used", "krts_wood_cargo", "krts_wood_aim", "krts_wood_task",
                    "krts_wood_x", "krts_wood_y", "krts_wood_z", "krts_wood_click", "krts_stone_used", "krts_work_kind",
                    "krts_post_aim", "krts_post_connected", "krts_iron_used", "krts_gold_used", "krts_nat_drill",
                    "krts_nat_farming", "krts_nat_rations", "krts_field_work", "krts_ration_hit")
STARTING_BREAD = 2


def generate(put, colors, buttons):
    put("natural/open",
        'execute unless score #phase krts_ctrl matches 0 run return 0',
        'scoreboard players set #arena_style krts_ctrl 2',
        'scoreboard players set #phase krts_ctrl 1',
        'scoreboard players set #left krts_ctrl 1',
        'scoreboard objectives setdisplay sidebar',
        'function krts:natural/border with storage krts:arena center',
        *[f'execute if score #slot krts_ctrl matches {slot}.. run function krts:natural/site with storage krts:arena h{slot}'
          for slot in range(1, 9)])
    put("natural/generation",
        'execute store result storage krts:args generation int 1 run scoreboard players get #generation krts_ctrl')
    put("natural/border", '$worldborder center $(x) $(z)', '$worldborder set $(width)')
    put("natural/site",
        '$forceload add $(min_x) $(min_z) $(max_x) $(max_z)',
        '$scoreboard players set #a$(slot) krts_ctrl 1',
        '$scoreboard players operation #g$(slot) krts_ctrl = #initial krts_ctrl',
        f'$scoreboard players set #bread$(slot) krts_ctrl {STARTING_BREAD}',
        '$data modify storage krts:args slot set value $(slot)',
        '$data modify storage krts:args owner set value $(owner)',
        'data modify storage krts:args frozen set value 1b',
        '$data modify storage krts:args foundation_y set value $(foundation_y)',
        '$data modify storage krts:args foundation_height set value $(foundation_height)',
        '$function krts:natural/theme/$(origin)',
        '$execute positioned $(hall_x) $(hall_y) $(hall_z) run function krts:natural/build/$(slot) with storage krts:args',
        '$execute positioned $(x) $(y) $(cz) run function krts:capital/spawn/$(slot)',
        '$execute positioned $(x) $(y) $(cz) positioned ~-1 ~ ~ run function krts:spawn_militia with storage krts:args',
        '$execute positioned $(x) $(y) $(cz) positioned ~1 ~ ~ run function krts:spawn_archer with storage krts:args',
        '$execute positioned $(x) $(y) $(z) run function krts:natural/wood/depot with storage krts:args')
    put("natural/release", '$forceload remove $(min_x) $(min_z) $(max_x) $(max_z)')
    put("natural/clear_stale", '$kill @e[tag=krts.unit,tag=!krts.gen$(generation)]',
        '$kill @e[tag=krts.woodworker,tag=!krts.gen$(generation)]')
    put("natural/lobby", '$teleport @a[scores={krts_slot=1..8}] $(x) $(y) $(z)',
        '$spawnpoint @a[scores={krts_slot=1..8}] $(x) $(y) $(z)')
    put("natural/arrive",
        'function krts:match/equip',
        'tag @s remove krts.late',
        '$scoreboard players set @s krts_found_site $(site)',
        '$scoreboard players set @s krts_origin $(origin)',
        '$scoreboard players set @s krts_profile $(profile)',
        '$execute in minecraft:overworld positioned $(x) $(y) $(entry_z) run tp @s ~ ~ ~',
        '$execute in minecraft:overworld positioned $(x) $(y) $(entry_z) run spawnpoint @s ~ ~ ~',
        'execute if score #phase krts_ctrl matches 2 unless score #strict krts_ctrl matches 1 run function krts:natural/rivals',
        '$tellraw @s {"text":"[RTS] Devlet $(slot) · Belediye binan bu bölgede.","color":"gold"}')
    put("natural/rivals", 'tag @s add krts.relation_self',
        'execute if entity @a[scores={krts_slot=1..8},tag=!krts.relation_self] run hyw relation set @a[scores={krts_slot=1..8},tag=!krts.relation_self] hostile',
        'execute as @a[scores={krts_slot=1..8},tag=!krts.relation_self] run hyw relation set @a[tag=krts.relation_self] hostile',
        'tag @s remove krts.relation_self')

    put("natural/wood/clock",
        *[f'execute if score #a{slot} krts_ctrl matches 1 if score #slot krts_ctrl matches {slot}.. '
          f'unless entity @e[tag=krts.wood_depot,tag=krts.s{slot}] run function krts:natural/wood/ensure with storage krts:arena h{slot}'
          for slot in range(1, 9)])
    put("natural/wood/ensure",
        '$execute if loaded $(x) $(y) $(z) run data modify storage krts:args slot set value $(slot)',
        '$execute if loaded $(x) $(y) $(z) positioned $(x) $(y) $(z) run function krts:natural/wood/depot with storage krts:args')
    put("natural/wood/depot",
        '$summon minecraft:marker ~2 ~ ~1 {Tags:["krts.wood_depot","krts.s$(slot)"]}',
        '$summon minecraft:villager ~2 ~ ~1 {Tags:["krts.worker","krts.woodworker","krts.s$(slot)",'
        '"krts.gen$(generation)"],NoAI:1b,PersistenceRequired:1b,Silent:1b,'
        'HandItems:[{id:"minecraft:iron_axe",count:1},{}],HandDropChances:[0.0f,0.0f],'
        'CustomName:\'{"text":"Oduncu","color":"dark_green"}\',CustomNameVisible:1b}',
        '$scoreboard players add #wood$(slot) krts_ctrl 0',
        '$scoreboard players add #stone$(slot) krts_ctrl 0',
        '$scoreboard players add #iron$(slot) krts_ctrl 0',
        '$scoreboard players add #wood_blocked$(slot) krts_ctrl 0',
        '$scoreboard players add #stone_blocked$(slot) krts_ctrl 0',
        '$scoreboard players add #iron_blocked$(slot) krts_ctrl 0',
        '$scoreboard players add #g_blocked$(slot) krts_ctrl 0')
    put("natural/wood/player",
        'execute if score @s krts_npc matches 1 run scoreboard players set @s krts_auto 1',
        'execute if score @s krts_npc matches 1 run scoreboard players set @s krts_work_kind 1',
        'execute if score @s krts_npc matches 3 run scoreboard players set @s krts_auto 1',
        'execute if score @s krts_npc matches 3 run scoreboard players set @s krts_work_kind 2',
        'execute if score @s krts_npc matches 4 run scoreboard players set @s krts_auto 1',
        'execute if score @s krts_npc matches 4 run scoreboard players set @s krts_work_kind 3',
        'execute if score @s krts_npc matches 1..4 run function krts:natural/wood/cancel',
        'execute if score @s krts_npc matches 1 run tellraw @s "[RTS] Oduncun açık."',
        'execute if score @s krts_npc matches 3 run tellraw @s "[RTS] İşçi doğal kaya arayacak."',
        'execute if score @s krts_npc matches 2 run scoreboard players set @s krts_auto 0',
        'execute if score @s krts_npc matches 2 run tellraw @s "[RTS] Oduncun kapalı."',
        'execute if score @s krts_npc matches 4 run tellraw @s "[RTS] İşçi ulaşabildiği demir damarını arayacak."',
        'execute if score @s krts_npc matches 5 if score #phase krts_ctrl matches 2 run scoreboard players set @s krts_drill_buy 1',
        'execute if score @s krts_npc matches 5 unless score #phase krts_ctrl matches 2 run tellraw @s "[RTS] Demir alımı aktif maçta yapılır."',
        'execute if score @s krts_npc matches 6 run scoreboard players set @s krts_nat_drill 1',
        'execute if score @s krts_npc matches 6 run tellraw @s "[RTS] Talim açıldı. Her seviye 1 demir ve 2 altın."',
        'execute if score @s krts_npc matches 7 run scoreboard players set @s krts_nat_drill 0',
        'execute if score @s krts_npc matches 7 run tellraw @s "[RTS] Talim durduruldu."',
        'execute if score @s krts_npc matches 8 run scoreboard players set @s krts_nat_farming 1',
        'execute if score @s krts_npc matches 8 run tellraw @s "[RTS] Çiftçi bağlı bir tarlada çalışacak."',
        'execute if score @s krts_npc matches 9 run scoreboard players set @s krts_nat_farming 0',
        'execute if score @s krts_npc matches 9 run tellraw @s "[RTS] Çiftçi durduruldu."',
        'execute if score @s krts_npc matches 10 run scoreboard players set @s krts_nat_rations 1',
        'execute if score @s krts_npc matches 10 run tellraw @s "[RTS] Belediye dinlenen yaralı birliklere erzak verecek."',
        'execute if score @s krts_npc matches 11 run scoreboard players set @s krts_nat_rations 0',
        'execute if score @s krts_npc matches 11 run tellraw @s "[RTS] Erzak dağıtımı durduruldu."',
        'execute if score @s krts_npc matches 12 run function krts:natural/gold/auto',
        'execute if score @s krts_job matches 1 if score #phase krts_ctrl matches 2 run function krts:natural/wood/aim',
        'execute if score @s krts_job matches 2 run function krts:natural/wood/cancel',
        'execute if score @s krts_job matches 3 if score #phase krts_ctrl matches 2 run function krts:natural/stone/aim',
        'execute if score @s krts_job matches 4 if score #phase krts_ctrl matches 2 run function krts:natural/iron/aim',
        'execute if score @s krts_job matches 5 if score #phase krts_ctrl matches 2 run function krts:natural/gold/aim',
        'scoreboard players set @s krts_npc 0', 'scoreboard players enable @s krts_npc',
        'scoreboard players set @s krts_job 0', 'scoreboard players enable @s krts_job')
    put("natural/wood/aim",
        'scoreboard players set @s krts_auto 0',
        'scoreboard players set @s krts_post_aim 0',
        'scoreboard players set @s krts_wood_aim 1',
        'tellraw @s "[RTS] Üstten kamera açıkken seçtiğin gövdeye sağ tıkla."')
    put("natural/stone/aim",
        'scoreboard players set @s krts_auto 0',
        'scoreboard players set @s krts_post_aim 0',
        'scoreboard players set @s krts_wood_aim 2',
        'tellraw @s "[RTS] Üstten kamera açıkken seçtiğin doğal kaya bloğuna sağ tıkla."')
    put("natural/iron/aim",
        'scoreboard players set @s krts_auto 0',
        'scoreboard players set @s krts_post_aim 0',
        'scoreboard players set @s krts_wood_aim 3',
        'tellraw @s "[RTS] Üstten kamera açıkken görünen demir cevherine sağ tıkla."')
    put("natural/gold/auto",
        'function krts:natural/wood/cancel',
        'scoreboard players set @s krts_auto 1',
        'scoreboard players set @s krts_work_kind 4',
        'tellraw @s "[RTS] İşçi ulaşabildiği altın damarını arayacak."')
    put("natural/gold/aim",
        'scoreboard players set @s krts_auto 0',
        'scoreboard players set @s krts_post_aim 0',
        'scoreboard players set @s krts_wood_aim 4',
        'tellraw @s "[RTS] Üstten kamera açıkken görünen altın cevherine sağ tıkla."')
    put("natural/wood/cancel",
        'scoreboard players set @s krts_wood_aim 0', 'scoreboard players set @s krts_wood_task 0')
    put("natural/wood/menu",
        'execute if score @s krts_auto matches 1 unless score @s krts_work_kind matches 2..4 run tellraw @s "Oduncu · Açık"',
        'execute if score @s krts_auto matches 1 if score @s krts_work_kind matches 2 run tellraw @s "Taş toplama · Açık"',
        'execute if score @s krts_auto matches 1 if score @s krts_work_kind matches 3 run tellraw @s "Demir toplama · Açık"',
        'execute if score @s krts_auto matches 1 if score @s krts_work_kind matches 4 run tellraw @s "Altın toplama · Açık"',
        'execute unless score @s krts_auto matches 1 run tellraw @s "İşçi otomasyonu · Kapalı"',
        'tellraw @s ' + buttons({"Oduncuyu aç": "/trigger krts_npc set 1", "Oduncuyu kapat": "/trigger krts_npc set 2"}),
        'execute if score #phase krts_ctrl matches 2 run tellraw @s ' + buttons({
            "Gövde seç": "/trigger krts_job set 1", "İş emrini iptal et": "/trigger krts_job set 2"}),
        'execute if score #phase krts_ctrl matches 2 run tellraw @s ' + buttons({
            "Taş toplamayı aç": "/trigger krts_npc set 3", "Kaya seç": "/trigger krts_job set 3"}),
        'execute if score #phase krts_ctrl matches 2 run tellraw @s ' + buttons({
            "Demir toplamayı aç": "/trigger krts_npc set 4", "Demir damarı seç": "/trigger krts_job set 4",
            "Demir al · 3 altın": "/trigger krts_npc set 5"}),
        'execute if score #phase krts_ctrl matches 2 run tellraw @s ' + buttons({
            "Altın toplamayı aç": "/trigger krts_npc set 12", "Altın damarı seç": "/trigger krts_job set 5"}),
        'execute if score @s krts_nat_drill matches 1 run tellraw @s "Birlik talimi · Açık"',
        'execute unless score @s krts_nat_drill matches 1 run tellraw @s "Birlik talimi · Kapalı"',
        'execute if score #phase krts_ctrl matches 2 run tellraw @s ' + buttons({
            "Talimi aç": "/trigger krts_npc set 6", "Talimi durdur": "/trigger krts_npc set 7"}),
        'execute if score @s krts_wood_task matches 1..4 run tellraw @s "[RTS] İşçi elle seçtiğin kaynağa atanmış."',
        *[f'execute if score @s krts_slot matches {slot} run function krts:natural/wood/status/{slot}'
          for slot in range(1, 9)])
    put("natural/post/menu",
        *[f'execute {test} score @s krts_profile matches 3 run tellraw @s ' + buttons({
            f"Karakol kur · 3 odun + {stone} kırık taş": "/trigger krts_build set 1",
            "Karakol yeri öner": "/trigger krts_build set 3"})
          for test, stone in (("unless", 2), ("if", 3))],
        "tellraw @s " + buttons({"Mahalle kur · 4 odun + 3 kırık taş": "/trigger krts_build set 4",
                                 "Mahalle yeri öner": "/trigger krts_build set 5", "İnşaat seçimini iptal et": "/trigger krts_build set 2"}),
        "tellraw @s " + buttons({"Tarla kur · 3 odun + 2 kırık taş": "/trigger krts_build set 6",
                                 "Tarla yeri öner": "/trigger krts_build set 7"}),
        "tellraw @s " + buttons({"Talim Ocağı kur · 3 odun + 2 kırık taş + 2 demir": "/trigger krts_build set 8",
                                 "Talim Ocağı yeri öner": "/trigger krts_build set 9"}),
        'execute if score @s krts_nat_farming matches 1 run tellraw @s "Çiftçi · Açık"',
        'execute unless score @s krts_nat_farming matches 1 run tellraw @s "Çiftçi · Kapalı"',
        'execute if score @s krts_nat_rations matches 1 run tellraw @s "Erzak dağıtımı · Açık"',
        'execute unless score @s krts_nat_rations matches 1 run tellraw @s "Erzak dağıtımı · Kapalı"',
        'tellraw @s ' + buttons({"Çiftçiyi çalıştır": "/trigger krts_npc set 8", "Çiftçiyi durdur": "/trigger krts_npc set 9"}),
        'tellraw @s ' + buttons({"Erzak dağıtımını aç": "/trigger krts_npc set 10",
                                 "Erzak dağıtımını durdur": "/trigger krts_npc set 11"}),
        *[f'execute if score @s krts_slot matches {slot} run function krts:natural/post/status/{slot}'
          for slot in range(1, 9)])
    put("natural/structure/build",
        '$execute positioned $(x) $(y) $(z) run summon minecraft:marker ~ ~ ~ '
        '{Tags:["krts.$(kind)_part","krts.$(kind)_anchor","krts.s$(slot)","krts.$(kind)$(id)"]}',
        '$execute positioned $(x) $(y) $(z) run summon hundred_years_war:militia ~ ~ ~ '
        '{OwnerUUID:$(owner),NoAI:1b,AttackStrategy:"cease_fire",PersistenceRequired:1b,'
        'Tags:["krts.$(kind)_part","krts.$(kind)_core","krts.s$(slot)","krts.$(kind)$(id)"]}',
        '$attribute @e[tag=krts.$(kind)_core,tag=krts.s$(slot),tag=krts.$(kind)$(id),limit=1] minecraft:generic.max_health base set $(health)',
        '$attribute @e[tag=krts.$(kind)_core,tag=krts.s$(slot),tag=krts.$(kind)$(id),limit=1] minecraft:generic.knockback_resistance base set 1',
        '$data merge entity @e[tag=krts.$(kind)_core,tag=krts.s$(slot),tag=krts.$(kind)$(id),limit=1] '
        '{Health:$(health).0d,CustomName:\'{"text":"Devlet $(slot) · $(label)","color":"gold"}\',CustomNameVisible:1b}',
        '$execute positioned $(x) $(floor) $(z) run function krts:natural/$(kind)/model/$(slot) with storage krts:outpost args',
        '$execute if entity @e[tag=krts.$(kind)_anchor,tag=krts.field_anchor,tag=krts.s$(slot)] '
        'unless entity @e[tag=krts.farmer,tag=krts.field_part,tag=krts.s$(slot)] '
        'positioned $(x) $(y) $(z) run function krts:natural/field/worker with storage krts:outpost args',
        '$execute if entity @e[tag=krts.$(kind)_anchor,tag=krts.field_anchor,tag=krts.s$(slot)] '
        'unless entity @e[tag=krts.farmer,tag=krts.field_part,tag=krts.s$(slot)] run return 0',
        '$execute if entity @e[tag=krts.$(kind)_anchor,tag=krts.s$(slot),tag=krts.$(kind)$(id)] '
        'if entity @e[tag=krts.$(kind)_core,tag=krts.s$(slot),tag=krts.$(kind)$(id)] '
        'if entity @e[tag=krts.$(kind)_visual,tag=krts.s$(slot),tag=krts.$(kind)$(id)] run return 1',
        'return 0')
    put("natural/field/worker",
        '$summon minecraft:villager ~ ~ ~ {Tags:["krts.farmer","krts.field_part","krts.s$(slot)","krts.field$(id)"],'
        'NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b,'
        'CustomName:\'{"text":"Çiftçi","color":"gold"}\',CustomNameVisible:1b}')
    put("natural/post/release", 'forceload remove ~ ~', 'kill @s')
    for slot in range(1, 9):
        stock = [{"text": "Belediye deposu · Odun "}, {"score": {"name": f"#wood{slot}", "objective": "krts_ctrl"}},
                 {"text": "/12 · Kırık taş "}, {"score": {"name": f"#stone{slot}", "objective": "krts_ctrl"}},
                 {"text": "/12 · Demir "}, {"score": {"name": f"#iron{slot}", "objective": "krts_ctrl"}},
                 {"text": "/8 · Erzak "}, {"score": {"name": f"#bread{slot}", "objective": "krts_ctrl"}}, {"text": "/8"}]
        put(f"natural/wood/status/{slot}", 'tellraw @s ' + json.dumps(stock, ensure_ascii=False),
            f'execute if score @s krts_auto matches 1 unless score @s krts_work_kind matches 2..4 if score #wood_blocked{slot} krts_ctrl matches 1 '
            'run tellraw @s "[RTS] Yakında erişilebilen ve kotası kalan odun kaynağı aranıyor."',
            f'execute if score @s krts_auto matches 1 if score @s krts_work_kind matches 2 '
            f'if score #stone_blocked{slot} krts_ctrl matches 1 run tellraw @s "[RTS] Yakında erişilebilen doğal kaya aranıyor."',
            f'execute if score @s krts_auto matches 1 if score @s krts_work_kind matches 3 '
            f'if score #iron_blocked{slot} krts_ctrl matches 1 run tellraw @s "[RTS] Ulaşılabilir demir bulunamadı. Demir satın alabilirsin."',
            f'execute if score @s krts_auto matches 1 if score @s krts_work_kind matches 4 '
            f'if score #g_blocked{slot} krts_ctrl matches 1 run tellraw @s "[RTS] Ulaşılabilir altın bulunamadı. Kervan siparişlerini kontrol et."',
            f'execute if score #goldPending{slot} krts_ctrl matches 1 run tellraw @s "[RTS] Hazinede yer açılınca 1 altın eklenecek."',
            f'execute unless entity @e[tag=krts.woodworker,tag=krts.s{slot}] run tellraw @s "[RTS] İşçi erişim dışında veya kayıp."')
        post_status = [{"text": "Karakol "}, {"score": {"name": f"#posts{slot}", "objective": "krts_ctrl"}}]
        linked_posts = {"score": {"name": f"#linked{slot}", "objective": "krts_ctrl"}}
        district_status = [{"text": "Mahalle "}, {"score": {"name": f"#districts{slot}", "objective": "krts_ctrl"}},
                           {"text": "/2 · Birlik kapasitesi +"}, {"score": {"name": f"#housing{slot}", "objective": "krts_ctrl"}}]
        put(f"natural/post/status/{slot}",
            *[f'execute {condition} score @s krts_profile matches 4 run tellraw @s '
              + json.dumps([*post_status, {"text": f"/{limit} · Bağlı "}, linked_posts], ensure_ascii=False)
              for condition, limit in (("unless", 3), ("if", 2))],
            'tellraw @s ' + json.dumps(district_status, ensure_ascii=False),
            'tellraw @s ' + json.dumps(
                [{"text": "Tarla "}, {"score": {"name": f"#fields{slot}", "objective": "krts_ctrl"}},
                 {"text": "/1 · Bağlı "}, {"score": {"name": f"#fields_linked{slot}", "objective": "krts_ctrl"}}],
                ensure_ascii=False),
            'tellraw @s ' + json.dumps(
                [{"text": "Talim Ocağı "}, {"score": {"name": f"#academies{slot}", "objective": "krts_ctrl"}},
                 {"text": "/1 · Bağlı "}, {"score": {"name": f"#academy_linked{slot}", "objective": "krts_ctrl"}}],
                ensure_ascii=False))

    # The facade faces the rally lane. Model the foundation at measured ground height
    # without filling blocks or introducing collisions with units.
    def display(slot, block, x, y, z, scale, part=None, group=("krts.townhall",), properties=""):
        sx, sy, sz = scale
        tags = [*group, f"krts.s{slot}"]
        if part:
            tags.append(f"krts.hall_{part}")
        macro = "$" if any("$(" in str(value) for value in (block, x, y, z, *scale, *tags)) else ""
        encoded_tags = json.dumps(tags, separators=(",", ":"))
        return (f'{macro}summon minecraft:block_display ~{x} ~{y} ~{z} '
                f'{{block_state:{{Name:"minecraft:{block}"{properties}}},Tags:{encoded_tags},view_range:2.5f,'
                'transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],'
                f'right_rotation:[0f,0f,0f,1f],scale:[{sx}f,{sy}f,{sz}f]}}}}')

    themes = (
        ("stone_bricks", "spruce_planks", "stripped_spruce_log", "deepslate_tiles", "bell"),
        ("nether_bricks", "crimson_planks", "stripped_crimson_stem", "red_nether_bricks", "shroomlight"),
        ("cobblestone", "dark_oak_planks", "stripped_dark_oak_log", "dark_oak_planks", "iron_block"),
        ("polished_blackstone_bricks", "warped_planks", "stripped_warped_stem", "polished_blackstone", "amethyst_block"),
    )
    for origin, theme in enumerate(themes, 1):
        put(f"natural/theme/{origin}",
            *(f'data modify storage krts:args {part} set value "{block}"'
              for part, block in zip(("foundation", "wall", "beam", "roof", "crest"), theme)))

    for slot, color in enumerate(colors, 1):
        shapes = [
            ("$(foundation)", -4.75, "$(foundation_y)", 0.0, (9.5, "$(foundation_height)", 8.5), "foundation"),
            ("$(beam)", -4.5, 0.95, 0.25, (9, 0.25, 8)),
            ("$(wall)", -4.25, 1.2, 7.75, (8.5, 4.15, 0.45)),
            ("$(wall)", -4.25, 1.2, 0.4, (0.40, 4.15, 7.65)),
            ("$(wall)", 3.85, 1.2, 0.4, (0.40, 4.15, 7.65)),
            ("$(wall)", -4.25, 1.2, 0.4, (2.85, 4.15, 0.40)),
            ("$(wall)", 1.40, 1.2, 0.4, (2.85, 4.15, 0.40)),
            ("$(wall)", -1.40, 4.05, 0.4, (2.8, 1.30, 0.45)),
            ("$(beam)", -1.55, 3.85, 0.1, (3.1, 0.35, 0.8)),
            ("$(foundation)", -1.4, 0.48, -0.35, (2.8, 0.22, 0.8)),
            ("$(foundation)", -1.4, 0.23, -0.7, (2.8, 0.23, 0.9)),
            ("$(roof)", -5.25, 5.35, -0.25, (10.5, 0.45, 9.0), "roof"),
            ("$(roof)", -4.25, 5.80, 0.1, (8.5, 0.63, 8.3)),
            ("$(roof)", -3.05, 6.43, 0.45, (6.1, 0.70, 7.6)),
            ("$(roof)", -1.70, 7.13, 0.95, (3.4, 0.68, 6.6)),
            ("$(beam)", -1.20, 7.65, 1.65, (2.4, 1.4, 3.1)),
            ("$(roof)", -1.55, 9.05, 1.35, (3.1, 0.45, 3.7)),
            ("$(crest)", -0.40, 8.0, 1.5, (0.8, 0.8, 0.58)),
            ("$(beam)", -0.12, 9.5, 3.0, (0.24, 1.5, 0.24)),
            (f"{color}_wool", 0.12, 10.0, 3.0, (2.2, 0.85, 0.14), "flag"),
        ]
        for x in (-4.35, -1.7, 1.4, 4.05):
            shapes.append(("$(beam)", x, 1.02, 0.0, (0.3, 4.5, 0.85)))
        for x in (-3.55, 2.22):
            shapes.extend([
                ("$(beam)", x - 0.1, 1.95, 0.2, (1.45, 1.8, 0.14)),
                ("tinted_glass", x, 2.10, 0.08, (1.25, 1.50, 0.22)),
                ("$(foundation)", x - 0.15, 1.83, 0.0, (1.55, 0.20, 0.55)),
            ])
        for z in (1.9, 4.15, 6.4):
            shapes.extend([
                ("$(beam)", -4.45, 1.10, z, (0.35, 4.4, 0.26)),
                ("$(beam)", 4.1, 1.10, z, (0.35, 4.4, 0.26)),
            ])
        put(f"natural/build/{slot}", *(display(slot, *shape) for shape in shapes))
        tower = [
            ("stone_bricks", -3.45, "$(bottom)", -3.45, (2.25, "$(height)", 6.9)),
            ("stone_bricks", 1.2, "$(bottom)", -3.45, (2.25, "$(height)", 6.9)),
            ("stone_bricks", -1.2, "$(bottom)", 1.1, (2.4, "$(height)", 2.35)),
            ("spruce_planks", -3, 0.7, 2.15, (6, 4.5, 0.65)),
            ("stripped_spruce_log", -3.1, 0.7, -2.8, (0.55, 4.65, 0.55)),
            ("stripped_spruce_log", 2.55, 0.7, -2.8, (0.55, 4.65, 0.55)),
            ("stripped_spruce_log", -3.1, 0.7, 0.5, (0.55, 4.65, 0.55)),
            ("stripped_spruce_log", 2.55, 0.7, 0.5, (0.55, 4.65, 0.55)),
            ("dark_oak_planks", -3.45, 5.0, -3.45, (6.9, 0.55, 6.9)),
            ("cobblestone", -3.45, 5.55, -3.45, (0.4, 1.0, 6.9)),
            ("cobblestone", 3.05, 5.55, -3.45, (0.4, 1.0, 6.9)),
            ("cobblestone", -3.45, 5.55, 3.05, (6.9, 1.0, 0.4)),
            ("stripped_spruce_log", -0.1, 5.55, 0.3, (0.25, 2.8, 0.25)),
            (f"{color}_wool", 0.15, 7.3, 0.3, (1.9, 0.8, 0.16)),
        ]
        for x in (-3.45, -1.35, 0.75, 2.85):
            tower.append(("cobblestone", x, 5.55, -3.45, (0.6, 1.3, 0.5)))
        post_group = ("krts.post_part", "krts.post_visual", "krts.post$(id)")
        put(f"natural/post/model/{slot}", *(display(slot, *shape, group=post_group) for shape in tower))
        houses = []
        for x in (-4.35, 1.25):
            houses.extend([
                ("stone_bricks", x, "$(bottom)", -2.85, (3.1, "$(height)", 5.9)),
                ("spruce_planks", x + 0.10, 0.95, -2.68, (2.9, 3.55, 5.56)),
                ("stripped_spruce_log", x + 0.10, 1.00, -2.86, (0.28, 3.75, 0.35)),
                ("stripped_spruce_log", x + 2.73, 1.00, -2.86, (0.28, 3.75, 0.35)),
                ("dark_oak_planks", x - 0.08, 4.5, -3.08, (3.26, 0.4, 6.25)),
                ("dark_oak_planks", x + 0.40, 4.9, -2.92, (2.3, 0.58, 5.9)),
                ("dark_oak_planks", x + 0.97, 5.48, -2.72, (1.15, 0.62, 5.5)),
                ("deepslate_tiles", x + 1.98, 5.25, 1.62, (0.65, 1.7, 0.72)),
                ("dark_oak_planks", x + 0.95, 1.04, -2.95, (1.2, 2.25, 0.45)),
                ("tinted_glass", x + 1.02, 3.48, -2.80, (1.06, 0.74, 0.17)),
                ("spruce_planks", x + 0.77, 3.33, -2.96, (1.55, 0.16, 0.48)),
            ])
        houses.extend([
            ("stripped_spruce_log", -0.18, 1.0, 2.72, (0.36, 4.65, 0.36)),
            (f"{color}_wool", 0.12, 4.58, 2.74, (1.6, 0.72, 0.13)),
        ])
        district_group = ("krts.district_part", "krts.district_visual", "krts.district$(id)")
        put(f"natural/district/model/{slot}", *(display(slot, *shape, group=district_group) for shape in houses))
        field_group = ("krts.field_part", "krts.field_visual", "krts.field$(id)")
        barn = [
            ("stone_bricks", -3.45, "$(bottom)", 1.05, (6.9, "$(height)", 2.35)),
            ("spruce_planks", -3.4, 0.65, 1.1, (6.8, 3.45, 2.25)),
            ("dark_oak_planks", -3.5, 4.0, 0.95, (7.0, 0.45, 2.55)),
            ("dark_oak_planks", -3.5, 4.45, 1.35, (7.0, 0.5, 1.8)),
            ("dark_oak_planks", -3.5, 4.95, 1.80, (7.0, 0.6, 0.9)),
            ("stripped_spruce_log", -3.35, 0.65, 0.98, (0.35, 3.45, 0.35)),
            ("stripped_spruce_log", 3.0, 0.65, 0.98, (0.35, 3.45, 0.35)),
            ("dark_oak_planks", -0.85, 0.68, 1.02, (1.7, 2.3, 0.18)),
            ("hay_block", 2.06, 0.35, -0.24, (0.9, 0.9, 0.9)),
            ("hay_block", -2.92, 0.35, -0.24, (0.9, 0.9, 0.9)),
            (f"{color}_wool", -0.6, 3.25, 0.96, (1.2, 0.75, 0.12)),
        ]
        rows = [
            display(slot, "wheat", x, 0.12, z, (1.1, 0.92, 1.1),
                    group=(*field_group, "krts.field_crop"), properties=',Properties:{age:"0"}')
            for x in (-3.15, -1.6, 0.5, 2.05) for z in (-2.88, -1.63)
        ]
        put(f"natural/field/model/{slot}",
            *(display(slot, *shape, group=field_group) for shape in barn),
            display(slot, "coarse_dirt", -3.45, 0.01, -3.42, (6.9, 0.12, 3.8), group=field_group), *rows)
        academy_group = ("krts.academy_part", "krts.academy_visual", "krts.academy$(id)")
        academy = [
            ("polished_andesite", -4.4, "$(bottom)", -3.25, (8.8, "$(height)", 6.5)),
            ("spruce_planks", -4.15, 0.6, 0.75, (8.3, 3.7, 2.5)),
            ("dark_oak_planks", -4.4, 4.3, 0.5, (8.8, 0.45, 3)),
            ("dark_oak_planks", -4.4, 4.75, 1.1, (8.8, 0.58, 1.9)),
            ("deepslate_tiles", -4.4, 5.33, 1.65, (8.8, 0.5, 0.8)),
            ("stripped_spruce_log", -4.22, 0.55, -0.1, (0.44, 3.7, 0.44)),
            ("stripped_spruce_log", 3.78, 0.55, -0.1, (0.44, 3.7, 0.44)),
            ("dark_oak_planks", -0.78, 0.65, 0.65, (1.56, 2.36, 0.20)),
            ("anvil", -3.08, 0.32, -2.65, (1, 1, 1)),
            ("blast_furnace", 2.1, 0.35, -2.7, (1.1, 1.1, 1.1)),
            ("iron_bars", -2.54, 3.00, 0.65, (1.0, 0.72, 0.20)),
            ("iron_bars", 1.52, 3.00, 0.65, (1.0, 0.72, 0.20)),
            (f"{color}_wool", -0.92, 3.35, 0.62, (1.84, 0.88, 0.10)),
            ("stripped_spruce_log", -3.75, 0.45, -1.58, (0.25, 1.65, 0.25)),
            ("target", -3.99, 1.8, -1.65, (0.75, 0.75, 0.25)),
            ("stripped_spruce_log", 3.52, 0.45, -1.58, (0.25, 1.65, 0.25)),
            ("target", 3.28, 1.8, -1.65, (0.75, 0.75, 0.25)),
        ]
        put(f"natural/academy/model/{slot}", *(display(slot, *shape, group=academy_group) for shape in academy))
