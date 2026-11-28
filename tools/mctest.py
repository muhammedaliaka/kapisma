#!/usr/bin/env python3
"""
Kapisma test koşucusu: sunucuyu baslatir, senaryo dosyasindaki konsol komutlarini sirayla verir,
log'u (gurultu ayiklanmis) yazdirir ve sunucuyu kapatir.

Senaryo satirlari: "# yorum" | "wait <sn>" | "await <metin>" | "<konsol komutu>" | "expect <metin>"
Kullanim: python mctest.py senaryo.txt [--dir <sunucu klasoru>] [--boot-timeout 180]
"""
import argparse, atexit, glob, os, queue, re, socket, subprocess, sys, threading, time
from pathlib import Path

project = Path(__file__).resolve().parents[1]
ap = argparse.ArgumentParser()
ap.add_argument("scenario")
ap.add_argument("--dir", type=Path, default=project / "server_test")
ap.add_argument("--boot-timeout", type=int, default=180)
ap.add_argument("--xmx", default="3G")
ap.add_argument("--jvm", action="append", default=[])
ap.add_argument("--command-delay", type=float, default=1.5)
a = ap.parse_args()
a.dir = a.dir.resolve()
production = (project / "server").resolve()
if a.dir == production or production in a.dir.parents:
    ap.error("Test koşucusu kayıtları temizler. Canlı sunucu yerine ayrı bir test kopyasını seçin.")
if not (a.dir / "fabric-server-launch.jar").is_file():
    ap.error(f"Test sunucusu bulunamadı: {a.dir}")

# Temizlikten ÖNCE aynı test kopyasını kilitle. OS, süreç zorla kapanınca da kilidi bırakır.
lock = open(a.dir / ".mctest.lock", "a+b")
if lock.seek(0, os.SEEK_END) == 0:
    lock.write(b"\0")
    lock.flush()
lock.seek(0)
try:
    if os.name == "nt":
        import msvcrt
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
    else:
        import fcntl
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except OSError:
    ap.error("Bu test kopyasında başka bir test koşusu etkin.")
atexit.register(lock.close)
props = dict(line.split("=", 1) for line in (a.dir / "server.properties").read_text(encoding="utf-8").splitlines()
             if "=" in line and not line.startswith("#"))
try:
    with socket.create_connection((props.get("server-ip") or "127.0.0.1", int(props.get("server-port", 25565))), timeout=0.5):
        ap.error("Test sunucusu portu zaten kullanımda; kayıtlar temizlenmedi.")
except OSError:
    pass
sys.stdout.reconfigure(line_buffering=True)

# Testler uretim durumunu kirletmesin: ayar/istek dosyalarini kosudan once ve sonra temizle
def clean_state():
    for rel in ("config/kapisma/settings.json", "config/kapisma/used_seeds.json", "config/kapisma/scores.json", "kapisma_next.json", "world/kapisma_manhunt_used"):
        try: os.remove(os.path.join(a.dir, *rel.split("/")))
        except OSError: pass
    # Sahte oyuncunun kayitli verisi (yercekimi 0, izleyici modu vb.) sonraki senaryoyu bozmasin
    import shutil as _sh
    for d in ("playerdata", "stats", "advancements"):
        _sh.rmtree(os.path.join(a.dir, "world", d), ignore_errors=True)
clean_state()

# test modlari (Carpet: sahte oyuncu) gecici olarak yuklenir, bitince kaldirilir
test_jars = glob.glob(os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-mods", "*.jar"))
import shutil
for j in test_jars:
    shutil.copy(j, os.path.join(a.dir, "mods", os.path.basename(j)))

java = glob.glob(os.path.expandvars(r"%APPDATA%\.minecraft\runtime\java-runtime-delta\*\java-runtime-delta\bin\java.exe"))[0]
p = subprocess.Popen([java, f"-Xmx{a.xmx}", *a.jvm, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-jar", "fabric-server-launch.jar", "nogui"], cwd=a.dir,
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                     text=True, encoding="utf-8", errors="replace")
q = queue.Queue()
threading.Thread(target=lambda: [q.put(l) for l in p.stdout], daemon=True).start()
log = []
NOISE = re.compile(r"Preparing spawn area|Time elapsed|Starting minecraft server|Loaded \d+ (recipes|advancements)|"
                   r"Reloading|Re-drawing|HikariPool|database|Picked up|Generating keypair|Using default channel|"
                   r"OFFLINE/INSECURE|authenticate usernames|hackers|online-mode|Default game type|Loading properties|"
                   r"Preparing level|Preparing start region|Environment:|ServerMain|Finished|Registry contains|Reading |"
                   r"Applied \d+ biome|Validating|Found \d+ items|DataLoader|ObjectiveLoader|TierList|TagLoader|"
                   r"TeamPreset|SpawnKit|Unable to find|Missing item image")


atexit.register(lambda: p.poll() is None and p.kill())  # test cokerse sunucu acik kalmasin

def pump(sec):
    end = time.time() + sec
    while time.time() < end:
        try:
            l = q.get(timeout=0.25)
        except queue.Empty:
            if p.poll() is not None:
                return
            continue
        log.append(l)
        if not NOISE.search(l):
            sys.stdout.buffer.write((l if isinstance(l,str) else str(l)).encode("utf-8","replace")); sys.stdout.flush()


def wait_for(text, timeout, after=0):
    end = time.time() + timeout
    seen = after
    while time.time() < end:
        pump(0.5)
        for index in range(seen, len(log)):
            if text in log[index]:
                return index + 1
        seen = len(log)
        if p.poll() is not None:
            return False
    return False


ok = True
if not wait_for("Done (", a.boot_timeout):
    print("!! sunucu acilmadi"); ok = False
else:
    pump(2)
    await_cursor = 0
    command_cursor = 0
    variables = {}
    for raw in open(a.scenario, encoding="utf-8"):
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if p.poll() is not None:
            print("!! Sunucu senaryo bitmeden kapandı.")
            ok = False
            break
        try:
            line = re.sub(r"%\{([a-zA-Z_][a-zA-Z0-9_]*)\}", lambda m: variables[m.group(1)], line)
        except KeyError as exc:
            print("!! Tanımsız senaryo değişkeni:", exc)
            ok = False
            break
        if line.startswith("capture "):
            _, name, pattern = line.split(" ", 2)
            regex = re.compile(pattern)
            limit = time.monotonic() + 15
            found = None
            while time.monotonic() < limit and found is None:
                pump(0.25)
                found = next((m for entry in log[command_cursor:] if (m := regex.search(entry))), None)
                if p.poll() is not None:
                    break
            if found is None or regex.groups != 1:
                print("!! CAPTURE BAŞARISIZ:", name, pattern)
                ok = False
                break
            variables[name] = found.group(1)
            print("== tamam: capture", name, "=", variables[name])
            continue
        if line.startswith("wait "):
            pump(float(line.split()[1])); continue
        if line.startswith("await_regex "):
            pattern = line[len("await_regex "):]
            regex = re.compile(pattern)
            limit = time.monotonic() + 90
            # Match only after the previous awaited event; an automatic close may
            # occur before the next console command has been sent.
            seen = await_cursor
            matched = None
            while time.monotonic() < limit:
                pump(0.25)
                for i in range(seen, len(log)):
                    if regex.search(log[i]):
                        matched = i + 1
                        break
                if matched is not None or p.poll() is not None:
                    break
                seen = len(log)
            if matched is None:
                print("!! BEKLENEN REGEX YOK:", pattern)
                ok = False
                break
            await_cursor = matched
            print("== tamam: await_regex", pattern)
            continue
        if line.startswith("await "):
            want = line[6:]
            matched = wait_for(want, 90, await_cursor)
            if not matched:
                print("!! BEKLENEN YOK: " + want)
                ok = False
                break
            await_cursor = matched
            print("== tamam: " + want)
            continue
        if line.startswith("expect "):
            want = line[7:]
            if not any(want in l for l in log):
                print("!! BEKLENEN YOK: " + want); ok = False
            else:
                print("== tamam: " + want)
            continue
        command_cursor = len(log)
        print(">> " + line)
        try:
            p.stdin.write(line + "\n"); p.stdin.flush()
        except OSError:
            print("!! Sunucu kapandı, kalan komutlar atlandı.")
            ok = False
            break
        pump(a.command_delay)
    if p.poll() is not None:
        print("!! Sunucu beklenen stop komutundan önce kapandı.")
        ok = False
try:
    if p.poll() is None:
        p.stdin.write("stop\n"); p.stdin.flush(); p.wait(60)
except Exception as exc:
    print("!! Sunucu düzgün kapanmadı:", exc)
    ok = False
    p.kill()
    p.wait()
pump(1)
if p.returncode != 0:
    print("!! Sunucu çıkış kodu:", p.returncode)
    ok = False
clean_state()
for j in test_jars:
    try: os.remove(os.path.join(a.dir, "mods", os.path.basename(j)))
    except OSError: pass
errs = [l for l in log if re.search(r"ERROR|Exception|FATAL", l) and "getScope" not in l]
print("\n=== OZET: hata satiri = %d, senaryo %s" % (len(errs), "OK" if ok and not errs else "BASARISIZ"))
sys.exit(0 if ok and not errs else 1)
