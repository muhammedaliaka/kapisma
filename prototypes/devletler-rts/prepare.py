"""Prepare or start the local, disposable Devletler RTS Sandbox."""

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED

import alliances
import caravan
import diplomacy
import economy
import natural
import npc

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parents[1]
SANDBOX = PROJECT / "_yedek" / "devletler-rts-prototip"
MOD = "HundredYearsWar-0.8.0r-fix2-1.21.1-fabric.jar"
SHA512 = "715a35a81af432af0afe13009b675dcdff16af654c9b54847d0cbf07b7958c7d8da0b98bfed177265481c48ed7455ef588cf6462329933fed50a6f7810e97d65"
HOMES = [(0, -64), (0, 64), (-64, 0), (64, 0), (-45, -45), (45, 45), (45, -45), (-45, 45)]
ZONES = [(-24, 0), (12, -21), (12, 21)]
COLORS = ["red", "blue", "green", "yellow", "purple", "orange", "light_blue", "pink"]
LIMITS = {
    "preparation_seconds": (0, 180), "match_seconds": (30, 3600),
    "victory_points": (5, 2000), "initial_gold": (0, 60),
    "archer_cost": (1, 60), "unit_limit": (2, 16),
    "capital_health": (20, 200),
    "income_seconds": (4, 60), "militia_cost": (1, 60), "recruit_cooldown": (1, 15),
    **diplomacy.LIMITS,
}
FLAT = {"biome": "minecraft:plains", "layers": [
    {"block": "minecraft:bedrock", "height": 1},
    {"block": "minecraft:dirt", "height": 2},
    {"block": "minecraft:grass_block", "height": 1},
]}


def read_rules():
    rules = json.loads((HERE / "rules.json").read_text(encoding="utf-8"))
    for key, value in {"session_mode": "persistent", "income_seconds": 12,
                       "militia_cost": 3, "recruit_cooldown": 3, "ruleset": "classic",
                       **diplomacy.DEFAULTS}.items():
        rules.setdefault(key, value)
    if set(rules) != {*LIMITS, "victory_mode", "session_mode", "ruleset"}:
        raise ValueError(f"rules.json alanları: victory_mode, session_mode, ruleset, {', '.join(LIMITS)}")
    if rules["victory_mode"] not in ("capital", "regions"):
        raise ValueError("victory_mode capital veya regions olmalı.")
    if rules["session_mode"] not in ("single", "persistent"):
        raise ValueError("session_mode single veya persistent olmalı.")
    if rules["ruleset"] not in ("classic", "diplomacy"):
        raise ValueError("ruleset classic veya diplomacy olmalı.")
    for name, (low, high) in LIMITS.items():
        value = rules[name]
        if type(value) is not int or not low <= value <= high:
            raise ValueError(f"{name} {low} ile {high} arasında bir tam sayı olmalı.")
    return rules


def ring(count):
    return [(round(64 * math.sin(index * math.tau / count)),
             round(-64 * math.cos(index * math.tau / count))) for index in range(count)]


def make_pack(destination, rules, *, survey_only=False, banking_trial=False, natural_match=False):
    if natural_match and (survey_only or banking_trial or rules["victory_mode"] != "capital"):
        raise ValueError("Doğal maç için Başkent zaferi ve ayrı bir oyun paketi gerekir.")
    founding = int(survey_only or natural_match)
    home_y = "$(y)" if natural_match else "-60"
    generation_tag = ',"krts.gen$(generation)"' if natural_match else ''
    equipment = []
    for item in ("hundred_years_war:command_staff", "hundred_years_war:conquerors_manual"):
        grant = f'give @s {item}'
        if natural_match:
            grant = (f'execute unless items entity @s hotbar.* {item} unless items entity @s inventory.* {item} '
                     f'unless items entity @s weapon.offhand {item} run {grant}')
        equipment.append(grant)
    procedures = {}

    def put(name, *commands):
        procedures[name] = "\n".join(commands) + "\n"

    bank_enabled = banking_trial or (natural_match and rules["ruleset"] == "diplomacy")
    put("load",
        'scoreboard objectives add krts_ctrl dummy',
        'scoreboard objectives add krts_slot dummy',
        'scoreboard objectives add krts_points dummy {"text":"Devletler · Puan"}',
        'scoreboard objectives add krts_buy trigger',
        'scoreboard objectives add krts_menu trigger',
        'scoreboard objectives add krts_host trigger',
        f'scoreboard objectives add {economy.TRIGGER} trigger',
        'scoreboard objectives add krts_survey trigger',
        'scoreboard objectives add krts_survey_cd dummy',
        *[f'scoreboard objectives add {goal} trigger' for goal in
          ("krts_found_start", "krts_found", "krts_found_origin", "krts_found_profile")],
        *[f'scoreboard objectives add {goal} dummy' for goal in ("krts_found_site", "krts_origin", "krts_profile")],
        *([*[f'scoreboard objectives add {goal} dummy' for goal in natural.MATCH_OBJECTIVES],
           'scoreboard objectives add krts_build trigger',
           f'scoreboard objectives add {caravan.TRIGGER} trigger',
           f'scoreboard objectives add {alliances.SEEN} dummy',
           *[f'scoreboard objectives add {goal} trigger' for goal in alliances.TRIGGERS],
           *[f'scoreboard players add #{kind}{slot} krts_ctrl 0'
             for kind in ("stone", "iron", "posts", "linked", "districts", "housing", "fields", "fields_linked",
                          "academies", "academy_linked", "minedGold", "goldPending")
             for slot in range(1, 9)],
           *[f'execute if score #a{slot} krts_ctrl matches 1 unless score #bread{slot} krts_ctrl matches 0.. '
             f'run scoreboard players set #bread{slot} krts_ctrl {natural.STARTING_BREAD}' for slot in range(1, 9)],
           'scoreboard players add #generation krts_ctrl 0',
           'function krts:natural/generation', 'function krts:caravan/load', 'function krts:ally/load'] if natural_match else []),
        'scoreboard players add #found_left krts_ctrl 0',
        'scoreboard objectives add krts_cool dummy',
        'scoreboard objectives add krts_identity dummy',
        *[f'scoreboard objectives add {name} dummy' for name in npc.CONTROL_OBJECTIVES],
        *[f'scoreboard objectives add {name} trigger' for name in npc.TRIGGERS],
        *[f'scoreboard objectives add {name} trigger' for name in diplomacy.TRIGGERS],
        'scoreboard players add #strict krts_ctrl 0',
        f'scoreboard players set #survey_only krts_ctrl {int(survey_only)}',
        f'scoreboard players set #founding krts_ctrl {founding}',
        f'scoreboard players set #natural_match krts_ctrl {int(natural_match)}',
        f'scoreboard players set #bank_trial krts_ctrl {int(bank_enabled)}',
        *(['function krts:bank/load'] if bank_enabled else []),
        'scoreboard players add #phase krts_ctrl 0',
        'scoreboard players add #boot krts_ctrl 0',
        'scoreboard players add #capital_mode krts_ctrl 0',
        'scoreboard players add #single krts_ctrl 0',
        'scoreboard players add #arena_style krts_ctrl 0',
        'scoreboard players add #income_left krts_ctrl 0',
        'scoreboard players add #income krts_ctrl 12',
        'scoreboard players add #left krts_ctrl 0',
        'execute unless data storage krts:arena h1 run function krts:layout/legacy',
        'schedule function krts:clock 1s replace')
    put("start",
        'execute if score #survey_only krts_ctrl matches 1 run return run say [RTS] Bu dünya kuruluş bölgelerini ölçmek için açık.',
        'execute if score #natural_match krts_ctrl matches 1 run return run say [RTS] Doğal maç için /trigger krts_found_start set 1 kullan.',
        'execute unless score #phase krts_ctrl matches 0 run return run say [RTS] Önce /function krts:reset kullan.',
        'scoreboard players set #online krts_ctrl 0',
        'execute as @a[gamemode=!spectator] run scoreboard players add #online krts_ctrl 1',
        'execute unless score #online krts_ctrl matches 2..8 run return run say [RTS] Başlamak için 2–8 katılımcı gerekir.',
        'scoreboard players set #boot krts_ctrl 0',
        f'scoreboard players set #single krts_ctrl {int(rules["session_mode"] == "single")}',
        'scoreboard players set #phase krts_ctrl 5',
        'execute in minecraft:overworld run forceload add -96 -96 95 95',
        'say [RTS] Arena parçaları hazırlanıyor.')
    put("warmup",
        'execute unless score #online krts_ctrl matches 2..8 run return 0',
        'execute ' + ' '.join(f'if loaded {x+4} -60 {z+4} if loaded {x-4} -60 {z-4}'
                              for x, z in HOMES + ZONES) + ' run function krts:new_match')
    put("match/settings",
        f'scoreboard players set #single krts_ctrl {int(rules["session_mode"] == "single")}',
        f'scoreboard players set #duration krts_ctrl {rules["match_seconds"]}',
        f'scoreboard players set #target krts_ctrl {rules["victory_points"]}',
        f'scoreboard players set #capital_mode krts_ctrl {int(rules["victory_mode"] == "capital")}',
        f'scoreboard players set #strict krts_ctrl {int(rules["ruleset"] == "diplomacy")}',
        *(['function krts:caravan/init'] if natural_match else []),
        'execute if score #strict krts_ctrl matches 1 run function krts:dip/init',
        'execute if score #bank_trial krts_ctrl matches 1 if score #strict krts_ctrl matches 1 run function krts:bank/init',
        f'scoreboard players set #income krts_ctrl {rules["income_seconds"]}',
        f'scoreboard players set #income_left krts_ctrl {rules["income_seconds"]}',
        f'scoreboard players set #militia_price krts_ctrl {rules["militia_cost"]}',
        f'scoreboard players set #cooldown krts_ctrl {rules["recruit_cooldown"]}',
        f'scoreboard players set #initial krts_ctrl {rules["initial_gold"]}',
        f'scoreboard players set #price krts_ctrl {rules["archer_cost"]}',
        f'scoreboard players set #limit krts_ctrl {rules["unit_limit"]}',
        'gamerule keepInventory true', 'gamerule doMobSpawning false', 'gamerule mobGriefing false',
        'gamerule doDaylightCycle false', 'time set noon', 'gamerule doWeatherCycle false', 'weather clear',
        *[f'scoreboard players set #{kind}{slot} krts_ctrl 0'
          for slot in range(1, 9) for kind in ("p", "g", "a", "iron", "bread", "renew", "alert")])
    put("new_match",
        'function krts:clear_units',
        'scoreboard players set #slot krts_ctrl 0',
        'scoreboard players set #phase krts_ctrl 1',
        f'scoreboard players set #left krts_ctrl {rules["preparation_seconds"]}',
        'function krts:match/settings',
        'scoreboard players set #arena_style krts_ctrl 1',
        *[f'execute if score #online krts_ctrl matches {count} run function krts:layout/{count}' for count in range(2, 9)],
        'worldborder center 0 0', 'worldborder set 192',
        'scoreboard objectives setdisplay sidebar krts_points',
        'fill -4 -61 -4 4 -61 4 white_wool',
        'setblock 0 -61 0 lodestone',
        'execute as @a[gamemode=!spectator,sort=arbitrary] run function krts:enlist',
        'say [RTS] Hazırlık başladı. Kumanda asası ve HYW RTS kılavuzu çantanda.',
        'execute if score #left krts_ctrl matches 0 run function krts:begin')
    for name, homes in [("legacy", HOMES), *((str(n), ring(n)) for n in range(2, 9))]:
        put(f"layout/{name}", *[
            f'data modify storage krts:arena h{slot} set value {{slot:{slot},x:{x},z:{z},bx:{x+2},cz:{z+3}}}'
            for slot, (x, z) in enumerate(homes, 1)])
    put("home/position", '$execute positioned $(x) -60 $(z) run function krts:home/$(slot)')
    put("home/loaded", f'$return run execute if loaded $(x) {home_y} $(cz)')
    put("home/anchor", f'$execute positioned $(x) {home_y} $(cz) as @e[tag=krts.capital,tag=krts.s$(slot),distance=1..] run tp @s ~ ~ ~')
    put("home/cleanup", '$execute positioned $(x) -61 $(z) run fill ~-4 ~ ~-4 ~4 ~ ~4 grass_block')
    put("enlist", 'scoreboard players add #slot krts_ctrl 1',
        'scoreboard players operation @s krts_slot = #slot krts_ctrl',
        'function krts:match/equip',
        *[f'execute if score @s krts_slot matches {slot} run function krts:home/position with storage krts:arena h{slot}' for slot in range(1, 9)])
    put("match/equip",
        'scoreboard players set @s krts_points 0',
        'scoreboard players set @s krts_cool 0',
        'scoreboard players set @s krts_auto 0', 'scoreboard players set @s krts_focus 3',
        'scoreboard players set @s krts_defense 0',
        'gamemode adventure @s',
        *equipment)
    for slot, color in enumerate(COLORS, 1):
        put(f"home/{slot}", f'fill ~-4 ~-1 ~-4 ~4 ~-1 ~4 {color}_wool',
            'tp @s ~ ~ ~', 'spawnpoint @s ~ ~ ~',
            f'scoreboard players set #a{slot} krts_ctrl 1',
            f'scoreboard players operation #g{slot} krts_ctrl = #initial krts_ctrl',
            'data modify storage krts:args owner set from entity @s UUID',
            f'data modify storage krts:args slot set value {slot}',
            'data modify storage krts:args frozen set value 1b',
            'function krts:spawn_militia with storage krts:args',
            'execute positioned ~2 ~ ~ run function krts:spawn_archer with storage krts:args',
            f'execute if score #capital_mode krts_ctrl matches 1 positioned ~ ~ ~3 run function krts:capital/spawn/{slot}')
    for unit in ("archer", "militia"):
        put(f"spawn_{unit}",
            f'$return run summon hundred_years_war:{unit} ~ ~ ~ {{OwnerUUID:$(owner),NoAI:$(frozen),PersistenceRequired:1b,Tags:["krts.unit","krts.s$(slot)"{generation_tag}]}}')
    put("capital/spawn", '$return run summon hundred_years_war:militia ~ ~ ~ {OwnerUUID:$(owner),NoAI:1b,Invulnerable:1b,AttackStrategy:"cease_fire",PersistenceRequired:1b,Tags:["krts.capital","krts.s$(slot)"]}')
    for slot in range(1, 9):
        core = f'@e[tag=krts.capital,tag=krts.s{slot},limit=1]'
        health = rules["capital_health"]
        put(f"capital/spawn/{slot}", *([] if natural_match else ['setblock ~ ~-1 ~ gilded_blackstone']),
            'function krts:capital/spawn with storage krts:args',
            f'data merge entity {core} {{CustomName:\'{{"text":"Devlet {slot} · Başkent","color":"gold"}}\',CustomNameVisible:1b}}',
            f'attribute {core} minecraft:generic.max_health base set {health}',
            f'data merge entity {core} {{Health:{health}.0d}}')
        put(f"capital/check/{slot}",
            # This HYW release serializes Health as a double.
            f'execute unless entity @e[tag=krts.capital,tag=krts.s{slot},nbt=!{{Health:0.0d}}] run return run function krts:capital/eliminate/{slot}',
            f'function krts:home/anchor with storage krts:arena h{slot}',
            'scoreboard players add #survivors krts_ctrl 1',
            f'scoreboard players set #last_capital krts_ctrl {slot}')
        put(f"capital/eliminate/{slot}",
            f'scoreboard players set #a{slot} krts_ctrl 0',
            f'kill @e[tag=krts.unit,tag=krts.s{slot}]',
            f'kill @e[tag=krts.worker,tag=krts.s{slot}]',
            f'kill @e[tag=krts.farmer,tag=krts.s{slot}]',
            *([f'kill @e[tag=krts.townhall,tag=krts.s{slot}]', f'kill @e[tag=krts.wood_depot,tag=krts.s{slot}]',
               f'kill @e[tag=krts.post_part,tag=krts.s{slot}]', f'scoreboard players set #posts{slot} krts_ctrl 0',
               f'scoreboard players set #linked{slot} krts_ctrl 0', f'kill @e[tag=krts.district_part,tag=krts.s{slot}]',
               f'scoreboard players set #districts{slot} krts_ctrl 0', f'scoreboard players set #housing{slot} krts_ctrl 0',
               f'kill @e[tag=krts.field_part,tag=krts.s{slot}]', f'scoreboard players set #fields{slot} krts_ctrl 0',
               f'scoreboard players set #fields_linked{slot} krts_ctrl 0', f'scoreboard players set #bread{slot} krts_ctrl 0',
               f'kill @e[tag=krts.academy_part,tag=krts.s{slot}]', f'scoreboard players set #academies{slot} krts_ctrl 0',
               f'scoreboard players set #academy_linked{slot} krts_ctrl 0'] if natural_match else []),
            f'execute as @a[scores={{krts_slot={slot}}},gamemode=!spectator] run function krts:spectate',
            f'say [RTS] Devlet {slot} elendi. Başkenti yıkıldı.')
    put("capital/check", 'scoreboard players set #survivors krts_ctrl 0',
        *[f'execute if score #a{slot} krts_ctrl matches 1 store result score #home_loaded krts_ctrl run function krts:home/loaded with storage krts:arena h{slot}\n'
          f'execute if score #a{slot} krts_ctrl matches 1 unless score #home_loaded krts_ctrl matches 1 run return 0'
          for slot in range(1, 9)],
        *[f'execute if score #a{slot} krts_ctrl matches 1 run function krts:capital/check/{slot}' for slot in range(1, 9)],
        'execute if score #survivors krts_ctrl matches ..1 run function krts:capital/finish')
    put("capital/finish", 'scoreboard players set #phase krts_ctrl 3', 'function krts:freeze_units',
        'execute if score #survivors krts_ctrl matches 0 run say [RTS] Başkent kalmadı. Maç berabere.',
        'execute if score #survivors krts_ctrl matches 2.. run say [RTS] Süre doldu. Başkenti ayakta kalan devletler berabere.',
        *[f'execute if score #survivors krts_ctrl matches 1 if score #last_capital krts_ctrl matches {slot} run say [RTS] Devlet {slot} kazandı. Son başkent ayakta.'
          for slot in range(1, 9)])
    put("clock", 'schedule function krts:clock 1s replace',
        *(['execute in minecraft:overworld run function krts:natural/clear_stale with storage krts:args'] if natural_match else []),
        'execute if score #boot krts_ctrl matches 1 run function krts:server_boot',
        'execute if score #phase krts_ctrl matches 6 in minecraft:overworld run return run function krts:interrupted',
        'execute if score #phase krts_ctrl matches 0 run function krts:restore_lobby',
        'execute as @a[scores={krts_menu=1..}] run function krts:menu',
        'scoreboard players enable @a krts_menu',
        'execute as @a[tag=krts.host,scores={krts_host=1..}] run function krts:host',
        'scoreboard players enable @a[tag=krts.host] krts_host',
        'execute if score #phase krts_ctrl matches 0 run scoreboard players enable @a[tag=krts.host] krts_survey',
        'execute if score #founding krts_ctrl matches 1 if score #phase krts_ctrl matches 0 if score #found_left krts_ctrl matches 0 run scoreboard players enable @a[tag=krts.host] krts_found_start',
        *[f'execute if score #founding krts_ctrl matches 1 if score #found_left krts_ctrl matches 1.. run scoreboard players enable @a {goal}'
          for goal in ("krts_found", "krts_found_origin", "krts_found_profile")],
        *[f'execute if score #capital_mode krts_ctrl matches 1 if score #phase krts_ctrl matches 1..4 if score #a{slot} krts_ctrl matches 0 as @a[scores={{krts_slot={slot}}},gamemode=!spectator] run function krts:spectate'
          for slot in range(1, 9)],
        *[f'execute unless score #natural_match krts_ctrl matches 1 if score #phase krts_ctrl matches {phases} as @a[scores={{krts_slot=1..8}},gamemode=!spectator] run function krts:npc/player'
          for phases in ("1..2", "4")],
        *(['execute if score #phase krts_ctrl matches 1..2 in minecraft:overworld run function krts:natural/wood/clock',
           'execute if score #phase krts_ctrl matches 1..4 as @a[scores={krts_slot=1..8},gamemode=!spectator] run function krts:natural/wood/player',
           'execute if score #phase krts_ctrl matches 2 run scoreboard players enable @a[scores={krts_slot=1..8},gamemode=!spectator] krts_build',
           'execute unless score #phase krts_ctrl matches 2 run scoreboard players set @a[scores={krts_post_aim=1..}] krts_post_aim 0',
           f'execute unless score #phase krts_ctrl matches 2 run scoreboard players reset @a[scores={{{caravan.TRIGGER}=1..}}] {caravan.TRIGGER}']
          if natural_match else []),
        'execute if score #phase krts_ctrl matches 5 run return run function krts:warmup_clock',
        'execute if score #phase krts_ctrl matches 2 if score #capital_mode krts_ctrl matches 1 in minecraft:overworld run function krts:capital/check',
        'execute if score #phase krts_ctrl matches 1..2 run function krts:online_clock')
    put("warmup_clock", 'scoreboard players set #online krts_ctrl 0',
        'execute as @a[gamemode=!spectator] run scoreboard players add #online krts_ctrl 1',
        'execute in minecraft:overworld run function krts:warmup')
    put("online_clock", 'scoreboard players set #online krts_ctrl 0',
        'execute as @a[gamemode=!spectator] unless score @s krts_slot matches 1..8 run function krts:spectate',
        'execute as @a[scores={krts_slot=1..8},gamemode=!spectator] run scoreboard players add #online krts_ctrl 1',
        'execute unless score #online krts_ctrl matches 2.. run return run function krts:pause',
        'execute if score #phase krts_ctrl matches 1 run return run function krts:preparation',
        'execute in minecraft:overworld run function krts:round')
    put("preparation",
        'title @a[scores={krts_slot=1..8}] actionbar [{"text":"Hazırlık · "},{"score":{"name":"#left","objective":"krts_ctrl"}},{"text":" sn"}]',
        'scoreboard players remove #left krts_ctrl 1',
        'execute if score #left krts_ctrl matches ..0 run function krts:begin')
    put("begin", 'scoreboard players set #phase krts_ctrl 2',
        'scoreboard players operation #left krts_ctrl = #duration krts_ctrl',
        'execute unless score #strict krts_ctrl matches 1 as @a[scores={krts_slot=1..8}] run function krts:set_rivals',
        'function krts:unfreeze_units',
        ('say [RTS] Maç başladı. Belediye binanı savun ve rakip başkentleri ele geçir.' if natural_match
         else 'say [RTS] Merkez açık. Birliğini gönder, altın ve puan kazan.'))
    put("set_rivals", 'tag @s add krts.relation_self',
        'hyw relation set @a[scores={krts_slot=1..8},tag=!krts.relation_self] hostile',
        'tag @s remove krts.relation_self')
    put("spectate", 'tag @s add krts.late', 'gamemode spectator @s')
    put("round",
        'execute if score #arena_style krts_ctrl matches 1 positioned -4 -62 -4 run function krts:capture',
        *[f'execute if score #arena_style krts_ctrl matches 0 positioned {x-4} -62 {z-4} run function krts:capture' for x, z in ZONES],
        'scoreboard players remove #income_left krts_ctrl 1',
        'execute if score #strict krts_ctrl matches 1 run function krts:dip/clock',
        'execute if score #income_left krts_ctrl matches ..0 run function krts:income',
        'execute if score #bank_trial krts_ctrl matches 1 if score #strict krts_ctrl matches 1 run function krts:bank/clock',
        *(['function krts:caravan/clock'] if natural_match else []),
        'scoreboard players set #best krts_ctrl -1',
        *[f'execute if score #slot krts_ctrl matches {slot}.. run scoreboard players operation #best krts_ctrl > #p{slot} krts_ctrl'
          for slot in range(1, 9)],
        'execute as @a[scores={krts_slot=1..8},gamemode=!spectator] at @s run function krts:player',
        'scoreboard players remove #left krts_ctrl 1',
        'execute if score #capital_mode krts_ctrl matches 1 run return run function krts:capital/timer',
        'execute if score #best krts_ctrl >= #target krts_ctrl run return run function krts:finish',
        'execute if score #left krts_ctrl matches ..0 run function krts:finish')
    put("capital/timer", 'execute if score #left krts_ctrl matches ..0 run function krts:capital/finish')
    put("income", 'scoreboard players operation #income_left krts_ctrl = #income krts_ctrl',
        'execute if score #strict krts_ctrl matches 1 run function krts:dip/income',
        *[f'execute if entity @a[scores={{krts_slot={slot}}},gamemode=!spectator] run function krts:earn/{slot}' for slot in range(1, 9)])
    put("capture", 'scoreboard players set #holders krts_ctrl 0',
        *[f'execute if entity @a[scores={{krts_slot={slot}}},gamemode=!spectator] if entity @e[tag=krts.unit,tag=krts.s{slot},dx=8,dy=5,dz=8] run function krts:holder/{slot}'
          for slot in range(1, 9)],
        *[f'execute if score #holders krts_ctrl matches 1 if score #holder krts_ctrl matches {slot} run function krts:reward/{slot}'
          for slot in range(1, 9)],
        'execute if score #arena_style krts_ctrl matches 1 unless score #holders krts_ctrl matches 1 unless block ~4 ~1 ~4 lodestone run setblock ~4 ~1 ~4 lodestone')
    for slot, color in enumerate(COLORS, 1):
        put(f"holder/{slot}", 'scoreboard players add #holders krts_ctrl 1',
            f'scoreboard players set #holder krts_ctrl {slot}')
        put(f"reward/{slot}", f'scoreboard players add #p{slot} krts_ctrl 1',
            f'function krts:earn/{slot}',
            f'execute if score #arena_style krts_ctrl matches 1 unless block ~4 ~1 ~4 {color}_wool run setblock ~4 ~1 ~4 {color}_wool')
        put(f"buy/{slot}",
            'execute if score @s krts_cool matches 1.. run return run tellraw @s "[RTS] Yeni takviye için biraz bekle."',
            f'execute if score #strict krts_ctrl matches 1 if score @s krts_buy matches 1 unless score #bow{slot} krts_ctrl matches 1 run return run tellraw @s "[RTS] Okçu almak için önce Okçuluk araştır."',
            'scoreboard players operation #cost krts_ctrl = #price krts_ctrl',
            'execute if score @s krts_buy matches 2 if score #militia_price krts_ctrl matches 1.. run scoreboard players operation #cost krts_ctrl = #militia_price krts_ctrl',
            f'execute if score #g{slot} krts_ctrl < #cost krts_ctrl run return run tellraw @s "[RTS] Altın yetersiz."',
            'scoreboard players set #count krts_ctrl 0',
            f'execute as @e[tag=krts.unit,tag=krts.s{slot}] run scoreboard players add #count krts_ctrl 1',
            *(['scoreboard players operation #unit_cap krts_ctrl = #limit krts_ctrl',
               f'scoreboard players operation #unit_cap krts_ctrl += #housing{slot} krts_ctrl'] if natural_match else []),
            f'execute if score #count krts_ctrl >= {"#unit_cap" if natural_match else "#limit"} krts_ctrl run return run tellraw @s "[RTS] Birlik sınırına ulaştın."',
            'data modify storage krts:args owner set from entity @s UUID',
            f'data modify storage krts:args slot set value {slot}',
            'data modify storage krts:args frozen set value 0b',
            f'execute store success score #bought krts_ctrl run function krts:buy/position with storage krts:arena h{slot}',
            f'execute if score #bought krts_ctrl matches 1 run scoreboard players operation #g{slot} krts_ctrl -= #cost krts_ctrl',
            'execute if score #bought krts_ctrl matches 1 run scoreboard players operation @s krts_cool = #cooldown krts_ctrl')
        put(f"hud/{slot}", f'scoreboard players operation @s krts_points = #p{slot} krts_ctrl',
            f'execute if score #capital_mode krts_ctrl matches 1 store result score #hp{slot} krts_ctrl run data get entity @e[tag=krts.capital,tag=krts.s{slot},limit=1] Health',
            f'execute if score #capital_mode krts_ctrl matches 1 run title @s actionbar [{{"text":"Başkent "}},{{"score":{{"name":"#hp{slot}","objective":"krts_ctrl"}}}},{{"text":" · Altın "}},{{"score":{{"name":"#g{slot}","objective":"krts_ctrl"}}}},{{"text":" · "}},{{"score":{{"name":"#left","objective":"krts_ctrl"}}}},{{"text":" sn"}}]',
            f'execute unless score #capital_mode krts_ctrl matches 1 run title @s actionbar [{{"text":"Devlet {slot} · Altın "}},{{"score":{{"name":"#g{slot}","objective":"krts_ctrl"}}}},{{"text":" · Süre "}},{{"score":{{"name":"#left","objective":"krts_ctrl"}}}},{{"text":" sn"}}]')
    put("buy/position", f'$execute if score @s krts_buy matches 2 positioned $(bx) {home_y} $(z) run return run function krts:spawn_militia with storage krts:args',
        f'$return run execute positioned $(bx) {home_y} $(z) run function krts:spawn_archer with storage krts:args')
    put("player",
        'execute if score @s krts_cool matches 1.. run scoreboard players remove @s krts_cool 1',
        'execute if score #strict krts_ctrl matches 1 run function krts:dip/player',
        'execute if score #bank_trial krts_ctrl matches 1 if score #strict krts_ctrl matches 1 run function krts:bank/player',
        *(['function krts:caravan/player'] if natural_match else []),
        *[f'execute if score @s krts_buy matches 1..2 if score @s krts_slot matches {slot} in minecraft:overworld run function krts:buy/{slot}'
          for slot in range(1, 9)],
        'scoreboard players set @s krts_buy 0', 'scoreboard players enable @s krts_buy',
        *[f'execute if score @s krts_slot matches {slot} run function krts:hud/{slot}' for slot in range(1, 9)])
    put("freeze_units", 'execute in minecraft:overworld as @e[tag=krts.unit] run data merge entity @s {NoAI:1b}',
        'execute in minecraft:overworld as @e[tag=krts.capital] run data merge entity @s {Invulnerable:1b}',
        *([f'execute in minecraft:overworld as @e[tag=krts.{kind}_core] run data merge entity @s {{Invulnerable:1b}}'
           for kind in ("post", "district", "field", "academy")] if natural_match else []))
    put("unfreeze_units", 'execute in minecraft:overworld as @e[tag=krts.unit] run data merge entity @s {NoAI:0b}',
        'execute in minecraft:overworld as @e[tag=krts.capital] run data merge entity @s {Invulnerable:0b}',
        *([f'execute in minecraft:overworld as @e[tag=krts.{kind}_core] run data merge entity @s {{Invulnerable:0b}}'
           for kind in ("post", "district", "field", "academy")] if natural_match else []))
    put("finish", 'scoreboard players set #phase krts_ctrl 3', 'function krts:freeze_units',
        'say [RTS] Maç bitti. En yüksek puanı paylaşan devletler:',
        *[f'execute if score #slot krts_ctrl matches {slot}.. if score #p{slot} krts_ctrl = #best krts_ctrl run say [RTS] Devlet {slot}'
          for slot in range(1, 9)])
    put("pause", 'execute unless score #phase krts_ctrl matches 1..2 run return 0',
        'scoreboard players operation #resume krts_ctrl = #phase krts_ctrl',
        'scoreboard players set #phase krts_ctrl 4',
        *([f'scoreboard players reset * {economy.TRIGGER}'] if bank_enabled else []),
        *([f'scoreboard players reset * {goal}' for goal in alliances.TRIGGERS] if natural_match else []),
        'function krts:freeze_units',
        'say [RTS] Puan, süre ve satın alma durdu. Devam için /function krts:resume kullan.')
    put("resume", 'execute unless score #phase krts_ctrl matches 4 run return 0',
        'execute if score #boot krts_ctrl matches 1 if score #single krts_ctrl matches 1 run return run function krts:server_boot',
        'scoreboard players set #online krts_ctrl 0',
        'execute as @a[scores={krts_slot=1..8},gamemode=!spectator] run scoreboard players add #online krts_ctrl 1',
        'execute unless score #online krts_ctrl matches 2.. run return run say [RTS] Devam etmek için en az iki kayıtlı oyuncu gerekir.',
        'scoreboard players set #boot krts_ctrl 0',
        'scoreboard players operation #phase krts_ctrl = #resume krts_ctrl',
        'execute if score #phase krts_ctrl matches 2 run function krts:unfreeze_units',
        'say [RTS] Maç devam ediyor.')
    put("status", 'tellraw @a [{"text":"[RTS] Aşama "},{"score":{"name":"#phase","objective":"krts_ctrl"}},{"text":" · Kalan "},{"score":{"name":"#left","objective":"krts_ctrl"}}]',
        'scoreboard players get #phase krts_ctrl', 'scoreboard players get #left krts_ctrl')
    actions = {"Başlat": "/trigger krts_host set 1", "Duraklat": "/trigger krts_host set 2", "Devam": "/trigger krts_host set 3"}
    purchases = {"Okçu al": "/trigger krts_buy set 1", "Milis al": "/trigger krts_buy set 2"}
    def buttons(labels):
        return json.dumps([{"text": f"[{label}] ", "color": "gold", "clickEvent": {
            "action": "run_command", "value": command}} for label, command in labels.items()], ensure_ascii=False)
    put("menu", 'scoreboard players set @s krts_menu 0',
        ('tellraw @s "Devletler RTS · başkentini savun."' if natural_match
         else 'tellraw @s "Devletler RTS · birliğinle merkezi tut ve başkentini savun."'),
        f'execute if score #phase krts_ctrl matches 2 if score @s krts_slot matches 1..8 unless entity @s[gamemode=spectator] run tellraw @s {buttons(purchases)}',
        *[f'execute unless score #natural_match krts_ctrl matches 1 if score #phase krts_ctrl matches {phases} if score @s krts_slot matches 1..8 unless entity @s[gamemode=spectator] run function krts:npc/menu'
          for phases in ("1..2", "4")],
        *(['execute if score #phase krts_ctrl matches 1..4 if score @s krts_slot matches 1..8 unless entity @s[gamemode=spectator] '
           'in minecraft:overworld run function krts:natural/wood/menu',
           'execute if score #phase krts_ctrl matches 2 if score @s krts_slot matches 1..8 unless entity @s[gamemode=spectator] '
           'in minecraft:overworld run function krts:natural/post/menu',
           'execute if score #phase krts_ctrl matches 2 if score @s krts_slot matches 1..8 unless entity @s[gamemode=spectator] '
           'run function krts:caravan/menu'] if natural_match else []),
        'execute if score #strict krts_ctrl matches 1 if score #phase krts_ctrl matches 2 if score @s krts_slot matches 1..8 unless entity @s[gamemode=spectator] run function krts:dip/help',
        f'execute if score #bank_trial krts_ctrl matches 1 if score #strict krts_ctrl matches 1 if score #phase krts_ctrl matches 2 if score @s krts_slot matches 1..8 unless entity @s[gamemode=spectator] run tellraw @s {buttons({"Hazine ve banka": "/trigger krts_bank set 4"})}',
        f'execute unless score #founding krts_ctrl matches 1 if entity @s[tag=krts.host] run tellraw @s {buttons(actions)}',
        f'execute if score #natural_match krts_ctrl matches 1 if score #phase krts_ctrl matches 1..4 if entity @s[tag=krts.host] run tellraw @s {buttons({label: value for label, value in actions.items() if label != "Başlat"})}',
        f'execute if score #founding krts_ctrl matches 1 if score #phase krts_ctrl matches 0 if score #found_left krts_ctrl matches 0 if entity @s[tag=krts.host] run tellraw @s {buttons({"Kuruluşu başlat": "/trigger krts_found_start set 1"})}',
        f'execute if score #founding krts_ctrl matches 1 if score #found_left krts_ctrl matches 1.. run tellraw @s {buttons({"Kuruluş seçimleri": "/trigger krts_found set 99"})}',
        f'execute if score #phase krts_ctrl matches 0 if entity @s[tag=krts.host] run tellraw @s {buttons({"Araziyi tara": "/trigger krts_survey set 1"})}')
    put("host", 'scoreboard players operation #action krts_ctrl = @s krts_host', 'scoreboard players set @s krts_host 0',
        *[f'execute if score #action krts_ctrl matches {i} run function krts:{name}' for i, name in enumerate(("start", "pause", "resume"), 1)])
    put("server_boot", 'scoreboard players set #boot krts_ctrl 0',
        'execute if score #phase krts_ctrl matches 5 run scoreboard players set #phase krts_ctrl 6',
        'execute if score #single krts_ctrl matches 1 if score #phase krts_ctrl matches 1..4 run scoreboard players set #phase krts_ctrl 6',
        'execute if score #phase krts_ctrl matches 1..2 run function krts:pause')
    put("interrupted",
        *[f'execute if score #slot krts_ctrl matches {slot}.. store result score #home_loaded krts_ctrl run function krts:home/loaded with storage krts:arena h{slot}\n'
          f'execute if score #slot krts_ctrl matches {slot}.. unless score #home_loaded krts_ctrl matches 1 run return 0'
          for slot in range(1, 9)],
        'function krts:reset', 'say [RTS] Önceki oturum yeniden açılışta kapatıldı.')
    put("clear_units",
        'execute in minecraft:overworld run kill @e[tag=krts.unit]',
        'execute in minecraft:overworld run kill @e[tag=krts.capital]',
        'execute in minecraft:overworld run kill @e[tag=krts.worker]',
        'execute in minecraft:overworld run kill @e[tag=krts.farmer]',
        'execute in minecraft:overworld run kill @e[tag=krts.guardpost]',
        *(['execute in minecraft:overworld run kill @e[tag=krts.townhall]',
           'execute in minecraft:overworld run kill @e[tag=krts.wood_depot]',
           'execute in minecraft:overworld run kill @e[tag=krts.post_part]',
           'execute in minecraft:overworld run kill @e[tag=krts.district_part]',
           'execute in minecraft:overworld run kill @e[tag=krts.field_part]',
           'execute in minecraft:overworld run kill @e[tag=krts.academy_part]',
           'execute in minecraft:overworld as @e[tag=krts.post_ticket] at @s run function krts:natural/post/release'] if natural_match else []),
        'execute in minecraft:overworld as @e[tag=krts.yard] at @s run function krts:npc/cleanup',
        'execute in minecraft:overworld as @e[tag=krts.farm] at @s run function krts:npc/cleanup_farm')
    put("restore_lobby", 'gamemode adventure @a[tag=krts.late]', 'tag @a[tag=krts.late] remove krts.late')
    put("reset", 'execute if score #survey_only krts_ctrl matches 1 run return 0',
        *(['scoreboard players set #found_left krts_ctrl -1'] if natural_match else []),
        'scoreboard players set #phase krts_ctrl 0', 'function krts:clear_units',
        *(['function krts:bank/init', f'scoreboard players reset * {economy.TRIGGER}'] if bank_enabled else []),
        *[f'execute if score #slot krts_ctrl matches {slot}.. in minecraft:overworld run function krts:{"natural/release" if natural_match else "home/cleanup"} with storage krts:arena h{slot}' for slot in range(1, 9)],
        *(['execute if data storage krts:arena center.y in minecraft:overworld run function krts:natural/lobby with storage krts:arena center',
           'scoreboard players reset * krts_identity',
           *[f'scoreboard players reset * {goal}' for goal in natural.MATCH_OBJECTIVES],
           'scoreboard players reset * krts_build',
           'function krts:caravan/init',
           'function krts:ally/init',
           'data remove storage krts:outpost args',
           *[f'scoreboard players set #{kind}{slot} krts_ctrl 0'
             for kind in ("wood", "wood_blocked", "stone", "stone_blocked", "iron", "iron_blocked", "g_blocked", "goldPending", "minedGold",
                          "posts", "linked", "districts", "housing", "fields", "fields_linked", "academies", "academy_linked",
                          "bread", "ration_at")
             for slot in range(1, 9)],
           'scoreboard players set #slot krts_ctrl 0', 'worldborder set 59999968',
           'scoreboard players add #generation krts_ctrl 1', 'function krts:natural/generation']
          if natural_match else ['execute in minecraft:overworld run forceload remove -96 -96 95 95']),
        'function krts:restore_lobby',
        'scoreboard players reset * krts_slot', 'scoreboard players reset * krts_points', 'scoreboard players reset * krts_buy',
        *[f'scoreboard players reset * {name}' for name in (*npc.CONTROL_OBJECTIVES, *npc.TRIGGERS)],
        'say [RTS] Prototip maçı sıfırlandı.')
    diplomacy.generate(put, rules, natural_match=natural_match)
    npc.generate(put, buttons)
    economy.generate(put, buttons, rules["capital_health"], natural_match=natural_match)
    if natural_match:
        natural.generate(put, COLORS, buttons)
        caravan.generate(put, buttons, economy.TREASURY_LIMIT)
        alliances.generate(put, rules)

    with ZipFile(destination, "w", ZIP_DEFLATED) as pack:
        pack.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 48, "description": "Kapışma · Devletler RTS prototipi"}}))
        pack.writestr("data/minecraft/tags/function/load.json", '{"values":["krts:load"]}')
        for name, code in procedures.items():
            pack.writestr(f"data/krts/function/{name}.mcfunction", code)


def settings(path, options, version):
    config = {}
    if path.exists():
        lines = path.read_text(encoding="utf-8").splitlines()
        config = json.loads("\n".join(line for line in lines if not line.lstrip().startswith("//")))
    config.setdefault("configVersion", version)
    config.update(options)
    path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")


def write_launchers():
    (SANDBOX / "start_tailnet.ps1").write_text(
        (HERE / "start_tailnet.ps1").read_text(encoding="utf-8"), encoding="utf-8-sig")
    for name, args in (("prototip", "-Local"), ("tailscale-classic", "-Preset classic"),
                       ("tailscale-diplomacy", "-Preset diplomacy"), ("dogal-yerel", "-Natural -Local"),
                       ("dogal-tailscale-classic", "-Natural -Preset classic"),
                       ("dogal-tailscale-diplomacy", "-Natural -Preset diplomacy")):
        (SANDBOX / f"baslat-{name}.cmd").write_text(
            f'@echo off\npowershell.exe -NoProfile -File "%~dp0start_tailnet.ps1" {args}\n'
            'if errorlevel 1 pause\n', encoding="ascii")


def prepare(source, warfare, rules):
    if SANDBOX.is_symlink() or SANDBOX.resolve() != SANDBOX:
        raise ValueError("Prototip klasörünün gerçek yolu beklenen konumdan farklı.")
    with warfare.open("rb") as mod_file:
        if hashlib.file_digest(mod_file, "sha512").hexdigest() != SHA512:
            raise ValueError("Hundred Years Warfare dosyası yayımlanan özete uymuyor.")
    fabric = source / "mods" / "fabric-api-0.116.18+1.21.1.jar"
    for path in (fabric, source / "fabric-server-launch.jar", source / "eula.txt"):
        if not path.is_file():
            raise ValueError(f"Fabric çalışma dosyası bulunamadı: {path}")
    if "eula=true" not in (source / "eula.txt").read_text(encoding="utf-8").splitlines():
        raise ValueError("Kaynak Fabric test profilinin EULA dosyasında eula=true bulunamadı.")
    for directory in ("mods", "config/hundredyearswar", "world/datapacks"):
        (SANDBOX / directory).mkdir(parents=True, exist_ok=True)
    for name in ("libraries", "versions", ".fabric"):
        if (source / name).is_dir() and not (SANDBOX / name).exists():
            shutil.copytree(source / name, SANDBOX / name)
    for path in (source / "fabric-server-launch.jar", source / "eula.txt"):
        shutil.copy2(path, SANDBOX / path.name)
    for path in (fabric, warfare):
        shutil.copy2(path, SANDBOX / "mods" / path.name)
    build_adapter(warfare, SANDBOX / "mods/kapisma-rts-rules.jar")
    write_launchers()
    for preset in ("classic", "diplomacy"):
        make_pack(SANDBOX / f"preset-{preset}.zip", {**rules, "ruleset": preset})
        make_pack(SANDBOX / f"preset-natural-{preset}.zip", {**rules, "ruleset": preset, "victory_mode": "capital"},
                  natural_match=True)
    (SANDBOX / "server.properties").write_text("\n".join([
        "server-ip=127.0.0.1", "server-port=25576", "online-mode=true", "max-players=8",
        "gamemode=adventure", "difficulty=normal", "pvp=false", "view-distance=8", "simulation-distance=4",
        "spawn-protection=0", "allow-nether=false", "generate-structures=false", "level-name=world",
        "level-type=minecraft:flat", f"generator-settings={json.dumps(FLAT, separators=(',', ':'))}", "",
    ]), encoding="ascii")
    config_dir = SANDBOX / "config" / "hundredyearswar"
    settings(config_dir / "hyw_main.json5", {
        "enableRecruitSystem": False, "enableScrollLootGeneration": False, "enableScrollMobDrops": False,
        "enableFirstJoinManual": False, "allowSoldierRecall": False, "allowSiegeRecall": False,
        "allowEquipmentChange": False, "dropEquipment": False, "enableSiegeCollapse": False,
        "enableStructureGeneration": False, "enablePlayerPuppets": False, "enableSupplySystem": False,
        "enableWorshipMechanism": False, "enablePlayerFriendlyFire": False,
        "baseCombatEntityPathfindingRange": 24.0, "freecamSurvivalRange": 192.0,
        "buildingPlacementBudgetMillis": 4.0,
    }, 25)
    settings(config_dir / "hyw_global_modifier.json5", {"globalRangedAttackRangeMultiplier": 0.85}, 4)
    make_pack(SANDBOX / "world" / "datapacks" / "devletler-rts.zip", rules)


def build_adapter(warfare, destination):
    mapped = SANDBOX / ".fabric/remappedJars/minecraft-1.21.1-0.20.1/server-intermediary.jar"
    if not mapped.is_file():
        raise ValueError("Fabric 1.21.1 / 0.20.1 ara eşleme önbelleği eksik. --runtime ile açılmış bir profil gösterin.")
    javac = Path(os.environ["JAVA_HOME"]) / "bin/javac.exe" if "JAVA_HOME" in os.environ else "javac"
    libraries = list((SANDBOX / "libraries").rglob("*.jar"))
    with tempfile.TemporaryDirectory(prefix="kapisma-rts-") as temporary:
        out = Path(temporary)
        subprocess.run([javac, "-proc:none", "-encoding", "UTF-8", "--release", "21", "-cp",
                        os.pathsep.join(map(str, [mapped, warfare, *libraries])), "-d", str(out),
                        *map(str, sorted((HERE / "adapter").glob("*.java")))], check=True)
        artifact = out / "adapter.jar"
        with ZipFile(artifact, "w", ZIP_DEFLATED) as jar:
            for item in out.rglob("*.class"):
                jar.write(item, item.relative_to(out).as_posix())
            jar.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "kapisma_rts_rules",
                "version": "0.20.1", "name": "Kapışma RTS Rules", "license": "MIT", "environment": "server",
                "mixins": ["kapisma-rts.mixins.json"], "depends": {"minecraft": "1.21.1", "hundred_years_war": "*"}}))
            jar.writestr("kapisma-rts.mixins.json", json.dumps({"required": True, "package": "kapisma.rts.mixin",
                "compatibilityLevel": "JAVA_21", "mixins": [
                    "ServerMixin", "RelationMixin", "DamageMixin", "ControlMixin", "WorkerMixin", "GuardMixin",
                    "FoundingTriggerMixin", "WoodOrderMixin"],
                "injectors": {"defaultRequire": 1}}))
        shutil.copy2(artifact, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path)
    parser.add_argument("--warfare", type=Path)
    parser.add_argument("--run", action="store_true")
    args = parser.parse_args()
    warfare = args.warfare or next((PROJECT / "_yedek").glob(f"sovereign-uyum-*/{MOD}"), None)
    if not warfare or not warfare.is_file():
        parser.error("Doğrulanabilir HYW dosyasını --warfare ile gösterin.")
    runtime = args.runtime or warfare.parent / "hundred-years-server"
    try:
        import msvcrt
        if SANDBOX.is_symlink() or SANDBOX.resolve() != SANDBOX:
            raise ValueError("Prototip klasörünün gerçek yolu beklenen konumdan farklı.")
        SANDBOX.mkdir(parents=True, exist_ok=True)
        try:
            lock = (SANDBOX / "rts-launch.lock").open("a+b")
        except PermissionError as exc:
            raise ValueError("RTS profil kilidi açılamadı. Çalışan RTS başlatıcısını kapatın.") from exc
        with lock:
            lock.seek(0)
            try:
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError as exc:
                raise ValueError("Önce çalışan RTS başlatıcısını veya hazırlama işlemini kapatın.") from exc
            properties = SANDBOX / "server.properties"
            if properties.is_file():
                connection_settings = dict(line.split("=", 1) for line in properties.read_text(encoding="ascii").splitlines()
                                           if line.startswith(("server-ip=", "server-port=")))
                try:
                    connection = socket.create_connection(
                        (connection_settings.get("server-ip") or "127.0.0.1",
                         int(connection_settings.get("server-port", 25576))), timeout=0.5)
                except OSError:
                    pass
                else:
                    connection.close()
                    raise ValueError("Önce çalışan prototip sunucusunu kapatın.")
            prepare(runtime.resolve(), warfare.resolve(), read_rules())
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    print(f"Devletler RTS prototipi: {SANDBOX}", flush=True)
    if args.run:
        return subprocess.call(["powershell.exe", "-NoProfile", "-File", SANDBOX / "start_tailnet.ps1",
                                "-Local"], cwd=SANDBOX)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
