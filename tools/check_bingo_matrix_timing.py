"""Compare completed matrix cases against raw logs, without runner echoes."""
import re, sys
from pathlib import Path
def parse(path):
    rows=[]
    for line in Path(path).read_text(encoding='utf-8-sig').splitlines():
        if '[Server thread/INFO]:' not in line:continue
        if m:=re.search(r'\[Server\] MATRIS (\w+ \d+ \d+ \d+ [01])',line):
            rows.append(dict(case=m[1],seed=None,timing=[],budget=[],start=0,close=0))
        if not rows:continue
        row=rows[-1]
        if m:=re.search(r'BINGO dunyasi acildi: seed=(-?\d+)',line):row['seed']=m[1]
        if m:=re.search(r'Tombala tarama ölçümü: chunk=(\d+) duvarMs=(\d+) taramaMs=(\d+) beklemeTuru=(\d+)',line):row['timing'].append(tuple(map(int,m.groups())))
        if m:=re.search(r'Tombala ortak kaynak bütçesi: (\d+) / 25 hedef, hedef başına toplam miktar (\d+)',line):row['budget'].append(tuple(map(int,m.groups())))
        if 'Tombala başladı!' in line:row['start']+=1
        if 'Arena depolama kapandı:' in line:row['close']+=1
    return rows
before,after=map(parse,sys.argv[1:3])
assert len(before)==len(after)==12
for a,b in zip(before,after):
    assert a['case']==b['case'] and a['seed']==b['seed'],(a,b)
    assert b['timing'] and all(0<=scan<=wall and n<=57 for n,wall,scan,polls in b['timing']),b
    assert b['close']==1 and len(b['budget'])==1,b
    n,wall,scan,polls=b['timing'][-1]
    print(b['case'],b['seed'],'old/new targets',a['budget'],b['budget'],'chunks',n,'wall_ms',wall,'scan_ms',scan,'start',b['start'])
print('Completed',sum(b['start'] for b in after),'/12; arena closes',sum(b['close'] for b in after))
