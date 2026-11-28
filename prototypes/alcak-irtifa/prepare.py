"""Alçak İrtifa — ordered Elytra gates, standalone vanilla 1.21.1 QA world."""
from pathlib import Path
import json
GATES=[(-30,-22,0),(0,-29,12),(28,-36,-8),(52,-43,0)]

def generate():
    f={}
    def add(n,*lines):f[n]="\n".join(lines)+"\n"
    add("load","scoreboard objectives add ai_ctrl dummy","scoreboard objectives add ai_epoch dummy","scoreboard objectives add ai_gate dummy","scoreboard objectives add ai_time dummy","scoreboard objectives add ai_death deathCount",
        "team add ai_racers","team modify ai_racers friendlyFire false","team modify ai_racers collisionRule never",
        "gamerule keepInventory true","gamerule doImmediateRespawn true","gamerule doMobSpawning false","gamerule doDaylightCycle false",
        *[f"execute unless score #{k} ai_ctrl matches -2147483648..2147483647 run scoreboard players set #{k} ai_ctrl 0" for k in ("phase","epoch","ticks","left","winners","countdown","count")],
        "schedule function ai:tick 1t replace")
    add("start","execute if score #phase ai_ctrl matches 0 run function ai:validate")
    add("validate","scoreboard players set #count ai_ctrl 0","execute as @a[gamemode=!spectator] run scoreboard players add #count ai_ctrl 1",
        "execute if score #count ai_ctrl matches 2..8 run function ai:begin")
    arena=["execute in minecraft:overworld run fill -58 -15 -6 -49 -15 6 smooth_stone"]
    for i,(x,y,z) in enumerate(GATES):
        color=("yellow_concrete","orange_concrete","lime_concrete","light_blue_concrete")[i]
        for a,b,c,d in ((y-4,z-4,y+4,z-4),(y-4,z+4,y+4,z+4),(y-4,z-3,y-4,z+3),(y+4,z-3,y+4,z+3)):
            arena.append(f"execute in minecraft:overworld run fill {x} {a} {b} {x} {c} {d} {color}")
    add("arena",*arena)
    add("begin","function ai:arena","execute as @a[tag=ai.member] run function ai:cleanup","scoreboard players add #epoch ai_ctrl 1",
        "scoreboard players set #assign ai_ctrl 0","scoreboard players set #ticks ai_ctrl 0","scoreboard players set #left ai_ctrl 1800",
        "scoreboard players set #winners ai_ctrl 0","scoreboard players set #countdown ai_ctrl 60",
        "execute as @a[gamemode=!spectator] run function ai:assign","scoreboard players set #phase ai_ctrl 1",
        "say [AI] 3 saniye sonra uçuş başlıyor!")
    add("assign","tag @s add ai.member","scoreboard players operation @s ai_epoch = #epoch ai_ctrl","scoreboard players set @s ai_gate 0","scoreboard players set @s ai_time -1","scoreboard players set @s ai_death 0",
        "gamemode adventure @s","team join ai_racers @s","clear @s","effect clear @s","item replace entity @s armor.chest with elytra[minecraft:unbreakable={}]",
        "item replace entity @s hotbar.0 with firework_rocket[minecraft:fireworks={flight_duration:1,explosions:[]}] 16",
        "effect give @s saturation 1 20 true",
        *[f"execute if score #assign ai_ctrl matches {i} in minecraft:overworld run tp @s -53 -14 {z} -90 0" for i,z in enumerate((-4,-3,-2,-1,1,2,3,4))],
        "execute at @s run spawnpoint @s ~ ~ ~","scoreboard players add #assign ai_ctrl 1")
    add("cleanup","clear @s","effect clear @s","team leave @s","tag @s remove ai.member","tag @s remove ai.winner",
        "scoreboard players reset @s ai_gate","scoreboard players reset @s ai_time","scoreboard players reset @s ai_death",
        "gamemode adventure @s","execute in minecraft:overworld run tp @s -53 -14 0")
    add("observer","tag @s add ai.observer","gamemode spectator @s")
    add("release_observer","tag @s remove ai.observer","gamemode adventure @s")
    add("tick","execute as @a[tag=ai.member] unless score @s ai_epoch = #epoch ai_ctrl run function ai:cleanup",
        "execute if score #phase ai_ctrl matches 0 as @a[tag=ai.member] run function ai:cleanup",
        "execute if score #phase ai_ctrl matches 0 as @a[tag=ai.observer] run function ai:release_observer",
        "execute if score #phase ai_ctrl matches 1..3 as @a[tag=!ai.member,gamemode=!spectator] run function ai:observer",
        "scoreboard players operation #oldPhase ai_ctrl = #phase ai_ctrl",
        "execute if score #oldPhase ai_ctrl matches 1 run function ai:countdown",
        "execute if score #oldPhase ai_ctrl matches 2 run function ai:race",
        "execute if score #phase ai_ctrl matches 3 as @a[tag=ai.member,gamemode=!spectator] if score @s ai_epoch = #epoch ai_ctrl run gamemode spectator @s",
        "schedule function ai:tick 1t replace")
    add("countdown","scoreboard players set #online ai_ctrl 0","execute as @a[tag=ai.member] if score @s ai_epoch = #epoch ai_ctrl run scoreboard players add #online ai_ctrl 1",
        "execute as @a[tag=ai.member,gamemode=!spectator] if score @s ai_epoch = #epoch ai_ctrl in minecraft:overworld run tp @s -53 -14 0 -90 0",
        "execute if score #online ai_ctrl = #count ai_ctrl run scoreboard players remove #countdown ai_ctrl 1",
        "execute if score #countdown ai_ctrl matches ..0 run function ai:go")
    add("go","scoreboard players set #phase ai_ctrl 2","scoreboard players set #assign ai_ctrl 0",
        "execute as @a[tag=ai.member] if score @s ai_epoch = #epoch ai_ctrl run function ai:launch",
        "say [AI] Başla! Kapıları sırayla uçuş halinde geç.")
    add("launch",*[f"execute if score #assign ai_ctrl matches {i} in minecraft:overworld run tp @s -49 -14 {z} -90 0" for i,z in enumerate((-4,-3,-2,-1,1,2,3,4))],
        "scoreboard players add #assign ai_ctrl 1")
    add("race","scoreboard players add #ticks ai_ctrl 1","scoreboard players remove #left ai_ctrl 1",
        "execute as @a[tag=ai.member,gamemode=!spectator] if score @s ai_epoch = #epoch ai_ctrl run function ai:player",
        "execute if score #winners ai_ctrl matches 1.. run function ai:finish",
        "execute if score #phase ai_ctrl matches 2 if score #left ai_ctrl matches ..0 run function ai:timeout")
    add("player","execute if score @s ai_death matches 1.. run function ai:recover",
        "execute at @s unless dimension minecraft:overworld run function ai:recover",
        "execute in minecraft:overworld positioned -70 -59 -32 unless entity @s[dx=145,dy=65,dz=64] run function ai:recover",
        "execute in minecraft:overworld positioned -70 -60 -32 if entity @s[dx=145,dy=1,dz=64,nbt={OnGround:1b}] run function ai:recover",
        "scoreboard players operation #oldGate ai_ctrl = @s ai_gate",
        *[f"execute if score #oldGate ai_ctrl matches {i} in minecraft:overworld positioned {x-1} {y-3} {z-3} if entity @s[dx=2,dy=6,dz=6,nbt={{FallFlying:1b}}] run function ai:advance" for i,(x,y,z) in enumerate(GATES)])
    add("advance","scoreboard players add @s ai_gate 1",
        'tellraw @s [{"text":"[AI] Geçit ","color":"gold"},{"score":{"name":"@s","objective":"ai_gate"}},{"text":"/4"}]',
        "execute if score @s ai_gate matches 4 if score @s ai_time matches -1 run function ai:record")
    add("record","scoreboard players operation @s ai_time = #ticks ai_ctrl","scoreboard players add #winners ai_ctrl 1","tag @s add ai.winner")
    add("recover","scoreboard players set @s ai_death 0",
        "execute if score @s ai_gate matches 0 in minecraft:overworld run tp @s -49 -14 0 -90 0",
        *[f"execute if score @s ai_gate matches {i+1} in minecraft:overworld run tp @s {x+2} {y+4} {z} -90 0" for i,(x,y,z) in enumerate(GATES[:-1])],
        "effect give @s instant_health 1 4 true")
    add("finish","scoreboard players set #phase ai_ctrl 3",
        'tellraw @a [{"text":"[AI] Kazanan: ","color":"gold"},{"selector":"@a[tag=ai.winner]"}]')
    add("timeout","scoreboard players set #phase ai_ctrl 3","say [AI] Süre bitti; tüm geçitleri tamamlayan yok.")
    add("reset","scoreboard players set #phase ai_ctrl 0","execute as @a[tag=ai.member] run function ai:cleanup","execute as @a[tag=ai.observer] run function ai:release_observer",
        *[f"scoreboard players set #{k} ai_ctrl 0" for k in ("ticks","left","winners","countdown")])
    return f

def install(root):
    root=Path(root);assert root.name=="alcak-irtifa"
    root.mkdir(parents=True,exist_ok=True)
    (root/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":48,"description":"Alcak Irtifa QA v0.1"}}),encoding="utf-8")
    d=root/"data/ai/function";d.mkdir(parents=True,exist_ok=True)
    for n,v in generate().items():(d/(n+".mcfunction")).write_text(v,encoding="utf-8",newline="\n")
    t=root/"data/minecraft/tags/function";t.mkdir(parents=True,exist_ok=True)
    (t/"load.json").write_text(json.dumps({"values":["ai:load"]}),encoding="utf-8")