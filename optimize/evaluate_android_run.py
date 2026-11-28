"""Fail-closed evaluation of physical Android stress logs, not human certification."""
import argparse
import csv
import json
import re
from pathlib import Path
from statistics import mean

PHASES = {"idle", "lobby", "spawn", "exploration", "pvp"}
TICK = re.compile(r"Average time per tick: ([\d.]+)ms")
PCT = re.compile(r"P95: ([\d.]+)ms P99: ([\d.]+)ms")
def assess(log, rows, thermal_rows=None, query_rows=None):
    fail = []
    groups = {}
    for row in rows:
        phase = row.get("phase")
        m = TICK.search(row.get("response",""))
        p = PCT.search(row.get("response",""))
        if not m or not p:
            fail.append(f"missing tick response: {phase}")
            continue
        a, p95, p99 = float(m[1]), float(p[1]), float(p[2])
        groups.setdefault(phase, []).append((a,p95,p99))
        if a <= 0 or p95 <= 0 or p99 <= 0:
            fail.append(f"uninitialized tick statistics: {phase}")
        if a > 50 or p95 > 50 or p99 > 100:
            fail.append(f"latency limit: {phase} {p95}/{p99}")
        try:
            if int(row["available_kib"]) < 524288: fail.append("RAM floor")
            if int(row["rss_kib"]) <= 0: fail.append("invalid RSS")
            if not 0 < int(row["battery_deci_c"]) < 460: fail.append("battery temperature limit")
        except (ValueError, KeyError):
            fail.append(f"missing resource telemetry: {phase}")
    if not PHASES <= groups.keys(): fail.append("missing phases")
    if groups.keys() - PHASES: fail.append("unknown phases")
    # Historical sample gate is retained for comparison. A separate stronger
    # scenario gate also demands full coverage, ordered timestamps and movement.
    from analyze_bot_activity import assess as assess_activity
    activity = assess_activity(log)
    scenario_fail=[]
    minimum={"idle":1,"lobby":1,"spawn":1,"exploration":12,"pvp":4}
    for phase,count in minimum.items():
        if len(groups.get(phase,()))<count: scenario_fail.append("insufficient samples: "+phase)
    phase_order={p:i for i,p in enumerate(("idle","lobby","spawn","exploration","pvp"))}
    previous=-1
    previous_ts=-1
    for row in rows:
        phase=phase_order.get(row.get("phase"),-1)
        try: ts=int(row["timestamp"])
        except (KeyError,ValueError,TypeError):
            scenario_fail.append("missing timestamp")
            break
        if phase<previous or ts<=previous_ts:
            scenario_fail.append("nonmonotonic phase or timestamp")
            break
        previous,previous_ts=phase,ts
    if not activity["all_four_observed_movement"]:
        scenario_fail.append("four-bot movement not proven")
    if "HARNESS_ACTIVITY_TRACE_V2" in log:
        if not activity["all_four_trace_coverage"]:
            scenario_fail.append("incomplete intermediate movement trace")
        if not activity["all_four_sustained_sampled_movement"]:
            scenario_fail.append("sustained sampled movement not proven")
    if not re.search(r"(?m)^HARNESS_SCENARIO_COMPLETE\s*$",log):
        scenario_fail.append("scenario completion marker missing")
    if "HARNESS_ABORT" in log:
        scenario_fail.append("scenario abort marker")
    from evaluate_thermal_trace import assess as assess_thermal
    thermal=assess_thermal(thermal_rows,rows)
    scenario_fail.extend(thermal["failures"])
    from evaluate_query_latency import assess as assess_query
    query=assess_query(query_rows,rows)
    if "HARNESS_QUERY_LATENCY_V1" in log and not query["latency_gate_pass"]:
        scenario_fail.extend(query["failures"] or ["query response exceeded 3000 ms polling budget"])
    if "There are 4 of a max of 4 players online" not in log: fail.append("4 players not observed")
    if "UHC başladı!" not in log: fail.append("UHC start not observed")
    warnings = re.findall(r"Can't keep up!.*?Running (\d+)ms",log)
    if warnings: fail.append(f"keep-up warnings: {len(warnings)}")
    if re.search(r"UnsatisfiedLinkError|OutOfMemoryError|Segmentation fault|SIGSEGV|/ERROR\]|/FATAL\]",log):
        fail.append("runtime error indicators")
    scenario_exit_ok=bool(re.search(r"(?m)^exit=0\s*$",log))
    clean_shutdown_logged=("All dimensions are saved" in log and "Stopping server" in log
                           and not re.search(r"Segmentation fault|SIGSEGV|OutOfMemoryError",log))
    restored="server_mode=restored" in log
    if not scenario_exit_ok:
        fail.append("scenario exit not successful")
    if not clean_shutdown_logged:
        fail.append("normal stop not proven")
    if not restored:
        fail.append("restore not proven")
    # Log timestamps establish phase labels but not uninterrupted human-equivalent action.
    return {"sample_gate_pass": not fail, "scenario_gate_pass":not fail and not scenario_fail,
            "scenario_failures":scenario_fail,"movement_evidence":activity["all_four_observed_movement"],
            "thermal_evidence":thermal,"query_latency":query,
            "human_certified": False,
            "scenario_exit_ok":scenario_exit_ok,"clean_shutdown_logged":clean_shutdown_logged,
            "restore_logged":restored,
            "samples":len(rows), "failures":fail,
            "phase_stats":{k:{"n":len(v),"mean_mspt":round(mean(x[0] for x in v),2),
            "max_p95":max(x[1] for x in v),"max_p99":max(x[2] for x in v)} for k,v in groups.items()},
            "max_keepup_ms":max(map(int,warnings),default=0)}
def main():
    p=argparse.ArgumentParser()
    p.add_argument("log",type=Path); p.add_argument("metrics",type=Path)
    p.add_argument('--thermal',type=Path,help='On-device thermal.tsv evidence (required by scenario gate)')
    p.add_argument('--query-latency',type=Path,help='Monotonic query-latency.tsv evidence (required by V1 marker)')
    p.add_argument('--legacy-sample-only',action='store_true',help='Historical comparisons only; default requires scenario evidence')
    a=p.parse_args()
    thermal_rows=None
    if a.thermal:
        with a.thermal.open(encoding="utf-8-sig",newline="") as f:
            thermal_rows=list(csv.DictReader(f,delimiter="\t"))
    query_rows=None
    if a.query_latency:
        with a.query_latency.open(encoding="utf-8-sig",newline="") as f:
            query_rows=list(csv.DictReader(f,delimiter="\t"))
    with a.metrics.open(encoding="utf-8-sig",newline="") as f:
        result=assess(a.log.read_text(encoding="utf-8-sig"),list(csv.DictReader(f,delimiter="\t")),thermal_rows,query_rows)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if result['sample_gate_pass' if a.legacy_sample_only else 'scenario_gate_pass'] else 1
if __name__=="__main__": raise SystemExit(main())
