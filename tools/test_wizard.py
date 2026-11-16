#!/usr/bin/env python3
"""
Kurulum pencerelerinin (oyuncu + sunucu) testi, Windows PowerShell ile. Zip'leri temiz klasore acar:
 OYUNCU  : -SelfTest (dugmeye gercekten tiklar: tek dugme, tek kurulum, ikinci acilista kapali), -Headless kurulum:
           modlar <.minecraft>\\mods icine gider, eski surum _kapisma_eski'ye tasinir, baska modlara dokunulmaz, Fabric surumu
           eklenir, launcher profili eklenir; ikinci calistirma hicbir dosyayi degistirmez
 SUNUCU  : -SelfTest (tek dugme, tek ayar), -Headless ayar: izinli_oyuncular.txt, ram_mb.txt, eula.txt
 --boot  : ayarlanan sunucuyu gercekten ac, beyaz listenin uygulandigini dogrula (yavas)
Not: TLauncher'in kendisi ve UAC (guvenlik duvari) penceresi otomatik test EDILEMEZ.
Kullanim: python tools/test_wizard.py [--boot] [--full]   (--full: ses jar'li -tam paketler)
"""
import argparse, json, os, shutil, subprocess, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument("--dest", default=os.path.join(ROOT, "_yedek", "wizard_test"))
ap.add_argument("--boot", action="store_true")
ap.add_argument("--full", action="store_true", help="ses jar'li -tam paketleri dene (varsayilan: -tam; yoksa normal)")
a = ap.parse_args()
suffix = "-tam" if True else ""
def zpath(kind):
    for s in ("-tam", ""):
        p = os.path.join(ROOT, "release", f"kapisma-{kind}-v0.1.0{s}.zip")
        if os.path.isfile(p): return p
    sys.exit("zip yok: python tools/make_release.py --full --offline")

fails = []
def check(c, what):
    print(("== TAMAM: " if c else "!! BASARISIZ: ") + what)
    if not c: fails.append(what)

if os.path.exists(a.dest): shutil.rmtree(a.dest)
os.makedirs(a.dest)
for kind in ("oyuncu", "sunucu"):
    with zipfile.ZipFile(zpath(kind)) as z: z.extractall(a.dest)
pl = os.path.join(a.dest, "kapisma-oyuncu")
sv = os.path.join(a.dest, "kapisma-sunucu")

def ps(script, *args):
    r = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-STA", "-File", script, *args],
                       capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=240)
    return r.returncode, (r.stdout or "") + (r.stderr or "")

def tree(d):
    out = {}
    for r, _, fs in os.walk(d):
        for f in fs:
            p = os.path.join(r, f); out[os.path.relpath(p, d)] = (os.path.getsize(p), int(os.path.getmtime(p)))
    return out

# ------------------------------------------------------------------ paket sagligi
for d, ps1, bat in ((pl, "oyuncu.ps1", "Kapisma-Oyuncu-Kur.bat"), (sv, "sunucu.ps1", "Sunucu-Ayarla.bat")):
    p = os.path.join(d, ps1)
    check(os.path.isfile(p) and open(p, "rb").read(3) == b"\xef\xbb\xbf", f"{ps1}: var ve UTF-8 BOM'lu")
    check(b"@@" not in open(p, "rb").read(), f"{ps1}: yer tutucular dolduruldu")
    b = open(os.path.join(d, bat), "rb").read()
    check(b"\r\n" in b and all(c < 128 for c in b), f"{bat}: CRLF ve sadece ASCII")
top_pl = sorted(os.listdir(pl)); print("oyuncu paketi ust duzey:", top_pl)
check(len(top_pl) <= 8, "oyuncu paketinde az dosya var (sade)")

# ------------------------------------------------------------------ OYUNCU: GUI tiklama testi
gui_mc = os.path.join(a.dest, "gui mc"); os.makedirs(gui_mc)
rc, out = ps(os.path.join(pl, "oyuncu.ps1"), "-SelfTest", "-McDir", gui_mc)
check(rc == 0 and "SELFTEST OK" in out, "OYUNCU GUI: tek dugme / tek kurulum / ikinci acilista kapali: " + (out.strip().splitlines()[-1] if out.strip() else "cikti yok"))
check(os.path.isdir(os.path.join(gui_mc, "mods")) and any(f.endswith(".jar") for f in os.listdir(os.path.join(gui_mc, "mods"))), "OYUNCU GUI: dugme mods klasorune jar koydu")

# ------------------------------------------------------------------ OYUNCU: gercek kurulum (bosluklu yol, eski surum, yabanci mod)
mc = os.path.join(a.dest, "fake .minecraft")
os.makedirs(os.path.join(mc, "mods"))
open(os.path.join(mc, "mods", "fabric-api-0.100.0+1.21.1.jar"), "wb").write(b"old")
open(os.path.join(mc, "mods", "baska-mod.jar"), "wb").write(b"mine")
os.makedirs(os.path.join(mc, "versions", "Fabric 1.20.1"))          # 1.21.1 degil: yok sayilmali
json.dump({"profiles": {"x": {"name": "x", "lastVersionId": "1.21.1"}}, "version": 3}, open(os.path.join(mc, "launcher_profiles.json"), "w"))
open(os.path.join(pl, "sunucu_adresi.txt"), "w").write("# host yazar\n100.64.1.2\n")
rc, out = ps(os.path.join(pl, "oyuncu.ps1"), "-Headless", "-McDir", mc)
print(out)
check(rc == 0, "oyuncu kurulumu hatasiz bitti")
mods = os.listdir(os.path.join(mc, "mods"))
for need in ("kapisma-hud-0.1.0.jar", "fabric-api-0.116.18+1.21.1.jar", "voicechat-fabric-1.21.1-2.6.27.jar"):
    check(need in mods, f"DOGRU YER <.minecraft>\\mods icinde: {need}")
check(any(m.startswith("Origins") for m in mods), "DOGRU YER <.minecraft>\\mods icinde: Origins")
check(not any(f.endswith(".jar") for f in os.listdir(mc)), ".minecraft kokune jar dusmedi")
check("baska-mod.jar" in mods, "baska modlara dokunulmadi")
check(os.path.isfile(os.path.join(mc, "mods", "_kapisma_eski", "fabric-api-0.100.0+1.21.1.jar")), "eski surum silinmedi, _kapisma_eski'ye tasindi")
fj = os.path.join(mc, "versions", "fabric-loader-0.20.1-1.21.1", "fabric-loader-0.20.1-1.21.1.json")
check(os.path.isfile(fj) and json.load(open(fj, encoding="utf-8")).get("inheritsFrom") == "1.21.1", "Fabric 1.21.1 surumu eklendi")
lp = json.loads(open(os.path.join(mc, "launcher_profiles.json"), encoding="utf-8-sig").read())
check("kapisma" in lp["profiles"] and "x" in lp["profiles"], "resmi launcher profili eklendi, eskisi korundu")
before = tree(mc)
rc, out = ps(os.path.join(pl, "oyuncu.ps1"), "-Headless", "-McDir", mc)
check(rc == 0 and tree(mc) == before, "ikinci calistirma hicbir dosyayi degistirmedi / cogaltmadi")

# ------------------------------------------------------------------ SUNUCU
rc, out = ps(os.path.join(sv, "sunucu.ps1"), "-SelfTest")
check(rc == 0 and "SELFTEST OK" in out, "SUNUCU GUI: tek dugme / tek ayar: " + (out.strip().splitlines()[-1] if out.strip() else "cikti yok"))
for f in ("izinli_oyuncular.txt", "eula.txt"):
    pass
check("eula=true" in open(os.path.join(sv, "eula.txt")).read(), "SUNUCU GUI: EULA onayi yazildi")
rc, out = ps(os.path.join(sv, "sunucu.ps1"), "-Headless", "-Names", "Ahmet_34, Zeynep;Geçersiz Ad!", "-Ram", "3", "-AcceptEula", "-NoStart", "-NoFirewall")
print(out)
check(rc == 0, "sunucu ayari hatasiz bitti")
names = [l.strip() for l in open(os.path.join(sv, "izinli_oyuncular.txt"), encoding="utf-8") if l.strip() and not l.startswith("#")]
check(names == ["Ahmet_34", "Zeynep"], "izinli_oyuncular.txt: gecerli adlar yazildi, gecersiz atlandi: %s" % names)
check(open(os.path.join(sv, "ram_mb.txt")).read().strip() == "3072", "ram_mb.txt = 3072")
check(os.path.isfile(os.path.join(sv, "mods", "kapisma-0.1.0.jar")) and os.path.isfile(os.path.join(sv, "start.bat")), "sunucu dosyalari yerinde")

if a.boot:
    import glob, queue, threading, time
    p = os.path.join(sv, "server.properties")
    t = open(p, encoding="latin-1").read().replace("server-port=25565", "server-port=25585")
    open(p, "w", encoding="latin-1", newline="\n").write(t)
    os.makedirs(os.path.join(sv, "config", "voicechat"), exist_ok=True)
    open(os.path.join(sv, "config", "voicechat", "voicechat-server.properties"), "w").write("port=24465\n")
    cand = glob.glob(os.path.expandvars(r"%APPDATA%\.minecraft\runtime\java-runtime-delta\*\java-runtime-delta\bin\java.exe"))
    jv = cand[0] if cand else (shutil.which("java") or "java")
    pr = subprocess.Popen([jv, "-Xmx3G", "-jar", "fabric-server-launch.jar", "nogui"], cwd=sv, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    q = queue.Queue(); threading.Thread(target=lambda: [q.put(l) for l in pr.stdout], daemon=True).start()
    log = []; end = time.time() + 420
    while time.time() < end and not any("Done (" in l for l in log):
        try: log.append(q.get(timeout=1))
        except queue.Empty:
            if pr.poll() is not None: break
    try: pr.stdin.write("stop\n"); pr.stdin.flush(); pr.wait(60)
    except Exception: pr.kill()
    txt = "".join(log)
    check("Done (" in txt, "ayarlanan sunucu acildi")
    check("Izinli oyuncu listesi uygulandi: 2 oyuncu" in txt, "Boot beyaz listeyi uyguladi")
    wl = json.load(open(os.path.join(sv, "whitelist.json"), encoding="utf-8"))
    check(sorted(x["name"] for x in wl) == ["Ahmet_34", "Zeynep"], "whitelist.json adlari dogru")
    props = open(p, encoding="latin-1").read()
    check("white-list=true" in props and "enforce-whitelist=true" in props, "server.properties: beyaz liste acik")

print("\n=== KURULUM PENCERELERI TESTI: %s" % ("BASARILI" if not fails else "BASARISIZ -> " + "; ".join(fails)))
sys.exit(0 if not fails else 1)
