#!/usr/bin/env python3
"""
Kurulum sihirbazi testi (Windows PowerShell): paketi temiz klasore acar ve sihirbazi `-Headless` modda calistirir.
 1) -SelfTest: GUI formu kuruluyor mu (sozdizimi + kontroller)
 2) Oyuncu: sahte .minecraft icinde mods kopyalanir, eski mod _kapisma_eski'ye tasinir, Fabric surumu eklenir,
    ses modu paketten gelir, servers.dat gecerli NBT, launcher_profiles.json'a profil eklenir
 3) Oyuncu tekrar: ikinci calistirma bozmaz (Fabric zaten var, servers.dat ezilmez)
 4) Sunucu: dosyalar hedefe kopyalanir, izinli_oyuncular.txt/ram_mb.txt/eula.txt yazilir, ev sahibi ayari ezilmez
 5) Sunucu gercekten acilir mi: izinli oyuncu listesi -> Boot beyaz listeyi yazar (whitelist.json, white-list=true)
Not: TLauncher'in kendisi ve UAC (guvenlik duvari) penceresi otomatik test EDILEMEZ.
Kullanim: python tools/test_wizard.py [--zip release/kapisma-kurulum-v0.1.0-tam.zip] [--boot]
"""
import argparse, json, os, re, shutil, struct, subprocess, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument("--zip", default=os.path.join(ROOT, "release", "kapisma-kurulum-v0.1.0-tam.zip"))
ap.add_argument("--dest", default=os.path.join(ROOT, "wizard_test"))
ap.add_argument("--boot", action="store_true", help="kurulan sunucuyu gercekten ac, beyaz listenin uygulandigini dogrula (yavas)")
a = ap.parse_args()

fails = []
def check(c, what):
    print(("== TAMAM: " if c else "!! BASARISIZ: ") + what)
    if not c: fails.append(what)

if os.path.exists(a.dest): shutil.rmtree(a.dest)
os.makedirs(a.dest)
with zipfile.ZipFile(a.zip) as z: z.extractall(a.dest)
pk = os.path.join(a.dest, "kapisma-kurulum")
ps1 = os.path.join(pk, "kurulum.ps1")
check(os.path.isfile(ps1) and open(ps1, "rb").read(3) == b"\xef\xbb\xbf", "kurulum.ps1 var ve UTF-8 BOM'lu")
bat = open(os.path.join(pk, "Kapisma-Kurulum.bat"), "rb").read()
check(b"\r\n" in bat and all(c < 128 for c in bat), "Kapisma-Kurulum.bat CRLF ve sadece ASCII")
check(b"@@" not in open(ps1, "rb").read(), "yer tutucular dolduruldu")

def ps(*args):
    r = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-STA", "-File", ps1, *args],
                       capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=180)
    return r.returncode, (r.stdout or "") + (r.stderr or "")

gui_mc = os.path.join(a.dest, "gui mc"); gui_sv = os.path.join(a.dest, "gui sunucu")
rc, out = ps("-SelfTest", "-McDir", gui_mc, "-ServerDir", gui_sv)
check(rc == 0 and "SELFTEST OK" in out, "GUI: sayfa gecisleri + Kur dugmeleri (dugmelere gercekten tiklanir): " + (out.strip().splitlines()[-1] if out.strip() else "cikti yok"))

# ---- oyuncu
mc = os.path.join(a.dest, "fake .minecraft")        # bosluk: yol kotu durum testi
os.makedirs(os.path.join(mc, "mods"))
open(os.path.join(mc, "mods", "fabric-api-0.100.0+1.21.1.jar"), "wb").write(b"old")
open(os.path.join(mc, "mods", "baska-mod.jar"), "wb").write(b"mine")
os.makedirs(os.path.join(mc, "versions", "Fabric 1.20.1"))     # 1.21.1 degil: yok sayilmali
json.dump({"profiles": {"x": {"name": "x", "lastVersionId": "1.21.1"}}, "version": 3}, open(os.path.join(mc, "launcher_profiles.json"), "w"))
rc, out = ps("-Headless", "-Role", "Player", "-McDir", mc, "-Address", "100.64.1.2")
print(out)
check(rc == 0, "oyuncu kurulumu hatasiz bitti")
mods = os.listdir(os.path.join(mc, "mods"))
for need in ("kapisma-hud-0.1.0.jar", "fabric-api-0.116.18+1.21.1.jar", "voicechat-fabric-1.21.1-2.6.27.jar"):
    check(need in mods, "mods icinde: " + need)
check(any(m.startswith("Origins") for m in mods), "mods icinde: Origins")
check("baska-mod.jar" in mods, "baska modlara dokunulmadi")
check(os.path.isfile(os.path.join(mc, "mods", "_kapisma_eski", "fabric-api-0.100.0+1.21.1.jar")), "eski fabric-api silinmedi, _kapisma_eski'ye tasindi")
fj = os.path.join(mc, "versions", "fabric-loader-0.20.1-1.21.1", "fabric-loader-0.20.1-1.21.1.json")
check(os.path.isfile(fj) and json.load(open(fj, encoding="utf-8")).get("inheritsFrom") == "1.21.1", "Fabric 1.21.1 surumu eklendi (inheritsFrom=1.21.1)")
lp = json.loads(open(os.path.join(mc, "launcher_profiles.json"), encoding="utf-8-sig").read())
check("kapisma" in lp["profiles"] and lp["profiles"]["kapisma"]["lastVersionId"] == "fabric-loader-0.20.1-1.21.1" and "x" in lp["profiles"], "resmi launcher profili eklendi, eskisi korundu")
check(os.path.isfile(os.path.join(mc, "launcher_profiles.json.kapisma_yedek")), "launcher_profiles.json yedeklendi")

def read_servers(path):
    b = open(path, "rb").read(); i = 0
    def u16():
        nonlocal i; v = struct.unpack(">H", b[i:i+2])[0]; i += 2; return v
    def s():
        nonlocal i; n = u16(); v = b[i:i+n].decode("utf-8"); i += n; return v
    assert b[i] == 10; i += 1; assert s() == ""
    assert b[i] == 9; i += 1; assert s() == "servers"; assert b[i] == 10; i += 1
    cnt = struct.unpack(">i", b[i:i+4])[0]; i += 4
    res = []
    for _ in range(cnt):
        d = {}
        while b[i] != 0:
            t = b[i]; i += 1; k = s(); assert t == 8; d[k] = s()
        i += 1; res.append(d)
    assert b[i] == 0 and i + 1 == len(b)
    return res
try:
    sv = read_servers(os.path.join(mc, "servers.dat"))
    check(sv == [{"name": "Kapisma", "ip": "100.64.1.2"}], "servers.dat gecerli NBT ve dogru icerik: %s" % sv)
except Exception as e:
    check(False, "servers.dat okunamadi: %r" % e)

# ikinci calistirma
open(os.path.join(mc, "servers.dat"), "ab").close()
before = open(os.path.join(mc, "servers.dat"), "rb").read()
rc, out = ps("-Headless", "-Role", "Player", "-McDir", mc, "-Address", "10.0.0.9")
check(rc == 0 and open(os.path.join(mc, "servers.dat"), "rb").read() == before, "ikinci kurulum: servers.dat ezilmedi")
check(len([m for m in os.listdir(os.path.join(mc, "versions")) if "fabric-loader" in m]) == 1, "ikinci kurulum: Fabric surumu cogalmadi")
lp = json.loads(open(os.path.join(mc, "launcher_profiles.json"), encoding="utf-8-sig").read())
check(list(lp["profiles"]).count("kapisma") == 1, "ikinci kurulum: profil cogalmadi")

# ---- sunucu
sd = os.path.join(a.dest, "benim sunucum")
rc, out = ps("-Headless", "-Role", "Server", "-ServerDir", sd, "-Names", "Ahmet_34, Zeynep;Geçersiz Ad!", "-Ram", "3", "-AcceptEula", "-NoStart", "-NoFirewall")
print(out)
check(rc == 0, "sunucu kurulumu hatasiz bitti")
for f in ("start.bat", "start.sh", "fabric-server-launch.jar", "server.properties", "guvenlik_duvari_ac.bat"):
    check(os.path.isfile(os.path.join(sd, f)), "sunucuda var: " + f)
check(os.path.isfile(os.path.join(sd, "mods", "kapisma-0.1.0.jar")), "sunucuda var: mods/kapisma-0.1.0.jar")
names = [l.strip() for l in open(os.path.join(sd, "izinli_oyuncular.txt"), encoding="utf-8") if l.strip() and not l.startswith("#")]
check(names == ["Ahmet_34", "Zeynep"], "izinli_oyuncular.txt: gecerli adlar yazildi, gecersiz atlandi: %s" % names)
check(open(os.path.join(sd, "ram_mb.txt")).read().strip() == "3072", "ram_mb.txt = 3072")
check("eula=true" in open(os.path.join(sd, "eula.txt")).read(), "eula.txt kullanici onayiyla true")
# ev sahibi ayari ezilmesin
open(os.path.join(sd, "server.properties"), "a").write("# benim notum\n")
rc, out = ps("-Headless", "-Role", "Server", "-ServerDir", sd, "-Names", "Ahmet_34", "-NoStart", "-NoFirewall")
check("# benim notum" in open(os.path.join(sd, "server.properties")).read(), "tekrar kurulum: server.properties ezilmedi")
check(os.path.getsize(os.path.join(sd, "mods", "kapisma-0.1.0.jar")) > 100000, "tekrar kurulum: mod jar'i tam")

# ---- sunucu gercekten acilir mi (beyaz liste)
if a.boot:
    p = os.path.join(sd, "server.properties")
    t = open(p, encoding="latin-1").read().replace("server-port=25565", "server-port=25585")
    open(p, "w", encoding="latin-1", newline="\n").write(t)
    os.makedirs(os.path.join(sd, "config", "voicechat"), exist_ok=True)
    open(os.path.join(sd, "config", "voicechat", "voicechat-server.properties"), "w").write("port=24465\n")
    open(os.path.join(sd, "izinli_oyuncular.txt"), "w").write("Ahmet_34\nZeynep\n")
    jv = shutil.which("java") or ""
    import glob
    cand = glob.glob(os.path.expandvars(r"%APPDATA%\.minecraft\runtime\java-runtime-delta\*\java-runtime-delta\bin\java.exe"))
    jv = cand[0] if cand else jv
    pr = subprocess.Popen([jv, "-Xmx3G", "-jar", "fabric-server-launch.jar", "nogui"], cwd=sd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    import threading, time, queue
    q = queue.Queue(); threading.Thread(target=lambda: [q.put(l) for l in pr.stdout], daemon=True).start()
    log = []; end = time.time() + 400
    while time.time() < end and not any("Done (" in l for l in log):
        try: log.append(q.get(timeout=1))
        except queue.Empty:
            if pr.poll() is not None: break
    try: pr.stdin.write("stop\n"); pr.stdin.flush(); pr.wait(60)
    except Exception: pr.kill()
    txt = "".join(log)
    check("Done (" in txt, "kurulan sunucu acildi")
    check("Izinli oyuncu listesi uygulandi: 2 oyuncu" in txt, "Boot beyaz listeyi uyguladi")
    wl = json.load(open(os.path.join(sd, "whitelist.json"), encoding="utf-8"))
    check(sorted(x["name"] for x in wl) == ["Ahmet_34", "Zeynep"], "whitelist.json adlari dogru")
    check("white-list=true" in open(p, encoding="latin-1").read() and "enforce-whitelist=true" in open(p, encoding="latin-1").read(), "server.properties: beyaz liste acik")

print("\n=== SIHIRBAZ TESTI: %s" % ("BASARILI" if not fails else "BASARISIZ -> " + "; ".join(fails)))
sys.exit(0 if not fails else 1)
