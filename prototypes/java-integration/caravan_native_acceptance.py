"""Two native Java clients, real vanilla W/A/D keys, no server QA input injection.
Mounting is a controlled fixture, not mouse-click or human proof.
"""
import time,re,uuid,hashlib
from qa_clients import NativeClient
def accept_native(send,wait,status,check,lines,process,RUN,clients):
    names=["ModNativeQA1","ModNativeQA2"];native={}
    base=RUN.parent.parent
    for n in names:
        off=len(lines)
        c=NativeClient(name=n,port=25580,game=RUN.parent/"native-load-clients"/n[-1],
            log=base/("caravan-drive-"+n+"-"+uuid.uuid4().hex[:12]+".log"),
            caravan=True,heap_mib=1200)
        clients.append(c);native[n]=c;wait(n+" joined the game",100,off);time.sleep(.7)
    time.sleep(1)
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    def snap(n,op="snapshot"):
        off=len(lines);send("arena-caravan-proof "+n+" "+op);return wait("CARAVANQA "+n+" op="+op,8,off).strip()
    def field(row,k):return re.search(r"\b"+k+r"=([^ ]+)",row)[1]
    def progress(row):return float(field(row,"progress"))
    def until(n,pred,seconds=20):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            row=snap(n)
            if pred(row):return row
            time.sleep(.3)
        raise RuntimeError("state not reached "+row)
    def team(index):
        off=len(lines);send("team list kp_arena_"+str(index));row=wait("Kırmızı" if index==0 else "Mavi",5,off)
        return [n for n in names if n in row][0]
    r,b=team(0),team(1)
    def client_map(n,row,seconds=15):
        wanted=f"CARAVAN_CLIENT_MAP {field(row,'map')} {field(row,'mapHash')} 16384"
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            if any(wanted in line for line in native[n].lines()):return
            time.sleep(.25)
        raise AssertionError("client did not receive "+wanted+"; tail="+str(native[n].lines()[-5:]))
    for n in names:native[n].trigger.write_text("idle",encoding="ascii")
    time.sleep(3)
    first={n:snap(n) for n in names}
    assert len({field(row,"map") for row in first.values()})==2
    assert len({field(row,"mapHash") for row in first.values()})==2
    for n,row in first.items():client_map(n,row)
    print("PASS both actual clients received their distinct 16384-pixel personal map frames",flush=True)
    y=int(float(re.search(r"wagon=Position\[x=[^,]+, y=([^,]+)",first[r])[1]))
    marks=[(-33,-9,"purple_concrete"),(3,15,"orange_concrete"),(24,0,"gold_block"),(-16,-5,"green_concrete"),(-10,3,"red_concrete")]
    check(f"execute as {r} at @s "+" ".join(f"if block {x} {y-1} {z} minecraft:{block} if block {x} {y} {z} minecraft:air" for x,z,block in marks),"CARAVAN_OBJECTIVE_MARKS")
    print("PASS all five world objective colors present with open walking clearance",flush=True)
    structures=[(-32,1,-15,"spruce_log"),(-29,4,-16,"spruce_planks"),(-29,5,-16,"purple_wool"),(3,1,21,"stone_bricks"),(0,4,22,"dark_oak_planks"),(0,5,22,"orange_wool"),(-29,2,-16,"air"),(0,2,22,"air")]
    check(f"execute as {r} at @s "+" ".join(f"if block {x} {y-1+h} {z} minecraft:{block}" for x,h,z,block in structures),"CARAVAN_ROLE_LANDMARKS")
    print("PASS both actual shelters have distinct materials and open interiors",flush=True)
    accents=[(-32,2,-15,"stripped_spruce_log"),(-32,4,-15,"dark_oak_planks"),(-31,5,-16,"purple_terracotta"),(3,2,21,"chiseled_stone_bricks"),(3,4,21,"smooth_stone"),(2,5,22,"orange_terracotta")]
    check(f"execute as {r} at @s "+" ".join(f"if block {x} {y-1+h} {z} minecraft:{block}" for x,h,z,block in accents),"CARAVAN_ARCHITECTURE_ACCENTS")
    portals=[(x,h,z,block) for x in (-6,6) for h,z,block in [(1,-16,"stone_bricks"),(1,-8,"stone_bricks"),(5,-12,"chiseled_stone_bricks"),(3,-12,"air")]]
    check(f"execute as {r} at @s "+" ".join(f"if block {x} {y-1+h} {z} minecraft:{block}" for x,h,z,block in portals),"CARAVAN_TUNNEL_PORTALS")
    print("PASS both physical masonry tunnel portals and clear passage",flush=True)
    # Scenery outside playable coordinates, actual world border and forced return.
    check(f"execute as {r} at @s if block 43 {y-1} 0 minecraft:stone if block 0 {y-1} 30 minecraft:stone","CARAVAN_SCENIC_EDGE")
    row=snap(r,"scenicborder");assert field(row,"result")=="192.0,0.5,0.5",row
    row=snap(r,"scenicfoliage");assert field(row,"result")=="24,72,72",row
    print("PASS all actual exterior trees and persistent leaf blocks present",flush=True)
    for n in names:
        first_horse=field(first[n],"horseId")
        row=snap(n,"horseedge");assert field(row,"result")=="EDGE",row
        row=until(n,lambda row:field(row,"horseHome")=="true",8)
        assert field(row,"horseId")==first_horse and field(row,"horses")=="1",row
    print("PASS both horses recalled from scenic perimeter without replacement",flush=True)
    # Aiming outwards toward real scenery: launch must not attach to outside blocks.
    send(f"execute as {b} at @s run tp @s .5 {y+2} 23.5 0 0")
    time.sleep(.4);row=snap(b,"hook")
    assert field(row,"hookAttached")=="false" and int(field(row,"hookRemaining"))==0,row
    print("PASS exterior-directed hook cannot attach or consume cooldown",flush=True)
    send(f"execute as {r} at @s run tp @s 38.5 {y+5} 0.5")
    time.sleep(.6);row=snap(r)
    assert abs(float(field(row,"posX"))+28.5)<4 and abs(float(field(row,"posZ"))+8.5)<4,row
    print("PASS expanded scenery and border, unchanged playable boundary enforced",flush=True)
    # Independent framebuffer of each synthetic game. No desktop capture.
    time.sleep(8)
    def shot(n,x,z,target_x,target_z,look_height=-.4):
        c=native[n]
        send(f"execute as {n} at @s run tp @s {x} {y} {z} facing {target_x} {y+look_height} {target_z}")
        time.sleep(.7)
        c.trigger.write_text("shot-wagon",encoding="ascii")
        png=c.trigger.parent/"screenshots"/(c.trigger.name+"-shot-wagon.png")
        end=time.monotonic()+20
        try:
            while time.monotonic()<end:
                if png.is_file():
                    data=png.read_bytes()
                    if data.startswith(b"\x89PNG\r\n\x1a\n") and data.endswith(b"IEND\xaeB\x60\x82"):
                        print("FRAMEBUFFER",str(png),hashlib.sha256(data).hexdigest(),flush=True);return
                time.sleep(.25)
            raise AssertionError("missing objective screenshot "+str(png))
        finally:c.trigger.write_text("idle",encoding="ascii")
    shot(r,-12.5,-11.5,-5.5,-11.5,2.0)
    shot(b,.5,7.5,.5,15.5,1.2)
    print("PASS tunnel and extraction approach framebuffer proofs captured from both actual clients",flush=True)
    def drive(n,route,obstacle=False):
        row=snap(n)
        x,y,z=map(float,re.search(r"wagon=Position\[x=([^,]+), y=([^,]+), z=([^\]]+)\]",row).groups())
        send(f"execute as {n} at @s run tp @s {x-2} {y} {z} facing {x-.85} {y+.5} {z}");time.sleep(.7)
        row=snap(n,"drive");assert "result=SUCCESS" in row and "mounted=true" in row and "driver=null" not in row,row
        # Native client key trigger is the only source of positive forward input.
        native[n].trigger.write_text("W",encoding="ascii")
        j=until(n,lambda row:"junction=true" in row,25)
        assert abs(progress(j)-8)<.001,j
        assert field(j,"wagonGeometry")=="true" and field(j,"seatGeometry")=="true" and abs(float(field(j,"heading")))<1e-8,j
        assert field(j,"input")!="null",j
        time.sleep(1);assert abs(progress(snap(n))-8)<.001
        print("PASS native vanilla W packets reach exact fork and wait",n,flush=True)
        row=snap(n)
        assert int(field(row,"selfPixels"))>0 and field(row,"mapHash")!=field(first[n],"mapHash"),row
        client_map(n,row)
        print("PASS actual mounted client map matches server at fork",n,flush=True)
        if obstacle:
            # Add a temporary block on the road, outside current wagon footprint.
            send(f"execute as {n} at @s run setblock -13 {int(y)} 1 minecraft:stone")
        key="WD" if route=="riskli" else "WA"
        expected="RISKY" if route=="riskli" else "SAFE"
        native[n].trigger.write_text(key,encoding="ascii")
        until(n,lambda row:"junction=false" in row and field(row,"route")==expected,12)
        native[n].trigger.write_text("W",encoding="ascii")
        print("PASS native "+key+" selects "+expected+" without route command",flush=True)
        if obstacle:
            row=until(n,lambda row:progress(row)>8.1,10);time.sleep(2)
            frozen=progress(snap(n));time.sleep(1)
            assert abs(progress(snap(n))-frozen)<.001 and frozen<12,(frozen,row)
            print("PASS real obstruction halts client-driven wagon without progress gain",flush=True)
            send(f"execute as {n} at @s run setblock -13 {int(y)} 1 minecraft:air")
            until(n,lambda row:progress(row)>frozen+.2,10)
            print("PASS removal of obstruction resumes fixed server speed",flush=True)
        turned=until(n,lambda row:progress(row)>8.3,12)
        angle=float(field(turned,"heading"))
        expected_angle=__import__("math").atan2(4,9) if route=="riskli" else -__import__("math").pi/2
        assert abs(angle-expected_angle)<1e-6 and field(turned,"wagonGeometry")=="true" and field(turned,"seatGeometry")=="true",turned
        print("PASS actual rotated display transforms, cargo hit and seat aligned on "+expected,flush=True)
        assert any("CARAVAN_CLIENT_POSITION" in line for line in native[n].lines()),"native controller did not record movement"
        print("PASS route "+route+" driven by native input; no injected forward fixture",flush=True)
    drive(r,"riskli",True)
    def wagon(row):return tuple(map(float,re.search(r"wagon=Position\[x=([^,]+), y=([^,]+), z=([^\]]+)\]",row).groups()))
    until(r,lambda row:wagon(row)[0]>=-.5,40)
    native[r].trigger.write_text("idle",encoding="ascii");time.sleep(.8)
    x,y,z=wagon(snap(r))
    send(f"execute as {b} at @s run tp @s {x} {y} {z+2} facing {x+.85} {y+1} {z}");time.sleep(.7)
    row=snap(b,"cargo");assert "result=SUCCESS" in row and "1:CARRIED" in row,row
    native[b].trigger.write_text("horse:0",encoding="ascii")
    try:until(b,lambda row:"1:STOLEN" in row,25)
    finally:native[b].trigger.write_text("idle",encoding="ascii")
    print("PASS actual native walking carries stolen crate to attacker base",flush=True)
    native[r].trigger.write_text("W",encoding="ascii")
    result=until(r,lambda row:"red=LegResult" in row,80)
    assert "delivered=19" in result and "stolen=1" in result,result
    native[r].trigger.write_text("idle",encoding="ascii")
    until(b,lambda row:"phase=LEG" in row and "leg=2" in row,15)
    drive(b,"guvenli")
    result=until(b,lambda row:"phase=ENDED" in row,90)
    assert "blue=LegResult" in result and "delivered=19" in result and "delivered=20" in result and "winner=BLUE" in result,result
    print("PASS actual native-client legs completed naturally 19 versus 20; delivered count wins",flush=True)
    for n in names:
        end=time.monotonic()+8
        while time.monotonic()<end:
            chat="\n".join(line for line in native[n].lines() if "[CHAT]" in line)
            if "Maç sonucu: Mavi kazandı · Ölçüt: Teslim edilen sandık sayısı" in chat:break
            time.sleep(.2)
        else:raise AssertionError(chat[-3000:])
        assert chat.count("Kırmızı eskort: 19 teslim · 1 çalındı · %100 ilerleme")==1,chat
        assert chat.count("Mavi eskort: 20 teslim · 0 çalındı · %100 ilerleme")==1,chat
        assert chat.count("Maç sonucu: Mavi kazandı · Ölçüt: Teslim edilen sandık sayısı")==1,chat
    print("PASS both actual clients received each exact round score and final winner reason once",flush=True)
    status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in names),"CARAVAN_NATIVE_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in names:
        off=len(lines);native[n].close();wait(n+" left the game",25,off)
    print("PASS two actual client JVMs disconnected and return ledger empty",flush=True)
