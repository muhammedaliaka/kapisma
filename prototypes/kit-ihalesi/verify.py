"""Kit İhalesi real server acceptance. Shared MOD lock, dedicated world."""
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
WORLD="kit-ihalesi-lab"
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
        self.send(f"scoreboard players get #{k} ki_ctrl")
        line=self.wait(f"#{k} has ")
        return int(re.search(r"has (-?\d+) \[ki_ctrl\]",line)[1])
    def spawn(self,n):
        name=f"kiqa{n}"
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
        pack=LAB/WORLD/"datapacks/kit-ihalesi";install(pack)
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
                    s.send("function ki:reset","forceload add -32 -32 32 32")
                    time.sleep(2)
                    s.spawn(1);s.send("function ki:start")
                    s.check("if score #phase ki_ctrl matches 0","KI_ONE_REJECTED")
                    s.spawn(2);s.send("function ki:start");time.sleep(2)
                    s.check("if score #phase ki_ctrl matches 1 if score #round ki_ctrl matches 1 if score kiqa1 ki_coins matches 100 if score kiqa2 ki_coins matches 100","KI_EQUAL_CURRENCY")
                    s.send("execute as kiqa1 run trigger ki_bid set 60","execute as kiqa2 run trigger ki_bid set 40");time.sleep(1)
                    s.check("if score kiqa1 ki_offer matches 60 if score kiqa2 ki_offer matches 40 if score kiqa1 ki_coins matches 100 if score kiqa2 ki_coins matches 100","KI_SEALED_OFFERS_NO_PRECHARGE")
                    s.send("execute as kiqa1 run trigger ki_bid set 101");time.sleep(1)
                    s.check("if score kiqa1 ki_offer matches 60","KI_OVERBUDGET_REJECTED")
                    s.send("execute as kiqa2 run trigger ki_bid set -1");time.sleep(1)
                    s.check("if score kiqa2 ki_offer matches 0","KI_WITHDRAW")
                    s.send("execute as kiqa2 run trigger ki_bid set 40");time.sleep(1)
                    s.send("player kiqa2 kill");s.wait("kiqa2 left the game");time.sleep(2)
                    left=s.get("left");s.send("scoreboard players set kiqa1 ki_bid 99");time.sleep(2)
                    assert s.get("left")==left
                    s.check("if score kiqa1 ki_offer matches 60 unless score kiqa1 ki_bid matches 1..","KI_PAUSE_DISCARDS_PENDING")
                    s.spawn(2);time.sleep(1)
                    s.send("scoreboard players set #left ki_ctrl 1");time.sleep(2)
                    s.check("if score #round ki_ctrl matches 2 if score kiqa1 ki_coins matches 40 if score kiqa2 ki_coins matches 100 if items entity kiqa1 hotbar.0 iron_sword if items entity kiqa1 weapon.offhand shield unless items entity kiqa2 hotbar.0 iron_sword","KI_WINNER_ONLY_PAYS")
                    s.check("unless score kiqa1 ki_offer matches 1.. unless score kiqa2 ki_offer matches 1..","KI_ROUND_OFFERS_CLEARED")
                    s.send("execute as kiqa1 run trigger ki_bid set 30","execute as kiqa2 run trigger ki_bid set 30");time.sleep(1)
                    s.send("scoreboard players set #left ki_ctrl 1");time.sleep(2)
                    s.check("if score #round ki_ctrl matches 3","KI_TIE_RESOLVED")
                    s.send("scoreboard players operation #sum ki_ctrl = kiqa1 ki_coins","scoreboard players operation #sum ki_ctrl += kiqa2 ki_coins")
                    s.check("if score #sum ki_ctrl matches 110","KI_TIE_SINGLE_PAYMENT")
                    s.send("scoreboard players set #bows ki_ctrl 0","execute as @a[tag=ki.member] if items entity @s hotbar.1 bow run scoreboard players add #bows ki_ctrl 1")
                    s.check("if score #bows ki_ctrl matches 1","KI_TIE_SINGLE_KIT")
                    s.spawn(3);time.sleep(1)
                    s.check("if entity @a[name=kiqa3,gamemode=spectator] unless score kiqa3 ki_epoch = #epoch ki_ctrl","KI_OUTSIDER")
                    s.send("scoreboard players set #left ki_ctrl 1");time.sleep(2)
                    s.check("if score #phase ki_ctrl matches 2 if score #alive ki_ctrl matches 2 if score #sum ki_ctrl matches 110 unless items entity kiqa1 armor.head iron_helmet unless items entity kiqa2 armor.head iron_helmet","KI_NO_BIDS_NO_KIT_COMBAT")
                    s.send("player kiqa2 kill");s.wait("kiqa2 left the game");time.sleep(1)
                    s.check("if score #phase ki_ctrl matches 2 if score #alive ki_ctrl matches 2 if entity @a[name=kiqa1,gamemode=spectator]","KI_COMBAT_DISCONNECT_PAUSE")
                    s.spawn(2);time.sleep(1)
                    s.check("if entity @a[name=kiqa1,gamemode=adventure] if entity @a[name=kiqa2,gamemode=adventure]","KI_COMBAT_RESUME")
                    s.send("scoreboard players set kiqa2 ki_death 1");time.sleep(1)
                    s.check("if score #phase ki_ctrl matches 3 if score #winner ki_ctrl matches 1 if score #alive ki_ctrl matches 1 if score kiqa2 ki_alive matches 0","KI_SURVIVOR_WINS")
                    s.send("function ki:reset")
                    s.check("if score #phase ki_ctrl matches 0 unless entity @a[tag=ki.member] if entity @a[name=kiqa3,gamemode=adventure]","KI_RESET")
                    for n in range(4,9):s.spawn(n)
                    s.send("function ki:start");time.sleep(2)
                    s.check("if score #count ki_ctrl matches 8 if score #online ki_ctrl matches 8 if score #alive ki_ctrl matches 8","KI_EIGHT_PLAYERS")
                    s.send("function ki:reset");s.spawn(9);s.send("function ki:start")
                    s.check("if score #phase ki_ctrl matches 0","KI_NINE_REJECTED")
                    s.send("function ki:reset")
                    for n in range(1,10):
                        s.send(f"player kiqa{n} kill");s.wait(f"kiqa{n} left the game")
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s:s.stop()
            logtext=(BASE/"acceptance.log").read_text(encoding="utf-8")
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",logtext)
            assert all((pack/"data/ki/function"/(k+".mcfunction")).read_text(encoding="utf-8")==v for k,v in generate().items())
            print("PASS installed functions and logs",flush=True)
        finally:
            for p,data in before.items():p.write_bytes(data)
            assert all(p.read_bytes()==data for p,data in before.items()) and [digest(p) for p in MODS]==EXPECTED
            print("PASS restored profiles and hashes",flush=True)
if __name__=="__main__":main()