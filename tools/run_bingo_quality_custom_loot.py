"""Verify replaced loot is rejected. Touch only the dedicated flat test fixture."""
import hashlib, shutil, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test_proof'
pack=fixture/'world/datapacks/kapisma_loot_empty'
raw=root/'tools/last_bingo_quality_custom_loot_raw.log'
assert not pack.exists() and not raw.exists()
assert (fixture/'server.properties').exists()
assert 'server-port=25571' in (fixture/'server.properties').read_text(encoding='utf-8')
jar=fixture/'mods/kapisma-0.1.0.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1ceadcb14db4065be811898d93052ce466f469755fa4b3459e52af8d93c82924'
try:
    shutil.copytree(root/'tools/test-fixtures/loot-empty',pack)
    code=subprocess.call([sys.executable,str(root/'tools/mctest.py'),str(root/'tools/scenarios/bingo_custom_loot.txt'),'--dir',str(fixture),'--xmx','2G','--command-delay','0.65'])
    raw.write_bytes((fixture/'logs/latest.log').read_bytes())
finally:
    if pack.exists(): shutil.rmtree(pack)
lines=[l for l in raw.read_text(encoding='utf-8-sig').splitlines() if '[Server thread/INFO]:' in l]
assert code==0
assert any('Kaynak ganimet kanıtı reddedildi: minecraft:blocks/dirt' in l for l in lines)
assert any('Bu seed ve sınırda 25 farklı hedef doğrulanamadı' in l for l in lines)
assert not any('Tombala başladı!' in l for l in lines)
assert sum('Arena depolama kapandı:' in l for l in lines)==1
print('PASS: overridden loot rejected, no invalid match, storage closed, fixture pack removed')
