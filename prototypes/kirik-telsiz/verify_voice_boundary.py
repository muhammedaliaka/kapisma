"""Read-only static gate for the exact local Analog / SVC versions.
This is not a voice delivery test and does not claim an actual leak.
"""
from pathlib import Path
import hashlib, os, re, subprocess

ROOT = Path(__file__).resolve().parents[2]
MODS = ROOT / "_yedek/sovereign-uyum-20261125-221852/server/mods"
ANALOG = MODS / "analog-1.2.2+1.21.jar"
VOICE = MODS / "voicechat-fabric-1.21.1-2.6.27.jar"
JAVAP = Path(os.environ["JAVA_HOME"]) / "bin/javap.exe"

def inspect(jar, cls):
    return subprocess.check_output([str(JAVAP), "-classpath", str(jar), "-c", "-p", cls],
        encoding="utf-8", errors="replace", stderr=subprocess.DEVNULL)

def method(output, signature):
    start=output.index(signature)
    tail=output[start:]
    next_method=re.search(r"\n  (?:public|private|protected|static) ", tail[1:])
    return tail[:next_method.start()+1] if next_method else tail

def check():
    for path, digest in [(ANALOG,"0FAEEF984050724DAC008E6A84A00C71E83F41419C9F151D1325E2804A99E91D"),
        (VOICE,"BAE2C4FA9F7764ABAC621EE70E1099BD39B7316CBC636280D188045B07F17AB1")]:
        assert hashlib.sha256(path.read_bytes()).hexdigest().upper()==digest
    analog=inspect(ANALOG,"dev.mrturtle.analog.util.RadioUtil")
    tx=method(analog,"public static void transmitOnChannel(")
    assert "isReceivingChannel:" in tx and "sendLocationalSoundPacketTo:" in tx
    print("PASS Analog channel path uses direct SVC API",flush=True)
    api=inspect(VOICE,"de.maxhenkel.voicechat.plugins.impl.VoicechatServerApiImpl")
    loc=method(api,"public void sendLocationalSoundPacketTo(")
    send=method(api,"public static void sendPacket(net.minecraft.")
    assert "Method sendPacket:" in loc
    assert re.search(r"aconst_null\s+\d+: aconst_null[\s\S]*?String plugin[\s\S]*?Server.sendSoundPacket:",send)
    print("PASS direct plugin send passes null sender/player state",flush=True)
    server=method(inspect(VOICE,"de.maxhenkel.voicechat.voice.server.Server"),"public void sendSoundPacket(")
    assert "PluginManager.onSoundPacket:" in server
    events=method(inspect(VOICE,"de.maxhenkel.voicechat.plugins.PluginManager"),"public boolean onSoundPacket(")
    assert "aconst_null" in events and "LocationalSoundPacketEventImpl" in events
    assert re.search(r"aload_1\s+\d+: ifnull",events)
    print("PASS sound event constructed with nullable sender",flush=True)
    src=(ROOT/"mod/kapisma/src/main/java/tr/kapisma/voice/KapismaVoice.java").read_text(encoding="utf-8")
    match=re.search(r"private void isolateSpectator\([^)]*\)\s*\{([\s\S]*?)\n\s*\}",src)
    assert match and "sender != null" in match[1] and "snapshot.permits(" in match[1] and "event.cancel()" in match[1]
    print("BLOCK LIVE: current Kapisma source only cancels when sender is non-null; real audio acceptance required",flush=True)
if __name__=="__main__":
    check()
