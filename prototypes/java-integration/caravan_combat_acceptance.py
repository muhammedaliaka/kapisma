"""D21: actual server damage and teleport sequence; eight synthetic Carpet actors."""
import re,time,os
def accept_combat(send,wait,status,check,lines,process,RUN):
    bots=["caravancombat"+str(i) for i in range(1,9)]
    for n in bots:
        off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",40,off)
    # Join event precedes the delayed lobby kit application. Wait for all actors.
    time.sleep(1.5)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_COMBAT_READY")
    if os.environ.get("KAPISMA_QA_AUTO8")=="1":
        off=len(lines);send("arena kervan kanca otomatik");wait("Kervan kanca modu: otomatik",8,off)
    elif os.environ.get("KAPISMA_QA_HOOKOBSTACLE")=="1":
        off=len(lines);send("arena kervan kanca manuel");wait("Kervan kanca modu: manuel",8,off)
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def field(row,k):return re.search(r"\b"+k+r"=([^ ]+)",row)[1]
    def team(i):
        off=len(lines);send("team list kp_arena_"+str(i));row=wait("Kırmızı" if i==0 else "Mavi",5,off)
        return [n for n in bots if re.search(r"\b"+n+r"\b",row)]
    red,blue=team(0),team(1);assert len(red)==len(blue)==4
    rows={n:snap(n) for n in bots}
    if os.environ.get("KAPISMA_QA_EXTERNAL") in ("servercore","optimizers5"):
        row=snap(blue[0],"servercoreconfig")
        assert field(row,"result")=="OFF_14",row
        print("PASS actual ServerCore 14 behavior-sensitive switches disabled",flush=True)
    if os.environ.get("KAPISMA_QA_AUTO8")=="1":
        row=snap(blue[0],"auto8");assert field(row,"result").split(",")[0]=="8",row
        print("PASS eight automatic hooks launched in one server command",flush=True)
        samples=[]
        for _ in range(4):
            row=snap(blue[0],"auto8sample");samples.append(tuple(map(float,field(row,"result").split(","))))
            time.sleep(.1)
        print("OBS automatic hook samples "+repr(samples),flush=True)
        assert any(a==8 and v>0 for a,v,m in samples),samples
        assert all(m<=.721 for a,v,m in samples if a==8),samples
        print("PASS simultaneous automatic pull has upward velocity and bounded speed",flush=True)
        for n in bots:
            row=rows[n];send(f"execute as {n} at @s run tp @s {field(row,'posX')} {field(row,'posY')} {field(row,'posZ')}")
        time.sleep(.5)
        assert all(field(snap(n),"hookAttached")=="false" for n in bots)
        print("PASS teleport cancels all eight automatic hooks",flush=True)
    if os.environ.get("KAPISMA_QA_HOOKOBSTACLE")=="1":
        n=blue[0]
        try:
            row=snap(n,"hookobstacle");assert field(row,"hookAttached")=="true" and field(row,"result")=="CONSUME",row
            time.sleep(.6)
            row=snap(n);baseline=os.environ.get("KAPISMA_QA_HOOK_BASELINE")=="1"
            assert (field(row,"hookAttached")=="true")==baseline,row
            assert 0<int(field(row,"hookRemaining"))<600,row
            print("PASS "+("baseline rope persisted behind new block" if baseline else "new obstacle cancels rope, cooldown retained"),flush=True)
        finally:
            send(f"execute as {n} at @s run setblock 0 304 -12 minecraft:air")
        if not baseline:
            time.sleep(.3);row=snap(n,"hook")
            assert field(row,"hookAttached")=="false" and int(field(row,"hookRemaining"))>0,row
            print("PASS clearing obstacle does not bypass cooldown",flush=True)
        n=blue[1]
        send(f"execute as {n} at @s run tp @s -1.5 301 -11.5 0 -90")
        time.sleep(.4);row=snap(n,"hook")
        assert field(row,"hookAttached")=="true" and field(row,"result")=="CONSUME",row
        time.sleep(.5);row=snap(n)
        assert field(row,"hookAttached")=="true",row
        print("PASS unobstructed roof rope persists after manual pulse",flush=True)
    if os.environ.get("KAPISMA_QA_BENCH")=="1":
        n=blue[0]
        off=len(lines);send(f"execute as {n} at @s run setblock 1 301 22 polydecorations:dark_oak_bench");wait("Changed the block",8,off)
        send(f"execute as {n} at @s run tp @s 1.5 301 20.5 0 30")
        time.sleep(.5)
        row=snap(n,"benchguard")
        print("OBS bench normal gameMode "+field(row,"result"),flush=True)
        assert field(row,"result")=="FAIL/NONE",row
        print("PASS exact actual bench normal server block use denied",flush=True)
        row=snap(n,"benchpositive")
        print("OBS bench direct positive control "+field(row,"result"),flush=True)
        assert field(row,"mounted")=="true" and "polydecorations:" in field(row,"result"),row
        time.sleep(.4)
        row=snap(n)
        baseline=os.environ.get("KAPISMA_QA_BENCH_BASELINE")=="1"
        assert (field(row,"mounted")=="true")==baseline,row
        print("PASS "+("baseline foreign bench seat persisted" if baseline else "foreign bench seat automatically dismounted"),flush=True)
        snap(n,"benchleave")
        assert "1:ON_WAGON" in snap(n)
        if not baseline:
            row=snap(n,"horseown");assert field(row,"result")=="MOUNTED",row
            print("PASS legitimate owned horse still mountable",flush=True)
            # Real wagon seat survives normal ticking; not a client movement test.
            r=red[0];send(f"execute as {r} at @s run tp @s -25.5 301 .5 -90 0")
            time.sleep(.5);row=snap(r,"drive")
            assert field(row,"result")=="SUCCESS" and field(row,"mounted")=="true",row
            time.sleep(.5);row=snap(r)
            assert field(row,"mounted")=="true" and field(row,"seatGeometry")=="true",row
            print("PASS legitimate wagon seat persists",flush=True)
    if os.environ.get("KAPISMA_QA_HORSESTATS")=="1":
        attrs=[tuple(map(float,field(snap(n,"horsestats"),"result").split(","))) for n in bots]
        print("OBS horse stats "+repr(attrs),flush=True)
        assert len(set(attrs))==1,attrs
        print("PASS eight real arena horses have identical movement speed, jump and health",flush=True)
    if os.environ.get("KAPISMA_QA_COVER")=="1":
        checks=[(-24,-13,"spruce_planks"),(-24,-11,"spruce_planks"),(-24,-7,"spruce_planks"),(-24,-5,"spruce_planks"),(-24,-9,"air"),(-4,10,"stone_bricks"),(-2,10,"stone_bricks"),(2,10,"stone_bricks"),(4,10,"stone_bricks"),(0,10,"air")]
        check("execute as "+red[0]+" at @s "+" ".join(f"if block {x} {h} {z} minecraft:{block}" for x,z,block in checks for h in (301,302)),"CARAVAN_COVER_BLOCKS")
        print("PASS actual paired screen blocks and open central gaps",flush=True)
        ray=snap(red[0],"coverray");assert field(ray,"result")=="BLOCK_CLEAR_BLOCK_CLEAR",ray
        print("PASS actual vanilla eye rays blocked by both screens and clear through both central gaps",flush=True)
    if os.environ.get("KAPISMA_QA_VERTICAL")=="1":
        row=snap(red[0],"verticaldrop");assert field(row,"result")=="OLD5_RELOCATED5_DIRECT5_AIR5",row
        print("PASS real summit landing: old 5 denied/relocated, new 5 direct and 5 airborne landings",flush=True)
    if os.environ.get("KAPISMA_QA_DROPSTRESS")=="1":
        w=rows[red[0]]
        x0,y0,z0=map(float,re.search(r"wagon=Position\[x=([^,]+), y=([^,]+), z=([^\]]+)\]",w).groups())
        def carrier_setup(n):
            send(f"execute as {n} at @s run tp @s {x0+2} {y0} {z0} facing {x0+.85} {y0+1} {z0}")
            time.sleep(.4)
            row=snap(n,"cargo");assert field(row,"result")=="SUCCESS",row
            snap(n,"summitplace");time.sleep(.3)
            row=snap(n)
            coords=field(row,"cargoCoords")
            m=re.search(r"(\d+):CARRIED:19\.5,309\.0,12\.5",coords)
            assert m,row
            return m[1]
        for idx,n in enumerate(blue[:2]):
            crate=carrier_setup(n)
            if idx==1:
                snap(n,"airplace");time.sleep(.12)
                row=snap(n)
                m=re.search(r"\b"+crate+r":CARRIED:19\.5,([0-9.]+),12\.5",field(row,"cargoCoords"))
                assert m and 314<float(m[1])<319.1,row
            off=len(lines);send("damage "+n+" 100 minecraft:generic_kill");wait("Applied",8,off)
            row=snap(n)
            assert field(row,"spectator")=="true" and field(row,"phase")=="LEG" and crate+":DROPPED:19.5,309.0,12.5" in field(row,"cargoCoords"),row
            grouped=os.environ.get("KAPISMA_QA_GROUPED")=="1"
            condition="unless" if grouped and idx==1 else "if"
            check(f"execute as {red[0]} at @s positioned 19.5 309 12.5 {condition} entity @e[type=minecraft:interaction,tag=kapisma.caravan.DROPPED,tag=kapisma.caravan.crate.{crate},distance=..0.2]","CARAVAN_DROP_ENTITY_"+crate)
            before=int(field(row,"ticks"));time.sleep(.4);after=snap(red[0])
            assert int(field(after,"ticks"))<before and "STOLEN" not in after.split(" crates=")[1].split(" red=")[0],after
            print("PASS "+("summit death direct drop" if idx==0 else "forced high-air death restored last safe summit, same crate, clock advances"),flush=True)
        if os.environ.get("KAPISMA_QA_GROUPED")=="1":
            for i,n in enumerate(blue[2:4],1):
                send(f"execute as {n} at @s run tp @s 18.5 309 12.5 facing 19.5 309.5 12.5")
                time.sleep(.4)
                row=snap(n,"dropped")
                assert field(row,"result")=="SUCCESS" and f"{i}:CARRIED:" in field(row,"cargoCoords"),row
                check(f"execute as {red[0]} at @s unless entity @e[type=minecraft:interaction,tag=kapisma.caravan.DROPPED,tag=kapisma.caravan.crate.{i}]","CARAVAN_GROUP_REMOVED_"+str(i))
                if i==1:check(f"execute as {red[0]} at @s positioned 19.5 309 12.5 if entity @e[type=minecraft:interaction,tag=kapisma.caravan.DROPPED,tag=kapisma.caravan.crate.2,distance=..0.2]","CARAVAN_GROUP_NEXT")
            print("PASS two overlapping crates picked up sequentially with distinct identities",flush=True)
        row=snap(red[0],"pendingdrop");assert field(row,"result")=="PENDING_CLOCK_CONTINUES",row
        time.sleep(1.3)
        row=snap(red[0],"pendingretry");assert field(row,"result")=="RESTORED_SINGLE_HIT",row
        print("PASS no safe landing deferred without clock freeze; restored after retry with one hit",flush=True)
    victim=max(red,key=lambda n:abs(float(field(rows[n],"posX"))+28.5))
    x=float(field(rows[victim],"posX"));z=float(field(rows[victim],"posZ"));y=float(field(rows[victim],"posY"))
    assert (x+28.5)**2+(z+8.5)**2>9,(victim,x,z)
    assert field(snap(victim,"friendlyhit"),"result").startswith("DENIED_20.0_20.0")
    hit=snap(victim,"enemyhit");assert field(hit,"result").startswith("APPLIED_20.0_") and float(field(hit,"health"))<20,hit
    time.sleep(1.1)
    hit=snap(victim,"environmenthit");assert field(hit,"result").startswith("APPLIED_"),hit
    print("PASS real friendly damage denied, enemy and environmental damage applied",flush=True)
    off=len(lines);send("damage "+victim+" 100 minecraft:generic_kill");wait("Applied",8,off)
    row=snap(victim);assert field(row,"spectator")=="true" and field(row,"cameraSelf")=="true",row
    a=int(field(row,"tpSeq"));time.sleep(1.0);row=snap(victim);delta=int(field(row,"tpSeq"))-a
    baseline=os.environ.get("KAPISMA_QA_COMBAT_BASELINE")=="1"
    print(f"OBS outer slot {victim} spawn=({x},{y},{z}) sequenceDelta={delta} baseline={baseline}",flush=True)
    if baseline:
        assert delta>=5,row
        print("PASS reproduced redundant spectator teleports at assigned spawn",flush=True)
    else:
        assert delta==0,row
        print("PASS assigned outer spawn no redundant teleports during waiting",flush=True)
    row=snap(victim,"enemyhit");assert field(row,"result").startswith("DENIED_20.0_20.0"),row
    send(f"execute as {victim} at @s run tp @s 0.5 {y+2} 0.5")
    time.sleep(.5);row=snap(victim)
    assert field(row,"spectator")=="true" and field(row,"cameraSelf")=="true" and abs(float(field(row,"posX"))-x)<.01 and abs(float(field(row,"posZ"))-z)<.01,row
    print("PASS spectator cannot leave assigned spawn vicinity; damage denied",flush=True)
    end=time.monotonic()+20
    while time.monotonic()<end:
        row=snap(victim)
        if field(row,"spectator")=="false":break
        time.sleep(.25)
    else:raise AssertionError(row)
    assert field(row,"health")=="20.0" and field(row,"map")==field(rows[victim],"map"),row
    time.sleep(1.1);hit=snap(victim,"enemyhit");assert field(hit,"result").startswith("APPLIED_"),hit
    print("PASS natural respawn restores kit/map, normal enemy damage returns",flush=True)
    send("kapisma stop");status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_COMBAT_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in bots:
        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
    print("PASS combat fixture cleanup and empty journal",flush=True)
