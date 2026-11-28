"""Single tiny test-only helper inside existing named QA build. No download."""
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import json,os,subprocess,hashlib
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[2]
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852"
game=next((LAB/"alcak-irtifa-client-1/.fabric/remappedJars").rglob("client-intermediary.jar"))
loader=LAB/"server/libraries/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar"
deps=sorted((ROOT/"prototypes/alcak-irtifa/flightqa/deps").glob("*.jar"));assert deps
out=BASE.parent/"build/caravan-client-proof";out.mkdir(exist_ok=True)
subprocess.run([str(Path(os.environ["JAVA_HOME"])/"bin/javac.exe"),"-proc:none","--release","21","-d",str(out),"-cp",os.pathsep.join(map(str,[game,loader,*deps])),str(BASE/"CaravanClientQa.java")],check=True)
artifact=out/"caravanqa.jar"
with ZipFile(artifact,"w",ZIP_DEFLATED) as z:
    z.write(out/"kapisma/caravanqa/CaravanClientQa.class","kapisma/caravanqa/CaravanClientQa.class")
    z.writestr("fabric.mod.json",json.dumps({"schemaVersion":1,"id":"kapisma_caravanqa","version":"0.1.0","environment":"client","entrypoints":{"client":["kapisma.caravanqa.CaravanClientQa"]}}))
print("QA JAR",artifact.stat().st_size,hashlib.sha256(artifact.read_bytes()).hexdigest())
