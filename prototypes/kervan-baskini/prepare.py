"""Kervan Baskını v0.1 — standalone vanilla 1.21.1 datapack, dedicated QA world only."""
from pathlib import Path
import json

def generate():
    f={}
    def add(n,*lines): f[n]="\n".join(lines)+"\n"
    ctrl="kb_ctrl"
    add("load","scoreboard objectives add kb_ctrl dummy","scoreboard objectives add kb_epoch dummy","scoreboard objectives add kb_leg dummy","scoreboard objectives add kb_death deathCount",
        "team add kb_red","team add kb_blue","team modify kb_red color red","team modify kb_blue color aqua",
        "team modify kb_red friendlyFire false","team modify kb_blue friendlyFire false",
        "gamerule keepInventory true","gamerule doImmediateRespawn true","gamerule doMobSpawning false","gamerule doDaylightCycle false",
        *[f"execute unless score #{k} kb_ctrl matches -2147483648..2147483647 run scoreboard players set #{k} kb_ctrl 0" for k in ("phase","epoch","leg","progress","cargo","left","winner","rProgress","rCargo","rTime","bProgress","bCargo","bTime","break")],
        "schedule function kb:clock 1s replace","schedule function kb:maintain 1t replace")
    add("start","execute if score #phase kb_ctrl matches 0 run function kb:validate")
    add("validate","scoreboard players set #count kb_ctrl 0","execute as @a[gamemode=!spectator] run scoreboard players add #count kb_ctrl 1",
        'execute unless score #count kb_ctrl matches 2..8 run tellraw @a {"text":"[KB] 2–8 aktif oyuncu gerekli.","color":"red"}',
        "execute if score #count kb_ctrl matches 2..8 run function kb:begin")
    add("arena","execute in minecraft:overworld run fill -34 -61 -14 34 -61 14 smooth_stone",
        "execute in minecraft:overworld run fill -24 -61 -2 24 -61 2 gravel",
        "execute in minecraft:overworld run fill -26 -61 -3 -24 -61 3 gold_block",
        "execute in minecraft:overworld run fill 24 -61 -3 26 -61 3 emerald_block",
        *[f"execute in minecraft:overworld run fill {x-1} -60 {z} {x+1} -59 {z} stone_bricks" for x in (-14,0,14) for z in (-7,7)])
    add("begin","function kb:arena","execute as @a[tag=kb.member] run function kb:cleanup",
        "scoreboard players add #epoch kb_ctrl 1","scoreboard players set #assign kb_ctrl 0",
        "execute as @a[gamemode=!spectator] run function kb:assign",
        *[f"scoreboard players set #{k} kb_ctrl 0" for k in ("winner","rProgress","rCargo","rTime","bProgress","bCargo","bTime")],
        "scoreboard players set #leg kb_ctrl 1","function kb:begin_leg")
    add("assign","tag @s add kb.member","scoreboard players operation @s kb_epoch = #epoch kb_ctrl",
        "execute if score #assign kb_ctrl matches 0 run tag @s add kb.red",
        "execute if score #assign kb_ctrl matches 1 run tag @s add kb.blue",
        "execute if entity @s[tag=kb.red] run team join kb_red @s",
        "execute if entity @s[tag=kb.blue] run team join kb_blue @s",
        "scoreboard players add #assign kb_ctrl 1","execute if score #assign kb_ctrl matches 2 run scoreboard players set #assign kb_ctrl 0")
    add("begin_leg","scoreboard players set #phase kb_ctrl 1","scoreboard players set #progress kb_ctrl 0",
        "scoreboard players set #cargo kb_ctrl 20","scoreboard players set #left kb_ctrl 120",
        "execute in minecraft:overworld run kill @e[tag=kb.caravan]",
        'execute in minecraft:overworld run summon minecraft:marker -23.5 -60 0.5 {Tags:["kb.caravan","kb.marker"]}',
        'execute in minecraft:overworld run summon minecraft:block_display -24 -60 0 {Tags:["kb.caravan","kb.visual"],block_state:{Name:"minecraft:hay_block"},transformation:{translation:[0.0f,0.0f,0.0f],left_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.0f,1.0f,1.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f]}}',
        "execute as @a[tag=kb.member] if score @s kb_epoch = #epoch kb_ctrl run function kb:prepare_player",
        'execute if score #leg kb_ctrl matches 1 run say [KB] Tur 1: Kırmızı eskort, mavi baskın.',
        'execute if score #leg kb_ctrl matches 2 run say [KB] Tur 2: Mavi eskort, kırmızı baskın.')
    add("prepare_player","scoreboard players operation @s kb_leg = #leg kb_ctrl","gamemode adventure @s","scoreboard players set @s kb_death 0","function kb:spawn_player","function kb:kit")
    add("spawn_player",
        "execute if score #leg kb_ctrl matches 1 if entity @s[tag=kb.red] in minecraft:overworld run tp @s -28 -60 -8",
        "execute if score #leg kb_ctrl matches 1 if entity @s[tag=kb.blue] in minecraft:overworld run tp @s -28 -60 8",
        "execute if score #leg kb_ctrl matches 2 if entity @s[tag=kb.blue] in minecraft:overworld run tp @s -28 -60 -8",
        "execute if score #leg kb_ctrl matches 2 if entity @s[tag=kb.red] in minecraft:overworld run tp @s -28 -60 8",
        "execute at @s run spawnpoint @s ~ ~ ~")
    add("kit","clear @s","effect clear @s",
        *[f"item replace entity @s {slot} with minecraft:{item}" for slot,item in (
        ("armor.head","iron_helmet"),("armor.chest","iron_chestplate"),("armor.legs","iron_leggings"),("armor.feet","iron_boots"),
        ("hotbar.0","iron_sword"),("hotbar.1","bow"),("hotbar.2","cooked_beef 16"),("hotbar.3","arrow 16"),("weapon.offhand","shield"))],
        "effect give @s instant_health 1 4 true","effect give @s saturation 1 20 true")
    add("cleanup","clear @s","effect clear @s","team leave @s","tag @s remove kb.member","tag @s remove kb.red","tag @s remove kb.blue",
        "scoreboard players reset @s kb_death","scoreboard players reset @s kb_leg","gamemode adventure @s",
        "execute in minecraft:overworld run tp @s 0 -60 0","execute in minecraft:overworld run spawnpoint @s 0 -60 0")
    add("maintain",
        "execute as @a[tag=kb.member] unless score @s kb_epoch = #epoch kb_ctrl run function kb:cleanup",
        "execute if score #phase kb_ctrl matches 0 as @a[tag=kb.member] run function kb:cleanup",
        "execute if score #phase kb_ctrl matches 1 as @a[tag=kb.member] if score @s kb_epoch = #epoch kb_ctrl unless score @s kb_leg = #leg kb_ctrl run function kb:prepare_player",
        "execute if score #phase kb_ctrl matches 1 as @a[tag=kb.member,gamemode=!spectator] if score @s kb_epoch = #epoch kb_ctrl run function kb:player_tick",
        "execute if score #phase kb_ctrl matches 1..3 as @a[tag=!kb.member,gamemode=!spectator] run function kb:observer",
        "execute if score #phase kb_ctrl matches 0 as @a[tag=kb.observer] run function kb:release_observer",
        "execute if score #phase kb_ctrl matches 2..3 as @a[tag=kb.member,gamemode=!spectator] if score @s kb_epoch = #epoch kb_ctrl run gamemode spectator @s",
        "schedule function kb:maintain 1t replace")
    add("observer","tag @s add kb.observer","gamemode spectator @s")
    add("release_observer","tag @s remove kb.observer","gamemode adventure @s")
    add("player_tick","execute if score @s kb_death matches 1.. run function kb:prepare_player",
        "execute at @s unless dimension minecraft:overworld run function kb:spawn_player",
        "execute in minecraft:overworld positioned -34 -60 -14 unless entity @s[dx=68,dy=8,dz=28] run function kb:spawn_player")
    add("clock","execute if score #phase kb_ctrl matches 1 run function kb:active",
        "execute if score #phase kb_ctrl matches 3 run function kb:intermission","schedule function kb:clock 1s replace")
    add("active","scoreboard players set #readyR kb_ctrl 0","scoreboard players set #readyB kb_ctrl 0",
        "execute as @a[tag=kb.member,tag=kb.red,gamemode=!spectator] if score @s kb_epoch = #epoch kb_ctrl run scoreboard players add #readyR kb_ctrl 1",
        "execute as @a[tag=kb.member,tag=kb.blue,gamemode=!spectator] if score @s kb_epoch = #epoch kb_ctrl run scoreboard players add #readyB kb_ctrl 1",
        "execute if score #readyR kb_ctrl matches 1.. if score #readyB kb_ctrl matches 1.. run function kb:tick")
    add("tick","scoreboard players set #nearE kb_ctrl 0","scoreboard players set #nearA kb_ctrl 0",
        "execute in minecraft:overworld as @e[tag=kb.marker,limit=1] at @s run function kb:count",
        "execute if score #nearE kb_ctrl matches 1.. if score #nearA kb_ctrl matches 0 run function kb:move",
        "execute if score #nearA kb_ctrl matches 1.. if score #nearE kb_ctrl matches 0 run scoreboard players remove #cargo kb_ctrl 1",
        "scoreboard players remove #left kb_ctrl 1",
        "execute if score #progress kb_ctrl matches 48.. run function kb:finish_leg",
        "execute if score #phase kb_ctrl matches 1 if score #cargo kb_ctrl matches ..0 run function kb:finish_leg",
        "execute if score #phase kb_ctrl matches 1 if score #left kb_ctrl matches ..0 run function kb:finish_leg",
        "execute if score #phase kb_ctrl matches 1 run function kb:status")
    lines=[]
    for leg,e,a in ((1,"red","blue"),(2,"blue","red")):
        for team,k in ((e,"nearE"),(a,"nearA")):
            lines.append(f"execute if score #leg kb_ctrl matches {leg} as @a[tag=kb.member,tag=kb.{team},gamemode=!spectator,distance=..5] if score @s kb_epoch = #epoch kb_ctrl run scoreboard players add #{k} kb_ctrl 1")
    add("count",*lines)
    add("move","execute in minecraft:overworld as @e[tag=kb.marker,limit=1] at @s run tp @s ~1 ~ ~",
        "execute in minecraft:overworld as @e[tag=kb.marker,limit=1] at @s run tp @e[tag=kb.visual,limit=1] ~-0.5 ~ ~-0.5",
        "scoreboard players add #progress kb_ctrl 1")
    add("status",'title @a actionbar [{"text":"Kervan: ","color":"gold"},{"score":{"name":"#progress","objective":"kb_ctrl"}},{"text":"/48 | Yük: "},{"score":{"name":"#cargo","objective":"kb_ctrl"}},{"text":"/20 | Süre: "},{"score":{"name":"#left","objective":"kb_ctrl"}}]')
    add("finish_leg",
        *[f"execute if score #leg kb_ctrl matches {leg} run scoreboard players operation #{prefix}{label} kb_ctrl = #{key} kb_ctrl" for leg,prefix in ((1,"r"),(2,"b")) for label,key in (("Progress","progress"),("Cargo","cargo"),("Time","left"))],
        "execute if score #leg kb_ctrl matches 1 run function kb:break",
        "execute if score #leg kb_ctrl matches 2 run function kb:result")
    add("break","scoreboard players set #phase kb_ctrl 3","scoreboard players set #break kb_ctrl 6","say [KB] Roller 5 saniye sonra değişecek.")
    add("intermission","scoreboard players set #readyR kb_ctrl 0","scoreboard players set #readyB kb_ctrl 0",
        "execute as @a[tag=kb.member,tag=kb.red] if score @s kb_epoch = #epoch kb_ctrl run scoreboard players add #readyR kb_ctrl 1",
        "execute as @a[tag=kb.member,tag=kb.blue] if score @s kb_epoch = #epoch kb_ctrl run scoreboard players add #readyB kb_ctrl 1",
        "execute if score #readyR kb_ctrl matches 1.. if score #readyB kb_ctrl matches 1.. run scoreboard players remove #break kb_ctrl 1",
        "execute if score #break kb_ctrl matches ..0 run function kb:second_leg")
    add("second_leg","scoreboard players set #leg kb_ctrl 2","function kb:begin_leg")
    add("result","scoreboard players set #phase kb_ctrl 2","scoreboard players set #winner kb_ctrl 3",
        "execute if score #rProgress kb_ctrl > #bProgress kb_ctrl run scoreboard players set #winner kb_ctrl 1",
        "execute if score #rProgress kb_ctrl < #bProgress kb_ctrl run scoreboard players set #winner kb_ctrl 2",
        "execute if score #rProgress kb_ctrl = #bProgress kb_ctrl if score #rCargo kb_ctrl > #bCargo kb_ctrl run scoreboard players set #winner kb_ctrl 1",
        "execute if score #rProgress kb_ctrl = #bProgress kb_ctrl if score #rCargo kb_ctrl < #bCargo kb_ctrl run scoreboard players set #winner kb_ctrl 2",
        "execute if score #rProgress kb_ctrl = #bProgress kb_ctrl if score #rCargo kb_ctrl = #bCargo kb_ctrl if score #rTime kb_ctrl > #bTime kb_ctrl run scoreboard players set #winner kb_ctrl 1",
        "execute if score #rProgress kb_ctrl = #bProgress kb_ctrl if score #rCargo kb_ctrl = #bCargo kb_ctrl if score #rTime kb_ctrl < #bTime kb_ctrl run scoreboard players set #winner kb_ctrl 2",
        "execute if score #winner kb_ctrl matches 1 run say [KB] Kırmızı takım kazandı.",
        "execute if score #winner kb_ctrl matches 2 run say [KB] Mavi takım kazandı.",
        "execute if score #winner kb_ctrl matches 3 run say [KB] Berabere.")
    add("reset","scoreboard players set #phase kb_ctrl 0","execute as @a[tag=kb.member] run function kb:cleanup","execute as @a[tag=kb.observer] run function kb:release_observer",
        "execute in minecraft:overworld run kill @e[tag=kb.caravan]",
        *[f"scoreboard players set #{k} kb_ctrl 0" for k in ("leg","progress","cargo","left","winner","rProgress","rCargo","rTime","bProgress","bCargo","bTime","break")])
    return f

def install(root):
    root=Path(root);assert root.name=="kervan-baskini"
    root.mkdir(parents=True,exist_ok=True)
    (root/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":48,"description":"Kervan Baskini QA v0.1"}}),encoding="utf-8")
    d=root/"data/kb/function";d.mkdir(parents=True,exist_ok=True)
    for n,v in generate().items():(d/(n+".mcfunction")).write_text(v,encoding="utf-8",newline="\n")
    t=root/"data/minecraft/tags/function";t.mkdir(parents=True,exist_ok=True)
    (t/"load.json").write_text(json.dumps({"values":["kb:load"]}),encoding="utf-8")