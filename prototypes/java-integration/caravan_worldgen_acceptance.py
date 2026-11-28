"""D36 isolated natural world benchmark. Console only, no players or live data."""
import time,re
def accept_worldgen(send,wait,lines,process,RUN,boot_start):
    print("OBS natural boot through registry smoke seconds="+str(round(time.monotonic()-boot_start,3)),flush=True)
    target=RUN/"arena-dev-proof"/"region"/"r.2.2.mca"
    assert not target.exists(),"target region must be fresh"
    def sample():
        off=len(lines);send("arena-caravan-proof none worldgen")
        row=wait("WORLDGEN_FULL count=",10,off)
        fields=dict(re.findall(r"(count|seed|cpuMs|heapMiB)=(-?\d+)",row))
        assert fields["seed"]=="42",row
        return {k:int(v) for k,v in fields.items()}
    baseline=sample()
    assert baseline["count"]==0 and baseline["cpuMs"]>=0,baseline
    print("PASS actual normal noise generator seed42 and zero loaded targets",flush=True)
    started=time.monotonic();send("forceload add 1024 1024 1119 1087")
    samples=[]
    try:
        while time.monotonic()-started<120:
            row=sample();samples.append(row)
            print("OBS WORLDGEN elapsed="+str(round(time.monotonic()-started,3))+" "+str(row),flush=True)
            if row["count"]==24:break
            time.sleep(.3)
        else:raise AssertionError("24 full chunks not reached")
        print("WORLDGEN_RESULT seconds="+str(round(time.monotonic()-started,3))+" cpuDeltaMs="+str(row["cpuMs"]-baseline["cpuMs"])+" samples="+str(len(samples))+" sampledHeapMaxMiB="+str(max(s["heapMiB"] for s in samples)),flush=True)
        print("PASS 24 remote target chunks FULL",flush=True)
        off=len(lines);send("save-all flush");wait("Saved the game",60,off)
        with target.open("rb") as f:header=f.read(4096)
        assert len(header)==4096
        assert all(int.from_bytes(header[4*(x+32*z):4*(x+32*z)+4],"big")!=0 for x in range(6) for z in range(4))
        print("PASS all 24 target chunk entries persisted",flush=True)
    finally:
        send("forceload remove 1024 1024 1119 1087")
