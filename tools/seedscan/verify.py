#!/usr/bin/env python3
"""
Kapisma - Asama 2: adaylari GERCEK 1.21.1 sunucusunda dogrular.

Her aday seed icin vanilla sunucu acilir (modsuz, ayni dunya uretimi) ve konsoldan sunlar sorulur:
  - setworldspawn          -> oyundaki gercek spawn
  - execute if biome ~ ~ ~ -> spawn biyomu cubiomes'in dedigi gibi mi
  - locate structure #village / locate biome river -> en yakin koy ve nehir

Kabul: spawn biyomu tutar, en yakin koy <= --max-dist, en yakin nehir <= --max-dist.
Cikti: verified.jsonl (kabul), rejected.jsonl (red + neden). Yeniden calistirilabilir (islenen seed atlanir).

Kullanim: python verify.py adaylar.jsonl --workers 4 [--max-dist 900] [--limit N]
"""
import argparse, glob, json, os, queue, re, shutil, subprocess, sys, threading, time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))                    # .minecraft/kapisma
VANILLA_JAR = os.path.join(ROOT, "server", ".fabric", "server", "1.21.1-server.jar")
WORK = os.path.join(ROOT, "tools", "verify")
JAVA = glob.glob(os.path.expandvars(
    r"%APPDATA%\.minecraft\runtime\java-runtime-delta\*\java-runtime-delta\bin\java.exe"))[0]

PROPS = """server-port={port}
online-mode=false
level-seed={seed}
level-name=world
view-distance=2
simulation-distance=2
max-players=1
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
generate-structures=true
enable-status=false
allow-nether=false
sync-chunk-writes=false
"""

RE_SPAWN = re.compile(r"world spawn point to \(?(-?\d+), (-?\d+), (-?\d+)")
RE_LOC = re.compile(r"is at \[(-?\d+), [^,]+, (-?\d+)\] \((\d+) blocks away\)")


def mc_biome_id(name):
    return name if ":" in name else "minecraft:" + name


class Worker:
    def __init__(self, idx):
        self.idx = idx
        self.dir = os.path.join(WORK, "w%d" % idx)
        self.port = 25600 + idx
        os.makedirs(self.dir, exist_ok=True)
        dst = os.path.join(self.dir, "server.jar")
        if not os.path.exists(dst):
            shutil.copy(VANILLA_JAR, dst)
        with open(os.path.join(self.dir, "eula.txt"), "w") as f:   # EULA kullanici tarafindan kabul edildi
            f.write("eula=true\n")

    def run(self, cand, debug=False):
        seed = cand["seed"]
        shutil.rmtree(os.path.join(self.dir, "world"), ignore_errors=True)
        with open(os.path.join(self.dir, "server.properties"), "w") as f:
            f.write(PROPS.format(port=self.port, seed=seed))
        p = subprocess.Popen([JAVA, "-Xms512M", "-Xmx1536M", "-jar", "server.jar", "nogui"],
                             cwd=self.dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        q = queue.Queue()
        threading.Thread(target=lambda: [q.put(l) for l in p.stdout], daemon=True).start()
        lines = []

        def wait_for(pred, timeout):
            end = time.time() + timeout
            while time.time() < end:
                try:
                    l = q.get(timeout=0.3)
                except queue.Empty:
                    if p.poll() is not None:
                        return None
                    continue
                lines.append(l)
                if pred(l):
                    return l
            return None

        def cmd(c, pred, timeout=40):
            p.stdin.write(c + "\n"); p.stdin.flush()
            return wait_for(pred, timeout)

        res = {"seed": seed, "ok": False}
        try:
            if not wait_for(lambda l: "Done (" in l, 120):
                res["why"] = "sunucu acilmadi"; return res
            l = cmd("setworldspawn", lambda l: "world spawn" in l or "Could not" in l)
            m = RE_SPAWN.search(l or "")
            if not m:
                res["why"] = "spawn okunamadi: %r" % l; return res
            gx, gz = int(m.group(1)), int(m.group(3))
            res["game_spawn"] = [gx, gz]
            res["spawn_drift"] = max(abs(gx - cand["spawn"][0]), abs(gz - cand["spawn"][1]))

            tag = "SPAWNBIOME_%d" % seed
            cmd("execute if biome ~ ~ ~ %s run say %s" % (mc_biome_id(cand["biome"]), tag),
                lambda l: tag in l, 6)
            res["biome_match"] = any(tag in x and "[Server]" in x for x in lines)

            l = cmd("locate structure #minecraft:village", lambda l: "nearest" in l or "Could not" in l, 60)
            m = RE_LOC.search(l or "")
            res["village"] = [int(m.group(1)), int(m.group(2)), int(m.group(3))] if m else None
            l = cmd("locate biome minecraft:river", lambda l: "nearest" in l or "Could not" in l, 60)
            m = RE_LOC.search(l or "")
            res["river"] = [int(m.group(1)), int(m.group(2)), int(m.group(3))] if m else None
            res["ok"] = True
        finally:
            try:
                if p.poll() is None:
                    p.stdin.write("stop\n"); p.stdin.flush()
                    p.wait(40)
            except Exception:
                p.kill()
            if debug:
                sys.stderr.write("".join(lines[-25:]))
            shutil.rmtree(os.path.join(self.dir, "world"), ignore_errors=True)
        return res


def judge(cand, res, max_dist):
    """kabul/red karari: (kabul_mu, neden)"""
    if not res.get("ok"):
        return False, res.get("why", "hata")
    if not res["biome_match"]:
        return False, "spawn biyomu oyunda farkli"
    if res["village"] is None or res["village"][2] > max_dist:
        return False, "koy yok/uzak (%s)" % (res["village"],)
    if res["river"] is None or res["river"][2] > max_dist:
        return False, "nehir yok/uzak (%s)" % (res["river"],)
    return True, ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("candidates")
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--max-dist", type=int, default=900)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--out", default=os.path.join(HERE, "out"))
    ap.add_argument("--debug", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    okf, badf = os.path.join(a.out, "verified.jsonl"), os.path.join(a.out, "rejected.jsonl")
    done = set()
    for fn in (okf, badf):
        if os.path.exists(fn):
            done |= {json.loads(l)["seed"] for l in open(fn, encoding="utf-8") if l.strip()}
    cands = [json.loads(l) for l in open(a.candidates, encoding="utf-8") if l.strip()]
    cands = [c for c in cands if c["seed"] not in done]
    if a.limit:
        cands = cands[:a.limit]
    print("dogrulanacak: %d seed, %d worker" % (len(cands), a.workers), flush=True)

    work_q = queue.Queue()
    for c in cands:
        work_q.put(c)
    lock = threading.Lock()
    stats = {"ok": 0, "bad": 0}
    t0 = time.time()

    def loop(idx):
        w = Worker(idx)
        while True:
            try:
                c = work_q.get_nowait()
            except queue.Empty:
                return
            try:
                r = w.run(c, a.debug)
                good, why = judge(c, r, a.max_dist)
            except Exception as e:                      # tek seed butun taramayi durdurmasin
                r, good, why = {"ok": False}, False, "istisna: %r" % e
            row = dict(c); row["verify"] = r
            if not good:
                row["reject_reason"] = why
            with lock:
                with open(okf if good else badf, "a", encoding="utf-8") as f:
                    f.write(json.dumps(row, ensure_ascii=False) + "\n")
                stats["ok" if good else "bad"] += 1
                n = stats["ok"] + stats["bad"]
                print("[%d/%d] seed=%d %s%s  (%.0f sn)" % (n, len(cands), c["seed"],
                      "KABUL" if good else "RED: " + why, "", time.time() - t0), flush=True)

    ths = [threading.Thread(target=loop, args=(i + 1,)) for i in range(a.workers)]
    [t.start() for t in ths]; [t.join() for t in ths]
    print("bitti: kabul=%d red=%d sure=%.0f sn" % (stats["ok"], stats["bad"], time.time() - t0))


if __name__ == "__main__":
    main()
