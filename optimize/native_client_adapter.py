"""Reusable real Java client harness. Import has no side effects.
Only explicitly chosen loopback servers; never use real account credentials.
KOD may use this independently in its own authorized test environment.
"""
from pathlib import Path
import hashlib,json,os,subprocess,time,uuid,re

BASE=Path(__file__).resolve().parent
ROOT=BASE.parent
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852/server"
API=LAB/"mods/fabric-api-0.116.18+1.21.1.jar"
def sha(path,algo="sha256"):
    return hashlib.new(algo,path.read_bytes()).hexdigest()

class NativeClient:
    def __init__(self,*,name,port,game,log,walk=False,yaw=-90,ticks=1200,heap_mib=1500):
        if not re.fullmatch(r"[A-Za-z0-9_]{3,16}",name):raise ValueError("synthetic player name")
        if type(port)!=int or not 1024<=port<=65535:raise ValueError("explicit loopback port")
        if not 1024<=heap_mib<=2048 or not 1<=ticks<=2400:raise ValueError("bounded QA settings")
        if not -180<=yaw<=180:raise ValueError("yaw")
        self.name=name;self.path=Path(log).absolute();game=Path(game).absolute()
        self.trigger=game/(self.path.stem+".trigger")
        if walk and self.trigger.exists():raise ValueError("stale walk trigger: choose fresh QA profile")
        # Caller must reserve a dedicated QA directory, not an existing actual game profile.
        game.mkdir(parents=True,exist_ok=True)
        marker=game/"KAPISMA_SYNTHETIC_CLIENT"
        if not marker.exists():
            if any(game.iterdir()):raise ValueError("refuse existing non-QA profile")
            marker.write_text(name,encoding="ascii")
        if marker.read_text(encoding="ascii")!=name:raise ValueError("profile identity mismatch")
        if self.path.exists():raise FileExistsError("preserve previous log "+str(self.path))
        self.path.parent.mkdir(parents=True,exist_ok=True)
        (game/"mods").mkdir(exist_ok=True)
        if list((game/"mods").iterdir()):raise ValueError("unexpected mods")
        (game/"natives").mkdir(exist_ok=True)
        mc=ROOT.parent;version=mc/"versions/Fabric 1.21.1";client=version/"Fabric 1.21.1.jar"
        assert sha(client,"sha1")=="30c73b1c5da787909b2f73340419fdf13b9def88"
        meta=json.loads((version/"Fabric 1.21.1.json").read_text(encoding="utf-8-sig"))
        paths=[]
        for lib in meta["libraries"]:
            rules=lib.get("rules",[])
            if rules and not any(r.get("action")=="allow" and r.get("os",{}).get("name","windows")=="windows" for r in rules):continue
            group,artifact,number,*classifier=lib["name"].split(":")
            if group=="net.fabricmc" and artifact in ("fabric-loader","sponge-mixin"):
                number={"fabric-loader":"0.20.1","sponge-mixin":"0.17.5+mixin.0.8.7"}[artifact]
                paths.append(LAB/"libraries"/Path(group.replace(".","/"))/artifact/number/(artifact+"-"+number+".jar"))
                continue
            entry=lib.get("downloads",{}).get("artifact",{}).get("path","/".join((group.replace(".","/"),artifact,number,artifact+"-"+number+("-"+classifier[0] if classifier else "")+".jar")))
            if group=="org.ow2.asm":
                candidate=LAB/"libraries"/Path(group.replace(".","/"))/artifact/"9.9.1"/(artifact+"-9.9.1.jar")
                if candidate.is_file():paths.append(candidate);continue
            paths.append(mc/"libraries"/entry)
        assert all(p.is_file() for p in paths)
        (game/"options.txt").write_text("version:3955\nonboardAccessibility:false\ntutorialStep:none\nrawMouseInput:false\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:20\nenableVsync:false\noverrideWidth:640\noverrideHeight:480\nguiScale:2\n",encoding="ascii")
        account=uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:"+name).encode()).digest(),version=3)
        java=Path(os.environ["JAVA_HOME"])/"bin/java.exe"
        args=[str(java),f"-Xmx{heap_mib}M","-XX:TieredStopAtLevel=1","-XX:ActiveProcessorCount=2","-Dfile.encoding=UTF-8","-Djava.library.path="+str(game/"natives"),"-cp",";".join(map(str,paths+[client])),"net.fabricmc.loader.impl.launch.knot.KnotClient","--username",name,"--version","KOD-QA","--gameDir",str(game),"--assetsDir",str(mc/"assets"),"--assetIndex","17","--uuid",account.hex,"--accessToken","0","--userType","legacy","--versionType","release","--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer",f"127.0.0.1:{port}","--width","640","--height","480"]
        if walk:
            qa=ROOT/"prototypes/alcak-irtifa/flightqa/flightqa.jar"
            assert sha(qa)=="3202b96cea44313e89a465190377c4d8eea4aff72d110f0fce806a0afb671122"
            assert sha(API)=="1cb46442dda6d3be9979a2619610eb1c1f6641cd7ed7cb3eec058589ce9eed46"
            origins=ROOT/"server/mods/Origins-1.13.0-pre.3+mc.1.21.1.jar"
            if sha(origins)!="c7e06f8fd49a279d566c3e778ff0c71bbb371f1c4358ab9b2190d1e3b6c36d33":raise ValueError("Origins JAR mismatch")
            args[1:1]=["-Dai.qa.enabled=true","-Dai.qa.walk=true",f"-Dai.qa.walkTicks={ticks}",f"-Dai.qa.yaw={yaw}",f"-Dai.qa.walkTrigger={self.trigger}","-Dfabric.addMods="+os.pathsep.join(map(str,[API,qa,origins]))]
        with self.path.open("xb") as out:
            self.p=subprocess.Popen(args,cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    def start_walk(self):
        with self.trigger.open('x',encoding='ascii') as f:f.write('start\n')
    def lines(self):return self.path.read_text(encoding="utf-8",errors="replace").splitlines()
    def positions(self):
        result=[]
        for l in self.lines():
            m=re.search(r"AIQA_WALK (\d+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)",l)
            if m:result.append((int(m[1]),float(m[2]),float(m[3]),float(m[4])))
        return result
    def close(self):
        if self.p.poll() is None:
            self.p.terminate();self.p.wait(timeout=15)