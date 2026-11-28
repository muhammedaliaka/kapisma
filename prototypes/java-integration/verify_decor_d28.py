"""Temporary QA-only pack path; preserve previous pack and configuration bytes."""
from pathlib import Path
import hashlib,json,os,subprocess,sys
base=Path(__file__).resolve().parent
run=base/"build/runtime-proof"
cfg=run/"config/polymer/resource-pack.json"
old=cfg.read_bytes()
default=run/"polymer/resource_pack.zip"
digest=hashlib.sha256(default.read_bytes()).hexdigest()
candidate=run/"polymer/kapisma-decor-d28.zip"
if candidate.exists():assert hashlib.sha256(candidate.read_bytes()).hexdigest()=="f659368292b8a5b61f6c95b447b0585f869e1e42b12be865d644036051554794","preserve unrecognized pack"
data=json.loads(old);data["resource_pack_location"]="polymer/kapisma-decor-d28.zip"
new=json.dumps(data,indent=2).encode("utf-8")
env=dict(os.environ,KAPISMA_QA_EXTERNAL="decor")
try:
    cfg.write_bytes(new)
    result=subprocess.run([sys.executable,"-B",str(base/"verify_runtime.py"),"--caravan-decor"],cwd=base,env=env)
finally:
    # Fail closed rather than overwrite any concurrent configuration changes.
    if json.loads(cfg.read_bytes())!=data:raise RuntimeError("QA config changed during decor test; restore refused")
    cfg.write_bytes(old)
    assert hashlib.sha256(default.read_bytes()).hexdigest()==digest
    print("PASS prior pack bytes and config restored",flush=True)
sys.exit(result.returncode)
