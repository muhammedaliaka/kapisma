"""Alçak İrtifa real server acceptance. Shared MOD lock, dedicated world."""
from pathlib import Path
import sys, os, msvcrt, socket, subprocess, time, re, json, uuid
from queue import Queue, Empty
from threading import Thread
BASE=Path(__file__).resolve().parent
sys.path.append(str(BASE.parent/"kirik-telsiz"))
from profile_guard import snapshot_profile
from prepare import generate, install
ROOT=BASE.parents[1]
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852/server"
PROPS=LAB/"server.properties"
VOICE=LAB/"config/voicechat/voicechat-server.properties"
WORLD="alcak-irtifa-lab"
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
        self.send(f"scoreboard players get #{k} ai_ctrl")
        line=self.wait(f"#{k} has ")
        return int(re.search(r"has (-?\d+) \[ai_ctrl\]",line)[1])
    def spawn(self,n):
        name=f"aiqa{n}"
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
        self.name=f"AiNativeQA{n}"
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
        game=LAB.parent/("alcak-irtifa-client-"+str(n))
        game.mkdir(exist_ok=True)
        (game/"mods").mkdir(exist_ok=True);assert not list((game/"mods").iterdir())
        (game/"natives").mkdir(exist_ok=True)
        (game/"options.txt").write_text("version:3955\nonboardAccessibility:false\ntutorialStep:none\nrawMouseInput:false\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:30\nenableVsync:true\noverrideWidth:640\noverrideHeight:480\nguiScale:2\n",encoding="ascii")
        account=uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+self.name).encode()).digest(),version=3)
        args=[str(JAVA),"-Dai.qa.enabled=true","-Dfabric.addMods="+os.pathsep.join(map(str,[MODS[0],BASE/"flightqa/flightqa.jar"])),"-Xmx1500M","-XX:TieredStopAtLevel=1","-Dfile.encoding=UTF-8","-Djava.library.path="+str(game/"natives"),"-cp",";".join(map(str,paths+[client])),"net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username",self.name,"--version","AI-QA","--gameDir",str(game),"--assetsDir",str(mc/"assets"),"--assetIndex","17","--uuid",account.hex,"--accessToken","0","--userType","legacy","--versionType","release","--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer","127.0.0.1:25578","--width","640","--height","480"]
        self.path=ROOT/"prototypes/alcak-irtifa"/("flight-client-"+str(n)+".log")
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
    assert [digest(p) for p in MODS]==EXPECTED
    assert LAB.resolve()==LAB and not (LAB/WORLD).is_symlink()
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for p in (25572,25578):port_free(p)
        empty=LAB/"kirik-telsiz-empty-mods";assert empty.is_dir() and not list(empty.iterdir())
        before=snapshot_profile((PROPS,VOICE))
        pack=LAB/WORLD/"datapacks/alcak-irtifa";install(pack)
        settings=("server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\nenforce-secure-profile=false\n"
                  "level-name="+WORLD+"\nlevel-type=minecraft:flat\ngenerate-structures=false\nallow-nether=false\n"
                  'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n'
                  "difficulty=peaceful\nmax-players=10\ngamemode=adventure\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nenable-rcon=false\nenable-query=false\n")
        s=None
        try:
            PROPS.write_text(settings,encoding="ascii")
            with (BASE/"acceptance.log").open("w",encoding="utf-8") as log:
                try:
                    s=Server(empty,log);s.wait("Done (",150)
                    print("PASS boot",flush=True)
                    s.send("function ai:reset","forceload add -80 -32 80 32")
                    time.sleep(2)
                    assert digest(BASE/"flightqa/flightqa.jar")=="4C95EBDEDCE5560F60DDA381CB0FF669B9ECCC34500C626BAFFA64E25FBC9827"
                    s.spawn(1);s.send("function ai:start")
                    s.check("if score #phase ai_ctrl matches 0","AI_ONE_REJECTED")
                    c=Client(1)
                    try:
                        s.wait(c.name+" joined the game",120)
                        s.send("gamemode adventure "+c.name,"function ai:start")
                        time.sleep(1)
                        s.check(f"if score #phase ai_ctrl matches 1 if items entity aiqa1 armor.chest elytra if items entity {c.name} armor.chest elytra if items entity aiqa1 hotbar.0 firework_rocket[count=16] if items entity {c.name} hotbar.0 firework_rocket[count=16]","AI_EQUAL_GEAR")
                        s.wait("Başla! Kapıları",10)
                        s.check("if score #phase ai_ctrl matches 2 if block -30 -26 -4 yellow_concrete if block 0 -33 8 orange_concrete","AI_COURSE_AND_START")
                        s.send("tp aiqa1 -30 -22 0");time.sleep(.5)
                        s.check("if score aiqa1 ai_gate matches 0","AI_NONFLYING_REJECTED")
                        s.send(f"tp {c.name} 0 -28 12 -90 0");time.sleep(1)
                        s.check(f"if score {c.name} ai_gate matches 0","AI_FUTURE_GATE_REJECTED")
                        # Real client glide activation via vanilla START_FALL_FLYING packet.
                        for i,(x,y,z) in enumerate(((-30,-22,0),(0,-29,12),(28,-36,-8),(52,-43,0))):
                            s.send(f"tp {c.name} {x-0.5} {y+1} {z+0.5} -90 0")
                            time.sleep(.8)
                            s.check(f"if score {c.name} ai_gate matches {i+1}",f"AI_NATIVE_GATE_{i+1}")
                        s.check(f"if score #phase ai_ctrl matches 3 if score #winners ai_ctrl matches 1 if entity @a[name={c.name},tag=ai.winner] if score {c.name} ai_time matches 1.. if score aiqa1 ai_gate matches 0","AI_NATIVE_FINISH")
                        lines=c.path.read_text(encoding="utf-8",errors="replace")
                        assert "AIQA_START_FALL_FLYING_PACKET" in lines and "AIQA_NATIVE_GLIDE" in lines
                        print("PASS native flight packet and client glide evidence",flush=True)
                        s.send("function ai:reset")
                    finally:c.close()
                    s.wait(c.name+" left the game")
                    for n in range(2,9):s.spawn(n)
                    s.send("function ai:start");time.sleep(1)
                    s.check("if score #count ai_ctrl matches 8 if score #online ai_ctrl matches 8","AI_EIGHT_PLAYERS")
                    s.send("function ai:reset");s.spawn(9);s.send("function ai:start")
                    s.check("if score #phase ai_ctrl matches 0","AI_NINE_REJECTED")
                    s.send("function ai:reset","gamemode spectator aiqa9","function ai:start")
                    s.wait("Başla! Kapıları",10)
                    s.send("scoreboard players set #left ai_ctrl 1");time.sleep(1)
                    s.check("if score #phase ai_ctrl matches 3 if score #winners ai_ctrl matches 0","AI_TIMEOUT")
                    s.send("function ai:reset")
                    for n in range(1,10):
                        s.send(f"player aiqa{n} kill");s.wait(f"aiqa{n} left the game")
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s:s.stop()
            logtext=(BASE/"acceptance.log").read_text(encoding="utf-8")
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",logtext)
            assert all((pack/"data/ai/function"/(k+".mcfunction")).read_text(encoding="utf-8")==v for k,v in generate().items())
            print("PASS installed functions and logs",flush=True)
        finally:
            for p,data in before.items():p.write_bytes(data)
            assert all(p.read_bytes()==data for p,data in before.items()) and [digest(p) for p in MODS]==EXPECTED
            print("PASS restored profiles and hashes",flush=True)
if __name__=="__main__":main()