#!/usr/bin/env python3
"""
Kapisma lobi parkuru uretici + fizik dogrulayici.

1) Minecraft 1.21 oyuncu hareketini (kosu/ziplama/surtunme) 1 boyutta simule eder.
2) Her ziplamanin, verilen kosu mesafesi (platform uzunlugu) ile ulasilabilir oldugunu en az
   MARGIN blok pay birakarak kanitlar. Gecilemeyen ziplama uretilmez.
3) Kare spiral rota ve 4 zorluk seviyesini uretir, mod kaynagina parkour.json olarak yazar.

Kullanim: python parkour_gen.py [--out yol] [--check]
"""
import argparse, json, math, os, random, sys

# ---------------------------------------------------------------- fizik
G = 0.08
DRAG_Y = 0.98
AIR_F = 0.91
GROUND_F = 0.6 * 0.91
SPRINT_SPEED = 0.13       # 0.1 * 1.3
WALK_SPEED = 0.10
INPUT = 0.98


def ground_acc(speed):
    return speed * (0.21600002 / GROUND_F ** 3) * INPUT


def simulate_jump(runway, dy, sprint=True, jump_tick=None, v0=0.0):
    """Platform [-runway, 0]. Oyuncu merkezi arka kenardan 0.3 iceride baslar, jump_tick'te zipler.
    Doner: (inis_x, ok) ; inis_x merkez konumu hedef yuksekligine inerken."""
    x = -runway + 0.3
    v = v0
    t = 0
    spd = SPRINT_SPEED if sprint else WALK_SPEED
    # yerde kosu
    while True:
        if jump_tick is not None and t == jump_tick:
            break
        if x > 0.3:
            return None
        v += ground_acc(spd)
        x += v
        v *= GROUND_F
        t += 1
        if t > 60:
            return None
    if x > 0.3:
        return None
    # ziplama tiki (hala yerde sayilir: surtunme yer)
    y = 0.0
    vy = 0.42
    v += (0.2 if sprint else 0.0)
    v += ground_acc(spd)
    x += v
    y += vy
    v *= GROUND_F
    vy = (vy - G) * DRAG_Y
    # havada
    for _ in range(80):
        v += 0.026 * INPUT if sprint else 0.02 * INPUT
        px, py = x, y
        x += v
        y += vy
        v *= AIR_F
        vy = (vy - G) * DRAG_Y
        if vy < 0 and y <= dy:
            # kesisim: dy seviyesine inis
            f = (py - dy) / max(1e-9, (py - y))
            return px + (x - px) * f, True
    return None


def max_reach(runway, dy, sprint=True):
    best = None
    for jt in range(0, 40):
        r = simulate_jump(runway, dy, sprint, jt)
        if r and (best is None or r[0] > best):
            best = r[0]
    return best


MARGIN = 0.35   # insan hatasi payi (blok)


def feasible(gap, dy, runway, sprint=True):
    """gap: iki platform arasindaki bos blok sayisi (eksen boyunca). Hedef near-edge = gap."""
    r = max_reach(runway, dy, sprint)
    if r is None:
        return False
    return r >= gap - 0.3 + MARGIN + 0.0   # kutunun kenari hedefe degmeli


# ---------------------------------------------------------------- seviyeler
# her seviye: (ad, renk, malzeme, platform boyutlari, bosluk secenekleri, dy secenekleri, ziplama sayisi, cp araligi)
LEVELS = [
    dict(key="kolay", name="KOLAY", color="lime", sizes=[(3, 3), (3, 3), (2, 3)], gaps=[1, 1, 2], dys=[0, 1, 1, 1, 0, -1], jumps=22, cp=7,
         climb=5, blink=None),
    dict(key="orta", name="ORTA", color="yellow", sizes=[(2, 2), (2, 2), (3, 2)], gaps=[1, 2, 2, 3], dys=[0, 1, 1, 1, -1], jumps=24, cp=8,
         climb=7, blink=dict(m=3, size=(3, 3), period=100, on=70, stagger=20)),
    dict(key="zor", name="ZOR", color="orange", sizes=[(1, 1), (2, 1), (1, 2), (4, 1), (2, 2)], gaps=[2, 2, 2, 3], dys=[1, 1, 0, -1, 1], jumps=26, cp=8,
         climb=9, blink=dict(m=4, size=(2, 2), period=90, on=60, stagger=20)),
    dict(key="cok_zor", name="ÇOK ZOR", color="red", sizes=[(1, 1), (1, 1), (1, 1), (4, 1), (1, 2)], gaps=[2, 2, 3, 3], dys=[1, 1, 1, 0, -1], jumps=28, cp=9,
         climb=12, blink=dict(m=5, size=(2, 2), period=80, on=55, stagger=20)),
]

# kare spiral: kose noktalari (hub merkezi 0,0). Basla (19,0) -> +z
def spiral_vertices(n_sides):
    vs = [(19, 0), (19, 19)]
    for i in range(2, n_sides + 1):
        e = 17 + 2 * i
        sx, sz = vs[-1]
        if i % 2 == 0:       # x degisir
            sign = -1 if (i // 2) % 2 == 1 else 1
            vs.append((sign * e, sz))
        else:                # z degisir
            sign = -1 if (i // 2) % 2 == 1 else 1
            vs.append((sx, sign * e))
    return vs


def snap_lat(c, size):
    """Yanal merkez: cift boyutta x.5, tek boyutta tam sayi."""
    return math.floor(c) + 0.5 if size % 2 == 0 else float(round(c))


def ylo(q):
    return q.get("ylo", q["y"])


def build(seed=7, verbose=False):
    rnd = random.Random(seed)
    vs = spiral_vertices(14)
    side = 0

    def dir_of(i):
        a, b = vs[i], vs[i + 1]
        return ((b[0] > a[0]) - (b[0] < a[0]), (b[1] > a[1]) - (b[1] < a[1]))

    def side_line(i):          # yan eksendeki hedef cizgi (kosenin sabit koordinati)
        a = vs[i]
        d = dir_of(i)
        return a[1] if d[0] != 0 else a[0]

    def end_bound(i):          # bu kenarin bitis koordinati (yon ekseninde)
        b = vs[i + 1]
        d = dir_of(i)
        return b[0] if d[0] != 0 else b[1]

    plats = [dict(cx=float(vs[0][0]), cz=float(vs[0][1]), y=0, sx=3, sz=3, level=0, kind="start")]
    cur = plats[0]

    def clash(cx, cz, sx, sz, lo, hi):
        if math.hypot(cx, cz) < 17.5 and lo < 12:      # hub uzerine binmesin
            return True
        for q in plats[:-3]:
            if abs(q["cx"] - cx) < (q["sx"] + sx) / 2 + 3 and abs(q["cz"] - cz) < (q["sz"] + sz) / 2 + 3 \
                    and ylo(q) - 6 < hi and q["y"] + 6 > lo:
                return True
        return False

    def place(lv, li, kind, size=None, extra=None):
        nonlocal cur, side
        extra = extra or {}
        why = {}
        for attempt in range(600):
            d = dir_of(side)
            ax = d[0] != 0
            sx, sz = size if size else rnd.choice(lv["sizes"])
            if kind == "climb":
                gap, dy = 0, extra["h"]
            else:
                gap = rnd.choice(lv["gaps"]) if kind == "step" else rnd.choice([1, 2])
                dy = rnd.choice(lv["dys"]) if kind == "step" else (0 if kind == "blink" else rnd.choice([0, 1]))
            if kind in ("end", "corner", "cp", "climb", "blink"):
                sx, sz = size
            elif not ax:                       # z yonunde: dikdortgenin uzun kenari yone paralel olsun
                sx, sz = sz, sx
            run_len = cur["sx"] if ax else cur["sz"]
            if kind != "climb" and not feasible(gap, dy, run_len):
                why["feasible"] = why.get("feasible", 0) + 1
                continue
            cx = cur["cx"] + (d[0] * (cur["sx"] / 2 + gap + sx / 2) if ax else 0)
            cz = cur["cz"] + (d[1] * (cur["sz"] / 2 + gap + sz / 2) if not ax else 0)
            lat = 0
            if kind == "step" and gap <= 2 and lv["key"] != "kolay":
                lat = rnd.choice([0, 0, 1, -1])
            line = side_line(side)
            if kind == "climb":
                # merdiven kulesi: yanal merkez ayni (merdiven iki platformun da uzerinde olmali)
                if ax:
                    cz = cur["cz"]
                else:
                    cx = cur["cx"]
            elif ax:
                cz = snap_lat(cz + lat, sz)
                if abs(cz - line) > 2.5:       # cizgiye en fazla 1 blok yaklas (ani yanal sicrama olmasin)
                    cz = snap_lat(cur["cz"] + max(-1.0, min(1.0, line - cur["cz"])), sz)
            else:
                cx = snap_lat(cx + lat, sx)
                if abs(cx - line) > 2.5:
                    cx = snap_lat(cur["cx"] + max(-1.0, min(1.0, line - cur["cx"])), sx)
            y = cur["y"] + dy
            lo = cur["y"] if kind == "climb" else y
            if clash(cx, cz, sx, sz, lo, y):
                why["clash"] = why.get("clash", 0) + 1
                continue
            cand = dict(cx=cx, cz=cz, y=y, sx=sx, sz=sz)
            if kind != "climb" and not jump_ok(cur, cand)[0]:
                why["jump_ok"] = why.get("jump_ok", 0) + 1
                why["last"] = (cand, jump_ok(cur, cand)[1])
                continue
            far = (cx + d[0] * sx / 2) if ax else (cz + d[1] * sz / 2)
            bnd = end_bound(side)
            over = (far - bnd) * (d[0] if ax else d[1]) > 0
            if kind in ("step", "climb", "blink") and over:
                return "corner"
            p = dict(cx=cx, cz=cz, y=y, sx=sx, sz=sz, level=li, kind=kind)
            if kind == "climb":
                p.update(h=dy, dx=d[0], dz=d[1], ylo=cur["y"])
            elif kind == "blink":
                p.update(extra)
            plats.append(p)
            cur = p
            return "ok"
        raise SystemExit("rota uretilemedi: seviye=%s tur=%s kenar=%d cur=%s neden=%s" % (lv["key"], kind, side, cur, why))

    def place_x(lv, li, kind, size=None, extra=None):
        """Kose gelirse kose platformu koyup yeniden dener. Doner: yerlestirilen platform sayisi."""
        nonlocal side
        n = 0
        while True:
            r = place(lv, li, kind, size, extra)
            if r == "corner":
                place(lv, li, "corner", (3, 3))
                side += 1
                n += 1
                continue
            return n + 1

    for li, lv in enumerate(LEVELS):
        n = 0
        did_climb = did_blink = False
        while n < lv["jumps"]:
            if not did_climb and n >= lv["jumps"] * (0.5 if lv["blink"] is None else 0.3):
                did_climb = True
                n += place_x(lv, li, "step", (3, 3))          # 3x3: merdivenin altinda genis zemin
                n += place_x(lv, li, "climb", (3, 3), dict(h=lv["climb"]))
                n += place_x(lv, li, "cp", (3, 3))
                continue
            if lv["blink"] and not did_blink and n >= lv["jumps"] * 0.65:
                did_blink = True
                bl = lv["blink"]
                n += place_x(lv, li, "cp", (3, 3))           # dusunce buradan devam
                for k in range(bl["m"]):
                    n += place_x(lv, li, "blink", bl["size"], dict(period=bl["period"], on=bl["on"], phase=(bl["period"] - k * bl["stagger"]) % bl["period"]))
                n += place_x(lv, li, "cp", (3, 3))
                continue
            r = place(lv, li, "step")
            if r == "corner":
                place(lv, li, "corner", (3, 3))
                side += 1
                n += 1
            else:
                n += 1
            if n % lv["cp"] == 0 and n < lv["jumps"] - 2:
                place(lv, li, "cp", (3, 3))
        # seviye sonu: 5x5 dinlenme + checkpoint platformu
        place_x(lv, li, "end", (5, 5))
    return plats


def jump_ok(a, b):
    dx = b["cx"] - a["cx"]
    dz = b["cz"] - a["cz"]
    axis_x = abs(dx) >= abs(dz)
    if axis_x:
        gap = abs(dx) - a["sx"] / 2 - b["sx"] / 2
        run = a["sx"]
        lat = abs(dz)
        wa, wb = a["sz"], b["sz"]
    else:
        gap = abs(dz) - a["sz"] / 2 - b["sz"] / 2
        run = a["sz"]
        lat = abs(dx)
        wa, wb = a["sx"], b["sx"]
    dy = b["y"] - a["y"]
    if gap < 0:
        return False, ("ust uste", gap)
    # yanal sapma: platformlarin yan kenarlari arasi bosluk (orta cizgi farki - yarim genislikler)
    lat_gap = max(lat - wa / 2 - wb / 2, 0)
    eff = math.hypot(gap, lat_gap)
    ok = feasible(eff, dy, max(run, 1))
    return ok, dict(gap=gap, dy=dy, run=run, eff=round(eff, 2))


def validate(plats):
    """Tum ardisik gecisleri bagimsiz yeniden dogrular (uretimden bagimsiz)."""
    problems = []
    for i in range(1, len(plats)):
        a, b = plats[i - 1], plats[i]
        if b["kind"] == "climb":
            # merdiven: bitisik, yanal merkez ayni, makul yukseklik, merdiven hucresi iki platformda da var
            d = (b["dx"], b["dz"])
            if d[0]:
                touching = abs(abs(b["cx"] - a["cx"]) - (a["sx"] + b["sx"]) / 2) < 1e-6
                lat_ok = abs(b["cz"] - a["cz"]) < 1e-6
            else:
                touching = abs(abs(b["cz"] - a["cz"]) - (a["sz"] + b["sz"]) / 2) < 1e-6
                lat_ok = abs(b["cx"] - a["cx"]) < 1e-6
            if not (touching and lat_ok and 2 <= b["h"] <= 14 and a["sx"] == 3 and a["sz"] == 3):
                problems.append((i, "merdiven gecersiz", dict(touching=touching, lat_ok=lat_ok, h=b["h"])))
            continue
        ok, info = jump_ok(a, b)
        if not ok:
            problems.append((i, "gecilemez", info))
        if b["kind"] == "blink":
            T, Pd, st = b["on"], b["period"], b["phase"]
            if T - 20 < 30 or Pd - T < 20 or T > Pd:
                problems.append((i, "blink zamanlama", dict(on=T, period=Pd)))
    return problems


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "mod", "kapisma", "src", "main", "resources", "data", "kapisma", "parkour.json"))
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--table", action="store_true")
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    if a.table:
        print("max erisim (merkez x, platform kenari=0) / kosu uzunlugu x dy")
        for dy in (-1, 0, 1):
            print("dy=%+d" % dy, {r: round(max_reach(r, dy) or -9, 2) for r in (1, 2, 3, 4, 5)})
        print("yuruyerek dy=0", {r: round(max_reach(r, 0, False) or -9, 2) for r in (1, 2, 3, 4)})
    plats = build(a.seed)
    probs = validate(plats)
    print("platform sayisi:", len(plats), "sorun:", len(probs))
    for p in probs[:10]:
        print("  ", p)
    ys = [p["y"] for p in plats]
    print("yukseklik:", min(ys), "->", max(ys))
    if probs:
        sys.exit(1)
    if not a.check:
        out = []
        for i, p in enumerate(plats):
            out.append(dict(i=i, x=p["cx"] - (p["sx"] - 1) / 2.0, z=p["cz"] - (p["sz"] - 1) / 2.0, y=p["y"], sx=p["sx"], sz=p["sz"], level=p["level"], kind=p["kind"],
                            h=p.get("h", 0), dx=p.get("dx", 0), dz=p.get("dz", 0), period=p.get("period", 0), on=p.get("on", 0), phase=p.get("phase", 0)))
        os.makedirs(os.path.dirname(a.out), exist_ok=True)
        json.dump(dict(levels=[dict(key=l["key"], name=l["name"], color=l["color"]) for l in LEVELS], platforms=out), open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
        print("yazildi:", a.out)
