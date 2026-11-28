"""Yaşayan Harita real server acceptance. Shared MOD lock, dedicated world."""
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
WORLD="yasayan-harita-lab"
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
        self.send(f"scoreboard players get #{k} yh_ctrl")
        line=self.wait(f"#{k} has ")
        return int(re.search(r"has (-?\d+) \[yh_ctrl\]",line)[1])
    def spawn(self,n):
        name=f"yhqa{n}"
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
        pack=LAB/WORLD/"datapacks/yasayan-harita";install(pack)
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
                    s.send("function yh:reset","forceload add -48 -32 48 32")
                    time.sleep(2)
                    s.spawn(1);s.send("function yh:start")
                    s.check("if score #phase yh_ctrl matches 0","YH_ONE_REJECTED")
                    s.spawn(2);s.send("function yh:start");time.sleep(2)
                    s.check("if score #phase yh_ctrl matches 1 if entity @a[name=yhqa1,team=yh_red] if entity @a[name=yhqa2,team=yh_blue]","YH_START")
                    s.check('if items entity yhqa1 hotbar.0 compass[minecraft:lodestone_tracker={target:{pos:[I;-24,-60,-8],dimension:"minecraft:overworld"},tracked:false}] if items entity yhqa2 hotbar.0 compass[minecraft:lodestone_tracker={target:{pos:[I;-24,-60,8],dimension:"minecraft:overworld"},tracked:false}]',"YH_INITIAL_COMPASSES")
                    s.send("tp yhqa1 -8 -60 -20","execute as yhqa1 run trigger yh_find")
                    time.sleep(1)
                    s.check("if score #red yh_ctrl matches 0 if score #blue yh_ctrl matches 0","YH_FUTURE_TARGET_REJECTED")
                    s.send("tp yhqa1 -24 -60 6","execute as yhqa1 run trigger yh_find");time.sleep(1)
                    s.check("if score #red yh_ctrl matches 0","YH_OTHER_TEAM_TARGET_REJECTED")
                    s.send("tp yhqa1 -24 -60 -6","execute as yhqa1 run trigger yh_find");time.sleep(1)
                    s.check("if score #red yh_ctrl matches 1 if score #blue yh_ctrl matches 0 if score yhqa1 yh_seen matches 1 if score yhqa2 yh_seen matches 0","YH_INDEPENDENT_PROGRESS")
                    s.check('if items entity yhqa1 hotbar.0 compass[minecraft:lodestone_tracker={target:{pos:[I;-8,-60,-22],dimension:"minecraft:overworld"},tracked:false}]',"YH_COMPASS_ADVANCED")
                    s.send("execute as yhqa1 run trigger yh_find");time.sleep(1)
                    s.check("if score #red yh_ctrl matches 1","YH_REPLAY_REJECTED")
                    s.spawn(3);time.sleep(1)
                    s.check("if entity @a[name=yhqa3,gamemode=spectator] unless score yhqa3 yh_epoch = #epoch yh_ctrl unless items entity yhqa3 hotbar.0 compass","YH_OUTSIDER")
                    s.send("player yhqa2 kill");s.wait("yhqa2 left the game");time.sleep(1)
                    left=s.get("left")
                    s.send("tp yhqa1 -8 -60 -20","scoreboard players set yhqa1 yh_find 1")
                    time.sleep(3)
                    assert s.get("left")==left and s.get("red")==1
                    print("PASS YH_PAUSED_NO_PROGRESS",flush=True)
                    s.spawn(2);time.sleep(2)
                    s.check("if score #red yh_ctrl matches 1 if score #blue yh_ctrl matches 0","YH_STALE_TRIGGER_REJECTED")
                    s.send("execute as yhqa1 run trigger yh_find");time.sleep(1)
                    s.check("if score #red yh_ctrl matches 2","YH_SECOND_TARGET")
                    s.send("tp yhqa1 12 -60 -6","execute as yhqa1 run trigger yh_find");time.sleep(1)
                    s.check("if score #red yh_ctrl matches 3","YH_THIRD_TARGET")
                    s.send("tp yhqa1 26 -60 -20","execute as yhqa1 run trigger yh_find");time.sleep(1)
                    s.check("if score #phase yh_ctrl matches 2 if score #red yh_ctrl matches 4 if score #winner yh_ctrl matches 1","YH_NATURAL_CHAIN_WIN")
                    s.send("function yh:reset")
                    s.check("if score #phase yh_ctrl matches 0 unless entity @a[tag=yh.member] unless items entity yhqa1 hotbar.0 compass","YH_RESET")
                    # Same tick completion: both teams' candidates are evaluated before a result.
                    s.send("gamemode spectator yhqa3","function yh:start");time.sleep(1)
                    s.send("scoreboard players set #red yh_ctrl 3","scoreboard players set #blue yh_ctrl 3")
                    time.sleep(1)
                    s.send("tp @a[team=yh_red] 26 -60 -20","tp @a[team=yh_blue] 26 -60 20",
                           "scoreboard players set @a[team=yh_red] yh_find 1","scoreboard players set @a[team=yh_blue] yh_find 1")
                    time.sleep(1)
                    s.check("if score #phase yh_ctrl matches 2 if score #red yh_ctrl matches 4 if score #blue yh_ctrl matches 4 if score #winner yh_ctrl matches 3","YH_SIMULTANEOUS_TIE")
                    s.send("function yh:reset","gamemode adventure yhqa3")
                    for n in range(4,9):s.spawn(n)
                    s.send("function yh:start");time.sleep(2)
                    s.check("if score #readyR yh_ctrl matches 4 if score #readyB yh_ctrl matches 4","YH_EIGHT_BALANCED")
                    s.send("function yh:reset");s.spawn(9);s.send("function yh:start")
                    s.check("if score #phase yh_ctrl matches 0","YH_NINE_REJECTED")
                    s.send("function yh:reset")
                    for n in range(1,10):
                        s.send(f"player yhqa{n} kill");s.wait(f"yhqa{n} left the game")
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s:s.stop()
            logtext=(BASE/"acceptance.log").read_text(encoding="utf-8")
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",logtext)
            assert all((pack/"data/yh/function"/(k+".mcfunction")).read_text(encoding="utf-8")==v for k,v in generate().items())
            print("PASS installed functions and logs",flush=True)
        finally:
            for p,data in before.items():p.write_bytes(data)
            assert all(p.read_bytes()==data for p,data in before.items()) and [digest(p) for p in MODS]==EXPECTED
            print("PASS restored profiles and hashes",flush=True)
if __name__=="__main__":main()