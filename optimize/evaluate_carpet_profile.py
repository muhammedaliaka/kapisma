"""Validate two bounded Carpet profiler reports. Times are elapsed per tick, not exclusive CPU time."""
import argparse,json,re
from pathlib import Path
PREFIX=re.compile(r"^\[\d\d:\d\d:\d\d\] \[Server thread/INFO\]: (.*)$")
BEGIN=re.compile(r"^\[Not Secure\] \[Server\] KAPISMA_PROFILE_BEGIN kind=(health|entities) ticks=100$")
END=re.compile(r"^\[Not Secure\] \[Server\] KAPISMA_PROFILE_END kind=(health|entities)$")
NUMBER=r"(\d+(?:\.\d+)?)"
MARK="HARNESS_CARPET_PROFILE_COMPLETE health_ticks=100 entities_ticks=100 performance_certified=false"

def assess(log):
    failures=[];reports={};active=None;section=None;world=None
    for line in log.splitlines():
        m=PREFIX.fullmatch(line)
        if not m:continue
        s=m[1].strip()
        m=BEGIN.fullmatch(s)
        if m:
            if active is not None or m[1] in reports or list(reports)!=({"health":[],"entities":["health"]}[m[1]]):
                failures.append("unexpected begin")
            active=m[1];reports[active]={"mean_mspt":None,"sections":{},"counts":{},"costs":{},"rest_ms":None}
            section=None;world=None;continue
        m=END.fullmatch(s)
        if m:
            if active!=m[1]:failures.append("unexpected end")
            active=None;continue
        if active is None:continue
        r=reports[active]
        m=re.fullmatch("Average tick time: "+NUMBER+"ms",s)
        if m:
            if r["mean_mspt"] is not None:failures.append("duplicate mean")
            r["mean_mspt"]=float(m[1]);continue
        if active=="health":
            m=re.fullmatch("The Rest, whatever that might be: "+NUMBER+"ms",s)
            if m:
                if r["rest_ms"] is not None:failures.append("duplicate rest")
                r["rest_ms"]=float(m[1]);continue
            m=re.fullmatch(r"([\w:/.-]+):",s)
            if m:world=m[1];continue
            m=re.fullmatch(r"(- )?([\w ]+): "+NUMBER+"ms",s)
            if m:
                key=(world+"/" if m[1] and world else "")+m[2]
                if key in r["sections"]:failures.append("duplicate section")
                r["sections"][key]=float(m[3])
        else:
            if s=="Top 10 counts:":section="counts";continue
            if s=="Top 10 CPU hogs:":section="costs";continue
            m=re.fullmatch(r"- ([\w:./-]+) in ([\w:./-]+): "+NUMBER+"(ms)?",s)
            if m and section:
                if bool(m[4])!=(section=="costs"):failures.append("wrong entity unit");continue
                key=m[2]+"/"+m[1]
                if key in r[section]:failures.append("duplicate entity")
                r[section][key]=float(m[3])
    if active is not None or list(reports)!=["health","entities"]:failures.append("incomplete profiles")
    if log.splitlines().count(MARK)!=1 or not re.search(r"(?m)^exit=0\s*$",log):failures.append("completion missing")
    if "HARNESS_ABORT" in log or "All dimensions are saved" not in log or "server_mode=restored" not in log:failures.append("shutdown failed")
    for k,r in reports.items():
        if not r["mean_mspt"] or r["mean_mspt"]<=0:failures.append(k+" mean missing")
        if k=="health" and (not r["sections"] or r["rest_ms"] is None):failures.append("health sections missing")
        if k=="entities" and (not r["counts"] or not r["costs"] or len(r["counts"])>10 or len(r["costs"])>10):failures.append("entity lists missing or invalid")
    return {"evidence_pass":not failures,"performance_certified":False,"failures":failures,"profiles":reports,
            "note":"Non-overlapping 100-tick windows. Elapsed time includes waits; no causal CPU attribution or human certification."}

def main():
    p=argparse.ArgumentParser();p.add_argument("log",type=Path);a=p.parse_args()
    r=assess(a.log.read_text(encoding="utf-8-sig"));print(json.dumps(r,indent=2));return 0 if r["evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
