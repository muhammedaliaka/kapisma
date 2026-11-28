#!/usr/bin/env python3
"""Independent checks for all UHC map decorations and target chest contents."""
import re, sys
from pathlib import Path
raw=Path(sys.argv[1]).read_text(encoding='utf-8-sig')
expected=int(sys.argv[2])
radius=int(sys.argv[3])
lines=[l for l in raw.splitlines() if '[Server thread/INFO]:' in l]
center=next(re.search(r'UHC dunyasi acildi:.* merkez=\((-?\d+),(-?\d+)\)',l) for l in lines if 'UHC dunyasi acildi:' in l)
cx,cz=map(int,center.groups())
nbts=[l for l in lines if 'has the following block data:' in l and 'minecraft:filled_map' in l]
targets=[];ids=[];counts=[];decorations=0;maps=0
for l in nbts:
    target=[(int(float(x)),int(float(z))) for x,z in re.findall(r'hazine: \{x: (-?[\d.]+)d, z: (-?[\d.]+)d, type: "minecraft:red_x"',l)]
    local_ids=re.findall(r'"minecraft:map_id": (\d+)',l)
    assert len(target)==len(local_ids)
    targets+=target; ids+=local_ids; counts.append(len(target))
    decorations+=l.count('"minecraft:map_decorations"')
    maps+=l.count('id: "minecraft:filled_map"')
chests=[]
for l in lines:
    if 'has the following block data:' not in l or 'minecraft:golden_apple' not in l: continue
    m=re.search(r'(-?\d+), (-?\d+), (-?\d+) has the following block data:',l)
    if not m: continue
    x,y,z=map(int,m.groups())
    assert all('minecraft:'+item in l for item in ('golden_apple','gold_ingot','iron_ingot','arrow')),l
    chests.append((x,z))
markers=[int(m.group(1)) for l in lines if (m:=re.search(r'\[Server\] UHC_CHEST_(\d+)_OK',l))]
assert len(ids)==len(set(ids))==len(targets)==len(set(targets))==maps==decorations==expected,(len(ids),len(targets),maps)
assert all(abs(x-cx)<radius and abs(z-cz)<radius for x,z in targets)
assert sorted(chests)==sorted(targets),(len(chests),len(targets),set(targets)-set(chests))
assert sorted(markers)==list(range(expected)),markers
assert sum('UHC başladı!' in l for l in lines)==1
assert sum('UHC kapandi' in l for l in lines)==1
assert sum('Arena depolama kapandı:' in l for l in lines)==1
print(f'PASS {expected}/{expected} distinct map IDs, in-border decorations and physical chest NBT; barrels={counts}; center=({cx},{cz})')
