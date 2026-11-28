from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json, os, subprocess, hashlib
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[2]
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852"
game=next((LAB/"alcak-irtifa-client-1/.fabric/remappedJars").rglob("client-intermediary.jar"))
loader=LAB/"server/libraries/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar"
api=ROOT/"_yedek/sovereign-uyum-20261125-221852/server/mods/fabric-api-0.116.18+1.21.1.jar"
assert hashlib.sha256(api.read_bytes()).hexdigest().upper()=="1CB46442DDA6D3BE9979A2619610EB1C1F6641CD7ED7CB3EEC058589CE9EED46"
deps=BASE/"deps";deps.mkdir(exist_ok=True);paths=[]
with ZipFile(api) as z:
    for n in z.namelist():
        if "fabric-lifecycle-events-v1-" in n or "fabric-api-base-" in n:
            p=deps/Path(n).name;p.write_bytes(z.read(n));paths.append(p)
classes=BASE/"classes";classes.mkdir(exist_ok=True)
java=Path(os.environ["JAVA_HOME"])/"bin/javac.exe"
subprocess.run([str(java),"-proc:none","--release","21","-d",str(classes),"-cp",os.pathsep.join(map(str,[game,loader,*paths])),str(BASE/"FlightClientQa.java")],check=True)
artifact=BASE/"flightqa.jar"
with ZipFile(artifact,"w",ZIP_DEFLATED) as z:
    for p in classes.rglob("*.class"):z.write(p,p.relative_to(classes).as_posix())
    z.writestr("fabric.mod.json",json.dumps({"schemaVersion":1,"id":"kapisma_flightqa","version":"0.1.0","environment":"client","entrypoints":{"client":["kapisma.flightqa.FlightClientQa"]}}))
print("QA JAR",artifact.stat().st_size,hashlib.sha256(artifact.read_bytes()).hexdigest().upper())