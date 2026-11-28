"""Eight server-side Carpet players in the real named Fabric JVM.
Input packet handlers, map counters, physical route and cleanup; not eight human clients.
"""
import re,time,statistics
def accept_load(send,wait,status,check,lines,process,RUN):
    bots=["caravanload"+str(i) for i in range(1,9)]
    for n in bots:
        off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",30,off)
    time.sleep(1)
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def field(row,k):return re.search(r"\b"+k+r"=([^ ]+)",row)[1]
    def num(row,k):return float(field(row,k))
    def until(n,pred,seconds=30):
        end=time.monotonic()+seconds;row=""
        while time.monotonic()<end:
            row=snap(n)
            if pred(row):return row
            time.sleep(.3)
        raise AssertionError(row)
    def team(i):
        off=len(lines);send("team list kp_arena_"+str(i));row=wait("Kırmızı" if i==0 else "Mavi",5,off)
        return [n for n in bots if re.search(r"\b"+n+r"\b",row)]
    red,blue=team(0),team(1)
    assert len(red)==len(blue)==4,(red,blue)
    a,b=red[0],blue[0]
    time.sleep(3)
    initial={n:snap(n) for n in bots}
    assert len({field(row,"map") for row in initial.values()})==8
    assert all(field(row,"horses")=="1" and num(row,"selfPixels")>0 for row in initial.values())
    print("PASS eight players, balanced teams, unique maps and eight real horses",flush=True)
    time.sleep(10)
    idle={n:snap(n) for n in bots}
    skip=sum(num(idle[n],"skipped")-num(initial[n],"skipped") for n in bots)
    render=sum(num(idle[n],"rendered")-num(initial[n],"rendered") for n in bots)
    writes=sum(num(idle[n],"pixelWrites")-num(initial[n],"pixelWrites") for n in bots)
    unchanged=[n for n in bots if num(idle[n],"rendered")==num(initial[n],"rendered")]
    assert len(unchanged)>=4 and all(field(idle[n],"mapHash")==field(initial[n],"mapHash") and num(idle[n],"pixelWrites")==num(initial[n],"pixelWrites") for n in unchanged)
    assert skip>=80 and skip/(skip+render)>=.90 and writes<=render*60,(skip,render,writes)
    print(f"PASS low-activity map cache skipped={int(skip)} rendered={int(render)} pixelWrites={int(writes)} unchangedPlayers={len(unchanged)} avoidedPixelScans={int(skip)*16384}",flush=True)
    x,y,z=map(float,re.search(r"wagon=Position\[x=([^,]+), y=([^,]+), z=([^\]]+)\]",initial[a]).groups())
    send(f"execute as {a} at @s run tp @s {x-2} {y} {z} facing {x-.85} {y+.5} {z}")
    time.sleep(.4)
    row=snap(a,"drive");assert "result=SUCCESS" in row and "mounted=true" in row,row
    snap(a,"right")
    assert "junction=false" in snap(a)
    snap(a,"forward")
    row=until(a,lambda row:"junction=true" in row,30)
    assert abs(num(row,"progress")-8)<.001 and num(row,"selfPixels")>0,row
    print("PASS mounted self marker visible on actual vanilla map",flush=True)
    snap(b,"right");assert "junction=true" in snap(a),"foreign input changed fork"
    # Neutral from continuous forward fixture arms; valid right choice is routed by actual packet handler.
    snap(a,"neutral");snap(a,"right")
    row=until(a,lambda row:"junction=false" in row and num(row,"progress")>8.1,15)
    assert "route=RISKY" in row,row
    print("PASS sideways packet selects risky route only at fork, foreign input denied",flush=True)
    start=num(row,"progress");start_ticks=num(row,"ticks")
    time.sleep(12)
    row=snap(a);distance=num(row,"progress")-start;elapsed=start_ticks-num(row,"ticks")
    assert distance>5 and abs(distance-.075*elapsed)<.15,(distance,elapsed,row)
    active={n:snap(n) for n in bots}
    assert all(num(row,"selfPixels")>0 for row in active.values())
    print(f"PASS eight-player moving map and fixed-speed route; activeTicks={elapsed:.0f} distance={distance:.3f}",flush=True)
    snap(a,"halt")
    send("kapisma stop");status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_LOAD_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in bots:
        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
    print("PASS eight-player cleanup and empty return journal",flush=True)
