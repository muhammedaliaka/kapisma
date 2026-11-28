"""Compare equal-population microbenchmarks; never certify whole-game performance."""
import argparse,json,math
from pathlib import Path
from evaluate_fixed_entity import assess,read_run,SPECIES

def compare(base,candidate,base_modset,candidate_modset):
    a,b=assess(*base),assess(*candidate)
    fail=[]
    if not a["fixture_evidence_pass"]:fail.append("baseline fixture evidence failed")
    if not b["fixture_evidence_pass"]:fail.append("candidate fixture evidence failed")
    ma,mb=a["manifest"],b["manifest"]
    if {k:v for k,v in ma.items() if k!="modset_sha256"}!={k:v for k,v in mb.items() if k!="modset_sha256"}:fail.append("non-modset inputs differ")
    if base_modset==candidate_modset or ma.get("modset_sha256")!=base_modset or mb.get("modset_sha256")!=candidate_modset:fail.append("unverified modset difference")
    def extract(r,run):
        profiles=r["profiles"];health=profiles["health"];entity=profiles["entities"]
        costs={s:entity["costs"]["overworld/"+s] for s in (*SPECIES,"player")}
        samples=run[1]
        rss=max(int(x["rss_kib"]) for x in samples)
        avail=min(int(x["available_kib"]) for x in samples)
        out={"health_mspt":health["mean_mspt"],"entity_profile_mspt":entity["mean_mspt"],"overworld_entities_ms":health["sections"].get("overworld/Entities"),
                "species_ms":costs,"four_mob_types_ms":sum(costs[s] for s in SPECIES),"max_sampled_rss_kib":rss,"min_sampled_available_kib":avail}
        if rss<=0 or avail<=0 or out["overworld_entities_ms"] is None or any(not math.isfinite(x) or x<0 for x in costs.values()):raise ValueError("invalid metrics")
        return out
    metrics=None
    if not fail:
        try:
            x,y=extract(a,base),extract(b,candidate)
            metrics={"baseline":x,"candidate":y,"mob_cost_delta_percent":round(100*(y["four_mob_types_ms"]/x["four_mob_types_ms"]-1),3) if x["four_mob_types_ms"]>0 else None,
                     "rss_delta_kib":y["max_sampled_rss_kib"]-x["max_sampled_rss_kib"]}
        except (KeyError,ValueError,TypeError):fail.append("invalid profile metrics")
    return {"comparison_evidence_pass":not fail,"failures":fail,"performance_certified":False,"metrics":metrics,
            "note":"Equal entity counts, room geometry and stationary creative players; random AI states and JVM warmup remain uncontrolled. Repeat before deciding. No full game, real-client, combat or human claim."}

def main():
    p=argparse.ArgumentParser();p.add_argument("base",type=Path);p.add_argument("candidate",type=Path);p.add_argument("--base-modset",required=True);p.add_argument("--candidate-modset",required=True);a=p.parse_args()
    r=compare(read_run(a.base),read_run(a.candidate),a.base_modset,a.candidate_modset);print(json.dumps(r,indent=2));return 0 if r["comparison_evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
