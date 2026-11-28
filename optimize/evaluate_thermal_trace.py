"""Evidence gate for the on-device thermal guard. No temperature manipulation."""
def assess(rows, samples):
    failures=[]
    if not rows:
        return {"pass":False,"failures":["thermal trace missing"],"observations":0}
    phases=set()
    readings={}
    previous=-1
    for row in rows:
        try:
            t=int(row["timestamp"]); status=int(row["status"]); phase=row["phase"]
            if t<previous or not 0<=status<=2: raise ValueError("unsafe or out of order")
        except (ValueError,KeyError,TypeError):
            failures.append("invalid, severe, or out-of-order thermal reading")
            continue
        previous=t
        phases.add(phase)
        readings.setdefault(phase,[]).append(t)
    if "prelaunch" not in phases: failures.append("prelaunch thermal check missing")
    for row in samples:
        try:
            ts=int(row["timestamp"]); phase=row["phase"]
        except (ValueError,KeyError,TypeError):
            failures.append("invalid sample timestamp");continue
        # The check precedes the response. Allow time for ADB and tick query,
        # but reject a reused reading from a much earlier sample.
        if not any(0<=ts-t<=30 for t in readings.get(phase,())):
            failures.append("thermal coverage missing: "+phase)
    return {"pass":not failures,"failures":failures,"observations":len(rows)}
