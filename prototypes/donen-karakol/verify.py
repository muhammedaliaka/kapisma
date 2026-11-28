"""Dönen Karakol real server acceptance. Shared MOD lock, dedicated world."""
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
WORLD="donen-karakol-lab"
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

def main():
    assert [digest(p) for p in MODS]==EXPECTED
    assert LAB.resolve()==LAB and not (LAB/WORLD).is_symlink()
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for p in (25572,25578):port_free(p)
        empty=LAB/"kirik-telsiz-empty-mods";assert empty.is_dir() and not list(empty.iterdir())
        before=snapshot_profile((PROPS,VOICE))
        pack=LAB/WORLD/"datapacks/donen-karakol";install(pack)
        settings=("server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\nenforce-secure-profile=false\n"
                  "level-name="+WORLD+"\nlevel-type=minecraft:flat\ngenerate-structures=false\nallow-nether=false\n"
                  'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n'
                  "difficulty=peaceful\nmax-players=10\ngamemode=adventure\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nenable-rcon=false\nenable-query=false\n")
        s=None
        try:
            PROPS.write_text(settings,encoding="ascii")
            with (BASE/"acceptance_v03.log").open("w",encoding="utf-8") as log:
                try:
                    s=Server(empty,log);s.wait("Done (",150)
                    print("PASS boot",flush=True)
                    s.send("function dk:reset","forceload add -48 -16 48 16")
                    time.sleep(2)
                    s.spawn(1);s.send("function dk:start")
                    s.check("if score #phase dk_ctrl matches 0","DK_ONE_REJECTED")
                    s.spawn(2);s.send("function dk:start");time.sleep(2)
                    s.check("if score #phase dk_ctrl matches 1 if score #readyR dk_ctrl matches 1 if score #readyB dk_ctrl matches 1","DK_START_BALANCED")
                    s.check("if entity @a[name=dkqa1,team=dk_red] if entity @a[name=dkqa2,team=dk_blue] if items entity dkqa1 armor.chest iron_chestplate if items entity dkqa2 armor.chest iron_chestplate if items entity dkqa1 weapon.offhand shield if items entity dkqa2 weapon.offhand shield if items entity dkqa1 hotbar.0 iron_sword if items entity dkqa2 hotbar.0 iron_sword","DK_EQUAL_KIT")
                    s.check("if block -24 -61 0 gold_block if block 0 -61 0 gray_concrete if block 24 -61 0 gray_concrete","DK_INITIAL_VISUAL")
                    s.send("tp dkqa1 60 -60 0");time.sleep(1)
                    s.check("in minecraft:overworld positioned 0 -60 -11 if entity @a[name=dkqa1,distance=..2]","DK_BOUNDARY_RETURN")
                    s.send("item replace entity dkqa1 hotbar.0 with dirt","scoreboard players set dkqa1 dk_death 1");time.sleep(2)
                    s.check("if score dkqa1 dk_death matches 0 if items entity dkqa1 hotbar.0 iron_sword if entity @a[name=dkqa1,team=dk_red]","DK_DEATH_COUNTER_REKIT")
                    s.send("tp dkqa1 -28 -60 -4");time.sleep(3)
                    s.check("if score #red dk_ctrl matches 2.. if score #blue dk_ctrl matches 0","DK_RED_SCORES")
                    s.send("tp dkqa2 -24 -60 0");time.sleep(2)
                    red=s.get("red");blue=s.get("blue");time.sleep(3)
                    assert s.get("red")==red and s.get("blue")==blue
                    print("PASS DK_CONTEST_PAUSES_SCORE",flush=True)
                    s.send("tp dkqa1 0 -60 0");time.sleep(3)
                    s.check(f"if score #red dk_ctrl matches {red} if score #blue dk_ctrl matches {blue+2}..","DK_BLUE_SCORES")
                    s.send("tp dkqa2 24 -60 10");time.sleep(2)
                    red=s.get("red");blue=s.get("blue")
                    time.sleep(2);assert s.get("red")==red and s.get("blue")==blue
                    print("PASS DK_INACTIVE_AND_EMPTY_NO_SCORE",flush=True)
                    s.send("scoreboard players set #left dk_ctrl 6");s.wait("Karakol 5 saniye sonra",10)
                    print("PASS DK_ROTATION_WARNING",flush=True)
                    s.send("scoreboard players set #left dk_ctrl 1");time.sleep(2)
                    s.check("if score #point dk_ctrl matches 1 if block -24 -61 0 gray_concrete if block 0 -61 0 gold_block if block 24 -61 0 gray_concrete","DK_ROTATES_CENTER")
                    s.send("scoreboard players set #left dk_ctrl 1");time.sleep(2)
                    s.check("if score #point dk_ctrl matches 2 if block 0 -61 0 gray_concrete if block 24 -61 0 gold_block","DK_ROTATES_EAST")
                    s.send("scoreboard players set #left dk_ctrl 1");time.sleep(2)
                    s.check("if score #point dk_ctrl matches 0 if block -24 -61 0 gold_block if block 24 -61 0 gray_concrete","DK_ROTATES_WRAP")
                    s.spawn(3);s.send("gamemode spectator dkqa3","tp dkqa3 -24 -60 0","tp dkqa1 -24 -60 0")
                    time.sleep(3)
                    s.check("if score #nearR dk_ctrl matches 1 if score #nearB dk_ctrl matches 0 unless score dkqa3 dk_epoch = #epoch dk_ctrl","DK_SPECTATOR_EXCLUDED")
                    s.send("gamemode adventure dkqa3");time.sleep(2)
                    s.check("if score #nearR dk_ctrl matches 1 if score #nearB dk_ctrl matches 0 unless score dkqa3 dk_epoch = #epoch dk_ctrl","DK_OLD_EPOCH_ACTIVE_EXCLUDED")
                    s.send("gamemode spectator dkqa3","player dkqa2 kill");s.wait("dkqa2 left the game");time.sleep(2)
                    left=s.get("left");red=s.get("red");time.sleep(3)
                    assert left==s.get("left") and red==s.get("red")
                    print("PASS DK_DISCONNECT_PAUSE",flush=True)
                    s.spawn(2);time.sleep(3)
                    s.check(f"if score #left dk_ctrl matches ..{left-1} if score #red dk_ctrl matches {red+1}..","DK_RECONNECT_RESUME")
                    s.send("scoreboard players set #red dk_ctrl 39");time.sleep(2)
                    s.check("if score #phase dk_ctrl matches 2 if score #red dk_ctrl matches 40","DK_RED_WIN")
                    s.check("if entity @a[name=dkqa1,gamemode=spectator] if entity @a[name=dkqa2,gamemode=spectator]","DK_FINISH_SPECTATOR")
                    left=s.get("left");time.sleep(2);assert s.get("left")==left and s.get("red")==40
                    print("PASS DK_FINISHED_FROZEN",flush=True)
                    s.send("function dk:start")
                    s.check("if score #phase dk_ctrl matches 2","DK_RESTART_WITHOUT_RESET_REJECTED")
                    s.send("function dk:reset")
                    s.check("if score #phase dk_ctrl matches 0 unless entity @a[tag=dk.member] unless entity @a[team=dk_red] unless entity @a[team=dk_blue] unless items entity dkqa1 hotbar.0 iron_sword","DK_RESET")
                    for n in range(3,9):
                        if n==3:s.send("gamemode adventure dkqa3")
                        else:s.spawn(n)
                    s.send("function dk:start");time.sleep(2)
                    s.check("if score #readyR dk_ctrl matches 4 if score #readyB dk_ctrl matches 4","DK_EIGHT_BALANCED")
                    s.send("function dk:reset");s.spawn(9);s.send("function dk:start")
                    s.check("if score #phase dk_ctrl matches 0","DK_NINE_REJECTED")
                    s.send("gamemode spectator dkqa9","function dk:start");time.sleep(2)
                    s.check("if score #readyR dk_ctrl matches 4 if score #readyB dk_ctrl matches 4 unless score dkqa9 dk_epoch = #epoch dk_ctrl","DK_NINTH_SPECTATOR_EXCLUDED")
                    s.send("tp @a[tag=dk.blue,limit=1] -24 -60 0","scoreboard players set #blue dk_ctrl 39");time.sleep(2)
                    s.check("if score #phase dk_ctrl matches 2 if score #blue dk_ctrl matches 40","DK_BLUE_WIN")
                    s.send("function dk:reset")
                    for n in range(1,10):
                        s.send(f"player dkqa{n} kill");s.wait(f"dkqa{n} left the game")
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s:s.stop()
            logtext=(BASE/"acceptance_v03.log").read_text(encoding="utf-8")
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",logtext)
            assert all((pack/"data/dk/function"/(k+".mcfunction")).read_text(encoding="utf-8")==v for k,v in generate().items())
            print("PASS installed functions and logs",flush=True)
        finally:
            for p,data in before.items():p.write_bytes(data)
            assert all(p.read_bytes()==data for p,data in before.items()) and [digest(p) for p in MODS]==EXPECTED
            print("PASS restored profiles and hashes",flush=True)
if __name__=="__main__":main()