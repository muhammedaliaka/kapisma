"""Strict historical stress log evaluator; not a real-client certification."""
import argparse
import json
import re
from pathlib import Path

ROW = re.compile(r"^\s*\d\d:\d\d:\d\d\s+(\S+)\s+mspt=(\S+)\s+p95=(\S+)\s+p99=(\S+)\s+bos_ram=(\d+)MB\s+java=(\d+)MB", re.M)
PHASES = {"bos-lobi", "lobi-bekle", "uhc-dogus", "kosu", "toplu-pvp"}
def assess(text, rss_cap=1700, min_free=512):
    rows = ROW.findall(text)
    errors = []
    present = {r[0] for r in rows}
    if not PHASES <= present:
        errors.append("missing phases: " + ", ".join(sorted(PHASES-present)))
    missing = slow95 = slow99 = 0
    rss_peak = max((int(r[5]) for r in rows), default=0)
    avail_min = min((int(r[4]) for r in rows), default=0)
    for phase, mspt, p95, p99, free, rss in rows:
        try:
            vals = [float(x) for x in (mspt,p95,p99)]
        except ValueError:
            missing += 1
            continue
        if not all(0 <= x < float("inf") for x in vals):
            missing += 1
            continue
        slow95 += vals[1] > 50
        slow99 += vals[2] > 100
    if missing: errors.append(f"missing/invalid samples: {missing}")
    if slow95: errors.append(f"p95 > 50ms samples: {slow95}")
    if slow99: errors.append(f"p99 > 100ms samples: {slow99}")
    if rss_peak > rss_cap: errors.append("RSS cap exceeded")
    if rows and avail_min < min_free: errors.append("available memory floor breached")
    keepup = re.findall(r"Can't keep up' uyarisi:\s*(\d+)", text)
    error_summary = re.findall(r"Hata satiri:\s*(\d+)", text)
    if len(keepup) != 1 or int(keepup[0]) != 0:
        errors.append("keep-up warnings nonzero or unverified")
    if len(error_summary) != 1 or int(error_summary[0]) != 0:
        errors.append("error count nonzero or unverified")
    if re.search(r"OutOfMemoryError|watchdog.*(?:crash|fatal)|SUNUCU ONGORULMEDEN KAPANDI",text,re.I):
        errors.append("fatal runtime indicator")
    if not re.search(r"(?m)^exit=0\s*$", text):
        errors.append("successful termination unverified")
    return {"sample_gate_pass": not errors, "certified": False, "samples": len(rows),
            "missing": missing, "p95_over_50": slow95, "p99_over_100": slow99,
            "rss_peak_mib": rss_peak, "available_min_mib": avail_min, "failures": errors,
            "scope": "legacy sampled bot stress only; no TPS/real-client/soak certification"}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("log", type=Path)
    p.add_argument("--rss-cap",type=int,default=1700)
    p.add_argument("--min-free",type=int,default=512)
    a=p.parse_args()
    result=assess(a.log.read_text(encoding="utf-8-sig"), a.rss_cap, a.min_free)
    print(json.dumps(result,indent=2))
    return 0 if result["sample_gate_pass"] else 1
if __name__ == "__main__":
    raise SystemExit(main())
