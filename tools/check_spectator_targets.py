"""Verify actual server evidence, separate from scenario command echoes."""
import sys
from pathlib import Path
lines=[l for l in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines() if '[Server thread/INFO]:' in l]
for marker in ('QA_SPECTATOR','QA_REJOIN_SPECTATOR','QA_LOBBY_RESET','QA_NEXT_MATCH'):
    assert sum('[Server] '+marker in l for l in lines)==1,marker
for name,value in [('denied',0),('live',1),('dead',1),('offline',0),('timeout',1),('stale',0)]:
    assert sum(f'{name} has {value} [{sys.argv[2]}]' in l for l in lines)==1,(name,value)
for pos,n in [('[12.5d, 310.0d, 12.5d]',1),('[24.5d, 311.0d, 24.5d]',1),('[-24.5d, 311.0d, -24.5d]',2)]:
    assert sum('Bot3 has the following entity data: '+pos in l for l in lines)==n,pos
assert sum('Bağlantısı kopan oyuncu elendi.' in l for l in lines)==1
assert sum('UHC başladı!' in l for l in lines)==2
assert sum('Arena depolama kapandı:' in l for l in lines)==2
print('PASS live/dead/offline targets, disconnect timeout and rejoin, lobby reset, two UHC lifecycle checks')
