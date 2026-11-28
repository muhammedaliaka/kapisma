"""Classify bounded JVM thread snapshots; one stack is not duration or causality."""
import argparse,json,re
from pathlib import Path
from analyze_android_jfr import load_mappings

def assess(text,mappings=({},{})):
    classes,methods=mappings
    threads={}
    current=None
    for line in text.splitlines():
        m=re.match(r'^"([^"]+)"\s+#\d+',line)
        if m:
            current=m[1]
            if current in threads: raise ValueError("duplicate thread in snapshot")
            threads[current]={"state":None,"frames":[]}
            continue
        if current is None:continue
        m=re.search(r"java\.lang\.Thread\.State:\s*([A-Z_]+)",line)
        if m:threads[current]["state"]=m[1]
        m=re.match(r"\s+at ([\w.$]+)\.([\w$<>]+)\(",line)
        if m:
            o,n=m.groups();threads[current]["frames"].append(classes.get(o,o)+"."+methods.get((o,n),n))
    complete=bool(re.search(r"(?m)^probe_exit=0\s*$",text)) and "Full thread dump" in text
    server=threads.get("Server thread",{"state":None,"frames":[]})
    frames=server["frames"]
    def has(name):return any(name in frame for frame in frames)
    chunk_wait=complete and has("BlockableEventLoop.managedBlock") and has("ServerChunkCache") and has("LevelReader.getChunk")
    movement=chunk_wait and has("LivingEntity.travel") and has("Entity.getBlockPosBelowThatAffectsMyMovement")
    carpet=movement and has("carpet.patches.EntityPlayerMPFake")
    worker_threads={k:v for k,v in threads.items() if k.startswith("Worker-Main-")}
    workers={k:{"state":v["state"],"frames":v["frames"][:12]} for k,v in worker_threads.items()}
    def running_worker(*names):
        return complete and any(v["state"]=="RUNNABLE" and any(n in f for f in v["frames"] for n in names) for v in worker_threads.values())
    surface=running_worker("ChunkStatusTasks.generateSurface")
    worldgen=running_worker("ChunkStatusTasks.","ChunkGenerationTask.","WorldGenRegion.")
    collision=complete and has("BlockCollisions.") and (has("getCollisionShape") or has("CollisionGetter.findSupportingBlock"))
    return {"complete_snapshot":complete,"thread_count":len(threads),"server_state":server["state"],
            "server_chunk_wait_observed":chunk_wait,"movement_chunk_wait_observed":movement,
            "carpet_fake_player_path":carpet,"surface_generation_worker_observed":surface,
            "carpet_tick_observed":complete and has("carpet.patches.EntityPlayerMPFake"),
            "entity_collision_observed":collision,
            "apoli_collision_hook_observed":collision and has("apoli$overrideCollisionShapeQuery"),
            "mob_tick_observed":complete and (has("world.entity.Mob.") or has("world.entity.monster.")),
            "worldgen_worker_observed":worldgen,
            "server_frames":frames,"workers":workers,
            "note":"Single instant. Does not establish full stall duration, identity of the worker's chunk, or normal human movement causality."}
def main():
    p=argparse.ArgumentParser();p.add_argument("dump",type=Path);p.add_argument("--mappings",type=Path);a=p.parse_args()
    r=assess(a.dump.read_text(encoding="utf-8-sig"),load_mappings(a.mappings));print(json.dumps(r,indent=2))
    return 0 if r["complete_snapshot"] else 1
if __name__=="__main__":raise SystemExit(main())
