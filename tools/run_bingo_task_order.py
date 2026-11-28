"""RED/GREEN test; never run against the live server."""
import argparse, hashlib, json, shutil, socket, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ap=argparse.ArgumentParser()
ap.add_argument('tag',choices=['red','green'])
a=ap.parse_args()
fixture=root/'server_test_order'
# Protect the user's nearly full disk. Do not modify any files until safe.
if shutil.disk_usage(root).free < 5*1024**3:
    sys.exit('BLOCKED: less than 5 GiB free; no test files changed')
with socket.socket() as s:
    s.settimeout(1)
    if s.connect_ex(('127.0.0.1',25572))==0:
        sys.exit('BLOCKED: test port busy')
cfg=fixture/'config/kapisma/seeds.json'
assert 'server-port=25572' in (fixture/'server.properties').read_text()
original=cfg.read_bytes()
obj=json.loads(original.decode('utf-8-sig'))
entry=next(e for es in obj['classes'].values() for e in es if str(e['seed'])=='-1250613006204744322')
obj['classes']['qa_task_order']=[entry]
pack=fixture/'world/datapacks/qa_task_order'
raw=root/f'tools/last_bingo_task_order_{a.tag}_raw.log'
assert not pack.exists() and not raw.exists()
print('JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
try:
    cfg.write_text(json.dumps(obj,ensure_ascii=False),encoding='utf-8')
    shutil.copytree(root/'tools/test-fixtures/bingo-task-order',pack)
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/('tools/scenarios/bingo_task_order_green.txt' if a.tag=='green' else 'tools/scenarios/bingo_task_order.txt')),'--dir',str(fixture),'--xmx','2G','--command-delay','0.65'])
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    if pack.exists(): shutil.rmtree(pack)
    cfg.write_bytes(original)
assert cfg.read_bytes()==original
if code!=0:sys.exit(code)
sys.exit(subprocess.call([sys.executable,str(root/'tools/check_bingo_task_order.py'),str(raw)]))
