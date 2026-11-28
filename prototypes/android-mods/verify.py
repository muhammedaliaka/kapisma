"""PC compatibility smoke with current KOD candidate, four bots, no Android performance claim."""
from pathlib import Path
import json,hashlib,os,subprocess,time,threading,socket,msvcrt,re,sys,math
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1]
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852/server"
RUN=BASE/"runtime-proof";RUN.mkdir(exist_ok=True)
assert RUN.resolve()==RUN
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
candidate=ROOT/"mod/kapisma/build/libs/kapisma-0.1.0.jar"
live=ROOT/"server/mods/kapisma-0.1.0.jar"
rts=ROOT/"_yedek/devletler-rts-prototip/mods/kapisma-rts-rules.jar"
before={p:sha(p) for p in (candidate,live,rts)}
assert before[candidate]=="0e2326996ed44829a736cd3aa7517d3bcf5d00ea473bdee999949261ec1f5843"
base=[candidate,LAB/"mods/fabric-api-0.116.18+1.21.1.jar",LAB/"mods/Origins-1.13.0-pre.3+mc.1.21.1.jar",LAB/"mods/voicechat-fabric-1.21.1-2.6.27.jar",ROOT/"_yedek/devletler-rts-prototip/laboratuvar/server/mods/fabric-carpet-1.21-1.4.147+v240613.jar"]
assert all(p.is_file() for p in base)
manifest=json.loads((BASE/"manifest.json").read_text())
adds={k:BASE/"candidates"/v["file"] for k,v in manifest.items()}
assert all(sha(p)==manifest[k]["sha256"] for k,p in adds.items())
mc=LAB/".fabric/remappedJars/minecraft-1.21.1-0.20.1/server-intermediary.jar"
libs=sorted((LAB/"libraries").rglob("*.jar"))
assert mc.is_file() and libs
cp=os.pathsep.join(map(str,[mc,*libs]))
java=Path(os.environ["JAVA_HOME"])/"bin/java.exe"
props=RUN/"server.properties"
settings="server-ip=127.0.0.1\nserver-port=25580\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=optimization-proof\nlevel-seed=5743893257777525637\nlevel-type=minecraft:normal\ngenerate-structures=true\nview-distance=3\nsimulation-distance=3\nmax-players=8\nspawn-protection=0\ndifficulty=peaceful\nenable-rcon=false\nenable-query=false\n"
if props.exists():
    assert "server-ip=127.0.0.1" in props.read_text() and "server-port=25580" in props.read_text()

assert "eula=true" in (LAB/"eula.txt").read_text()
(RUN/"eula.txt").write_text("eula=true\n",encoding="ascii")
vc=RUN/"config/voicechat";vc.mkdir(parents=True,exist_ok=True)
(vc/"voicechat-server.properties").write_text("port=25581\nbind_address=127.0.0.1\nvoice_host=127.0.0.1:25581\n",encoding="ascii")
empty=RUN/"mods";empty.mkdir(exist_ok=True);assert not list(empty.iterdir())
def port_free(port,kind):
    with socket.socket(socket.AF_INET,kind) as s:s.bind(("127.0.0.1",port))
def run(name,keys):
    parity=name.startswith("parity-")
    world="optimization-"+name if parity else "optimization-proof"
    if parity:assert not (RUN/world).exists(),"fresh generation only"
    props.write_text(settings.replace("level-name=optimization-proof","level-name="+world),encoding="ascii")
    port_free(25580,socket.SOCK_STREAM);port_free(25581,socket.SOCK_DGRAM)
    mods=base+[adds[k] for k in keys]
    for p in mods:print("MOD",p.name,sha(p),flush=True)
    args=[str(java),"-Xms256m","-Xmx2048m","-XX:ActiveProcessorCount=4","-Dfabric.gameJarPath="+str(mc),"-Dfabric.gameMappingNamespace=intermediary","-Dfabric.modsFolder="+str(empty),"-Dfabric.addMods="+os.pathsep.join(map(str,mods)),"-cp",cp,"net.fabricmc.loader.impl.launch.knot.KnotServer","nogui"]
    log=BASE/("acceptance_"+name+".log")
    assert not log.exists(),"keep previous evidence"
    lines=[];started=time.monotonic()
    with log.open("x",encoding="utf-8") as stream:
        p=subprocess.Popen(args,cwd=RUN,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding="utf-8",errors="replace",bufsize=1)
        def reader():
            for l in p.stdout:lines.append(l);stream.write(l);stream.flush()
        t=threading.Thread(target=reader,daemon=True);t.start()
        def wait(marker,secs=30,off=0):
            until=time.monotonic()+secs
            while time.monotonic()<until:
                for l in lines[off:]:
                    if marker.casefold() in l.casefold():return l
                if p.poll() is not None:break
                time.sleep(.2)
            raise RuntimeError("missing "+marker+"\n"+"".join(lines[-25:]))
        def send(cmd):
            p.stdin.write(cmd+"\n");p.stdin.flush()
        try:
            wait("Done (",150);wait("Kapisma hazir",30)
            print("PASS",name,"boot",round(time.monotonic()-started,2),"s",flush=True)
            for k in keys:wait(manifest[k]["metadata"]["id"],5)
            if parity:
                # Identical remote 3x3 chunk area, newly generated in each world.
                if name.startswith("parity-frozen-"):
                    off=len(lines);send("gamerule randomTickSpeed 0");send("gamerule doMobSpawning false");send("tick freeze");wait("frozen",10,off)
                    print("PASS",name,"world tick frozen before remote generation",flush=True)
                off=len(lines);start=time.monotonic()
                send("forceload add 1024 1024 1071 1071")
                wait("marked",30,off)
                end=time.monotonic()+60
                while time.monotonic()<end:
                    off=len(lines)
                    checks=" ".join(f"if loaded {x} 64 {z}" for x in (1024,1040,1056) for z in (1024,1040,1056))
                    send("execute "+checks+" run say OPT_TERRAIN_READY")
                    try:wait("[Server] OPT_TERRAIN_READY",2,off);break
                    except RuntimeError:
                        if p.poll() is not None:raise
                else:raise RuntimeError("terrain timeout")
                print("PASS",name,"nine fresh chunks loaded",round(time.monotonic()-start,2),"s",flush=True)
            else:
                for n in range(1,5):
                    off=len(lines);send(f"player optqa{n} spawn");wait(f"optqa{n} joined the game",30,off)
                off=len(lines);send("list");wait("There are 4 of a max of 8 players online",10,off)
                print("PASS",name,"four Carpet bots online",flush=True)
                for n in range(1,5):
                    send(f"gamemode creative optqa{n}")
                    send(f"execute in minecraft:overworld positioned {16+n*3} 0 -48 positioned over motion_blocking_no_leaves run tp optqa{n} ~ ~ ~ -90 0")
                time.sleep(1)
                def position(n):
                    off=len(lines);send(f"data get entity optqa{n} Pos")
                    l=wait(f"optqa{n} has the following entity data:",10,off)
                    match=re.search(r"data: \[([^]]+)\]",l)
                    if not match:raise RuntimeError(l)
                    return tuple(float(x.strip().rstrip("dD")) for x in match[1].split(","))
                initial=[position(n) for n in range(1,5)]
                for n in range(1,5):send(f"player optqa{n} move forward")
                time.sleep(12)
                for n in range(1,5):send(f"player optqa{n} stop")
                final=[position(n) for n in range(1,5)]
                movement=[round(math.dist(a,b),2) for a,b in zip(initial,final)]
                print("OBS movement",movement,flush=True)
                assert all(d>=1 for d in movement),movement
                print("PASS",name,"four movement observations",flush=True)
                off=len(lines);send("tick query");wait("Target tick rate",10,off);time.sleep(.5)
                for line in lines[off:]:print("OBS",line.strip(),flush=True)
                for n in range(1,5):
                    off=len(lines);send(f"player optqa{n} kill");wait(f"optqa{n} left the game",20,off)
            off=len(lines);send("save-all flush");wait("Saved the game",30,off)
            print("PASS",name,"save",flush=True)
        finally:
            if p.poll() is None:
                send("stop")
                try:p.wait(timeout=45)
                except subprocess.TimeoutExpired:p.kill();p.wait()
            t.join(5)
        assert p.returncode==0,p.returncode
        assert not any("Mixin apply failed" in l or "Exception in server tick loop" in l or "Failed to start the minecraft server" in l for l in lines)
        print("PASS",name,"clean shutdown",flush=True)
    assert {p:sha(p) for p in before}==before
    print("PASS",name,"protected jar hashes unchanged",flush=True)
with (LAB/".mctest.lock").open("a+b") as lock:
    lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
    mode=sys.argv[1] if len(sys.argv)>1 else "baseline"
    profiles={"parity-frozen-base-repeat":[],"parity-frozen-noisium":["noisium"],"parity-frozen-base":[],"parity-frozen-memory":["ferrite-core","lithium"],"parity-frozen-worldgen":["ferrite-core","lithium","noisium"],"parity-base":[],"parity-worldgen":["ferrite-core","lithium","noisium"],"baseline":[],"memory":["ferrite-core","lithium"],"worldgen":["ferrite-core","lithium","noisium"],"full":["ferrite-core","lithium","noisium","modernfix"]}
    run(mode,profiles[mode])