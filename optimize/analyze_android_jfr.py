"""Summarize JFR execution samples without treating sampling as exact CPU duration."""
import argparse
from collections import Counter
from datetime import datetime
import json
from pathlib import Path
import subprocess

def load_mappings(path):
    classes={}
    methods={}
    if path is None: return classes,methods
    owner=None
    with path.open(encoding="utf-8") as f:
        if f.readline().strip()!="tiny\t2\t0\tofficial\tintermediary\tnamed":
            raise ValueError("unexpected Tiny mapping namespaces")
        for line in f:
            p=line.rstrip("\n\r").split("\t")
            if p[0]=="c" and len(p)>=4:
                owner=p[2].replace("/",".")
                classes[owner]=p[3].replace("/",".")
            elif p[:2]==["","m"] and len(p)>=6 and owner:
                methods[(owner,p[4])]=p[5]
    return classes,methods

def summarize(events,mappings=None):
    classes,method_names=mappings or ({},{})
    threads=Counter()
    leaf=Counter()
    inclusive=Counter()
    families=Counter()
    gc=[]
    for event in events:
        values=event.get("values",{})
        kind=event.get("type","")
        if kind=="jdk.GarbageCollection":
            gc.append({"start":values.get("startTime"),"duration":values.get("duration"),"cause":values.get("cause")})
        if kind!="jdk.ExecutionSample":
            continue
        thread=values.get("sampledThread") or {}
        name=thread.get("javaName") or thread.get("osName") or "unknown"
        threads[name]+=1
        if name!="Server thread": continue
        frames=(values.get("stackTrace") or {}).get("frames") or []
        methods=[]
        for f in frames:
            m=f.get("method") or {}
            owner=(m.get("type") or {}).get("name","?").replace("/",".")
            name=m.get("name","?")
            methods.append(classes.get(owner,owner)+"."+method_names.get((owner,name),name))
        if not methods: continue
        leaf[methods[0]]+=1
        inclusive.update(set(methods))
        patterns={"map_preview":("renderBiomePreviewMap","centeredMap","stockMaps","giveWorldMap"),
                  "chunk_worldgen":("ChunkStatus","ChunkGenerator","NoiseChunk","ChunkMap","ChunkTask"),
                  "biome":("BiomeManager","getBiome","Climate","MultiNoiseBiomeSource"),
                  "kapisma":("tr.kapisma.",)}
        for family,patterns_ in patterns.items():
            if any(p in m for p in patterns_ for m in methods): families[family]+=1
    return {"execution_samples":sum(threads.values()),"server_samples":threads["Server thread"],
            "threads":threads.most_common(15),"server_leaf":leaf.most_common(25),
            "server_inclusive":inclusive.most_common(35),"overlapping_family_counts":dict(families),
            "gc_count":len(gc),"gc_events":gc[:30],
            "note":"Sample counts are not exact durations. Obfuscated names may hide family matches; counts overlap."}

def parse_time(value):
    return datetime.fromisoformat(value.replace("Z","+00:00"))

def window(events,start,end):
    if not start and not end: return events
    if not start or not end: raise ValueError("both window bounds required")
    start,end=parse_time(start),parse_time(end)
    if start>=end or start.tzinfo is None or end.tzinfo is None: raise ValueError("invalid offset-aware window")
    return [event for event in events if event.get("values",{}).get("startTime")
            and start<=parse_time(event["values"]["startTime"])<end]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("recording",type=Path)
    parser.add_argument("--mappings",type=Path)
    parser.add_argument("--start")
    parser.add_argument("--end")
    a=parser.parse_args()
    raw=subprocess.check_output(["jfr","print","--json","--stack-depth","64","--events","jdk.ExecutionSample,jdk.GarbageCollection",str(a.recording)])
    events=window(json.loads(raw)["recording"]["events"],a.start,a.end)
    result=summarize(events,load_mappings(a.mappings))
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if result["server_samples"] else 2
if __name__=="__main__": raise SystemExit(main())
