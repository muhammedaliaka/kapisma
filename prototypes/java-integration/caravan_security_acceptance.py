"""Real named server, two actual Java client JVMs; server-side interaction fixture and actual reconnect."""
import re,time,uuid
from qa_clients import NativeClient
def accept_security(send,wait,status,check,lines,process,RUN,clients):
    base=RUN.parent.parent
    bots=["ModNativeQA1","ModNativeQA2"]
    native={}
    def join(n):
        off=len(lines)
        c=NativeClient(name=n,port=25580,game=RUN.parent/"native-load-clients"/n[-1],
            log=base/("caravan-security-"+n+"-"+uuid.uuid4().hex[:12]+".log"),heap_mib=1200)
        clients.append(c);native[n]=c
        wait(n+" joined the game",100,off);time.sleep(1)
    def leave(n):
        off=len(lines);native[n].close();wait(n+" left the game",25,off);time.sleep(.6)
    for n in bots:join(n)
    time.sleep(1)
    off=len(lines);send("arena kervan kanca manuel");wait("Kervan kanca modu: manuel",8,off)
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def field(row,k):
        return re.search(r"\b"+k+r"=([^ ]+)",row)[1]
    def until(n,pred,seconds=20):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            row=snap(n)
            if pred(row):return row
            time.sleep(.2)
        raise RuntimeError("state not reached "+row)
    def team(index):
        off=len(lines);send("team list kp_arena_"+str(index));row=wait("Kırmızı" if index==0 else "Mavi",5,off)
        return [n for n in bots if n in row][0]
    r,b=team(0),team(1)
    a,z=snap(r),snap(b)
    assert int(field(a,"selfPixels"))>0 and int(field(z,"selfPixels"))>0,(a,z)
    assert field(a,"map")!=field(z,"map") and field(a,"mapHash")!=field(z,"mapHash"),(a,z)
    print("PASS actual personal map pixel buffers show both players at their separate bases",flush=True)
    for n in bots:
        assert "result=PROTECTED" in snap(n,"mapdrop")
        assert "result=PROTECTED" in snap(n,"mapmove")
    print("PASS actual drop, throw, and outside cursor map transfers blocked",flush=True)
    y=float(re.search(r"wagon=Position\[x=[^,]+, y=([^,]+)",a)[1])
    send(f"execute as {b} at @s run tp @s .5 {y} -11.5 facing .5 {y+5} -11.5")
    time.sleep(.4)
    row=snap(b,"ordinaryblock")
    assert "result=FAIL" in row and "hookAttached=false" in row,row
    row=snap(b,"hookblock")
    assert "result=CONSUME" in row and "hookAttached=true" in row,row
    print("PASS actual block-use handler permits tagged hook only",flush=True)
    row=snap(b,"hook")
    # The second press can arrive inside the rate limit or after it; no new cooldown can be started.
    assert 0<int(field(row,"hookRemaining"))<=600,row
    if "result=CONSUME" not in row:
        row=until(b,lambda x:"hookAttached=true" in x,2)
        row=snap(b,"hook")
    assert "result=CONSUME" in row,row
    print("PASS manual hook launch and repeated press accepted by actual item handler",flush=True)
    send(f"execute as {b} at @s run tp @s -21.5 {y} .5 facing -22.65 {y+1.5} .5")
    time.sleep(.5)
    row=snap(b,"cargo");assert "1:CARRIED" in row,row
    before=snap(r)
    leave(b)
    row=snap(r);assert "1:DROPPED" in row,row
    frozen=int(field(row,"ticks"));time.sleep(1.2)
    assert int(field(snap(r),"ticks"))==frozen
    print("PASS disconnect drops cargo and freezes 1v1 match",flush=True)
    join(b)
    row=until(b,lambda x:"spectator=false" in x and "1:DROPPED" in x)
    assert field(row,"map")==field(z,"map"),row
    assert int(field(row,"hookRemaining"))>0,row
    assert int(field(snap(r),"ticks"))<frozen
    print("PASS reconnect retains personal map and cooldown, no duplicate cargo; match resumes",flush=True)
    # A death during a disconnected team must retain the pending timer.
    off=len(lines);send("damage "+b+" 100 minecraft:generic_kill");wait("Applied",8,off)
    dead=until(b,lambda x:"spectator=true" in x,4)
    leave(b);time.sleep(2)
    join(b)
    again=snap(b);assert "spectator=true" in again,again
    until(b,lambda x:"spectator=false" in x,20)
    check("execute if items entity "+b+" hotbar.4 minecraft:filled_map","CARAVAN_SECURITY_RESPAWN")
    print("PASS reconnect during death cannot bypass respawn wait",flush=True)
    send("kapisma stop");status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in bots),"CARAVAN_SECURITY_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in bots:leave(n)
    print("PASS security test cleanup and empty journal",flush=True)
