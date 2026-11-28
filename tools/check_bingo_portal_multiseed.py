"""Verify far returns using only actual server-side log data."""
import re,sys
from pathlib import Path
lines=[l for l in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines() if '[Server thread/INFO]:' in l]
cases={}
case=None
for l in lines:
    if m:=re.search(r'\[Server\] QA_PORTAL_CASE_(forest|desert)$',l):
        case=m[1];cases[case]={'seed':None,'end':0,'nether':0,'returns':[],'pos':[],'markers':[],'close':0,'start':0}
    if case is None:continue
    c=cases[case]
    if m:=re.search(r'BINGO dunyasi acildi: seed=(-?\d+)',l):c['seed']=m[1]
    if 'Bot1 tamamladı: Görev: End\'e geç' in l:c['end']+=1
    if 'Bot2 tamamladı: Görev: Nether\'a geç' in l:c['nether']+=1
    if m:=re.search(r'Tombala Nether uzak dönüş: (-?\d+), (-?\d+), (-?\d+) aday=(\d+) platform=(true|false)',l):
        c['returns'].append((int(m[1]),int(m[2]),int(m[3]),int(m[4]),m[5]))
    if m:=re.search(r'Bot2 has the following entity data: \[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]',l):
        c['pos'].append(tuple(map(float,m.groups())))
    if m:=re.search(r'\[Server\] QA_FAR_(forest|desert)_(\d+)$',l):c['markers'].append(int(m[2]))
    if 'Tombala başladı!' in l:c['start']+=1
    if 'Arena depolama kapandı:' in l:c['close']+=1
assert set(cases)=={'forest','desert'},cases
for name,seed in [('forest','-1250613006204744322'),('desert','-335906671735727005')]:
    c=cases[name]
    assert c['seed']==seed and c['end']==c['nether']==c['start']==1,c
    assert len(c['returns'])==len(c['pos'])==2 and c['markers']==[0,1] and c['close']==3,c
    assert any('[Server] QA_END_'+name in l for l in lines)
    for (x,y,z,n,fallback),(px,py,pz) in zip(c['returns'],c['pos']):
        assert -150<=x<150 and -150<=z<150,(name,x,z)
        assert (px,py,pz)==(x+.5,float(y),z+.5),(name,x,y,z,px,py,pz)
        print(name,seed,'landing',x,y,z,'safe candidates',n,'fallback',fallback)
print('PASS two seeds, four far returns with matching entity coordinates, two End round trips, six dimension closes')
