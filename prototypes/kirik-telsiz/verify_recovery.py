"""Real Fabric natural timer and saved-checkpoint crash acceptance. Reuses MOD lab; never touches live Kapisma."""
from pathlib import Path
from queue import Queue, Empty
from threading import Thread
import msvcrt, os, re, socket, subprocess, time
from verify import ROOT, LAB, WORLD, JAVA, MODS, PROPS, VOICE, EXPECTED, digest, port_free
from profile_guard import snapshot_profile
from prepare import install, generate

LOG = ROOT / "prototypes/kirik-telsiz/acceptance_recovery.log"

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
                    s=Server(empty,log); s.wait("Done (",150)
                    print("PASS first boot",flush=True)
                    s.send("difficulty peaceful","function kt:reset")
                    s.spawn(1);s.spawn(2)
                    # Natural 90 server seconds: no scoreboard writes to the clock.
                    s.send("function kt:start")
                    s.wait("[KT] Yeni şifre hazır.")
                    t0=time.monotonic()
                    s.send("time query gametime"); line=s.wait("The time is "); tick0=int(re.search(r"The time is (\d+)",line)[1])
                    s.check("if score #phase kt_ctrl matches 1 if score #round kt_ctrl matches 1 if score #attempt kt_ctrl matches 3 if score #left kt_ctrl matches 89..90","KT_TIMER_INITIAL")
                    time.sleep(30)
                    mid=s.get("#left")
                    assert 0<mid<90,mid
                    s.check("if score #readyA kt_ctrl matches 1 if score #readyB kt_ctrl matches 1","KT_TIMER_ROLES_PRESENT")
                    print("PASS timer midpoint "+str(mid),flush=True)
                    s.wait("[KT] Görev başarısız.",timeout=160)
                    elapsed=time.monotonic()-t0
                    s.send("time query gametime"); line=s.wait("The time is "); tick1=int(re.search(r"The time is (\d+)",line)[1])
                    assert 1750<=tick1-tick0<=1820,(tick0,tick1)
                    print("PASS elapsed server ticks "+str(tick1-tick0),flush=True)
                    s.check("if score #phase kt_ctrl matches 3 if score #left kt_ctrl matches 0 if score #attempt kt_ctrl matches 3","KT_NATURAL_TIMEOUT")
                    print("MEASURE natural timer elapsed %.3f s"%elapsed,flush=True)
                    # Build a known checkpoint, then deliberately kill only the child Java process.
                    s.send("function kt:reset","function kt:start");time.sleep(3)
                    ans=s.get("#answer")
                    s.send(f"execute as ktqa1 run trigger kt_code set {ans}");time.sleep(3)
                    s.check("if score #round kt_ctrl matches 2","KT_CRASH_ROUND2")
                    ans=s.get("#answer")
                    wrong=1000 if ans!=1000 else 1001
                    s.send(f"execute as ktqa2 run trigger kt_code set {wrong}");time.sleep(3)
                    s.check("if score #attempt kt_ctrl matches 2","KT_CRASH_ATTEMPT")
                    s.send("player ktqa2 kill");s.wait("ktqa2 left the game");time.sleep(2)
                    left=s.get("#left");epoch=s.get("#epoch")
                    s.send("save-all flush");s.wait("Saved the game",timeout=40)
                    print("PASS explicit checkpoint saved",flush=True)
                    # An unsaved marker distinguishes saved checkpoint recovery from a clean stop.
                    s.send("scoreboard players set #crashprobe kt_ctrl 8123")
                    s.check("if score #crashprobe kt_ctrl matches 8123","KT_UNSAVED_MARKER")
                    pid=s.p.pid
                    s.p.kill();s.p.wait(timeout=20);s.t.join(timeout=5)
                    assert s.p.returncode!=0
                    print("PASS deliberate child kill pid=%d exit=%d"%(pid,s.p.returncode),flush=True)
                    s=None
                    log.write("\n=== AFTER FORCED TERMINATION ===\n");log.flush()
                    port_free(25578,socket.SOCK_STREAM);port_free(25579,socket.SOCK_DGRAM)
                    s=Server(empty,log);s.wait("Done (",150)
                    print("PASS recovery boot",flush=True)
                    time.sleep(3)
                    s.check(f"if score #phase kt_ctrl matches 1 if score #round kt_ctrl matches 2 if score #attempt kt_ctrl matches 2 if score #answer kt_ctrl matches {ans} if score #left kt_ctrl matches {left} if score #epoch kt_ctrl matches {epoch}","KT_CRASH_CHECKPOINT_STATE")
                    s.check("unless score #crashprobe kt_ctrl matches 8123","KT_UNSAVED_MARKER_ROLLED_BACK")
                    s.spawn(1);time.sleep(2)
                    s.check(f"if score #left kt_ctrl matches {left} if score #readyA kt_ctrl matches 1 if score #readyB kt_ctrl matches 0","KT_CRASH_PAUSE")
                    s.spawn(2);time.sleep(3)
                    s.check(f"if score #left kt_ctrl matches ..{left-1} if score #attempt kt_ctrl matches 2","KT_CRASH_RESUME")
                    s.send(f"execute as ktqa2 run trigger kt_code set {ans}");time.sleep(3)
                    s.check("if score #round kt_ctrl matches 3","KT_CRASH_ROUND3")
                    s.send(f"execute as ktqa1 run trigger kt_code set {s.get('#answer')}");time.sleep(3)
                    s.check("if score #phase kt_ctrl matches 2","KT_CRASH_WIN")
                    s.send("function kt:reset","scoreboard players reset #crashprobe kt_ctrl")
                    for n in (1,2):
                        s.send(f"player ktqa{n} kill");s.wait(f"ktqa{n} left the game")
                    s.stop();s=None
                    print("PASS final clean stop",flush=True)
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