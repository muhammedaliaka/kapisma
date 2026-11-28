"""Read one real desert seed from production config; run only in server_test; restore config bytes."""
import json, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cfg=root/'server_test/config/kapisma/seeds.json'
original=cfg.read_bytes()
prod=json.loads((root/'server/config/kapisma/seeds.json').read_text(encoding='utf-8-sig'))
test=json.loads(original.decode('utf-8-sig'))
entries=prod['classes']['col_mesa']
entry=next(x for x in entries if str(x['seed'])=='-335906671735727005')
test['classes']['col_mesa']=[entry]
print('EXACT DESERT',entry,flush=True)
try:
    cfg.write_text(json.dumps(test,ensure_ascii=False,indent=2),encoding='utf-8')
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/'tools/scenarios/bingo_real_desert.txt'),'--dir',str(root/'server_test'),'--xmx','3G','--command-delay','0.65'])
finally:
    cfg.write_bytes(original)
    assert cfg.read_bytes()==original
sys.exit(code)
