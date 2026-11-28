"""One-time repair of MOD lab settings after verify_client finally shadow bug.
Reconstruct server.properties from sibling profile and original smoke-world NBT;
this cannot recover the exact previous server.properties bytes.
"""
import msvcrt,re,socket
from pathlib import Path
from verify import ROOT, LAB, PROPS, VOICE, digest, port_free
from inspect_smoke import read
BASE=ROOT/"prototypes/kirik-telsiz"
def main():
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for port,kind in ((25572,socket.SOCK_STREAM),(25578,socket.SOCK_STREAM),(25579,socket.SOCK_DGRAM)):
            port_free(port,kind)
        assert digest(PROPS)=="E625F633846097B98067DDEFA354CCA818C58F31792EE217B0A7321453DFC84E"
        assert digest(VOICE)=="D264C14DF36B43E694F834A14A1860488A00E97B5B15F211D191DAB35712F488"
        original_world=read(LAB/"smoke-world/level.dat")["Data"]
        assert original_world["LevelName"]=="smoke-world"
        sibling=LAB.parent/"hundred-years-server/server.properties"
        assert digest(sibling)=="D16AA59E81CD5BDB93AB0103C6FAD9602B3A603B18CEE95D985FAE58E5E1AA4F"
        props=sibling.read_bytes()
        seed=original_world["WorldGenSettings"]["seed"]
        packs=",".join(original_world["DataPacks"]["Enabled"]).encode("ascii")
        assert f"level-seed={seed}".encode("ascii") in props
        assert b"server-ip=127.0.0.1" in props
        assert b"server-port=25574" in props
        props=props.replace(b"server-port=25574",b"server-port=25572")
        props=re.sub(rb"(?m)^initial-enabled-packs=[^\r\n]+",b"initial-enabled-packs="+packs,props)
        props=re.sub(rb"(?m)^#Wed Nov 25[^\r\n]+",b"# Reconstructed from sibling profile and smoke-world NBT 2026-11-26",props)
        voice=VOICE.read_bytes()
        assert voice.count(b"port=25579")==1
        voice=voice.replace(b"port=25579",b"port=25573")
        assert b"bind_address=127.0.0.1" in voice
        for name,data in (("interrupted-client-server.properties",PROPS.read_bytes()),("interrupted-client-voice.properties",VOICE.read_bytes()),("reconstructed-smoke-server.properties",props),("restored-smoke-voice.properties",voice)):
            with (BASE/name).open("xb") as f:f.write(data)
        PROPS.write_bytes(props);VOICE.write_bytes(voice)
        assert PROPS.read_bytes()==props and VOICE.read_bytes()==voice
        print("PASS reconstructed smoke profile",digest(PROPS),flush=True)
        print("PASS restored voice port",digest(VOICE),flush=True)
        print("NOTE original server.properties byte identity not established",flush=True)
if __name__=="__main__": main()