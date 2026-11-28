"""Download exact official Fabric candidates to MOD-only staging; no installation."""
from pathlib import Path
import hashlib, json, io, zipfile, urllib.request
BASE=Path(__file__).resolve().parent
DEST=BASE/"candidates";DEST.mkdir(exist_ok=True)
PINNED={"lithium":"mc1.21.1-0.15.4-fabric","ferrite-core":"7.0.3-fabric","modernfix":"5.25.0+mc1.21.1","noisium":"2.3.0+mc1.21-1.21.1"}
def fetch(url,limit):
    if not url.startswith(("https://api.modrinth.com/","https://cdn.modrinth.com/")):raise ValueError(url)
    req=urllib.request.Request(url,headers={"User-Agent":"Kapisma-MOD-QA/1.0 (local compatibility testing)"})
    with urllib.request.urlopen(req,timeout=30) as res:
        data=res.read(limit+1)
    if len(data)>limit:raise ValueError("oversize")
    return data
def audit(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        assert z.testzip() is None
        meta=json.loads(z.read("fabric.mod.json"))
        natives=[n for n in z.namelist() if n.lower().endswith((".so",".dll",".dylib",".jnilib",".exe"))]
        java=[]
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/versions/"):
                head=z.read(n)[:8]
                assert head[:4]==bytes.fromhex("cafebabe")
                java.append(int.from_bytes(head[6:8],"big"))
        nested=[j["file"] for j in meta.get("jars",[])]
        return {"id":meta["id"],"version":meta["version"],"environment":meta.get("environment","*"),"depends":meta.get("depends",{}),"breaks":meta.get("breaks",{}),"max_class_major":max(java,default=0),"native_entries":natives,"nested_jars":nested,"mixins":meta.get("mixins",[])}
manifest={}
for slug,version in PINNED.items():
    from urllib.parse import quote
    url="https://api.modrinth.com/v2/project/"+slug+"/version?loaders="+quote('["fabric"]')+"&game_versions="+quote('["1.21.1"]')
    records=json.loads(fetch(url,4_000_000))
    matches=[r for r in records if r["version_number"]==version]
    if not matches:
        # Modrinth version_number may omit platform suffix.
        matches=[r for r in records if r["name"]==version or r["version_number"]==version.removesuffix("-fabric")]
    if len(matches)!=1:raise ValueError((slug,version,[(r["version_number"],r["name"]) for r in records[:15]]))
    r=matches[0]
    assert r["version_type"]=="release" and "fabric" in r["loaders"] and "1.21.1" in r["game_versions"]
    f=next(x for x in r["files"] if x["primary"])
    assert Path(f["filename"]).name==f["filename"] and f["filename"].endswith(".jar")
    path=DEST/f["filename"]
    data=path.read_bytes() if path.exists() else fetch(f["url"],20_000_000)
    assert len(data)==f["size"]
    for algo in ("sha1","sha512"):assert hashlib.new(algo,data).hexdigest()==f["hashes"][algo],slug
    meta=audit(data)
    assert meta["max_class_major"]<=65 and not meta["native_entries"],(slug,meta)
    if not path.exists():
        with path.open("xb") as out:out.write(data)
    manifest[slug]={"project_id":r["project_id"],"version_id":r["id"],"version_number":r["version_number"],"file":path.name,"url":f["url"],"size":len(data),"sha256":hashlib.sha256(data).hexdigest(),"sha512":f["hashes"]["sha512"],"metadata":meta}
    print("PASS",slug,meta["version"],len(data),manifest[slug]["sha256"],flush=True)
(BASE/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
print("PASS candidate inventory only; no mods directory modified")
