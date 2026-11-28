"""Restart the already provisioned helper after phone reboot; never change keys."""
from setup_bridge import adb,check,REMOTE,BASE
import hashlib,time,sys
def main():
    check()
    if adb("shell",f"if [ -d {REMOTE} ]; then echo yes; else echo no; fi")!="yes":
        raise RuntimeError("No provisioned helper; initial USB setup needed")
    if any(line.split()[-1]=="ultra-cleanup-bridge" for line in adb("shell","ps","-A","-o","UID,PID,NAME").splitlines() if line.split()):
        print("Helper already running");return
    profile=BASE.parent/"generated"/"phone"
    paths=[BASE/"build/bridge.jar",profile/"daily.sh",profile/"server_mode.sh",profile/"p1_cleanup.sh",BASE/"build/bridge-keys/daily.token",BASE/"build/bridge-keys/server.token"]
    for p in paths:
        expected=hashlib.sha256(p.read_bytes()).hexdigest()
        actual=adb("shell","sha256sum",REMOTE+"/"+p.name).split()[0]
        if actual!=expected:raise RuntimeError("Bridge asset mismatch: "+p.name)
    adb("shell",f"CLASSPATH={REMOTE}/bridge.jar nohup setsid /system/bin/app_process /system/bin --nice-name=ultra-cleanup-bridge tr.kapisma.ultra.bridge.Bridge </dev/null >{REMOTE}/bridge.log 2>&1 &")
    time.sleep(2)
    if "ULTRA bridge listening on loopback" not in adb("shell","cat",REMOTE+"/bridge.log"):raise RuntimeError("Helper restart failed")
    print("Local helper restarted with existing keys")
if __name__=="__main__":
    try:main()
    except Exception as e:print(str(e),file=sys.stderr);sys.exit(2)
