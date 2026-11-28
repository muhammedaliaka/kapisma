"""Additional acceptance gate for isolated four-lane native client diagnostics.
No production performance, human or protection correctness certificate.
"""
import argparse,json,re
from pathlib import Path
from evaluate_carpet_profile import assess as profiles, MARK as PROFILE_MARK
from evaluate_native_probe import MARK as NATIVE_MARK, POS, NAMES

def assess(log):
    failures=[]
    lines=log.splitlines()
    def unique(pattern):
        hits=[i for i,l in enumerate(lines) if re.search(pattern,l)]
        if len(hits)!=1:
            failures.append("missing/duplicate "+pattern)
            return -1
        return hits[0]
    def marker(s):
        return unique(r"\[Server\] "+re.escape(s)+r"$")
    stages=[marker("KAPISMA_NATIVE_ROUTE_"+s) for s in ("DIMENSION","EMPTY","TICKETS","BUILT","VERIFIED","PLACED","PLAYERS","RELEASED")]
    if any(a>=b for a,b in zip(stages,stages[1:])):failures.append("route setup order")
    setup=unique(r"\[Server\] HARNESS_NATIVE_ROUTE_SETUP dimension=fantasy:[a-z0-9_/-]+ lanes=4 chunks=42 setup_teleports=4 tickets_released=true$")
    ready=marker("KAPISMA_NATIVE_WALK_READY");begin=marker("KAPISMA_NATIVE_WALK_BEGIN");end=marker("KAPISMA_NATIVE_WALK_END")
    if not stages[-1]<setup<ready<begin<end:failures.append("setup and measurement overlap")
    expected={(x,z) for x in range(-64,160,16) for z in (-57,-45,-33,-21)}
    observed=re.findall(r"\[Server\] KAPISMA_ROUTE_LANE_(-?\d+)_(-?\d+)$","\n".join(lines[max(0,stages[3]):max(0,stages[4])]),re.M)
    if len(observed)!=56 or {(int(x),int(z)) for x,z in observed}!=expected:failures.append("56 lane samples missing/duplicate")
    for i in range(1,5):
        idx=marker(f"KAPISMA_ROUTE_PLAYER_{i}")
        if not stages[5]<idx<stages[6]:failures.append("player setup out of order")
    m=re.search(r"\[Server\] HARNESS_NATIVE_ROUTE_SETUP dimension=(fantasy:[a-z0-9_/-]+) ",log,re.M)
    if not m:failures.append("dimension missing")
    else:
        empty=[i for i,l in enumerate(lines) if "No force loaded chunks were found in "+m[1] in l]
        if len(empty)!=2 or not empty[0]<stages[1]<stages[6]<empty[1]<stages[7]:failures.append("ticket release incomplete")
    movement={}
    pts={n:[] for n in NAMES}
    window="\n".join(lines[max(0,begin):max(0,end)])
    if re.search(r"Teleported KodNativeQA[1-4]",window):failures.append("teleport within measurement")
    for line in window.splitlines():
        stamp=re.match(r"^\[(\d\d):(\d\d):(\d\d)\]",line)
        match=POS.search(line)
        if stamp and match:
            n,x,y,z=match.groups()
            if n in pts:
                h,minute,sec=map(int,stamp.groups())
                pts[n].append((float(x),float(y),float(z),h*3600+minute*60+sec))
    for i,n in enumerate(NAMES):
        a=pts[n]
        z0=float(str(-57+i*12)+".5")
        bounds=all(-60.5<=x<=159 and abs(y-240)<=.5 and abs(z-z0)<=1 for x,y,z,t in a)
        longest=streak=0
        for v,w in zip(a,a[1:]):
            dt=w[3]-v[3]
            if dt<0:dt+=86400
            # Console clock has one-second precision. Conservative route-only
            # evidence envelope: bounded 2–20s gaps, up to 8 blocks/s plus
            # one second of timestamp quantization. Not a cheat detector.
            streak=streak+1 if 2<=dt<=20 and .5<=w[0]-v[0]<=8*(dt+1) else 0
            longest=max(streak,longest)
        movement[n]={"samples":len(a),"bounds":bounds,"eastward_streak":longest}
        if len(a)!=13 or not bounds or longest<3:failures.append("route movement not verified "+n)
        data=re.findall(re.escape(n)+r" has the following entity data: (.*)$",window,re.M)
        mode_count=sum(v=="0" for v in data)
        dim_count=sum(v=='"'+m[1]+'"' for v in data) if m else 0
        if mode_count!=13 or dim_count!=13 or len(data)!=39:failures.append("Survival/dimension trace mismatch "+n)
    profile_end=marker("KAPISMA_NATIVE_PROFILE_COMPLETE")
    starts=[marker("KAPISMA_PROFILE_BEGIN kind="+s+" ticks=100") for s in ("health","entities")]
    ends=[marker("KAPISMA_PROFILE_END kind="+s) for s in ("health","entities")]
    if not end<starts[0]<ends[0]<starts[1]<ends[1]<profile_end:failures.append("profiler window order")
    # Reuse the exact existing report parser with the native completion marker
    # replaced only in memory, not in raw evidence.
    if lines.count(NATIVE_MARK)!=1:failures.append("native completion missing")
    r=profiles(log.replace(NATIVE_MARK,PROFILE_MARK))
    if not r["evidence_pass"]:failures.extend(r["failures"])
    guard=[(int(a),int(b)) for a,b in re.findall(r"koruma: .*?degerlendirme=(\d+) gizleme=(\d+)",log)]
    if not guard or max(a for a,b in guard)<=0 or max(b for a,b in guard)<=0:failures.append("guard evaluation and hidden decisions not demonstrated")
    return {"route_evidence_pass":not failures,"failures":failures,"movement":movement,"profiles":r["profiles"],"performance_certified":False,"note":"Controlled sky lane, not natural terrain exploration. Guard counters are not proof of client-visible occlusion or combat correctness."}
def main():
    p=argparse.ArgumentParser();p.add_argument("log",type=Path);a=p.parse_args();r=assess(a.log.read_text(encoding="utf-8-sig"));print(json.dumps(r,indent=2));return 0 if r["route_evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
