"""Correctness-only gate for the isolated real-world map shadow-render run."""
import argparse
import json
import re
from pathlib import Path

def assess(log):
    failures=[]
    pattern=r"(?m)^.*ULTRA_MAP_PARITY pixels=(\d+) scale=(\d+) matched=(true|false)\s*$"
    records=re.findall(pattern,log)
    if sorted(records)!=[("16384","2","true")]*4+[("16384","4","true")]:
        failures.append("expected exactly four treasure and one region pixel comparison")
    published=re.findall(r"ULTRA_MAP_COMPLETE kind=treasure index=(\d+)",log)
    if published!=["1","2","3","4"]:
        failures.append("missing or duplicate treasure publication")
    if log.count("ULTRA_MAP_COMPLETE kind=region")!=1:
        failures.append("region publication not unique")
    for marker in ("ULTRA_MAP_MODE incremental=true","UHC başladı!",
                   "HARNESS_MAP_PARITY_COMPLETE treasure=4 region=1 performance_certified=false",
                   "Stopping server","All dimensions are saved","server_mode=restored"):
        if marker not in log:
            failures.append("missing marker: "+marker)
    if not re.search(r"(?m)^exit=0\s*$",log):
        failures.append("nonzero or missing exit")
    if re.search(r"incremental/vanilla biome preview mismatch|HARNESS_ABORT|Segmentation fault|SIGSEGV|OutOfMemoryError",log):
        failures.append("correctness or fatal runtime failure")
    return {"map_parity_pass":not failures,"performance_certified":False,
            "human_certified":False,"comparisons":len(records),
            "pixels_compared":sum(int(x[0]) for x in records),"failures":failures}

def main():
    p=argparse.ArgumentParser();p.add_argument("log",type=Path);a=p.parse_args()
    result=assess(a.log.read_text(encoding="utf-8-sig"))
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if result["map_parity_pass"] else 1
if __name__=="__main__": raise SystemExit(main())
