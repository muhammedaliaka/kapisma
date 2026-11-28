"""Persist recovery bytes before mutating the two MOD lab configuration files."""
from pathlib import Path
import base64, hashlib, json, os, time, uuid
BASE=Path(__file__).resolve().parent
def snapshot_profile(paths):
    before={p:p.read_bytes() for p in paths}
    root=BASE/"profile-recovery"
    root.mkdir(exist_ok=True)
    payload={str(p):{"sha256":hashlib.sha256(data).hexdigest(),"bytes_b64":base64.b64encode(data).decode("ascii")} for p,data in before.items()}
    path=root/(time.strftime("%Y%m%d-%H%M%S")+"-"+uuid.uuid4().hex+".json")
    with path.open("x",encoding="utf-8") as out:
        json.dump(payload,out);out.flush();os.fsync(out.fileno())
    check=json.loads(path.read_text(encoding="utf-8"))
    assert all(base64.b64decode(check[str(p)]["bytes_b64"])==data and check[str(p)]["sha256"]==hashlib.sha256(data).hexdigest() for p,data in before.items())
    print("PROFILE_RECOVERY_SNAPSHOT "+str(path),flush=True)
    return before
