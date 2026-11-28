import json, subprocess, sys, hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test'
cfg=fixture/'config/kapisma/seeds.json'
original=cfg.read_bytes()
obj=json.loads(original.decode('utf-8-sig'))
entry=next(e for e in obj['classes']['orman'] if str(e['seed'])=='9161362629027940354')
obj['classes']['uhc_natural']=[entry]
print('NATURAL SEED',entry, 'JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
try:
    cfg.write_text(json.dumps(obj,ensure_ascii=False),encoding='utf-8')
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/'tools/scenarios/uhc_64_close_normal.txt'),'--dir',str(fixture),'--xmx','3G','--command-delay','0.25'])
    (root/'tools/last_uhc_64_close_normal_raw.log').write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    cfg.write_bytes(original)
    assert cfg.read_bytes()==original
sys.exit(code)
