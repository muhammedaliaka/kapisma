"""İnşa Et Savun real server acceptance. Shared MOD lock, dedicated world."""
from pathlib import Path
import sys, os, msvcrt, socket, subprocess, time, re
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
WORLD="insa-et-savun-lab"
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
        self.send(f"scoreboard players get #{k} is_ctrl")
        line=self.wait(f"#{k} has ")
        return int(re.search(r"has (-?\d+) \[is_ctrl\]",line)[1])
    def spawn(self,n):
        name=f"isqa{n}"
        self.send(f"player {name} spawn");self.wait(name+" joined the game");self.send("gamemode adventure "+name)
    def stop(self):
        if self.p.poll() is None:
            self.send("stop")
            try:self.p.wait(timeout=60)
            except subprocess.TimeoutExpired:self.p.terminate();self.p.wait(timeout=10)
        self.t.join(timeout=5)
        assert self.p.returncode==0,self.p.returncode

def main():
    assert [digest(p) for p in MODS]==EXPECTED
    assert LAB.resolve()==LAB and not (LAB/WORLD).is_symlink()
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for p in (25572,25578):port_free(p)
        empty=LAB/"kirik-telsiz-empty-mods";assert empty.is_dir() and not list(empty.iterdir())
        before=snapshot_profile((PROPS,VOICE))
        pack=LAB/WORLD/"datapacks/insa-et-savun";install(pack)
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
                    s.send("function is:reset","forceload add -48 -32 48 32")
                    time.sleep(2)
                    s.spawn(1);s.send("function is:start")
                    s.check("if score #phase is_ctrl matches 0","IS_ONE_REJECTED")
                    s.spawn(2);s.send("function is:start");time.sleep(2)
                    s.check("if score #phase is_ctrl matches 1 if score #leg is_ctrl matches 1 if entity @a[name=isqa1,tag=is.defender,gamemode=adventure] if entity @a[name=isqa2,tag=is.attacker,gamemode=spectator]","IS_BUILD_ROLES")
                    s.check('if items entity isqa1 hotbar.0 oak_planks[count=64] if items entity isqa1 hotbar.1 oak_planks[count=64] unless items entity isqa2 hotbar.0 oak_planks if block -11 -59 0 barrier if block 0 -53 0 barrier if block 0 -60 0 target',"IS_BUILD_MATERIALS")
                    s.send("tp isqa1 -5.5 -60 5.5 -90 45","player isqa1 use once");time.sleep(1)
                    s.check("if block -4 -60 5 oak_planks","IS_REAL_BLOCK_PLACEMENT")
                    s.send("player isqa2 kill");s.wait("isqa2 left the game");time.sleep(1)
                    left=s.get("left");time.sleep(2)
                    assert s.get("left")==left
                    s.check("if entity @a[name=isqa1,gamemode=spectator]","IS_BUILD_PAUSE_NO_BUILD")
                    s.spawn(2);time.sleep(2)
                    s.check("if entity @a[name=isqa1,gamemode=adventure] if entity @a[name=isqa2,gamemode=spectator]","IS_BUILD_RESUME")
                    s.send("scoreboard players set #left is_ctrl 1");time.sleep(2)
                    s.check("if score #phase is_ctrl matches 2 if entity @a[name=isqa1,gamemode=adventure] if entity @a[name=isqa2,gamemode=adventure] if block -4 -60 5 oak_planks if block -11 -59 0 air if block 0 -53 0 air","IS_ASSAULT_TRANSITION")
                    s.check("if items entity isqa2 hotbar.4 iron_axe unless items entity isqa1 hotbar.4 iron_axe unless items entity isqa1 hotbar.0 oak_planks","IS_ATTACKER_TOOLS")
                    s.send("item replace entity isqa2 hotbar.0 from entity isqa2 hotbar.4","tp isqa2 0.5 -60 2.5","player isqa2 look at 0.5 -59.5 0.5","player isqa2 attack continuous")
                    s.wait("Mavi inşa ediyor",20)
                    s.send("player isqa2 stop");time.sleep(1)
                    s.check("if score #leg is_ctrl matches 2 if score #phase is_ctrl matches 1 if score #blue is_ctrl matches 1.. if block 0 -60 0 target if block -4 -60 5 air","IS_REAL_CORE_BREAK_SWAP")
                    s.check('if entity @a[name=isqa2,tag=is.defender,gamemode=adventure] if entity @a[name=isqa1,tag=is.attacker,gamemode=spectator] if items entity isqa2 hotbar.0 oak_planks[count=64] if items entity isqa2 hotbar.1 oak_planks[count=64]',"IS_EQUAL_SECOND_BUILD")
                    s.send("scoreboard players set #left is_ctrl 1");time.sleep(2)
                    s.check("if score #phase is_ctrl matches 2 if items entity isqa1 hotbar.4 iron_axe unless items entity isqa2 hotbar.4 iron_axe","IS_ROLE_SWAP_TOOLS")
                    s.spawn(3);time.sleep(1)
                    s.check("if entity @a[name=isqa3,gamemode=spectator]","IS_OUTSIDER")
                    s.send("scoreboard players set #left is_ctrl 1");time.sleep(2)
                    s.check("if score #phase is_ctrl matches 3 if score #red is_ctrl matches 0 if score #winner is_ctrl matches 2","IS_TIMEOUT_BLUE_WIN")
                    s.send("function is:reset")
                    s.check("if score #phase is_ctrl matches 0 unless entity @a[tag=is.member] if entity @a[name=isqa3,gamemode=adventure]","IS_RESET")
                    for n in range(4,9):s.spawn(n)
                    s.send("function is:start");time.sleep(2)
                    s.check("if score #readyR is_ctrl matches 4 if score #readyB is_ctrl matches 4","IS_EIGHT_BALANCED")
                    s.send("function is:reset");s.spawn(9);s.send("function is:start")
                    s.check("if score #phase is_ctrl matches 0","IS_NINE_REJECTED")
                    s.send("function is:reset")
                    for n in range(1,10):
                        s.send(f"player isqa{n} kill");s.wait(f"isqa{n} left the game")
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s:s.stop()
            logtext=(BASE/"acceptance.log").read_text(encoding="utf-8")
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",logtext)
            assert all((pack/"data/is/function"/(k+".mcfunction")).read_text(encoding="utf-8")==v for k,v in generate().items())
            print("PASS installed functions and logs",flush=True)
        finally:
            for p,data in before.items():p.write_bytes(data)
            assert all(p.read_bytes()==data for p,data in before.items()) and [digest(p) for p in MODS]==EXPECTED
            print("PASS restored profiles and hashes",flush=True)
if __name__=="__main__":main()