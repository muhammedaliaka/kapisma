"""Yaşayan Harita: team clue-chain race, Minecraft 1.21.1, dedicated QA world."""
from pathlib import Path
import json

# Mirrored routes. Both teams have the same length and landmark sequence.
BASE_POINTS=[(-24,-60,-8),(-8,-60,-22),(12,-60,-8),(26,-60,-22)]
POINTS={"red":BASE_POINTS,"blue":[(x,y,-z) for x,y,z in BASE_POINTS]}
LABELS=["Taş sütun","Bakır kemer","Altın işaret","Zümrüt anıt"]
BLOCKS=["stone_bricks","copper_block","gold_block","emerald_block"]

def generate():
    f={}
    def add(n,*lines):f[n]="\n".join(lines)+"\n"
    add("load","scoreboard objectives add yh_ctrl dummy","scoreboard objectives add yh_epoch dummy","scoreboard objectives add yh_seen dummy","scoreboard objectives add yh_find trigger","scoreboard objectives add yh_hint trigger",
        "team add yh_red","team add yh_blue","team modify yh_red color red","team modify yh_blue color aqua",
        "team modify yh_red friendlyFire false","team modify yh_blue friendlyFire false",
        "gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doImmediateRespawn true","gamerule keepInventory true",
        *[f"execute unless score #{k} yh_ctrl matches -2147483648..2147483647 run scoreboard players set #{k} yh_ctrl 0" for k in ("phase","epoch","red","blue","winner","left")],
        "schedule function yh:tick 1t replace","schedule function yh:clock 1s replace")
    add("start","execute if score #phase yh_ctrl matches 0 run function yh:validate")
    add("validate","scoreboard players set #count yh_ctrl 0","execute as @a[gamemode=!spectator] run scoreboard players add #count yh_ctrl 1",
        "execute if score #count yh_ctrl matches 2..8 run function yh:begin")
    arena=["execute in minecraft:overworld run fill -34 -61 -28 34 -61 28 smooth_stone"]
    for team,points in POINTS.items():
        for (x,y,z),block in zip(points,BLOCKS):
            arena.append(f"execute in minecraft:overworld run fill {x-2} -61 {z-2} {x+2} -61 {z+2} minecraft:{block}")
            arena.append(f"execute in minecraft:overworld run fill {x} -60 {z} {x} -57 {z} minecraft:{block}")
    add("arena",*arena)
    add("begin","function yh:arena","execute as @a[tag=yh.member] run function yh:cleanup","scoreboard players add #epoch yh_ctrl 1",
        "scoreboard players set #assign yh_ctrl 0","scoreboard players set #red yh_ctrl 0","scoreboard players set #blue yh_ctrl 0",
        "scoreboard players set #winner yh_ctrl 0","scoreboard players set #left yh_ctrl 180",
        "execute as @a[gamemode=!spectator] run function yh:assign","scoreboard players set #phase yh_ctrl 1",
        'tellraw @a {"text":"[YH] Harita zinciri başladı. Pusula ile hedefe git, /trigger yh_find ile doğrula. /trigger yh_hint ipucunu tekrar gösterir.","color":"gold"}')
    add("assign","tag @s add yh.member","scoreboard players operation @s yh_epoch = #epoch yh_ctrl",
        "execute if score #assign yh_ctrl matches 0 run tag @s add yh.red","execute if score #assign yh_ctrl matches 1 run tag @s add yh.blue",
        "execute if entity @s[tag=yh.red] run team join yh_red @s","execute if entity @s[tag=yh.blue] run team join yh_blue @s",
        "gamemode adventure @s","clear @s","scoreboard players set @s yh_seen -1","function yh:spawn",
        "scoreboard players add #assign yh_ctrl 1","execute if score #assign yh_ctrl matches 2 run scoreboard players set #assign yh_ctrl 0")
    add("spawn","execute if entity @s[tag=yh.red] in minecraft:overworld run tp @s -30 -60 -4",
        "execute if entity @s[tag=yh.blue] in minecraft:overworld run tp @s -30 -60 4","execute at @s run spawnpoint @s ~ ~ ~")
    add("cleanup","clear @s","effect clear @s","team leave @s","tag @s remove yh.member","tag @s remove yh.red","tag @s remove yh.blue",
        "scoreboard players reset @s yh_find","scoreboard players reset @s yh_hint","scoreboard players reset @s yh_seen",
        "gamemode adventure @s","execute in minecraft:overworld run tp @s 0 -60 0","execute in minecraft:overworld run spawnpoint @s 0 -60 0")
    add("observer","tag @s add yh.observer","gamemode spectator @s")
    add("release_observer","tag @s remove yh.observer","gamemode adventure @s")
    add("tick","execute as @a[tag=yh.member] unless score @s yh_epoch = #epoch yh_ctrl run function yh:cleanup",
        "execute if score #phase yh_ctrl matches 0 as @a[tag=yh.member] run function yh:cleanup",
        "execute if score #phase yh_ctrl matches 0 as @a[tag=yh.observer] run function yh:release_observer",
        "execute if score #phase yh_ctrl matches 1..2 as @a[tag=!yh.member,gamemode=!spectator] run function yh:observer",
        "execute if score #phase yh_ctrl matches 1 run function yh:ready",
        "execute if score #phase yh_ctrl matches 1 if score #readyR yh_ctrl matches 1.. if score #readyB yh_ctrl matches 1.. run function yh:active",
        "execute if score #phase yh_ctrl matches 1 if score #readyR yh_ctrl matches 0 run scoreboard players reset * yh_find",
        "execute if score #phase yh_ctrl matches 1 if score #readyB yh_ctrl matches 0 run scoreboard players reset * yh_find",
        "execute if score #phase yh_ctrl matches 2 as @a[tag=yh.member,gamemode=!spectator] if score @s yh_epoch = #epoch yh_ctrl run gamemode spectator @s",
        "schedule function yh:tick 1t replace")
    add("active","scoreboard players set #advanceR yh_ctrl 0","scoreboard players set #advanceB yh_ctrl 0",
        "execute as @a[tag=yh.member,gamemode=!spectator] if score @s yh_epoch = #epoch yh_ctrl run function yh:player",
        "execute if score #advanceR yh_ctrl matches 1 run scoreboard players add #red yh_ctrl 1",
        "execute if score #advanceB yh_ctrl matches 1 run scoreboard players add #blue yh_ctrl 1",
        "execute if score #red yh_ctrl matches 4.. run function yh:finish",
        "execute if score #phase yh_ctrl matches 1 if score #blue yh_ctrl matches 4.. run function yh:finish")
    add("player","effect give @s resistance 1 255 true","effect give @s saturation 1 5 true",
        "execute at @s unless dimension minecraft:overworld run function yh:spawn",
        "execute in minecraft:overworld positioned -34 -60 -28 unless entity @s[dx=68,dy=8,dz=56] run function yh:spawn",
        *[f"execute if entity @s[tag=yh.{team}] unless score @s yh_seen = #{team} yh_ctrl run function yh:update_{team}" for team in POINTS],
        "execute if score @s yh_hint matches 1.. run function yh:hint",
        "execute if score @s yh_find matches 1.. run function yh:find",
        "scoreboard players enable @s yh_find","scoreboard players enable @s yh_hint")
    for team,points in POINTS.items():
        update=[f"scoreboard players operation @s yh_seen = #{team} yh_ctrl","scoreboard players set @s yh_find 0","scoreboard players set @s yh_hint 0"]
        for i,((x,y,z),label) in enumerate(zip(points,LABELS)):
            clue=f"[YH] Hedef {i+1}/4: {label}. Pusulayı takip et; anıtın yanında /trigger yh_find."
            comp='{"text":"'+label+'","italic":false,"color":"gold"}'
            item=f'minecraft:compass[minecraft:custom_name=\'{comp}\',minecraft:lodestone_tracker={{target:{{pos:[I;{x},{y},{z}],dimension:"minecraft:overworld"}},tracked:false}}]'
            add(f"clue_{team}{i}",f"item replace entity @s hotbar.0 with {item}",f"tellraw @s {json.dumps({'text':clue,'color':'gold'},ensure_ascii=False)}")
            update.append(f"execute if score #{team} yh_ctrl matches {i} run function yh:clue_{team}{i}")
        add("update_"+team,*update)
    hint=["scoreboard players set @s yh_hint 0"]
    find=["scoreboard players set @s yh_find 0","scoreboard players set #valid yh_ctrl 0"]
    for team,points in POINTS.items():
        for i,(x,y,z) in enumerate(points):
            hint.append(f"execute if entity @s[tag=yh.{team}] if score #{team} yh_ctrl matches {i} run function yh:clue_{team}{i}")
            find.append(f"execute if entity @s[tag=yh.{team}] if score @s yh_seen = #{team} yh_ctrl if score #{team} yh_ctrl matches {i} in minecraft:overworld positioned {x+0.5} {y} {z+0.5} if entity @s[distance=..3] run scoreboard players set #valid yh_ctrl 1")
        find.append(f"execute if score #valid yh_ctrl matches 1 if entity @s[tag=yh.{team}] run scoreboard players set #advance{team[0].upper()} yh_ctrl 1")
    find.append('execute if score #valid yh_ctrl matches 0 run tellraw @s {"text":"[YH] Henüz doğru hedefte değilsin.","color":"red"}')
    add("hint",*hint);add("find",*find)
    add("clock","execute if score #phase yh_ctrl matches 1 run function yh:timer","schedule function yh:clock 1s replace")
    add("ready","scoreboard players set #readyR yh_ctrl 0","scoreboard players set #readyB yh_ctrl 0",
        "execute as @a[tag=yh.member,tag=yh.red,gamemode=!spectator] if score @s yh_epoch = #epoch yh_ctrl run scoreboard players add #readyR yh_ctrl 1",
        "execute as @a[tag=yh.member,tag=yh.blue,gamemode=!spectator] if score @s yh_epoch = #epoch yh_ctrl run scoreboard players add #readyB yh_ctrl 1")
    add("timer","function yh:ready",
        "execute if score #readyR yh_ctrl matches 1.. if score #readyB yh_ctrl matches 1.. run scoreboard players remove #left yh_ctrl 1",
        "execute if score #left yh_ctrl matches ..0 run function yh:finish",
        'title @a actionbar [{"text":"Kırmızı ","color":"red"},{"score":{"name":"#red","objective":"yh_ctrl"}},{"text":"/4 | Mavi ","color":"aqua"},{"score":{"name":"#blue","objective":"yh_ctrl"}},{"text":"/4 | Süre "},{"score":{"name":"#left","objective":"yh_ctrl"}}]')
    add("finish","scoreboard players set #phase yh_ctrl 2","scoreboard players set #winner yh_ctrl 3",
        "execute if score #red yh_ctrl > #blue yh_ctrl run scoreboard players set #winner yh_ctrl 1","execute if score #blue yh_ctrl > #red yh_ctrl run scoreboard players set #winner yh_ctrl 2",
        "execute if score #winner yh_ctrl matches 1 run say [YH] Kırmızı kazandı.","execute if score #winner yh_ctrl matches 2 run say [YH] Mavi kazandı.","execute if score #winner yh_ctrl matches 3 run say [YH] Berabere.")
    add("reset","scoreboard players set #phase yh_ctrl 0","execute as @a[tag=yh.member] run function yh:cleanup","execute as @a[tag=yh.observer] run function yh:release_observer",
        "scoreboard players reset * yh_find","scoreboard players reset * yh_hint",
        *[f"scoreboard players set #{k} yh_ctrl 0" for k in ("red","blue","left","winner")])
    return f

def install(root):
    root=Path(root);assert root.name=="yasayan-harita"
    root.mkdir(parents=True,exist_ok=True)
    (root/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":48,"description":"Yasayan Harita QA v0.1"}}),encoding="utf-8")
    d=root/"data/yh/function";d.mkdir(parents=True,exist_ok=True)
    for n,v in generate().items():(d/(n+".mcfunction")).write_text(v,encoding="utf-8",newline="\n")
    t=root/"data/minecraft/tags/function";t.mkdir(parents=True,exist_ok=True)
    (t/"load.json").write_text(json.dumps({"values":["yh:load"]}),encoding="utf-8")