"""Explicit recovery for interrupted MOD-lab test setup. Default: validate only."""
import argparse, base64, hashlib, json, msvcrt, socket
from pathlib import Path
from verify import ROOT, LAB, PROPS, VOICE, port_free, digest
from profile_guard import snapshot_profile

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("snapshot",type=Path)
    parser.add_argument("--apply",action="store_true")
    args=parser.parse_args()
    root=(ROOT/"prototypes/kirik-telsiz/profile-recovery").resolve()
    path=args.snapshot.resolve()
    assert path.parent==root and path.suffix==".json","Unexpected snapshot path"
    raw=json.loads(path.read_text(encoding="utf-8"))
    expected={str(PROPS),str(VOICE)}
    assert set(raw)==expected,"Unexpected recovery destinations"
    recovered={}
    for p in (PROPS,VOICE):
        item=raw[str(p)]
        data=base64.b64decode(item["bytes_b64"],validate=True)
        assert hashlib.sha256(data).hexdigest()==item["sha256"],"Snapshot integrity mismatch"
        recovered[p]=data
    assert b"server-ip=127.0.0.1" in recovered[PROPS] and b"level-name=smoke-world" in recovered[PROPS] and b"server-port=25572" in recovered[PROPS]
    assert b"bind_address=127.0.0.1" in recovered[VOICE] and b"port=25573" in recovered[VOICE]
    print("PASS validated recovery snapshot",flush=True)
    if not args.apply:
        print("Dry run: no files changed",flush=True);return
    with (LAB/".mctest.lock").open("a+b") as lock:
        lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        for port,kind in ((25572,socket.SOCK_STREAM),(25578,socket.SOCK_STREAM),(25573,socket.SOCK_DGRAM),(25579,socket.SOCK_DGRAM)):
            port_free(port,kind)
        current=PROPS.read_bytes();voice=VOICE.read_bytes()
        assert current==recovered[PROPS] or (b"level-name=kirik-telsiz-lab" in current and b"server-port=25578" in current and b"server-ip=127.0.0.1" in current),"Current profile not recognized"
        assert voice==recovered[VOICE] or (b"port=25579" in voice and b"bind_address=127.0.0.1" in voice),"Current voice profile not recognized"
        snapshot_profile((PROPS,VOICE))
        for p,data in recovered.items():p.write_bytes(data)
        assert all(p.read_bytes()==data for p,data in recovered.items())
        print("PASS exact snapshot restoration",flush=True)
if __name__=="__main__":main()
