"""Real Minecraft client chat delivery acceptance. Reuses MOD lab; never touches live Kapisma."""
from pathlib import Path
from queue import Queue, Empty
from threading import Thread
import msvcrt, os, re, socket, subprocess, time, json, hashlib, uuid
from verify import ROOT, LAB, WORLD, JAVA, MODS, PROPS, VOICE, EXPECTED, digest, port_free
from profile_guard import snapshot_profile
from prepare import install, generate

LOG = ROOT / "prototypes/kirik-telsiz/acceptance_client.log"

class Server:
    def __init__(self, empty, log):
        self.q = Queue()
        self.p = subprocess.Popen([str(JAVA), "-Xmx2G", "-Dfabric.modsFolder="+str(empty),
            "-Dfabric.addMods="+os.pathsep.join(map(str, MODS)), "-jar", "fabric-server-launch.jar", "nogui"],
            cwd=LAB, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace", bufsize=1)
        def reader():
            for line in self.p.stdout:
                log.write(line); log.flush(); self.q.put(line)
        self.t = Thread(target=reader, daemon=True); self.t.start()
    def send(self,*commands):
        assert self.p.poll() is None
        self.p.stdin.write("\n".join(commands)+"\n"); self.p.stdin.flush()
    def wait(self,marker,timeout=30):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            try: line=self.q.get(timeout=.4)
            except Empty:
                if self.p.poll() is not None: break
                continue
            if marker in line: return line
        raise RuntimeError("Missing marker "+marker)
    def check(self, condition, marker):
        self.send("execute "+condition+" run say "+marker, "say END_"+marker)
        found=False
        end=time.monotonic()+20
        while time.monotonic()<end:
            try: line=self.q.get(timeout=.4)
            except Empty: continue
            if line.rstrip().endswith("[Server] "+marker): found=True
            if line.rstrip().endswith("[Server] END_"+marker):
                assert found, "FAIL "+marker
                print("PASS "+marker,flush=True); return
        raise RuntimeError("Missing check boundary "+marker)
    def get(self,name):
        self.send("scoreboard players get "+name+" kt_ctrl")
        line=self.wait(name+" has ")
        m=re.search(r"has (-?\d+) \[kt_ctrl\]",line)
        assert m,line
        return int(m[1])
    def spawn(self,n):
        name=f"ktqa{n}"
        self.send(f"player {name} spawn")
        self.wait(name+" joined the game")
        self.send("gamemode adventure "+name)
    def stop(self):
        if self.p.poll() is None:
            self.send("stop")
            try: self.p.wait(timeout=55)
            except subprocess.TimeoutExpired: self.p.terminate(); self.p.wait(timeout=10)
        self.t.join(timeout=5)
        assert self.p.returncode==0,self.p.returncode

class Client:
    def __init__(self,n):
        self.name=f"KtNativeQA{n}"
        mc=ROOT.parent
        version=mc/"versions/Fabric 1.21.1"
        client=version/"Fabric 1.21.1.jar"
        assert hashlib.sha1(client.read_bytes()).hexdigest()=="30c73b1c5da787909b2f73340419fdf13b9def88"
        meta=json.loads((version/"Fabric 1.21.1.json").read_text(encoding="utf-8-sig"))
        rts=ROOT/"_yedek/devletler-rts-prototip"
        paths=[]
        for library in meta["libraries"]:
            rules=library.get("rules",[])
            if rules and not any(r.get("action")=="allow" and r.get("os",{}).get("name","windows")=="windows" for r in rules): continue
            group,artifact,number,*classifier=library["name"].split(":")
            if group=="net.fabricmc" and artifact in ("fabric-loader","sponge-mixin"):
                number={"fabric-loader":"0.20.1","sponge-mixin":"0.17.5+mixin.0.8.7"}[artifact]
                paths.append(rts/"libraries"/Path(group.replace(".","/"))/artifact/number/(artifact+"-"+number+".jar"))
            else:
                entry=library.get("downloads",{}).get("artifact",{}).get("path","/".join((group.replace(".","/"),artifact,number,artifact+"-"+number+("-"+classifier[0] if classifier else "")+".jar")))
                if group=="org.ow2.asm":
                    candidate=rts/"libraries"/Path(group.replace(".","/"))/artifact/"9.9.1"/(artifact+"-9.9.1.jar")
                    if candidate.is_file(): paths.append(candidate);continue
                paths.append(mc/"libraries"/entry)
        assert all(p.is_file() for p in paths)
        game=LAB.parent/("kirik-telsiz-client-"+str(n))
        game.mkdir(exist_ok=True)
        (game/"mods").mkdir(exist_ok=True);assert not list((game/"mods").iterdir())
        (game/"natives").mkdir(exist_ok=True)
        (game/"options.txt").write_text("version:3955\nonboardAccessibility:false\ntutorialStep:none\nrawMouseInput:false\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:30\nenableVsync:true\noverrideWidth:640\noverrideHeight:480\nguiScale:2\n",encoding="ascii")
        account=uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+self.name).encode()).digest(),version=3)
        args=[str(JAVA),"-Xmx1500M","-XX:TieredStopAtLevel=1","-Dfile.encoding=UTF-8","-Djava.library.path="+str(game/"natives"),"-cp",";".join(map(str,paths+[client])),"net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username",self.name,"--version","KT-QA","--gameDir",str(game),"--assetsDir",str(mc/"assets"),"--assetIndex","17","--uuid",account.hex,"--accessToken","0","--userType","legacy","--versionType","release","--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer","127.0.0.1:25578","--width","640","--height","480"]
        self.path=ROOT/"prototypes/kirik-telsiz"/("client-"+str(n)+".log")
        with self.path.open("wb") as out:
            self.p=subprocess.Popen(args,cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    def chats(self):
        return [line for line in self.path.read_text(encoding="utf-8",errors="replace").splitlines() if "[CHAT]" in line]
    def chat_count(self,needle):
        return sum(needle in line for line in self.chats())
    def wait_chat(self,needle,count=1):
        end=time.monotonic()+15
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

def main():
    assert [digest(x) for x in MODS]==EXPECTED
    assert LAB.resolve()==LAB and not (LAB/WORLD).is_symlink()
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0); msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for port,kind in ((25578,socket.SOCK_STREAM),(25579,socket.SOCK_DGRAM),(25572,socket.SOCK_STREAM)):
            port_free(port,kind)
        empty=LAB/"kirik-telsiz-empty-mods"
        empty.mkdir(exist_ok=True); assert not list(empty.iterdir())
        before = snapshot_profile((PROPS, VOICE))
        pack=LAB/WORLD/"datapacks/kirik-telsiz"
        install(pack)
        settings=("server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\n"
            "enforce-secure-profile=false\nlevel-name="+WORLD+"\nlevel-type=minecraft:flat\n"
            'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n'
            "generate-structures=false\nallow-nether=false\nmax-players=10\ngamemode=adventure\n"
            "spawn-protection=0\nview-distance=4\nsimulation-distance=4\nenable-rcon=false\nenable-query=false\n")
        voice=re.sub(rb"(?m)^port=.*$",b"port=25579",before[VOICE])
        voice=re.sub(rb"(?m)^bind_address=.*$",b"bind_address=127.0.0.1",voice)
        assert b"port=25579" in voice
        s=None
        try:
            PROPS.write_text(settings,encoding="ascii"); VOICE.write_bytes(voice)
            with LOG.open("w",encoding="utf-8") as log:
                try:
                    s=Server(empty,log);s.wait("Done (",150)
                    print("PASS server boot",flush=True)
                    s.send("difficulty peaceful","function kt:reset")
                    clients=[]
                    try:
                        for n in (1,2):
                            c=Client(n)
                            clients.append(c)
                            s.wait(c.name+" joined the game",120)
                            s.send("gamemode adventure "+c.name)
                        print("PASS two native Java clients joined",flush=True)
                        a,b=clients
                        s.send("function kt:start")
                        time.sleep(3)
                        s.check(f"if entity @a[name={a.name},tag=kt.a] if entity @a[name={b.name},tag=kt.b]","KT_NATIVE_ROLES")
                        a.wait_chat("Operatör A: ilk iki rakam")
                        b.wait_chat("Operatör B: son iki rakam")
                        a.assert_no_chat("Operatör B: son iki rakam")
                        b.assert_no_chat("Operatör A: ilk iki rakam")
                        print("PASS role-specific client chat delivery",flush=True)
                        ans=s.get("#answer")
                        s.send(f"execute as {a.name} run trigger kt_code set {ans}");time.sleep(3)
                        s.check("if score #round kt_ctrl matches 2","KT_NATIVE_ROUND2")
                        a.wait_chat("Operatör A: ilk iki rakam",2)
                        b.wait_chat("Operatör B: son iki rakam",2)
                        a.assert_no_chat("Operatör B: son iki rakam")
                        b.assert_no_chat("Operatör A: ilk iki rakam")
                        print("PASS second round role confidentiality",flush=True)
                        # B becomes spectator; A and a new bot begin a separate match.
                        s.send("function kt:reset","gamemode spectator "+b.name)
                        s.spawn(1)
                        spectator_chat_count=b.chat_count("Operatör")
                        s.send("function kt:start");time.sleep(3)
                        s.check(f"unless entity @a[name={b.name},tag=kt.player] if score #phase kt_ctrl matches 1","KT_NATIVE_SPECTATOR")
                        s.send(f"execute as {b.name} run trigger kt_hint")
                        time.sleep(2)
                        assert b.chat_count("Operatör")==spectator_chat_count
                        print("PASS spectator receives no new secret hint",flush=True)
                        s.send("function kt:reset","player ktqa1 kill")
                    finally:
                        for c in clients: c.close()
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s: s.stop()
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",LOG.read_text(encoding="utf-8"))
            assert all((pack/"data/kt/function"/(name+".mcfunction")).read_text(encoding="utf-8")==value for name,value in generate().items())
            print("PASS log and installed functions",flush=True)
        finally:
            for path,data in before.items(): path.write_bytes(data)
            assert all(path.read_bytes()==data for path,data in before.items())
            assert [digest(x) for x in MODS]==EXPECTED
            print("PASS restored settings and mod hashes",flush=True)

if __name__=="__main__":
    main()