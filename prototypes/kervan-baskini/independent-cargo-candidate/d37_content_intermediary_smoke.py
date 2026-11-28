"""Original distribution dependency smoke; never touches live worlds."""
from pathlib import Path
import hashlib, json, os, queue, socket, subprocess, threading, time, uuid, sys
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[2]
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852/server"
JARS=BASE/"external-mod-candidates"
RUN=BASE/"build"/("d37-intermediary-"+uuid.uuid4().hex[:10])
PORT=25584
def digest(p,algorithm="sha256"):
    return hashlib.new(algorithm,p.read_bytes()).hexdigest()
protected=[ROOT/"server/mods/kapisma-0.1.0.jar",ROOT/"_yedek/devletler-rts-prototip/mods/kapisma-rts-rules.jar"]
before={str(p):digest(p) for p in protected}
print("PROTECTED_BEFORE",before,flush=True)
manifest=json.loads((BASE/"D37_CONTENT_CANDIDATES.json").read_text())
mods=[]
for m in manifest["mods"]:
    p=JARS/m["name"]
    assert p.stat().st_size==m["size"] and digest(p,"sha512")==m["sha512"]
    mods.append(p)
poly=JARS/"polymer-bundled-0.9.19+1.21.1.jar"
assert poly.stat().st_size==1705944 and digest(poly,"sha512")=="6a804059be62ed312f6f3c8ef31fbda5889d2a0d088891966bf5a9ae18756198617b7fede322f596b1c98c0068638e28d9b6099037e00eca32a864f3441354cf"
mods.extend([poly,LAB/"mods/fabric-api-0.116.18+1.21.1.jar"])
COMBINED="--combined" in sys.argv
if COMBINED:
    wanted={"lithium","ferrite-core","modernfix","noisium"}
    candidates=[]
    for filename in ("D27_OPTIMIZER_CANDIDATES.json","D33_OPTIMIZER_CANDIDATES.json"):
        candidates.extend(json.loads((BASE/filename).read_text())["mods"])
    selected=[v for v in candidates if v["slug"] in wanted]
    assert len(selected)==4 and {v["slug"] for v in selected}==wanted
    for m in selected:
        jar=JARS/m["name"]
        assert jar.stat().st_size==m["size"] and digest(jar,"sha512")==m["sha512"]
        mods.append(jar)
mc=LAB/".fabric/remappedJars/minecraft-1.21.1-0.20.1/server-intermediary.jar"
libs=sorted((LAB/"libraries").rglob("*.jar"))
assert mc.is_file() and libs and all(p.is_file() for p in mods)
with socket.socket() as s:s.bind(("127.0.0.1",PORT))
RUN.mkdir(parents=True,exist_ok=False)
assert "eula=true" in (ROOT/"server/eula.txt").read_text()
(RUN/"eula.txt").write_text("eula=true\n",encoding="ascii")
(RUN/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=qa\nlevel-seed=42\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nmax-players=8\nenable-rcon=false\nenable-query=false\n",encoding="ascii")
config=RUN/"config/polymer/auto-host.json"
config.parent.mkdir(parents=True)
config.write_text('{"enabled":false}',encoding="ascii")
def q(x):return '"'+str(x).replace("\\","/")+'"'
args=RUN/"java.args"
args.write_text("\n".join(["-Xms256m","-Xmx2048m","-XX:ActiveProcessorCount=4",q("-Dfabric.gameJarPath="+str(mc)),"-Dfabric.gameMappingNamespace=intermediary","-Dfabric.defaultModDistributionNamespace=intermediary",q("-Dfabric.addMods="+os.pathsep.join(map(str,mods))),"-cp",q(os.pathsep.join(map(str,[mc,*libs]))),"net.fabricmc.loader.impl.launch.knot.KnotServer","nogui"]),encoding="utf-8")
print("QA_DIR",RUN,flush=True)
print("MODS",[(p.name,digest(p)) for p in mods],flush=True)
p=None
try:
    p=subprocess.Popen([str(Path(os.environ["JAVA_HOME"])/"bin/java.exe"),"@"+str(args)],cwd=RUN,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding="utf-8",errors="replace")
    messages=queue.Queue()
    def reader():
        with (RUN/"console.log").open("w",encoding="utf-8") as log:
            for line in p.stdout:
                log.write(line);log.flush();messages.put(line)
    t=threading.Thread(target=reader,daemon=True);t.start()
    lines=[]
    def wait(marker,seconds):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            try:line=messages.get(timeout=.25)
            except queue.Empty:
                if p.poll() is not None and not t.is_alive():break
                continue
            lines.append(line)
            if marker in line:return line
        raise RuntimeError("Missing "+marker+" exit="+str(p.poll())+"\n"+"".join(lines[-40:]))
    print("BOOT",wait("Done (",120).strip(),flush=True)
    p.stdin.write("datapack list enabled\n");p.stdin.flush()
    enabled=wait("data pack(s) enabled",20)
    print("DATAPACKS",enabled.strip(),flush=True)
    assert "decorativeboxes" in enabled and "tsa-decorations" in enabled
    if COMBINED:
        def send(command):
            p.stdin.write(command+"\n");p.stdin.flush()
        send("forceload add 0 0")
        wait("Marked chunk",20)
        send("setblock 0 100 0 decorativeboxes:oak_box")
        print("PLACE",wait("Changed the block",20).strip(),flush=True)
        send("execute if block 0 100 0 decorativeboxes:oak_box run say D37_BOX_PRESENT")
        print("VERIFY",wait("[Server] D37_BOX_PRESENT",20).strip(),flush=True)
        send("setblock 0 100 0 air")
        wait("Changed the block",20)
        send("execute if block 0 100 0 air run say D37_BOX_CLEARED")
        print("CLEANUP",wait("[Server] D37_BOX_CLEARED",20).strip(),flush=True)
        print("PASS combined optimizer + actual decorative box placement and removal",flush=True)
    p.stdin.write("stop\n");p.stdin.flush()
    assert p.wait(timeout=40)==0
    t.join(5)
    print("PASS original intermediary boot, both content datapacks, clean stop",flush=True)
finally:
    if p is not None and p.poll() is None:
        p.terminate()
        try:p.wait(timeout=10)
        except subprocess.TimeoutExpired:p.kill();p.wait()
    after={str(x):digest(x) for x in protected}
    print("PROTECTED_AFTER",after,flush=True)
    assert before==after
