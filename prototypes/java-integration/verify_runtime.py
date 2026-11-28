"""Named Fabric dev boot, using existing dependencies and MOD-only output; no product jar."""
from pathlib import Path
import hashlib,json,os,socket,subprocess,threading,time,zipfile,msvcrt,sys,uuid,re
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1]
MOD=ROOT/"mod/kapisma";CACHE=MOD/".gradle/loom-cache"
G=Path.home()/".gradle/caches/modules-2/files-2.1"
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852/server"
WORLDGEN="--worldgen-proof" in sys.argv
if WORLDGEN:
    profile=os.environ.get("KAPISMA_QA_EXTERNAL","none")
    assert profile in ("none","noisium")
    RUN=BASE/"build"/("runtime-worldgen-d36-"+profile)
    assert not RUN.exists(),"preserve prior natural-world evidence"
else:RUN=BASE/"build/runtime-proof"
RUN.mkdir(parents=True,exist_ok=True)
assert RUN.resolve()==RUN and not (RUN/"kapisma_next.json").exists()
RES=BASE/"build/runtime-resources";RES.mkdir(exist_ok=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def fresh_log(path):
    if not path.exists():return path
    return path.with_name(path.stem+"-"+uuid.uuid4().hex[:12]+path.suffix)
def guard():
    paths=[ROOT/"server/mods/kapisma-0.1.0.jar",ROOT/"_yedek/devletler-rts-prototip/mods/kapisma-rts-rules.jar"]
    return {p:sha(p) for p in paths}
protected_before=guard()
print("PROTECTED_BEFORE",json.dumps({str(p.relative_to(ROOT)):h for p,h in protected_before.items()},sort_keys=True),flush=True)
PRODUCTION="--production" in sys.argv
VOICE="--voice" in sys.argv
RED="--voice-red" in sys.argv
VOICE=VOICE or RED
ECONOMY="--economy" in sys.argv
PACKET="--packet" in sys.argv
EQUIPMENT="--equipment" in sys.argv or PACKET
COMBAT="--combat" in sys.argv or ECONOMY or EQUIPMENT
CRASH_ARM="--crash-arm" in sys.argv
CRASH_RECOVER="--crash-recover" in sys.argv
CRASH=CRASH_ARM or CRASH_RECOVER
CRASH_NOSAVE="--no-save" in sys.argv
assert not CRASH_NOSAVE or CRASH
FLIGHT="--flight" in sys.argv
FLIGHT_PROOF="--flight-proof" in sys.argv
SIEGE="--siege" in sys.argv
CARAVAN_SECURITY="--caravan-security" in sys.argv
CARAVAN_NATIVE="--caravan-native" in sys.argv
CARAVAN_LOAD="--caravan-load" in sys.argv
CARAVAN_GUIDE="--caravan-guide" in sys.argv
CARAVAN_HORSE="--caravan-horse" in sys.argv
CARAVAN_VISUAL="--caravan-visual" in sys.argv
CARAVAN_REPEAT="--caravan-repeat" in sys.argv
CARAVAN_COMBAT="--caravan-combat" in sys.argv
CARAVAN_DECOR="--caravan-decor" in sys.argv
CARAVAN=WORLDGEN or "--caravan" in sys.argv or CARAVAN_SECURITY or CARAVAN_NATIVE or CARAVAN_LOAD or CARAVAN_GUIDE or CARAVAN_HORSE or CARAVAN_VISUAL or CARAVAN_REPEAT or CARAVAN_COMBAT or CARAVAN_DECOR
assert sum((WORLDGEN,CARAVAN_SECURITY,CARAVAN_NATIVE,CARAVAN_LOAD,CARAVAN_GUIDE,CARAVAN_HORSE,CARAVAN_VISUAL,CARAVAN_REPEAT,CARAVAN_COMBAT,CARAVAN_DECOR))<=1
assert not CARAVAN_DECOR or os.environ.get("KAPISMA_QA_EXTERNAL")=="decor"
assert not (CARAVAN_SECURITY and CARAVAN_NATIVE)
NATIVE_LOAD="--native-load" in sys.argv
assert not FLIGHT_PROOF or not any((PRODUCTION,VOICE,CRASH,FLIGHT,NATIVE_LOAD,COMBAT,PACKET,SIEGE))
assert not SIEGE or not any((PRODUCTION,VOICE,CRASH,FLIGHT,NATIVE_LOAD,COMBAT,PACKET,CARAVAN))
assert not CARAVAN or not any((PRODUCTION,VOICE,CRASH,FLIGHT,NATIVE_LOAD,COMBAT,PACKET,SIEGE,FLIGHT_PROOF))
MORE="--more" in sys.argv
PLAYERS="--players" in sys.argv or MORE or VOICE or FLIGHT
# Copy only existing resources (not the protected build or sources); overlay manifests are MOD-owned.
for root in (() if PRODUCTION else (MOD/"build/resources/main",BASE/"overlay-resources")):
    for p in root.rglob("*"):
        if p.is_file():
            dst=RES/p.relative_to(root);dst.parent.mkdir(parents=True,exist_ok=True)
            data=p.read_bytes()
            if p.name=="fabric.mod.json":
                data=data.replace(b"${version}",b"0.1.0-arena-qa")
                if SIEGE:
                    meta=json.loads(data);meta["entrypoints"]["main"].append("tr.kapisma.game.SiegeRuntimeProof");data=json.dumps(meta).encode()
                if CARAVAN:
                    meta=json.loads(data);meta["entrypoints"]["main"].append("tr.kapisma.game.CaravanRuntimeProof");data=json.dumps(meta).encode()
                if FLIGHT_PROOF:
                    meta=json.loads(data);meta["entrypoints"]["main"].append("tr.kapisma.game.FlightRuntimeProof");data=json.dumps(meta).encode()
                if EQUIPMENT:
                    meta=json.loads(data);meta["entrypoints"]["main"].append("tr.kapisma.game.EquipmentRuntimeProof");data=json.dumps(meta).encode()
                if VOICE:
                    meta=json.loads(data);meta["entrypoints"]["main"].append("tr.kapisma.voice.VoiceRuntimeProof");meta["entrypoints"]["voicechat"].append("tr.kapisma.voice.VoiceRuntimeProof");data=json.dumps(meta).encode()
            dst.write_bytes(data)
def dependency(group,name,version):
    paths=[p for p in (G/group/name/version).rglob("*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar"))]
    if not paths:raise FileNotFoundError(f"{group}:{name}:{version}")
    return sorted(paths)[0]
def choose(group,name,prefer=None):
    root=G/group/name
    versions=sorted((p.name for p in root.iterdir() if p.is_dir()),reverse=True)
    if prefer and prefer in versions:versions=[prefer]+[v for v in versions if v!=prefer]
    for v in versions:
        try:return dependency(group,name,v)
        except FileNotFoundError:continue
    raise FileNotFoundError(str(root))
mc=(LAB/".fabric/remappedJars/minecraft-1.21.1-0.20.1/server-intermediary.jar" if PRODUCTION else next(CACHE.glob("minecraftMaven/**/minecraft-merged-*.jar")))
loader=choose("net.fabricmc","fabric-loader","0.20.1")
mods=[] if PRODUCTION else [p for p in sorted((CACHE/"remapped_mods").rglob("*.jar")) if not p.name.endswith(("-sources.jar","-javadoc.jar"))]
# Use official dedicated server library coordinates, not arbitrary versions.
vanilla=Path.home()/".gradle/caches/fabric-loom/1.21.1/minecraft-server.jar"
libs=[]
with zipfile.ZipFile(vanilla) as z:
    for line in z.read("META-INF/libraries.list").decode().splitlines():
        digest,coord,rel=line.split("\t")
        group,name,version,*classifier=coord.split(":")
        p=ROOT/"_yedek/devletler-rts-prototip/libraries"/rel
        if not p.is_file():
            candidates=list((G/group/name/version).rglob(Path(rel).name))
            if not candidates:raise FileNotFoundError(coord)
            p=candidates[0]
        if sha(p)!=digest:raise ValueError("library hash "+coord)
        libs.append(p)
for group,name in [("net.fabricmc","sponge-mixin"),("net.fabricmc","access-widener"),("net.fabricmc","tiny-remapper"),
    *[("org.ow2.asm",x) for x in ("asm","asm-analysis","asm-commons","asm-tree","asm-util")]]:
    try:libs.append(choose(group,name))
    except FileNotFoundError:
        if name not in ("tiny-remapper","access-widener"):raise
classes=BASE/"build/classes";original=MOD/"build/classes/java/main"
# Explicitly group resource directory and both class directories into one dev mod.
group=os.pathsep.join(map(str,[classes,original,RES]))
cp=os.pathsep.join(map(str,[classes,original,RES,mc,loader,*mods,*libs]))
props=RUN/"server.properties"
if not props.exists():
    props.write_text("server-ip=127.0.0.1\nserver-port=25580\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=arena-dev-proof\nlevel-seed=42\nlevel-type="+("minecraft:normal" if WORLDGEN else "minecraft:flat")+"\ngenerate-structures="+str(WORLDGEN).lower()+"\nview-distance=3\nsimulation-distance=3\nmax-players=8\nenable-rcon=false\nenable-query=false\n",encoding="ascii")
existing=props.read_text()
assert "server-ip=127.0.0.1" in existing and ("server-port=25578" in existing or "server-port=25580" in existing)
if "server-port=25578" in existing:
    props.write_text(existing.replace("server-port=25578","server-port=25580"),encoding="utf-8")
assert "server-port=25580" in props.read_text()
assert "eula=true" in (ROOT/"server/eula.txt").read_text()
(RUN/"eula.txt").write_text("eula=true\n",encoding="ascii")
assert not (RUN/"mods").exists() or not list((RUN/"mods").iterdir())
java=Path(os.environ["JAVA_HOME"])/"bin/java.exe"
def q(x):return '"'+str(x).replace("\\","/")+'"'
mapping=next((Path.home()/".gradle/caches/fabric-loom/1.21.1").glob("loom.mappings.*/mappings.tiny"))
extra=[]
if VOICE:
    analog=LAB/"mods/analog-1.2.2+1.21.jar";svc=LAB/"mods/voicechat-fabric-1.21.1-2.6.27.jar"
    assert sha(analog)=="0faeef984050724dac008e6a84a00c71e83f41419c9f151d1325e2804a99e91d"
    assert sha(svc)=="bae2c4fa9f7764abac621ee70e1099bd39b7316cbc636280d188045b07f17ab1"
    extra=[q("-Dfabric.addMods="+os.pathsep.join(map(str,[analog,svc]))),"-Dfabric.defaultModDistributionNamespace=intermediary"]
    intermediary=next((Path.home()/".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary").rglob("minecraft-merged-intermediary-*.jar"))
    remap=BASE/"build/remap-classpath.txt";remap.write_text(os.pathsep.join(map(str,[intermediary,*libs,loader])),encoding="utf-8")
    extra.append(q("-Dfabric.remapClasspathFile="+str(remap)))
    polymer=RUN/"config/polymer/auto-host.json";polymer.parent.mkdir(parents=True,exist_ok=True)
    if polymer.exists():
        data=json.loads(polymer.read_text(encoding="utf-8"));data["enabled"]=False;polymer.write_text(json.dumps(data,indent=2),encoding="utf-8")
    else:polymer.write_text(json.dumps({"enabled":False}),encoding="utf-8")
    vc=RUN/"config/voicechat";vc.mkdir(parents=True,exist_ok=True)
    (vc/"voicechat-server.properties").write_text("port=25581\nbind_address=127.0.0.1\nvoice_host=127.0.0.1:25581\n",encoding="ascii")
if COMBAT or CRASH or FLIGHT_PROOF or SIEGE or CARAVAN:
    carpet=ROOT/"_yedek/devletler-rts-prototip/laboratuvar/server/mods/fabric-carpet-1.21-1.4.147+v240613.jar"
    assert sha(carpet)=="079fc8a4e073eb24b010ffcc588e59f93b984143e17d8ef19fbb0b6aff0f19d9"
    intermediary=next((Path.home()/".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary").rglob("minecraft-merged-intermediary-*.jar"))
    remap=BASE/"build/remap-classpath.txt";remap.write_text(os.pathsep.join(map(str,[intermediary,*libs,loader])),encoding="utf-8")
    external=os.environ.get("KAPISMA_QA_EXTERNAL","none")
    assert external in ("none","lithium","ferrite-core","combined","decor","modernfix","noisium","optimizers4","servercore","optimizers5","content37"),"explicit candidate selection only"
    selected=[]
    if external!="none":
        assert CARAVAN and not PRODUCTION,"only MOD local test environment"
        folder=BASE.parent/"kervan-baskini"/"independent-cargo-candidate"
        if external in ("decor","content37"):
            entries=[
                ("polydecorations-0.4.5+1.21.1.jar",1322882,"7cc181752df0033364efe9e7fc6ce24af7497ef140838023b3135b2288045da56311e68518d320fbb2f4d2eca317db75d7966ea8228bfdf77c2cf6bdae1dfe8b"),
                ("polymer-bundled-0.9.19+1.21.1.jar",1705944,"6a804059be62ed312f6f3c8ef31fbda5889d2a0d088891966bf5a9ae18756198617b7fede322f596b1c98c0068638e28d9b6099037e00eca32a864f3441354cf")]
            if external=="content37":
                # Keep PolyDecorations out of this first compatibility screen.
                entries=entries[1:]
                content=json.loads((folder/"D37_CONTENT_CANDIDATES.json").read_text(encoding="utf-8"))["mods"]
                assert {v["slug"] for v in content}=={"decorative-boxes","tsa-decorations","filament"} and len(content)==3
                entries.extend((v["name"],v["size"],v["sha512"]) for v in content)
            config=RUN/"config/polymer/auto-host.json";config.parent.mkdir(parents=True,exist_ok=True)
            data=json.loads(config.read_text(encoding="utf-8")) if config.exists() else {}
            data["enabled"]=False
            config.write_text(json.dumps(data,indent=2),encoding="utf-8")
        else:
            mods=[]
            for manifest in ("D27_OPTIMIZER_CANDIDATES.json","D33_OPTIMIZER_CANDIDATES.json"):
                mods.extend(json.loads((folder/manifest).read_text(encoding="utf-8"))["mods"])
            wanted={"lithium","ferrite-core","modernfix","noisium","servercore"} if external=="optimizers5" else {"lithium","ferrite-core","modernfix","noisium"} if external=="optimizers4" else {"lithium","ferrite-core"} if external=="combined" else {external}
            entries=[(v["name"],v["size"],v["sha512"]) for v in mods if v["slug"] in wanted]
            assert len(entries)==len(wanted)
            if external in ("servercore","optimizers5"):
                # Explicit conservative candidate config; do not overwrite unrelated prior config.
                config=RUN/"config/servercore"
                config.mkdir(parents=True,exist_ok=True)
                for name,source in (("config.yml","D34_SERVERCORE_CONFIG.yml"),("optimizations.yml","D34_SERVERCORE_OPTIMIZATIONS.yml")):
                    dest=config/name
                    data=(folder/source).read_bytes()
                    if dest.exists():
                        generated_ok=name=="config.yml" and hashlib.sha256(dest.read_bytes()).hexdigest()=="038d93612d468c03564ae5d60e37d9a654ba3f1c1dc65928c832d852b8631690"
                        assert dest.read_bytes()==data or generated_ok,"existing ServerCore config needs separate review"
                    else:dest.write_bytes(data)
        for name,size,digest in entries:
            assert Path(name).name==name
            p=folder/"external-mod-candidates"/name
            assert p.stat().st_size==size and hashlib.sha512(p.read_bytes()).hexdigest()==digest
            selected.append(p)
    print("QA_EXTERNAL_PROFILE",external,[p.name for p in selected],flush=True)
    extra=[q("-Dfabric.addMods="+os.pathsep.join(map(str,[carpet,*selected]))),"-Dfabric.defaultModDistributionNamespace=intermediary",q("-Dfabric.remapClasspathFile="+str(remap))]
arg=BASE/"build/runtime.args"
if PRODUCTION:
    assert not CRASH and not NATIVE_LOAD,"use named recovery/load tests or explicit production test plan"
    candidate=BASE/"build/kapisma-intermediary-qa.jar"
    manifest=json.loads((BASE/"build/intermediary-qa-manifest.json").read_text())
    assert sha(candidate)==manifest["candidate_sha256"]=="42ae1104a059f0ccf0dd2ae12657488d5e6248817e2922231359099b678ae2e2"
    # A stale base must not reach JVM boot again; audit actual candidate bytes first.
    subprocess.run([sys.executable,"-B",str(BASE/"audit_intermediary_linkage.py")],check=True)
    prodmods=[candidate,LAB/"mods/fabric-api-0.116.18+1.21.1.jar",LAB/"mods/voicechat-fabric-1.21.1-2.6.27.jar",LAB/"mods/analog-1.2.2+1.21.jar"]
    if COMBAT:prodmods.append(carpet)
    assert all(p.is_file() for p in prodmods)
    # Production namespace, no dev mode, no named classes/classpath groups. Origins deliberately omitted for stock native test clients.
    cp=os.pathsep.join(map(str,[mc,*sorted((LAB/"libraries").rglob("*.jar"))]))
    extra=[q("-Dfabric.addMods="+os.pathsep.join(map(str,prodmods))),"-Dfabric.defaultModDistributionNamespace=intermediary"]
    vc=RUN/"config/voicechat";vc.mkdir(parents=True,exist_ok=True)
    (vc/"voicechat-server.properties").write_text("port=25581\nbind_address=127.0.0.1\nvoice_host=127.0.0.1:25581\n",encoding="ascii")
    print("PRODUCTION QA candidate",sha(candidate),flush=True)
arg.write_text("\n".join(["-Xms256m","-Xmx2048m","-XX:ActiveProcessorCount=4",
    q("-Dfabric.gameJarPath="+str(mc)),*(["-Dfabric.gameMappingNamespace=intermediary"] if PRODUCTION else [q("-Dfabric.classPathGroups="+group),"-Dfabric.development=true",q("-Dfabric.mappingPath="+str(mapping)),"-Dfabric.runtimeMappingNamespace=named","-Dfabric.gameMappingNamespace=named"]),
    "-Dkapisma.experimentalArena=true","-Dkapisma.arenaProof=true","-Dkapisma.experimentalCaravan2="+str(CARAVAN).lower(),"-Dkapisma.experimentalSiegeEconomy="+str(SIEGE).lower(),"-Dkapisma.experimentalAnalogVoice="+("true" if (VOICE or PRODUCTION) and not RED else "false"),"-Dkapisma.profile=lite",*extra,
    "-cp",q(cp),"net.fabricmc.loader.impl.launch.knot.KnotServer","nogui"]),encoding="utf-8")
def free(port):
    with socket.socket() as s:s.bind(("127.0.0.1",port))
class Client:
    def __init__(self,n):
        self.name=f"ArenaDevQA{n}"
        mc=ROOT.parent
        version=mc/"versions/Fabric 1.21.1"
        client=version/"Fabric 1.21.1.jar"
        assert hashlib.sha1(client.read_bytes()).hexdigest()=="30c73b1c5da787909b2f73340419fdf13b9def88"
        meta=json.loads((version/"Fabric 1.21.1.json").read_text(encoding="utf-8-sig"))
        clientlibs=LAB/"libraries"
        paths=[]
        for library in meta["libraries"]:
            rules=library.get("rules",[])
            if rules and not any(r.get("action")=="allow" and r.get("os",{}).get("name","windows")=="windows" for r in rules): continue
            group,artifact,number,*classifier=library["name"].split(":")
            if group=="net.fabricmc" and artifact in ("fabric-loader","sponge-mixin"):
                number={"fabric-loader":"0.20.1","sponge-mixin":"0.17.5+mixin.0.8.7"}[artifact]
                paths.append(clientlibs/Path(group.replace(".","/"))/artifact/number/(artifact+"-"+number+".jar"))
            else:
                entry=library.get("downloads",{}).get("artifact",{}).get("path","/".join((group.replace(".","/"),artifact,number,artifact+"-"+number+("-"+classifier[0] if classifier else "")+".jar")))
                if group=="org.ow2.asm":
                    candidate=clientlibs/Path(group.replace(".","/"))/artifact/"9.9.1"/(artifact+"-9.9.1.jar")
                    if candidate.is_file(): paths.append(candidate);continue
                paths.append(mc/"libraries"/entry)
        assert all(p.is_file() for p in paths)
        game=LAB.parent/(("kirik-telsiz-voice-client-" if VOICE else "donen-karakol-client-")+str(n))
        game.mkdir(exist_ok=True)
        (game/"mods").mkdir(exist_ok=True);assert not list((game/"mods").iterdir())
        (game/"natives").mkdir(exist_ok=True)
        (game/"options.txt").write_text("version:3955\nonboardAccessibility:false\ntutorialStep:none\nrawMouseInput:false\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:30\nenableVsync:true\noverrideWidth:640\noverrideHeight:480\nguiScale:2\n",encoding="ascii")
        account=uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+self.name).encode()).digest(),version=3)
        args=[str(java),"-Xmx1500M","-XX:TieredStopAtLevel=1","-Dfile.encoding=UTF-8","-Djava.library.path="+str(game/"natives"),"-cp",";".join(map(str,paths+[client])),"net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username",self.name,"--version","ARENA-QA","--gameDir",str(game),"--assetsDir",str(mc/"assets"),"--assetIndex","17","--uuid",account.hex,"--accessToken","0","--userType","legacy","--versionType","release","--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer","127.0.0.1:25580","--width","640","--height","480"]
        if PACKET:
            qa=BASE/"packetqa/packetqa.jar"
            assert sha(qa)=="b3f161279de0c7a4685c8f60db89653a4cce0e0dddb96ea61f4a77ed78dc4c18"
            self.packet_trigger=BASE/("build/packetqa-production-trigger.txt" if PRODUCTION else "build/packetqa-trigger.txt")
            if self.packet_trigger.exists():self.packet_trigger=self.packet_trigger.with_name(self.packet_trigger.stem+"-"+uuid.uuid4().hex[:12]+self.packet_trigger.suffix)
            args[1:1]=["-Dkapisma.packetqa.trigger="+str(self.packet_trigger),"-Dfabric.addMods="+os.pathsep.join(map(str,[LAB/"mods/fabric-api-0.116.18+1.21.1.jar",qa]))]
        if FLIGHT:
            qa=ROOT/"prototypes/alcak-irtifa/flightqa/flightqa.jar"
            assert sha(qa)=="3202b96cea44313e89a465190377c4d8eea4aff72d110f0fce806a0afb671122"
            args[1:1]=["-Dai.qa.enabled=true","-Dai.qa.autoflight=true","-Dfabric.addMods="+os.pathsep.join(map(str,[LAB/"mods/fabric-api-0.116.18+1.21.1.jar",qa]))]
        if VOICE:
            vc=game/"config/voicechat";vc.mkdir(parents=True,exist_ok=True)
            (vc/"voicechat-client.properties").write_text("config_version=1\nmuted=true\nmicrophone_activation_type=PTT\nonboarding_finished=true\nvoice_chat_volume=0.0\n",encoding="ascii")
            qa=ROOT/"prototypes/kirik-telsiz/voiceqa/kt-voiceqa.jar"
            assert sha(qa)=="68cec37e0814ecbbd44cbfaf89627eceb6bfede60751ce75ec9c8ffc65c4f358"
            args[1:1]=["-Dkt.voiceqa.mode=client","-Dfabric.addMods="+os.pathsep.join(map(str,[LAB/"mods/fabric-api-0.116.18+1.21.1.jar",svc,qa]))]
        self.path=BASE/(("production-" if PRODUCTION else "")+("packet-" if PACKET else "flight-" if FLIGHT else "voice-red-" if RED else "voice-" if VOICE else "more-" if MORE else "")+"native-arena-client-"+str(n)+".log")
        self.path=fresh_log(self.path)
        with self.path.open("xb") as out:
            self.p=subprocess.Popen(args,cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    def pcm(self):
        return self.path.read_text(encoding="utf-8",errors="replace").count("KTQA_PCM ")
    def wait_voice(self):
        end=time.monotonic()+60
        while time.monotonic()<end:
            if "KTQA_VOICE_CONNECTED true" in self.path.read_text(encoding="utf-8",errors="replace"):return
            assert self.p.poll() is None,"Voice client exited"
            time.sleep(.4)
        raise RuntimeError("Voice handshake missing "+self.name)
    def chats(self):
        return [line for line in self.path.read_text(encoding="utf-8",errors="replace").splitlines() if "[CHAT]" in line]
    def chat_count(self,needle):
        return sum(needle in line for line in self.chats())
    def wait_chat(self,needle,count=1):
        end=time.monotonic()+40
        while time.monotonic()<end:
            if self.chat_count(needle)>=count:return
            assert self.p.poll() is None, "Native client exited"
            time.sleep(.4)
        raise RuntimeError("Missing client chat "+self.name+" "+needle)
    def assert_no_chat(self,needle):
        assert self.chat_count(needle)==0,(self.name,needle)
    def close(self):
        if self.p.poll() is None:
            self.p.terminate();self.p.wait(timeout=15)

with (LAB/".mctest.lock").open("a+b") as lock:
    lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
    free(25580)
    if VOICE or PRODUCTION:
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as udp:udp.bind(("127.0.0.1",25581))
    log_name=(("acceptance_runtime_crash_arm_nosave.log" if CRASH_NOSAVE else "acceptance_runtime_crash_arm.log") if CRASH_ARM else ("acceptance_runtime_crash_recover_nosave.log" if CRASH_NOSAVE else "acceptance_runtime_crash_recover.log") if CRASH_RECOVER else "acceptance_runtime_native_load_v2.log" if NATIVE_LOAD else "acceptance_runtime_caravan.log" if CARAVAN else "acceptance_runtime_siege.log" if SIEGE else "acceptance_runtime_flight_proof.log" if FLIGHT_PROOF else "acceptance_runtime_flight.log" if FLIGHT else "acceptance_runtime_packet.log" if PACKET else "acceptance_runtime_equipment_v2.log" if EQUIPMENT else "acceptance_runtime_economy_v2.log" if ECONOMY else "acceptance_runtime_combat.log" if COMBAT else "acceptance_runtime_voice_red.log" if RED else "acceptance_runtime_voice.log" if VOICE else "acceptance_runtime_more.log" if MORE else "acceptance_runtime_players.log" if PLAYERS else "acceptance_runtime.log")
    log_path=fresh_log(BASE/(("production_" if PRODUCTION else "")+log_name))
    print("QA_LOG",log_path,flush=True)
    with log_path.open("x",encoding="utf-8") as log:
        boot_start=time.monotonic()
        process=subprocess.Popen([str(java),"@"+str(arg)],cwd=RUN,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding="utf-8",errors="replace",bufsize=1)
        lines=[]
        def read():
            for line in process.stdout:lines.append(line);log.write(line);log.flush()
        reader=threading.Thread(target=read,daemon=True);reader.start()
        def wait(marker,secs=90,start=0):
            until=time.monotonic()+secs
            while time.monotonic()<until:
                if any(marker in line for line in lines[start:]):return next(line for line in lines[start:] if marker in line)
                if process.poll() is not None:break
                time.sleep(.2)
            raise RuntimeError(f"missing {marker}, exit={process.poll()}\n"+"".join(lines[-40:]))
        def send(cmd):process.stdin.write(cmd+"\n");process.stdin.flush()
        clients=[]
        def status(marker,secs=120):
            until=time.monotonic()+secs
            while time.monotonic()<until:
                offset=len(lines);send("kapisma status")
                try:line=wait(" | Oyun: ",5,offset)
                except RuntimeError:raise
                if marker in line:return line
                time.sleep(.5)
            raise RuntimeError("status not reached: "+marker+"\n"+"".join(lines[-30:]))
        def check(cmd,marker):
            offset=len(lines);send(cmd+" run say "+marker);wait(marker,8,offset)
            print("PASS "+marker,flush=True)
        try:
            wait("Done (",120);wait("Kapisma hazir",30)
            print("PASS "+("intermediary production" if PRODUCTION else "named")+" Fabric boot and Kapisma initialization",flush=True)
            send("kapisma game DONEN_KARAKOL");send("kapisma start")
            wait("Oyuncu yok.",15)
            print("PASS game command accepted; no-player start rejected",flush=True)
            send("kapisma game KERVAN_BASKINI");send("kapisma status")
            wait("KERVAN_BASKINI",15)
            print("PASS native command registry smoke",flush=True)
            if CRASH:
                ledger=RUN/"kapisma-arena-return.v1"
                evidence=BASE/("crash-proof-nosave.json" if CRASH_NOSAVE else "crash-proof.json")
                bots=[("arenanoflushqa" if CRASH_NOSAVE else "arenacrashqa")+str(i) for i in range(1,5)]
                ids={n:str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+n).encode()).digest(),version=3)) for n in bots}
                def entries():
                    rows=ledger.read_text(encoding="utf-8").splitlines()
                    assert rows[0]=="KAPISMA_ARENA_RETURN_V1"
                    return {row.split("|")[0]:row for row in rows[1:]}
                def join(n):
                    off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",40,off)
                    until=time.monotonic()+20
                    while time.monotonic()<until:
                        off=len(lines);send("execute if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star run say CRASH_READY_"+n)
                        try:wait("[Server] CRASH_READY_"+n,2,off);return
                        except RuntimeError:
                            if process.poll() is not None:raise
                    raise RuntimeError("lobby readiness missing "+n)
                def proof(pred,secs=15):
                    until=time.monotonic()+secs
                    while time.monotonic()<until:
                        off=len(lines);send("arena proof");line=wait("ARENA_PROOF ",5,off)
                        if pred(line):return line.strip()
                        time.sleep(.25)
                    raise RuntimeError("state not reached "+line)
                if CRASH_ARM:
                    assert not entries() and not evidence.exists(),"prior crash proof must be preserved"
                    for i,n in enumerate(bots):
                        join(n)
                        off=len(lines);send("execute in minecraft:overworld run spawnpoint "+n+" "+str(100+i)+" -60 "+str(200+i)+" 45")
                        wait("Set spawn point",10,off)
                    player_files={n:RUN/"arena-dev-proof/playerdata"/(ids[n]+".dat") for n in bots}
                    before_player_files={}
                    if CRASH_NOSAVE:
                        off=len(lines);send("save-all flush");wait("Saved the game",30,off)
                        before_player_files={n:sha(p) for n,p in player_files.items()}
                    send("kapisma game DONEN_KARAKOL");send("kapisma start")
                    status("DONEN_KARAKOL RUNNING",150)
                    print("PASS CRASH_FOUR_REGISTERED_ACTIVE",flush=True)
                    allrows=entries()
                    assert set(allrows)==set(ids.values()) and len({row.split("|")[1] for row in allrows.values()})==1
                    for i,n in enumerate(bots):
                        row=allrows[ids[n]].split("|")
                        assert row[2:6]==["minecraft:overworld",str(100+i),"-60",str(200+i)],row
                        check("execute if items entity "+n+" hotbar.0 minecraft:iron_sword","CRASH_ARENA_KIT_"+n)
                    print("PASS CRASH_FOUR_DURABLE_RETURNS_BEFORE_TERMINATION",flush=True)
                    # Observe natural progress before interruption; no fabricated score.
                    off=len(lines);send("team list kp_arena_0");row=wait("Kırmızı",5,off)
                    red=[n for n in bots if n in row];blue=[n for n in bots if n not in red]
                    assert len(red)==len(blue)==2
                    for i,n in enumerate(red):send("execute as "+n+" at @s run tp @s -23.5 305 "+str(.5+i))
                    for i,n in enumerate(blue):send("execute as "+n+" at @s run tp @s "+str(.5+i)+" 305 11.5")
                    observed=proof(lambda l:bool(re.search(r"red=[1-9]",l)),15)
                    print("PASS CRASH_OBSERVED_LIVE_SCORE",observed,flush=True)
                    if CRASH_NOSAVE:
                        # No explicit save since lobby baseline; record whether vanilla auto-saved.
                        after_player_files={n:sha(p) for n,p in player_files.items()}
                        unchanged=sum(before_player_files[n]==after_player_files[n] for n in bots)
                        print("OBS CRASH_NOSAVE_PLAYER_FILES_UNCHANGED",unchanged,"of",len(bots),flush=True)
                        assert unchanged==len(bots),"cannot claim unsaved player-data case"
                        print("PASS CRASH_NOSAVE_PRE_MATCH_PLAYER_FILES",flush=True)
                    else:
                        off=len(lines);send("save-all flush");wait("Saved the game",30,off)
                    # Hash and preserve the committed return ledger; terminate ONLY our Popen child.
                    evidence.write_text(json.dumps({"pid":process.pid,"entries":allrows,"state":observed,"journal_sha256":sha(ledger)},indent=2),encoding="utf-8")
                    assert process.poll() is None
                    process.kill();process.wait(timeout=20)
                    assert process.returncode!=0
                    print("PASS CRASH_OWN_TEST_JAVA_PROCESS_TERMINATED",process.returncode,flush=True)
                    assert sha(ledger)==json.loads(evidence.read_text())["journal_sha256"]
                    print("PASS CRASH_JOURNAL_SURVIVED",flush=True)
                else:
                    prior=json.loads(evidence.read_text(encoding="utf-8"))
                    assert entries()==prior["entries"] and sha(ledger)==prior["journal_sha256"]
                    proof(lambda l:"ARENA_PROOF none" in l)
                    print("PASS CRASH_OLD_MATCH_NOT_RESUMED_UNSAFELY",flush=True)
                    for i,n in enumerate(bots):
                        join(n)
                        until=time.monotonic()+15
                        while time.monotonic()<until:
                            if ids[n] not in entries():break
                            time.sleep(.2)
                        else:raise RuntimeError("return remains "+n)
                        # Exact respawn data, lobby kit, no sword. All still-offline records preserved.
                        for key,value in (("SpawnX",100+i),("SpawnY",-60),("SpawnZ",200+i)):
                            off=len(lines);send("data get entity "+n+" "+key)
                            l=wait(n+" has the following entity data:",5,off)
                            assert re.search(r"data: "+str(value)+r"\s*$",l),l
                        check("execute if items entity "+n+" hotbar.4 minecraft:nether_star unless items entity "+n+" hotbar.0 minecraft:iron_sword","CRASH_LOBBY_KIT_"+n)
                        expected={ids[x]:prior["entries"][ids[x]] for x in bots[i+1:]}
                        assert entries()==expected,(entries(),expected)
                        print("PASS CRASH_EXACT_RESPAWN_AND_OFFLINE_RETENTION_"+n,flush=True)
                    assert not entries()
                    send("kapisma game YASAYAN_HARITA");send("kapisma start")
                    status("YASAYAN_HARITA RUNNING",150)
                    assert set(entries())==set(ids.values()) and all(row.split("|")[1] not in {v.split("|")[1] for v in prior["entries"].values()} for row in entries().values())
                    print("PASS CRASH_NEW_MATCH_DISTINCT_EPOCH",flush=True)
                    send("kapisma stop");status("yok (lobi)",60);assert not entries()
                    print("PASS CRASH_NEW_MATCH_CLEAN_RETURN",flush=True)
                    for n in bots:
                        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
            elif NATIVE_LOAD:
                from qa_clients import NativeClient
                # Four independent actual client JVMs, one small existing server proof profile.
                # Explicitly synthetic profiles, no real launcher account or external address.
                case=BASE/"build/native-load-clients"
                case.mkdir(exist_ok=True)
                for n in range(1,5):
                    c=NativeClient(name="ModNativeQA"+str(n),port=25580,game=case/str(n),
                        log=BASE/("native-load-v2-client-"+str(n)+".log"),walk=True,yaw=-90,ticks=220)
                    clients.append(c)
                    off=len(lines);wait(c.name+" joined the game",90,off)
                    time.sleep(1)
                print("PASS FOUR_INDEPENDENT_NATIVE_CLIENT_JVMS",flush=True)
                assert len({c.p.pid for c in clients})==4 and all(c.p.poll() is None for c in clients)
                # Actual arena, away from lobby enforcement, isolated flat lanes.
                send("kapisma game YASAYAN_HARITA");send("kapisma start")
                status("YASAYAN_HARITA RUNNING",150)
                for i,c in enumerate(clients):
                    send("execute as "+c.name+" at @s run tp @s -30.5 301 "+str([-25.5,-23.5,23.5,25.5][i])+" -90 0")
                time.sleep(2)
                def pos(c):
                    off=len(lines);send("data get entity "+c.name+" Pos")
                    l=wait(c.name+" has the following entity data:",8,off)
                    m=re.search(r"data: \[([^]]+)\]",l)
                    assert m,l
                    return tuple(float(s.strip().rstrip("dD")) for s in m[1].split(","))
                before_walk=[pos(c) for c in clients]
                assert all(abs(p[0]+30.5)<.5 and abs(p[1]-301)<.5 for p in before_walk),before_walk
                for c in clients:c.start_walk()
                observations=[before_walk]
                for _ in range(4):
                    time.sleep(2)
                    observations.append([pos(c) for c in clients])
                for i,c in enumerate(clients):
                    steps=[round(observations[k+1][i][0]-observations[k][i][0],2) for k in range(len(observations)-1)]
                    print("OBS",c.name,"native x advances",steps,"client samples",len(c.positions()),flush=True)
                    assert all(v>0 for v in steps) and sum(v>1 for v in steps)>=3,(c.name,steps)
                    assert len(c.positions())>=5,(c.name,c.positions())
                    print("PASS "+c.name+"_REPEATED_NATIVE_MOVEMENT",flush=True)
                print("PASS FOUR_NATIVE_CLIENT_MOVEMENT_INTERVALS",flush=True)
                send("kapisma stop");status("yok (lobi)",60)
                assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                print("PASS FOUR_NATIVE_CLIENT_RETURN",flush=True)
                for c in clients:
                    off=len(lines);c.close();wait(c.name+" left the game",25,off)
                print("PASS FOUR_NATIVE_CLIENT_DISCONNECT",flush=True)
            elif CARAVAN:
                if WORLDGEN:
                    from caravan_worldgen_acceptance import accept_worldgen
                    accept_worldgen(send,wait,lines,process,RUN,boot_start)
                elif CARAVAN_SECURITY:
                    from caravan_security_acceptance import accept_security
                    accept_security(send,wait,status,check,lines,process,RUN,clients)
                elif CARAVAN_NATIVE:
                    from caravan_native_acceptance import accept_native
                    accept_native(send,wait,status,check,lines,process,RUN,clients)
                elif CARAVAN_LOAD:
                    from caravan_load_acceptance import accept_load
                    accept_load(send,wait,status,check,lines,process,RUN)
                elif CARAVAN_GUIDE:
                    from caravan_guide_acceptance import accept_guide
                    accept_guide(send,wait,status,check,lines,process,RUN,clients)
                elif CARAVAN_HORSE:
                    from caravan_horse_acceptance import accept_horse
                    accept_horse(send,wait,status,check,lines,process,RUN,clients)
                elif CARAVAN_VISUAL:
                    from caravan_visual_acceptance import accept_visual
                    accept_visual(send,wait,status,check,lines,process,RUN,clients)
                elif CARAVAN_REPEAT:
                    from caravan_repeat_acceptance import accept_repeat
                    accept_repeat(send,wait,status,check,lines,process,RUN)
                elif CARAVAN_COMBAT:
                    from caravan_combat_acceptance import accept_combat
                    accept_combat(send,wait,status,check,lines,process,RUN)
                elif CARAVAN_DECOR:
                    from caravan_decor_acceptance import accept_decor
                    accept_decor(send,wait,status,check,lines,process,RUN,clients)
                else:
                    from caravan_runtime_acceptance import accept_caravan
                    accept_caravan(send,wait,status,check,lines,process,RUN)
            elif SIEGE:
                bots=["arenasiegeqa1","arenasiegeqa2","arenasiegeqa3"]
                for n in bots:
                    off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",40,off)
                time.sleep(2)
                check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"SIEGE_LOBBY")
                send("kapisma game INSA_ET_SAVUN");send("kapisma start");status("INSA_ET_SAVUN RUNNING",150)
                def proof():
                    off=len(lines);send("arena proof");return wait("ARENA_PROOF INSA_ET_SAVUN",5,off)
                def funds(n):
                    return int(re.search(r"credits=\[[^\]]*"+n+r":(\d+)",proof())[1])
                def team(index):
                    off=len(lines);send("team list kp_arena_"+str(index))
                    row=wait("Kırmızı" if index==0 else "Mavi",5,off)
                    return [n for n in bots if n in row]
                red,blue=team(0),team(1);assert sorted(map(len,[red,blue]))==[1,2],(red,blue)
                own=red if len(red)==2 else blue;enemy=blue if own==red else red
                a,b=own;c=enemy[0];sign=-1 if own==red else 1
                assert all(funds(n)==1000 for n in bots)
                check("execute as "+a+" at @s if block 0 305 0 minecraft:bedrock","SIEGE_BUILD_WALL")
                def cmd(n,action):send("execute as "+n+" run arena "+action);time.sleep(.2)
                def item(n,slot,id,count):
                    check(f"execute if items entity {n} {slot} minecraft:{id}[count={count}]","SIEGE_ITEM_"+n+"_"+str(count)+"_"+uuid.uuid4().hex[:6])
                def snap(n,op="snapshot"):
                    off=len(lines);send("arena-siege-proof "+n+" "+op);row=wait("SIEGEQA "+n+" ",5,off);time.sleep(.2);return row
                def expect(row,*fields):
                    assert all(x in row for x in fields),row
                cmd(a,"magaza");expect(snap(a),"gui=true","credits=1000","oak=0")
                expect(snap(a,"stale"),"credits=1000","oak=0")
                print("PASS stale shop container ID rejected",flush=True)
                expect(snap(a,"click 11 0"),"credits=1000")
                expect(snap(a,"click 0 0"),"credits=984","oak=8","ledger=8")
                expect(snap(a,"click 0 1"),"credits=1000","oak=0","ledger=0")
                expect(snap(a,"click 26 0"),"gui=false")
                print("PASS actual server packet GUI buy and refund",flush=True)
                cmd(a,"al 1 10");item(a,"hotbar.5","oak_planks",10)
                assert funds(a)==funds(b)==980 and funds(c)==1000
                print("PASS shared team purchase and independent enemy credits",flush=True)
                cmd(b,"al 5 20")
                assert funds(b)==980
                check("execute unless items entity "+b+" hotbar.5 minecraft:obsidian","SIEGE_OVERSPEND_DENIED")
                cmd(a,"sat 1 2");item(a,"hotbar.5","oak_planks",8)
                assert funds(a)==funds(b)==984
                cmd(b,"sat 1 2");assert funds(b)==984
                print("PASS full resale and unpaid stock denied",flush=True)
                x=sign*12;z=0
                def aim(n,px,py,pz,tx,ty,tz):
                    send(f"execute as {n} at @s run tp @s {px} {py} {pz} facing {tx} {ty} {tz}")
                def place():
                    send("player "+a+" hotbar 6")
                    send(f"execute as {a} at @s run tp @s {x+.5} 301 2.5 180 39")
                    send("player "+a+" use once");time.sleep(.6)
                    check(f"execute as {a} at @s if block {x} 301 0 minecraft:oak_planks","SIEGE_PLACED_"+uuid.uuid4().hex[:6])
                place();item(a,"hotbar.5","oak_planks",7)
                cmd(a,"sat 1 8");assert funds(a)==984
                print("PASS real block placement consumes paid stock; placed units cannot resell",flush=True)
                cmd(a,"kit 3");cmd(c,"kit 2")
                cmd(b,"al 1 37");expect(snap(b,"fragment"),"oak=37","ledger=37","slots=36","cursor=1")
                print("PASS deliberately fragmented paid inventory fixture",flush=True)
                until=time.monotonic()+85
                while time.monotonic()<until:
                    row=proof()
                    if "phase=BATTLE" in row and "opening=false" in row:break
                    time.sleep(.5)
                else:raise RuntimeError("battle transition timeout "+row)
                check("execute as "+a+" at @s if block 0 305 0 minecraft:air","SIEGE_WALL_OPENED")
                item(a,"hotbar.0","iron_pickaxe",1);item(a,"hotbar.1","bow",1);item(c,"hotbar.1","iron_sword",1)
                item(a,"hotbar.5","oak_planks",7)
                print("PASS selected kits granted without deleting paid materials",flush=True)
                expect(snap(b),"oak=37","ledger=37","cursor=0")
                print("PASS full fragmented inventory compacted without loss",flush=True)
                send(f"execute as {a} at @s run tp @s {sign*28+.5} 301 8 0 0")
                send(f"execute as {b} at @s run tp @s {sign*28+.5} 301 -8 0 0")
                send("player "+c+" hotbar 1");send(f"execute as {c} at @s run tp @s {x+.5} 301 2.5 180 29")
                send("player "+c+" attack continuous");time.sleep(8);send("player "+c+" stop")
                check(f"execute as {a} at @s if block {x} 301 0 minecraft:air","SIEGE_REAL_WALL_DESTROYED")
                send(f"execute as {c} at @s run tp @s {-sign*28+.5} 301 8 0 0")
                place();item(a,"hotbar.5","oak_planks",6)
                print("PASS real repair during simultaneous battle",flush=True)
                cmd(a,"al 2 3");assert funds(a)==funds(b)==901
                print("PASS battle shop remains enabled",flush=True)
                # Enemy core is a provisional 25-point hit target, no score from own target.
                cx=sign*24
                send(f"execute as {c} at @s run tp @s {cx+.5} 301 2.5 180 29")
                send("player "+c+" attack continuous");time.sleep(6);send("player "+c+" stop")
                row=proof();pointkey="redPoints" if sign==-1 else "bluePoints"
                assert 0<int(re.search(pointkey+r"=(\d+)",row)[1])<500,row
                print("PASS provisional real core hit reduces team points",row.strip(),flush=True)
                send("player "+c+" attack continuous")
                until=time.monotonic()+65
                while time.monotonic()<until:
                    row=proof()
                    if "ENDED" in row and pointkey+"=0" in row:break
                    time.sleep(.2)
                else:raise RuntimeError("natural siege end missing "+row)
                assert "winner="+("BLUE" if sign==-1 else "RED") in row,row
                send("player "+c+" stop");status("yok (lobi)",60)
                assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                print("PASS natural zero-point winner and all players returned",flush=True)
                for n in bots:
                    off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
            elif FLIGHT_PROOF:
                names=["arenaflightqa1","arenaflightqa2"]
                for n in names:
                    off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",40,off)
                time.sleep(2)
                check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in names),"FLIGHT_PROOF_LOBBY")
                send("kapisma game ALCAK_IRTIFA");send("kapisma start");status("ALCAK_IRTIFA RUNNING",150)
                a,b=names
                check("execute if items entity "+a+" hotbar.0 minecraft:firework_rocket[count=1] if items entity "+b+" hotbar.0 minecraft:firework_rocket[count=1]","FLIGHT_PROOF_ROCKET_KIT")
                def snapshot(n,op="snapshot"):
                    off=len(lines);send("arena-flight-proof "+n+" "+op);return wait("FLIGHTQA_STATE "+n+" "+op,6,off)
                def tp(n,x,y,z,yaw=-90,pitch=0):
                    send(f"execute as {n} at @s run tp @s {x} {y} {z} {yaw} {pitch}")
                def field(row,key):
                    return re.search(r"\b"+key+r"=([^ ]+)",row.strip())[1]
                def cooldown(n):
                    off=len(lines);send("arena proof");row=wait("ARENA_PROOF ALCAK_IRTIFA ",5,off)
                    return int(re.search(r"cooldowns=\[[^\]]*"+n+r":(\d+)",row)[1]),row
                def launch(n,x,y,z):
                    tp(n,x,y,z)
                    # Teleport can leave the previous onGround flag until a real entity tick.
                    # Wait for physical free fall before issuing vanilla startFallFlying.
                    until=time.monotonic()+2
                    while time.monotonic()<until:
                        row=snapshot(n)
                        if field(row,"ground")=="false":break
                        time.sleep(.05)
                    else:raise RuntimeError("not airborne "+row)
                    row=snapshot(n,"glide")
                    assert field(row,"glide")=="true" and field(row,"ground")=="false",row
                    time.sleep(.12);row=snapshot(n)
                    assert field(row,"glide")=="true",row
                    return row
                # Actual green platform collision (not a direct velocity call).
                off=len(lines);tp(a,-55.5,297.05,5.5)
                row=wait("FLIGHT_PAD "+a+" lift",12,off)
                assert "1.05" in row,row
                print("PASS lift pad real collision and vertical velocity",row.strip(),flush=True)
                # Controlled initial placement only; actual speed-ring collision during gliding.
                off=len(lines);launch(a,-43.0,292.5,10.0)
                row=wait("FLIGHT_PAD "+a+" speed",8,off)
                assert "before=" in row and "after=" in row,row
                def vector(s,key):return tuple(float(x) for x in re.search(key+r"=\(([^)]+)\)",s)[1].split(","))
                pre=vector(row,"before");post=vector(row,"after")
                assert post[0]-pre[0]>.5,(pre,post)
                print("PASS speed pad real collision and velocity gain",pre,post,flush=True)
                # Real vanilla ItemStack.use on a gliding server player.
                launch(a,-66,295,-15)
                off=len(lines);row=snapshot(a,"rocket");accepted=wait("FLIGHT_ROCKET "+a+" true",5,off)
                assert field(row,"count")=="1" and int(field(row,"rockets"))==1,(row,accepted)
                print("PASS actual rocket entity and unlimited stack",flush=True)
                off=len(lines);row=snapshot(a,"rocket");denied=wait("FLIGHT_ROCKET "+a+" false",5,off)
                assert field(row,"count")=="1" and int(field(row,"rockets"))==1,(row,denied)
                print("PASS early second rocket blocked without extra entity",flush=True)
                before,_=cooldown(a);assert before>0,before
                # Landing on the low catch floor triggers the real checkpoint recovery.
                tp(a,-60,251,-15);time.sleep(.6);row=snapshot(a)
                assert float(field(row,"y"))>=296 and float(field(row,"x"))<-49 and field(row,"rockets")=="0",row
                after,_=cooldown(a);assert 0<after<before,(before,after)
                print("PASS real checkpoint return discards rocket and preserves cooldown",before,after,flush=True)
                launch(a,-65,295,-15)
                off=len(lines);snapshot(a,"rocket");wait("FLIGHT_ROCKET "+a+" false",5,off)
                print("PASS checkpoint cannot bypass cooldown",flush=True)
                until=time.monotonic()+25
                while time.monotonic()<until:
                    left,state=cooldown(a)
                    if left==0:break
                    time.sleep(.35)
                else:raise RuntimeError("cooldown did not expire")
                launch(a,-65,295,-15)
                off=len(lines);row=snapshot(a,"rocket");wait("FLIGHT_ROCKET "+a+" true",5,off)
                assert field(row,"count")=="1" and int(field(row,"rockets"))==1,row
                print("PASS second rocket after natural 200 tick cooldown",flush=True)
                send("kapisma stop");status("yok (lobi)",60)
                assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                print("PASS flight proof returns both players",flush=True)
                for n in names:
                    off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
            elif COMBAT:
                bots=["ArenaDevQA1","arenapacketqa2"] if PACKET else [("arenaeconqa" if ECONOMY else "arenacombatqa")+str(n) for n in range(1,4 if ECONOMY else 5)]
                for n in bots:
                    off=len(lines)
                    if PACKET and n==bots[0]:
                        native=Client(1);clients.append(native);wait(n+" joined the game",90,off)
                    else:send("player "+n+" spawn");wait(n+" joined the game",40,off)
                if ECONOMY:
                    # Recover the previous failed Carpet victim via its original synthetic UUID.
                    time.sleep(2)
                    for n in bots[1:]:
                        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
                    from qa_clients import NativeClient
                    case=BASE/"build/economy-native-clients";case.mkdir(exist_ok=True)
                    def econ_client(n,run):
                        c=NativeClient(name=n,port=25580,game=case/n,log=BASE/(n+"-"+run+".log"))
                        clients.append(c)
                        return c
                    native={}
                    for n in bots[1:]:
                        off=len(lines);native[n]=econ_client(n,"economy-v2");wait(n+" joined the game",90,off)
                    time.sleep(2)
                # Join events precede delayed lobby/return initialization.
                ledger=RUN/"kapisma-arena-return.v1"
                bot_ids={str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+n).encode()).digest(),version=3)) for n in bots}
                def entries():
                    return set(ledger.read_text(encoding="utf-8").splitlines()[1:]) if ledger.exists() else set()
                until=time.monotonic()+20
                while time.monotonic()<until:
                    if not any(row.split("|")[0] in bot_ids for row in entries()):break
                    time.sleep(.5)
                else:raise RuntimeError("combat bot return pending")
                unrelated=entries()
                time.sleep(2)
                check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"COMBAT_ALL_LOBBY_READY")
                def proof(predicate,secs=15):
                    until=time.monotonic()+secs
                    while time.monotonic()<until:
                        off=len(lines);send("arena proof");line=wait("ARENA_PROOF ",5,off)
                        if predicate(line):return line
                        time.sleep(.25)
                    raise RuntimeError("proof not reached "+line)
                def teams():
                    result=[]
                    for index in (0,1):
                        off=len(lines);send("team list kp_arena_"+str(index))
                        line=wait("Kırmızı" if index==0 else "Mavi",5,off)
                        result.append([n for n in bots if n in line])
                    assert [len(x) for x in result]==([2,1] if ECONOMY else [2,2]),result
                    return result
                def start_game(id):
                    send("kapisma game "+id);send("kapisma start");status(id+" RUNNING",150)
                    print("PASS "+id+"_RUNNING",flush=True)
                def stop_game(id):
                    send("kapisma stop");status("yok (lobi)",60)
                    assert entries()==unrelated,"unrelated returns changed or combat return pending"
                    print("PASS "+id+"_RETURN",flush=True)
                def health(n):
                    off=len(lines);send("data get entity "+n+" Health")
                    line=wait(n+" has the following entity data:",5,off)
                    return float(re.search(r"data: ([\d.]+)f",line)[1])
                def look(n,coords,target):
                    send("execute as "+n+" at @s run tp @s "+coords+" facing "+target)
                def item(n,item,slot="hotbar.0"):
                    check("execute if items entity "+n+" "+slot+" "+item,"ITEM_"+n+"_"+item.replace(":","_").replace("[","_").replace("]","_"))
                def attack(n):
                    send("player "+n+" attack once");time.sleep(.6)
                def use(n):
                    send("player "+n+" use once");time.sleep(.6)
                if PACKET:
                    a,b=bots
                    def packet_step(step,slot,cursor):
                        assert native.p.poll() is None
                        tmp=native.packet_trigger.with_suffix(".pending")
                        tmp.write_text(str(step),encoding="ascii");os.replace(tmp,native.packet_trigger)
                        until=time.monotonic()+18
                        expect=f"PACKETQA_STATE {step} slot={slot} cursor={cursor}"
                        while time.monotonic()<until:
                            raw=native.path.read_text(encoding="utf-8",errors="replace")
                            if "PACKETQA_ERROR" in raw:raise RuntimeError(raw[-2000:])
                            if f"PACKETQA_STATE {step} " in raw:
                                assert expect in raw,raw[-2000:];break
                            time.sleep(.25)
                        else:raise RuntimeError("native packet timeout "+raw[-1500:])
                        assert f"PACKETQA_SENT {step}" in raw
                        off=len(lines);send("arena-equipment-proof "+a+" snapshot")
                        row=wait("PACKETQA_SERVER "+a,5,off)
                        assert f"slot={slot} cursor={cursor}" in row,row
                        print("PASS NATIVE_PACKET_"+str(step)+"_SERVER_CLIENT_AGREE",flush=True)
                    start_game("KIT_IHALESI")
                    item(a,"minecraft:wooden_sword")
                    packet_step(1,0,1)
                    packet_step(2,0,1)
                    packet_step(3,1,0)
                    packet_step(4,1,0)
                    item(a,"minecraft:wooden_sword")
                    check("execute as "+a+" at @s unless entity @e[type=minecraft:item,distance=..75]","PACKET_NO_DROPPED_ITEM")
                    stop_game("PACKET_LOCKED")
                    start_game("YASAYAN_HARITA")
                    packet_step(5,0,1)
                    packet_step(6,0,0)
                    stop_game("PACKET_CONTROL")
                    off=len(lines);native.close();wait(a+" left the game",20,off)
                    off=len(lines);send("player "+b+" kill");wait(b+" left the game",20,off)
                elif EQUIPMENT:
                    start_game("KIT_IHALESI")
                    off=len(lines);send("arena-equipment-proof "+bots[0]+" locked")
                    wait("EQUIP_DONE true",12,off)
                    for line in lines[off:]:
                        if "EQUIP_PASS " in line:print("PASS "+line.split("EQUIP_PASS ",1)[1].strip(),flush=True)
                    stop_game("EQUIPMENT_LOCKED")
                    start_game("YASAYAN_HARITA")
                    off=len(lines);send("arena-equipment-proof "+bots[0]+" control")
                    wait("EQUIP_DONE false",12,off)
                    for line in lines[off:]:
                        if "EQUIP_PASS " in line:print("PASS "+line.split("EQUIP_PASS ",1)[1].strip(),flush=True)
                    stop_game("EQUIPMENT_CONTROL")
                    for n in bots:
                        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
                elif ECONOMY:
                    a,b,c=bots
                    start_game("KIT_IHALESI")
                    for n in bots:item(n,"minecraft:wooden_sword")
                    send("execute as "+a+" run arena teklif 70")
                    send("execute as "+b+" run arena teklif 30")
                    proof(lambda l:"round=2" in l,35)
                    item(a,"minecraft:iron_sword");item(a,"minecraft:shield","weapon.offhand")
                    item(b,"minecraft:wooden_sword");item(c,"minecraft:wooden_sword")
                    send("player "+a+" hotbar 1");send("player "+a+" drop once");send("player "+a+" dropStack once")
                    time.sleep(1)
                    item(a,"minecraft:iron_sword")
                    check("execute as "+a+" at @s unless entity @e[type=minecraft:item,distance=..75]","KI_NO_DROPPED_ITEM")
                    send("execute as "+b+" run arena teklif 70")
                    send("execute as "+a+" run arena teklif 31")
                    proof(lambda l:"round=3" in l,35)
                    item(b,"minecraft:bow","hotbar.1")
                    check("execute unless items entity "+a+" hotbar.1 minecraft:bow","KI_OVERBUDGET_DENIED")
                    send("execute as "+c+" run arena teklif 90")
                    proof(lambda l:"phase=COMBAT" in l,35)
                    item(c,"minecraft:iron_chestplate","armor.chest")
                    # Use real melee path; only positions set by console. No /damage or kill.
                    send("execute as "+a+" at @s run tp @s .5 301 2.5 0 0")
                    send("execute as "+b+" at @s run tp @s .5 301 4.5 0 0")
                    send("execute as "+c+" at @s run tp @s 12.5 301 12.5 0 0")
                    time.sleep(1.3);before_hp=health(b);attack(a);after_hp=health(b)
                    assert after_hp<before_hp,(before_hp,after_hp)
                    print("PASS KI_REAL_PVP",before_hp,after_hp,flush=True)
                    def eliminate(target):
                        until=time.monotonic()+30
                        while time.monotonic()<until:
                            off=len(lines);send("execute if entity @a[name="+target+",gamemode=spectator] run say KI_ELIM_"+target)
                            try:wait("[Server] KI_ELIM_"+target,1,off);return
                            except RuntimeError:pass
                            # Reposition both only to compensate ordinary knockback.
                            send("execute as "+a+" at @s run tp @s .5 301 2.5 0 0")
                            send("execute as "+target+" at @s run tp @s .5 301 4.5 0 0")
                            time.sleep(.6);attack(a);time.sleep(.5)
                        raise RuntimeError("real combat elimination failed "+target)
                    eliminate(b)
                    proof(lambda l:"alive=2" in l)
                    check("execute unless items entity "+b+" hotbar.0 minecraft:wooden_sword unless items entity "+b+" hotbar.1 minecraft:bow","KI_ELIMINATED_KIT_CLEARED")
                    off=len(lines);native[b].close();wait(b+" left the game",20,off)
                    off=len(lines);native[b]=econ_client(b,"economy-v2-rejoin");wait(b+" joined the game",90,off);time.sleep(2)
                    check("execute if entity @a[name="+b+",gamemode=spectator] unless items entity "+b+" hotbar.1 minecraft:bow","KI_ELIMINATED_REJOIN_NO_KIT")
                    eliminate(c)
                    proof(lambda l:"phase=ENDED" in l and "alive=1" in l)
                    print("PASS KI_NATURAL_LAST_SURVIVOR",flush=True)
                    status("yok (lobi)",60);assert entries()==unrelated
                    print("PASS KI_NATURAL_RETURN",flush=True)
                    for n in bots[1:]:
                        off=len(lines);native[n].close();wait(n+" left the game",20,off)
                        off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",30,off)
                    native.clear();time.sleep(2)
                    start_game("INSA_ET_SAVUN")
                    red,blue=teams();a,b=red;c=blue[0]
                    for n in red:
                        item(n,"minecraft:oak_planks[count=64]")
                        check("execute unless items entity "+n+" hotbar.1 minecraft:oak_planks","IS_ODD_NO_EXTRA_"+n)
                    print("PASS IS_ODD_FIRST_TEAM_TOTAL_128",flush=True)
                    proof(lambda l:"phase=ASSAULT" in l,45)
                    send("player "+c+" hotbar 5")
                    send("execute as "+c+" at @s run tp @s .5 301 2.5 180 29")
                    time.sleep(.4);send("player "+c+" attack continuous");time.sleep(4);send("player "+c+" stop")
                    proof(lambda l:"leg=2" in l and "phase=BUILD" in l and "reset=false" in l,20)
                    item(c,"minecraft:oak_planks[count=64]");item(c,"minecraft:oak_planks[count=64]","hotbar.1")
                    print("PASS IS_ODD_SECOND_TEAM_TOTAL_128",flush=True)
                    stop_game("IS_ODD")
                    for n in bots:
                        off=len(lines)
                        if n in native:native[n].close()
                        else:send("player "+n+" kill")
                        wait(n+" left the game",20,off)
                else:
                    start_game("DONEN_KARAKOL")
                    red,blue=teams()
                    a,b=red;c,d=blue
                    # All four stay far from active point; real Player.attack path, no /damage for PvP.
                    for n,x,z in ((a,0,8),(b,0,10),(c,10,8),(d,12,8)):
                        send("execute as "+n+" at @s run tp @s "+str(x+.5)+" 305 "+str(z+.5))
                    send("execute as "+a+" at @s run tp @s .5 305 8.5 0 0");send("player "+a+" hotbar 1");time.sleep(1.5)
                    before=health(b);attack(a);assert health(b)==before
                    print("PASS DK_FRIENDLY_FIRE_DENIED",flush=True)
                    send("execute as "+a+" at @s run tp @s 10.5 305 6.5 0 0")
                    time.sleep(1.2);before=health(c);attack(a);after=health(c)
                    assert after<before,(before,after)
                    print("PASS DK_OPPONENT_REAL_ATTACK",before,after,flush=True)
                    send("gamemode spectator "+d);send("gamemode spectator "+c)
                    proof(lambda l:"paused=true" in l)
                    send("gamemode adventure "+c);proof(lambda l:"paused=false" in l)
                    print("PASS DK_REAL_ROSTER_PAUSE_RESUME",flush=True)
                    send("gamemode adventure "+d)
                    stop_game("DK_COMBAT")
                    start_game("INSA_ET_SAVUN")
                    red,blue=teams();a,b=red;c,d=blue
                    # Real Carpet use hits the floor from two blocks away, not setblock.
                    send("execute as "+a+" at @s run tp @s -4.5 301 2.5 180 39");send("player "+a+" hotbar 1");time.sleep(.4)
                    item(a,"minecraft:oak_planks[count=64]")
                    use(a)
                    check("execute as "+a+" at @s if block -5 301 0 minecraft:oak_planks","IS_REAL_PLACE")
                    item(a,"minecraft:oak_planks[count=63]")
                    # Repeating on the occupied cell must not double-consume or overwrite the wall.
                    send("execute as "+a+" at @s run tp @s -4.5 301 2.5 180 39");send("player "+a+" hotbar 1");time.sleep(.4)
                    use(a)
                    # The ray may legitimately hit the new wall and place another cell; check original ledger
                    # using an out-of-zone target instead.
                    send("execute as "+a+" at @s run tp @s -9.5 301 2.5 90 39");time.sleep(.4)
                    use(a)
                    check("execute as "+a+" at @s if block -12 301 2 minecraft:air","IS_OUT_OF_ZONE_DENIED")
                    proof(lambda l:"phase=ASSAULT" in l,45)
                    send("player "+c+" hotbar 5")
                    send("execute as "+c+" at @s run tp @s -4.5 301 2.5 180 29")
                    time.sleep(.4)
                    send("player "+c+" attack continuous")
                    time.sleep(3);send("player "+c+" stop")
                    check("execute as "+c+" at @s if block -5 301 0 minecraft:air","IS_REAL_BREAK")
                    send("execute as "+c+" at @s run tp @s .5 301 2.5 180 29")
                    send("player "+c+" attack continuous");time.sleep(4);send("player "+c+" stop")
                    proof(lambda l:"leg=2" in l and "phase=BUILD" in l and "reset=false" in l,20)
                    check("execute as "+c+" at @s if block 0 301 0 minecraft:target","IS_ROLE_SWAP_CORE_RESTORED")
                    item(c,"minecraft:oak_planks[count=64]")
                    print("PASS IS_REAL_CAPTURE_ROLE_SWAP",flush=True)
                    stop_game("IS_COMBAT")
                    for n in bots:
                        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
            elif PLAYERS:
                for n in ((1,2,3) if VOICE or FLIGHT else (1,2)):
                    c=Client(n);clients.append(c);wait(c.name+" joined the game",120);time.sleep(1)
                    if VOICE:c.wait_voice()
                a,b=clients[:2]
                if FLIGHT:
                    ledger=RUN/"kapisma-arena-return.v1"
                    until=time.monotonic()+20
                    while time.monotonic()<until:
                        if ledger.read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n":break
                        time.sleep(.5)
                    else:raise RuntimeError("prior client returns unresolved")
                    print("PASS three earlier native-client returns recovered",flush=True)
                    for c in clients:
                        check("execute if entity @a[name="+c.name+",gamemode=adventure] if items entity "+c.name+" hotbar.4 minecraft:nether_star","FLIGHT_RECOVERED_"+c.name)
                    c=clients[2];off=len(lines);c.close();wait(c.name+" left the game",20,off)
                    send("kapisma game ALCAK_IRTIFA");send("kapisma start");status("ALCAK_IRTIFA RUNNING",150)
                    print("PASS FLIGHT_REAL_GAME_RUNNING",flush=True)
                    check("execute if items entity "+a.name+" hotbar.0 minecraft:firework_rocket[count=1] if items entity "+b.name+" hotbar.0 minecraft:firework_rocket[count=1]","FLIGHT_SINGLE_UNLIMITED_ROCKET_KIT")
                    until=time.monotonic()+100
                    last=""
                    while time.monotonic()<until:
                        off=len(lines);send("arena proof");line=wait("ARENA_PROOF ",5,off)
                        if line!=last:
                            if "gates=" in line:print("OBS "+line.strip(),flush=True)
                            last=line
                        if "ended=true" in line and ("ArenaDevQA1:4" in line or "ArenaDevQA2:4" in line):
                            print("PASS FLIGHT_CONTINUOUS_NATIVE_ALL_GATES",flush=True);break
                        if "ended=true" in line or "ARENA_PROOF none" in line:raise RuntimeError("race ended without native flight win "+line)
                        time.sleep(.6)
                    else:raise RuntimeError("native flight timeout "+last)
                    rocket_rows=[l for l in lines if "FLIGHT_ROCKET " in l]
                    assert any(" true remaining=200 count=1" in l for l in rocket_rows),rocket_rows
                    assert all("count=1" in l for l in rocket_rows),rocket_rows
                    print("PASS native rocket accepted and server item not consumed",flush=True)
                    print("OBS rocket accepted",sum(" true " in l for l in rocket_rows),"denied",sum(" false " in l for l in rocket_rows),flush=True)
                    status("yok (lobi)",60)
                    assert ledger.read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                    print("PASS FLIGHT_NATURAL_RETURN",flush=True)
                elif VOICE:
                    observer=clients[2]
                    wait("Voice chat server started",30)
                    print("PASS actual SVC clients connected",flush=True)
                    send("kapisma game DONEN_KARAKOL");send("kapisma start")
                    status("DONEN_KARAKOL RUNNING",150)
                    print("PASS VOICE_ARENA_RUNNING",flush=True)
                    send("execute as "+a.name+" at @s run tp @s -20 305 -8")
                    send("execute as "+b.name+" at @s run tp @s 20 305 8")
                    send("execute as "+observer.name+" at @s run tp @s 21 305 8")
                    send("item replace entity "+observer.name+" hotbar.0 with minecraft:air")
                    def tune(channel):
                        send("item replace entity "+b.name+" hotbar.0 with analog:radio[minecraft:radio={enabled:1b,transmit:0b,receive:1b,channel:"+str(channel)+"}]")
                        check("execute if items entity "+b.name+" hotbar.0 analog:radio","VOICE_TUNED_"+str(channel))
                        time.sleep(.8)
                    def audio(label,expected,expect_observer,nearby=False,source=None):
                        before_pcm=b.pcm();before_observer=observer.pcm();off=len(lines)
                        send("arena-voice-proof "+(source or a).name+" 7"+(" nearby" if nearby else ""))
                        wait("ARENA_VOICE_PROOF_DONE",30,off)
                        time.sleep(2)
                        delta=b.pcm()-before_pcm
                        odelta=observer.pcm()-before_observer
                        rows=[l for l in lines[off:] if "ARENA_VOICE_PACKET " in l]
                        assert not any("ARENA_VOICE_PROOF_ERROR" in l for l in lines[off:])
                        print(f"{label}: receiver_PCM={delta} observer_PCM={odelta} observed_packet_events={len(rows)}",flush=True)
                        assert (delta>0)==expected and (odelta>0)==expect_observer,(label,delta,odelta)
                        print("PASS "+label,flush=True)
                    def mic_audio(source,expected,label):
                        counts={c:c.pcm() for c in expected};off=len(lines)
                        send("arena-voice-proof "+source.name+" 7 mic")
                        wait("ARENA_VOICE_PROOF_DONE",30,off);time.sleep(2)
                        actual={c.name:c.pcm()-counts[c] for c in expected}
                        assert all((actual[c.name]>0)==expected[c] for c in expected),(label,actual)
                        print("PASS "+label+" "+str(actual),flush=True)
                    if not RED:
                        send("execute as "+a.name+" at @s run tp @s 18 305 8")
                        mic_audio(a,{b:True,observer:True},"VOICE_ORDINARY_MIC_POSITIVE_CONTROL")
                        send("execute as "+a.name+" at @s run tp @s -20 305 -8")
                    tune(7)
                    audio("VOICE_ACTIVE_RECEIVER_AND_BYSTANDER",True,True)
                    send("gamemode spectator "+observer.name);time.sleep(1)
                    audio("VOICE_SPECTATOR_RED_LEAK" if RED else "VOICE_SPECTATOR_GREEN_BLOCKED",True,RED)
                    send("gamemode adventure "+observer.name);time.sleep(1)
                    audio("VOICE_ACTIVE_RESTORED",True,True)
                    tune(8);audio("VOICE_WRONG_CHANNEL",False,False)
                    send("arena-voice-fixture "+b.name+" receiver")
                    wait("ARENA_VOICE_FIXTURE receiver",10)
                    audio("FIXED_ACTIVE_RECEIVER",True,True)
                    send("gamemode spectator "+observer.name);time.sleep(1)
                    audio("FIXED_SPECTATOR_"+("RED_LEAK" if RED else "GREEN_BLOCKED"),True,RED)
                    send("gamemode adventure "+observer.name);time.sleep(1)
                    send("arena-voice-fixture "+a.name+" transmitter")
                    wait("ARENA_VOICE_FIXTURE transmitter",10)
                    audio("FIXED_NEARBY_TRANSMITTER",True,True,nearby=True)
                    send("gamemode spectator "+observer.name);time.sleep(1)
                    audio("FIXED_NEARBY_SPECTATOR_"+("RED_LEAK" if RED else "GREEN_BLOCKED"),True,RED,nearby=True)
                    send("gamemode adventure "+observer.name);time.sleep(1)
                    send("kapisma stop");status("yok (lobi)",60)
                    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                    print("PASS VOICE_ARENA_RETURN",flush=True)
                    if not RED:
                        send("kapisma game KIRIK_TELSIZ");send("kapisma start")
                        status("KIRIK_TELSIZ RUNNING",150)
                        for c in clients:
                            check("execute if items entity "+c.name+" hotbar.0 analog:radio[minecraft:radio={enabled:1b,transmit:1b,receive:1b,channel:7}]","KT_READY_RADIO_"+c.name)
                        def fragments(round):
                            result={}
                            for c in clients:
                                until=time.monotonic()+15
                                found=[]
                                while time.monotonic()<until:
                                    found=[re.search(r"Tur "+str(round)+r" · (RED|BLUE) · Gizli parça: (\d{2})",l) for l in c.chats()]
                                    found=[m for m in found if m]
                                    if found:break
                                    time.sleep(.3)
                                assert found,("missing fragment",c.name,round)
                                assert len({(m[1],m[2]) for m in found})==1
                                result[c]=(found[-1][1],int(found[-1][2]))
                            assert {role for role,_ in result.values()}=={"RED","BLUE"}
                            for role in ("RED","BLUE"):
                                assert len({value for r,value in result.values() if r==role})==1
                            return result
                        def puzzle_state(predicate,secs=12):
                            until=time.monotonic()+secs
                            while time.monotonic()<until:
                                off=len(lines);send("arena proof");line=wait("ARENA_PROOF ",5,off)
                                if predicate(line):return line.split("ARENA_PROOF ",1)[1]
                                time.sleep(.25)
                            raise RuntimeError("puzzle state not reached "+line)
                        first=fragments(1)
                        red=next(c for c,(role,_) in first.items() if role=="RED")
                        blue=next(c for c,(role,_) in first.items() if role=="BLUE")
                        singleton=next(c for c,(role,_) in first.items() if sum(r==role for r,_ in first.values())==1)
                        print("PASS KT_NATIVE_PRIVATE_ROLE_HINTS",flush=True)
                        old_pcm=blue.pcm();off=len(lines);send("arena-voice-proof "+red.name+" 7")
                        wait("ARENA_VOICE_PROOF_DONE",30,off);time.sleep(2)
                        assert blue.pcm()>old_pcm
                        print("PASS KT_DEFAULT_KIT_ACTUAL_AUDIO",flush=True)
                        majority=[c for c,(role,_) in first.items() if role!=first[singleton][0]]
                        speaker,relay=majority
                        send("execute at "+speaker.name+" run tp "+relay.name+" ~ ~ ~1")
                        time.sleep(.5)
                        mic_audio(speaker,{singleton:True},"KT_ACTUAL_MIC_ENTRY_RADIO")
                        for c in clients:send("item replace entity "+c.name+" hotbar.0 with minecraft:air")
                        time.sleep(.5)
                        mic_audio(speaker,{relay:False,singleton:False},"KT_ORDINARY_MIC_WITHOUT_RADIO_BLOCKED")
                        send("item replace entity "+relay.name+" hotbar.0 with analog:radio[minecraft:radio={enabled:1b,transmit:1b,receive:0b,channel:7}]")
                        send("item replace entity "+singleton.name+" hotbar.0 with analog:radio[minecraft:radio={enabled:1b,transmit:0b,receive:1b,channel:7}]")
                        time.sleep(.5)
                        mic_audio(speaker,{singleton:True},"KT_NEARBY_CARRIER_ACTUAL_SPEAKER")
                        send("gamemode spectator "+relay.name);time.sleep(1)
                        mic_audio(speaker,{singleton:False},"KT_SPECTATOR_CARRIER_DENIED")
                        send("gamemode adventure "+relay.name);time.sleep(.5)
                        for c in clients:send("item replace entity "+c.name+" hotbar.0 with analog:radio[minecraft:radio={enabled:1b,transmit:1b,receive:1b,channel:7}]")
                        send("gamemode spectator "+singleton.name)
                        frozen=puzzle_state(lambda l:"paused=true" in l)
                        time.sleep(2)
                        assert puzzle_state(lambda l:"paused=true" in l)==frozen
                        print("PASS KT_NATIVE_MISSING_ROLE_PAUSE",flush=True)
                        send("gamemode adventure "+singleton.name)
                        puzzle_state(lambda l:"paused=false" in l)
                        code=next(v for r,v in first.values() if r=="RED")*100+next(v for r,v in first.values() if r=="BLUE")
                        wrong=1000 if code!=1000 else 1001
                        send("execute as "+red.name+" run arena kod "+str(wrong))
                        puzzle_state(lambda l:"attempts=2" in l and "round=1" in l)
                        print("PASS KT_NATIVE_WRONG_GUESS_ONE_CHARGE",flush=True)
                        puzzle_state(lambda l:"cooldown=0" in l)
                        for round in (1,2,3):
                            hints=fragments(round)
                            code=next(v for r,v in hints.values() if r=="RED")*100+next(v for r,v in hints.values() if r=="BLUE")
                            send("execute as "+red.name+" run arena kod "+str(code))
                            if round<3:
                                puzzle_state(lambda l:"round="+str(round+1) in l and "attempts=3" in l)
                            else:
                                puzzle_state(lambda l:"phase=WON" in l)
                            print("PASS KT_NATIVE_SOLVED_ROUND_"+str(round),flush=True)
                        status("yok (lobi)",60)
                        assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                        print("PASS KT_NATIVE_NATURAL_WIN_RETURN",flush=True)
                elif MORE:
                    ledger=RUN/"kapisma-arena-return.v1"
                    if ledger.exists():
                        assert ledger.read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                        print("PASS prior offline records recovered on server restart",flush=True)
                    def proof(pred,secs=20):
                        until=time.monotonic()+secs
                        while time.monotonic()<until:
                            off=len(lines);send("arena proof")
                            line=wait("ARENA_PROOF ",5,off)
                            if pred(line):return line
                            time.sleep(.25)
                        raise RuntimeError("proof state not reached\n"+"".join(lines[-20:]))
                    def choose_red():
                        off=len(lines);send("team list kp_arena_0")
                        line=wait("Kırmızı",5,off)
                        if a.name in line:return a,b
                        if b.name in line:return b,a
                        raise RuntimeError("team roster unreadable "+line)
                    def start_game(id):
                        send("kapisma game "+id);send("kapisma start")
                        status(id+" RUNNING",150)
                        print("PASS "+id+"_RUNNING",flush=True)
                    def stop_game(id):
                        send("kapisma stop");status("yok (lobi)",60)
                        assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                        check("execute if items entity "+a.name+" hotbar.4 minecraft:nether_star if items entity "+b.name+" hotbar.4 minecraft:nether_star",id+"_LOBBY")
                    send("kapisma rule DONEN_KARAKOL scoreGoal 5")
                    start_game("DONEN_KARAKOL")
                    red,blue=choose_red()
                    send("execute as "+red.name+" at @s run tp @s -23.5 305 .5")
                    send("execute as "+blue.name+" at @s run tp @s -23.5 305 1.5")
                    time.sleep(2)
                    proof(lambda l:"red=0" in l and "blue=0" in l)
                    print("PASS DK_NATIVE_CONTESTED",flush=True)
                    send("execute as "+blue.name+" at @s run tp @s .5 305 11.5")
                    proof(lambda l:"red=5" in l and "winner=RED" in l,12)
                    status("yok (lobi)",30)
                    print("PASS DK_NATIVE_NATURAL_WIN_RETURN",flush=True)
                    start_game("KERVAN_BASKINI")
                    red,blue=choose_red()
                    send("execute as "+red.name+" at @s run tp @s -23.5 301 .5")
                    send("execute as "+blue.name+" at @s run tp @s -28.5 301 8.5")
                    proof(lambda l:bool(re.search(r"distance=[2-4], cargo=20",l)),10)
                    check("execute as "+red.name+" at @s if block -24 300 0 minecraft:gray_concrete","KB_NATIVE_STABLE_ROAD")
                    print("PASS KB_NATIVE_ESCORT_PROGRESS",flush=True)
                    send("execute as "+red.name+" at @s run tp @s -28.5 301 -8.5")
                    send("execute as "+blue.name+" at @s run tp @s -20.5 301 .5")
                    proof(lambda l:"cargo=19" in l,10)
                    print("PASS KB_NATIVE_RAIDER_CARGO",flush=True)
                    # Disconnect a real registered player; match must pause and retain return ledger.
                    b.close();wait(b.name+" left the game",20)
                    first=proof(lambda l:"paused=true" in l)
                    time.sleep(2)
                    second=proof(lambda l:"paused=true" in l)
                    assert first.split("ARENA_PROOF ",1)[1]==second.split("ARENA_PROOF ",1)[1]
                    print("PASS KB_NATIVE_DISCONNECT_PAUSE",flush=True)
                    send("kapisma stop");status("yok (lobi)",60)
                    ledger=RUN/"kapisma-arena-return.v1"
                    assert len(ledger.read_text(encoding="utf-8").splitlines())==2
                    print("PASS KB_NATIVE_OFFLINE_RETURN_RETAINED",flush=True)
                    off=len(lines);b=Client(2);clients.append(b);wait(b.name+" joined the game",120,off);time.sleep(2)
                    check("execute if items entity "+b.name+" hotbar.4 minecraft:nether_star","KB_NATIVE_REJOIN_LOBBY")
                    assert ledger.read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                    print("PASS KB_NATIVE_OFFLINE_RETURN_ACK",flush=True)
                    start_game("INSA_ET_SAVUN")
                    red,blue=choose_red()
                    check("execute if items entity "+red.name+" hotbar.0 minecraft:oak_planks[count=64] if items entity "+red.name+" hotbar.1 minecraft:oak_planks[count=64]","IS_NATIVE_BUILD_MATERIALS")
                    check("execute as "+red.name+" at @s if block 0 301 0 minecraft:target","IS_NATIVE_CORE_EXISTS")
                    proof(lambda l:"phase=ASSAULT" in l,45)
                    check("execute if items entity "+blue.name+" hotbar.4 minecraft:iron_axe","IS_NATIVE_ATTACKER_AXE")
                    print("PASS IS_NATIVE_NATURAL_STAGE_TRANSITION",flush=True)
                    stop_game("IS_NATIVE")
                    start_game("ALCAK_IRTIFA")
                    check("execute if items entity "+a.name+" armor.chest minecraft:elytra if items entity "+b.name+" armor.chest minecraft:elytra if items entity "+a.name+" hotbar.0 minecraft:firework_rocket[count=1] if items entity "+b.name+" hotbar.0 minecraft:firework_rocket[count=16]","AI_NATIVE_EQUAL_KIT")
                    check("execute as "+a.name+" at @s if block -30 288 -5 minecraft:yellow_concrete","AI_NATIVE_GATE_FRAME")
                    send("execute as "+a.name+" at @s run tp @s -31 288 0 -90 0")
                    send("execute as "+a.name+" at @s run tp @s -29 288 0 -90 0")
                    proof(lambda l:a.name+":0" in l and b.name+":0" in l)
                    print("PASS AI_NATIVE_TELEPORT_NOT_GATE",flush=True)
                    stop_game("AI_NATIVE")
                    send("kapisma game KIRIK_TELSIZ");off=len(lines);send("kapisma start")
                    wait("Telsiz için deneysel Analog ve ses köprüsü gerekli.",8,off)
                    print("PASS KT_NATIVE_REJECTED_WITHOUT_VOICE_DEPENDENCY",flush=True)
                else:
                    send("kapisma game YASAYAN_HARITA");send("kapisma start")
                    status("YASAYAN_HARITA RUNNING",120)
                    print("PASS YH_NATIVE_GAME_MANAGER_RUNNING",flush=True)
                    check("execute if entity @a[name="+a.name+",gamemode=adventure] if entity @a[name="+b.name+",gamemode=adventure]","YH_NATIVE_ADVENTURE")
                    check("execute if items entity "+a.name+" hotbar.0 minecraft:compass if items entity "+b.name+" hotbar.0 minecraft:compass","YH_NATIVE_COMPASSES")
                    a.wait_chat("Sıradaki hedef: Taş sütun");b.wait_chat("Sıradaki hedef: Taş sütun")
                    # Discover authoritative team assignment; advance only one team's first target.
                    offset=len(lines);send("team list kp_arena_0");line=wait("Kırmızı",5,offset)
                    red=a if a.name in line else b if b.name in line else None
                    if red is None:
                        offset=len(lines);send("execute if entity @a[name="+a.name+",team=kp_arena_0] run say YH_RED_A")
                        try:wait("YH_RED_A",3,offset);red=a
                        except RuntimeError:red=b
                    blue=b if red==a else a
                    send("execute as "+red.name+" at @s run tp @s -24.5 301 -5.5")
                    send("execute as "+red.name+" run arena bul")
                    red.wait_chat("Sıradaki hedef: Bakır kemer")
                    blue.assert_no_chat("Sıradaki hedef: Bakır kemer")
                    print("PASS YH_NATIVE_PRIVATE_NEXT_CLUE",flush=True)
                    send("kapisma stop");status("yok (lobi)",60)
                    check("execute if items entity "+a.name+" hotbar.4 minecraft:nether_star if items entity "+b.name+" hotbar.4 minecraft:nether_star","YH_NATIVE_RETURN_LOBBY")
                    ledger=RUN/"kapisma-arena-return.v1"
                    assert ledger.read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                    print("PASS YH_NATIVE_PERSISTED_RETURN_ACK",flush=True)
                    send("kapisma game KIT_IHALESI");send("kapisma start");status("KIT_IHALESI RUNNING",120)
                    print("PASS KI_NATIVE_SECOND_MATCH_RUNNING",flush=True)
                    send("execute as "+a.name+" run arena teklif 63")
                    send("execute as "+b.name+" run arena teklif 27")
                    a.wait_chat("Gizli teklif isteği alındı.");b.wait_chat("Gizli teklif isteği alındı.")
                    send("execute as "+a.name+" run arena bakiye");send("execute as "+b.name+" run arena bakiye")
                    a.wait_chat("Teklif: 63");b.wait_chat("Teklif: 27")
                    a.assert_no_chat("Teklif: 27");b.assert_no_chat("Teklif: 63")
                    print("PASS KI_NATIVE_SEALED_BID",flush=True)
                    a.wait_chat("Kalan jetonun: 37")
                    b.assert_no_chat("Kalan jetonun: 37")
                    a.wait_chat("İhaleyi kazanan: "+a.name);b.wait_chat("İhaleyi kazanan: "+a.name)
                    print("PASS KI_NATIVE_AWARD_PRIVATE_BALANCE",flush=True)
                    send("kapisma stop");status("yok (lobi)",60)
                    assert ledger.read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
                    print("PASS KI_NATIVE_RETURN_AND_NO_STALE_LEDGER",flush=True)
        finally:
            for c in clients:c.close()
            if process.poll() is None:
                send("stop")
                try:process.wait(timeout=45)
                except subprocess.TimeoutExpired:process.kill();process.wait()
            reader.join(5)
        if CRASH_ARM:assert process.returncode!=0,process.returncode
        else:assert process.returncode==0,process.returncode
        assert not any("Mixin apply failed" in line or "Exception in server tick loop" in line for line in lines)
        if not CRASH_ARM:print("PASS clean dev server shutdown",flush=True)
protected_after=guard()
print("PROTECTED_AFTER",json.dumps({str(p.relative_to(ROOT)):h for p,h in protected_after.items()},sort_keys=True),flush=True)
assert protected_after==protected_before
print("PASS live/RTS artifact hashes unchanged",flush=True)