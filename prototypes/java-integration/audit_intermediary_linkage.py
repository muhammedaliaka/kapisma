"""Read-only JVM member linkage audit against pinned QA candidate.
Checks symbolic own-package members including bootstrap MethodHandle targets.
External inheritance is classified as unresolved-by-this-audit, not PASS.
"""
from pathlib import Path
from zipfile import ZipFile
from struct import unpack_from
import hashlib,json,os,sys
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1]
jar=BASE/"build/kapisma-intermediary-qa.jar"
expected=json.loads((BASE/"build/intermediary-qa-manifest.json").read_text())
assert hashlib.sha256(jar.read_bytes()).hexdigest()==expected["candidate_sha256"]
def parse(data):
    assert data[:4]==b"\xca\xfe\xba\xbe"
    pos=8
    def u1():
        nonlocal pos
        v=data[pos];pos+=1;return v
    def u2():
        nonlocal pos
        v=unpack_from(">H",data,pos)[0];pos+=2;return v
    def u4():
        nonlocal pos
        v=unpack_from(">I",data,pos)[0];pos+=4;return v
    n=u2();cp=[None]*n;i=1
    while i<n:
        tag=u1()
        if tag==1:
            l=u2();cp[i]=(tag,data[pos:pos+l].decode("utf-8",errors="replace"));pos+=l
        elif tag in (3,4):pos+=4
        elif tag in (5,6):pos+=8;i+=1
        elif tag in (7,8,16,19,20):cp[i]=(tag,u2())
        elif tag in (9,10,11,12,17,18):cp[i]=(tag,u2(),u2())
        elif tag==15:cp[i]=(tag,u1(),u2())
        else:raise ValueError(tag)
        i+=1
    def utf(i):return cp[i][1]
    def cls(i):return utf(cp[i][1]) if i else None
    u2();name=cls(u2());superclass=cls(u2())
    parents=[superclass]+[cls(u2()) for _ in range(u2())]
    def members():
        out=set()
        for _ in range(u2()):
            u2();a,b=utf(u2()),utf(u2());out.add((a,b))
            for _ in range(u2()):u2();l=u4();skip(l)
        return out
    def skip(l):
        nonlocal pos
        pos+=l
    fields=members();methods=members()
    refs=[]
    for row in cp:
        if row and row[0] in (9,10,11):
            owner=cls(row[1]);nt=cp[row[2]]
            refs.append(("field" if row[0]==9 else "method",owner,utf(nt[1]),utf(nt[2])))
    return name,{"parents":[p for p in parents if p],"fields":fields,"methods":methods,"refs":refs}
with ZipFile(jar) as z:
    classes=dict(parse(z.read(name)) for name in z.namelist() if name.endswith(".class") and name.startswith("tr/kapisma/"))
mc=ROOT/"_yedek/sovereign-uyum-20261125-221852/server/.fabric/remappedJars/minecraft-1.21.1-0.20.1/server-intermediary.jar"
game=ZipFile(mc);jdk=ZipFile(Path(os.environ["JAVA_HOME"])/"jmods/java.base.jmod")
external_cache={}
def external(owner):
    if owner not in external_cache:
        archive,prefix=(jdk,"classes/") if owner.startswith("java/") else (game,"")
        try:external_cache[owner]=parse(archive.read(prefix+owner+".class"))[1]
        except KeyError:external_cache[owner]=None
    return external_cache[owner]
staged={p.relative_to(BASE/"build/intermediary-qa-classes").as_posix().removesuffix(".class") for p in (BASE/"build/intermediary-qa-classes").rglob("*.class")}
R38="--r38-base" in sys.argv
if R38:
    r38=ROOT/"mod/kapisma/build/libs/kapisma-0.1.0.jar"
    r38hash="9708483619c3dab78ff3a536d170a8c5c28c42564ba99045ef634dc4ff880463"
    assert hashlib.sha256(r38.read_bytes()).hexdigest()==r38hash,"R38 coordination hash mismatch"
    # In-memory comparison ONLY. Old overlay snapshot, not latest named classes.
    overlay={name:classes[name] for name in staged}
    with ZipFile(r38) as z:
        classes=dict(parse(z.read(name)) for name in z.namelist() if name.endswith(".class") and name.startswith("tr/kapisma/"))
    classes.update(overlay)
    print("R38 in-memory ABI comparison of previous intermediary overlay, no new artifact",flush=True)
def resolve(owner,kind,name,desc,seen=None):
    seen=set() if seen is None else seen
    if owner in seen:return False,False
    seen.add(owner)
    c=classes.get(owner) or external(owner)
    if c is None:return False,not owner.startswith("tr/kapisma/")
    if (name,desc) in c[kind+"s"]:return True,False
    if name=="<init>":return False,False
    ext=False
    for parent in c["parents"]:
        ok,unknown=resolve(parent,kind,name,desc,seen)
        if ok:return True,False
        ext|=unknown
    return False,ext
missing={};deferred={};resolved=0
for source in sorted(classes if R38 else staged):
    for kind,owner,name,desc in classes[source]["refs"]:
        if not owner.startswith("tr/kapisma/"):continue
        ok,unknown=resolve(owner,kind,name,desc)
        key=f"{kind} {owner}.{name}{desc}"
        if ok:resolved+=1
        else:(deferred if unknown else missing).setdefault(key,set()).add(source)
print("Resolved own-package reference occurrences:",resolved)
for label,items in [("MISSING",missing),("EXTERNAL_PARENT_REVIEW",deferred)]:
    for key,owners in sorted(items.items()):
        print(label,key,"FROM",",".join(sorted(owners)))
report={"candidate":expected["candidate_sha256"],"resolved_reference_occurrences":resolved,"missing":{k:sorted(v) for k,v in missing.items()},"external_parent_review":{k:sorted(v) for k,v in deferred.items()}}
if R38:
    assert hashlib.sha256(r38.read_bytes()).hexdigest()==r38hash,"R38 changed during audit"
    report["r38_base_sha256"]=r38hash
    report["scope"]="all own-package members in combined old overlay + R38; not current source or runtime"
    with (BASE/"build/intermediary-r38-abi-report.json").open("x",encoding="utf-8") as out:json.dump(report,out,indent=2)
else:(BASE/"build/intermediary-linkage-report.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
print("SUMMARY definite missing",len(missing),"external review",len(deferred),flush=True)
game.close();jdk.close()
if missing or deferred:sys.exit(2)
print("PASS own-package linkage preflight",flush=True)