"""One-variable input check for opt-in anti-xray BFS skip.
A valid pair is diagnostic, not causal evidence of end-to-end TPS improvement.
"""
import argparse,json
from pathlib import Path
from evaluate_xray_skip import assess as xray
from evaluate_native_route import assess as route
REQUIRED=("candidate_sha256","modset_sha256","config_except_view_sha256","script_sha256","native_fixture_sha256","native_route_sha256","view","simulation","fixed_heap_mib","arena_seed","arena_center","native_mode","native_route","map_mode","spawn_mode","chunk_mode","android_jna","jfr","bg_threads")
def read_manifest(text):
    m={}
    for l in text.splitlines():
        if "=" not in l:raise ValueError("malformed manifest")
        k,v=l.split("=",1)
        if not k or not v or k in m:raise ValueError("duplicate/empty manifest field")
        m[k]=v
    return m
def input_failures(base,candidate):
    failures=[]
    for k in REQUIRED:
        if k not in base or k not in candidate or base[k]!=candidate[k]:failures.append("missing/mismatched "+k)
    if base.get("xray_empty_skip","0")!="0" or candidate.get("xray_empty_skip")!="1":failures.append("wrong A/B flag")
    if {k:v for k,v in base.items() if k!="xray_empty_skip"}!={k:v for k,v in candidate.items() if k!="xray_empty_skip"}:failures.append("other manifest difference")
    for k,v in (("native_route","1"),("native_mode","1"),("view","3"),("simulation","3")):
        if base.get(k)!=v:failures.append("unexpected fixture input "+k)
    return failures
def assess(base_log,base_manifest,candidate_log,candidate_manifest):
    failures=input_failures(base_manifest,candidate_manifest)
    reports={}
    for label,log,enabled in (("base",base_log,False),("candidate",candidate_log,True)):
        r=route(log);x=xray(log,enabled)
        if not r["route_evidence_pass"]:failures.append(label+" route evidence failed")
        if not x["telemetry_pass"]:failures.append(label+" xray telemetry failed")
        reports[label]={"route_pass":r["route_evidence_pass"],"telemetry":x}
    summary=None
    if not failures:
        a=reports["base"]["telemetry"]["intervals"]["walk"]
        b=reports["candidate"]["telemetry"]["intervals"]["walk"]
        summary={"base":a,"candidate":b}
    return {"comparison_evidence_pass":not failures,"failures":failures,"walk":summary,"reports":reports,"performance_certified":False,"note":"Same candidate/route inputs and distinct flags, but different JVM runs, random UHC start, GC, scheduling, mob populations. Sky route is not natural terrain. No total-server speedup certified."}
def main():
    p=argparse.ArgumentParser();p.add_argument("base",type=Path);p.add_argument("candidate",type=Path);a=p.parse_args()
    def read(prefix,suffix):return Path(str(prefix)+suffix).read_text(encoding="utf-8-sig")
    r=assess(read(a.base,".log"),read_manifest(read(a.base,"-experiment.txt")),read(a.candidate,".log"),read_manifest(read(a.candidate,"-experiment.txt")))
    print(json.dumps(r,indent=2));return 0 if r["comparison_evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
