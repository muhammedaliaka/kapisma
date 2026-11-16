#!/usr/bin/env python3
"""
Kapisma etkilesimli deneme sunucusu.
- Sunucuyu acar (Carpet sahte oyuncu modu GECICI yuklenir, kapaninca kaldirilir).
- Manhunt yeni dunya isterse (kapisma_next.json) sunucuyu tekrar acar; dunyayi mod icindeki preLaunch (tr.kapisma.boot.Boot) yeniler.
- Konsol komutlari tools/serve_cmds.txt dosyasina satir eklenerek verilir (dosya izlenir).
- Ozel komutlar:
    !bot <bot> <hedef>      botu ekler, hedef oyuncunun yanina alir, glowing + direnc verir, gezinmeyi baslatir
    !wander <bot> on|off    botun rastgele kosup ziplamasi
    !botfix <bot> <hedef>   bot oyundan dusmusse (olum) yeniden ekler
    !quit                   sunucuyu kapatir ve cikar
- Sunucu cikisi tools/serve.log dosyasina yazilir.
"""
import atexit, glob, os, random, shutil, subprocess, threading, time

HERE = os.path.dirname(os.path.abspath(__file__))
SERVER = os.path.join(os.path.dirname(HERE), "server")
CMDS = os.path.join(HERE, "serve_cmds.txt")
LOG = os.path.join(HERE, "serve.log")

open(CMDS, "w").close()
test_jars = glob.glob(os.path.join(HERE, "test-mods", "*.jar"))
copied = []
for j in test_jars:
    dst = os.path.join(SERVER, "mods", os.path.basename(j))
    shutil.copy(j, dst)
    copied.append(dst)


def cleanup():
    for c in copied:
        try:
            os.remove(c)
        except OSError:
            pass


atexit.register(cleanup)
java = glob.glob(os.path.expandvars(r"%APPDATA%\.minecraft\runtime\java-runtime-delta\*\java-runtime-delta\bin\java.exe"))[0]
logf = open(LOG, "w", encoding="utf-8", buffering=1)
state = {"p": None, "quit": False}
wanderers = {}          # bot adi -> threading.Event (calisiyor)


def send(line):
    p = state["p"]
    if p is None or p.poll() is not None:
        return
    try:
        p.stdin.write(line + "\n")
        p.stdin.flush()
    except OSError:
        pass


def wander_loop(bot, stop):
    acts = [
        "move forward", "move forward", "sprint", "jump", "jump", "move left", "move right",
        "stop", "move backward", "attack once",
    ]
    while not stop.is_set():
        r = random.random()
        if r < 0.35:
            send(f"player {bot} look {random.randint(-180, 180)} {random.randint(-20, 30)}")
        else:
            send(f"player {bot} {random.choice(acts)}")
        if random.random() < 0.25:
            send(f"player {bot} jump continuous")
        stop.wait(random.uniform(1.2, 3.5))
    send(f"player {bot} stop")
    send(f"player {bot} unjump")


def set_wander(bot, on):
    ev = wanderers.get(bot)
    if ev:
        ev.set()
        wanderers.pop(bot, None)
    if on:
        stop = threading.Event()
        wanderers[bot] = stop
        threading.Thread(target=wander_loop, args=(bot, stop), daemon=True).start()


def bot_setup(bot, target):
    send(f"player {bot} spawn")
    time.sleep(6)
    send(f"tp {bot} {target}")
    send(f"effect give {bot} minecraft:glowing infinite 0 true")
    send(f"effect give {bot} minecraft:resistance infinite 4 true")
    send(f"effect give {bot} minecraft:regeneration infinite 2 true")
    time.sleep(1)
    set_wander(bot, True)


def special(line):
    parts = line.split()
    cmd = parts[0]
    if cmd in ("!bot", "!botfix") and len(parts) >= 3:
        threading.Thread(target=bot_setup, args=(parts[1], parts[2]), daemon=True).start()
    elif cmd == "!wander" and len(parts) >= 3:
        set_wander(parts[1], parts[2] == "on")
    elif cmd == "!quit":
        state["quit"] = True
        send("stop")


def run_server():
    p = subprocess.Popen([java, *"-Xms6G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1".split(), "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-jar", "fabric-server-launch.jar", "nogui"],
                         cwd=SERVER, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, encoding="utf-8", errors="replace")
    state["p"] = p

    def pump():
        for line in p.stdout:
            logf.write(line)

    threading.Thread(target=pump, daemon=True).start()
    atexit.register(lambda: p.poll() is None and p.kill())
    return p


def main():
    pos = 0
    while True:
        p = run_server()
        while p.poll() is None:
            time.sleep(0.4)
            with open(CMDS, encoding="utf-8") as f:
                f.seek(pos)
                data = f.read()
                pos = f.tell()
            for line in data.splitlines():
                line = line.strip()
                if not line:
                    continue
                if line.startswith("!"):
                    special(line)
                else:
                    send(line)
        for ev in list(wanderers.values()):
            ev.set()
        wanderers.clear()
        nxt = os.path.join(SERVER, "kapisma_next.json")
        if not state["quit"] and os.path.exists(nxt):
            logf.write("[serve.py] yeni dunya istegi: sunucu yeniden aciliyor (preLaunch dunyayi yeniler)\n")
            continue
        break
    logf.write("[serve.py] sunucu kapandi\n")


main()
