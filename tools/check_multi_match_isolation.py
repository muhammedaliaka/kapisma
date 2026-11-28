"""Independent check of actual Minecraft server logs, not scenario echoes."""
import re,sys
from pathlib import Path
lines=[l for l in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines() if '[Server thread/INFO]:' in l]
def count(s): return sum(s in l for l in lines)
assert count('Kapisma hazir:')==1
assert count('UHC başladı!')==1
assert count('Eşya Yarışı · Tur 1 başladı')==1
assert count('Tombala başladı!')==1
assert count('Katil Kim başladı!')==1
assert count('Arena depolama kapandı:')==3
for g in ('UHC','ESYA','BINGO','KATIL'):
    assert count(g+' kapandi')==1,g
    assert count('[Server] QA_LOBBY_'+g)==1,g
assert count('Bot1 has the following entity data: [{count: 7, Slot: 0b, id: "minecraft:diamond"}]')==4
assert count('Bot1 has the following entity data: []')==4
assert count('Bot1 has the following entity data: 0')==4
assert count('diamonds has 0 [qa_multi]')==4
assert any('race Bot1 round=1' in l and 'score=1 lives=5 skips=0 resolved=true' in l for l in lines)
assert count('Bot1 aldı:')>=1
assert count('Gamerule showDeathMessages is currently set to: false')==1
print('PASS four distinct modes in one JVM, four clean inventories/ender chests/XP, teams and lobby mode, scoring, death-message restoration')
