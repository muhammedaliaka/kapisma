"""Kervan Baskını real server acceptance. Shared MOD lock, dedicated world."""
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
WORLD="kervan-baskini-lab"
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
            if marker in line:return line
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
        self.send(f"scoreboard players get #{k} kb_ctrl")
        line=self.wait(f"#{k} has ")
        return int(re.search(r"has (-?\d+) \[kb_ctrl\]",line)[1])
    def spawn(self,n):
        name=f"kbqa{n}"
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
        pack=LAB/WORLD/"datapacks/kervan-baskini";install(pack)
        settings=("server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\nenforce-secure-profile=false\n"
                  "level-name="+WORLD+"\nlevel-type=minecraft:flat\ngenerate-structures=false\nallow-nether=false\n"
                  'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n'
                  "difficulty=peaceful\nmax-players=10\ngamemode=adventure\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nenable-rcon=false\nenable-query=false\n")
        s=None
        try:
            PROPS.write_text(settings,encoding="ascii")
            with (BASE/"acceptance_natural.log").open("w",encoding="utf-8") as log:
                try:
                    s=Server(empty,log);s.wait("Done (",150)
                    print("PASS boot",flush=True)
                    s.send("function kb:reset","forceload add -48 -16 48 16")
                    time.sleep(2)
                    for n in range(1,4):s.spawn(n)
                    s.send("function kb:start");time.sleep(2)
                    s.check("if score #readyR kb_ctrl matches 2 if score #readyB kb_ctrl matches 1","KB_THREE_BALANCED")
                    s.send("player kbqa3 kill");s.wait("kbqa3 left the game")
                    # Whole first delivery with no score or clock edits.
                    t0=time.monotonic();last=-1
                    while s.get("phase")==1 and time.monotonic()-t0<90:
                        s.send("execute at @e[tag=kb.marker,limit=1] run tp kbqa1 ~ ~ ~")
                        time.sleep(2.5)
                        progress=s.get("progress")
                        assert progress>=last;last=progress
                    s.check("if score #phase kb_ctrl matches 3 if score #rProgress kb_ctrl matches 48 if score #rCargo kb_ctrl matches 20","KB_NATURAL_DELIVERY")
                    print("MEASURE delivery wall seconds %.2f"%(time.monotonic()-t0),flush=True)
                    s.wait("Tur 2: Mavi eskort",20)
                    s.spawn(3);time.sleep(1)
                    s.check("if score kbqa3 kb_leg matches 2 if entity @a[name=kbqa3,team=kb_red,gamemode=adventure] if items entity kbqa3 hotbar.0 iron_sword","KB_OFFLINE_ROLE_UPDATE")
                    s.check("in minecraft:overworld positioned -28 -60 8 if entity @a[name=kbqa3,distance=..2]","KB_OFFLINE_ROLE_BASE")
                    s.send("execute at @e[tag=kb.marker,limit=1] run tp kbqa1 ~ ~ ~")
                    time.sleep(3)
                    s.check("if score #cargo kb_ctrl matches ..18","KB_SECOND_LEG_DAMAGE")
                    # Save at a known paused checkpoint.
                    s.send("player kbqa2 kill");s.wait("kbqa2 left the game");time.sleep(2)
                    state={k:s.get(k) for k in ("phase","epoch","leg","cargo","progress","left","rProgress","rCargo","rTime")}
                    s.send("save-all flush");s.wait("Saved the game",40)
                    s.stop();s=None;print("PASS saved active checkpoint",flush=True)
                    log.write("\n=== RESTART ===\n");log.flush()
                    s=Server(empty,log);s.wait("Done (",150);time.sleep(2)
                    assert {k:s.get(k) for k in state}==state
                    s.check("if entity @e[tag=kb.marker,limit=1] if entity @e[tag=kb.visual,limit=1]","KB_RESTART_CARAVAN")
                    print("PASS KB_RESTART_STATE",flush=True)
                    s.spawn(1);s.spawn(3);time.sleep(2)
                    assert s.get("left")==state["left"]
                    print("PASS KB_RESTART_PAUSE",flush=True)
                    s.spawn(2);time.sleep(2)
                    assert s.get("left")<state["left"]
                    print("PASS KB_RESTART_RESUME",flush=True)
                    # Uncontested raider naturally destroys remaining cargo.
                    s.send("tp kbqa2 -28 -60 -8","execute at @e[tag=kb.marker,limit=1] run tp kbqa1 ~ ~ ~")
                    s.wait("Kırmızı takım kazandı",40)
                    s.check("if score #phase kb_ctrl matches 2 if score #bCargo kb_ctrl matches 0 if score #winner kb_ctrl matches 1","KB_NATURAL_DESTRUCTION")
                    s.send("function kb:reset")
                    for n in range(4,9):
                        s.spawn(n);s.send("function kb:start");time.sleep(1.4)
                        s.check(f"if score #readyR kb_ctrl matches {(n+1)//2} if score #readyB kb_ctrl matches {n//2}",f"KB_CAPACITY_{n}")
                        s.send("function kb:reset")
                    for n in range(1,9):
                        s.send(f"player kbqa{n} kill");s.wait(f"kbqa{n} left the game")
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s:s.stop()
            logtext=(BASE/"acceptance_natural.log").read_text(encoding="utf-8")
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",logtext)
            assert all((pack/"data/kb/function"/(k+".mcfunction")).read_text(encoding="utf-8")==v for k,v in generate().items())
            print("PASS installed functions and logs",flush=True)
        finally:
            for p,data in before.items():p.write_bytes(data)
            assert all(p.read_bytes()==data for p,data in before.items()) and [digest(p) for p in MODS]==EXPECTED
            print("PASS restored profiles and hashes",flush=True)
if __name__=="__main__":main()