"""Physical UI acceptance. Never prints credentials or UI dump."""
import sys,time,re,subprocess,xml.etree.ElementTree as ET
from setup_bridge import adb,check,BASE,ADB,SERIAL
DUMP="/data/local/tmp/kapisma-ultra-ui-test.xml"
STATE="/data/local/tmp/kapisma-phone-mode-v1"
def nodes():
    adb("shell","uiautomator","dump",DUMP,timeout=15)
    return list(ET.fromstring(adb("shell","cat",DUMP)).iter("node"))
def find(text,timeout=15):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        for n in nodes():
            if n.get("text","").casefold().startswith(text.casefold()):
                return n
        time.sleep(.5)
    raise RuntimeError("UI text not found: "+text)
def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds","")))
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
def tap(text):tap_node(find(text))
def active():
    return adb("shell",f"if [ -f {STATE}/journal ]; then echo yes; else echo no; fi")=="yes"
def disabled():
    return set(adb("shell","pm","list","packages","-d","--user","0").splitlines())
def setup_app(pkg,role):
    adb("shell","am","start","-n",pkg+"/.MainActivity")
    find("ULTRA "+("Cleaner" if role=="daily" else "Server"))
    tap("Bağlantı ayarları")
    n=next((x for x in nodes() if x.get("class")=="android.widget.EditText"),None)
    if n is None:raise RuntimeError("key field not found")
    tap_node(n)
    key=(BASE/"build/bridge-keys"/(role+".token")).read_text().strip()
    # Never log the secret or the dump. Input is typed into a password field.
    adb("shell","input","text",key)
    tap("Kaydet")
    find("Android kullanılabilir RAM:" if role=="daily" else "Sunucu modu kapalı",timeout=30)
    print("PASS",role,"app installed, local key saved, real backend connected",flush=True)
def main():
    check()
    if active():raise RuntimeError("server mode unexpectedly active before test")
    original=disabled()
    expected=set((BASE.parent.parent/"phone-mode-before-disabled-20261127.txt").read_text(encoding="utf-8-sig").splitlines())
    if original!=expected:raise RuntimeError("baseline package state changed")
    try:
        setup_app("tr.kapisma.ultra.cleaner","daily")
        tap("RAM TEMİZLE")
        find("Temizlik tamamlandı",timeout=45)
        if disabled()!=original or active():raise RuntimeError("daily cleanup changed enabled states")
        print("PASS daily button, no package disabled",flush=True)
        setup_app("tr.kapisma.ultra.server","server")
        tap("SUNUCU MODUNU AÇ")
        find("Sunucu modu aktif",timeout=120)
        journal=adb("shell","cat",STATE+"/journal").splitlines()
        after=disabled()
        packages={"package:"+x.split()[0] for x in journal}
        if not packages or after-original!=packages:raise RuntimeError("isolation state mismatch")
        print("PASS one-tap enter, isolated packages:",len(packages),flush=True)
        close=find("SUNUCU MODUNU KAPAT")
        tap_node(close)
        # First tap must not change state; check within the eight-second window.
        if not active() or disabled()!=after:raise RuntimeError("first tap changed mode")
        print("PASS first close tap does not restore",flush=True)
        tap_node(close)
        find("Sunucu modu kapalı",timeout=120)
        if active() or disabled()!=original:raise RuntimeError("double tap did not restore exact state")
        print("PASS second close tap restores original disabled list",flush=True)
    finally:
        if active():
            helper=BASE.parent/"generated/phone/server_mode.sh"
            r=subprocess.run([str(ADB),"-s",SERIAL,"shell","sh","-s","--","--leave"],input=helper.read_bytes(),capture_output=True,timeout=150)
            if r.returncode:raise RuntimeError("emergency restore failed; inspect journal")
        if disabled()!=original:raise RuntimeError("FINAL restoration mismatch")
        adb("shell","rm","-f",DUMP)
        print("FINAL disabled-list difference=0; mode inactive",flush=True)
        print("mobile_data="+adb("shell","settings","get","global","mobile_data"),"airplane_mode="+adb("shell","settings","get","global","airplane_mode_on"),flush=True)
if __name__=="__main__":main()
