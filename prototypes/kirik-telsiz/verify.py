"""Real isolated Fabric server acceptance; no live or KOD test directories."""
from pathlib import Path
from queue import Queue, Empty
from threading import Thread
import hashlib, msvcrt, os, re, socket, subprocess, time, sys
from profile_guard import snapshot_profile
from prepare import install

ROOT = Path(__file__).resolve().parents[2]
LAB = ROOT / "_yedek/sovereign-uyum-20261125-221852/server"
WORLD = "kirik-telsiz-lab"
EXTENDED = "--extended" in sys.argv
LOG = ROOT / "prototypes/kirik-telsiz" / ("acceptance_extended.log" if EXTENDED else "acceptance.log")
JAVA = Path(os.environ["JAVA_HOME"]) / "bin/java.exe"
MODS = [LAB / "mods" / x for x in ("fabric-api-0.116.18+1.21.1.jar",
    "analog-1.2.2+1.21.jar", "voicechat-fabric-1.21.1-2.6.27.jar")]
MODS.append(ROOT / "_yedek/devletler-rts-prototip/laboratuvar/server/mods/fabric-carpet-1.21-1.4.147+v240613.jar")
PROPS = LAB / "server.properties"
VOICE = LAB / "config/voicechat/voicechat-server.properties"
EXPECTED = ["1CB46442DDA6D3BE9979A2619610EB1C1F6641CD7ED7CB3EEC058589CE9EED46",
 "0FAEEF984050724DAC008E6A84A00C71E83F41419C9F151D1325E2804A99E91D",
 "BAE2C4FA9F7764ABAC621EE70E1099BD39B7316CBC636280D188045B07F17AB1",
 "079FC8A4E073EB24B010FFCC588E59F93B984143E17D8EF19FBB0B6AFF0F19D9"]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()

def port_free(port, kind):
    with socket.socket(socket.AF_INET, kind) as s:
        if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
            s.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        s.bind(("127.0.0.1", port))

def main():
    assert [digest(x) for x in MODS] == EXPECTED, "Dependency hash mismatch"
    assert LAB.resolve() == LAB and not (LAB / WORLD).is_symlink()
    with (LAB / ".mctest.lock").open("a+b") as lock:
        lock.seek(0)
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        port_free(25578, socket.SOCK_STREAM)
        port_free(25579, socket.SOCK_DGRAM)
        for port in (25572,):
            port_free(port, socket.SOCK_STREAM)
        empty = LAB / "kirik-telsiz-empty-mods"
        empty.mkdir(exist_ok=True)
        assert not list(empty.iterdir())
        before = snapshot_profile((PROPS, VOICE))
        install(LAB / WORLD / "datapacks/kirik-telsiz")
        settings = ("server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\n"
            "enforce-secure-profile=false\nlevel-name="+WORLD+"\nlevel-type=minecraft:flat\n"
            "generate-structures=false\nallow-nether=false\nmax-players=10\n"
            'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n'
            "gamemode=adventure\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\n"
            "enable-rcon=false\nenable-query=false\n")
        voice = re.sub(rb"(?m)^port=.*$", b"port=25579", before[VOICE])
        voice = re.sub(rb"(?m)^bind_address=.*$", b"bind_address=127.0.0.1", voice)
        assert b"port=25579" in voice
        proc = None
        try:
            PROPS.write_text(settings, encoding="ascii")
            VOICE.write_bytes(voice)
            with LOG.open("w", encoding="utf-8") as log:
                proc = subprocess.Popen([str(JAVA), "-Xmx2G",
                    "-Dfabric.modsFolder="+str(empty),
                    "-Dfabric.addMods="+os.pathsep.join(map(str, MODS)),
                    "-jar", "fabric-server-launch.jar", "nogui"], cwd=LAB,
                    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, encoding="utf-8", errors="replace", bufsize=1)
                q = Queue()
                def reader():
                    with LOG.open("a", encoding="utf-8") as stream:
                        for line in proc.stdout:
                            stream.write(line); stream.flush(); q.put(line)
                t = Thread(target=reader, daemon=True); t.start()
                def send(*commands):
                    assert proc.poll() is None
                    proc.stdin.write("\n".join(commands)+"\n"); proc.stdin.flush()
                def wait(marker, timeout=25):
                    end = time.monotonic()+timeout
                    while time.monotonic()<end:
                        try: line = q.get(timeout=.5)
                        except Empty:
                            if proc.poll() is not None: break
                            continue
                        if marker in line: return line
                    raise RuntimeError("Missing marker: "+marker)
                def check(condition, marker):
                    send("execute "+condition+" run say "+marker, "say END_"+marker)
                    end = time.monotonic()+15
                    found=False
                    while time.monotonic()<end:
                        try: line=q.get(timeout=.5)
                        except Empty: continue
                        if line.rstrip().endswith("[Server] "+marker): found=True
                        if line.rstrip().endswith("[Server] END_"+marker): break
                    if not found: raise RuntimeError("FAIL "+marker)
                    print("PASS "+marker, flush=True)
                def get(name):
                    send("scoreboard players get "+name+" kt_ctrl")
                    line=wait(name+" has ")
                    m=re.search(r"has (-?\d+) \[kt_ctrl\]",line)
                    if not m: raise RuntimeError(line)
                    return int(m[1])
                def submit(name, value):
                    send(f"execute as {name} run trigger kt_code set {value}")
                    time.sleep(3.2)
                wait("Done (", timeout=150)
                print("PASS Fabric ready", flush=True)
                send("function kt:reset", "player ktqa1 spawn")
                wait("ktqa1 joined the game", timeout=30)
                send("player ktqa2 spawn")
                wait("ktqa2 joined the game", timeout=30)
                send("gamemode adventure ktqa1", "gamemode adventure ktqa2", "function kt:start")
                time.sleep(3)
                check("if score #phase kt_ctrl matches 1 if score #round kt_ctrl matches 1 if score #readyA kt_ctrl matches 1 if score #readyB kt_ctrl matches 1","KT_START_ROLES")
                check("if entity @a[name=ktqa1,tag=kt.player] if entity @a[name=ktqa2,tag=kt.player]","KT_REGISTERED")
                send("player ktqa3 spawn"); wait("ktqa3 joined the game")
                send("gamemode spectator ktqa3", "scoreboard players set ktqa3 kt_code 9999")
                time.sleep(2)
                check("if score #attempt kt_ctrl matches 3 unless entity @a[name=ktqa3,tag=kt.player]","KT_SPECTATOR_IGNORED")
                answer=get("#answer")
                submit("ktqa1", 1000 if answer!=1000 else 1001)
                check("if score #attempt kt_ctrl matches 2 if score #round kt_ctrl matches 1","KT_WRONG_CHARGED")
                send("gamemode spectator ktqa2")
                time.sleep(2)
                left=get("#left"); time.sleep(3)
                assert left==get("#left"), "Timer did not pause"
                print("PASS KT_ROLE_PAUSE",flush=True)
                send("gamemode adventure ktqa2"); time.sleep(3)
                assert get("#left")<left, "Timer did not resume"
                print("PASS KT_ROLE_RESUME",flush=True)
                submit("ktqa1",answer)
                check("if score #round kt_ctrl matches 2 if score #attempt kt_ctrl matches 3","KT_ROUND2")
                submit("ktqa2",get("#answer"))
                check("if score #round kt_ctrl matches 3","KT_ROUND3")
                submit("ktqa1",get("#answer"))
                check("if score #phase kt_ctrl matches 2","KT_WIN")
                send("function kt:reset")
                check("if score #phase kt_ctrl matches 0 unless entity @a[tag=kt.player]","KT_RESET")
                send("function kt:start"); time.sleep(3)
                ans=get("#answer")
                for _ in range(3):
                    submit("ktqa1",1000 if ans!=1000 else 1001)
                check("if score #phase kt_ctrl matches 3","KT_LOSS")
                if EXTENDED:
                    # All QA players are Carpet entities in the local-only lab.
                    send("function kt:reset", "gamemode spectator ktqa2")
                    send("function kt:start")
                    check("if score #phase kt_ctrl matches 0","KT_ONE_REJECTED")
                    send("gamemode adventure ktqa2","gamemode adventure ktqa3","function kt:start")
                    time.sleep(2)
                    check("if score #readyA kt_ctrl matches 2 if score #readyB kt_ctrl matches 1","KT_THREE_BALANCED")
                    ans=get("#answer")
                    send("scoreboard players set #cool kt_ctrl 20",
                         "scoreboard players set ktqa3 kt_code 9999", "scoreboard players set ktqa3 kt_hint 1", "player ktqa3 kill")
                    wait("ktqa3 left the game")
                    send("scoreboard players set #cool kt_ctrl 0")
                    time.sleep(2)
                    check("if score #readyA kt_ctrl matches 1 if score #readyB kt_ctrl matches 1","KT_EXTRA_ROLE_DISCONNECT")
                    submit("ktqa1",ans)
                    check("if score #round kt_ctrl matches 2","KT_OFFLINE_ROUND_ADVANCE")
                    check("unless score ktqa3 kt_code matches 1.. unless score ktqa3 kt_hint matches 1..","KT_OFFLINE_STALE_CODE_CLEARED")
                    send("player ktqa3 spawn")
                    wait("ktqa3 joined the game")
                    send("gamemode adventure ktqa3")
                    time.sleep(3)
                    check("if score #attempt kt_ctrl matches 3 if score #readyA kt_ctrl matches 2","KT_REJOIN_NO_STALE_GUESS")
                    send("player ktqa2 kill")
                    wait("ktqa2 left the game")
                    time.sleep(2)
                    left=get("#left")
                    send("scoreboard players set ktqa1 kt_code 9999")
                    time.sleep(3)
                    assert left==get("#left"),"Disconnect did not pause"
                    check("unless score ktqa1 kt_code matches 1..","KT_PAUSED_QUEUE_CLEARED")
                    print("PASS KT_DISCONNECT_PAUSE",flush=True)
                    send("player ktqa2 spawn")
                    wait("ktqa2 joined the game")
                    send("gamemode adventure ktqa2")
                    time.sleep(3)
                    assert get("#left")<left,"Reconnect did not resume"
                    print("PASS KT_RECONNECT_RESUME",flush=True)
                    send("scoreboard players set #left kt_ctrl 1")
                    time.sleep(2)
                    check("if score #phase kt_ctrl matches 3","KT_TIMEOUT")
                    send("function kt:reset")
                    for n in range(4,10):
                        send(f"player ktqa{n} spawn")
                        wait(f"ktqa{n} joined the game")
                        send(f"gamemode adventure ktqa{n}")
                    send("function kt:start")
                    check("if score #phase kt_ctrl matches 0","KT_NINE_REJECTED")
                    send("gamemode spectator ktqa9","function kt:start")
                    time.sleep(3)
                    check("if score #readyA kt_ctrl matches 4 if score #readyB kt_ctrl matches 4 if score #count kt_ctrl matches 8","KT_EIGHT_BALANCED")
                    check("unless entity @a[name=ktqa9,tag=kt.player]","KT_NINTH_SPECTATOR_EXCLUDED")
                    ans=get("#answer")
                    wrong=1000 if ans!=1000 else 1001
                    send(f"scoreboard players set ktqa1 kt_code {wrong}",f"scoreboard players set ktqa2 kt_code {wrong}")
                    time.sleep(2)
                    check("if score #attempt kt_ctrl matches 2","KT_SIMULTANEOUS_SINGLE_CHARGE")
                    send("scoreboard players set ktqa1 kt_code 99","scoreboard players set ktqa2 kt_code 10000")
                    time.sleep(3)
                    check("if score #attempt kt_ctrl matches 2","KT_INVALID_CODES_IGNORED")
                    send("player ktqa8 kill")
                    wait("ktqa8 left the game")
                    send("scoreboard players set ktqa8 kt_code 9999","function kt:reset","function kt:start")
                    time.sleep(2)
                    check("unless score ktqa8 kt_code matches 1..","KT_RESET_OFFLINE_SCORE_CLEARED")
                    send("player ktqa8 spawn")
                    wait("ktqa8 joined the game")
                    send("gamemode adventure ktqa8")
                    time.sleep(2)
                    check("unless score ktqa8 kt_epoch = #epoch kt_ctrl if score #attempt kt_ctrl matches 3","KT_OLD_EPOCH_EXCLUDED")
                    send("function kt:reset")
                    for n in range(3,10):
                        send(f"player ktqa{n} kill")
                    time.sleep(2)
                send("function kt:reset","stop")
                proc.wait(timeout=60); t.join(timeout=5)
                assert proc.returncode==0, proc.returncode
                assert "analog" in LOG.read_text(encoding="utf-8").lower()
                assert "voicechat" in LOG.read_text(encoding="utf-8").lower()
                assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function", LOG.read_text(encoding="utf-8")), "Server log contains errors"
                print("PASS clean stop",flush=True)
        finally:
            if proc and proc.poll() is None:
                try: proc.stdin.write("stop\n");proc.stdin.flush();proc.wait(timeout=45)
                except Exception: proc.terminate();proc.wait(timeout=10)
            for path, data in before.items(): path.write_bytes(data)
            assert all(path.read_bytes()==data for path,data in before.items())
            assert [digest(x) for x in MODS] == EXPECTED
            print("PASS restored profile settings and mod hashes",flush=True)

if __name__=="__main__":
    main()