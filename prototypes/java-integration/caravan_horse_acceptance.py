"""Actual Fabric server and two native Java clients; horse manipulations are isolated console fixtures."""
import time,re,uuid
from qa_clients import NativeClient
def accept_horse(send,wait,status,check,lines,process,RUN,clients):
    names=["ModNativeQA1","ModNativeQA2"];native={};base=RUN.parent.parent
    def join(n):
        off=len(lines)
        c=NativeClient(name=n,port=25580,game=RUN.parent/"native-load-clients"/n[-1],
            log=base/("caravan-horse-"+n+"-"+uuid.uuid4().hex[:12]+".log"),heap_mib=1200)
        clients.append(c);native[n]=c;wait(n+" joined the game",100,off);time.sleep(.7)
    def leave(n):
        off=len(lines);native[n].close();wait(n+" left the game",25,off)
    for n in names:join(n)
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def field(row,k):return re.search(r"\b"+k+r"=([^ ]+)",row)[1]
    def until(n,pred,seconds=20):
        end=time.monotonic()+seconds;row=""
        while time.monotonic()<end:
            row=snap(n)
            if pred(row):return row
            time.sleep(.25)
        raise AssertionError(row)
    def team(i):
        off=len(lines);send("team list kp_arena_"+str(i));row=wait("Kırmızı" if i==0 else "Mavi",5,off)
        return [n for n in names if n in row][0]
    r,b=team(0),team(1)
    first={n:snap(n) for n in names}
    ids={n:field(row,"horseId") for n,row in first.items()}
    assert all(id!="none" for id in ids.values()) and len(set(ids.values()))==2
    def home(n):
        row=until(n,lambda x:field(x,"horseHome")=="true",8)
        assert field(row,"horseId")==ids[n] and field(row,"horses")=="1",row
        return row
    for n,op in [(r,"horseedge"),(b,"horsefall")]:
        row=snap(n,op);assert "result="+("EDGE" if op=="horseedge" else "FALL") in row,row
        home(n)
    print("PASS out-of-bounds and falling horses recalled with unchanged UUID and one entity per owner",flush=True)
    row=snap(b,"horsemountedcargo");assert "result=DENIED" in row and "1:ON_WAGON" in row,row
    x,y,z=map(float,re.search(r"wagon=Position\[x=([^,]+), y=([^,]+), z=([^\]]+)\]",row).groups())
    send(f"execute as {b} at @s run tp @s {x+2} {y} {z} facing {x+.85} {y+1.5} {z}");time.sleep(.5)
    row=snap(b,"cargo");assert "result=SUCCESS" in row and "1:CARRIED" in row,row
    row=snap(b,"horsecarrying");assert "result=DENIED" in row and "1:CARRIED" in row,row
    print("PASS horse-mounted cargo take and carrying-player mount blocked; walking take retained",flush=True)
    off=len(lines);send("damage "+b+" 100 minecraft:generic_kill");wait("Applied",8,off)
    dead=until(b,lambda x:"spectator=true" in x,4);assert "1:DROPPED" in dead,dead
    home(b)
    until(b,lambda x:"spectator=false" in x,20)
    home(b)
    print("PASS death and natural respawn retain one same horse at base",flush=True)
    row=snap(b,"horseaway");assert "result=AWAY" in row and "horseHome=false" in row,row
    leave(b);join(b)
    home(b)
    print("PASS real client disconnect and reconnect retain same horse at base",flush=True)
    send("kapisma stop");status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in names),"CARAVAN_HORSE_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in names:leave(n)
    print("PASS horse lifecycle test cleanup and empty journal",flush=True)
