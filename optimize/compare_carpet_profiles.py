"""Conservative profile comparison: exact inputs except modset; no performance certificate."""
import argparse,csv,json,re,statistics
from pathlib import Path
from evaluate_carpet_profile import assess
from evaluate_query_latency import assess as query_assess
from evaluate_thermal_trace import assess as thermal_assess

REQUIRED={"bg_threads","jfr","map_mode","spawn_mode","chunk_mode","android_jna","timing","fixed_heap_mib","map_parity_proof","route_mode","carpet_profile","profile_mobcap","candidate_sha256","config_except_view_sha256","modset_sha256","script_sha256","view","simulation","route_version","arena_seed","arena_center"}
FIXED={"bg_threads":"default","jfr":"0","map_mode":"1","spawn_mode":"1","chunk_mode":"1","android_jna":"1","timing":"0","fixed_heap_mib":"2048","map_parity_proof":"0","route_mode":"0","carpet_profile":"1","profile_mobcap":"70","view":"3","simulation":"3","route_version":"1","arena_seed":"5743893257777525637","arena_center":"16,-48"}
def meta(s):
    m={}
    for line in s.splitlines():
        k,sep,v=line.partition("=")
        if not sep or k in m or not v:raise ValueError("invalid manifest")
        m[k]=v
    if set(m)!=REQUIRED or any(m[k]!=v for k,v in FIXED.items()):raise ValueError("unexpected profile inputs")
    for k in ("candidate_sha256","config_except_view_sha256","modset_sha256","script_sha256"):
        if not re.fullmatch("[a-f0-9]{64}",m[k]):raise ValueError("bad hash")
    return m

def compare(base,candidate,base_modset,candidate_modset):
    fail=[];results=[]
    try:
        a,b=meta(base[0]),meta(candidate[0])
        if any(a[k]!=b[k] for k in REQUIRED-{"modset_sha256"}):fail.append("non-modset inputs differ")
        if a["modset_sha256"]!=base_modset or b["modset_sha256"]!=candidate_modset or base_modset==candidate_modset:fail.append("unexpected modset hashes")
    except ValueError as e:fail.append(str(e))
    for tag,run in (("baseline",base),("candidate",candidate)):
        _,log,rows,query,thermal=run
        r=assess(log)
        if not r["evidence_pass"]:fail.append(tag+" profile incomplete")
        q=query_assess(query,rows)
        if not q["coverage_pass"]:fail.append(tag+" query evidence incomplete")
        t=thermal_assess(thermal,rows)
        if not t["pass"]:fail.append(tag+" thermal evidence failed")
        try:
            if len([x for x in rows if x["phase"]=="exploration"])!=2:raise ValueError()
            rss=max(int(x["rss_kib"]) for x in rows);avail=min(int(x["available_kib"]) for x in rows)
            if rss<=0 or avail<=0:raise ValueError()
        except (ValueError,KeyError):fail.append(tag+" invalid RAM/sample trace");rss=avail=None
        results.append({"tag":tag,"profiles":r["profiles"],"max_sampled_rss_kib":rss,"min_sampled_available_kib":avail,"query":q,"thermal":t})
    # Same species' per-ticked-entity elapsed cost is descriptive only. Counts
    # and costs come from one window, but mob AI states and locations differ.
    def by_species(r,field):
        entries=r.get("profiles",{}).get("entities",{}).get(field,{})
        sums={}
        for key,v in entries.items():
            if key.startswith("fantasy:"):
                species=key.rsplit("/",1)[-1];sums[species]=sums.get(species,0)+v
        return sums
    bc,cc=(by_species(r,"counts") for r in results)
    bt,ct=(by_species(r,"costs") for r in results)
    species={}
    for k in ("zombie","skeleton","creeper","bat","player"):
        if k in bc and k in cc and bc[k]>0 and cc[k]>0 and k in bt and k in ct:
            species[k]={"baseline_mean_count":bc[k],"candidate_mean_count":cc[k],"baseline_ms":bt[k],"candidate_ms":ct[k],
                        "baseline_ms_per_ticked_entity":round(bt[k]/bc[k],4),"candidate_ms_per_ticked_entity":round(ct[k]/cc[k],4),
                        "count_difference_over_20_percent":abs(cc[k]-bc[k])>0.2*bc[k]}
    return {"input_comparison_pass":not fail,"failures":fail,"runs":results,"species_diagnostic":species,"performance_certified":False,
            "note":"Expected modset hashes must be independently verified from staged JAR inventory. Nominal workload only: random player locations, entity populations, generation, profiler overhead and background scheduling differ. No continuous peak RAM or human/anti-cheat load certificate."}

def main():
    p=argparse.ArgumentParser();p.add_argument("base",type=Path);p.add_argument("candidate",type=Path);p.add_argument("--base-modset",required=True);p.add_argument("--candidate-modset",required=True);a=p.parse_args()
    def read(prefix):
        def t(s):
            with Path(str(prefix)+s).open(encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
        return Path(str(prefix)+"-experiment.txt").read_text(encoding="utf-8-sig"),Path(str(prefix)+".log").read_text(encoding="utf-8-sig"),t(".tsv"),t("-query-latency.tsv"),t("-thermal.tsv")
    r=compare(read(a.base),read(a.candidate),a.base_modset,a.candidate_modset);print(json.dumps(r,indent=2));return 0 if r["input_comparison_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
