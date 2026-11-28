"""Compile staged sources against existing mapped Kapisma classes. No Gradle or release build."""
from pathlib import Path
import os, subprocess, hashlib, json
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[1]
MOD=ROOT/"mod/kapisma"
CLASSES=MOD/"build/classes/java/main"
JAVA=Path(os.environ["JAVA_HOME"])/"bin"
GRADLE=Path.home()/".gradle/caches/modules-2/files-2.1"
CACHE=MOD/".gradle/loom-cache"
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def state():
    files=[CLASSES/"tr/kapisma"/p for p in ("game/Game.class","game/GameManager.class","game/GameWorld.class","team/TeamService.class","lobby/Lobby.class","voice/VoiceBridge.class")]
    return {p:sha(p) for p in files}
def usable(p):return not p.name.endswith(("-sources.jar","-javadoc.jar"))
before=state()
manifest=json.loads((BASE/"overlay-manifest.json").read_text(encoding="utf-8"))
for rel,hashes in manifest.items():
    source=MOD/"src/main"/rel if rel.startswith("resources/") else MOD/"src/main/java"/rel
    assert sha(source)==hashes["base_sha256"],f"KOD source changed; regenerate/review overlay: {rel}"
    staged=BASE/"overlay-resources"/rel.removeprefix("resources/") if rel.startswith("resources/") else BASE/"overlay"/rel
    assert sha(staged)==hashes["overlay_sha256"],f"overlay integrity mismatch: {rel}"
game=sorted(CACHE.glob("minecraftMaven/**/minecraft-merged-*.jar"))
assert len(game)==1,game
remapped=[p for p in sorted((CACHE/"remapped_mods").rglob("*.jar")) if usable(p)]
# Only known game linkage libraries; avoid classpath shadowing by alternate Minecraft/API builds.
groups=("com.mojang","com.google.guava","com.google.code.gson","org.slf4j","it.unimi.dsi","org.jetbrains","net.fabricmc","io.netty","org.apache.logging.log4j","org.ow2.asm","de.maxhenkel.voicechat")
deps=[p for group in groups for p in sorted((GRADLE/group).rglob("*.jar")) if usable(p)]
# Minecraft 1.21.1's official bundled math library (read-only cached copy; verified against server manifest).
joml=ROOT/"_yedek/devletler-rts-prototip/libraries/org/joml/joml/1.10.5/joml-1.10.5.jar"
assert sha(joml)=="cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a"
cp=os.pathsep.join(map(str,[CLASSES,*game,*remapped,*deps,joml]))
out=BASE/"build/classes";out.mkdir(parents=True,exist_ok=True)
src=sorted((BASE/"src").rglob("*.java"))+sorted((BASE/"overlay").rglob("*.java"))+[MOD/"src/proofTest/java/tr/kapisma/voice/VoicePolicyTest.java"]
caravan_candidate=BASE.parent/"kervan-baskini/independent-cargo-candidate"
src+=sorted(caravan_candidate.glob("*.java"))
assert src
def q(s):return '"'+str(s).replace("\\","/")+'"'
arg=BASE/"build/javac.args"
arg.write_text("\n".join(["-proc:none","--release","21","-encoding","UTF-8","-cp",q(cp),"-d",q(out),*[q(p) for p in src]]),encoding="utf-8")
subprocess.run([str(JAVA/"javac.exe"),"@"+str(arg)],check=True)
assert state()==before,"KOD compiled dependencies changed during validation"
print("PASS Java 21 mapped Minecraft and Kapisma linkage; source files",len(src),flush=True)
subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.arena.ArenaRulesTest"],check=True)
subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.arena.ModeRulesTest"],check=True)
subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.arena.SiegeEconomyTest"],check=True)
subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.arena.FlightBoostTest"],check=True)
subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.voice.VoiceRouteTest"],check=True)
subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.voice.VoicePolicyTest"],check=True)
subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.arena.ReturnJournalTest",str(BASE/"build/journal-proof")],check=True)
for test in sorted(caravan_candidate.glob("*Test.java")):
    subprocess.run([str(JAVA/"java.exe"),"-ea","-cp",str(out),"tr.kapisma.arena."+test.stem],check=True)
assert state()==before,"KOD compiled dependencies changed during tests"
for rel,hashes in manifest.items():
    source=MOD/"src/main"/rel if rel.startswith("resources/") else MOD/"src/main/java"/rel
    assert sha(source)==hashes["base_sha256"],rel
print("PASS no KOD class file changed; no JAR/ZIP produced",flush=True)