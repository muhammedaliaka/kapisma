"""İnşa Et, Savun — symmetric build/assault rounds, dedicated vanilla 1.21.1 arena."""
from pathlib import Path
import json

def generate():
    f={}
    def add(n,*lines):f[n]="\n".join(lines)+"\n"
    add("load","scoreboard objectives add is_ctrl dummy","scoreboard objectives add is_epoch dummy","scoreboard objectives add is_stage dummy","scoreboard objectives add is_death deathCount",
        "team add is_red","team add is_blue","team modify is_red color red","team modify is_blue color aqua","team modify is_red friendlyFire false","team modify is_blue friendlyFire false",
        "gamerule keepInventory true","gamerule doImmediateRespawn true","gamerule doMobSpawning false","gamerule doDaylightCycle false",
        *[f"execute unless score #{k} is_ctrl matches -2147483648..2147483647 run scoreboard players set #{k} is_ctrl 0" for k in ("phase","epoch","leg","left","red","blue","winner")],
        "schedule function is:clock 1s replace","schedule function is:maintain 1t replace")
    add("start","execute if score #phase is_ctrl matches 0 run function is:validate")
    add("validate","scoreboard players set #count is_ctrl 0","execute as @a[gamemode=!spectator] run scoreboard players add #count is_ctrl 1",
        "execute if score #count is_ctrl matches 2..8 run function is:begin")
    add("arena","execute in minecraft:overworld run fill -32 -61 -16 32 -61 16 smooth_stone",
        "execute in minecraft:overworld run fill -10 -61 -6 10 -61 6 polished_andesite",
        "execute in minecraft:overworld run fill -10 -60 -6 10 -54 6 air",
        "execute in minecraft:overworld run setblock 0 -60 0 target",
        "function is:build_shell",
        "execute in minecraft:overworld run kill @e[type=item,x=-32,y=-60,z=-16,dx=64,dy=12,dz=32]")
    shell=[(-11,-60,-7,-11,-53,7),(11,-60,-7,11,-53,7),(-10,-60,-7,10,-53,-7),(-10,-60,7,10,-53,7),(-10,-53,-6,10,-53,6)]
    add("build_shell",*[f"execute in minecraft:overworld run fill {' '.join(map(str,box))} barrier" for box in shell])
    add("remove_shell",*[f"execute in minecraft:overworld run fill {' '.join(map(str,box))} air replace barrier" for box in shell])
    add("begin","execute as @a[tag=is.member] run function is:cleanup","scoreboard players add #epoch is_ctrl 1","scoreboard players set #assign is_ctrl 0",
        "execute as @a[gamemode=!spectator] run function is:assign","scoreboard players set #red is_ctrl 0","scoreboard players set #blue is_ctrl 0",
        "scoreboard players set #winner is_ctrl 0","scoreboard players set #leg is_ctrl 1","function is:build")
    add("assign","tag @s add is.member","scoreboard players operation @s is_epoch = #epoch is_ctrl",
        "execute if score #assign is_ctrl matches 0 run tag @s add is.red","execute if score #assign is_ctrl matches 1 run tag @s add is.blue",
        "execute if entity @s[tag=is.red] run team join is_red @s","execute if entity @s[tag=is.blue] run team join is_blue @s",
        "scoreboard players set @s is_stage 0","scoreboard players add #assign is_ctrl 1","execute if score #assign is_ctrl matches 2 run scoreboard players set #assign is_ctrl 0")
    add("build","function is:arena","scoreboard players set #phase is_ctrl 1","scoreboard players set #left is_ctrl 30",
        "execute as @a[tag=is.member] if score @s is_epoch = #epoch is_ctrl run function is:build_player",
        "execute if score #leg is_ctrl matches 1 run say [IS] Kırmızı inşa ediyor, mavi bekliyor.",
        "execute if score #leg is_ctrl matches 2 run say [IS] Mavi inşa ediyor, kırmızı bekliyor.")
    add("role",
        "tag @s remove is.defender","tag @s remove is.attacker",
        "execute if score #leg is_ctrl matches 1 if entity @s[tag=is.red] run tag @s add is.defender",
        "execute if score #leg is_ctrl matches 1 if entity @s[tag=is.blue] run tag @s add is.attacker",
        "execute if score #leg is_ctrl matches 2 if entity @s[tag=is.blue] run tag @s add is.defender",
        "execute if score #leg is_ctrl matches 2 if entity @s[tag=is.red] run tag @s add is.attacker")
    add("build_player","function is:role","scoreboard players operation @s is_stage = #leg is_ctrl",
        "clear @s","effect clear @s","scoreboard players set @s is_death 0",
        "execute if entity @s[tag=is.attacker] run gamemode spectator @s",
        "execute if entity @s[tag=is.defender] run gamemode adventure @s",
        "execute if entity @s[tag=is.defender] in minecraft:overworld run tp @s -5 -60 5",
        'execute if entity @s[tag=is.defender] run item replace entity @s hotbar.0 with oak_planks[minecraft:can_place_on={predicates:[{blocks:"minecraft:polished_andesite"},{blocks:"minecraft:oak_planks"}]}] 64',
        'execute if entity @s[tag=is.defender] run item replace entity @s hotbar.1 with oak_planks[minecraft:can_place_on={predicates:[{blocks:"minecraft:polished_andesite"},{blocks:"minecraft:oak_planks"}]}] 64')
    add("assault","function is:remove_shell","scoreboard players set #phase is_ctrl 2","scoreboard players set #left is_ctrl 90",
        "execute as @a[tag=is.member] if score @s is_epoch = #epoch is_ctrl run function is:assault_player",
        "say [IS] Hücum başladı. Hedef bloğu kır veya savun!")
    add("assault_player","function is:role","scoreboard players operation @s is_stage = #leg is_ctrl","scoreboard players add @s is_stage 2",
        "gamemode adventure @s","clear @s","effect clear @s","scoreboard players set @s is_death 0",
        "function is:spawn",
        *[f"item replace entity @s {slot} with {item}" for slot,item in (("armor.head","iron_helmet"),("armor.chest","iron_chestplate"),("armor.legs","iron_leggings"),("armor.feet","iron_boots"),("hotbar.0","iron_sword"),("hotbar.1","bow"),("hotbar.2","arrow 16"),("hotbar.3","cooked_beef 16"),("weapon.offhand","shield"))],
        'execute if entity @s[tag=is.attacker] run item replace entity @s hotbar.4 with iron_axe[minecraft:can_break={predicates:[{blocks:"minecraft:oak_planks"},{blocks:"minecraft:target"}]}]',
        "effect give @s instant_health 1 4 true","effect give @s saturation 1 20 true")
    add("spawn","execute if entity @s[tag=is.defender] in minecraft:overworld run tp @s 0 -60 -10",
        "execute if entity @s[tag=is.attacker] in minecraft:overworld run tp @s 0 -60 12","execute at @s run spawnpoint @s ~ ~ ~")
    add("cleanup","clear @s","effect clear @s","team leave @s","tag @s remove is.member","tag @s remove is.red","tag @s remove is.blue",
        "tag @s remove is.defender","tag @s remove is.attacker","scoreboard players reset @s is_death","scoreboard players reset @s is_stage",
        "gamemode adventure @s","execute in minecraft:overworld run tp @s 0 -60 0")
    add("observer","tag @s add is.observer","gamemode spectator @s")
    add("release_observer","tag @s remove is.observer","gamemode adventure @s")
    add("maintain","execute as @a[tag=is.member] unless score @s is_epoch = #epoch is_ctrl run function is:cleanup",
        "execute if score #phase is_ctrl matches 0 as @a[tag=is.member] run function is:cleanup",
        "execute if score #phase is_ctrl matches 0 as @a[tag=is.observer] run function is:release_observer",
        "execute if score #phase is_ctrl matches 1..3 as @a[tag=!is.member,gamemode=!spectator] run function is:observer",
        "execute if score #phase is_ctrl matches 1 as @a[tag=is.member] if score @s is_epoch = #epoch is_ctrl unless score @s is_stage = #leg is_ctrl run function is:build_player",
        "execute if score #phase is_ctrl matches 1..2 run function is:ready",
        "execute if score #phase is_ctrl matches 1 run function is:maintain_build",
        "execute if score #phase is_ctrl matches 2 run function is:maintain_assault",
        "execute if score #phase is_ctrl matches 1..2 if score #readyR is_ctrl matches 0 run function is:pause_players",
        "execute if score #phase is_ctrl matches 1..2 if score #readyB is_ctrl matches 0 run function is:pause_players",
        "execute if score #phase is_ctrl matches 3 as @a[tag=is.member,gamemode=!spectator] if score @s is_epoch = #epoch is_ctrl run gamemode spectator @s",
        "schedule function is:maintain 1t replace")
    add("pause_players","execute as @a[tag=is.member,gamemode=!spectator] if score @s is_epoch = #epoch is_ctrl run gamemode spectator @s")
    add("maintain_build",
        "execute as @a[tag=is.member,tag=is.attacker,gamemode=!spectator] if score @s is_epoch = #epoch is_ctrl run gamemode spectator @s",
        "execute if score #readyR is_ctrl matches 1.. if score #readyB is_ctrl matches 1.. as @a[tag=is.member,tag=is.defender,gamemode=spectator] if score @s is_epoch = #epoch is_ctrl run gamemode adventure @s",
        "execute as @a[tag=is.member,tag=is.defender,gamemode=!spectator] if score @s is_epoch = #epoch is_ctrl run effect give @s resistance 1 255 true",
        "execute as @a[tag=is.member,tag=is.defender,gamemode=!spectator] if score @s is_epoch = #epoch is_ctrl in minecraft:overworld positioned -10 -60 -6 unless entity @s[dx=20,dy=7,dz=12] run tp @s -5 -60 5")
    add("maintain_assault","scoreboard players operation #expected is_ctrl = #leg is_ctrl","scoreboard players add #expected is_ctrl 2",
        "execute as @a[tag=is.member] if score @s is_epoch = #epoch is_ctrl unless score @s is_stage = #expected is_ctrl run function is:assault_player",
        "execute if score #readyR is_ctrl matches 1.. if score #readyB is_ctrl matches 1.. as @a[tag=is.member,gamemode=spectator] if score @s is_epoch = #epoch is_ctrl run gamemode adventure @s",
        "execute as @a[tag=is.member,gamemode=!spectator] if score @s is_epoch = #epoch is_ctrl run function is:player_tick")
    add("player_tick","execute if score @s is_death matches 1.. run function is:assault_player",
        "execute at @s unless dimension minecraft:overworld run function is:spawn",
        "execute in minecraft:overworld positioned -32 -60 -16 unless entity @s[dx=64,dy=8,dz=32] run function is:spawn")
    add("clock","execute if score #phase is_ctrl matches 1..2 run function is:active","schedule function is:clock 1s replace")
    add("ready","scoreboard players set #readyR is_ctrl 0","scoreboard players set #readyB is_ctrl 0",
        "execute as @a[tag=is.member,tag=is.red] if score @s is_epoch = #epoch is_ctrl run scoreboard players add #readyR is_ctrl 1",
        "execute as @a[tag=is.member,tag=is.blue] if score @s is_epoch = #epoch is_ctrl run scoreboard players add #readyB is_ctrl 1")
    add("active","function is:ready",
        "execute if score #readyR is_ctrl matches 1.. if score #readyB is_ctrl matches 1.. run function is:tick")
    add("tick","scoreboard players operation #oldPhase is_ctrl = #phase is_ctrl","scoreboard players remove #left is_ctrl 1",
        "execute if score #oldPhase is_ctrl matches 1 if score #left is_ctrl matches ..0 run function is:assault",
        "execute if score #oldPhase is_ctrl matches 2 in minecraft:overworld unless block 0 -60 0 target run function is:capture",
        "execute if score #oldPhase is_ctrl matches 2 if score #phase is_ctrl matches 2 if score #left is_ctrl matches ..0 run function is:timeout",
        'title @a actionbar [{"text":"Tur "},{"score":{"name":"#leg","objective":"is_ctrl"}},{"text":" | Süre "},{"score":{"name":"#left","objective":"is_ctrl"}}]')
    add("capture","scoreboard players operation #attack is_ctrl = #left is_ctrl","scoreboard players add #attack is_ctrl 1","function is:end_leg")
    add("timeout","scoreboard players set #attack is_ctrl 0","function is:end_leg")
    add("end_leg","execute if score #leg is_ctrl matches 1 run scoreboard players operation #blue is_ctrl = #attack is_ctrl",
        "execute if score #leg is_ctrl matches 2 run scoreboard players operation #red is_ctrl = #attack is_ctrl",
        "scoreboard players operation #oldLeg is_ctrl = #leg is_ctrl",
        "execute if score #oldLeg is_ctrl matches 1 run function is:swap",
        "execute if score #oldLeg is_ctrl matches 2 run function is:result")
    add("swap","scoreboard players set #leg is_ctrl 2","function is:build")
    add("result","scoreboard players set #phase is_ctrl 3","scoreboard players set #winner is_ctrl 3",
        "execute if score #red is_ctrl > #blue is_ctrl run scoreboard players set #winner is_ctrl 1","execute if score #blue is_ctrl > #red is_ctrl run scoreboard players set #winner is_ctrl 2",
        "execute if score #winner is_ctrl matches 1 run say [IS] Kırmızı kazandı.","execute if score #winner is_ctrl matches 2 run say [IS] Mavi kazandı.","execute if score #winner is_ctrl matches 3 run say [IS] Berabere.")
    add("reset","scoreboard players set #phase is_ctrl 0","function is:remove_shell","execute as @a[tag=is.member] run function is:cleanup","execute as @a[tag=is.observer] run function is:release_observer",
        *[f"scoreboard players set #{k} is_ctrl 0" for k in ("leg","left","red","blue","winner")])
    return f

def install(root):
    root=Path(root);assert root.name=="insa-et-savun"
    root.mkdir(parents=True,exist_ok=True)
    (root/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":48,"description":"Insa Et Savun QA v0.1"}}),encoding="utf-8")
    d=root/"data/is/function";d.mkdir(parents=True,exist_ok=True)
    for n,v in generate().items():(d/(n+".mcfunction")).write_text(v,encoding="utf-8",newline="\n")
    t=root/"data/minecraft/tags/function";t.mkdir(parents=True,exist_ok=True)
    (t/"load.json").write_text(json.dumps({"values":["is:load"]}),encoding="utf-8")