"""Kit İhalesi — sealed bids with match-only coins, isolated vanilla 1.21.1 arena."""
from pathlib import Path
import json
def generate():
    f={}
    def add(n,*lines):f[n]="\n".join(lines)+"\n"
    add("load","scoreboard objectives add ki_ctrl dummy","scoreboard objectives add ki_epoch dummy","scoreboard objectives add ki_bid trigger","scoreboard objectives add ki_offer dummy","scoreboard objectives add ki_coins dummy","scoreboard objectives add ki_alive dummy","scoreboard objectives add ki_death deathCount",
        "gamerule keepInventory true","gamerule doImmediateRespawn true","gamerule doMobSpawning false","gamerule doDaylightCycle false",
        *[f"execute unless score #{k} ki_ctrl matches -2147483648..2147483647 run scoreboard players set #{k} ki_ctrl 0" for k in ("phase","epoch","round","left","count","alive","max","winner")],
        "schedule function ki:tick 1t replace","schedule function ki:clock 1s replace")
    add("start","execute if score #phase ki_ctrl matches 0 run function ki:validate")
    add("validate","scoreboard players set #count ki_ctrl 0","execute as @a[gamemode=!spectator] run scoreboard players add #count ki_ctrl 1",
        "execute if score #count ki_ctrl matches 2..8 run function ki:begin")
    add("arena","execute in minecraft:overworld run fill -24 -61 -24 24 -61 24 smooth_stone",
        *[f"execute in minecraft:overworld run fill {x-1} -60 {z-1} {x+1} -58 {z+1} stone_bricks" for x in (-10,10) for z in (-10,10)])
    add("begin","function ki:arena","execute as @a[tag=ki.member] run function ki:cleanup","scoreboard players add #epoch ki_ctrl 1",
        "scoreboard players set #assign ki_ctrl 0","scoreboard players set #alive ki_ctrl 0","scoreboard players set #winner ki_ctrl 0",
        "execute as @a[gamemode=!spectator] run function ki:assign",
        "scoreboard players set #round ki_ctrl 1","scoreboard players set #phase ki_ctrl 1","function ki:new_auction")
    add("assign","tag @s add ki.member","scoreboard players operation @s ki_epoch = #epoch ki_ctrl","scoreboard players set @s ki_coins 100",
        "scoreboard players set @s ki_alive 1","scoreboard players set @s ki_death 0","scoreboard players add #alive ki_ctrl 1",
        "clear @s","gamemode adventure @s","effect clear @s","item replace entity @s hotbar.0 with wooden_sword",
        "item replace entity @s hotbar.3 with cooked_beef 16","item replace entity @s armor.chest with leather_chestplate",
        "execute in minecraft:overworld run tp @s 0 -60 0")
    add("new_auction","scoreboard players reset * ki_bid","scoreboard players reset * ki_offer","scoreboard players set #left ki_ctrl 20",
        "tag @a remove ki.candidate","tag @a remove ki.auction_winner",
        "execute if score #round ki_ctrl matches 1 run say [KI] İhale 1: demir kılıç ve kalkan.",
        "execute if score #round ki_ctrl matches 2 run say [KI] İhale 2: yay ve 24 ok.",
        "execute if score #round ki_ctrl matches 3 run say [KI] İhale 3: tam demir zırh.",
        'tellraw @a {"text":"[KI] /trigger ki_bid set 1..100; -1 ile teklifi geri çek. Teklifler gizlidir.","color":"gold"}')
    add("cleanup","clear @s","effect clear @s","tag @s remove ki.member","tag @s remove ki.candidate","tag @s remove ki.auction_winner",
        "scoreboard players reset @s ki_bid","scoreboard players reset @s ki_offer","scoreboard players reset @s ki_alive","scoreboard players reset @s ki_death",
        "gamemode adventure @s","execute in minecraft:overworld run tp @s 0 -60 0")
    add("observer","tag @s add ki.observer","gamemode spectator @s")
    add("release_observer","tag @s remove ki.observer","gamemode adventure @s")
    add("tick","execute as @a[tag=ki.member] unless score @s ki_epoch = #epoch ki_ctrl run function ki:cleanup",
        "execute if score #phase ki_ctrl matches 0 as @a[tag=ki.member] run function ki:cleanup",
        "execute if score #phase ki_ctrl matches 0 as @a[tag=ki.observer] run function ki:release_observer",
        "execute if score #phase ki_ctrl matches 1..3 as @a[tag=!ki.member,gamemode=!spectator] run function ki:observer",
        "execute if score #phase ki_ctrl matches 1 run function ki:auction_tick",
        "execute if score #phase ki_ctrl matches 2 run function ki:combat_tick",
        "execute if score #phase ki_ctrl matches 3 as @a[tag=ki.member,gamemode=!spectator] if score @s ki_epoch = #epoch ki_ctrl run gamemode spectator @s",
        "schedule function ki:tick 1t replace")
    add("online","scoreboard players set #online ki_ctrl 0","execute as @a[tag=ki.member] if score @s ki_epoch = #epoch ki_ctrl run scoreboard players add #online ki_ctrl 1")
    add("auction_tick","function ki:online",
        "execute as @a[tag=ki.member] if score @s ki_epoch = #epoch ki_ctrl run effect give @s resistance 1 255 true",
        "execute as @a[tag=ki.member] if score @s ki_epoch = #epoch ki_ctrl run effect give @s saturation 1 5 true",
        "execute if score #online ki_ctrl = #count ki_ctrl as @a[tag=ki.member] if score @s ki_epoch = #epoch ki_ctrl run function ki:bid_player",
        "execute unless score #online ki_ctrl = #count ki_ctrl run scoreboard players reset * ki_bid")
    add("bid_player","execute if score @s ki_bid matches -1 run function ki:withdraw",
        "execute if score @s ki_bid matches 1.. run function ki:submit",
        "scoreboard players set @s ki_bid 0","scoreboard players enable @s ki_bid")
    add("withdraw","scoreboard players set @s ki_offer 0",'tellraw @s {"text":"[KI] Teklif geri çekildi.","color":"gray"}')
    add("submit",
        'execute if score @s ki_bid > @s ki_coins run tellraw @s {"text":"[KI] Yetersiz jeton; önceki teklif korundu.","color":"red"}',
        "execute if score @s ki_bid <= @s ki_coins run function ki:accept")
    add("accept","scoreboard players operation @s ki_offer = @s ki_bid",
        'tellraw @s [{"text":"[KI] Gizli teklif kaydedildi: ","color":"gold"},{"score":{"name":"@s","objective":"ki_offer"}}]')
    add("clock","execute if score #phase ki_ctrl matches 1 run function ki:auction_clock",
        "execute if score #phase ki_ctrl matches 2 run function ki:combat_clock","schedule function ki:clock 1s replace")
    add("auction_clock","function ki:online",
        "execute if score #online ki_ctrl = #count ki_ctrl run scoreboard players remove #left ki_ctrl 1",
        "execute if score #left ki_ctrl matches ..0 run function ki:close",
        'title @a actionbar [{"text":"İhale "},{"score":{"name":"#round","objective":"ki_ctrl"}},{"text":" | Süre "},{"score":{"name":"#left","objective":"ki_ctrl"}}]')
    add("close","scoreboard players set #max ki_ctrl 0","tag @a remove ki.candidate",
        "execute as @a[tag=ki.member] if score @s ki_epoch = #epoch ki_ctrl if score @s ki_offer matches 1.. if score @s ki_offer <= @s ki_coins run scoreboard players operation #max ki_ctrl > @s ki_offer",
        "execute if score #max ki_ctrl matches 1.. as @a[tag=ki.member] if score @s ki_epoch = #epoch ki_ctrl if score @s ki_offer = #max ki_ctrl run tag @s add ki.candidate",
        "execute as @a[tag=ki.candidate,sort=random,limit=1] run function ki:award",
        "execute if score #max ki_ctrl matches 0 run say [KI] Geçerli teklif yok.",
        "scoreboard players add #round ki_ctrl 1",
        "execute if score #round ki_ctrl matches ..3 run function ki:new_auction",
        "execute if score #round ki_ctrl matches 4 run function ki:begin_combat")
    add("award","scoreboard players operation @s ki_coins -= @s ki_offer","tag @s add ki.auction_winner",
        "execute if score #round ki_ctrl matches 1 run item replace entity @s hotbar.0 with iron_sword",
        "execute if score #round ki_ctrl matches 1 run item replace entity @s weapon.offhand with shield",
        "execute if score #round ki_ctrl matches 2 run item replace entity @s hotbar.1 with bow",
        "execute if score #round ki_ctrl matches 2 run item replace entity @s hotbar.2 with arrow 24",
        *[f"execute if score #round ki_ctrl matches 3 run item replace entity @s armor.{slot} with iron_{item}" for slot,item in (("head","helmet"),("chest","chestplate"),("legs","leggings"),("feet","boots"))],
        'tellraw @a [{"text":"[KI] İhaleyi kazanan: ","color":"gold"},{"selector":"@s"}]',
        'tellraw @s [{"text":"[KI] Kalan jetonun: ","color":"green"},{"score":{"name":"@s","objective":"ki_coins"}}]')
    add("begin_combat","scoreboard players set #phase ki_ctrl 2","scoreboard players set #left ki_ctrl 90","scoreboard players reset * ki_bid","scoreboard players reset * ki_offer",
        "scoreboard players set #assign ki_ctrl 0","execute as @a[tag=ki.member] if score @s ki_epoch = #epoch ki_ctrl run function ki:combat_spawn",
        "say [KI] İhaleler bitti, hayatta kalan kazanır!")
    points=[(-18,0),(18,0),(0,-18),(0,18),(-13,-13),(13,13),(-13,13),(13,-13)]
    add("combat_spawn","effect clear @s","gamemode adventure @s","scoreboard players set @s ki_death 0",
        *[f"execute if score #assign ki_ctrl matches {i} in minecraft:overworld run tp @s {x} -60 {z}" for i,(x,z) in enumerate(points)],
        "execute at @s run spawnpoint @s ~ ~ ~","scoreboard players add #assign ki_ctrl 1")
    add("combat_tick",
        "execute as @a[tag=ki.member,scores={ki_alive=1,ki_death=1..}] if score @s ki_epoch = #epoch ki_ctrl run function ki:eliminate",
        "scoreboard players set #onlineAlive ki_ctrl 0",
        "execute as @a[tag=ki.member,scores={ki_alive=1}] if score @s ki_epoch = #epoch ki_ctrl run scoreboard players add #onlineAlive ki_ctrl 1",
        "execute if score #alive ki_ctrl matches 0 run function ki:draw",
        "execute if score #phase ki_ctrl matches 2 if score #alive ki_ctrl matches 1 if score #onlineAlive ki_ctrl matches 1 run function ki:survivor",
        "execute if score #phase ki_ctrl matches 2 if score #onlineAlive ki_ctrl = #alive ki_ctrl as @a[tag=ki.member,scores={ki_alive=1},gamemode=spectator] if score @s ki_epoch = #epoch ki_ctrl run gamemode adventure @s",
        "execute if score #phase ki_ctrl matches 2 unless score #onlineAlive ki_ctrl = #alive ki_ctrl as @a[tag=ki.member,scores={ki_alive=1},gamemode=!spectator] if score @s ki_epoch = #epoch ki_ctrl run gamemode spectator @s",
        "execute as @a[tag=ki.member,scores={ki_alive=0},gamemode=!spectator] if score @s ki_epoch = #epoch ki_ctrl run gamemode spectator @s",
        "execute as @a[tag=ki.member,scores={ki_alive=1},gamemode=!spectator] if score @s ki_epoch = #epoch ki_ctrl in minecraft:overworld positioned -24 -60 -24 unless entity @s[dx=48,dy=8,dz=48] run tp @s 0 -60 0")
    add("eliminate","scoreboard players set @s ki_alive 0","scoreboard players remove #alive ki_ctrl 1","gamemode spectator @s","clear @s")
    add("survivor","scoreboard players set #phase ki_ctrl 3","scoreboard players set #winner ki_ctrl 1",
        'execute as @a[tag=ki.member,scores={ki_alive=1},limit=1] if score @s ki_epoch = #epoch ki_ctrl run tellraw @a [{"text":"[KI] Maç kazananı: ","color":"gold"},{"selector":"@s"}]')
    add("draw","scoreboard players set #phase ki_ctrl 3","scoreboard players set #winner ki_ctrl 2","say [KI] Berabere.")
    add("combat_clock","execute if score #onlineAlive ki_ctrl = #alive ki_ctrl run scoreboard players remove #left ki_ctrl 1",
        "execute if score #left ki_ctrl matches ..0 run function ki:draw")
    add("reset","scoreboard players set #phase ki_ctrl 0","execute as @a[tag=ki.member] run function ki:cleanup","execute as @a[tag=ki.observer] run function ki:release_observer",
        "scoreboard players reset * ki_bid","scoreboard players reset * ki_offer",
        *[f"scoreboard players set #{k} ki_ctrl 0" for k in ("round","left","alive","winner","max")])
    return f

def install(root):
    root=Path(root);assert root.name=="kit-ihalesi"
    root.mkdir(parents=True,exist_ok=True)
    (root/"pack.mcmeta").write_text(json.dumps({"pack":{"pack_format":48,"description":"Kit Ihalesi QA v0.1"}}),encoding="utf-8")
    d=root/"data/ki/function";d.mkdir(parents=True,exist_ok=True)
    for n,v in generate().items():(d/(n+".mcfunction")).write_text(v,encoding="utf-8",newline="\n")
    t=root/"data/minecraft/tags/function";t.mkdir(parents=True,exist_ok=True)
    (t/"load.json").write_text(json.dumps({"values":["ki:load"]}),encoding="utf-8")
