#!/usr/bin/env python3
"""
Sunucu paketini TEMIZ bir klasore acip gercek baslaticiyla (start.bat veya start.sh) ayaga kaldirir:
EULA onayi, Java bulma, ses modunun indirilip dogrulanmasi, ilk acilis seed secimi, donanim profili,
Manhunt "yeni dunya" dongusu (kapisma_next.json) ve temiz kapanis denenir.
Canli sunucudan ve run_all'dan ayri portlar kullanir (oyun 25580, ses 24460).

Kullanim: python tools/test_pack.py [--sh] [--lite] [--zip release/kapisma-sunucu-v0.1.0.zip]
"""
import argparse, glob, os, queue, re, shutil, subprocess, sys, threading, time, zipfile, hashlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument("--sh", action="store_true", help="start.sh'i (Git Bash) dene")
ap.add_argument("--lite", action="store_true", help="profil.txt = lite yaz")
ap.add_argument("--nojava", action="store_true", help="Java'siz makineyi taklit et: baslatici Java 21'i kendisi indirmeli (Windows)")
ap.add_argument("--zip", default=os.path.join(ROOT, "release", "kapisma-sunucu-v0.1.0.zip"))
ap.add_argument("--dest", default=os.path.join(ROOT, "_yedek", "pack_test"))
a = ap.parse_args()

dest = a.dest
if os.path.exists(dest):
    shutil.rmtree(dest)
os.makedirs(dest)
with zipfile.ZipFile(a.zip) as z:
    z.extractall(dest)
srv = os.path.join(dest, "kapisma-sunucu")
print("acildi:", srv)

# Testin canli sunucuyla cakismamasi icin portlar (paketin kendisi varsayilan 25565/24454 ile gelir)
p = os.path.join(srv, "server.properties")
t = open(p, encoding="latin-1").read().replace("server-port=25565", "server-port=25580")
open(p, "w", encoding="latin-1", newline="\n").write(t)
os.makedirs(os.path.join(srv, "config", "voicechat"), exist_ok=True)
open(os.path.join(srv, "config", "voicechat", "voicechat-server.properties"), "w").write("port=24460\n")
if a.lite:
    open(os.path.join(srv, "profil.txt"), "w").write("lite\n")

if a.sh:
    bash = shutil.which("bash") or r"C:\Program Files\Git\bin\bash.exe"
    cmd = [bash, "start.sh"]
else:
    cmd = ["cmd", "/c", os.path.join(srv, "start.bat")]
env = dict(os.environ)
if a.nojava:
    # PATH'te Java yok, JAVA_HOME yok, Minecraft calisma zamani klasoru (APPDATA) bos
    fake = os.path.join(dest, "fake_appdata")
    os.makedirs(fake, exist_ok=True)
    env["APPDATA"] = fake
    env.pop("JAVA_HOME", None)
    env.pop("JAVA_TOOL_OPTIONS", None)
    win = env.get("SystemRoot", r"C:\Windows")
    env["PATH"] = os.pathsep.join([os.path.join(win, "System32"), win, os.path.join(win, "System32", "WindowsPowerShell", "v1.0")])
proc = subprocess.Popen(cmd, cwd=srv, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True, encoding="utf-8", errors="replace")
q = queue.Queue()
threading.Thread(target=lambda: [q.put(l) for l in proc.stdout], daemon=True).start()
log = []


def pump(sec):
    end = time.time() + sec
    while time.time() < end:
        try:
            l = q.get(timeout=0.25)
        except queue.Empty:
            if proc.poll() is not None:
                return
            continue
        log.append(l)
        if "Picked up" not in l and "authlib" not in l:
            sys.stdout.buffer.write(l.encode("utf-8", "replace")); sys.stdout.flush()


def wait_for(text, timeout, start=0):
    end = time.time() + timeout
    while time.time() < end:
        pump(0.5)
        if any(text in l for l in log[start:]):
            return True
        if proc.poll() is not None:
            return any(text in l for l in log[start:])
    return False


def send(s):
    try:
        proc.stdin.write(s + "\n"); proc.stdin.flush()
    except OSError:
        pass


fails = []
def check(cond, what):
    print(("== TAMAM: " if cond else "!! BASARISIZ: ") + what)
    if not cond: fails.append(what)


atexit_kill = lambda: proc.poll() is None and proc.kill()
import atexit; atexit.register(atexit_kill)

# EULA sorusu satir sonu olmadan yazildigi icin beklemeden cevap veririz (boru tamponlar)
pump(3)
send("E")
check(any("Mojang EULA" in l for l in log), "EULA sorusu geldi")
if a.nojava:
    check(wait_for("Java 21 bulunamadi", 30), "Java'siz makine: baslatici Java'yi indirmeye basladi")
check(wait_for("Done (", 900 if a.nojava else 600), "sunucu acildi (Done)")
if a.nojava:
    check(os.path.isfile(os.path.join(srv, "runtime", "bin", "java.exe")), "runtime/bin/java.exe indirildi ve kullanildi")
pump(2)
txt = "".join(log)
check("Kapisma hazir" in txt, "Kapisma hazir mesaji")
check(re.search(r"Donanim profili: (LITE|NORMAL)", txt) is not None, "donanim profili yazildi")
if a.lite:
    check("Donanim profili: LITE" in txt, "profil.txt lite -> LITE profil")
    props = open(os.path.join(srv, "server.properties"), encoding="latin-1").read()
    check("view-distance=6" in props and "simulation-distance=4" in props, "LITE: gorus mesafesi kisildi")
check("Ilk acilis seed'i havuzdan secildi" in txt, "ilk acilis seed'i havuzdan secildi (preLaunch)")
vc = os.path.join(srv, "mods", "voicechat-fabric-1.21.1-2.6.27.jar")
check(os.path.isfile(vc), "ses modu mods klasorunde")
if os.path.isfile(vc):
    h = hashlib.sha512(open(vc, "rb").read()).hexdigest()
    check(h.startswith("4ab4e18279bb17b9"), "ses modu dogrulama ozeti dogru")
check(re.search(r"Voice ?[Cc]hat server (started|is running|listening).*24460|started at port 24460|24460", txt) is not None, "ses sunucusu 24460 UDP'de basladi")
props = open(os.path.join(srv, "server.properties"), encoding="latin-1").read()
m1 = re.search(r"^level-seed=(-?\d+)$", props, re.M)
check(m1 is not None, "level-seed yazildi: " + (m1.group(1) if m1 else "?"))

# Manhunt "yeni dunya" dongusu
n0 = len(log)
send("kapisma newworld")
check(wait_for("Done (", 240, n0), "yeni dunya istegi sonrasi sunucu yeniden acildi")
pump(2)
txt2 = "".join(log[n0:])
check("Yeni dunya: seed=" in txt2, "preLaunch yeni dunyayi uyguladi")
props = open(os.path.join(srv, "server.properties"), encoding="latin-1").read()
m2 = re.search(r"^level-seed=(-?\d+)$", props, re.M)
check(m2 is not None and (m1 is None or m2.group(1) != m1.group(1)), "yeni seed farkli: " + (m2.group(1) if m2 else "?"))
check(not os.path.exists(os.path.join(srv, "kapisma_next.json")), "kapisma_next.json tuketildi")

# temiz kapanis
n1 = len(log)
send("stop")
if not a.sh:
    wait_for("Sunucu kapandi", 90, n1)
    send("")          # pause
else:
    wait_for("Sunucu kapandi", 90, n1)
try:
    proc.wait(60)
except subprocess.TimeoutExpired:
    proc.kill(); fails.append("kapanmadi")
pump(1)
errs = [l for l in log if re.search(r"ERROR|Exception|FATAL", l) and "authlib" not in l and "Failed to read value" not in l
        and not l.startswith("Caused by: com.google.gson") and "IllegalStateException: Expected BEGIN_OBJECT" not in l
        and "errorref" not in l and "MinecraftClientException" not in l]
print("hata satirlari:", len(errs))
for l in errs[:10]:
    print("   ", l.rstrip())
if errs:
    fails.append("log'da hata var")
print("\n=== PAKET TESTI: %s" % ("BASARILI" if not fails else "BASARISIZ -> " + "; ".join(fails)))
sys.exit(0 if not fails else 1)
