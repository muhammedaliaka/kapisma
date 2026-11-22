#!/usr/bin/env python3
"""parkour.json'dan lobby_parkour.txt senaryosunu uretir (rota degisince koordinatlar degisir)."""
import json, os, sys
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
d = json.load(open(os.path.join(root, "mod", "kapisma", "src", "main", "resources", "data", "kapisma", "parkour.json"), encoding="utf-8"))
P = d["platforms"]
BASE = 100
def c(p):  # platform ustu merkez (oyuncu ayak konumu)
    return (p["x"] + p["sx"] / 2.0, BASE + p["y"] + 1, p["z"] + p["sz"] / 2.0)
def tp(p, dy=0):
    x, y, z = c(p)
    return "execute as Bot1 at @s run tp @s %.1f %d %.1f" % (x, y + dy, z)
out = ["# Otomatik uretildi (tools/gen_parkour_scenario.py): lobi + parkur (tirmanma kuleleri, zamanli kizil tas kopruleri dahil)",
       "player Bot1 spawn", "wait 5",
       "data get entity Bot1 Dimension", "data get entity Bot1 Pos", "data get entity Bot1 Inventory",
       "execute as Bot1 at @s if entity @e[type=text_display,distance=..40]",
       tp(P[0]), "wait 3", "expect PARKUR Bot1 basladi", "data get entity Bot1 Inventory"]
cps = [p for p in P if p["kind"] == "cp"]
ends = [p for p in P if p["kind"] == "end"]
out += [tp(cps[0]), "wait 3", "expect cp platformuna ulasti"]
out += [tp(ends[0]), "wait 3", "expect end platformuna ulasti (bolum KOLAY"]
out += ["# yuksekten dus: son checkpoint'e donmeli (bot yercekimi uygulamaz, dogrudan asagi isinlaniyor)",
        "execute as Bot1 at @s run tp @s %.1f %d %.1f" % (c(cps[0])[0], BASE + cps[0]["y"] - 8, c(cps[0])[2]), "wait 5", "data get entity Bot1 Pos"]
# tirmanma kuleleri: direk, merdiven ve sahanliklar; kule tepesi b platformuna bitisik
for p in P:
    if p["kind"] != "climb": continue
    a = P[p["i"] - 1]
    out.append("# spiral merdiven idx %d (h=%d)" % (p["i"], p["h"]))
    pole = (a["x"] + 1, a["z"] + 1)
    for y in (BASE + a["y"] + 1, BASE + p["y"]):
        out.append("execute as Bot1 at @s if block %d %d %d minecraft:stone_bricks" % (pole[0], y, pole[1]))
        out.append("expect Test passed")
    # ilk basamak: yer seviyesindeki dort kenar ortasindan biri merdiven olmali
    for (u, v) in ((1, 0), (2, 1), (1, 2), (0, 1)):
        out.append("execute as Bot1 at @s if block %d %d %d minecraft:stone_brick_stairs run say STAIR1_OK" % (a["x"] + u, BASE + a["y"] + 1, a["z"] + v))
    out.append("expect STAIR1_OK")
    out.append("execute as Bot1 at @s unless block %d %d %d minecraft:air" % (p["x"] + p["sx"] // 2, BASE + p["y"], p["z"] + p["sz"] // 2))
    out.append("expect Test passed")
# zamanli kopru: platform zamanla acilip kapaniyor
bl = [p for p in P if p["kind"] == "blink"]
if bl:
    b = bl[0]
    pos = "%d %d %d" % (b["x"], BASE + b["y"], b["z"])
    out.append("# zamanli kizil tas kopru idx %d: 5 sn boyunca 1 sn arayla durum" % b["i"])
    for k in range(12):
        out.append("execute as Bot1 at @s if block %s minecraft:redstone_block run say BLINK_ON" % pos)
        out.append("execute as Bot1 at @s if block %s minecraft:air run say BLINK_OFF" % pos)
        out.append("wait 1")
    out.append("expect BLINK_ON")
    out.append("expect BLINK_OFF")
for lv in (1, 2, 3):
    out += [tp(ends[lv]), "wait 3"]
out += ["expect end platformuna ulasti (bolum ÇOK ZOR", "wait 12", "data get entity Bot1 Pos", "data get entity Bot1 Inventory",
        "execute as Bot1 at @s run tp @s 0.5 80 60", "wait 4", "data get entity Bot1 Pos", "execute as Bot1 run puan"]
open(os.path.join(root, "tools", "scenarios", "lobby_parkour.txt"), "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
print("yazildi, satir:", len(out))
