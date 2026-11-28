"""Read-only inventory; policies are intent, not forced swap placement."""
import argparse,csv,re,json
from collections import Counter,defaultdict
from pathlib import Path
P1=set("""com.google.android.inputmethod.latin com.miui.miwallpaper com.google.android.gm com.google.android.apps.maps com.xiaomi.mipicks com.google.android.apps.docs com.android.chrome com.google.android.apps.chromecast.app com.google.android.apps.photos com.mi.appfinder com.mi.global.bbs com.microsoft.appmanager com.google.android.apps.tachyon com.google.android.youtube com.google.android.apps.youtube.music com.google.android.projection.gearhead com.miui.android.fashiongallery com.android.thememanager com.google.android.videos com.miui.gallery com.miui.notes ru.iiec.pydroid3 com.google.android.apps.nbu.files com.miui.calculator com.miui.weather2 com.miui.mediaeditor com.miui.screenrecorder com.xiaomi.scanner com.miui.compass com.android.soundrecorder com.mi.global.shop com.duokan.phone.remotecontroller com.xiaomi.glgm com.mi.globalminusscreen com.facebook.appmanager com.facebook.services com.xiaomi.discover com.android.camera com.google.android.apps.wellbeing com.android.vending com.rhmsoft.edit""".split())
P2=set("com.miui.home com.google.android.gms com.google.android.gms.unstable com.google.android.gms.learning com.google.android.gms.ui com.google.android.tts com.google.android.webview com.google.android.as com.google.android.ext.services".split())
P3=set("java com.termux system system_server surfaceflinger init ueventd lmkd logd servicemanager hwservicemanager vndservicemanager vold zygote zygote64 usap32 usap64 audioserver cameraserver camerahalserver mediaserver hostapd dnsmasq netd wificond adbd com.android.systemui com.android.phone com.android.networkstack.process com.android.networkstack com.android.settings com.android.nfc com.android.contacts com.android.mms com.google.android.apps.messaging com.whatsapp com.turktelekom.bimcell.androidapp io.heckel.ntfy com.google.android.permissioncontroller com.google.android.verifier com.google.android.safetycore com.miui.securitycenter com.miui.guardprovider com.miui.powerkeeper com.miui.face com.miui.global.packageinstaller com.google.android.providers.media.module".split())
GOOGLE="com.google.android.googlequicksearchbox"
ROW=re.compile(r"^\s*([\d,]+)K: (.+?) \(pid (\d+)(?:[^)]*)\)\s*(?:\(user (\d+)\))?\s*\(\s*([\d,]+)K in swap\)\s*$")
ROW_NO_SWAP=re.compile(r"^\s*([\d,]+)K: (.+?) \(pid (\d+)(?:[^)]*)\)\s*(?:\(user (\d+)\))?\s*$")
def policy(name,allow):
    base=name.split(":")[0]
    if base==GOOGLE: return "P0","BLOCKED_SYSTEM_PACKAGE","HyperOS denied disable; force-stop did not persist; no bypass"
    if base in allow: return "P0","USER0_ISOLATION_ONLY","Verified third-party allowlist; other users preserved"
    if base in P1:return "P1","ANDROID_MANAGED","Retain enabled; cold pages OS-managed, no forced swap"
    if base in P2 or base.startswith("com.google.android.gms."):return "P2","ANDROID_MANAGED","Preserve service; cold pages OS-managed"
    if base in P3 or base.startswith(("android.hardware.","vendor.","com.android.networkstack","com.android.providers.","com.android.server.","com.android.telephony","com.android.bluetooth")):
        return "P3","PROTECT","Critical, active communication, or server service; do not mutate"
    return "P3","REVIEW_HOLD","Unreviewed; conservatively preserve, not proven critical"
def parse(dump):
    lines=dump.splitlines()
    a=lines.index("Total PSS by process:");b=lines.index("Total PSS by OOM adjustment:")
    if b<=a:raise ValueError("invalid sections")
    rows=[]
    for line in lines[a+1:b]:
        if not line.strip():continue
        m=ROW.match(line); no_swap=False
        if not m:
            m=ROW_NO_SWAP.match(line);no_swap=True
        if not m:raise ValueError("unparsed process row: "+line)
        g=m.groups(); rows.append((g[1],int(g[2]),int(g[3] or 0),int(g[0].replace(",","")),None if no_swap else int(g[4].replace(",",""))))
    if not rows or len({r[1] for r in rows})!=len(rows):raise ValueError("empty or duplicate PID")
    return rows
def package_base(name,installed):
    base=name.split(":")[0]
    if base in installed:return base
    # These are observed Play Services auxiliary process names.
    if base.startswith("com.google.android.gms.") and "com.google.android.gms" in installed:return "com.google.android.gms"
    return None
def main():
    p=argparse.ArgumentParser()
    for n in ("dump","packages","mode","disabled","out"):p.add_argument("--"+n,required=True,type=Path)
    a=p.parse_args()
    read=lambda x:x.read_text(encoding="utf-8-sig")
    allow=set(re.search(r"(?m)^allow='([^']+)'",read(a.mode)).group(1).split())
    installed={x.strip()[8:] for x in read(a.packages).splitlines() if x.startswith("package:")}
    disabled={x.strip()[8:] for x in read(a.disabled).splitlines() if x.startswith("package:")}
    rows=parse(read(a.dump)); result=[]; sums=defaultdict(list)
    for name,pid,user,pss,swap in rows:
        cat,action,reason=policy(name,allow)
        pkg=package_base(name,installed)
        if cat=="P0" and user!=0:action="OTHER_PROFILE_PRESERVED"
        if pkg and user==0:sums[pkg].append((pss,swap))
        result.append(dict(process=name,pid=pid,android_user=user,package_user0_match=pkg or "",policy=cat,action=action,pss_kib=pss,swap_pss_kib=swap,reason=reason))
    def write(path,rr):
        with path.open("w",newline="",encoding="utf-8-sig") as f:
            w=csv.DictWriter(f,fieldnames=list(rr[0]));w.writeheader();w.writerows(rr)
    write(a.out.with_name(a.out.name+"-processes.csv"),result)
    packages=[]
    for pkg in sorted(installed):
        cat,action,reason=policy(pkg,allow)
        mm=sums[pkg]
        packages.append(dict(package=pkg,policy=cat,action=action,disabled_user0=pkg in disabled,measured_processes=len(mm),pss_kib=sum(x[0] for x in mm),swap_pss_kib=sum(x[1] for x in mm) if mm and all(x[1] is not None for x in mm) else "",measurement="measured" if mm else "no_matching_process",reason=reason))
    write(a.out.with_name(a.out.name+"-packages.csv"),packages)
    summary={"process_rows":len(rows),"installed_user0":len(packages),"process_policies":dict(Counter(x["policy"] for x in result)),"package_policies":dict(Counter(x["policy"] for x in packages)),"swap_reported_rows":sum(x[4] is not None for x in rows),"top_processes":sorted(result,key=lambda x:x["pss_kib"],reverse=True)[:12],"selected_packages":[x for x in packages if x["package"] in P1|P2|{GOOGLE,"com.termux","com.instagram.android","com.openai.chatgpt"}]}
    print(json.dumps(summary,ensure_ascii=True))
if __name__=="__main__":main()
