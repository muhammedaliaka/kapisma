"""Create isolated single-seed pool without changing live seed files."""
import json
from pathlib import Path
import sys
src,dst,seed=sys.argv[1:]
p=Path(dst)
assert not p.exists(), "refusing overwrite"
d=json.loads(Path(src).read_text(encoding="utf-8-sig"))
matches=[(c,e) for c,entries in d["classes"].items() for e in entries if str(e["seed"])==seed]
assert len(matches)==1,matches
c,e=matches[0]
d["classes"]={c:[e]}
d["class_names"]={c:d["class_names"][c]}
with p.open("x",encoding="utf-8") as f: json.dump(d,f,ensure_ascii=False)
print(c,seed,e["spawn"],e["biome"])
