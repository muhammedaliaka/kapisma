"""Evaluate a diagnostic first-route probe, never a full game or speed certificate."""
import argparse,csv,json,re,math
from pathlib import Path
from evaluate_chunk_route import expected,MARK
from analyze_bot_activity import POS,MODE
from evaluate_android_run import TICK,PCT
from evaluate_thermal_trace import assess as thermal_assess

def assess(log,preload,metrics,latency,thermal):
    fail=[]
    targets=list(expected(1).values())
    if len(preload)!=4:fail.append("four preload targets required")
    duration=[]
    for i,row in enumerate(preload):
        try:
            if i>=4 or int(row["target"])!=i or (int(row["x"]),int(row["z"]))!=targets[i] or row["result"]!="ready":raise ValueError()
            d=int(row["prepare_ms"])
            if not 0<d<=65000:raise ValueError()
            duration.append(d)
        except (ValueError,KeyError,TypeError):fail.append("invalid preload row")
    markers=[]
    ready_all=False
    route=[]
    positions={};modes={}
    for line in log.splitlines():
        m=re.search(r"\[Server\] KAPISMA_PRELOAD_READY target=(\d+) x=(-?\d+) z=(-?\d+)\s*$",line)
        if m and not route and not ready_all:
            v=tuple(map(int,m.groups()))
            if v not in markers:markers.append(v)
        if re.search(r"\[Server\] KAPISMA_PRELOAD_ALL_READY\s*$",line):
            if markers==[(i,*p) for i,p in enumerate(targets)] and not route:ready_all=True
        m=MARK.search(line)
        if m:route.append((m[1],int(m[2])))
        if route:
            p=POS.search(line);g=MODE.search(line)
            if p:positions[p[1]]=tuple(map(float,p.groups()[1:]))
            if g:modes[g[1]]=int(g[2])
    if not ready_all:fail.append("ordered readiness evidence missing")
    if route!=[("first",1)]:fail.append("wrong route")
    for n,(x,z) in expected(1).items():
        v=positions.get(n)
        if not v or not all(map(math.isfinite,v)) or abs(v[0]-x)>.01 or abs(v[2]-z)>.01 or modes.get(n)!=0:fail.append("position/mode mismatch "+n)
    q=[r for r in latency if r.get("phase")=="route_first"]
    elapsed=None
    if len(q)!=1:fail.append("route latency missing or duplicate")
    else:
        try:
            elapsed=int(q[0]["elapsed_ms"])
            if elapsed<=0 or elapsed>15000 or q[0]["result"]!="ok":fail.append("route response deadline failed")
        except (ValueError,KeyError,TypeError):fail.append("invalid route latency")
    rows=[r for r in metrics if r.get("phase")=="route_first"]
    stats=None
    if len(rows)!=1:fail.append("route sample incomplete")
    else:
        a=TICK.search(rows[0]["response"]);p=PCT.search(rows[0]["response"])
        if not a or not p:fail.append("tick statistics missing")
        else:stats={"mean_mspt":float(a[1]),"p95":float(p[1]),"p99":float(p[2])}
    if not re.search(r"No force loaded chunks were found in fantasy:[a-z0-9_/-]+",log):fail.append("temporary tickets not proven released")
    for mark in ("HARNESS_PRELOAD_COMPLETE centers=4 route_steps=1 performance_certified=false","All dimensions are saved","server_mode=restored"):
        if mark not in log:fail.append("missing "+mark)
    if not re.search(r"(?m)^exit=0\s*$",log) or re.search(r"HARNESS_ABORT|SIGSEGV|Segmentation fault|OutOfMemoryError",log):fail.append("aborted or failed")
    fail+=thermal_assess(thermal,metrics)["failures"]
    return {"probe_evidence_pass":not fail,"performance_certified":False,"failures":fail,"prepared_centers":len(duration),"total_preparation_ms":sum(duration),"route_query_ms":elapsed,"route_stats":stats,
            "note":"Single synthetic first-step diagnostic, not causal proof. Vanilla forceload is synchronous; preparation cost must not be omitted."}

def main():
    p=argparse.ArgumentParser();p.add_argument("prefix",type=Path);a=p.parse_args()
    def read(suffix):
        with Path(str(a.prefix)+suffix).open(encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
    r=assess(Path(str(a.prefix)+".log").read_text(encoding="utf-8-sig"),read("-preload.tsv"),read(".tsv"),read("-query-latency.tsv"),read("-thermal.tsv"))
    print(json.dumps(r,indent=2));return 0 if r["probe_evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
