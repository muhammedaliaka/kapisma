"""G1 completed-pause heap sizes and pause durations. Post-GC occupancy != retained/live heap."""
import argparse,json,re,statistics
from pathlib import Path
ROW=re.compile(r"^\[[^\]]+\]\[([\d.]+)s\]\[info\]\[gc\s*\]\s+GC\((\d+)\) Pause .*? (\d+)M->(\d+)M\((\d+)M\) ([\d.]+)ms\s*$",re.M)
def assess(log):
    rows=[(float(u),int(n),int(b),int(a),int(c),float(ms)) for u,n,b,a,c,ms in ROW.findall(log)]
    # Remark and Cleanup can share a GC ID. Reject exact duplicate records,
    # invalid occupancy, or non-monotonic order, not valid multiple phases.
    if not rows or len(set(rows))!=len(rows) or any(r[3]>r[4] for r in rows) or any(a[0]>b[0] for a,b in zip(rows,rows[1:])):
        return {"evidence_pass":False,"failures":["missing/duplicate/invalid completed GC heap records"]}
    def summary(rs):
        return {"n":len(rs),"post_gc_mib_median":statistics.median(x[3] for x in rs),"post_gc_mib_max":max(x[3] for x in rs),
                "capacity_mib_max":max(x[4] for x in rs),"pause_total_ms":round(sum(x[5] for x in rs),3),"pause_max_ms":max(x[5] for x in rs)}
    return {"evidence_pass":True,"all":summary(rows),"last_30s_of_gc_records":summary([r for r in rows if r[0]>=rows[-1][0]-30]),
            "note":"Completed pauses with whole-MiB fields only; some pause classes excluded. Final 30 seconds may include shutdown. Occupancy after GC is not exclusively live data, nor RSS."}
def main():
    p=argparse.ArgumentParser();p.add_argument("log",type=Path);a=p.parse_args();r=assess(a.log.read_text(encoding="utf-8-sig"));print(json.dumps(r,indent=2));return 0 if r["evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
