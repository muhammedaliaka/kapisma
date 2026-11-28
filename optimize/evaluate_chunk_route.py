"""Compare first/revisited synthetic routes, not cold-cache or gameplay certification."""
import argparse
import csv
import json
import math
import re
from pathlib import Path
from statistics import mean
from analyze_bot_activity import POS, MODE
from evaluate_android_run import TICK, PCT
from evaluate_thermal_trace import assess as thermal_assess

STEPS=[(p,s) for p in ("first","revisit") for s in range(1,13)]
MARK=re.compile(r"KAPISMA_ROUTE_STEP pass=(first|revisit) step=(\d+)\s*$")
def expected(step):
    r=64+step*24
    return {"ultrabot1":(16,-48-r),"ultrabot2":(16+r,-48),
            "ultrabot3":(16,-48+r),"ultrabot4":(16-r,-48)}

def assess(log,rows,thermal):
    failures=[]
    groups=[]
    for line in log.splitlines():
        m=MARK.search(line)
        if m:
            groups.append((m[1],int(m[2]),{},{}))
            continue
        if not groups:continue
        p=POS.search(line);mode=MODE.search(line)
        if p:
            name=p[1]
            if name in groups[-1][2]:failures.append("duplicate position")
            groups[-1][2][name]=tuple(map(float,p.groups()[1:]))
        if mode:
            name=mode[1]
            if name in groups[-1][3]:failures.append("duplicate mode")
            groups[-1][3][name]=int(mode[2])
    if [(p,s) for p,s,_,_ in groups]!=STEPS:failures.append("step sequence incomplete")
    for p,s,positions,modes in groups:
        for name,(x,z) in expected(s).items():
            v=positions.get(name)
            if not v or not all(map(math.isfinite,v)) or abs(v[0]-x)>.01 or abs(v[2]-z)>.01 or modes.get(name)!=0:
                failures.append(f"route position/mode mismatch {p}/{s}/{name}")
    stats={"first":[],"revisit":[]}
    route_rows=[r for r in rows if r.get("phase","").startswith("route_")]
    if [r["phase"] for r in route_rows]!=["route_first"]*12+["route_revisit"]*12:failures.append("route samples incomplete")
    prev=-1
    for r in route_rows:
        try:
            ts=int(r["timestamp"]);a=TICK.search(r["response"]);p=PCT.search(r["response"])
            v=(float(a[1]),float(p[1]),float(p[2]))
            if ts<=prev or min(v)<=0 or int(r["available_kib"])<524288 or int(r["rss_kib"])<=0 or not 0<int(r["battery_deci_c"])<460:
                raise ValueError("invalid metrics")
            prev=ts
            stats[r["phase"][6:]].append(v)
        except (ValueError,KeyError,TypeError):failures.append("invalid route metrics")
    failures+=thermal_assess(thermal,rows)["failures"]
    for m in ("HARNESS_ROUTE_COMPLETE first=12 revisit=12 performance_certified=false","All dimensions are saved","server_mode=restored"):
        if m not in log:failures.append("missing "+m)
    if not re.search(r"(?m)^exit=0\s*$",log) or re.search(r"HARNESS_ABORT|SIGSEGV|Segmentation fault|OutOfMemoryError",log):
        failures.append("aborted or failed")
    return {"route_evidence_pass":not failures,"performance_certified":False,"human_certified":False,
            "failures":failures,"steps":len(groups),
            "stats":{p:{"n":len(v),"mean_mspt":round(mean(x[0] for x in v),2),"max_p95":max(x[1] for x in v),"max_p99":max(x[2] for x in v)} for p,v in stats.items() if v},
            "note":"First visit may already contain generated chunks; revisit may include evicted chunks. Not a cold/warm cache proof."}

def main():
    p=argparse.ArgumentParser();p.add_argument("log",type=Path);p.add_argument("metrics",type=Path);p.add_argument("thermal",type=Path);a=p.parse_args()
    def read(path):
        with path.open(encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
    r=assess(a.log.read_text(encoding="utf-8-sig"),read(a.metrics),read(a.thermal))
    print(json.dumps(r,indent=2))
    return 0 if r["route_evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
