"""Kapışma ULTRA profile planner. No filesystem writes; stdout JSON.
MiB values are actual measured physical/available memory, NOT advertised RAM.
Candidate tuning, not a validated device guarantee.
"""
import argparse
import json

def plan(total, available, cpus, platform):
    if not (isinstance(total, int) and isinstance(available, int) and isinstance(cpus, int)
            and 0 < available <= total and 1 <= cpus <= 256):
        raise ValueError("invalid memory/CPU measurement")
    if platform not in ("android", "desktop"):
        raise ValueError("unknown platform")
    mobile = platform == "android"
    small = total <= 4096 or available < 3072
    reserve = 896 if mobile else 1024
    # This is a starting envelope, not a measured native-memory upper bound.
    native = 512 if small else 768
    budget = available - reserve - native
    limit = 1280 if small else (3072 if mobile else 4096)
    heap = min(limit, total * (48 if mobile else 50) // 100, budget)
    heap = max(0, heap // 64 * 64)
    minimum = 1024 if small else 1536
    if heap < minimum:
        return {"ready": False, "reason": "insufficient available physical RAM; do not launch",
                "total_mib": total, "available_mib": available, "heap_candidate_mib": heap,
                "os_reserve_mib": reserve, "native_reserve_mib": native}
    threads = min(cpus, 4 if small else (6 if mobile else 8))
    view, simulation = (4, 3) if small else ((6, 4) if mobile else (8, 6))
    return {"ready": True, "status": "experimental", "platform": platform,
            "profile": "ultra3" if small else ("mobile6" if mobile else "desktop"),
            "total_mib": total, "available_mib": available,
            "heap_mib": heap, "os_reserve_mib": reserve, "native_reserve_mib": native,
            "java_flags": [f"-Xms{min(512, heap)}M", f"-Xmx{heap}M", "-XX:+UseG1GC",
                           f"-XX:ActiveProcessorCount={threads}", "-XX:MaxGCPauseMillis=150",
                           "-XX:+ParallelRefProcEnabled", "-Dfile.encoding=UTF-8",
                           "-Xlog:gc*:file=ultra-gc.log:time,uptime,level,tags:filecount=3,filesize=4M"],
            "properties_caps": {"max-players": 4, "view-distance": view,
                                "simulation-distance": simulation},
            "notes": ["Keep existing anti-cheat, loot, world and authentication rules.",
                      "Do not use this plan as evidence of real-device performance.",
                      "Properties are upper bounds; preserve stricter valid user settings."]}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--total-mib", type=int, required=True)
    p.add_argument("--available-mib", type=int, required=True)
    p.add_argument("--cpus", type=int, required=True)
    p.add_argument("--platform", choices=("android", "desktop"), required=True)
    a = p.parse_args()
    try:
        result = plan(a.total_mib, a.available_mib, a.cpus, a.platform)
    except ValueError as e:
        p.error(str(e))
    print(json.dumps(result, indent=2))
    return 0 if result["ready"] else 2

if __name__ == "__main__":
    raise SystemExit(main())
