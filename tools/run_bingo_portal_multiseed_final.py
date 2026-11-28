import json, subprocess, sys, hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test_order'
cfg=fixture/'config/kapisma/seeds.json'
original=cfg.read_bytes()
obj=json.loads(original.decode('utf-8-sig'))
prod=json.loads((root/'server/config/kapisma/seeds.json').read_text(encoding='utf-8-sig'))
for tag, cls, seed in [('forest','orman','-1250613006204744322'),('desert','col_mesa','-335906671735727005')]:
    pool=obj['classes'][cls] if tag=='forest' else prod['classes'][cls]
    entry=next(e for e in pool if str(e['seed'])==seed)
    obj['classes']['qa_portal_'+tag]=[entry]
    print('PORTAL SEED',tag,entry,flush=True)
raw=root/'tools/last_bingo_portal_multiseed_final_raw.log'
assert not raw.exists()
print('JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
try:
    cfg.write_text(json.dumps(obj,ensure_ascii=False),encoding='utf-8')
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/'tools/scenarios/bingo_portal_multiseed_final.txt'),'--dir',str(fixture),'--xmx','3G','--command-delay','0.65'])
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    cfg.write_bytes(original)
    assert cfg.read_bytes()==original
sys.exit(code)
