"""Native client framebuffer screenshots and actual vanilla horse key input."""
import time,re,uuid,hashlib
from qa_clients import NativeClient
def accept_visual(send,wait,status,check,lines,process,RUN,clients):
    names=["ModNativeQA1","ModNativeQA2"];native={};base=RUN.parent.parent
    for n in names:
        off=len(lines)
        c=NativeClient(name=n,port=25580,game=RUN.parent/"native-load-clients"/n[-1],
            log=base/("caravan-visual-"+n+"-"+uuid.uuid4().hex[:12]+".log"),heap_mib=1200,caravan=True)
        clients.append(c);native[n]=c;wait(n+" joined the game",100,off);time.sleep(.7)
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def field(row,k):return re.search(r"\b"+k+r"=([^ ]+)",row)[1]
    def num(row,k):return float(field(row,k))
    def until(n,pred,seconds=15):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            row=snap(n)
            if pred(row):return row
            time.sleep(.25)
        raise AssertionError(row)
    def team(i):
        off=len(lines);send("team list kp_arena_"+str(i));row=wait("Kırmızı" if i==0 else "Mavi",5,off)
        return [n for n in names if n in row][0]
    r,b=team(0),team(1)
    c=native[r];row=snap(r);y=num(row,"horseY")
    check("execute "+" ".join("if items entity "+n+" hotbar.6 minecraft:shield unless items entity "+n+" weapon.offhand *" for n in names),"CARAVAN_MAP_FREE_HAND")
    for n,role in [(r,"ESKORT · Sağ tık"),(b,"SALDIRGAN · Sandığı al")]:
        end=time.monotonic()+10
        while time.monotonic()<end:
            chat="\n".join(l for l in native[n].lines() if "[CHAT]" in l)
            if role in chat and "5 Harita · 6 Kanca · 7 Kalkan" in chat:break
            time.sleep(.25)
        else:raise AssertionError(chat[-2000:])
        assert chat.count(role)==1 and "Üssüne yürüyerek teslim" not in chat,chat
    print("PASS concise role-specific start guide and shield in slot 7",flush=True)
    time.sleep(11)
    terrain=snap(r)
    known=num(terrain,"terrainKnown")
    assert 400<known<800 and num(terrain,"terrainOutside")==0 and num(terrain,"terrainMin")==y and y+5<=num(terrain,"terrainMax")<=y+11,terrain
    assert all(num(terrain,k)>0 for k in ("terrainLow","terrainMid","terrainHigh")),terrain
    print("PASS actual arena-only elevation samples: "+ " ".join(k+"="+field(terrain,k) for k in ("terrainKnown","terrainOutside","terrainLow","terrainMid","terrainHigh","terrainMin","terrainMax")),flush=True)
    for n in names:
        row=snap(n)
        assert num(row,"escortBasePixels")>=20 and num(row,"attackBasePixels")>=12,row
        assert "result=GUIDE" in snap(n,"maplore"),row
    print("PASS both actual maps contain public base symbols and updated legend",flush=True)
    def shot(name):
        c.trigger.write_text("shot-"+name,encoding="ascii")
        png=c.trigger.parent/"screenshots"/(c.trigger.name+"-shot-"+name+".png")
        end=time.monotonic()+20
        while time.monotonic()<end:
            if png.is_file():
                data=png.read_bytes()
                if data.startswith(b"\x89PNG\r\n\x1a\n") and data.endswith(b"IEND\xaeB\x60\x82"):
                    print("FRAMEBUFFER",str(png),hashlib.sha256(data).hexdigest(),flush=True);return
            time.sleep(.25)
        raise AssertionError("missing screenshot "+str(png))
    send(f"execute as {r} at @s run tp @s -27.5 {y} 4.5 facing -23.5 {y+.9} .5")
    time.sleep(1);shot("wagon")
    send(f"execute as {r} at @s run tp @s -27.5 {y} 4.5 -135 65")
    time.sleep(1);shot("map")
    c.trigger.write_text("idle",encoding="ascii")
    print("PASS actual client framebuffer wagon and map screenshots saved",flush=True)
    row=snap(r,"horsedrive");assert "result=MOUNTED" in row and field(row,"horseRider")=="true",row
    id=field(row,"horseId")
    for i in range(3):
        before=num(snap(r),"horseX")
        c.trigger.write_text("horse:-90",encoding="ascii")
        row=until(r,lambda row:num(row,"horseX")>before+1.2,8)
        assert field(row,"horseId")==id and field(row,"horseRider")=="true",row
        c.trigger.write_text("idle",encoding="ascii");time.sleep(1.3)
        x=num(snap(r),"horseX");time.sleep(.8)
        row=snap(r);assert abs(num(row,"horseX")-x)<.35 and field(row,"horseRider")=="true",row
    print("PASS three actual native horse movement and stop intervals; same mounted horse",flush=True)
    row=snap(r,"horseedge");assert "result=EDGE" in row,row
    row=until(r,lambda row:field(row,"horseHome")=="true" and field(row,"horseRider")=="false",8)
    assert field(row,"horseId")==id and field(row,"horses")=="1",row
    print("PASS mounted boundary recall ejects rider and returns same horse",flush=True)
    send("execute as "+r+" run arena kervan yardim")
    end=time.monotonic()+10
    while time.monotonic()<end:
        chat="\n".join(l for l in native[r].lines() if "[CHAT]" in l)
        if "Büyük harita için sol el boş olmalı" in chat and "İleri (W)" in chat:break
        time.sleep(.2)
    else:raise AssertionError(chat[-2000:])
    print("PASS optional detailed guide delivered on request",flush=True)
    send("kapisma stop");status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in names),"CARAVAN_VISUAL_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in names:
        off=len(lines);native[n].close();wait(n+" left the game",25,off)
    print("PASS visual horse test cleanup and empty journal",flush=True)
