"""Original distribution dependency smoke; never touches live worlds."""
from pathlib import Path
import hashlib, json, os, queue, socket, subprocess, threading, time, uuid, sys
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[2]
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852/server"
JARS=BASE/"external-mod-candidates"
RUN=BASE/"build"/("d40-furniture-"+uuid.uuid4().hex[:10])
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
COMBINED=True
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
carpet=ROOT/"_yedek/devletler-rts-prototip/laboratuvar/server/mods/fabric-carpet-1.21-1.4.147+v240613.jar"
assert digest(carpet)=="079fc8a4e073eb24b010ffcc588e59f93b984143e17d8ef19fbb0b6aff0f19d9"
mods.append(carpet)
mc=LAB/".fabric/remappedJars/minecraft-1.21.1-0.20.1/server-intermediary.jar"
libs=sorted((LAB/"libraries").rglob("*.jar"))
assert mc.is_file() and libs and all(p.is_file() for p in mods)
with socket.socket() as s:s.bind(("127.0.0.1",PORT))
RUN.mkdir(parents=True,exist_ok=False)
assert "eula=true" in (ROOT/"server/eula.txt").read_text()
(RUN/"eula.txt").write_text("eula=true\n",encoding="ascii")
(RUN/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=qa\nlevel-seed=42\nlevel-type=minecraft:normal\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nmax-players=8\nenable-rcon=false\nenable-query=false\n",encoding="ascii")
config=RUN/"config/polymer/auto-host.json"
config.parent.mkdir(parents=True)
config.write_text('{"enabled":false}',encoding="ascii")
options=RUN/"config/modernfix-mixins.properties"
options.write_text("mixin.perf.remove_biome_temperature_cache=false\nmixin.bugfix.paper_chunk_patches=false\n",encoding="ascii")
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
    def send(command):
        p.stdin.write(command+"\n");p.stdin.flush()
    def verify(condition,label,speaker="Server"):
        send("execute "+condition+" run say D40_"+label)
        print("PASS",wait("["+speaker+"] D40_"+label,12).strip(),flush=True)
    print("BOOT",wait("Done (",150).strip(),flush=True)
    send("forceload add 0 0");wait("Marked chunk",20)
    for i in range(20):
        marker=f"D40_READY_{i}"
        send("execute if loaded 0 300 0 run say "+marker)
        try:wait("[Server] "+marker,2);break
        except RuntimeError:pass
    else:raise RuntimeError("chunk not loaded")
    send("fill 0 300 0 10 300 10 minecraft:stone");wait("Successfully filled",20)
    send("scoreboard objectives add d40_qa dummy");wait("Created new objective",10)
    bot="d40bot"
    send("player "+bot+" spawn at 2.5 301 4.5");wait(bot+" joined the game",40)
    send("gamemode survival "+bot)
    send(f"tp {bot} 9.5 301 4.5 180 39.2")
    send(f"item replace entity {bot} hotbar.0 with minecraft:oak_planks")
    verify(f"if items entity {bot} weapon.mainhand minecraft:oak_planks if block 9 301 2 air","VANILLA_READY")
    send(f"player {bot} use once");time.sleep(1)
    verify("if block 9 301 2 minecraft:oak_planks","VANILLA_PLACE")
    for x,block,label in [(2,"tsa:spruce_table","TABLE"),(6,"tsa:spruce_chair","CHAIR")]:
        send(f"tp {bot} {x+.5} 301 4.5 180 39.2")
        send(f"item replace entity {bot} hotbar.0 with {block}")
        verify(f"if items entity {bot} weapon.mainhand {block} if block {x} 301 2 air","READY_"+label)
        send(f"player {bot} use once")
        time.sleep(1)
        verify(f"if block {x} 301 2 {block}","PLACED_"+label)
        verify(f"unless items entity {bot} hotbar.0 *","CONSUMED_"+label)
        if label=="TABLE":
            send(f"data get block {x} 301 2")
            print("PASS simple table has no block entity",wait("The target block is not a block entity",10).strip(),flush=True)
        send(f"tp {bot} {x+.5} 301 4.5 180 29.2")
        send(f"player {bot} use once")
        time.sleep(.6)
        if label=="TABLE":
            verify("unless entity @e[type=filament:decoration_seat]","TABLE_NO_SEAT")
        else:
            verify(f"as {bot} on vehicle if entity @s[type=filament:decoration_seat]","CHAIR_MOUNT","entity.filament.decoration_seat")
            send("scoreboard players set #vehicle d40_qa 0")
            send(f"execute as {bot} on vehicle if entity @s[type=filament:decoration_seat] run scoreboard players set #vehicle d40_qa 1")
            verify("if score #vehicle d40_qa matches 1","CHAIR_VEHICLE_POSITIVE")
            send(f"ride {bot} dismount");wait(bot+" stopped riding",10)
            send("scoreboard players set #vehicle d40_qa 0")
            send(f"execute as {bot} on vehicle run scoreboard players set #vehicle d40_qa 1")
            verify("if score #vehicle d40_qa matches 0","CHAIR_DISMOUNT")
        send(f"gamemode creative {bot}")
        send(f"tp {bot} {x+.5} 301 4.5 180 29.2")
        send(f"player {bot} attack once")
        time.sleep(.6)
        verify(f"if block {x} 301 2 air","REMOVED_"+label)
        verify("unless entity @e[type=filament:decoration_seat]","NO_SEAT_AFTER_"+label)
        send(f"gamemode survival {bot}")
    send("player "+bot+" kill");wait(bot+" left the game",20)
    send("stop");assert p.wait(timeout=60)==0;t.join(5);assert not t.is_alive()
    print("PASS clean shutdown",digest(RUN/"console.log"),flush=True)
finally:
    if p is not None and p.poll() is None:
        try:
            p.stdin.write("stop\n");p.stdin.flush();p.wait(timeout=30)
        except Exception:
            p.terminate()
            try:p.wait(timeout=10)
            except subprocess.TimeoutExpired:p.kill();p.wait()
    after={str(x):digest(x) for x in protected}
    print("PROTECTED_AFTER",after,flush=True);assert before==after
