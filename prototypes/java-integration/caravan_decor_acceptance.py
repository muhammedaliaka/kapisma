"""D28 actual furniture smoke in disposable arena, not production placement."""
import hashlib,json,time,uuid,zipfile,re
from qa_clients import NativeClient
def accept_decor(send,wait,status,check,lines,process,RUN,clients):
    # AutoHost remains disabled. Explicit generation in test server only.
    pack=RUN/"polymer"/"kapisma-decor-d28.zip"
    if pack.exists():
        assert hashlib.sha256(pack.read_bytes()).hexdigest()=="f659368292b8a5b61f6c95b447b0585f869e1e42b12be865d644036051554794"
    else:send("polymer generate-pack")
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        if pack.is_file():
            try:
                with zipfile.ZipFile(pack) as z:
                    names=z.namelist()
                    assert "pack.mcmeta" in names
                    assert any("polydecorations" in n and "dark_oak_bench" in n for n in names)
                    assert z.testzip() is None
                    meta=json.loads(z.read("pack.mcmeta"))
                break
            except (zipfile.BadZipFile,OSError):pass
        time.sleep(.25)
    else:raise AssertionError("resource pack missing or incomplete")
    digest=hashlib.sha256(pack.read_bytes()).hexdigest()
    print("PASS local generated pack",pack.stat().st_size,digest,meta,flush=True)
    names=["ModNativeQA1","ModNativeQA2"];native={};base=RUN.parent.parent
    for n in names:
        off=len(lines)
        c=NativeClient(name=n,port=25580,game=RUN.parent/"native-load-clients"/n[-1],
            log=base/("caravan-decor-"+n+"-"+uuid.uuid4().hex[:12]+".log"),
            caravan=True,heap_mib=1200,resource_pack=pack if n==names[0] else None)
        clients.append(c);native[n]=c;wait(n+" joined the game",100,off);time.sleep(.8)
    expected="file/kapisma-decor-"+digest[:16]+".zip"
    assert any("Reloading ResourceManager" in l and expected in l for l in native[names[0]].lines()),"local pack not loaded by client"
    assert not any("Reloading ResourceManager" in l and expected in l for l in native[names[1]].lines()),"fallback unexpectedly loaded pack"
    print("PASS local pack loaded by first client, second joined without pack",flush=True)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in names),"CARAVAN_DECOR_READY")
    send("kapisma game KERVAN_BASKINI");send("kapisma start");status("KERVAN_BASKINI RUNNING",180)
    # Both open rear shelters. Two same type objects per role, identical offsets.
    objects=[(-30,-16,"spruce_table"),(-28,-16,"spruce_bench"),(-1,22,"dark_oak_table"),(1,22,"dark_oak_bench")]
    for x,z,name in objects:
        off=len(lines);send(f"execute as {names[0]} at @s run setblock {x} 301 {z} polydecorations:{name}")
        wait("Changed the block",8,off)
    check(f"execute as {names[0]} at @s "+" ".join(f"if block {x} 301 {z} polydecorations:{name}" for x,z,name in objects),"CARAVAN_DECOR_BLOCKS")
    check(f"execute as {names[0]} at @s if block -29 301 -15 minecraft:air if block 0 301 21 minecraft:air","CARAVAN_DECOR_OPEN")
    # Vanilla outline rays missed the table in D28 v1/v2; these are virtual-entity
    # models. Do not imply interaction/security acceptance from visual smoke.
    print("OBS interaction acceptance separate: D28 v1/v2 vanilla outline target not reached",flush=True)
    # Screenshot both local-pack and fallback clients at same camera coordinates.
    for n in names:
        # Never photograph from the horse spawn lane or stack two player cameras.
        other=next(x for x in names if x!=n)
        send(f"execute as {other} at @s run tp @s -28.5 301 -8.5")
        c=native[n];send(f"execute as {n} at @s run tp @s .5 301 20.2 facing .5 301.55 22.5")
        time.sleep(2);c.trigger.write_text("shot-wagon",encoding="ascii")
        png=c.trigger.parent/"screenshots"/(c.trigger.name+"-shot-wagon.png")
        deadline=time.monotonic()+25
        try:
            while time.monotonic()<deadline:
                if png.is_file():
                    data=png.read_bytes()
                    if data.startswith(b"\x89PNG\r\n\x1a\n") and data.endswith(b"IEND\xaeB\x60\x82"):
                        print("FRAMEBUFFER",n,str(png),hashlib.sha256(data).hexdigest(),flush=True);break
                time.sleep(.25)
            else:raise AssertionError("missing decor screenshot")
        finally:c.trigger.write_text("idle",encoding="ascii")
    # No claim of seat/protection acceptance: separate interaction test required.
    send("kapisma stop");status("yok (lobi)",60)
    check("execute "+" ".join("if entity @a[name="+n+",gamemode=adventure] if items entity "+n+" hotbar.4 minecraft:nether_star" for n in names),"CARAVAN_DECOR_RETURN")
    assert (RUN/"kapisma-arena-return.v1").read_text(encoding="utf-8")=="KAPISMA_ARENA_RETURN_V1\n"
    for n in names:
        off=len(lines);native[n].close();wait(n+" left the game",25,off)
    print("PASS decor test return and empty journal; actual screenshots require inspection",flush=True)
