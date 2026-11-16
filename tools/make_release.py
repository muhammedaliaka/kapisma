#!/usr/bin/env python3
"""
Kapisma yayin paketlerini uretir (release/ klasorune):
  kapisma-sunucu-vX.zip   sunucu paketi (start.bat/start.sh, mod jar'lari, ayarlar)
  kapisma-istemci-vX.zip  oyuncu paketi (mod jar'lari + kurulum.bat/.sh)
  SHA256SUMS.txt
Zip'ler jar'lari oldugu gibi icerir (jar'lar acilmaz/yeniden paketlenmez).

Simple Voice Chat "All Rights Reserved" lisansli oldugu icin varsayilan paketlere KONMAZ; start/kurulum
dosyalari onu Modrinth'ten indirir ve SHA-512 ile dogrular. Sadece kendi cihazlarin arasinda dagitmak icin
  python tools/make_release.py --full
ile jar'in icinde oldugu "-tam" paketler de uretilir (bunlari herkese acik yerlere YUKLEME).

Kullanim:  python tools/make_release.py [--full] [--offline]
"""
import hashlib, json, os, shutil, stat, sys, time, urllib.request, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "release")
VERSION = "0.1.0"
FULL = "--full" in sys.argv
OFFLINE = "--offline" in sys.argv

MODS = {
    "kapisma": os.path.join(ROOT, "mod", "kapisma", "build", "libs", f"kapisma-{VERSION}.jar"),
    "kapisma-hud": os.path.join(ROOT, "mod", "kapisma-hud", "build", "libs", f"kapisma-hud-{VERSION}.jar"),
    "fabric-api": os.path.join(ROOT, "server", "mods", "fabric-api-0.116.18+1.21.1.jar"),
    "origins": os.path.join(ROOT, "server", "mods", "Origins-1.13.0-pre.3+mc.1.21.1.jar"),
    "voicechat": os.path.join(ROOT, "server", "mods", "voicechat-fabric-1.21.1-2.6.27.jar"),
}
LAUNCHER = os.path.join(ROOT, "server", "fabric-server-launch.jar")
SEEDS = os.path.join(ROOT, "server", "config", "kapisma", "seeds.json")
VC_FILE = os.path.basename(MODS["voicechat"])
VC_PROJECT = "9eGKb6K1"          # Modrinth: simple-voice-chat
VC_VERSION_ID = "dyhq1XyO"       # fabric-1.21.1-2.6.27
VC_URL = f"https://cdn.modrinth.com/data/{VC_PROJECT}/versions/{VC_VERSION_ID}/{VC_FILE}"


def sha(path, algo):
    h = hashlib.new(algo)
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


for k, p in {**MODS, "launcher": LAUNCHER, "seeds": SEEDS}.items():
    if not os.path.isfile(p):
        sys.exit(f"EKSIK DOSYA ({k}): {p}")

VC_SHA512 = sha(MODS["voicechat"], "sha512")
if not OFFLINE:
    # sabitledigimiz indirme adresi gercekten bu dosyayi veriyor mu? (Modrinth API ile dogrula)
    req = urllib.request.Request(f"https://api.modrinth.com/v2/version/{VC_VERSION_ID}", headers={"User-Agent": "kapisma-pack-builder"})
    meta = json.load(urllib.request.urlopen(req, timeout=30))
    files = [f for f in meta["files"] if f["filename"] == VC_FILE]
    if not files or files[0]["hashes"]["sha512"] != VC_SHA512 or files[0]["url"] != VC_URL:
        sys.exit("Modrinth'teki ses modu dosyasi yerel jar ile uyusmuyor; VC_* sabitlerini guncelle.")
    print("ses modu sabiti dogrulandi (Modrinth sha512 eslesiyor)")

SUBST = {"@@VC_FILE@@": VC_FILE, "@@VC_URL@@": VC_URL, "@@VC_SHA512@@": VC_SHA512}
NOTICE = f"""KAPISMA - ucuncu taraf bilesenler ve lisanslar
==============================================
Kapisma (sunucu modu) ve kapisma-hud (istemci modu): MIT (LICENSE-MIT.txt)
Fabric API 0.116.18: Apache-2.0  https://github.com/FabricMC/fabric
fabric-server-launch.jar (Fabric Installer 1.1.2 sunucu baslatici): Apache-2.0  https://github.com/FabricMC/fabric-installer
Origins 1.13.0-pre.3 (Apoli, Calio, PAL, Cardinal Components vb. gomulu): MIT  https://github.com/apace100/origins-fabric
Fantasy 0.6.4 (NucleoidMC) ve sgui 1.6.1 (Patbox): LGPL-3.0; Kapisma jar'inin icine DEGISTIRILMEDEN gomulu (jar-in-jar),
   kaynak: https://github.com/NucleoidMC/fantasy , https://github.com/Patbox/sgui
Simple Voice Chat {VC_FILE.split('-')[-1].replace('.jar','')} (Max Henkel): All Rights Reserved. Pakete konmaz; yazarin
   dagitim adresinden indirilir ve SHA-512 ile dogrulanir: {VC_URL}
   Proje: https://modrinth.com/plugin/simple-voice-chat
Minecraft: Mojang Studios. Sunucu dosyalari ilk acilista Mojang'dan indirilir; kullanmak icin Minecraft EULA
   (https://aka.ms/MinecraftEULA) kabul edilmelidir. Bu proje Mojang/Microsoft ile bagli degildir.
Carpet (test icin) pakette YOKTUR.
"""
MIT = """MIT License

Copyright (c) 2026 Kapisma contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
"""

STAMP = (2026, 11, 16, 12, 0, 0)


def add_bytes(z, arcname, data, mode=0o644):
    zi = zipfile.ZipInfo(arcname, STAMP)
    zi.compress_type = zipfile.ZIP_STORED if arcname.endswith(".jar") else zipfile.ZIP_DEFLATED
    zi.external_attr = (stat.S_IFREG | mode) << 16
    z.writestr(zi, data)


def add_file(z, arcname, path, mode=0o644):
    with open(path, "rb") as f:
        add_bytes(z, arcname, f.read(), mode)


def add_text(z, arcname, src_path, mode=0o644):
    """Sablon dosyasi: @@...@@ yer tutucularini doldurur; .bat -> CRLF, digerleri -> LF."""
    with open(src_path, encoding="utf-8") as f:
        t = f.read()
    for k, v in SUBST.items():
        t = t.replace(k, v)
    t = t.replace("\r\n", "\n")
    if arcname.endswith((".bat", ".cmd", ".txt", ".ps1")):
        t = t.replace("\n", "\r\n")
    if arcname.endswith(".ps1"):
        t = "\ufeff" + t          # Windows PowerShell 5.1 Turkce karakterleri BOM ile UTF-8 okur
    left = [k for k in SUBST if k in t]
    assert not left, (arcname, left)
    add_bytes(z, arcname, t.encode("utf-8"), mode)


def info(kind, include_vc):
    lines = [f"Kapisma {kind} paketi v{VERSION}  ({time.strftime('%Y-%m-%d')})", "Minecraft 1.21.1 / Fabric Loader 0.20.1", "", "Dosyalar (SHA-256):"]
    for name in ("kapisma", "kapisma-hud", "fabric-api", "origins") + (("voicechat",) if include_vc else ()):
        if kind == "sunucu" and name == "kapisma-hud":
            continue
        if kind == "istemci" and name == "kapisma":
            continue
        p = MODS[name]
        lines.append(f"  {os.path.basename(p)}  {sha(p, 'sha256')}")
    if kind in ("sunucu", "kurulum"):
        lines.append(f"  fabric-server-launch.jar  {sha(LAUNCHER, 'sha256')}")
    if not include_vc:
        lines += ["", f"Ses modu pakette degil; ilk calistirmada indirilir (SHA-512 dogrulamali): {VC_FILE}"]
    return "\r\n".join(lines) + "\r\n"


def write_server(z, top, include_vc):
    src = os.path.join(ROOT, "pack", "server")
    for fn in ("start.bat", "guvenlik_duvari_ac.bat", "adres_goster.bat", "BENI_OKU.txt", "eula.txt", "server.properties", "izinli_oyuncular.txt"):
        add_text(z, top + fn, os.path.join(src, fn))
    add_text(z, top + "start.sh", os.path.join(src, "start.sh"), 0o755)
    add_file(z, top + "fabric-server-launch.jar", LAUNCHER)
    for k in ("kapisma", "fabric-api", "origins") + (("voicechat",) if include_vc else ()):
        add_file(z, top + "mods/" + os.path.basename(MODS[k]), MODS[k])
    add_file(z, top + "config/kapisma/seeds.json", SEEDS)


def write_client(z, top, include_vc):
    src = os.path.join(ROOT, "pack", "client")
    add_text(z, top + "kurulum.bat", os.path.join(src, "kurulum.bat"))
    add_text(z, top + "BENI_OKU.txt", os.path.join(src, "BENI_OKU.txt"))
    add_text(z, top + "kurulum.sh", os.path.join(src, "kurulum.sh"), 0o755)
    for k in ("kapisma-hud", "fabric-api", "origins") + (("voicechat",) if include_vc else ()):
        add_file(z, top + "mods/" + os.path.basename(MODS[k]), MODS[k])


def write_legal(z, top, kind, include_vc):
    add_bytes(z, top + "LICENSES/NOTICE.txt", NOTICE.replace("\n", "\r\n").encode("utf-8"))
    add_bytes(z, top + "LICENSES/LICENSE-MIT.txt", MIT.replace("\n", "\r\n").encode("utf-8"))
    add_bytes(z, top + "PAKET_BILGISI.txt", info(kind, include_vc).encode("utf-8"))


def build_server(include_vc):
    path = os.path.join(OUT, f"kapisma-sunucu-v{VERSION}" + ("-tam" if include_vc else "") + ".zip")
    with zipfile.ZipFile(path, "w") as z:
        write_server(z, "kapisma-sunucu/", include_vc)
        write_legal(z, "kapisma-sunucu/", "sunucu", include_vc)
    return path


def build_client(include_vc):
    path = os.path.join(OUT, f"kapisma-istemci-v{VERSION}" + ("-tam" if include_vc else "") + ".zip")
    with zipfile.ZipFile(path, "w") as z:
        write_client(z, "kapisma-istemci/", include_vc)
        write_legal(z, "kapisma-istemci/", "istemci", include_vc)
    return path


def build_wizard(include_vc):
    """Tek zip: sihirbaz (Windows) + sunucu + istemci. Cift tikla, rol sec, kurulur."""
    path = os.path.join(OUT, f"kapisma-kurulum-v{VERSION}" + ("-tam" if include_vc else "") + ".zip")
    top = "kapisma-kurulum/"
    src = os.path.join(ROOT, "pack", "wizard")
    with zipfile.ZipFile(path, "w") as z:
        add_text(z, top + "Kapisma-Kurulum.bat", os.path.join(src, "Kapisma-Kurulum.bat"))
        add_text(z, top + "kurulum.ps1", os.path.join(src, "kurulum.ps1"))
        add_text(z, top + "BENI_OKU.txt", os.path.join(src, "BENI_OKU.txt"))
        add_file(z, top + "istemci/fabric-loader-0.20.1-1.21.1.json", os.path.join(src, "fabric-loader-0.20.1-1.21.1.json"))
        write_server(z, top + "sunucu/", include_vc)
        write_client(z, top + "istemci/", include_vc)
        write_legal(z, top, "kurulum", include_vc)
    return path


os.makedirs(OUT, exist_ok=True)
for f in os.listdir(OUT):
    if f.endswith(".zip") or f == "SHA256SUMS.txt":
        os.remove(os.path.join(OUT, f))
made = [build_server(False), build_client(False), build_wizard(False)]
if FULL:
    made += [build_server(True), build_client(True), build_wizard(True)]
with open(os.path.join(OUT, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\n") as f:
    for p in made:
        f.write(f"{sha(p, 'sha256')}  {os.path.basename(p)}\n")
for p in made:
    print(f"{os.path.basename(p):42s} {os.path.getsize(p) / 1048576:6.1f} MB")

# Herkese acik dagitim klasoru (git'e eklenir): ses jar'i ICERMEYEN paketler
DIST = os.path.join(ROOT, "dist")
os.makedirs(DIST, exist_ok=True)
for f in os.listdir(DIST):
    if f.endswith(".zip") or f == "SHA256SUMS.txt":
        os.remove(os.path.join(DIST, f))
pub = [p for p in made if not p.endswith("-tam.zip")]
for p in pub:
    shutil.copy(p, DIST)
with open(os.path.join(DIST, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\n") as f:
    for p in pub:
        f.write(f"{sha(p, 'sha256')}  {os.path.basename(p)}\n")
print("dist/ guncellendi:", ", ".join(os.path.basename(p) for p in pub))
