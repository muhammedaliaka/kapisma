import json, subprocess, sys, hashlib, re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test'
cfg=fixture/'config/kapisma/seeds.json'
original=cfg.read_bytes()
obj=json.loads(original.decode('utf-8-sig'))
entry=next(e for es in obj['classes'].values() for e in es if str(e['seed'])=='-2564433333985252572')
obj['classes']['qa_tier']=[entry]
raw=root/'tools/last_bingo_tier_policy_raw.log'
assert not raw.exists()
print('FIXTURE',entry,'JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
try:
    cfg.write_text(json.dumps(obj,ensure_ascii=False),encoding='utf-8')
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/'tools/scenarios/bingo_tier_policy.txt'),'--dir',str(fixture),'--xmx','3G','--command-delay','0.65'])
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    cfg.write_bytes(original)
    assert cfg.read_bytes()==original
cases=[]
for line in raw.read_text(encoding='utf-8-sig').splitlines():
    if '[Server thread/INFO]:' not in line:continue
    if m:=re.search(r'\[Server\] QA_TIER_(\d+)_(\d+)',line):
        cases.append(dict(diff=int(m[1]),requested=int(m[2]),seed=None,budget=[],card=[],distribution=[],start=0,close=0))
    if not cases:continue
    c=cases[-1]
    if m:=re.search(r'BINGO dunyasi acildi: seed=(-?\d+)',line):c['seed']=m[1]
    if m:=re.search(r'Tombala ortak kaynak bütçesi: (\d+) / 25 hedef, hedef başına toplam miktar (\d+)',line):c['budget'].append(tuple(map(int,m.groups())))
    if m:=re.search(r'Tombala zorluk dağılımı: seviye=(\d+) hedef=(\[[\d, ]+\]) gerçekleşen=(\[[\d, ]+\]) görev=(\d+)/(\d+) çeşitlilikCeza=(\d+)',line):
        c['distribution'].append((int(m[1]),json.loads(m[2]),json.loads(m[3]),int(m[4]),int(m[5]),int(m[6])))
    if 'Kart icerigi: ' in line:c['card']=re.findall(r'(GOREV:[^=]+|[a-z0-9_]+)=(\d+[,.]\d+)(?:\s|$)',line.split('Kart icerigi: ',1)[1])
    if 'Tombala başladı!' in line:c['start']+=1
    if 'Arena depolama kapandı:' in line:c['close']+=1
assert len(cases)==4 and code==0
for c in cases:
    assert c['seed']=='-2564433333985252572' and c['budget']==[(25,2)] and len(c['distribution'])==1 and c['start']==c['close']==1,c
    d,w,a,t,req,penalty=c['distribution'][0]
    assert d==c['diff'] and req==c['requested'] and t<=req and sum(w)==sum(a)==25-t,c
    assert len(c['card'])==len(set(i for i,v in c['card']))==25 and sum(i.startswith('GOREV:') for i,v in c['card'])==t,c
    c['mean']=sum(float(v.replace(',','.')) for _,v in c['card'])/25
    print('CASE',d,req,'quota',w,'actual',a,'tasks',t,'penalty',penalty,'mean',round(c['mean'],2))
print('TIER POLICY 4/4 card integrity PASS; difficulty ordering', [c['mean'] for c in cases[:3]])
# Report actual difficulty ordering separately; don't equate card integrity with quota match.
assert cases[0]['mean']<=cases[1]['mean']<=cases[2]['mean'], 'Difficulty ordering failed'
print('DIFFICULTY ORDER PASS')
