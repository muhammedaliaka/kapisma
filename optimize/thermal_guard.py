"""Parse Android thermalservice's CURRENT readings, not thresholds or cached history."""
import re

def assess(text):
    statuses=re.findall(r"(?m)^[ \t]*Thermal Status:[ \t]*([^\r\n]*)\r?$",text)
    status=statuses[0].strip() if len(statuses)==1 else None
    if status not in tuple("0123456"): status=None
    # The service may contain both current and historical temperature tables.
    # Restrict to the current cached-temperature section; never infer a unit or
    # safety threshold for undocumented vendor sensor names.
    current=re.search(r"(?ms)^\s*Cached temperatures:\s*\n(.*?)(?=^\s*HAL [^\n]*:|^\s*Temperature thresholds:|\Z)",text)
    readings={}
    if current:
        for value,kind,name in re.findall(r"Temperature\{mValue=([-\d.]+), mType=(-?\d+), mName=([^,\n}]+)",current[1]):
            try: val=float(value)
            except ValueError: continue
            if kind in ("0","1","2","3") and -30<=val<=150:
                readings.setdefault(name,val)
    if status is None:
        return {"status":None,"stop":True,"reason":"thermal status missing","readings_c":readings}
    n=int(status)
    return {"status":n,"stop":n>=3,"reason":"Android severe or higher" if n>=3 else "status below severe; not a safety guarantee","readings_c":readings}
