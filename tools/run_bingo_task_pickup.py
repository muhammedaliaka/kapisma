"""Actual item pickup -> vanilla advancement -> one match claim."""
import hashlib,json,re,shutil,socket,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test'
assert shutil.disk_usage(root).free>=5*1024**3
with socket.socket() as s:
    s.settimeout(1)
    assert s.connect_ex(('127.0.0.1',25570))!=0
cfg=fixture/'config/kapisma/seeds.json'
assert 'server-port=25570' in (fixture/'server.properties').read_text()
original=cfg.read_bytes()
obj=json.loads(original.decode('utf-8-sig'))
entry=next(e for es in obj['classes'].values() for e in es if str(e['seed'])=='-1250613006204744322')
obj['classes']['qa_task_pickup']=[entry]
raw=root/'tools/last_bingo_task_pickup_raw.log'
assert not raw.exists()
print('JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
try:
    cfg.write_text(json.dumps(obj,ensure_ascii=False),encoding='utf-8')
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/'tools/scenarios/bingo_task_pickup.txt'),'--dir',str(fixture),'--xmx','2G','--command-delay','0.65'])
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    cfg.write_bytes(original)
    assert cfg.read_bytes()==original
lines=[l for l in raw.read_text(encoding='utf-8-sig').splitlines() if '[Server thread/INFO]:' in l]
assert code==0
assert sum('Kart icerigi:' in l and 'GOREV:Çalışma masası edin=' in l for l in lines)==1
assert sum('Bot1 tamamladı: Görev: Çalışma masası edin' in l for l in lines)==1
assert sum('[Server] QA_PICKUP_ADV' in l for l in lines)==1
assert sum('pickup has 1 [qa_pickup]' in l for l in lines)==1
assert sum('pickup has 2 [qa_pickup]' in l for l in lines)==1
assert sum('Arena depolama kapandı:' in l for l in lines)==1
print('PASS natural pickup triggered advancement, repeated pickup gave no duplicate point')
