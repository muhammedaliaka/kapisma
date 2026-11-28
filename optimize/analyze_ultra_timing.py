"""Summarize opt-in Java timing records; nested durations are not additive."""
import argparse
import json
import re
from collections import defaultdict
from pathlib import Path
from statistics import mean

RECORD=re.compile(r"ULTRA_TIMING phase=([a-z_.]+) elapsed_ns=(\d+)(?:\s|$)")
PHASES={"map.create","map.biome_preview","map.preview_slice","uhc.stock_maps","arena.prepare","arena.safe_surface",
        "arena.candidate","arena.candidate_slice","arena.initial_candidates","uhc.village_candidates","uhc.place_chests","uhc.find_depot","running.mode","running.minimap","running.compass"}

def summarize(text):
    groups=defaultdict(list)
    for line in text.splitlines():
        match=RECORD.search(line)
        if match:
            phase,ns=match.groups()
            if phase in PHASES:
                groups[phase].append(int(ns))
    return {"valid_records":sum(map(len,groups.values())),
            "phases":{name:{"count":len(samples),"total_ms":round(sum(samples)/1e6,3),
                "max_ms":round(max(samples)/1e6,3),"mean_ms":round(mean(samples)/1e6,3)}
                for name,samples in sorted(groups.items())},
            "note":"Nested phase durations overlap. Arena and stock_maps records include map calls. Prepare and safe_surface below 20ms, preview slices below 8ms, and candidate/chest/depot phases below 5ms omitted. Running phases below 2ms omitted. Counts for thresholded phases are not total calls."}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("log",type=Path)
    a=parser.parse_args()
    result=summarize(a.log.read_text(encoding="utf-8-sig"))
    print(json.dumps(result,indent=2))
    return 0 if result["valid_records"] else 2

if __name__=="__main__": raise SystemExit(main())
