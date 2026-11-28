"""Test custom advancement rejection and vanilla positive control, isolated fixture only."""
import hashlib,json,shutil,subprocess,sys,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test_order'
cfg=fixture/'config/kapisma/seeds.json'
original=cfg.read_bytes()
obj=json.loads(original.decode('utf-8-sig'))
entry=next(e for es in obj['classes'].values() for e in es if str(e['seed'])=='-1250613006204744322')
obj['classes']['qa_advancement']=[entry]
pack=fixture/'world/datapacks/qa_advancement_proof'
assert not pack.exists()
assert 'server-port=25572' in (fixture/'server.properties').read_text(encoding='utf-8')
scenario=root/'tools/scenarios/bingo_advancement_proof.txt'
assert scenario.exists()
for name in ('custom','vanilla'):
    assert not (root/f'tools/last_advancement_proof_{name}_raw.log').exists()
print('JAR',hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)
def run(name):
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(scenario),'--dir',str(fixture),'--xmx','2G','--command-delay','0.65'])
    raw=root/f'tools/last_advancement_proof_{name}_raw.log'
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
    lines=[l for l in raw.read_text(encoding='utf-8-sig').splitlines() if '[Server thread/INFO]:' in l]
    assert code==0, (name,code)
    assert sum('Tombala başladı!' in l for l in lines)==1
    assert sum('Arena depolama kapandı:' in l for l in lines)==1
    card=next(l.split('Kart icerigi: ',1)[1] for l in lines if 'Kart icerigi: ' in l)
    assert len(re.findall(r'(GOREV:[^=]+|[a-z0-9_]+)=\d+[,.]\d+(?:\s|$)',card))==25
    rejected=[l for l in lines if 'Tombala görev kanıtı reddedildi:' in l]
    if name=='custom':
        assert any('minecraft:story/root' in l for l in rejected), rejected
        assert 'GOREV:Çalışma masası edin=' not in card, card
    else:
        assert not rejected,rejected
        assert 'GOREV:' in card,card
    print('PASS',name,'card 25/25','rejected',len(rejected),flush=True)
try:
    cfg.write_text(json.dumps(obj,ensure_ascii=False),encoding='utf-8')
    run('vanilla')
    dest=pack/'data/minecraft/advancement/story/root.json'
    dest.parent.mkdir(parents=True)
    (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Isolated proof test'}}),encoding='utf-8')
    dest.write_text(json.dumps({'criteria':{'never':{'trigger':'minecraft:impossible'}},'requirements':[['never']]}),encoding='utf-8')
    run('custom')
finally:
    if pack.exists(): shutil.rmtree(pack)
    cfg.write_bytes(original)
    assert cfg.read_bytes()==original
print('ADVANCEMENT PROOF 2/2 PASS')
