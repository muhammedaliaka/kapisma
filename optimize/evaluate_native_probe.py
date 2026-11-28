"""Evidence-only evaluation of actual native client test. Separate diagnostic and acceptance."""
import argparse,csv,json,math,re
from pathlib import Path
from evaluate_query_latency import assess as query_assess
from evaluate_thermal_trace import assess as thermal_assess

NAMES=tuple("KodNativeQA"+str(i) for i in range(1,5))
MARK="HARNESS_NATIVE_COMPLETE players=4 exploration_samples=12 performance_certified=false"
POS=re.compile(r"(?i)\b(KodNativeQA[1-4]) has the following entity data: \[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]")
WALK=re.compile(r"AIQA_WALK (\d+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)")
def progress(points):
    # Three consecutive independently observed horizontal movements, no single
    # teleport or net-displacement shortcut.
    best=streak=0
    for a,b in zip(points,points[1:]):
        step=math.hypot(b[0]-a[0],b[2]-a[2])
        streak=streak+1 if .5<=step<=40 else 0
        best=max(best,streak)
    return best

def assess(log,rows,query,thermal,events,client_logs):
    failures=[];limits=[]
    def ordered(kind):
        return [e.get("name") for e in events if e.get("event")==kind]
    started=[e for e in events if e.get("event")=="client_started"]
    if ordered("client_started")!=list(NAMES) or len({e.get("pid") for e in started})!=4:failures.append("four distinct client processes not proven")
    if ordered("client_joined")!=list(NAMES):failures.append("four host join acknowledgements missing")
    if ordered("client_closed")!=list(NAMES):failures.append("client cleanup incomplete")
    for kind in ("forward_verified","forward_removed","walk_started","server_complete","host_complete"):
        if sum(e.get("event")==kind for e in events)!=1:failures.append("missing/duplicate "+kind)
    if any(e.get("event") in ("failure","client_close_failed","forward_remove_failed","abort_signal_failed") for e in events):failures.append("host reported failure")
    if not events or events[-1].get("event")!="host_complete" or events[-1].get("diagnostic_complete") is not True:failures.append("host diagnostic incomplete")
    if log.splitlines().count(MARK)!=1 or not re.search(r"(?m)^exit=0\s*$",log) or "HARNESS_ABORT" in log or "All dimensions are saved" not in log or "server_mode=restored" not in log:failures.append("server completion/restore missing")
    if "UHC başladı!" not in log or len(re.findall(r"ULTRA_MAP_COMPLETE kind=treasure index=[1-4]\b",log))!=4 or "ULTRA_MAP_COMPLETE kind=region" not in log:failures.append("UHC/map evidence incomplete")
    window=log.partition("[Server] KAPISMA_NATIVE_WALK_BEGIN")[2].partition("[Server] KAPISMA_NATIVE_WALK_END")[0]
    positions={n:[] for n in NAMES}
    for n,x,y,z in POS.findall(window):
        name=next((v for v in NAMES if v.lower()==n.lower()),None)
        if name:positions[name].append((float(x),float(y),float(z)))
    evidence={}
    for n in NAMES:
        actual=bool(re.search(r"(?im)\b"+n+r"\[(?:/[^\]]+|IP hidden)\] logged in with entity id \d+",log))
        host_join=[e for e in events if e.get("event")=="client_joined" and e.get("name")==n]
        if len(host_join)!=1 or not host_join[0].get("socket_owned") or not host_join[0].get("client_chat"):failures.append("PID-owned TCP/client chat evidence missing "+n)
        if not actual or n.lower()+" joined the game" not in log.lower():failures.append("real join missing "+n)
        c=client_logs.get(n,"")
        walk=[(int(t),float(x),float(y),float(z)) for t,x,y,z in WALK.findall(c)]
        monotonic=all(b[0]>a[0] for a,b in zip(walk,walk[1:]))
        client_progress=progress([r[1:] for r in walk]) if monotonic else 0
        server_progress=progress(positions[n])
        evidence[n]={"actual_join":actual,"client_samples":len(walk),"server_samples":len(positions[n]),"client_progress_streak":client_progress,"server_progress_streak":server_progress}
        if "Registry remapping failed!" in c:failures.append("registry mismatch "+n)
        if client_progress<3 or server_progress<3:limits.append("sustained movement not proven "+n)
    chunks=[int(x) for x in re.findall(r"anti-xray: .*?\bchunk=(\d+)",log)]
    scans=[int(x) for x in re.findall(r"açığa: (\d+) tarama",log)]
    guard=[int(x) for x in re.findall(r"koruma: .*?degerlendirme=(\d+)",log)]
    if max(chunks,default=0)<=0:limits.append("anti-xray chunk path not exercised")
    if max(scans,default=0)<=0:limits.append("anti-xray reveal path not exercised")
    if max(guard,default=0)<=0:limits.append("guard evaluation path not exercised")
    q=query_assess(query,rows);t=thermal_assess(thermal,rows)
    if not q["coverage_pass"]:failures.append("query trace incomplete")
    if not t["pass"]:failures.append("thermal trace failed")
    if [r.get("phase") for r in rows].count("exploration")!=12:failures.append("12 exploration samples missing")
    if not q["latency_gate_pass"]:limits.append("polling latency budget failed")
    return {"diagnostic_evidence_pass":not failures,"movement_and_packet_coverage_pass":not failures and not limits,"performance_certified":False,"failures":failures,"limitations":limits,"clients":evidence,"max_xray_chunks":max(chunks,default=0),"max_xray_scans":max(scans,default=0),"max_guard_evaluations":max(guard,default=0),"thermal":t,"query":q,"note":"No human test, no PvP/voice/Origins ability acceptance; server ticks and percentile budgets require separate performance evaluation."}

def main():
    p=argparse.ArgumentParser();p.add_argument("prefix",type=Path);p.add_argument("client_dir",type=Path);a=p.parse_args()
    def read(path):return path.read_text(encoding="utf-8-sig",errors="replace")
    def t(s):
        with Path(str(a.prefix)+s).open(encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
    events=[json.loads(l) for l in read(a.client_dir/"host.jsonl").splitlines()]
    logs={n:read(a.client_dir/f"client-{i}.log") for i,n in enumerate(NAMES,1) if (a.client_dir/f"client-{i}.log").is_file()}
    r=assess(read(Path(str(a.prefix)+".log")),t(".tsv"),t("-query-latency.tsv"),t("-thermal.tsv"),events,logs)
    print(json.dumps(r,indent=2));return 0 if r["diagnostic_evidence_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
