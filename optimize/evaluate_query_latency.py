"""Monotonic polling latency, including harness overhead, not exact tick duration."""
from statistics import median

def assess(latency,metrics,limit_ms=3000):
    failures=[]
    if latency is None:
        return {"coverage_pass":False,"latency_gate_pass":False,"failures":["query latency evidence missing"],"phases":{}}
    samples=[]
    last_end=-1
    phases={}
    for row in latency:
        try:
            start=int(row["start_uptime_ms"]);elapsed=int(row["elapsed_ms"]);phase=row["phase"];status=row["result"]
            if start<last_end or elapsed<=0 or status not in ("ok","timeout"):raise ValueError()
            last_end=start+elapsed
            phases.setdefault(phase,[]).append(elapsed)
            if status=="ok":samples.append(phase)
            else:failures.append("query timeout: "+phase)
        except (ValueError,KeyError,TypeError):failures.append("invalid query latency row")
    if samples!=[r.get("phase") for r in metrics]:failures.append("query/sample phase sequence mismatch")
    if not latency:failures.append("empty query latency trace")
    summary={p:{"n":len(v),"median_ms":median(v),"max_ms":max(v),"over_limit":sum(x>limit_ms for x in v)} for p,v in phases.items()}
    return {"coverage_pass":not failures,"latency_gate_pass":not failures and all(v["over_limit"]==0 for v in summary.values()),"failures":failures,"phases":summary,"limit_ms":limit_ms}
