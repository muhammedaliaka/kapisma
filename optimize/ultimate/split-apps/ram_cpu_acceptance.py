"""Physical staged RAM/CPU measurement; restore original package states in finally."""
import json,re,time,subprocess,statistics
from pathlib import Path
from setup_bridge import adb,check,BASE,ADB,SERIAL
ROOT=BASE.parent
OUT=ROOT/"evidence"/"phone-r3-20261127"
STATE="/data/local/tmp/kapisma-phone-mode-v1"
EXTRA=("com.rhmsoft.edit","com.mi.appfinder","com.xiaomi.mipicks","com.xiaomi.glgm","com.android.thememanager","com.facebook.appmanager","com.facebook.services","com.microsoft.appmanager")
def helper(name,flag):
 p=subprocess.run([str(ADB),"-s",SERIAL,"shell","sh","-s","--",flag],input=(ROOT/"generated/phone"/name).read_bytes(),capture_output=True,timeout=150)
 if p.returncode:raise RuntimeError(p.stdout.decode(errors="replace")+" "+p.stderr.decode(errors="replace"))
 return p.stdout.decode(errors="replace")
def active():return adb("shell",f"if [ -f {STATE}/journal ]; then echo yes; else echo no; fi")=="yes"
def disabled():return set(adb("shell","pm","list","packages","-d","--user","0").splitlines())
def cpu():
 raw=adb("shell","head","-n","1","/proc/stat")
 n=list(map(int,raw.split()[1:9]));return sum(n),n[3]+n[4]
def sample(name):
 rows=[];prev=cpu()
 for i in range(3):
  time.sleep(2)
  now=cpu();dt=now[0]-prev[0];di=now[1]-prev[1];prev=now
  raw=adb("shell","cat","/proc/meminfo")
  ram=int(re.search(r"(?m)^MemAvailable:\s+(\d+)",raw)[1])
  rows.append({"ram_kib":ram,"cpu_busy_pct":round(100*(dt-di)/dt,2) if dt>0 else None})
  (OUT/f"{name}-mem-{i+1}.txt").write_text(raw,encoding="utf-8")
 thermal=adb("shell","dumpsys","thermalservice")
 (OUT/f"{name}-thermal.txt").write_text(thermal,encoding="utf-8")
 status=re.search(r"Thermal Status:\s*(\d+)",thermal)
 result={"stage":name,"samples":rows,"mean_ram_mib":round(statistics.mean(x["ram_kib"] for x in rows)/1024,2),"mean_cpu_pct":round(statistics.mean(x["cpu_busy_pct"] for x in rows if x["cpu_busy_pct"] is not None),2),"thermal_status":int(status[1]) if status else None}
 print(json.dumps(result),flush=True);return result
def main():
 check();OUT.mkdir(parents=True,exist_ok=True)
 if active():raise RuntimeError("mode already active")
 original=disabled();results=[]
 if "java" in adb("shell","ps","-A","-o","NAME").splitlines():raise RuntimeError("Java running")
 if adb("shell","settings","get","global","mobile_data")!="1":raise RuntimeError("unexpected mobile data state")
 try:
  adb("shell","input","keyevent","223")
  time.sleep(3)
  results.append(sample("baseline"))
  print(helper("server_mode.sh","--enter").splitlines()[-1],flush=True)
  print(helper("p1_cleanup.sh","--apply").strip(),flush=True)
  results.append(sample("standard"))
  # Only Android's background kill API, not force-stop; unique UID, safe allowlist.
  ime=adb("shell","settings","get","secure","default_input_method")
  access=adb("shell","settings","get","secure","enabled_accessibility_services")
  uids=adb("shell","pm","list","packages","-U","--user","0").splitlines()
  for pkg in EXTRA:
   if pkg+"/" in ime+" "+access:continue
   matches=[line.split()[-1] for line in uids if line.split()[0]=="package:"+pkg]
   if len(matches)!=1 or sum(line.split()[-1]==matches[0] for line in uids)!=1:continue
   adb("shell","am","kill","--user","0",pkg)
  results.append(sample("extra"))
  (OUT/"final-dump.txt").write_text(adb("shell","dumpsys","meminfo","-S"),encoding="utf-8")
 finally:
  if active():print(helper("server_mode.sh","--leave").splitlines()[-1],flush=True)
  diff=disabled()^original
  print("RESTORE_DIFF",len(diff),flush=True)
  if diff:raise RuntimeError("restoration mismatch")
  adb("shell","input","keyevent","224")
  print("mobile_data="+adb("shell","settings","get","global","mobile_data"),"airplane_mode="+adb("shell","settings","get","global","airplane_mode_on"),flush=True)
  (OUT/"results.json").write_text(json.dumps(results,indent=2),encoding="utf-8")
if __name__=="__main__":main()
