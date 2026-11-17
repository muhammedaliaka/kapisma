#!/usr/bin/env python3
"""
Telefon/tablet sunucusunda jstack ornekleme ile profil: 7 sahte oyuncu, once bos lobide, sonra UHC kosusunda sunucu ana parcacigi
nerede zaman harcıyor? (tools/stress_phone.py ile ayni kurulum; sunucu paketi telefonda ~/kapisma-sunucu icinde olmali)
Kullanim: python tools/profile_phone.py --key <ozel_anahtar>
"""
import argparse, collections, os, re, subprocess, sys, time

ap = argparse.ArgumentParser()
ap.add_argument("--key", required=True)
ap.add_argument("--port", default="8022")
ap.add_argument("--bots", type=int, default=7)
ap.add_argument("--secs", type=int, default=45)
a = ap.parse_args()
KH = os.path.join(os.path.dirname(os.path.abspath(a.key)), "kh")
SSHO = ["-i", a.key, "-o", "StrictHostKeyChecking=no", "-o", f"UserKnownHostsFile={KH}", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10"]
SRV = "~/kapisma-sunucu"


def ssh(cmd, timeout=300):
    r = subprocess.run(["ssh", "-p", a.port, *SSHO, "kapisma@127.0.0.1", cmd], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (r.stdout or "") + (r.stderr or "")


def say(c):
    ssh(f"echo '{c}' >> {SRV}/cmds")


ssh("pkill -f 'bin/[j]ava .*fabric-server-launch'; pkill -f 'tail -n0 -f [c]mds'; sleep 2; true")
ssh(f"cd {SRV} && : > cmds && : > out.log && (setsid nohup bash -c 'tail -n0 -f cmds | bash start.sh' > out.log 2>&1 &) ; echo started")
for _ in range(120):
    time.sleep(5)
    if "Done (" in ssh(f"cat {SRV}/out.log"): break
else:
    sys.exit("sunucu acilmadi")
time.sleep(4)
for i in range(1, a.bots + 1):
    say(f"player Bot{i} spawn"); time.sleep(1.5)
time.sleep(15)
JPID = "ps -e -o pid,args | awk '/[b]in\\/java .*fabric-server-launch/ {print $1; exit}'"
pid = ssh(JPID).strip()


def profile(tag):
    # JFR Termux JVM'ini cokertiyor (Segmentation fault) -> jstack ile ornekleme (sunucu ana parcacigi)
    top = collections.Counter(); kinds = collections.Counter(); deep = collections.Counter(); n = 0
    end = time.time() + a.secs
    while time.time() < end:
        d = ssh(f"jstack {pid} 2>/dev/null | awk '/^\"Server thread\"/{{f=1;next}} f&&/^$/{{exit}} f' | grep '^[[:space:]]*at ' | head -60")
        fr = [re.sub(r"^\s*at\s+", "", l).split("(")[0] for l in d.splitlines()]
        if not fr: continue
        n += 1
        top[fr[0]] += 1
        if any("parkNanos" in f or "LockSupport" in f for f in fr[:3]) and any("class_1255" in f for f in fr[:6]): kinds["bos (sonraki tick'i bekliyor)"] += 1
        elif any(f.startswith("tr.kapisma") for f in fr): kinds["KAPISMA modu"] += 1
        elif any(f.startswith("carpet") for f in fr): kinds["Carpet (sahte oyuncu, sadece test)"] += 1
        else: kinds["Minecraft/diger"] += 1
        for f in fr:
            if f.startswith("tr.kapisma"):
                deep[f] += 1; break
    print(f"\n===== {tag}: ana parcacik {n} ornek =====")
    for k, v in kinds.most_common(): print("  %5.1f%%  %s" % (100 * v / max(n, 1), k))
    print("en sik ust cerceveler:")
    for k, v in top.most_common(8): print("  %5.1f%%  %s" % (100 * v / max(n, 1), k))
    if deep:
        print("bizim kod (yigindaki ilk tr.kapisma cercevesi):")
        for k, v in deep.most_common(8): print("  %5.1f%%  %s" % (100 * v / max(n, 1), k))


profile("bos_lobi")
say("kapisma game UHC"); time.sleep(1); say("kapisma rule UHC pvpGraceMin 0"); time.sleep(1); say("kapisma start")
time.sleep(110)
profile("uhc_baslangic")
for i in range(1, a.bots + 1):
    say(f"player Bot{i} look {i * 360 // a.bots} 0"); say(f"player Bot{i} sprint"); say(f"player Bot{i} move forward")
time.sleep(60)
profile("uhc_kosu")
say("stop"); time.sleep(20)
ssh("pkill -f 'tail -n0 -f [c]mds'; true")
