#!/usr/bin/env python3
"""Read-only production catalog audit. Metadata checks are not world-generation proof."""
import collections, hashlib, json, re, sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
p=root/'server/config/kapisma/seeds.json'
data=p.read_bytes()
obj=json.loads(data.decode('utf-8-sig'))
classes=obj['classes']
expected={
    'ova':{'plains','sunflower_plains','meadow'},
    'orman':{'forest','old_growth_birch_forest','flower_forest','birch_forest','dark_forest','cherry_grove'},
    'taiga_kar':{'taiga','snowy_plains','snowy_taiga','old_growth_spruce_taiga','old_growth_pine_taiga'},
    'col_mesa':{'desert','wooded_badlands','eroded_badlands','badlands'},
    'savan_cangil':{'bamboo_jungle','sparse_jungle','savanna','jungle','savanna_plateau'},
    'dag':{'windswept_gravelly_hills','snowy_slopes','grove','windswept_forest','windswept_hills','jagged_peaks','frozen_peaks','stony_peaks'},
}
assert set(classes)==set(expected)
assert obj.get('game_version')=='1.21.1'
seen=set(); duplicates=[]; invalid=[]; suspicious=collections.Counter(); ocean=[]; criteria=[]
for cls, entries in classes.items():
    for e in entries:
        s=e['seed']; pos=e['spawn']; bio=e['biome']
        if not (isinstance(s,str) and re.fullmatch(r'-?\d+',s) and -(2**63)<=int(s)<2**63 and len(pos)==2 and all(type(x) is int for x in pos) and bio in expected[cls]):
            invalid.append((cls,s,bio,pos))
        if int(s) in seen: duplicates.append((cls,s))
        seen.add(int(s))
        for k,v in e.get('important',{}).items():
            suspicious[(k,v)]+=1
        if e.get('ocean') is None or e['ocean']>=.25: ocean.append((cls,s,e.get('ocean')))
        if e.get('nearest_important',-1)<0 or e['nearest_important']>600 or len(e.get('important',{}))<3:
            criteria.append((cls,s))
    print(cls,len(entries),'entries')
print('SHA256',hashlib.sha256(data).hexdigest())
print('unique seeds',len(seen),'duplicate',len(duplicates),'invalid schema/class',len(invalid))
print('ocean metadata outside stated <0.25 criterion',len(ocean),ocean)
print('recorded structure metadata criterion inconsistencies',len(criteria),criteria)
print('Repeated metadata counts (not a statistical test, not actual structure proof):',suspicious.most_common(12))
assert p.read_bytes()==data
assert not invalid and not duplicates and len(seen)==sum(len(x) for x in classes.values())
