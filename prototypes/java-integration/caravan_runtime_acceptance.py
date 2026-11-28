"""Offline isolated Minecraft server acceptance. Carpet fake players; no human/native client claim."""
import time,re,uuid
def accept_caravan(send,wait,status,check,lines,process,RUN):
    bots=["caravanqa1","caravanqa2"]
    for n in bots:
        off=len(lines);send("player "+n+" spawn");wait(n+" joined the game",40,off)
    time.sleep(2)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_LOBBY")
    off=len(lines);send("arena kervan kanca otomatik");wait("Kervan kanca modu: otomatik",8,off)
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def until(n,pred,seconds=30):
        end=time.monotonic()+seconds;row=""
        while time.monotonic()<end:
            row=snap(n)
            if pred(row):return row
            time.sleep(.25)
        raise RuntimeError("Caravan state not reached "+row)
    def expect(row,*fields):
        assert all(f in row for f in fields),row
    def num(row,name):
        return float(re.search(r"\b"+name+r"=(-?\d+(?:\.\d+)?(?:E[-+]?\d+)?)",row)[1])
    def wagon(row):
        m=re.search(r"wagon=Position\[x=([^,]+), y=([^,]+), z=([^\]]+)\]",row)
        return tuple(float(v) for v in m.groups())
    def tp(n,x,y,z,tx=None,ty=None,tz=None):
        cmd=f"execute as {n} at @s run tp @s {x} {y} {z}"
        if tx is not None:cmd+=f" facing {tx} {ty} {tz}"
        send(cmd);time.sleep(.35)
    def cmd(n,action):
        send("execute as "+n+" run arena kervan "+action);time.sleep(.3)
    def team(index):
        off=len(lines);send("team list kp_arena_"+str(index));row=wait("Kırmızı" if index==0 else "Mavi",5,off)
        return [n for n in bots if n in row][0]
    r,b=team(0),team(1)
    a1,a2=snap(r),snap(b)
    expect(a1,"phase=LEG","leg=1","escort=RED","horses=1","map=")
    expect(a2,"horses=1","spectator=false")
    assert re.search(r"map=(\d+)",a1)[1]!=re.search(r"map=(\d+)",a2)[1],(a1,a2)
    print("PASS unique personal maps and one actual horse per player",flush=True)
    x,y,z=wagon(a1)
    check(f"execute as {r} at @s if block 0 {int(y)+4} -12 minecraft:stone_bricks if block 0 {int(y)+1} -12 minecraft:air","CARAVAN_TUNNEL")
    # Real raycast at the tunnel roof, no anchor injected.
    tp(b,.5,y,-11.5,.5,y+5,-11.5)
    send("player "+b+" hotbar 6");time.sleep(.25)
    hook=snap(b,"hook");expect(hook,"result=CONSUME","hookAttached=true")
    assert num(hook,"hookRemaining")>=590,hook
    until(b,lambda row:num(row,"posY")>y+.15,6)
    tp(b,x-2,y,z,x-.85,y+.5,z)
    cooled=snap(b,"hook");expect(cooled,"result=FAIL","hookAttached=false")
    assert num(cooled,"hookRemaining")>0,cooled
    print("PASS actual roof raycast, upward motion and persistent hook cooldown",flush=True)
    tp(b,x-2,y,z,x-.85,y+.5,z)
    expect(snap(b,"drive"),"result=FAIL","mounted=false")
    print("PASS attacker cannot claim wagon",flush=True)
    tp(b,x+2,y,z,x+.85,y+1.5,z)
    expect(snap(b,"cargo"),"result=SUCCESS","1:CARRIED")
    expect(snap(b,"cargo"),"result=FAIL","1:CARRIED")
    slow=until(b,lambda row:num(row,"speed")<.08)
    assert num(slow,"speed")>.05,slow
    print("PASS actual interaction, one crate per carrier, movement attribute",flush=True)
    off=len(lines);send("damage "+b+" 100 minecraft:generic_kill");wait("Applied",8,off)
    dead=until(b,lambda row:"spectator=true" in row,5);expect(dead,"1:DROPPED")
    ticks=num(dead,"ticks");time.sleep(1)
    assert num(snap(r),"ticks")<ticks,"1v1 death paused timer"
    print("PASS lethal damage drops actual crate and 1v1 timer continues",flush=True)
    tp(r,x+2,y,z,x+2,y+.5,z)
    expect(snap(r,"dropped"),"result=SUCCESS","1:CARRIED")
    until(r,lambda row:"1:ON_WAGON" in row,5)
    print("PASS defender retrieves dropped crate to wagon",flush=True)
    until(b,lambda row:"spectator=false" in row,20)
    expect(snap(b),"map=","horses=1")
    check("execute if items entity "+b+" hotbar.4 minecraft:filled_map","CARAVAN_RESPAWN_MAP")
    print("PASS natural respawn and kit reissue",flush=True)
    def drive(n,route):
        row=snap(n);x,y,z=wagon(row)
        tp(n,x-2,y,z,x-.85,y+.5,z)
        mounted=snap(n,"drive");expect(mounted,"result=SUCCESS","mounted=true");assert "driver=null" not in mounted,mounted
        snap(n,"forward")
        j=until(n,lambda row:"junction=true" in row,35)
        progress=num(j,"progress");assert abs(progress-8)<.001,j
        time.sleep(1)
        assert abs(num(snap(n),"progress")-progress)<.001
        cmd(n,route)
        until(n,lambda row:num(row,"progress")>progress+.1,12)
        print("PASS exact fork stop, authorized route selection and physical riding input "+route,flush=True)
    drive(r,"riskli")
    # Stop midway and steal a crate near the attack base. Real Carpet movement carries it home.
    mid=until(r,lambda row:wagon(row)[0]>=-.5,40)
    snap(r,"halt");time.sleep(.5);mid=snap(r);x,y,z=wagon(mid)
    tp(b,x,y,z+2,.5,y,15.5)
    expect(snap(b,"cargo"),"result=SUCCESS","1:CARRIED")
    send("player "+b+" move forward")
    try:
        delivered=until(b,lambda row:"1:STOLEN" in row,20)
    finally:
        send("player "+b+" stop")
    assert num(delivered,"speed")>.08,delivered
    print("PASS actual player movement to own base extracts one crate",flush=True)
    snap(r,"forward")
    result=until(r,lambda row:"red=LegResult" in row,50)
    expect(result,"delivered=19","stolen=1")
    print("PASS first escort naturally delivers 19 crates",flush=True)
    until(b,lambda row:"phase=LEG" in row and "leg=2" in row,15)
    snap(r,"halt")
    drive(b,"guvenli")
    won=until(b,lambda row:"phase=ENDED" in row,70)
    expect(won,"blue=LegResult","delivered=20","winner=BLUE")
    print("PASS second escort traverses tunnel and wins natural two-leg match",flush=True)
    status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_RETURN")
    ledger=RUN/"kapisma-arena-return.v1"
    text=ledger.read_text(encoding="utf-8")
    for n in bots:
        account=uuid.UUID(bytes=__import__("hashlib").md5(("OfflinePlayer:"+n).encode()).digest(),version=3)
        assert str(account) not in text,"pending caravan return"
        off=len(lines);send("player "+n+" kill");wait(n+" left the game",20,off)
    print("PASS no caravan pending return; fake players disconnected",flush=True)
