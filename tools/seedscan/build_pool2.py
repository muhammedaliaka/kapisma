#!/usr/bin/env python3
"""
Seed havuzunu GERCEK dunya ureteci sonuclariyla yeniden kurar.

Girdi : server_test/config/kapisma/seedscan_in.json  (aday seed'ler: pool + cubiomes adaylari)
        server_test/config/kapisma/seedscan_out.jsonl (oyun ici tarama: /kapisma debug seedscan)
        tools/seedscan/out/scan_*.jsonl (cubiomes: spawn biyomu, okyanus orani)
Cikti : seeds.json (+ seed/ klasorune kopya sunucu config'lerine yazilmaz; deploy asamasinda kopyalanir)

Kriter ("seed" denebilmesi): koy ZORUNLU DEGIL. Merkezden <=600 blok icinde onemli yapi, en az 3 FARKLI onemli
yapi turu ve en az bir kume (bir yapinin 300 blok icinde baska turden onemli yapi) gerekir. Puan: SeedScore.java.
"""
import glob, json, os, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
IN = os.path.join(ROOT, "server_test", "config", "kapisma", "seedscan_in.json")
OUT = os.path.join(ROOT, "server_test", "config", "kapisma", "seedscan_out.jsonl")
PER_CLASS = int(sys.argv[1]) if len(sys.argv) > 1 else 80

inp = {e["seed"]: e for e in json.load(open(IN, encoding="utf-8"))}
old = json.load(open(os.path.join(ROOT, "seeds.json"), encoding="utf-8"))
old_by_seed = {e["seed"]: e for l in old["classes"].values() for e in l}
cub = {}
for f in glob.glob(os.path.join(ROOT, "tools", "seedscan", "out", "scan_*.jsonl")):
    for line in open(f, encoding="utf-8"):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        cub[str(d["seed"])] = d

res = []
for line in open(OUT, encoding="utf-8"):
    try:
        r = json.loads(line)
    except ValueError:
        continue
    res.append(r)
print("taranan:", len(res), "/", len(inp))

by_class = collections.defaultdict(list)
dropped = collections.Counter()
for r in res:
    s = r["seed"]
    meta = inp.get(s)
    if not meta:
        continue
    q = r["q"]
    if not q["ok"]:
        dropped["kriter"] += 1
        continue
    o = old_by_seed.get(s)
    c = cub.get(s)
    biome = (o or {}).get("biome") or (c or {}).get("biome")
    if not biome:
        dropped["biyom yok"] += 1
        continue
    imp = {k: v for k, v in q["counts"].items() if k not in ("mineshaft", "ocean_ruin")}
    by_class[meta["cls"]].append(dict(
        seed=s, spawn=[r["x"], r["z"]], biome=biome, score=q["score"], nearest_important=q["nearest"],
        best_cluster=q["best_cluster"], important=imp,
        ocean=(o or {}).get("ocean", (c or {}).get("ocean")), biomes=len(r["biomes"])))

classes = {}
for cls in old["classes"]:
    l = sorted(by_class.get(cls, []), key=lambda e: -e["score"])[:PER_CLASS]
    classes[cls] = l
    print("%-13s aday %4d  havuz %3d  en iyi %s  en dusuk %s" % (cls, len(by_class.get(cls, [])), len(l),
          l[0]["score"] if l else "-", l[-1]["score"] if l else "-"))
print("elenen:", dict(dropped))

old["generated"] = "2026-11-16"
old["criteria"] = {
    "kural": "Koy zorunlu degil; cesitli ve YAKIN onemli yapilar (koy, karakol, tapinak, iglo, gemi, harabe portal, deneme odasi...)",
    "yakin": "merkezden <= 600 blokta en az bir onemli yapi",
    "cesitlilik": ">= 3 farkli onemli yapi turu (mineshaft ve su alti harabesi sayilmaz)",
    "kume": "bir yapinin 300 blok icinde baska turden onemli yapi (ornek: koy + karakol + harabe portal)",
    "spawn": "kara, sinif biyomunda (okyanus/mantar yok); okyanus orani < 0.25; >= 3 biyom",
    "dogrulama": "gercek dunya ureteci (Knowledge.scan / SeedScore), cubiomes sadece on eleme",
}
old["classes"] = classes
json.dump(old, open(os.path.join(ROOT, "seeds.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("seeds.json yazildi, toplam", sum(len(v) for v in classes.values()))
