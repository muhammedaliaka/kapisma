"""Measure effective ART memory performance, not DRAM clock. Restore packages."""
import re,time,json,statistics,subprocess
from setup_bridge import adb,check,BASE,ADB,SERIAL
from ram_cpu_acceptance import helper,active,disabled
REMOTE="/data/local/tmp/kapisma-memory-probe-r1"
OUT=BASE.parent/"evidence"/"phone-memory-probe-20261127"
def thermal():
 s=adb("shell","dumpsys","thermalservice")
 m=re.search(r"Thermal Status:\s*(\d+)",s)
 return int(m[1]) if m else None,s
def main():
 check();OUT.mkdir(parents=True,exist_ok=True)
 if active():raise RuntimeError("existing server mode")
 if "java" in adb("shell","ps","-A","-o","NAME").splitlines():raise RuntimeError("server already running")
 if thermal()[0]!=0:raise RuntimeError("thermal status not normal")
 original=disabled()
 if adb("shell",f"if [ -e {REMOTE} ]; then echo yes; else echo no; fi")!="no":raise RuntimeError("test directory exists")
 adb("shell",f"umask 077; mkdir {REMOTE}")
 results=[]
 try:
  adb("push",BASE/"build/memory-probe/probe.jar",REMOTE+"/probe.jar")
  for stage in ("normal","ultimate"):
   if stage=="ultimate":
    print(helper("server_mode.sh","--enter").splitlines()[-1],flush=True)
    print(helper("p1_cleanup.sh","--apply").strip(),flush=True)
   status,before=thermal()
   if status!=0:raise RuntimeError("thermal guard")
   (OUT/(stage+"-thermal-before.txt")).write_text(before,encoding="utf-8")
   mem=adb("shell","cat","/proc/meminfo")
   (OUT/(stage+"-mem-before.txt")).write_text(mem,encoding="utf-8")
   proc=subprocess.run([str(ADB),"-s",SERIAL,"shell",f"CLASSPATH={REMOTE}/probe.jar timeout 45 /system/bin/app_process /system/bin --nice-name=ultra-memory-probe tr.kapisma.ultra.probe.MemoryProbe"],capture_output=True,timeout=55)
   r=proc.stdout.decode(errors="replace")
   (OUT/(stage+"-stderr.txt")).write_text(proc.stderr.decode(errors="replace"),encoding="utf-8")
   if proc.returncode:raise RuntimeError(f"probe exit={proc.returncode}: {r[-1000:]} {proc.stderr.decode(errors='replace')[-1000:]}")
   (OUT/(stage+".txt")).write_text(r,encoding="utf-8")
   if "probe_complete" not in r:raise RuntimeError("incomplete probe")
   vals={}
   for k in ("copy_payload_GiB_s","sequential_read_GiB_s","dependent_latency_ns"):
    v=[float(x) for x in re.findall(k+r"=([0-9.]+)",r)]
    if k=="sequential_read_GiB_s":v=v[1:]
    if len(v)!=3:raise RuntimeError("wrong sample count "+k)
    vals[k]={"samples":v,"median":statistics.median(v)}
   status,after=thermal()
   (OUT/(stage+"-thermal-after.txt")).write_text(after,encoding="utf-8")
   result={"stage":stage,"thermal_after":status,"metrics":vals}
   results.append(result);print(json.dumps(result),flush=True)
   if status!=0:raise RuntimeError("thermal status changed; stop test")
   time.sleep(3)
 finally:
  if active():print(helper("server_mode.sh","--leave").splitlines()[-1],flush=True)
  diff=disabled()^original
  print("RESTORE_DIFF",len(diff),flush=True)
  adb("shell",f"rm -f {REMOTE}/probe.jar; rmdir {REMOTE}")
  (OUT/"results.json").write_text(json.dumps(results,indent=2),encoding="utf-8")
  if diff:raise RuntimeError("restore mismatch")
  print("mobile_data="+adb("shell","settings","get","global","mobile_data"),"airplane_mode="+adb("shell","settings","get","global","airplane_mode_on"),flush=True)
if __name__=="__main__":main()
