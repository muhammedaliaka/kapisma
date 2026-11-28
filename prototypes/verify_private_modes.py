"""Dönen Karakol real server acceptance. Shared MOD lock, dedicated world."""
from pathlib import Path
import importlib.util, json, uuid
import sys, os, msvcrt, socket, subprocess, time, re
from queue import Queue, Empty
from threading import Thread
PROTOTYPES=Path(__file__).resolve().parent
sys.path.append(str(PROTOTYPES/"kirik-telsiz"))
from profile_guard import snapshot_profile
ROOT=PROTOTYPES.parent
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852/server"
PROPS=LAB/"server.properties"
VOICE=LAB/"config/voicechat/voicechat-server.properties"
JAVA=Path(os.environ["JAVA_HOME"])/"bin/java.exe"
MODS=[LAB/"mods/fabric-api-0.116.18+1.21.1.jar",ROOT/"_yedek/devletler-rts-prototip/laboratuvar/server/mods/fabric-carpet-1.21-1.4.147+v240613.jar"]
import hashlib
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
EXPECTED=["1CB46442DDA6D3BE9979A2619610EB1C1F6641CD7ED7CB3EEC058589CE9EED46","079FC8A4E073EB24B010FFCC588E59F93B984143E17D8EF19FBB0B6AFF0F19D9"]
def port_free(p):
    with socket.socket() as s:
        s.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1);s.bind(("127.0.0.1",p))
class Server:
    def __init__(self,empty,log):
        self.q=Queue()
        self.p=subprocess.Popen([str(JAVA),"-Xmx2G","-Dfabric.modsFolder="+str(empty),"-Dfabric.addMods="+os.pathsep.join(map(str,MODS)),"-jar","fabric-server-launch.jar","nogui"],cwd=LAB,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding="utf-8",errors="replace",bufsize=1)
        def read():
            for line in self.p.stdout:log.write(line);log.flush();self.q.put(line)
        self.t=Thread(target=read,daemon=True);self.t.start()
    def send(self,*cmds):
        assert self.p.poll() is None
        self.p.stdin.write("\n".join(cmds)+"\n");self.p.stdin.flush()
    def wait(self,marker,timeout=30):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            try:line=self.q.get(timeout=.4)
            except Empty:
                if self.p.poll() is not None:break
                continue
            if marker.casefold() in line.casefold():return line
        raise RuntimeError("missing "+marker)
    def check(self,condition,marker):
        self.send("execute "+condition+" run say "+marker,"say END_"+marker)
        found=False
        end=time.monotonic()+20
        while time.monotonic()<end:
            try:line=self.q.get(timeout=.4)
            except Empty:continue
            if line.rstrip().endswith("[Server] "+marker):found=True
            if line.rstrip().endswith("[Server] END_"+marker):
                assert found,"FAIL "+marker
                print("PASS "+marker,flush=True);return
        raise RuntimeError("missing check "+marker)
    def get(self,k):
        self.send(f"scoreboard players get #{k} dk_ctrl")
        line=self.wait(f"#{k} has ")
        return int(re.search(r"has (-?\d+) \[dk_ctrl\]",line)[1])
    def spawn(self,n):
        name=f"dkqa{n}"
        self.send(f"player {name} spawn");self.wait(name+" joined the game");self.send("gamemode adventure "+name)
    def stop(self):
        if self.p.poll() is None:
            self.send("stop")
            try:self.p.wait(timeout=60)
            except subprocess.TimeoutExpired:self.p.terminate();self.p.wait(timeout=10)
        self.t.join(timeout=5)
        assert self.p.returncode==0,self.p.returncode

class Client:
    def __init__(self,n):
        self.name=f"KpPrvQA{n}"
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
        game=LAB.parent/("private-qa-client-"+str(n))
        game.mkdir(exist_ok=True)
        (game/"mods").mkdir(exist_ok=True);assert not list((game/"mods").iterdir())
        (game/"natives").mkdir(exist_ok=True)
        (game/"options.txt").write_text("version:3955\nonboardAccessibility:false\ntutorialStep:none\nrawMouseInput:false\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:30\nenableVsync:true\noverrideWidth:640\noverrideHeight:480\nguiScale:2\n",encoding="ascii")
        account=uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+self.name).encode()).digest(),version=3)
        args=[str(JAVA),"-Xmx1500M","-XX:TieredStopAtLevel=1","-Dfile.encoding=UTF-8","-Djava.library.path="+str(game/"natives"),"-cp",";".join(map(str,paths+[client])),"net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username",self.name,"--version","PRIVACY-QA","--gameDir",str(game),"--assetsDir",str(mc/"assets"),"--assetIndex","17","--uuid",account.hex,"--accessToken","0","--userType","legacy","--versionType","release","--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer","127.0.0.1:25578","--width","640","--height","480"]
        self.path=BASE/("privacy-client-"+str(n)+".log")
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


def run(mode):
    global BASE
    BASE=PROTOTYPES/mode
    world=mode+"-lab"
    ns={"yasayan-harita":"yh","kit-ihalesi":"ki"}[mode]
    spec=importlib.util.spec_from_file_location("generator_"+ns,BASE/"prepare.py")
    generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(generator)
    assert [digest(p) for p in MODS]==EXPECTED
    assert LAB.resolve()==LAB and not (LAB/world).is_symlink()
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for p in (25572,25578):port_free(p)
        empty=LAB/"kirik-telsiz-empty-mods";assert empty.is_dir() and not list(empty.iterdir())
        before=snapshot_profile((PROPS,VOICE))
        pack=LAB/world/"datapacks"/mode;generator.install(pack)
        settings=("server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\nenforce-secure-profile=false\n"
                  "level-name="+world+"\nlevel-type=minecraft:flat\ngenerate-structures=false\nallow-nether=false\n"
                  'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n'
                  "difficulty=peaceful\nmax-players=10\ngamemode=adventure\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nenable-rcon=false\nenable-query=false\n")
        s=None
        try:
            PROPS.write_text(settings,encoding="ascii")
            with (BASE/"acceptance_privacy.log").open("w",encoding="utf-8") as log:
                try:
                    s=Server(empty,log);s.wait("Done (",150)
                    s.send(f"function {ns}:reset","forceload add -48 -32 48 32")
                    clients=[]
                    try:
                        for n in (1,2):
                            c=Client(n);clients.append(c);s.wait(c.name+" joined the game",120)
                            s.send("gamemode adventure "+c.name)
                        a,b=clients
                        print("PASS "+ns+" native clients connected",flush=True)
                        s.send(f"function {ns}:start");time.sleep(2)
                        if ns=="yh":
                            s.check(f"if entity @a[name={a.name},team=yh_red] if entity @a[name={b.name},team=yh_blue]","YH_NATIVE_TEAMS")
                            a.wait_chat("Hedef 1/4: Taş sütun");b.wait_chat("Hedef 1/4: Taş sütun")
                            s.send(f"tp {a.name} -24 -60 -6",f"execute as {a.name} run trigger yh_find")
                            a.wait_chat("Hedef 2/4: Bakır kemer");time.sleep(1)
                            b.assert_no_chat("Hedef 2/4: Bakır kemer")
                            print("PASS YH_NATIVE_PRIVATE_NEXT_CLUE",flush=True)
                            s.send(f"tp {b.name} -24 -60 6",f"execute as {b.name} run trigger yh_find")
                            b.wait_chat("Hedef 2/4: Bakır kemer")
                            s.send(f"execute as {a.name} run trigger yh_hint")
                            a.wait_chat("Hedef 2/4: Bakır kemer",2)
                            assert b.chat_count("Hedef 2/4: Bakır kemer")==1
                            print("PASS YH_NATIVE_PRIVATE_HINT_REPLAY",flush=True)
                        else:
                            s.send(f"execute as {a.name} run trigger ki_bid set 63",f"execute as {b.name} run trigger ki_bid set 27")
                            a.wait_chat("Gizli teklif kaydedildi: 63");b.wait_chat("Gizli teklif kaydedildi: 27")
                            a.assert_no_chat("Gizli teklif kaydedildi: 27");b.assert_no_chat("Gizli teklif kaydedildi: 63")
                            print("PASS KI_NATIVE_SEALED_BID_PRIVACY",flush=True)
                            s.send("scoreboard players set #left ki_ctrl 1")
                            a.wait_chat("Kalan jetonun: 37")
                            b.assert_no_chat("Kalan jetonun: 37")
                            a.wait_chat("İhaleyi kazanan: "+a.name);b.wait_chat("İhaleyi kazanan: "+a.name)
                            print("PASS KI_NATIVE_PRIVATE_BALANCE_PUBLIC_WINNER",flush=True)
                        s.send(f"function {ns}:reset")
                    finally:
                        for c in clients:c.close()
                    s.stop();s=None
                    print("PASS "+ns+" clean stop",flush=True)
                finally:
                    if s:s.stop()
            logtext=(BASE/"acceptance_privacy.log").read_text(encoding="utf-8")
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",logtext)
            assert all((pack/"data"/ns/"function"/(k+".mcfunction")).read_text(encoding="utf-8")==v for k,v in generator.generate().items())
            print("PASS "+ns+" installed functions and logs",flush=True)
        finally:
            for p,data in before.items():p.write_bytes(data)
            assert all(p.read_bytes()==data for p,data in before.items()) and [digest(p) for p in MODS]==EXPECTED
            print("PASS "+ns+" restored profiles and hashes",flush=True)
if __name__=="__main__":
    for mode in ("yasayan-harita","kit-ihalesi"):run(mode)
