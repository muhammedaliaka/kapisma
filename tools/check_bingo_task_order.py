"""Independent raw-log check. Missing task is an invalid fixture, not a pass."""
import re, sys
from pathlib import Path
cases={}
case=None
for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines():
    if '[Server thread/INFO]:' not in line: continue
    if m:=re.search(r'\[Server\] QA_TASK_CASE_(EXCLUSIVE|SHARED|TIE)$',line):
        case=cases[m[1]]=dict(card=0,claim=[],done=0,start=0,close=0,tie=0)
    if case is None: continue
    if 'Kart icerigi:' in line and 'GOREV:Çalışma masası edin=' in line: case['card']+=1
    if m:=re.search(r'\[Kapışma\] (.*?) tamamladı: Görev: Çalışma masası edin\s+\(',line):
        case['claim'].append(m[1])
    if '[Server] QA_TASK_ORDER_DONE' in line: case['done']+=1
    if 'Tombala başladı!' in line: case['start']+=1
    if 'Arena depolama kapandı:' in line: case['close']+=1
    if 'BINGO bitti: kazanan yok' in line: case['tie']+=1
expected={'EXCLUSIVE':['Bot2'],'SHARED':['Bot2','Bot1']}
if 'green' in Path(sys.argv[1]).name: expected['TIE']=['Bot2','Bot1']
assert set(cases)==set(expected),cases
for name,claims in expected.items():
    c=cases[name]
    assert c['card']==c['done']==c['start']==c['close']==1,('INVALID FIXTURE',name,c)
    assert c['claim']==claims,('FAIL claim order',name,c)
    assert c['tie']==(1 if name=='TIE' else 0),('FAIL winner resolution',name,c)
    print('PASS',name,c['claim'])
