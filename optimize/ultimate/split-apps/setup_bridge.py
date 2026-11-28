"""Explicit authorized USB or wireless ADB setup of a restricted local shell bridge. No root or radio changes.
Keys are random, role-separated and never printed. Use only on the exact phone.
"""
import os,sys,subprocess,secrets,pathlib,time,json,ipaddress
BASE=pathlib.Path(__file__).resolve().parent
PHONE_SERIAL="HM59FYLJEY59M7O7"
def transport(value):
    if value==PHONE_SERIAL:return value
    host,sep,port=value.rpartition(":")
    if not sep or not port.isascii() or not port.isdigit() or not 1<=int(port)<=65535:
        raise ValueError("Expected exact USB serial or explicit IPv4:port")
    ipaddress.IPv4Address(host)
    return value
# Legacy consumers use SERIAL for the adb -s transport selector, not the phone identity.
SERIAL=transport(os.environ.get("ULTRA_ADB_TRANSPORT",PHONE_SERIAL))
REMOTE="/data/local/tmp/kapisma-ultimate-bridge"
ADB=pathlib.Path(os.environ["LOCALAPPDATA"])/"Android/Sdk/platform-tools/adb.exe"
def adb(*args,timeout=30):
    p=subprocess.run([str(ADB),"-s",SERIAL,*map(str,args)],capture_output=True,timeout=timeout)
    if p.returncode:raise RuntimeError("ADB failure: "+p.stderr.decode(errors="replace").strip())
    return p.stdout.decode("utf-8",errors="replace").strip()
def check():
    if adb("get-state")!="device" or adb("shell","getprop","ro.serialno")!=PHONE_SERIAL:raise RuntimeError("wrong or unavailable phone")
    if adb("shell","getprop","ro.product.model")!="23090RA98I":raise RuntimeError("wrong model")
def setup():
    check()
    if adb("shell",f"if [ -e {REMOTE} ]; then echo exists; else echo absent; fi")!="absent":
        raise RuntimeError("Existing bridge directory; inspect before replacing")
    keys=BASE/"build"/"bridge-keys";keys.mkdir(exist_ok=True)
    for role in ("daily","server"):
        (keys/(role+".token")).write_text(secrets.token_hex(32),encoding="ascii")
    # The directory is shell-private before transferring credentials.
    adb("shell",f"umask 077; mkdir {REMOTE} && chmod 700 {REMOTE}")
    profile=BASE.parent/"generated"/"phone"
    for path in [BASE/"build"/"bridge.jar",profile/"daily.sh",profile/"server_mode.sh",profile/"p1_cleanup.sh",keys/"daily.token",keys/"server.token"]:
        adb("push",path,REMOTE+"/"+path.name)
    adb("shell",f"chmod 600 {REMOTE}/*")
    # No arbitrary remote commands are supported by the bridge.
    adb("shell",f"CLASSPATH={REMOTE}/bridge.jar nohup setsid /system/bin/app_process /system/bin --nice-name=ultra-cleanup-bridge tr.kapisma.ultra.bridge.Bridge </dev/null >{REMOTE}/bridge.log 2>&1 &")
    time.sleep(2)
    log=adb("shell","cat",REMOTE+"/bridge.log")
    if "ULTRA bridge listening on loopback" not in log:raise RuntimeError("Bridge did not start: "+log[:1000])
    print("Restricted loopback bridge started; two role keys generated; no keys printed.")
if __name__=="__main__":
    try:setup()
    except Exception as e:print(str(e),file=sys.stderr);sys.exit(2)
