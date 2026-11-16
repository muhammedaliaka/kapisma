#!/usr/bin/env python3
"""
Kapisma test koşucusu: sunucuyu baslatir, senaryo dosyasindaki konsol komutlarini sirayla verir,
log'u (gurultu ayiklanmis) yazdirir ve sunucuyu kapatir.

Senaryo satirlari:  "# yorum" | "wait <sn>" | "<konsol komutu>" | "expect <metin>" (log'da gecmeli)
Kullanim: python mctest.py senaryo.txt [--dir <sunucu klasoru>] [--boot-timeout 180]
"""
import argparse, glob, os, queue, re, subprocess, sys, threading, time

ap = argparse.ArgumentParser()
ap.add_argument("scenario")
ap.add_argument("--dir", default=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "server"))
ap.add_argument("--boot-timeout", type=int, default=180)
ap.add_argument("--xmx", default="3G")
ap.add_argument("--jvm", action="append", default=[])
a = ap.parse_args()

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


import atexit
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


def wait_for(text, timeout):
    end = time.time() + timeout
    seen = 0
    while time.time() < end:
        pump(0.5)
        if any(text in l for l in log[seen:]):
            return True
        if p.poll() is not None:
            return False
    return False


ok = True
if not wait_for("Done (", a.boot_timeout):
    print("!! sunucu acilmadi"); ok = False
else:
    pump(2)
    for raw in open(a.scenario, encoding="utf-8"):
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line.startswith("wait "):
            pump(float(line.split()[1])); continue
        if line.startswith("expect "):
            want = line[7:]
            if not any(want in l for l in log):
                print("!! BEKLENEN YOK: " + want); ok = False
            else:
                print("== tamam: " + want)
            continue
        print(">> " + line)
        try:
            p.stdin.write(line + "\n"); p.stdin.flush()
        except OSError:
            print("(sunucu zaten kapandi, kalan komutlar atlandi)"); break
        pump(1.5)
try:
    if p.poll() is None:
        p.stdin.write("stop\n"); p.stdin.flush(); p.wait(60)
except Exception:
    p.kill()
pump(1)
clean_state()
for j in test_jars:
    try: os.remove(os.path.join(a.dir, "mods", os.path.basename(j)))
    except OSError: pass
errs = [l for l in log if re.search(r"ERROR|Exception|FATAL", l) and "getScope" not in l]
print("\n=== OZET: hata satiri = %d, senaryo %s" % (len(errs), "OK" if ok else "BASARISIZ"))
sys.exit(0 if ok and not errs else 1)
