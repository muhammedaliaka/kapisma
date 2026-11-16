#!/usr/bin/env python3
"""verified.jsonl -> kapisma/seeds.json (Kapisma modunun okuyacagi seed havuzu)."""
import collections, datetime, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(HERE, "out", "verified.jsonl")
DST = os.path.join(ROOT, "seeds.json")

NAMES = {
    "ova": "Ova", "orman": "Orman", "taiga_kar": "Taiga / Kar",
    "col_mesa": "Çöl / Mesa", "savan_cangil": "Savan / Cangıl", "dag": "Dağ",
}

rows = [json.loads(l) for l in open(SRC, encoding="utf-8") if l.strip()]
pool = collections.defaultdict(list)
for r in rows:
    v = r["verify"]
    pool[r["class"]].append({
        "seed": str(r["seed"]),                       # string: 64 bit sayi, JSON/JS'de hassasiyet kaybolmasin
        "spawn": v["game_spawn"],                     # oyunun gercek spawn'i (border merkezi)
        "biome": r["biome"],
        "score": r["score"],
        "village_dist": v["village"][2],              # spawn'a en yakin koy (blok)
        "river_dist": v["river"][2],
        "villages_scan": r["n_villages"],
        "ocean": r["ocean"],
        "biomes": r["biomes"],
    })
for c in pool:
    pool[c].sort(key=lambda s: (-s["score"], s["village_dist"]))

out = {
    "game_version": "1.21.1",
    "generated": datetime.date.today().isoformat(),
    "border_radius": 1000,
    "criteria": {
        "spawn": "kara, sinif biyomunda (okyanus/mantar/plaj yok)",
        "village": "spawn'a <= 900 blok (gercek sunucuda /locate ile dogrulandi)",
        "river": "spawn'a <= 900 blok (gercek sunucuda dogrulandi)",
        "ocean_fraction": "< 0.25 (2000x2000 kutu, cubiomes)",
        "biomes": ">= 3 farkli biyom",
        "trees": "spawn cevresinde agac biyomu",
        "note": "lav yeralti her dunyada vardir, kriter degil",
    },
    "class_names": NAMES,
    "classes": {c: pool.get(c, []) for c in NAMES},
}
with open(DST, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print("yazildi:", DST)
for c in NAMES:
    print("  %-14s %3d seed" % (c, len(pool.get(c, []))))
print("  toplam: %d" % sum(len(v) for v in pool.values()))
