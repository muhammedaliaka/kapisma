"""Host runner for generated profiles; no automatic downloads, pairing or radio changes."""
import argparse, subprocess, hashlib, json, sys, time, re, csv
from pathlib import Path
from build_profiles import PROFILES, OUT, ROOT
def manifest(profile):
 d=OUT/profile; m=json.loads((d/"manifest.json").read_text(encoding="utf-8"))
 for key,value in PROFILES[profile].items():
  if m[key]!=value:raise RuntimeError("profile identity mismatch")
 if set(m["sha256"])!={"server_mode.sh","p1_cleanup.sh","daily.sh","launch.sh"}:raise RuntimeError("incomplete manifest")
 for n,h in m["sha256"].items():
  if hashlib.sha256((d/n).read_bytes()).hexdigest()!=h:raise RuntimeError("asset hash mismatch: "+n)
 return m,d
def run(adb,serial,args,data=None,timeout=30):
 p=subprocess.run([str(adb),"-s",serial,*args],input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if p.returncode:raise RuntimeError(p.stderr.decode(errors="replace")+"\n"+p.stdout.decode(errors="replace"))
 return p.stdout.decode("utf-8",errors="replace").replace("\r","")
def main():
 p=argparse.ArgumentParser()
 p.add_argument("profile",choices=PROFILES)
 p.add_argument("action",choices=["status","plan","enter","leave","clean","daily","measure"])
 p.add_argument("--adb",required=True,type=Path)
 p.add_argument("--out",type=Path)
 a=p.parse_args();m,d=manifest(a.profile);s=m["serial"]
 call=lambda *x:run(a.adb,s,list(x))
 if call("get-state").strip()!="device":raise RuntimeError("device unavailable")
 if call("shell","getprop","ro.serialno").strip()!=s:raise RuntimeError("device serial mismatch")
 if m["model"] and call("shell","getprop","ro.product.model").strip()!=m["model"]:raise RuntimeError("device model mismatch")
 if a.action=="measure":
  samples=[]
  for i in range(5):
   raw=call("shell","cat","/proc/meminfo")
   v=int(re.search(r"(?m)^MemAvailable:\s+(\d+)",raw)[1]);samples.append(v)
   if a.out:
    a.out.mkdir(parents=True,exist_ok=True)
    (a.out/f"sample-{i+1}.txt").write_text(raw,encoding="utf-8")
   if i!=4:time.sleep(2)
  dump=call("shell","dumpsys","meminfo","-S")
  if a.out:
   (a.out/"dumpsys.txt").write_text(dump,encoding="utf-8")
   for name,args in {"packages":["pm","list","packages","--user","0"],"disabled":["pm","list","packages","-d","--user","0"],"power":["dumpsys","power"]}.items():
    (a.out/(name+".txt")).write_text(call("shell",*args),encoding="utf-8")
  print(json.dumps({"profile":a.profile,"samples_kib":samples,"mean_mib":sum(samples)/len(samples)/1024,"min_mib":min(samples)/1024,"max_mib":max(samples)/1024,"mobile_data":call("shell","settings","get","global","mobile_data").strip(),"airplane_mode":call("shell","settings","get","global","airplane_mode_on").strip(),"java_running":"java" in call("shell","ps","-A","-o","NAME").splitlines()}))
  return
 helper,flag={"clean":("p1_cleanup.sh","--apply"),"daily":("daily.sh","--apply")}.get(a.action,("server_mode.sh","--"+a.action))
 print(run(a.adb,s,["shell","sh","-s","--",flag],(d/helper).read_bytes(),timeout=150),end="")
if __name__=="__main__":
 try:main()
 except (RuntimeError,OSError,subprocess.TimeoutExpired,ValueError) as e:print("ULTIMATE:",e,file=sys.stderr);sys.exit(2)
