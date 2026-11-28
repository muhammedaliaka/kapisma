#!/usr/bin/env python3
"""Audit a stratified sample with the corrected Minecraft generator; production is read-only.
The results are structure-placement estimates, not generated StructureStart proof.
"""
import argparse, hashlib, json, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ap=argparse.ArgumentParser()
ap.add_argument('--per-class',type=int,default=4,choices=[1,2,4,8,12,16])
a=ap.parse_args()
fixture=root/'server_test_order'
cfg=fixture/'config/kapisma'
source=root/'server/config/kapisma/seeds.json'
original=source.read_bytes()
catalog=json.loads(original.decode('utf-8-sig'))
rows=[]
for cls, entries in catalog['classes'].items():
    picks=sorted({round(i*(len(entries)-1)/max(1,a.per_class-1)) for i in range(a.per_class)})
    for i in picks:
        e=entries[i]
        rows.append(dict(seed=e['seed'],x=e['spawn'][0],z=e['spawn'][1],cls=cls))
tag=f'catalog_{len(rows)}'
inp=cfg/'seedscan_in.json'
out=cfg/'seedscan_out.jsonl'
raw=root/f'tools/last_{tag}_raw.log'
result=root/f'tools/last_{tag}_results.jsonl'
scenario=root/f'tools/scenarios/{tag}.txt'
assert not raw.exists() and not result.exists() and not scenario.exists()
assert not inp.exists() and not out.exists(), 'Preserve any pre-existing fixture audit files'
scenario.write_text(f'kapisma debug seedscan 1000\nawait seedscan bitti: {len(rows)} seed\n',encoding='utf-8')
print('CATALOG SHA256',hashlib.sha256(original).hexdigest(),'JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
try:
    inp.write_text(json.dumps(rows,ensure_ascii=False),encoding='utf-8')
    # Existing scan uses APPEND without CREATE: pre-create only in the test fixture.
    out.touch(exist_ok=False)
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(scenario),'--dir',str(fixture),'--xmx','3G','--command-delay','0.65'])
    result.write_bytes(out.read_bytes())
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    inp.unlink(missing_ok=True)
    out.unlink(missing_ok=True)
    assert source.read_bytes()==original
actual=[json.loads(l) for l in result.read_text(encoding='utf-8-sig').splitlines() if l.strip()]
expected={r['seed']:r for r in rows}
assert len(actual)==len(expected)==len(set(r['seed'] for r in actual))
assert all(r['seed'] in expected and r['x']==expected[r['seed']]['x'] and r['z']==expected[r['seed']]['z'] for r in actual)
old={e['seed']:e for entries in catalog['classes'].values() for e in entries}
passed=0
for r in actual:
    q=r['q']; old_e=old[r['seed']]
    passed+=bool(q['ok'])
    print(expected[r['seed']]['cls'],r['seed'],'old/new portal',old_e.get('important',{}).get('ruined_portal',0),q['counts'].get('ruined_portal',0),'old/new score',old_e.get('score'),q['score'],'new criteria',q['ok'])
print('Generator placement estimates',len(actual),'/',len(rows),'criteria met',passed,'runner',code)
sys.exit(code)
