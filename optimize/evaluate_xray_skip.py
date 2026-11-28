"""Read native-run anti-xray cumulative telemetry without modifying raw logs.
The timing field is rounded mean per request, so reconstructed interval costs
are approximate. Skip count is exact, not an estimate of total server speedup.
"""
import argparse,json,re
from pathlib import Path
PATTERN=re.compile(r"anti-xray: gönderilen=(\d+) chunk=(\d+) gizli=(\d+) cevher=(\d+) sahte=(\d+) ort=([\d.]+)ms \| açığa: (\d+) tarama, (\d+) paketli, (\d+) hücre, ort ([\d.]+)ms, gönderilen paket=(\d+) \((\d+) blok\)(?: \| bos tarama atlandi=(\d+) etkin=(true|false))?$")
def parse(line):
    m=PATTERN.search(line)
    if not m:return None
    sent,chunk,hidden,ores,fake,build_ms,runs,reveals,cells,scan_ms,packets,blocks,skips,enabled=m.groups()
    return dict(sent=int(sent),chunks=int(chunk),hidden=int(hidden),ores=int(ores),fakes=int(fake),build_ms=float(build_ms),requests=int(runs),reveals=int(reveals),cells=int(cells),scan_ms=float(scan_ms),packets=int(packets),blocks=int(blocks),skips=int(skips or 0),enabled=None if enabled is None else enabled=="true")
COUNTERS=("sent","chunks","hidden","ores","fakes","requests","reveals","cells","packets","blocks","skips")
def delta(a,b):
    d={k:b[k]-a[k] for k in COUNTERS}
    d["bfs_executed"]=d["requests"]-d["skips"]
    d["skip_fraction"]=d["skips"]/d["requests"] if d["requests"] else None
    d["approx_total_reveal_ms"]=round(b["scan_ms"]*b["requests"]-a["scan_ms"]*a["requests"],3)
    d["rounding_bound_ms"]=round(.0005*(a["requests"]+b["requests"]),3)
    d["approx_mean_reveal_ms_per_request"]=round(d["approx_total_reveal_ms"]/d["requests"],3) if d["requests"] else None
    return d
def assess(log,expected=None):
    failures=[];stages={}
    before=log.partition("[Server] KAPISMA_NATIVE_WALK_READY")
    walk=before[2].partition("[Server] KAPISMA_NATIVE_WALK_END")
    if not before[1] or not walk[1]:failures.append("walk markers missing")
    for phase,section in (("before_walk",before[0]),("walk_end",walk[0]),("profile_end",walk[2])):
        rows=[r for l in section.splitlines() if (r:=parse(l))]
        if not rows:failures.append("missing "+phase);continue
        # Every console status is normally printed twice. Exact duplicates
        # are harmless; monotonicity still checked across all entries.
        for a,b in zip(rows,rows[1:]):
            if any(b[k]<a[k] for k in COUNTERS):failures.append("nonmonotonic "+phase)
        stages[phase]=rows[-1]
        for r in rows:
            if r["skips"]>r["requests"] or r["reveals"]>r["requests"]-r["skips"]:failures.append("impossible counter relationship")
            if expected is not None and r["enabled"] is not expected:failures.append("unexpected runtime flag "+phase)
            if r["enabled"] is False and r["skips"]:failures.append("skip while disabled")
    intervals={}
    for a,b,n in (("before_walk","walk_end","walk"),("walk_end","profile_end","profile")):
        if a in stages and b in stages:
            d=delta(stages[a],stages[b]);intervals[n]=d
            if any(d[k]<0 for k in COUNTERS) or d["bfs_executed"]<0:failures.append("nonmonotonic interval "+n)
    if expected is True and ("walk" not in intervals or intervals["walk"]["skips"]<=0):failures.append("enabled skip not exercised")
    if "HARNESS_ABORT" in log or not re.search(r"(?m)^exit=0\s*$",log):failures.append("run failed")
    return dict(telemetry_pass=not failures,failures=sorted(set(failures)),stages=stages,intervals=intervals,performance_certified=False,note="Rounded cumulative means, exact counter deltas. No causal comparison, client-visible reveal proof or human acceptance.")
def main():
    p=argparse.ArgumentParser();p.add_argument("log",type=Path);p.add_argument("--enabled",choices=("0","1","legacy"),required=True);a=p.parse_args()
    r=assess(a.log.read_text(encoding="utf-8-sig"),None if a.enabled=="legacy" else a.enabled=="1");print(json.dumps(r,indent=2));return 0 if r["telemetry_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
