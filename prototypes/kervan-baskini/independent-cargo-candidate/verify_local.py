"""Offline isolated compilation; no Gradle, no downloads, no product jar."""
from pathlib import Path
import os, subprocess
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[2]
MOD=ROOT/"mod/kapisma"
JAVA=Path(os.environ["JAVA_HOME"])/"bin"
GRADLE=Path.home()/".gradle/caches/modules-2/files-2.1"
CACHE=MOD/".gradle/loom-cache"
OUT=BASE/"build";OUT.mkdir(exist_ok=True)
roster=ROOT/"prototypes/java-integration/src/main/java/tr/kapisma/arena/MatchRoster.java"
def q(s):return '"'+str(s).replace("\\","/")+'"'
game=sorted(CACHE.glob("minecraftMaven/**/minecraft-merged-*.jar"))
assert len(game)==1,game
groups=("com.mojang","com.google.guava","com.google.code.gson","org.slf4j","it.unimi.dsi","org.jetbrains","net.fabricmc","io.netty","org.apache.logging.log4j","org.ow2.asm","de.maxhenkel.voicechat")
deps=[p for group in groups for p in sorted((GRADLE/group).rglob("*.jar")) if not p.name.endswith(("-sources.jar","-javadoc.jar"))]
remapped=[p for p in sorted((CACHE/"remapped_mods").rglob("*.jar")) if not p.name.endswith(("-sources.jar","-javadoc.jar"))]
cp=os.pathsep.join(map(str,[*game,*remapped,*deps]))
args=OUT/"javac.args"
sources=[roster,*sorted(BASE.glob("*.java"))]
args.write_text("\n".join(["-proc:none","--release","21","-encoding","UTF-8","-cp",q(cp),"-d",q(OUT),*[q(p) for p in sources]]),encoding="utf-8")
subprocess.run([str(JAVA/"javac.exe"),"@"+str(args)],check=True)
print("PASS mapped Minecraft Java 21 compilation",len(sources),flush=True)
for f in sorted(BASE.glob("*Test.java")):
    subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(OUT),"tr.kapisma.arena."+f.stem],check=True)
print("PASS all offline local suites",flush=True)
