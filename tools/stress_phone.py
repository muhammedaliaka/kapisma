#!/usr/bin/env python3
"""
Android (Termux) uzerinde 7 kisilik stres testi: telefon/tablet sunucu olarak denenir.
PC'den adb (USB) + SSH (adb forward 8022) ile yonetilir; sunucu start.sh ile, gercek kullanimdaki gibi baslar.
Sahte oyuncu: Carpet (tools/test-mods). Olcum: tick query (MSPT), "Can't keep up", bellek, pil/sicaklik.

Hazirlik (bir kere): telefonda Termux'ta  pkg install openssh openjdk-21 curl unzip ; sshd ; ~/.ssh/authorized_keys'e anahtar
Kullanim: python tools/stress_phone.py --key <ozel_anahtar> --eula [--profile auto|lite|normal] [--ram 3072] [--cores 0-3] [--bots 7]
  --eula   : Mojang EULA'sini SEN kabul ettigini onaylar (eula.txt'ye true yazilir)
  --cores  : taskset ile CPU cekirdeklerini kisitla (zayif tablet simulasyonu, ornek 0-3)
"""
import argparse, glob, os, re, statistics, subprocess, sys, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument("--key", required=True)
ap.add_argument("--port", default="8022")
ap.add_argument("--zip", default=os.path.join(ROOT, "release", "kapisma-sunucu-v0.1.0.zip"))
ap.add_argument("--profile", default="lite")
ap.add_argument("--ram", default="3072")
ap.add_argument("--cores", default="")
ap.add_argument("--bots", type=int, default=7)
ap.add_argument("--eula", action="store_true")
ap.add_argument("--scale", type=float, default=1.0, help="sure carpani (0.5 = yarim sure)")
ap.add_argument("--no-deploy", action="store_true")
a = ap.parse_args()

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
KH = os.path.join(os.path.dirname(os.path.abspath(a.key)), "kh")
SSHO = ["-i", a.key, "-o", "StrictHostKeyChecking=no", "-o", f"UserKnownHostsFile={KH}", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10"]
HOST = "kapisma@127.0.0.1"
SRV = "~/kapisma-sunucu"


def ssh(cmd, timeout=180):
    r = subprocess.run(["ssh", "-p", a.port, *SSHO, HOST, cmd], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (r.stdout or "") + (r.stderr or "")


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60).stdout


def battery():
    o = adb("shell", "dumpsys battery")
    lvl = re.search(r"level: (\d+)", o); tmp = re.search(r"temperature: (\d+)", o)
    return (int(lvl.group(1)) if lvl else -1), (int(tmp.group(1)) / 10 if tmp else -1)


def say(cmd):
    cmd = cmd.replace("'", "'\\''")
    ssh(f"echo '{cmd}' >> {SRV}/cmds")


offset = [0]
def new_log():
    o = ssh(f"tail -c +{offset[0] + 1} {SRV}/out.log")
    offset[0] += len(o.encode("utf-8", "replace"))
    return o


alllog = []
def pump():
    o = new_log()
    if o: alllog.append(o)
    return o


JPID = "ps -e -o pid,args | awk '/[b]in\\/java .*fabric-server-launch/ {print $1; exit}'"


def alive():
    return bool(ssh(JPID).strip())


rows = []
stalls = []
def sample(label):
    say("tick query")
    time.sleep(2.0)
    o = pump()
    m = re.search(r"Average time per tick: ([\d.]+)ms", o)
    p = re.search(r"P50: ([\d.]+)ms P95: ([\d.]+)ms P99: ([\d.]+)ms", o)
    st = ssh("awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo; ps -e -o rss,args 2>/dev/null | grep '[f]abric-server-launch' | awk '{print int($1/1024)}' | head -1; cat /proc/loadavg 2>/dev/null | cut -d' ' -f1").split()
    lvl, tmp = battery()
    row = dict(t=time.strftime("%H:%M:%S"), phase=label, mspt=float(m.group(1)) if m else None,
               p95=float(p.group(2)) if p else None, p99=float(p.group(3)) if p else None,
               avail=int(st[0]) if st else None, rss=int(st[1]) if len(st) > 1 else None,
               load=st[2] if len(st) > 2 else "?", bat=lvl, temp=tmp)
    rows.append(row)
    if not m:   # sunucu 2 sn icinde cevap vermedi = ana is parcacigi takili: ne yaptigini yakala
        d = ssh("P=$(" + JPID + "); jstack $P 2>&1 | awk '/^\"Server thread\"/{f=1} f&&/^$/{exit} f' | grep -v '^$' | head -24")
        stalls.append(row["t"] + "\n" + d)
        print("  !! ana parcacik takili; yigin kaydi alindi (%d satir)" % len(d.splitlines()), flush=True)
    print("  %s %-12s mspt=%s p95=%s p99=%s  bos_ram=%sMB java=%sMB yuk=%s pil=%%%s %s C" %
          (row["t"], label, row["mspt"], row["p95"], row["p99"], row["avail"], row["rss"], row["load"], lvl, tmp), flush=True)
    if (lvl != -1 and lvl <= 4 and "status: 3" in adb("shell", "dumpsys battery")) or tmp >= 46:
        print("!! PIL/SICAKLIK SINIRI: test durduruluyor"); raise SystemExit(3)
    if not alive():
        print("!! SUNUCU ONGORULMEDEN KAPANDI"); raise SystemExit(2)


def soak(sec, label, every=12):
    end = time.time() + sec * a.scale
    while time.time() < end:
        sample(label)
        time.sleep(max(0, every - 3))


# ---------------------------------------------------------------- dagitim
if not a.no_deploy:
    if not a.eula: sys.exit("--eula gerekli (Mojang EULA'sini kabul ettigini onayla)")
    print("== sunucu paketi telefona kopyalaniyor")
    ssh("pkill -f 'bin/[j]ava .*fabric-server-launch'; pkill -f 'tail -n0 -f [c]mds'; true")
    r = subprocess.run(["scp", "-P", a.port, *SSHO, a.zip, HOST + ":kapisma-sunucu.zip"], capture_output=True, text=True)
    if r.returncode: sys.exit("scp hata: " + r.stderr)
    carpet = glob.glob(os.path.join(ROOT, "tools", "test-mods", "*.jar"))[0]
    subprocess.run(["scp", "-P", a.port, *SSHO, carpet, HOST + ":carpet.jar"], check=True)
    print(ssh("cd ~ && rm -rf kapisma-sunucu && unzip -q kapisma-sunucu.zip && ls kapisma-sunucu | head -20 && "
              f"cd kapisma-sunucu && printf 'eula=true\\n' > eula.txt && "
              "cp ~/carpet.jar mods/ && : > cmds && echo DEPLOY_OK"))

print("== sunucu start.sh ile aciliyor (profil=%s, ram=%s MB, cekirdek=%s)" % (a.profile, a.ram, a.cores or "hepsi"))
ssh(f"cd {SRV} && echo {a.ram} > ram_mb.txt && " + ("rm -f profil.txt" if a.profile == "auto" else f"echo {a.profile} > profil.txt"))
pre = f"taskset -c {a.cores} " if a.cores else ""
ssh(f"cd {SRV} && : > cmds && : > out.log && (setsid nohup bash -c \"tail -n0 -f cmds | {pre}bash start.sh\" > out.log 2>&1 &) ; sleep 1; echo started")
t0 = time.time(); booted = False
while time.time() - t0 < 1200:
    time.sleep(10)
    o = pump()
    if o.strip(): print("   " + o.strip().splitlines()[-1][:150], flush=True)
    if any("Done (" in x for x in alllog): booted = True; break
    if not alive(): print("!! sunucu acilirken kapandi"); break
if not booted:
    print("".join(alllog)[-3000:]); sys.exit("sunucu acilmadi")
boot_s = time.time() - t0
txt = "".join(alllog)
print("== ACILDI: %.0f sn sonra  | %s" % (boot_s, (re.findall(r"Done \(([\d.]+)s\)", txt) or ["?"])[0] + " sn (sunucunun kendi olcumu)"))
print("   cekirdek siniri: " + ssh("taskset -cp $(" + JPID + ")").strip())
for l in txt.splitlines():
    if "Donanim profili" in l or "profil" in l.lower() and "LITE" in l: print("   " + l.strip()[:160]); break
time.sleep(3)

# ---------------------------------------------------------------- senaryo
N = a.bots
bots = [f"Bot{i}" for i in range(1, N + 1)]
print("== %d sahte oyuncu giriyor" % N)
for b in bots:
    say(f"player {b} spawn"); time.sleep(1.5)
time.sleep(5); sample("bos-lobi")
soak(30, "lobi-bekle", 15)

say("kapisma game UHC"); time.sleep(1)
say("kapisma rule UHC pvpGraceMin 0"); time.sleep(1)
print("== UHC basliyor (daginik dogus = en agir dunya uretimi)")
say("kapisma start")
soak(100, "uhc-dogus", 10)

print("== herkes farkli yone kosuyor (surekli yeni parca yukleme)")
for i, b in enumerate(bots):
    say(f"player {b} look {i * 360 // N} 0"); say(f"player {b} sprint"); say(f"player {b} move forward"); say(f"player {b} jump continuous")
    time.sleep(0.4)
soak(210, "kosu", 12)

print("== hepsi tek noktaya + surekli saldiri (toplu PvP + hile koruma)")
for b in bots[1:]:
    say(f"tp {b} {bots[0]}")
for b in bots:
    say(f"player {b} stop"); say(f"player {b} look 0 0"); say(f"player {b} attack continuous")
time.sleep(2)
soak(60, "toplu-pvp", 10)

say("kapisma status"); time.sleep(2); pump()
print("== durduruluyor")
say("stop"); time.sleep(20); pump()
ssh("pkill -f 'tail -n0 -f [c]mds'; true")
time.sleep(3); pump()

# ---------------------------------------------------------------- ozet
txt = "".join(alllog)
cant = re.findall(r"Can't keep up! .*? Running (\d+)ms or (\d+) ticks behind", txt)
errs = [l for l in txt.splitlines() if re.search(r"ERROR|Exception|FATAL|OutOfMemory", l) and "getScope" not in l]
koruma = [l for l in txt.splitlines() if "[koruma]" in l]
print("\n================ OZET ================")
by = {}
for r in rows:
    if r["mspt"] is not None: by.setdefault(r["phase"], []).append(r)
for ph, rs in by.items():
    ms = [r["mspt"] for r in rs]; p99 = [r["p99"] for r in rs if r["p99"] is not None]
    print("%-12s MSPT ort %.1f  en yuksek %.1f  p99 en yuksek %s   (50 ms = TPS 20 siniri)" % (ph, statistics.mean(ms), max(ms), ("%.1f" % max(p99)) if p99 else "?"))
allm = [r["mspt"] for r in rows if r["mspt"] is not None]
if allm:
    over = sum(1 for m in allm if m > 50)
    print("Genel: MSPT ort %.1f, en yuksek %.1f; 50 ms ustu olcum: %d/%d  -> %s" % (statistics.mean(allm), max(allm), over, len(allm),
          "TPS 20 KORUNDU" if over == 0 else "TPS DUSTU"))
print("Sunucu 'Can't keep up' uyarisi: %d kez%s" % (len(cant), (" (en kotu %d ms / %d tick geride)" % max((int(x), int(y)) for x, y in cant)) if cant else ""))
print("En yuksek java bellegi: %s MB | en dusuk bos RAM: %s MB" % (max([r["rss"] for r in rows if r["rss"]] or [0]), min([r["avail"] for r in rows if r["avail"]] or [0])))
if rows: print("Pil: %%%s -> %%%s | sicaklik %.1f -> en yuksek %.1f C" % (rows[0]["bat"], rows[-1]["bat"], rows[0]["temp"], max(r["temp"] for r in rows)))
print("Hata satiri: %d | [koruma] kaydi: %d | acilis: %.0f sn" % (len(errs), len(koruma), boot_s))
for l in errs[:8]: print("   " + l.strip()[:200])
open(os.path.join(ROOT, "_yedek", "stress_phone_%s.log" % time.strftime("%H%M%S")), "w", encoding="utf-8").write(txt)
if stalls:
    print("\nTAKILMA ANLARINDA ANA PARCACIK (jstack):")
    for s in stalls[:6]: print(s[:1800])
