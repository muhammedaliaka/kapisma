from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json,os,subprocess,hashlib
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[2]
LAB=ROOT/"_yedek/sovereign-uyum-20261125-221852"
game=next((LAB/"alcak-irtifa-client-1/.fabric/remappedJars").rglob("client-intermediary.jar"))
loader=LAB/"server/libraries/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar"
api=LAB/"server/mods/fabric-api-0.116.18+1.21.1.jar"
assert hashlib.sha256(api.read_bytes()).hexdigest()=="1cb46442dda6d3be9979a2619610eb1c1f6641cd7ed7cb3eec058589ce9eed46"
# Reuse exact existing QA dependency extraction, read only.
deps=ROOT/"prototypes/alcak-irtifa/flightqa/deps"
paths=sorted(deps.glob("*.jar"));assert paths
classes=BASE/"classes";classes.mkdir(exist_ok=True)
subprocess.run([str(Path(os.environ["JAVA_HOME"])/"bin/javac.exe"),"-proc:none","--release","21","-d",str(classes),"-cp",os.pathsep.join(map(str,[game,loader,*paths])),str(BASE/"InventoryClientQa.java")],check=True)
artifact=BASE/"packetqa.jar"
with ZipFile(artifact,"w",ZIP_DEFLATED) as z:
    z.write(classes/"kapisma/packetqa/InventoryClientQa.class","kapisma/packetqa/InventoryClientQa.class")
    z.writestr("fabric.mod.json",json.dumps({"schemaVersion":1,"id":"kapisma_packetqa","version":"0.1.0","environment":"client","entrypoints":{"client":["kapisma.packetqa.InventoryClientQa"]}}))
print("QA JAR",artifact.stat().st_size,hashlib.sha256(artifact.read_bytes()).hexdigest())
