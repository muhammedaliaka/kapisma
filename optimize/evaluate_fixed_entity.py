"""Strict diagnostic evidence gate for a fixed-population flat-world microbenchmark."""
import argparse,csv,json,re
from pathlib import Path
from evaluate_carpet_profile import assess as profile_assess
from evaluate_query_latency import assess as query_assess
from evaluate_thermal_trace import assess as thermal_assess

SPECIES=("zombie","skeleton","creeper","bat")
MARK="HARNESS_FIXED_ENTITY_COMPLETE mobs=64 players=4 performance_certified=false"
def assess(log,rows,queries,thermal,manifest):
    fail=[]
    m={}
    for line in manifest.splitlines():
        k,sep,v=line.partition("=")
        if not sep or not v or k in m:fail.append("invalid manifest");continue
        m[k]=v
    expected={"fixed_entity":"1","fixture_world_seed":"20261126","fixture_level_type":"flat","view":"3","simulation":"3","profile_mobcap":"70","carpet_profile":"1","route_mode":"0","fixed_heap_mib":"2048","jfr":"0"}
    if any(m.get(k)!=v for k,v in expected.items()):fail.append("wrong fixture inputs")
    for k in ("fixture_sha256","candidate_sha256","config_except_view_sha256","modset_sha256","script_sha256"):
        if not re.fullmatch("[a-f0-9]{64}",m.get(k,"")):fail.append("invalid hash "+k)
    lines=log.splitlines()
    def unique(pattern):
        hits=[i for i,l in enumerate(lines) if re.search(pattern,l)]
        if len(hits)!=1:fail.append("missing/duplicate "+pattern);return -1
        return hits[0]
    def marker(name):
        return unique(r"\[Server\] "+re.escape(name)+r"\s*$")
    empty=marker("KAPISMA_FIXTURE_EMPTY_TICKETS")
    loaded=marker("KAPISMA_FIXTURE_LOADED")
    rooms=[marker(f"KAPISMA_FIXTURE_{kind}_{i}") for i in range(4) for kind in ("ROOM","CAGE")]
    summons=[marker("KAPISMA_FIXTURE_SUMMON_"+s) for s in SPECIES]
    last=empty
    for i in [loaded,*rooms,marker("KAPISMA_FIXTURE_PLAYERS_BARRIER"),*summons]:
        if i<=last:fail.append("fixture setup out of order")
        last=i
    for label in ("before","health","entities"):
        boundary=marker("KAPISMA_FIXTURE_COUNTS_"+label)
        if label!="before":
            begin=marker("KAPISMA_PROFILE_BEGIN kind="+label+" ticks=100")
            end=marker("KAPISMA_PROFILE_END kind="+label)
            if not last<begin<end<boundary:fail.append("profile order "+label)
            last=end
        for s in SPECIES:
            idx=unique(r"\b"+s+"_"+label+r" has 16 (?:ultraFixture|\[ultraFixture\])\s*$")
            if not last<idx<boundary:fail.append("count order "+s+" "+label)
        for i in range(1,5):
            idx=marker(f"KAPISMA_FIXTURE_PLAYER_{label}_{i}")
            if not last<idx<boundary:fail.append("player order "+label)
        idx=marker("KAPISMA_FIXTURE_NO_EXTRA_"+label)
        if not last<idx<boundary:fail.append("extra entities check "+label)
        last=boundary
    released=marker("KAPISMA_FIXTURE_TICKETS_RELEASED")
    empty_rows=[i for i,l in enumerate(lines) if "No force loaded chunks were found in minecraft:overworld" in l]
    if len(empty_rows)!=2 or not empty_rows[0]<empty<loaded<last<empty_rows[-1]<released:fail.append("ticket release evidence incomplete")
    if lines.count(MARK)!=1 or "UHC başladı!" in log:fail.append("wrong completion mode")
    r=profile_assess(log)
    if not r["evidence_pass"]:fail.extend(r["failures"])
    prof=r["profiles"].get("entities",{})
    counts=prof.get("counts",{});costs=prof.get("costs",{})
    for s in SPECIES:
        if counts.get("overworld/"+s)!=16.0:fail.append("profile count mismatch "+s)
        if "overworld/"+s not in costs:fail.append("missing species cost "+s)
    if counts.get("overworld/player")!=4.0 or "overworld/player" not in costs:fail.append("profile player count/cost missing")
    if [x.get("phase") for x in rows]!=["idle","lobby","exploration","exploration"]:fail.append("wrong sample sequence")
    q=query_assess(queries,rows);t=thermal_assess(thermal,rows)
    if not q["coverage_pass"]:fail.append("query trace incomplete")
    if not t["pass"]:fail.append("thermal trace failed")
    return {"fixture_evidence_pass":not fail,"failures":fail,"performance_certified":False,"manifest":m,"profiles":r["profiles"],"thermal":t,"query":q,
            "note":"Four stationary creative bots, 64 fixed mobs in flat enclosed rooms; AI random state remains variable. Not UHC, PvP, actual network clients or a human acceptance run."}

def read_run(prefix):
    def t(s):
        with Path(str(prefix)+s).open(encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
    return Path(str(prefix)+".log").read_text(encoding="utf-8-sig"),t(".tsv"),t("-query-latency.tsv"),t("-thermal.tsv"),Path(str(prefix)+"-experiment.txt").read_text(encoding="utf-8-sig")
def main():
    p=argparse.ArgumentParser();p.add_argument("prefix",type=Path);a=p.parse_args();r=assess(*read_run(a.prefix));print(json.dumps(r,indent=2));return 0 if r["fixture_evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
