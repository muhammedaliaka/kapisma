"""Three matches with eight Carpet server-side players; same JVM, same roster.
Console fixture delivers same-entity callbacks in one server tick, not human clients.
"""
import re,time,os,statistics
def accept_repeat(send,wait,status,check,lines,process,RUN):
    bots=["caravanrepeat"+str(i) for i in range(1,9)]
    for n in bots:
        off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",40,off)
    time.sleep(1.5)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_REPEAT_READY")
    send("kapisma game KERVAN_BASKINI")
    seen_maps=set();seen_horses=set()
    soak=int(os.environ.get("KAPISMA_QA_SOAK_SECONDS","0"))
    assert soak in (0,45),"only bounded test durations supported"
    active_metrics=[];active_seconds=0
    def proof(op,marker):
        off=len(lines);send("arena-caravan-proof "+bots[0]+" "+op);return wait(marker,10,off).strip()
    def metrics(label):
        row=proof("metrics","CARAVAN_METRICS ")
        payload=row.split("CARAVAN_METRICS ")[1]
        print("OBS",label,payload,flush=True)
        return {k:float(v) for k,v in re.findall(r"(\w+)=([0-9.]+)",payload)}
    normal=proof("vanilla","CARAVAN_VANILLA ")
    assert "unchanged=true" in normal,normal
    normal_id=int(re.search(r"id=(\d+)",normal)[1])
    metrics("lobby-before")
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def field(row,k):return re.search(r"\b"+k+r"=([^ ]+)",row)[1]
    def team(i):
        off=len(lines);send("team list kp_arena_"+str(i));row=wait("Kırmızı" if i==0 else "Mavi",5,off)
        return [n for n in bots if re.search(r"\b"+n+r"\b",row)]
    def count(row,state):return len(re.findall(r"\d+:"+state+r"\b",row.split(" crates=")[1].split(" red=")[0]))
    for cycle in range(1,4):
        send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
        red,blue=team(0),team(1);assert len(red)==len(blue)==4
        rows=[snap(n) for n in bots]
        maps={field(row,"map") for row in rows};horses={field(row,"horseId") for row in rows}
        assert len(maps)==len(horses)==8 and "none" not in maps|horses and not maps&seen_maps and not horses&seen_horses
        assert all(count(row,"ON_WAGON")==20 and field(row,"driver")=="null" and float(field(row,"progress"))==0 for row in rows)
        seen_maps.update(maps);seen_horses.update(horses)
        print(f"PASS cycle {cycle}: fresh 20 crates, 8 maps and 8 horses, no previous state",flush=True)
        x,y,z=map(float,re.search(r"wagon=Position\[x=([^,]+), y=([^,]+), z=([^\]]+)\]",rows[0]).groups())
        for n in blue:send(f"execute as {n} at @s run tp @s {x+2} {y} {z} facing {x+.85} {y+1} {z}")
        time.sleep(.4)
        row=snap(blue[0],"race");assert field(row,"result")=="RACE_1_OF_4" and count(row,"CARRIED")==1,row
        for n in sorted(blue)[1:]:
            row=snap(n,"cargo");assert field(row,"result")=="SUCCESS",row
        assert count(row,"CARRIED")==4 and count(row,"ON_WAGON")==16,row
        for n in blue:
            row=snap(n,"cargo");assert field(row,"result")=="FAIL" and count(row,"CARRIED")==4,row
        print(f"PASS cycle {cycle}: same target 1/4, distinct targets 4 carriers, repeat rejected",flush=True)
        a=red[0]
        send(f"execute as {a} at @s run tp @s {x-2} {y} {z} facing {x-.85} {y+.5} {z}")
        time.sleep(.4);row=snap(a,"drive");assert field(row,"mounted")=="true",row
        snap(a,"forward");time.sleep(1)
        row=snap(a,"capture");assert float(field(row,"progress"))>0,row
        if soak:
            start=time.monotonic();initial=snap(a);last_ticks=int(field(initial,"ticks"))
            halted=False;chose=False;samples=0
            while time.monotonic()-start<soak:
                time.sleep(min(2,max(0,soak-(time.monotonic()-start))))
                row=snap(a)
                assert field(row,"phase")=="LEG" and field(row,"leg")=="1",row
                assert count(row,"CARRIED")==4 and count(row,"ON_WAGON")==16,row
                assert field(row,"mounted")=="true" and field(row,"map")==field(rows[bots.index(a)],"map"),row
                if field(row,"junction")=="true" and not chose:
                    snap(a,"neutral");snap(a,"right");chose=True
                if float(field(row,"progress"))>=24 and not halted:
                    snap(a,"halt");halted=True
                # A non-carrying escort is moved within the safe corridor to exercise personal map refresh.
                # This is an explicit server-side positioning fixture, not a native client movement test.
                send(f"execute as {red[1]} at @s run tp @s {-22 if samples%2==0 else -19} {y} -1.5")
                if time.monotonic()-start>=samples*5:
                    current=metrics("cycle-"+str(cycle)+"-active-"+str(samples))
                    assert int(field(row,"ticks"))<last_ticks or samples==0,row
                    last_ticks=int(field(row,"ticks"))
                    active_metrics.append(current);samples+=1
            elapsed=time.monotonic()-start
            active_seconds+=elapsed
            assert elapsed>=soak and samples>=9 and chose and halted,(elapsed,samples,chose,halted,row)
            assert float(field(row,"progress"))>=24 and field(row,"route")=="RISKY",row
            print(f"PASS cycle {cycle}: sustained {elapsed:.1f}s active, {samples} samples, 4 carriers and route progress {field(row,'progress')}",flush=True)
            # Leave active map/horse/scene references, but do not restart halted driver.
        else:
            time.sleep(5);metrics("cycle-"+str(cycle)+"-active")
        off=len(lines);send("save-all flush");wait("Saved the game",25,off)
        data=RUN/"arena-dev-proof"/"data"
        assert (data/("map_"+str(normal_id)+".dat")).is_file(),"vanilla control map not saved"
        assert all(not (data/("map_"+mid+".dat")).exists() for mid in seen_maps),"transient map persisted during active save"
        # Test deliberately leaves active forward fixture and 4 live carriers during cleanup.
        send("kapisma stop");status("yok (lobi)",60)
        check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_REPEAT_RETURN_"+str(cycle))
        assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
        off=len(lines);send("arena-caravan-proof "+a+" audit");audit=wait("CARAVAN_AUDIT ",8,off)
        assert int(field(audit,"entities"))>=26 and field(audit,"removed")==field(audit,"entities")==field(audit,"unregistered"),audit
        assert field(audit,"maps")==field(audit,"blank")==field(audit,"released")=="8" and field(audit,"forward")=="0" and field(audit,"registry").strip()=="0",audit
        assert "unchanged=true" in proof("vanilla","CARAVAN_VANILLA ")
        off=len(lines);send("save-all flush");wait("Saved the game",25,off)
        assert all(not (data/("map_"+mid+".dat")).exists() for mid in seen_maps),"released map persisted"
        metrics("cycle-"+str(cycle)+"-lobby")
        print(f"PASS cycle {cycle}: "+audit.strip().split("CARAVAN_AUDIT ")[1],flush=True)
    for n in bots:
        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
    print(f"PASS same JVM 3 matches: {len(seen_maps)} distinct map IDs, {len(seen_horses)} distinct horses; journal empty",flush=True)
    print("PASS vanilla control map persisted, all 24 transient maps absent from disk after active and lobby saves",flush=True)
    if soak:
        values=[r["mspt"] for r in active_metrics]
        p95s=[r["p95"] for r in active_metrics]
        heap=[r["heapUsedMiB"] for r in active_metrics]
        print(f"SOAK_SUMMARY seconds={active_seconds:.1f} samples={len(values)} msptMean={statistics.mean(values):.3f} msptMax={max(values):.3f} sampleP95Max={max(p95s):.3f} heapMinMiB={min(heap):.0f} heapMaxMiB={max(heap):.0f}",flush=True)
        assert active_seconds>=135 and len(values)>=27 and max(values)<50,(active_seconds,len(values),max(values))
        print("PASS sustained eight-actor workload, all sampled mean tick times under 50 ms",flush=True)
