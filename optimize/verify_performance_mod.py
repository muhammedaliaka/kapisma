"""Read-only candidate intake. Hash/metadata/Java class audit, NOT a runtime compatibility certificate."""
import argparse,hashlib,io,json,struct,zipfile
from pathlib import Path

def inspect_blob(blob,entry):
    fail=[];info={}
    if len(blob)!=entry["bytes"]:fail.append("size mismatch")
    if hashlib.sha512(blob).hexdigest()!=entry["sha512"].lower():fail.append("publisher SHA512 mismatch")
    if fail:return {"intake_pass":False,"failures":fail,"metadata":info,"runtime_certified":False}
    try:
        with zipfile.ZipFile(io.BytesIO(blob)) as z:
            names=z.namelist()
            if len(names)!=len(set(names)):raise ValueError("duplicate ZIP names")
            if sum(n.file_size for n in z.infolist())>32*1024*1024:raise ValueError("archive expansion budget")
            if z.testzip() is not None:raise ValueError("CRC mismatch")
            meta=json.loads(z.read("fabric.mod.json"))
            if meta.get("id")!=entry["id"]:raise ValueError("Fabric mod id mismatch")
            major=[]
            for name in names:
                if not name.endswith(".class"):continue
                # Java 21 ignores newer multi-release variants.
                if name.startswith("META-INF/versions/") and int(name.split("/")[2])>21:continue
                with z.open(name) as f:header=f.read(8)
                if len(header)!=8 or header[:4]!=b"\xca\xfe\xba\xbe":raise ValueError("invalid class header")
                major.append(struct.unpack(">H",header[6:8])[0])
            if not major or max(major)>65:raise ValueError("Java 21 class compatibility failed")
            info={"id":meta["id"],"version":meta.get("version"),"environment":meta.get("environment","*"),"depends":meta.get("depends",{}),
                  "breaks":meta.get("breaks",{}),"mixins":meta.get("mixins",[]),"nested_jars":meta.get("jars",[]),
                  "max_class_major":max(major),"native_files":[n for n in names if n.lower().endswith((".so",".dll",".dylib"))]}
            if info["environment"]=="client":raise ValueError("client-only mod")
    except (ValueError,KeyError,TypeError,zipfile.BadZipFile,RuntimeError,OSError) as e:fail.append(str(e))
    return {"intake_pass":not fail,"failures":fail,"metadata":info,"runtime_certified":False,
            "note":"Nested jars and mixin interactions need separate runtime validation. No installation or configuration change."}

def main():
    p=argparse.ArgumentParser();p.add_argument("jar",type=Path);p.add_argument("--manifest",type=Path,default=Path(__file__).with_name("performance-mod-candidates-20261126.json"));a=p.parse_args()
    m=json.loads(a.manifest.read_text(encoding="utf-8-sig"))
    entries=[x for x in m["candidates"] if x["filename"]==a.jar.name]
    if len(entries)!=1:print("unlisted candidate");return 2
    if a.jar.stat().st_size!=entries[0]["bytes"]:print("size mismatch");return 1
    result=inspect_blob(a.jar.read_bytes(),entries[0]);print(json.dumps(result,indent=2));return 0 if result["intake_pass"] else 1
if __name__=="__main__":raise SystemExit(main())
