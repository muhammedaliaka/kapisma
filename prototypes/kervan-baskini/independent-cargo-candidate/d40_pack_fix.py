"""Minimal QA-only pack correction. Preserve original bytes and every other entry."""
import zipfile,json,hashlib
from pathlib import Path
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def fix(source,dest):
    assert not dest.exists()
    assert sha(source)=="09dd3c1c2ca6f67fdd4a2d225f961078cbfe79706d92593e1910f8afc0eaf62b"
    refs={"minecraft:trims/models/armor/chainmail","minecraft:trims/models/armor/chainmail_leggings"}
    changed=[];models=[]
    with zipfile.ZipFile(source) as src,zipfile.ZipFile(dest,"x",compression=zipfile.ZIP_DEFLATED) as out:
        for info in src.infolist():
            data=src.read(info.filename);new=data
            if info.filename.startswith("assets/minecraft/models/custom/furniture/boards/") and info.filename.endswith(".json"):
                d=json.loads(data)
                if d.get("parent")=="stone":
                    assert d.get("elements") and d.get("textures") and d.get("display")
                    d["parent"]="minecraft:block/stone"
                    new=json.dumps(d,separators=(",",":"),ensure_ascii=False).encode()
                    models.append(info.filename)
            if info.filename=="assets/minecraft/atlases/armor_trims.json":
                d=json.loads(data)
                assert len(d["sources"])==1 and set(d["sources"][0]["textures"])==refs
                d["sources"]=[]
                new=json.dumps(d,separators=(",",":")).encode()
            if info.filename=="assets/minecraft/atlases/blocks.json":
                d=json.loads(data)
                assert len(d["sources"])==2 and {s["resource"] for s in d["sources"]}==refs
                d["sources"]=[]
                new=json.dumps(d,separators=(",",":")).encode()
            if data!=new:changed.append(info.filename)
            out.writestr(info,new)
    assert len(models)==14 and len(changed)==16
    with zipfile.ZipFile(source) as src,zipfile.ZipFile(dest) as out:
        assert src.namelist()==out.namelist() and out.testzip() is None
        assert [n for n in src.namelist() if src.read(n)!=out.read(n)]==changed
        for n in models:
            a=json.loads(src.read(n));b=json.loads(out.read(n));assert a.pop("parent")=="stone" and b.pop("parent")=="minecraft:block/stone" and a==b
    print("PASS pack fix 14 model parents and 2 atlas files; other entries byte-identical",sha(dest),dest.stat().st_size,flush=True)
    return dest
