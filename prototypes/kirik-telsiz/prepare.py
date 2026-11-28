"""Standalone Kırık Telsiz datapack. No modification of normal Kapışma."""
from pathlib import Path
import argparse
import json

def generate():
    f = {}
    def put(name, *lines):
        f[name] = "\n".join(lines) + "\n"
    def msg(text):
        return json.dumps({"text": text}, ensure_ascii=False)
    def private(text):
        return "tellraw @s " + msg(text)
    player = "@a[tag=kt.player,gamemode=!spectator]"
    roles = ["@a[tag=kt.player,tag=kt.a,gamemode=!spectator]", "@a[tag=kt.player,tag=kt.b,gamemode=!spectator]"]
    put("load", *[f"scoreboard objectives add {o} {t}" for o,t in
        (("kt_ctrl","dummy"),("kt_epoch","dummy"),("kt_code","trigger"),("kt_hint","trigger"))],
        "scoreboard players add #epoch kt_ctrl 0", "scoreboard players add #phase kt_ctrl 0",
        "scoreboard players set #hundred kt_ctrl 100",
        "schedule function kt:clock 1s replace")
    put("start",
        "execute unless score #phase kt_ctrl matches 0 run return 0",
        "scoreboard players set #count kt_ctrl 0",
        "execute as @a[gamemode=!spectator] run scoreboard players add #count kt_ctrl 1",
        "execute unless score #count kt_ctrl matches 2..8 run return run say [KT] 2-8 katılımcı gerekli.",
        "scoreboard players add #epoch kt_ctrl 1",
        "tag @a remove kt.player", "tag @a remove kt.a", "tag @a remove kt.b",
        "scoreboard players set #assign kt_ctrl 0",
        "execute as @a[gamemode=!spectator] run function kt:assign",
        "scoreboard players set #round kt_ctrl 1", "scoreboard players set #phase kt_ctrl 1",
        "function kt:new_round")
    put("assign", "tag @s add kt.player", "scoreboard players operation @s kt_epoch = #epoch kt_ctrl",
        "execute if score #assign kt_ctrl matches 0 run tag @s add kt.a",
        "execute if score #assign kt_ctrl matches 1 run tag @s add kt.b",
        "scoreboard players add #assign kt_ctrl 1",
        "execute if score #assign kt_ctrl matches 2 run scoreboard players set #assign kt_ctrl 0")
    put("new_round",
        "execute store result score #a kt_ctrl run random value 10..99",
        "execute store result score #b kt_ctrl run random value 10..99",
        "scoreboard players operation #answer kt_ctrl = #a kt_ctrl",
        "scoreboard players operation #answer kt_ctrl *= #hundred kt_ctrl",
        "scoreboard players operation #answer kt_ctrl += #b kt_ctrl",
        "scoreboard players set #left kt_ctrl 90", "scoreboard players set #attempt kt_ctrl 3",
        "scoreboard players set #cool kt_ctrl 0",
        "function kt:discard_pending",
        f"execute as {player} if score @s kt_epoch = #epoch kt_ctrl run function kt:hint",
        "say [KT] Yeni şifre hazır. İki rolün bilgisini birleştirin.")
    put("hint",
        "execute unless score #phase kt_ctrl matches 1 run return 0",
        "execute unless entity @s[tag=kt.player,gamemode=!spectator] run return 0",
        "execute unless score @s kt_epoch = #epoch kt_ctrl run return 0",
        "execute if entity @s[tag=kt.a] run tellraw @s " + json.dumps([
            {"text":"[KT] Operatör A: ilk iki rakam "},{"score":{"name":"#a","objective":"kt_ctrl"}},
            {"text":". Diğer yarıyı B'den öğren."}],ensure_ascii=False),
        "execute if entity @s[tag=kt.b] run tellraw @s " + json.dumps([
            {"text":"[KT] Operatör B: son iki rakam "},{"score":{"name":"#b","objective":"kt_ctrl"}},
            {"text":". Diğer yarıyı A'dan öğren."}],ensure_ascii=False),
        private("Şifre: /trigger kt_code set 1234 (örnek). İpucu: /trigger kt_hint"))
    put("clock", "schedule function kt:clock 1s replace",
        "execute unless score #phase kt_ctrl matches 1 run return 0",
        "scoreboard players set #readyA kt_ctrl 0", "scoreboard players set #readyB kt_ctrl 0",
        f"execute as {roles[0]} if score @s kt_epoch = #epoch kt_ctrl run scoreboard players add #readyA kt_ctrl 1",
        f"execute as {roles[1]} if score @s kt_epoch = #epoch kt_ctrl run scoreboard players add #readyB kt_ctrl 1",
        "execute unless score #readyA kt_ctrl matches 1.. run return run function kt:paused",
        "execute unless score #readyB kt_ctrl matches 1.. run return run function kt:paused",
        "execute if score #cool kt_ctrl matches 1.. run scoreboard players remove #cool kt_ctrl 1",
        f"execute as {player} if score @s kt_epoch = #epoch kt_ctrl run function kt:player",
        "execute unless score #phase kt_ctrl matches 1 run return 0",
        "scoreboard players remove #left kt_ctrl 1",
        "execute if score #left kt_ctrl matches ..0 run function kt:lose")
    put("discard_pending", "scoreboard players reset * kt_code", "scoreboard players reset * kt_hint")
    put("paused", "function kt:discard_pending",
        f"execute as {player} if score @s kt_epoch = #epoch kt_ctrl run title @s actionbar " + msg("Diğer operatör bekleniyor · süre durdu."))
    put("player",
        "execute unless score #phase kt_ctrl matches 1 run return 0",
        "execute if score @s kt_hint matches 1.. run function kt:hint",
        "scoreboard players set @s kt_hint 0", "scoreboard players enable @s kt_hint",
        "execute if score @s kt_code matches 1000..9999 if score #phase kt_ctrl matches 1 if score #cool kt_ctrl matches 0 run function kt:submit",
        "execute unless score #phase kt_ctrl matches 1 run return 0",
        "scoreboard players set @s kt_code 0", "scoreboard players enable @s kt_code",
        "title @s actionbar " + json.dumps([{"text":"Telsiz · Tur "},{"score":{"name":"#round","objective":"kt_ctrl"}},
            {"text":"/3 · Süre "},{"score":{"name":"#left","objective":"kt_ctrl"}},
            {"text":" · Hak "},{"score":{"name":"#attempt","objective":"kt_ctrl"}}],ensure_ascii=False))
    put("submit", "scoreboard players set #cool kt_ctrl 2",
        "execute if score @s kt_code = #answer kt_ctrl run return run function kt:correct",
        "scoreboard players remove #attempt kt_ctrl 1", "say [KT] Şifre yanlış.",
        "execute if score #attempt kt_ctrl matches ..0 run function kt:lose")
    put("correct", "execute if score #round kt_ctrl matches 3 run return run function kt:win",
        "scoreboard players add #round kt_ctrl 1", "function kt:new_round", "scoreboard players set #cool kt_ctrl 2")
    put("win", "scoreboard players set #phase kt_ctrl 2", "function kt:discard_pending", "say [KT] Üç sinyal çözüldü. Görev başarılı.")
    put("lose", "scoreboard players set #phase kt_ctrl 3", "function kt:discard_pending", "say [KT] Görev başarısız.")
    put("reset", "scoreboard players set #phase kt_ctrl 0", "tag @a remove kt.player",
        "tag @a remove kt.a", "tag @a remove kt.b",
        "function kt:discard_pending",
        "say [KT] Maç sıfırlandı.")
    return f

def install(destination):
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    expected = {"pack.mcmeta": json.dumps({"pack":{"pack_format":48,"description":"Kirik Telsiz isolated prototype"}}),
                "data/minecraft/tags/function/load.json": json.dumps({"values":["kt:load"]})}
    expected.update({f"data/kt/function/{key}.mcfunction": value for key,value in generate().items()})
    for name, content in expected.items():
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    return len(expected)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    print("Generated", install(args.destination), "files")