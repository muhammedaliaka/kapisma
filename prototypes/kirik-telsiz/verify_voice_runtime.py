"""Real Analog/SVC synthetic audio with two native clients. Reuses MOD lab; never touches live Kapisma."""
from pathlib import Path
from queue import Queue, Empty
from threading import Thread
import msvcrt, os, re, socket, subprocess, time, json, hashlib, uuid
from verify import ROOT, LAB, WORLD, JAVA, MODS, PROPS, VOICE, EXPECTED, digest, port_free
from profile_guard import snapshot_profile
from prepare import install, generate

BASE=ROOT/"prototypes/kirik-telsiz"
LOG=BASE/"acceptance_voice_runtime.log"
QA=BASE/"voiceqa/kt-voiceqa.jar"
REQUEST=BASE/"voiceqa/request.txt"

class Server:
    def __init__(self, empty, log):
        self.q = Queue()
        self.p = subprocess.Popen([str(JAVA), "-Xmx2G", "-Dkt.voiceqa.mode=server","-Dkt.voiceqa.request="+str(REQUEST),"-Dfabric.modsFolder="+str(empty),
            "-Dfabric.addMods="+os.pathsep.join(map(str, MODS+[QA])), "-jar", "fabric-server-launch.jar", "nogui"],
            cwd=LAB, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace", bufsize=1)
        def reader():
            for line in self.p.stdout:
                log.write(line); log.flush(); self.q.put(line)
        self.t = Thread(target=reader, daemon=True); self.t.start()
    def send(self,*commands):
        assert self.p.poll() is None
        self.p.stdin.write("\n".join(commands)+"\n"); self.p.stdin.flush()
    def wait(self,marker,timeout=30):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            try: line=self.q.get(timeout=.4)
            except Empty:
                if self.p.poll() is not None: break
                continue
            if marker in line: return line
        raise RuntimeError("Missing marker "+marker)
    def check(self, condition, marker):
        self.send("execute "+condition+" run say "+marker, "say END_"+marker)
        found=False
        end=time.monotonic()+20
        while time.monotonic()<end:
            try: line=self.q.get(timeout=.4)
            except Empty: continue
            if line.rstrip().endswith("[Server] "+marker): found=True
            if line.rstrip().endswith("[Server] END_"+marker):
                assert found, "FAIL "+marker
                print("PASS "+marker,flush=True); return
        raise RuntimeError("Missing check boundary "+marker)
    def get(self,name):
        self.send("scoreboard players get "+name+" kt_ctrl")
        line=self.wait(name+" has ")
        m=re.search(r"has (-?\d+) \[kt_ctrl\]",line)
        assert m,line
        return int(m[1])
    def spawn(self,n):
        name=f"ktqa{n}"
        self.send(f"player {name} spawn")
        self.wait(name+" joined the game")
        self.send("gamemode adventure "+name)
    def stop(self):
        if self.p.poll() is None:
            self.send("stop")
            try: self.p.wait(timeout=55)
            except subprocess.TimeoutExpired: self.p.terminate(); self.p.wait(timeout=10)
        self.t.join(timeout=5)
        assert self.p.returncode==0,self.p.returncode

class Client:
    def __init__(self,n):
        self.name=f"KtNativeQA{n}"
        mc=ROOT.parent
        version=mc/"versions/Fabric 1.21.1"
        client=version/"Fabric 1.21.1.jar"
        assert hashlib.sha1(client.read_bytes()).hexdigest()=="30c73b1c5da787909b2f73340419fdf13b9def88"
        meta=json.loads((version/"Fabric 1.21.1.json").read_text(encoding="utf-8-sig"))
        rts=ROOT/"_yedek/devletler-rts-prototip"
        paths=[]
        for library in meta["libraries"]:
            rules=library.get("rules",[])
            if rules and not any(r.get("action")=="allow" and r.get("os",{}).get("name","windows")=="windows" for r in rules): continue
            group,artifact,number,*classifier=library["name"].split(":")
            if group=="net.fabricmc" and artifact in ("fabric-loader","sponge-mixin"):
                number={"fabric-loader":"0.20.1","sponge-mixin":"0.17.5+mixin.0.8.7"}[artifact]
                paths.append(rts/"libraries"/Path(group.replace(".","/"))/artifact/number/(artifact+"-"+number+".jar"))
            else:
                entry=library.get("downloads",{}).get("artifact",{}).get("path","/".join((group.replace(".","/"),artifact,number,artifact+"-"+number+("-"+classifier[0] if classifier else "")+".jar")))
                if group=="org.ow2.asm":
                    candidate=rts/"libraries"/Path(group.replace(".","/"))/artifact/"9.9.1"/(artifact+"-9.9.1.jar")
                    if candidate.is_file(): paths.append(candidate);continue
                paths.append(mc/"libraries"/entry)
        assert all(p.is_file() for p in paths)
        game=LAB.parent/("kirik-telsiz-voice-client-"+str(n))
        game.mkdir(exist_ok=True)
        (game/"mods").mkdir(exist_ok=True);assert not list((game/"mods").iterdir())
        (game/"natives").mkdir(exist_ok=True)
        (game/"options.txt").write_text("version:3955\nonboardAccessibility:false\ntutorialStep:none\nrawMouseInput:false\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:30\nenableVsync:true\noverrideWidth:640\noverrideHeight:480\nguiScale:2\n",encoding="ascii")
        vc=game/"config/voicechat"
        vc.mkdir(parents=True,exist_ok=True)
        (vc/"voicechat-client.properties").write_text("config_version=1\nmuted=true\nmicrophone_activation_type=PTT\nonboarding_finished=true\nvoice_chat_volume=0.0\n",encoding="ascii")
        account=uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+self.name).encode()).digest(),version=3)
        args=[str(JAVA),"-Dkt.voiceqa.mode=client","-Dfabric.addMods="+os.pathsep.join(map(str,[MODS[0],MODS[2],QA])),"-Xmx1500M","-XX:TieredStopAtLevel=1","-Dfile.encoding=UTF-8","-Djava.library.path="+str(game/"natives"),"-cp",";".join(map(str,paths+[client])),"net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username",self.name,"--version","KT-QA","--gameDir",str(game),"--assetsDir",str(mc/"assets"),"--assetIndex","17","--uuid",account.hex,"--accessToken","0","--userType","legacy","--versionType","release","--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer","127.0.0.1:25578","--width","640","--height","480"]
        self.path=ROOT/"prototypes/kirik-telsiz"/("voice-client-"+str(n)+".log")
        with self.path.open("wb") as out:
            self.p=subprocess.Popen(args,cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    def voice_lines(self):
        return self.path.read_text(encoding="utf-8",errors="replace").splitlines()
    def pcm(self):
        return sum("KTQA_PCM " in x for x in self.voice_lines())
    def wait_voice(self):
        end=time.monotonic()+45
        while time.monotonic()<end:
            if any("KTQA_VOICE_CONNECTED true" in x for x in self.voice_lines()):return
            assert self.p.poll() is None,"Voice client exited"
            time.sleep(.5)
        raise RuntimeError("Voice handshake missing "+self.name)
    def chats(self):
        return [line for line in self.path.read_text(encoding="utf-8",errors="replace").splitlines() if "[CHAT]" in line]
    def chat_count(self,needle):
        return sum(needle in line for line in self.chats())
    def wait_chat(self,needle,count=1):
        end=time.monotonic()+15
        while time.monotonic()<end:
            if self.chat_count(needle)>=count:return
            assert self.p.poll() is None, "Native client exited"
            time.sleep(.4)
        raise RuntimeError("Missing client chat "+self.name+" "+needle)
    def assert_no_chat(self,needle):
        assert self.chat_count(needle)==0,(self.name,needle)
    def close(self):
        if self.p.poll() is None:
            self.p.terminate();self.p.wait(timeout=15)

def main():
    assert [digest(x) for x in MODS]==EXPECTED
    assert QA.is_file() and digest(QA)=="68CEC37E0814ECBBD44CBFAF89627ECEB6BFEDE60751CE75EC9C8FFC65C4F358"
    assert LAB.resolve()==LAB and not (LAB/WORLD).is_symlink()
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0); msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for port,kind in ((25578,socket.SOCK_STREAM),(25579,socket.SOCK_DGRAM),(25572,socket.SOCK_STREAM)):
            port_free(port,kind)
        empty=LAB/"kirik-telsiz-empty-mods"
        empty.mkdir(exist_ok=True); assert not list(empty.iterdir())
        before = snapshot_profile((PROPS, VOICE))
        REQUEST.write_text("idle",encoding="ascii")
        pack=LAB/WORLD/"datapacks/kirik-telsiz"
        install(pack)
        settings=("server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\n"
            "enforce-secure-profile=false\nlevel-name="+WORLD+"\nlevel-type=minecraft:flat\n"
            'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}\n'
            "generate-structures=false\nallow-nether=false\nmax-players=10\ngamemode=adventure\n"
            "spawn-protection=0\nview-distance=4\nsimulation-distance=4\nenable-rcon=false\nenable-query=false\n")
        voice=re.sub(rb"(?m)^port=.*$",b"port=25579",before[VOICE])
        voice=re.sub(rb"(?m)^bind_address=.*$",b"bind_address=127.0.0.1",voice)
        assert b"port=25579" in voice
        s=None
        try:
            PROPS.write_text(settings,encoding="ascii"); VOICE.write_bytes(voice)
            with LOG.open("w",encoding="utf-8") as log:
                try:
                    s=Server(empty,log);s.wait("Done (",150)
                    print("PASS server boot",flush=True)
                    s.send("difficulty peaceful","function kt:reset")
                    clients=[]
                    try:
                        for n in (1,2):
                            c=Client(n);clients.append(c)
                            s.wait(c.name+" joined the game",120)
                            s.send("gamemode adventure "+c.name)
                            c.wait_voice()
                        print("PASS both native voice clients connected",flush=True)
                        a,b=clients
                        s.spawn(1)
                        s.send("tp ktqa1 0 -60 0",f"tp {a.name} 100 -60 0",f"tp {b.name} 101 -60 0",f"gamemode spectator {b.name}",
                            "clear ktqa1",f"clear {a.name}",f"clear {b.name}",
                            "give ktqa1 analog:radio[minecraft:radio={enabled:1b,transmit:1b,receive:0b,channel:7}]",
                            f"give {a.name} analog:radio[minecraft:radio={{enabled:1b,transmit:0b,receive:1b,channel:7}}]")
                        time.sleep(3)
                        # Component namespace is validated by real command parser.
                        s.check("if items entity ktqa1 hotbar.* analog:radio if items entity "+a.name+" hotbar.* analog:radio","KTQA_RADIOS_PRESENT")
                        def phase(mode):
                            olda,oldb=a.pcm(),b.pcm()
                            REQUEST.write_text(mode,encoding="ascii")
                            s.wait("KTQA_FINISHED "+mode,30)
                            time.sleep(3)
                            log.flush()
                            rows=[line for line in LOG.read_text(encoding="utf-8").splitlines() if "KTQA_PACKET "+mode+" " in line]
                            assert not any("KTQA_ERROR" in line for line in LOG.read_text(encoding="utf-8").splitlines())
                            return rows,a.pcm()-olda,b.pcm()-oldb
                        rows,ra,rb=phase("red")
                        print("NATIVE microphone path packet events=%d receiver PCM=%d spectator PCM=%d"%(len(rows),ra,rb),flush=True)
                        direct = not (ra>0 and rb>0)
                        if direct:
                            rows,ra,rb=phase("directred")
                        print("RED direct=%s packet events=%d receiver PCM=%d spectator PCM=%d"%(direct,len(rows),ra,rb),flush=True)
                        assert ra>0 and rb>0, "RED actual audio delivery not reproduced"
                        assert any("receiver=B nullSender=true spectator=true cancelled=false" in x for x in rows)
                        print("PASS unguarded Analog spectator audio reproduced",flush=True)
                        rows,ra,rb=phase("directgreen" if direct else "green")
                        print("GREEN packet events=%d receiver PCM=%d spectator PCM=%d"%(len(rows),ra,rb),flush=True)
                        assert ra>0 and rb==0, "GREEN isolation failed"
                        assert any("receiver=B nullSender=true spectator=true cancelled=true" in x for x in rows)
                        print("PASS candidate guard blocks spectator but not receiver",flush=True)
                        if not direct:
                            rows,ra,rb=phase("postred")
                            print("POSTRED packet events=%d receiver PCM=%d spectator PCM=%d"%(len(rows),ra,rb),flush=True)
                            assert ra>0 and rb>0, "Positive control after guard failed"
                            print("PASS guard disabled restores delivery in QA only",flush=True)
                        s.send(f"clear {a.name}",f"give {a.name} analog:radio[minecraft:radio={{enabled:1b,transmit:0b,receive:1b,channel:8}}]")
                        time.sleep(2)
                        rows,ra,rb=phase("directwrong" if direct else "wrongchannel")
                        assert ra==0 and rb==0 and not rows
                        print("PASS wrong channel rejects audio",flush=True)
                        s.send("clear ktqa1",f"clear {a.name}",f"clear {b.name}","player ktqa1 kill")
                    finally:
                        for c in clients: c.close()
                    s.stop();s=None
                    print("PASS clean stop",flush=True)
                finally:
                    if s: s.stop()
            assert not re.search(r"(?im)/ERROR\]|Exception in thread|Failed to load function",LOG.read_text(encoding="utf-8"))
            assert all((pack/"data/kt/function"/(name+".mcfunction")).read_text(encoding="utf-8")==value for name,value in generate().items())
            print("PASS log and installed functions",flush=True)
        finally:
            for path,data in before.items(): path.write_bytes(data)
            assert all(path.read_bytes()==data for path,data in before.items())
            assert [digest(x) for x in MODS]==EXPECTED
            print("PASS restored settings and mod hashes",flush=True)

if __name__=="__main__":
    main()