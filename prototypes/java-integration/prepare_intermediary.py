"""One isolated full intermediary QA candidate, built from pinned live base and verified staging.
No KOD build/libs reads; never deployed. Keep named QA classes as rollback/reference.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import os, subprocess, hashlib, json
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1];MOD=ROOT/"mod/kapisma"
G=Path.home()/".gradle/caches/modules-2/files-2.1"
JAVA=Path(os.environ["JAVA_HOME"])/"bin"
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def pick(group,name,version):
    p=[x for x in (G/group/name/version).rglob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar"))]
    assert len(p)==1,p
    return p[0]
live=ROOT/"server/mods/kapisma-0.1.0.jar"
assert sha(live)=="bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"
staging=BASE/"build/classes"
snapshot={p:sha(p) for p in staging.rglob("*.class")}
assert len(snapshot)>30
mapping=next((Path.home()/".gradle/caches/fabric-loom/1.21.1").glob("loom.mappings.*/mappings.tiny"))
named=next((MOD/".gradle/loom-cache/minecraftMaven").rglob("minecraft-merged-*.jar"))
cp=[pick("net.fabricmc","tiny-remapper","0.13.0"),pick("net.fabricmc","mapping-io","0.8.0")]
cp += [pick("org.ow2.asm",a,"9.10.1") for a in ("asm","asm-tree","asm-commons","asm-analysis","asm-util")]
deps=[x for x in (MOD/".gradle/loom-cache/remapped_mods").rglob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar"))]
out=BASE/"build/intermediary-qa-classes";out.mkdir(exist_ok=True)
# Remap whole staging, including QA fixtures. Only game/library linkage; no KOD mutable class output.
subprocess.run([str(JAVA/"java.exe"),"-Xmx1024m","-cp",os.pathsep.join(map(str,cp)),"net.fabricmc.tinyremapper.Main",str(staging),str(out),str(mapping),"named","intermediary",str(named),*map(str,deps),"--mixin"],check=True)
assert {p:sha(p) for p in staging.rglob("*.class")}==snapshot
stage={p.relative_to(out).as_posix():p.read_bytes() for p in out.rglob("*.class")}
assert set(stage)=={p.relative_to(staging).as_posix() for p in snapshot},"unexpected remap residue"
resources=BASE/"overlay-resources"
artifact=BASE/"build/kapisma-intermediary-qa.jar"
existing=artifact.exists()
if existing:print('VERIFY existing QA candidate; no rebuild',flush=True)
with ZipFile(live) as base:
    meta=json.loads(base.read("fabric.mod.json"))
    assert meta["id"]=="kapisma"
    assert "kapisma.mixins.json" in meta["mixins"]
    meta["version"]="0.1.0-arena-qa"
    meta["mixins"]+=["kapisma.analog-voice.mixins.json","kapisma.arena.mixins.json"]
    meta["entrypoints"]["main"] += ["tr.kapisma.game.EquipmentRuntimeProof","tr.kapisma.voice.VoiceRuntimeProof"]
    meta["entrypoints"]["voicechat"] += ["tr.kapisma.voice.VoiceRuntimeProof"]
    # Base includes nested Fantasy/SGUI jars; preserve byte-for-byte.
    overlay={"fabric.mod.json":json.dumps(meta,ensure_ascii=False).encode(),**stage}
    for p in resources.glob("*.mixins.json"):overlay[p.name]=p.read_bytes()
    if not existing:
        import copy
        with ZipFile(artifact,"x",ZIP_DEFLATED) as z:
            for info in base.infolist():
                if info.filename in overlay:continue
                z.writestr(copy.copy(info),base.read(info))
            for name,data in sorted(overlay.items()):z.writestr(name,data)
    with ZipFile(artifact) as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
        for name in base.namelist():
            if name not in overlay:assert base.read(name)==z.read(name),name
        for name,data in overlay.items():assert z.read(name)==data
assert sha(live)=="bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"
manifest={"base_sha256":sha(live),"candidate_sha256":sha(artifact),"mapping_sha256":sha(mapping),"classes":len(stage),"bytes":artifact.stat().st_size}
(BASE/"build/intermediary-qa-manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
print("PASS full intermediary QA candidate assembled",json.dumps(manifest),flush=True)