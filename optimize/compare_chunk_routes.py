"""Paired route comparison: reject differing inputs other than view distance."""
import argparse,csv,json,re
from pathlib import Path
from statistics import mean,median
from evaluate_chunk_route import assess
from evaluate_android_run import TICK,PCT

FIELDS={"bg_threads","jfr","map_mode","spawn_mode","chunk_mode","android_jna","timing","fixed_heap_mib","map_parity_proof","route_mode","candidate_sha256","config_except_view_sha256","modset_sha256","script_sha256","view","simulation","route_version","arena_seed","arena_center"}
FIXED={"jfr":"0","map_mode":"1","spawn_mode":"1","chunk_mode":"1","android_jna":"1","timing":"0","fixed_heap_mib":"2048","map_parity_proof":"0","route_mode":"1","simulation":"3","route_version":"1","arena_seed":"5743893257777525637","arena_center":"16,-48","bg_threads":"default"}
def metadata(text):
    result={}
    for line in text.splitlines():
        k,sep,v=line.partition("=")
        if not sep or not v or k in result:raise ValueError("malformed/duplicate metadata")
        result[k]=v
    if set(result)!=FIELDS:raise ValueError("incomplete metadata")
    if any(result[k]!=v for k,v in FIXED.items()):raise ValueError("unexpected route input")
    for k in ("candidate_sha256","config_except_view_sha256","modset_sha256","script_sha256"):
        if not re.fullmatch("[0-9a-f]{64}",result[k]):raise ValueError("invalid digest")
    if result["view"] not in ("3","4"):raise ValueError("invalid view")
    return result

def compare(a,b):
    ma,la,ra,ta=a;mb,lb,rb,tb=b
    failures=[]
    try:
        ma=metadata(ma);mb=metadata(mb)
        if ma["view"]!="4" or mb["view"]!="3":failures.append("expected view 4 then 3")
        if any(ma[k]!=mb[k] for k in FIELDS-{"view"}):failures.append("non-view input differs")
    except ValueError as e:failures.append(str(e))
    aa=assess(la,ra,ta);bb=assess(lb,rb,tb)
    if not aa["route_evidence_pass"] or not bb["route_evidence_pass"]:failures.append("route evidence incomplete")
    stats={}
    if not failures:
        for phase in ("first","revisit"):
            vals=[]
            for rows in (ra,rb):
                vals.append([(float(TICK.search(r["response"])[1]),float(PCT.search(r["response"])[1]),float(PCT.search(r["response"])[2])) for r in rows if r["phase"]=="route_"+phase])
            old,new=vals
            diff=[a[0]-b[0] for a,b in zip(old,new)]
            stats[phase]={"view4_mean_mspt":round(mean(x[0] for x in old),2),"view3_mean_mspt":round(mean(x[0] for x in new),2),"paired_median_reduction_ms":round(median(diff),2),"steps_improved":sum(d>0 for d in diff),"view3_samples_within_gate":sum(a<=50 and p<=50 and q<=100 for a,p,q in new)}
    return {"comparison_valid":not failures,"failures":failures,"stats":stats,"performance_certified":False,"note":"One paired diagnostic run, not causal proof; arena preparation and background workloads may differ. Bot teleports are not human movement."}
def main():
    p=argparse.ArgumentParser();p.add_argument("prefix4",type=Path);p.add_argument("prefix3",type=Path);a=p.parse_args()
    def load(prefix):
        def rows(suffix):
            with Path(str(prefix)+suffix).open(encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
        return Path(str(prefix)+"-experiment.txt").read_text(encoding="utf-8-sig"),Path(str(prefix)+".log").read_text(encoding="utf-8-sig"),rows(".tsv"),rows("-thermal.tsv")
    r=compare(load(a.prefix4),load(a.prefix3));print(json.dumps(r,indent=2))
    return 0 if r["comparison_valid"] else 1
if __name__=="__main__":raise SystemExit(main())
