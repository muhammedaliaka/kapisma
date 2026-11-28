"""Classify observed PSS processes from Android dumpsys meminfo. No device mutations."""
import csv
import re
import sys
from pathlib import Path

RULES = {
    "system": ("KORU", "Android framework"),
    "com.android.systemui": ("KORU", "system interface"),
    "com.android.launcher3": ("KORU", "home screen"),
    "com.android.settings": ("KORU", "settings"),
    "com.google.android.inputmethod.latin": ("KORU", "keyboard"),
    "com.google.android.permissioncontroller": ("KORU", "permissions"),
    "com.android.networkstack.process": ("KORU", "network stack"),
    "com.android.phone": ("KOSULLU_KORU", "telephony and modem integration"),
    "com.spreadtrum.ims": ("KOSULLU_KORU", "IMS calls"),
    "com.android.smspush": ("KOSULLU_KORU", "SMS push"),
    "com.google.android.cellbroadcastreceiver": ("KORU", "emergency broadcasts"),
    "com.google.android.apps.safetyhub": ("KORU", "safety services"),
    "com.android.se": ("KORU", "secure element"),
    "com.google.android.gms": ("KOSULLU_KORU", "Google APIs/auth/push; do not disable wholesale"),
    "com.google.android.gms.ui": ("KOSULLU_KORU", "Google services UI; cached may reclaim"),
    "com.android.vending": ("KOSULLU_KORU", "Play Store and updates"),
    "com.android.vending:background": ("KOSULLU_KORU", "Play Store background updates"),
    "com.android.settings.intelligence": ("KOSULLU_KORU", "settings search/intelligence"),
    "com.google.android.vending": ("KOSULLU_KORU", "Play Store and updates"),
    "com.google.android.vending:background": ("KOSULLU_KORU", "Play Store background updates"),
    "com.google.process.gservices": ("KOSULLU_KORU", "Google services framework"),
    "com.google.android.providers.media.module": ("KORU", "media storage provider"),
    "android.process.media": ("KORU", "media provider"),
    "com.android.bluetooth": ("KOSULLU_KORU", "Bluetooth; unused feature may be switched off normally"),
    "com.google.android.ext.services": ("KORU", "Android extension services"),
    "com.android.shell": ("KORU", "ADB diagnostics"),
    "com.termux": ("KORU", "server runtime"),
    "com.cghs.stresstest": ("ADAY_INCELE", "factory stress test, persistent system app; server does not need it; vendor dependencies unverified"),
    "com.zediel.update": ("KOSULLU_KORU", "persistent FOTA updater; security updates; do not disable"),
    "com.allfiles.viewerer": ("SUNUCU_ICIN_GEREKSIZ", "third-party file viewer; only if not using"),
    "com.document.pdf.word.excel.ppt.editor.office.reader.viewer": ("SUNUCU_ICIN_GEREKSIZ", "third-party office viewer; only if not using"),
    "com.android.chrome": ("ISTEGE_BAGLI", "browser; usually cached"),
    "com.google.android.apps.photos": ("ISTEGE_BAGLI", "photos and sync"),
    "com.google.android.apps.messaging": ("ISTEGE_BAGLI", "SMS app if Wi-Fi-only; not emergency broadcasts"),
    "com.google.android.apps.messaging:rcs": ("ISTEGE_BAGLI", "RCS messaging"),
    "com.google.android.googlequicksearchbox:googleapp": ("ISTEGE_BAGLI", "Google search/assistant"),
    "com.google.android.googlequicksearchbox:search": ("ISTEGE_BAGLI", "Google search"),
    "com.google.android.googlequicksearchbox:interactor": ("ISTEGE_BAGLI", "Google voice interaction"),
    "com.google.android.tts": ("ISTEGE_BAGLI", "text to speech; preserve if accessibility used"),
    "com.google.android.marvin.talkback": ("KOSULLU_KORU", "accessibility; do not disable without verifying user needs"),
    "com.google.android.settings.intelligence": ("KOSULLU_KORU", "settings search/intelligence"),
    "com.google.android.videos": ("ISTEGE_BAGLI", "video playback/store"),
    "com.google.android.projection.gearhead:projection": ("ISTEGE_BAGLI", "Android Auto projection"),
    "com.google.android.apps.restore": ("KOSULLU_KORU", "backup/restore"),
    "com.google.android.apps.wellbeing": ("KORU", "digital wellbeing / parental limits; preserve"),
    "com.google.android.healthconnect.controller": ("ISTEGE_BAGLI", "health data integration; preserve data and permissions"),
    "com.google.android.adservices.api": ("ISTEGE_BAGLI", "privacy sandbox advertising APIs; no server need, system dependencies unverified"),
    "com.google.android.as": ("KOSULLU_KORU", "Android System Intelligence"),
    "com.google.android.as.oss": ("KOSULLU_KORU", "system intelligence companion"),
}
CRITICAL = {"artd", "drmserver", "traced", "traced_probes", "dumpsys", "tee_rpc", "init", "zygote", "zygote64", "webview_zygote", "surfaceflinger", "adbd", "netd", "wpa_supplicant", "wificond", "lmkd", "thermald", "charged", "vold", "installd", "servicemanager", "hwservicemanager", "ueventd", "keystore2", "gatekeeperd", "credstore", "prng_seeder", "logd", "statsd", "storaged", "gpuservice", "audioserver", "mediaserver", "cameraserver", "update_engine", "tombstoned", "incidentd", "iptables-restore", "ip6tables-restore"}
def classify(name):
    if name in RULES: return RULES[name]
    if name in CRITICAL or name.startswith(("android.hardware.", "android.system.", "android.hidl.", "media.", "vendor.sprd.hardware.", "vendor.unisoc.hardware.")):
        return "KORU", "system/native or hardware HAL; dependencies not individually proven"
    return "BELIRSIZ_KORU", "vendor/native role needs device-specific verification; no removal recommendation"
def extract(text):
    segment = text.split("Total PSS by process:",1)[1].split("Total PSS by OOM adjustment:",1)[0]
    pattern = re.compile(r"^\s*([\d,]+)K:\s+(.+?)\s+\(pid (\d+)(?:[^)]*)\)\s*$",re.M)
    for m in pattern.finditer(segment):
        k,name,pid=m.groups(); status,reason=classify(name)
        yield (pid,name,int(k.replace(",","")),round(int(k.replace(",",""))/1024,2),status,reason)
if __name__=="__main__":
    source=Path(sys.argv[1]); target=Path(sys.argv[2])
    rows=list(extract(source.read_text(encoding="utf-8-sig")))
    if not rows: raise SystemExit("no process data")
    with target.open("x",encoding="utf-8-sig",newline="") as f:
        w=csv.writer(f);w.writerow(["pid","process","pss_kib","pss_mib","classification","reason"]);w.writerows(rows)
    from collections import defaultdict
    totals=defaultdict(lambda:[0,0])
    for row in rows:
        totals[row[4]][0]+=1;totals[row[4]][1]+=row[2]
    print("processes",len(rows))
    for k,(n,v) in sorted(totals.items()): print(k,n,round(v/1024,2))
