"""Native role guide delivery, actual map lore and natural horse ownership callback."""
import re,time,uuid
from qa_clients import NativeClient
def accept_guide(send,wait,status,check,lines,process,RUN,clients):
    names=["ModNativeQA1","ModNativeQA2"];native={}
    base=RUN.parent.parent
    for n in names:
        off=len(lines)
        c=NativeClient(name=n,port=25580,game=RUN.parent/"native-load-clients"/n[-1],
            log=base/("caravan-guide-"+n+"-"+uuid.uuid4().hex[:12]+".log"),heap_mib=1200)
        clients.append(c);native[n]=c;wait(n+" joined the game",100,off);time.sleep(.7)
    send("arena kervan kanca manuel")
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def team(i):
        off=len(lines);send("team list kp_arena_"+str(i));row=wait("Kırmızı" if i==0 else "Mavi",5,off)
        return [n for n in names if n in row][0]
    r,b=team(0),team(1)
    def messages(n,*need):
        end=time.monotonic()+15
        while time.monotonic()<end:
            chat="\n".join(line for line in native[n].lines() if "[CHAT]" in line)
            if all(s in chat for s in need):return chat
            time.sleep(.3)
        raise AssertionError((n,need,chat[-2000:]))
    for n,role,action in [(r,"ESKORT · Arabayı","İleri (W)"),(b,"SALDIRGAN · Sandığı","Üssüne yürüyerek teslim")]:
        messages(n,"ESKORT · Sağ tık" if n==r else "SALDIRGAN · Sandığı al","5 Harita · 6 Kanca · 7 Kalkan")
        send("execute as "+n+" run arena kervan yardim")
        chat=messages(n,role,action,"5: Kişisel harita","tekrar sağ tıklayarak çekil")
        assert chat.count("ESKORT · Arabayı" if n==r else "SALDIRGAN · Sandığı ele geçir")==1,chat
        assert ("SALDIRGAN · Sandığı" if n==r else "ESKORT · Arabayı") not in chat
    print("PASS actual client received exactly one correct role guide and manual hook instructions",flush=True)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    for n in names:
        row=snap(n,"maplore");assert "result=GUIDE" in row,row
    print("PASS real map item has all six color legend lore lines",flush=True)
    for n in names:
        row=snap(n,"horseforeign");assert "result=DENIED" in row,row
        row=snap(n,"horseown");assert "result=MOUNTED" in row,row
    print("PASS actual vanilla horse mounting for owner and foreign rider rejection",flush=True)
    send("kapisma stop");status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in names),"CARAVAN_GUIDE_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in names:
        off=len(lines);native[n].close();wait(n+" left the game",25,off)
    print("PASS guide test cleanup and empty journal",flush=True)
