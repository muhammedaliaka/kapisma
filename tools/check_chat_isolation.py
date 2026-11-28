"""Independent check of actual server log; no command-echo evidence."""
import re, sys
from pathlib import Path
lines=[line for line in Path(sys.argv[1]).read_text(encoding="utf-8-sig").splitlines() if "[Server thread/INFO]:" in line]
blocks=[m.group(1) for line in lines if (m:=re.search(r"Sohbet izolasyonu: 1 alıcı engellendi \((Bot\d+)\)",line))]
assert blocks==["Bot1","Bot2","Bot1","Bot1"], blocks
assert any("[Server] BOTH_ELIMINATED" in l for l in lines)
assert any("blocked has 0 [qa_chat_result]" in l for l in lines)
assert any("allowed has 1 [qa_chat_result]" in l for l in lines)
assert any("alive has 1 [qa_chat_result]" in l for l in lines)
assert sum("Arena depolama kapandı:" in l for l in lines)==1
assert not any("[ERROR]" in l for l in lines)
print("PASS 4 vanilla recipient blocks, custom isolation and positive routes, one arena storage close")
