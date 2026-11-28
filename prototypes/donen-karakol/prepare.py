"""Dönen Karakol v0.3: vanilla 1.21.1 datapack, isolated arena only."""
from pathlib import Path
import json

POINTS=[(-24,-60,0),(0,-60,0),(24,-60,0)]
def generate():
    f={}
    def add(name,*lines): f[name]="\n".join(lines)+"\n"
    add("load","scoreboard objectives add dk_ctrl dummy","scoreboard objectives add dk_epoch dummy","scoreboard objectives add dk_death deathCount",
        "team add dk_red","team add dk_blue","team modify dk_red color red","team modify dk_blue color aqua",
        "team modify dk_red friendlyFire false","team modify dk_blue friendlyFire false",
        "gamerule keepInventory true","gamerule doImmediateRespawn true","gamerule doMobSpawning false","gamerule doDaylightCycle false",
        "schedule function dk:maintain 1t replace",
        *[f"execute unless score #{k} dk_ctrl matches -2147483648..2147483647 run scoreboard players set #{k} dk_ctrl 0" for k in ("phase","epoch","red","blue","point","left")],
        "schedule function dk:clock 1s replace")
    add("start",'execute unless score #phase dk_ctrl matches 0 run tellraw @a {"text":"[DK] Maç etkin veya sonuçlandı; önce reset gerekir.","color":"yellow"}',
        "execute if score #phase dk_ctrl matches 0 run function dk:validate")
    add("validate","scoreboard players set #count dk_ctrl 0","execute as @a[gamemode=!spectator] run scoreboard players add #count dk_ctrl 1",
        'execute unless score #count dk_ctrl matches 2..8 run tellraw @a {"text":"[DK] 2–8 aktif oyuncu gerekli.","color":"red"}',
        "execute if score #count dk_ctrl matches 2..8 run function dk:begin")
    add("arena","execute in minecraft:overworld run fill -36 -61 -12 36 -61 12 minecraft:smooth_stone",
        *[f"execute in minecraft:overworld run fill {x-4} -61 -4 {x+4} -61 4 minecraft:gray_concrete" for x,y,z in POINTS],
        "execute in minecraft:overworld run fill -2 -61 -12 2 -61 -10 minecraft:red_concrete",
        "execute in minecraft:overworld run fill -2 -61 10 2 -61 12 minecraft:blue_concrete",
        *[f"execute in minecraft:overworld run fill {x-1} -60 {z} {x+1} -59 {z} minecraft:stone_bricks" for x in (-16,16) for z in (-7,7)])
    add("begin","function dk:arena","scoreboard players add #epoch dk_ctrl 1",
        "execute as @a[tag=dk.member] run function dk:cleanup",
        "scoreboard players set #assign dk_ctrl 0",
        "execute as @a[gamemode=!spectator] run function dk:assign",
        "scoreboard players set #red dk_ctrl 0","scoreboard players set #blue dk_ctrl 0",
        "scoreboard players set #point dk_ctrl 0","scoreboard players set #left dk_ctrl 30",
        "scoreboard players set #phase dk_ctrl 1","function dk:visual",
        'tellraw @a {"text":"[DK] Maç başladı. Aktif karakolu tek takım tutarsa saniyede 1 puan. Hedef 40.","color":"gold"}')
    add("assign","tag @s add dk.member","scoreboard players operation @s dk_epoch = #epoch dk_ctrl",
        "execute if score #assign dk_ctrl matches 0 run tag @s add dk.red",
        "execute if score #assign dk_ctrl matches 1 run tag @s add dk.blue",
        "execute if entity @s[tag=dk.red] run team join dk_red @s",
        "execute if entity @s[tag=dk.blue] run team join dk_blue @s",
        "gamemode adventure @s","scoreboard players set @s dk_death 0","function dk:spawn_player","function dk:kit",
        'execute if entity @s[tag=dk.red] run tellraw @s {"text":"[DK] Kırmızı takım","color":"red"}',
        'execute if entity @s[tag=dk.blue] run tellraw @s {"text":"[DK] Mavi takım","color":"aqua"}',
        "scoreboard players add #assign dk_ctrl 1","execute if score #assign dk_ctrl matches 2 run scoreboard players set #assign dk_ctrl 0")
    add("spawn_player",
        "execute if entity @s[tag=dk.red] in minecraft:overworld run tp @s 0 -60 -11 0 0",
        "execute if entity @s[tag=dk.blue] in minecraft:overworld run tp @s 0 -60 11 180 0",
        "execute if entity @s[tag=dk.red] in minecraft:overworld run spawnpoint @s 0 -60 -11",
        "execute if entity @s[tag=dk.blue] in minecraft:overworld run spawnpoint @s 0 -60 11")
    add("kit","clear @s","effect clear @s",
        "item replace entity @s armor.head with minecraft:iron_helmet",
        "item replace entity @s armor.chest with minecraft:iron_chestplate",
        "item replace entity @s armor.legs with minecraft:iron_leggings",
        "item replace entity @s armor.feet with minecraft:iron_boots",
        "item replace entity @s hotbar.0 with minecraft:iron_sword",
        "item replace entity @s hotbar.1 with minecraft:bow",
        "item replace entity @s hotbar.2 with minecraft:cooked_beef 16",
        "item replace entity @s hotbar.3 with minecraft:arrow 16",
        "item replace entity @s weapon.offhand with minecraft:shield",
        "effect give @s minecraft:instant_health 1 4 true",
        "effect give @s minecraft:saturation 1 20 true")
    add("respawn","scoreboard players set @s dk_death 0","function dk:spawn_player","function dk:kit")
    add("cleanup","clear @s","effect clear @s","team leave @s",
        "tag @s remove dk.member","tag @s remove dk.red","tag @s remove dk.blue","scoreboard players reset @s dk_death",
        "gamemode adventure @s","execute in minecraft:overworld run tp @s 0 -60 0",
        "execute in minecraft:overworld run spawnpoint @s 0 -60 0")
    add("maintain",
        "execute as @a[tag=dk.member] unless score @s dk_epoch = #epoch dk_ctrl run function dk:cleanup",
        "execute if score #phase dk_ctrl matches 0 as @a[tag=dk.member] run function dk:cleanup",
        "execute if score #phase dk_ctrl matches 1 as @a[tag=dk.member,gamemode=!spectator] if score @s dk_epoch = #epoch dk_ctrl run function dk:player_tick",
        "execute if score #phase dk_ctrl matches 2 as @a[tag=dk.member,gamemode=!spectator] if score @s dk_epoch = #epoch dk_ctrl run gamemode spectator @s",
        "execute if score #phase dk_ctrl matches 1..2 as @a[tag=!dk.member,gamemode=!spectator] run function dk:observer",
        "execute if score #phase dk_ctrl matches 0 as @a[tag=dk.observer] run function dk:release_observer",
        "schedule function dk:maintain 1t replace")
    add("observer","tag @s add dk.observer","gamemode spectator @s")
    add("release_observer","tag @s remove dk.observer","gamemode adventure @s")
    add("player_tick",
        "execute if score @s dk_death matches 1.. run function dk:respawn",
        "execute at @s unless dimension minecraft:overworld run function dk:spawn_player",
        "execute in minecraft:overworld positioned -36 -60 -12 unless entity @s[dx=72,dy=8,dz=24] run function dk:spawn_player",
        "execute if entity @s[tag=dk.red,team=!dk_red] run team join dk_red @s",
        "execute if entity @s[tag=dk.blue,team=!dk_blue] run team join dk_blue @s")
    add("clock","execute if score #phase dk_ctrl matches 1 run function dk:active","schedule function dk:clock 1s replace")
    add("active","scoreboard players set #readyR dk_ctrl 0","scoreboard players set #readyB dk_ctrl 0",
        "execute as @a[tag=dk.member,tag=dk.red,gamemode=!spectator] if score @s dk_epoch = #epoch dk_ctrl run scoreboard players add #readyR dk_ctrl 1",
        "execute as @a[tag=dk.member,tag=dk.blue,gamemode=!spectator] if score @s dk_epoch = #epoch dk_ctrl run scoreboard players add #readyB dk_ctrl 1",
        "execute if score #readyR dk_ctrl matches 1.. if score #readyB dk_ctrl matches 1.. run function dk:tick",
        'execute if score #readyR dk_ctrl matches 0 run title @a actionbar {"text":"[DK] Kırmızı takım bekleniyor","color":"yellow"}',
        'execute if score #readyB dk_ctrl matches 0 run title @a actionbar {"text":"[DK] Mavi takım bekleniyor","color":"yellow"}')
    for i,(x,y,z) in enumerate(POINTS):
        add(f"count{i}",*[f"execute in minecraft:overworld positioned {x-4} {y} {z-4} as @a[tag=dk.member,tag=dk.{team},gamemode=!spectator,dx=8,dy=2,dz=8] if score @s dk_epoch = #epoch dk_ctrl run scoreboard players add #{k} dk_ctrl 1" for team,k in (("red","nearR"),("blue","nearB"))])
    add("tick","scoreboard players set #nearR dk_ctrl 0","scoreboard players set #nearB dk_ctrl 0",
        *[f"execute if score #point dk_ctrl matches {i} run function dk:count{i}" for i in range(3)],
        "execute if score #nearR dk_ctrl matches 1.. if score #nearB dk_ctrl matches 0 run scoreboard players add #red dk_ctrl 1",
        "execute if score #nearB dk_ctrl matches 1.. if score #nearR dk_ctrl matches 0 run scoreboard players add #blue dk_ctrl 1",
        'execute if score #nearR dk_ctrl matches 1.. if score #nearB dk_ctrl matches 1.. run title @a actionbar {"text":"[DK] Çekişmeli — puan yok","color":"yellow"}',
        "execute if score #red dk_ctrl matches 40.. run function dk:win_red",
        "execute if score #blue dk_ctrl matches 40.. if score #phase dk_ctrl matches 1 run function dk:win_blue",
        "execute if score #phase dk_ctrl matches 1 run function dk:advance")
    add("advance","scoreboard players remove #left dk_ctrl 1",
        'execute if score #left dk_ctrl matches 5 run say [DK] Karakol 5 saniye sonra değişecek!',
        "execute if score #left dk_ctrl matches ..0 run function dk:rotate",
        'execute unless score #nearR dk_ctrl matches 1.. run function dk:status',
        'execute if score #nearR dk_ctrl matches 1.. if score #nearB dk_ctrl matches 0 run function dk:status')
    add("status",'title @a actionbar [{"text":"Kırmızı ","color":"red"},{"score":{"name":"#red","objective":"dk_ctrl"}},{"text":" | Mavi ","color":"aqua"},{"score":{"name":"#blue","objective":"dk_ctrl"}},{"text":" | Kalan: ","color":"gold"},{"score":{"name":"#left","objective":"dk_ctrl"}}]')
    add("rotate","scoreboard players add #point dk_ctrl 1","execute if score #point dk_ctrl matches 3.. run scoreboard players set #point dk_ctrl 0","scoreboard players set #left dk_ctrl 30","function dk:visual")
    add("visual",*[f"execute in minecraft:overworld run fill {x-4} -61 -4 {x+4} -61 4 minecraft:gray_concrete" for x,y,z in POINTS],
        *[f"execute if score #point dk_ctrl matches {i} in minecraft:overworld run fill {x-4} -61 -4 {x+4} -61 4 minecraft:gold_block" for i,(x,y,z) in enumerate(POINTS)],
        *[f'execute if score #point dk_ctrl matches {i} run tellraw @a {{"text":"[DK] Aktif karakol: {name} ({x}, 0)","color":"gold"}}' for i,((x,y,z),name) in enumerate(zip(POINTS,("Batı","Merkez","Doğu")))])
    for team,label,color in (("red","Kırmızı","red"),("blue","Mavi","aqua")):
        add("win_"+team,"scoreboard players set #phase dk_ctrl 2",f'tellraw @a {{"text":"[DK] {label} takım kazandı!","color":"{color}"}}')
    add("reset","scoreboard players set #phase dk_ctrl 0","execute as @a[tag=dk.member] run function dk:cleanup","execute as @a[tag=dk.observer] run function dk:release_observer",
        "scoreboard players set #red dk_ctrl 0","scoreboard players set #blue dk_ctrl 0","scoreboard players set #point dk_ctrl 0","scoreboard players set #left dk_ctrl 0",
        'tellraw @a {"text":"[DK] Maç sıfırlandı.","color":"gray"}')
    return f

def install(root):
    root=Path(root)
    assert root.name=="donen-karakol"
    root.mkdir(parents=True,exist_ok=True)
    (root/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":48,"description":"Donen Karakol QA v0.3"}}),encoding="utf-8")
    d=root/"data/dk/function"; d.mkdir(parents=True,exist_ok=True)
    for k,v in generate().items(): (d/(k+".mcfunction")).write_text(v,encoding="utf-8",newline="\n")
    t=root/"data/minecraft/tags/function";t.mkdir(parents=True,exist_ok=True)
    (t/"load.json").write_text(json.dumps({"values":["dk:load"]}),encoding="utf-8")