import json, subprocess, sys, hashlib, re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test'
cfg=fixture/'config/kapisma/seeds.json'
original=cfg.read_bytes()
obj=json.loads(original.decode('utf-8-sig'))
entry=next(e for es in obj['classes'].values() for e in es if str(e['seed'])=='-2564433333985252572')
obj['classes']['qa_ova_retry']=[entry]
raw=root/'tools/last_bingo_survey_timing_raw.log'
assert not raw.exists()
print('FIXTURE',entry,'JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
try:
    cfg.write_text(json.dumps(obj,ensure_ascii=False),encoding='utf-8')
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/'tools/scenarios/bingo_survey_timing.txt'),'--dir',str(fixture),'--xmx','3G','--command-delay','0.65'])
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    cfg.write_bytes(original)
    assert cfg.read_bytes()==original
cases=[]
for line in raw.read_text(encoding='utf-8-sig').splitlines():
    if '[Server thread/INFO]:' not in line:continue
    if m:=re.search(r'\[Server\] QA_OVA_RETRY_(\d+)',line):
        cases.append(dict(id=int(m[1]),seed=None,extra=[],timing=[],budget=[],card=[],start=0,close=0,refused=0))
    if not cases:continue
    c=cases[-1]
    if m:=re.search(r'BINGO dunyasi acildi: seed=(-?\d+)',line):c['seed']=m[1]
    if m:=re.search(r'Tombala tarama ölçümü: chunk=(\d+) duvarMs=(\d+) taramaMs=(\d+) beklemeTuru=(\d+)',line):c['timing'].append(tuple(map(int,m.groups())))
    if m:=re.search(r'Tombala ek tarama: ilk bütçe (\d+)/25, taranan (\d+) parça',line):c['extra'].append(tuple(map(int,m.groups())))
    if m:=re.search(r'Tombala ortak kaynak bütçesi: (\d+) / 25 hedef, hedef başına toplam miktar (\d+)',line):c['budget'].append(tuple(map(int,m.groups())))
    if 'Kart icerigi: ' in line:c['card']=re.findall(r'(GOREV:[^=]+|[a-z0-9_]+)=\d+[,.]\d+(?:\s|$)',line.split('Kart icerigi: ',1)[1])
    if 'Tombala başladı!' in line:c['start']+=1
    if 'Arena depolama kapandı:' in line:c['close']+=1
    if '25 farklı hedef doğrulanamadı' in line:c['refused']+=1
passed=0
for c in cases:
    ok=c['seed']=='-2564433333985252572' and len(c['extra'])<=1 and c['budget']==[(25,2)] and len(set(c['card']))==len(c['card'])==25 and c['start']==c['close']==1 and c['refused']==0
    passed+=ok
    print('CASE',c['id'],'PASS' if ok else 'FAIL','extra',c['extra'],'timing',c['timing'],'budget',c['budget'],'start/close',c['start'],c['close'])
print('ADAPTIVE OVA',passed,'/2','runner',code)
sys.exit(0 if code==0 and passed==len(cases)==2 else 1)
