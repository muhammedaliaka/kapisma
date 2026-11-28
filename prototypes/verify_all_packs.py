"""Read-only generated pack inventory and source/install parity. No live world access."""
from pathlib import Path
import importlib.util, hashlib, json
BASE=Path(__file__).resolve().parent
LAB=BASE.parent/"_yedek/sovereign-uyum-20261125-221852/server"
MODES={"kirik-telsiz":"kt","donen-karakol":"dk","kervan-baskini":"kb","yasayan-harita":"yh","insa-et-savun":"is","kit-ihalesi":"ki","alcak-irtifa":"ai"}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
for name,ns in MODES.items():
    src=BASE/name/"prepare.py";spec=importlib.util.spec_from_file_location("pack_"+ns,src)
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    expected=m.generate();root=LAB/(name+"-lab")/"datapacks"/name
    assert root.is_dir() and root.resolve()==root
    funcs=root/"data"/ns/"function"
    actual={p.stem:p.read_text(encoding="utf-8") for p in funcs.glob("*.mcfunction")}
    assert actual==expected,(name,set(actual)^set(expected))
    assert json.loads((root/"pack.mcmeta").read_text(encoding="utf-8"))["pack"]["pack_format"]==48
    assert json.loads((root/"data/minecraft/tags/function/load.json").read_text(encoding="utf-8"))["values"]==[ns+":load"]
    print("PASS",name,"functions",len(actual),"source",sha(src))
print("PASS all seven datapack source/install parity; no live files written")
