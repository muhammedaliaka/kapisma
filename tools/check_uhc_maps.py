#!/usr/bin/env python3
"""Inspect server NBT response: exactly four unique maps and one in-border target each."""
import re, sys
from pathlib import Path
log = Path(sys.argv[1]).read_text(encoding="utf-8-sig")
lines = [l for l in log.splitlines() if "[Server thread/INFO]:" in l]
center = next(re.search(r"UHC dunyasi acildi:.* merkez=\((-?\d+),(-?\d+)\)", l) for l in lines if "UHC dunyasi acildi:" in l)
cx, cz = map(int, center.groups())
nbt = next(l for l in lines if "has the following block data:" in l and "minecraft:filled_map" in l)
targets = [(float(x), float(z)) for x,z in re.findall(r'hazine: \{x: (-?[\d.]+)d, z: (-?[\d.]+)d, type: "minecraft:red_x"', nbt)]
ids = re.findall(r'"minecraft:map_id": (\d+)', nbt)
maps = nbt.count('id: "minecraft:filled_map"')
decorations = nbt.count('"minecraft:map_decorations"')
radius = int(sys.argv[2]) if len(sys.argv)>2 else 50
for i,(x,z) in enumerate(targets):
    print(f"Map {ids[i]} -> {x:g},{z:g} | dx={x-cx:g},dz={z-cz:g}")
ok = (maps == decorations == len(ids) == len(set(ids)) == len(targets) == len(set(targets)) == 4
      and all(abs(x-cx)<radius and abs(z-cz)<radius for x,z in targets))
print("MAP NBT", "PASS 4/4" if ok else "FAIL")
sys.exit(0 if ok else 1)
