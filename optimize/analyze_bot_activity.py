"""Independent position/rotation evidence; not a human or packet-load certificate."""
import argparse
import json
import math
import re
from collections import defaultdict
from pathlib import Path

POS=re.compile(r"\b(ultrabot[1-4]) has the following entity data: \[([-\d.Ee+]+)d, ([-\d.Ee+]+)d, ([-\d.Ee+]+)d\]")
ROT=re.compile(r"\b(ultrabot[1-4]) has the following entity data: \[([-\d.Ee+]+)f, ([-\d.Ee+]+)f\]")
MODE=re.compile(r"\b(ultrabot[1-4]) has the following entity data: ([0-3])\s*$")
EXPECTED={"ultrabot1":(0,-1,180),"ultrabot2":(1,0,-90),"ultrabot3":(0,1,0),"ultrabot4":(-1,0,90)}

def assess(log):
    points=defaultdict(list)
    rotations=defaultdict(list)
    modes=defaultdict(list)
    started=False
    ended=False
    # Teleport phase invalidates exploration distance. Stop at first explicit teleport.
    for line in log.splitlines():
        if "UHC başladı!" in line: started=True
        if not started: continue
        if re.search(r"Teleported ultrabot[234] to",line,re.I) or re.search(r"\[Server\] KAPISMA_MOVEMENT_END\s*$",line):
            ended=True
            break
        for pat,store in ((POS,points),(ROT,rotations),(MODE,modes)):
            match=pat.search(line)
            if match:
                try:
                    values=tuple(map(float,match.groups()[1:]))
                except ValueError:
                    continue
                if all(map(math.isfinite,values)): store[match[1]].append(values)
    result={}
    for name,(vx,vz,yaw) in EXPECTED.items():
        p=points[name]; r=rotations[name]; m=modes[name]
        dx,dz=(p[-1][0]-p[0][0],p[-1][2]-p[0][2]) if len(p)>=2 else (0,0)
        forward=dx*vx+dz*vz
        rotation_ok=bool(r) and all(abs((a[0]-yaw+180)%360-180)<=2 for a in r)
        survival=bool(m) and all(a==(0.0,) for a in m)
        segment_progress=[(b[0]-a[0])*vx+(b[2]-a[2])*vz for a,b in zip(p,p[1:])]
        active_segments=sum(d>=2 for d in segment_progress)
        result[name]={"positions":len(p),"rotations":len(r),"modes":len(m),
                      "active_forward_segments":active_segments,
                      "trace_coverage":len(p)>=6 and len(p)==len(r)==len(m),
                      "dx":round(dx,2),"dz":round(dz,2),"horizontal_displacement":round(math.hypot(dx,dz),2),
                      "expected_direction_progress":round(forward,2),"rotation_ok":rotation_ok,
                      "all_observed_survival":survival,
                      "observed_movement":len(p)>=2 and len(r)>=2 and len(m)>=2 and forward>=2 and rotation_ok and survival}
    return {"players":result,"exploration_end_observed":ended,
            "all_four_observed_movement":ended and all(v["observed_movement"] for v in result.values()),
            "all_four_trace_coverage":ended and all(v["trace_coverage"] for v in result.values()),
            "all_four_sustained_sampled_movement":ended and all(v["trace_coverage"] and v["observed_movement"] and v["active_forward_segments"]>=2 for v in result.values()),
            "human_certified":False,"note":"Discrete position evidence only; no uninterrupted movement or PvP hit proof."}

def main():
    p=argparse.ArgumentParser();p.add_argument("log",type=Path);a=p.parse_args()
    print(json.dumps(assess(a.log.read_text(encoding="utf-8-sig")),indent=2))
if __name__=="__main__":main()
