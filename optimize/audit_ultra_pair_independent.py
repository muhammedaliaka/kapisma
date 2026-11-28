"""Read-only comparison gate. No device access; never certifies speedup.
Compare two finalized experimental manifests and tick telemetry.
"""
import argparse, csv, json, math, re
from pathlib import Path
from statistics import mean

REQUIRED = ("candidate_sha256","config_except_view_sha256","modset_sha256",
            "script_sha256","native_fixture_sha256","native_route_sha256",
            "fixed_heap_mib","view","simulation","arena_seed","arena_center",
            "route_version","native_mode","native_route")
FLAG = "xray_empty_skip"
TICK = re.compile(r"Average time per tick: ([0-9.]+)ms .*?P50: ([0-9.]+)ms P95: ([0-9.]+)ms P99: ([0-9.]+)ms, sample: (\d+)")

def manifest(s):
    out = {}
    for line in s.splitlines():
        if not line.strip():
            continue
        k, sep, v = line.partition("=")
        if not sep or not k or not v or k in out:
            raise ValueError("invalid or duplicate manifest field: " + k)
        out[k] = v
    return out

def samples(s):
    rows = []
    last = None
    for r in csv.DictReader(s.splitlines(), delimiter="\t"):
        if r["phase"] != "exploration":
            continue
        m = TICK.search(r["response"])
        if not m:
            raise ValueError("missing tick statistics")
        t = int(r["timestamp"])
        if last is not None and t <= last:
            raise ValueError("nonmonotonic exploration timestamp")
        last = t
        a, p50, p95, p99 = map(float, m.groups()[:4])
        if not all(math.isfinite(v) and v >= 0 for v in (a,p50,p95,p99)) or not p50 <= p95 <= p99 or int(m[5]) != 100:
            raise ValueError("invalid tick statistics")
        rows.append((a,p99,int(r["rss_kib"]),int(r["available_kib"])))
    if len(rows) != 12:
        raise ValueError("expected 12 controlled route samples")
    def stats(r):
        return dict(mean_of_window_means_ms=round(mean(x[0] for x in r),3),
                    max_window_p99_ms=max(x[1] for x in r))
    return dict(all_windows=stats(rows), first_six_windows=stats(rows[:6]),
                last_six_windows=stats(rows[6:]), peak_rss_kib=max(x[2] for x in rows),
                min_available_kib=min(x[3] for x in rows))

def assess(a,b,ta,tb):
    errors=[]
    for k in REQUIRED:
        if k not in a or k not in b:
            errors.append("missing:"+k)
    for k in sorted((set(a)|set(b))-{FLAG}):
        if a.get(k)!=b.get(k):
            errors.append("different:"+k)
    if a.get(FLAG)!="0" or b.get(FLAG)!="1":
        errors.append("expected explicit disabled then enabled flag")
    return dict(manifest_match=not errors, blockers=errors, baseline=samples(ta),
                candidate=samples(tb), performance_certified=False,
                note="Window statistics only, not pooled P99. First/last six are diagnostic, not proof of movement. Require independent completed route, exact runtime flag, client coverage, thermal and repeated counterbalanced runs before causal inference.")

def main():
    p=argparse.ArgumentParser()
    for key in ("baseline_manifest","candidate_manifest","baseline_tsv","candidate_tsv"):
        p.add_argument(key,type=Path)
    a=p.parse_args()
    read=lambda x:x.read_text(encoding="utf-8-sig")
    r=assess(manifest(read(a.baseline_manifest)),manifest(read(a.candidate_manifest)),read(a.baseline_tsv),read(a.candidate_tsv))
    print(json.dumps(r,indent=2))
    return 0 if r["manifest_match"] else 1
if __name__=="__main__":
    raise SystemExit(main())
