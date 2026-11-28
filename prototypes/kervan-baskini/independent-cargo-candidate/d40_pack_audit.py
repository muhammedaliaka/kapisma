from pathlib import Path
import json,zipfile,hashlib
BASE=Path(__file__).resolve().parent
pack=BASE/"build/d39-native-de19e71121/polymer/resource_pack.zip"
assert hashlib.sha256(pack.read_bytes()).hexdigest()=="09dd3c1c2ca6f67fdd4a2d225f961078cbfe79706d92593e1910f8afc0eaf62b"
vanilla=Path.home()/"AppData/Roaming/.minecraft/versions/Fabric 1.21.1/Fabric 1.21.1.jar"
with zipfile.ZipFile(pack) as z,zipfile.ZipFile(vanilla) as v:
    names=set(z.namelist())|set(v.namelist())
    def path(ref):
        ns,ref=ref.split(":",1) if ":" in ref else ("minecraft",ref)
        return f"assets/{ns}/models/{ref}.json"
    missing=[]
    for n in sorted(z.namelist()):
        if not("/models/" in n and n.endswith(".json")):continue
        d=json.loads(z.read(n))
        refs=[d["parent"]] if "parent" in d else []
        refs += [x["model"] for x in d.get("overrides",[])]
        for ref in refs:
            if path(ref) not in names and ref not in ("builtin/generated","builtin/entity","minecraft:builtin/generated","minecraft:builtin/entity"):
                missing.append((n,ref))
    print("MISSING_MODEL_REFS",missing)
    print("ATLAS",[(n,z.read(n).decode()[:3000]) for n in z.namelist() if "/atlases/" in n])
    print("STONE",v.read("assets/minecraft/models/item/stone.json").decode(),v.read("assets/minecraft/models/block/stone.json").decode())
    for q in (BASE/"external-mod-candidates").glob("*.jar"):
        if not any(k in q.name for k in ("tsa","filament","decorative","polymer")):continue
        with zipfile.ZipFile(q) as a:
            for n in a.namelist():
                if "/filament/" not in n or not n.endswith(".json") or not n.startswith("data/"):continue
                d=json.loads(a.read(n))
                if isinstance(d,dict) and d.get("model") in ("stone","minecraft:stone"):
                    print("SOURCE",q.name,n,d)
    print("VANILLA_TRIMS",[n for n in v.namelist() if "/trims/models/armor/" in n and "chainmail" in n])
