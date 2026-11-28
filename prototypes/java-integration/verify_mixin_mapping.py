"""TinyRemapper production namespace audit, Mixin classes only; no product JAR.
Does not prove full production server boot. Uses original KOD classpath read-only.
"""
from pathlib import Path
import os, re, subprocess, hashlib
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[1]; MOD=ROOT/"mod/kapisma"; G=Path.home()/".gradle/caches/modules-2/files-2.1"
JAVA=Path(os.environ["JAVA_HOME"])/"bin"
def pick(group,name,version):
    p=[x for x in (G/group/name/version).rglob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar"))]
    assert len(p)==1,p
    return p[0]
mapping=next((Path.home()/".gradle/caches/fabric-loom/1.21.1").glob("loom.mappings.*/mappings.tiny"))
named=next((MOD/".gradle/loom-cache/minecraftMaven").rglob("minecraft-merged-*.jar"))
intermediary=next((Path.home()/".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary").rglob("minecraft-merged-intermediary-*.jar"))
cp=[pick("net.fabricmc","tiny-remapper","0.13.0"),pick("net.fabricmc","mapping-io","0.8.0")]
cp += [pick("org.ow2.asm",a,"9.10.1") for a in ("asm","asm-tree","asm-commons","asm-analysis","asm-util")]
staging=BASE/"build/classes"; src=staging/"tr/kapisma/mixin"
out=BASE/"build/mixin-namespace-audit";out.mkdir(exist_ok=True)
deps=[x for x in (MOD/".gradle/loom-cache/remapped_mods").rglob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar"))]
args=[str(JAVA/"java.exe"),"-Xmx768m","-cp",os.pathsep.join(map(str,cp)),"net.fabricmc.tinyremapper.Main",str(src),str(out),str(mapping),"named","intermediary",str(named),str(MOD/"build/classes/java/main"),str(staging),*map(str,deps),"--mixin"]
subprocess.run(args,check=True)
# Map the exact selector with descriptor translation via the official source namespace.
rows=mapping.read_text(encoding="utf-8").splitlines()
assert rows[0]=="tiny\t2\t0\tofficial\tintermediary\tnamed"
classes={}
methods={}
current=None
for line in rows[1:]:
    row=line.split("\t")
    if row[0]=="c":
        off,inter,name=row[1:4];classes[off]=(inter,name);current=name
    elif row[0]=="" and len(row)>=6 and row[1]=="m":
        desc,off,inter,name=row[2:6];methods.setdefault(current,[]).append((desc,inter,name))
def desc_in(d,index):
    return re.sub(r"L([^;]+);",lambda m:"L"+classes.get(m[1],(m[1],m[1]))[index]+";",d)
def audit(mixin,owner,method,desc):
    cls=next(v[0] for v in classes.values() if v[1]==owner)
    found={(inter,desc_in(d,0)) for d,inter,n in methods[owner] if n==method and desc_in(d,1)==desc}
    # Tiny may record override mappings on a parent only. Require unique exact inherited selector,
    # then independently verify its declaration on the actual target class below.
    if not found:found={(inter,desc_in(d,0)) for ms in methods.values() for d,inter,n in ms if n==method and desc_in(d,1)==desc}
    # Global fallback can contain unrelated methods with the same named signature; intersect with the actual target declarations.
    declared=subprocess.check_output([str(JAVA/"javap.exe"),"-p","-s","-classpath",str(intermediary),cls.replace("/",".")],text=True,encoding="utf-8",errors="replace").splitlines()
    found={(n,d) for n,d in found if any(n+"(" in l and "descriptor: "+d in "\n".join(declared[i+1:i+3]) for i,l in enumerate(declared))}
    assert len(found)==1,(owner,method,desc,found)
    name,mapped=next(iter(found))
    javap=subprocess.check_output([str(JAVA/"javap.exe"),"-v","-p","-classpath",str(out),"tr.kapisma.mixin."+mixin],text=True,encoding="utf-8",errors="replace")
    target=subprocess.check_output([str(JAVA/"javap.exe"),"-p","-s","-classpath",str(intermediary),cls.replace("/","." )],text=True,encoding="utf-8",errors="replace")
    # TinyRemapper may emit the unique bare method selector instead of a descriptor.
    assert ('method=["'+name+mapped+'"]') in javap or ('method=["'+name+'"]') in javap,(mixin,name,mapped)
    if ('method=["'+name+'"]') in javap:assert sum(name+"(" in l for l in target.splitlines())==1,(mixin,"ambiguous bare selector")
    assert any(name+"(" in l and "descriptor: "+mapped in "\n".join(target.splitlines()[i+1:i+3]) for i,l in enumerate(target.splitlines())),(cls,name,mapped)
    assert cls in javap,(mixin,cls)
    print("PASS",mixin,cls,name+mapped,flush=True)
for args in [
    ("ArenaTeleportMixin","net/minecraft/server/network/ServerGamePacketListenerImpl","teleport","(DDDFFLjava/util/Set;)V"),
    ("ArenaPlayerEquipmentMixin","net/minecraft/server/level/ServerPlayer","drop","(Z)Z"),
    ("ArenaContainerEquipmentMixin","net/minecraft/world/inventory/AbstractContainerMenu","clicked","(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"),
    ("ArenaItemPickupMixin","net/minecraft/world/entity/item/ItemEntity","playerTouch","(Lnet/minecraft/world/entity/player/Player;)V"),
    ("ArenaRecipeEquipmentMixin","net/minecraft/world/inventory/RecipeBookMenu","handlePlacement","(ZLnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/server/level/ServerPlayer;)V"),
    ("ArenaArrowPickupMixin","net/minecraft/world/entity/projectile/AbstractArrow","playerTouch","(Lnet/minecraft/world/entity/player/Player;)V"),
    ("ArenaFlightRocketMixin","net/minecraft/world/item/FireworkRocketItem","use","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"),
    ("ArenaSiegeShopPacketMixin","net/minecraft/server/network/ServerGamePacketListenerImpl","handleContainerClick","(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V"),
]:audit(*args)
print("PASS eight vanilla Mixin selectors remapped and resolved in real intermediary Minecraft; no product artifact",flush=True)

analog=ROOT/"_yedek/sovereign-uyum-20261125-221852/server/mods/analog-1.2.2+1.21.jar"
assert hashlib.sha256(analog.read_bytes()).hexdigest()=="0faeef984050724dac008e6a84a00c71e83f41419c9f151d1325e2804a99e91d"
def dump(cp,cls,*flags):
    return subprocess.check_output([str(JAVA/"javap.exe"),"-p",*flags,"-classpath",str(cp),cls],text=True,encoding="utf-8",errors="replace")
def block(text,method):
    lines=text.splitlines()
    starts=[i for i,l in enumerate(lines) if l.startswith("  ") and not l.startswith("   ") and method+"(" in l]
    assert len(starts)==1,(method,starts)
    i=starts[0];end=next((k for k in range(i+1,len(lines)) if lines[k].startswith("  ") and not lines[k].startswith("   ") and "(" in lines[k]),len(lines))
    return "\n".join(lines[i:end])
radio=dump(analog,"dev.mrturtle.analog.util.RadioUtil","-c","-s")
plugin=dump(analog,"dev.mrturtle.analog.AnalogPlugin","-c","-s")
voice=dump(out,"tr.kapisma.mixin.AnalogVoiceScopeMixin","-v")
micro=dump(out,"tr.kapisma.mixin.AnalogMicrophoneScopeMixin","-v")
execute="net/minecraft/server/MinecraftServer.execute:(Ljava/lang/Runnable;)V"
loc="VoicechatServerApi.sendLocationalSoundPacketTo:"
for target,method,invoke,annotation in [(radio,"transmitOnChannel",loc,voice),(radio,"transmitOnChannel",execute,voice),(radio,"transmitOnNearbyTransmitters",execute,voice),(radio,"lambda$transmitOnChannel$2",loc,voice),(plugin,"onMicrophonePacket",execute,micro)]:
    body=block(target,method)
    assert body.count(invoke)==1,(method,invoke,body)
    assert '"'+method+'"' in annotation,(method,"selector")
    print("PASS Analog exact invocation",method,invoke,flush=True)
assert "Lnet/minecraft/class_3222;I)V" in voice and "Lnet/minecraft/class_3218;I" in voice
assert "target=\"Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V\"" in voice and "target=\"Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V\"" in micro
print("PASS Analog mapped handler descriptors and unchanged remap=false targets",flush=True)