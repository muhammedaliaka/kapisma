"""Offline common-profile compiler. Never contacts a device."""
from pathlib import Path
import re,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
OUT=Path(__file__).resolve().parent/"generated"
PROFILES={
 "phone":{"serial":"HM59FYLJEY59M7O7","model":"23090RA98I","state":"/data/local/tmp/kapisma-phone-mode-v1","legacy":"android_phone_server_mode.sh","target_mib":6656},
 "tablet":{"serial":"SCTB19202412160567","model":None,"state":"/data/local/tmp/kapisma-server-mode-v1","legacy":"android_server_mode.sh","target_mib":None}
}
def read(n):return (ROOT/n).read_text(encoding="utf-8-sig")
def allow(s):return set(re.search(r"(?m)^allow='([^']+)'",s).group(1).split())
def replace(s,a,b):
 if s.count(a)!=1:raise ValueError("unexpected template "+a)
 return s.replace(a,b)
def build():
 core=read("android_phone_server_mode.sh")
 p1=read("android_phone_p1_cleanup.sh")
 daily=read("android_phone_daily_cleanup.sh")
 launcher=read("ultra_phone_termux.sh")
 # Additional optional third-party packages, no communications or carrier apps.
 extra=set("ru.iiec.pydroid3 com.valvesoftware.android.steam.community ai.meshy.app com.gamma.scan com.vjsjlqvlmp.wearewarriors com.mi.global.bbs com.mi.global.shop".split())
 # Shared policy uses the union of reviewed app candidates. Actual mutation still
 # requires installed third-party unique UID, unmanaged device and enabled state.
 approved=allow(core)|allow(read("android_server_mode.sh"))|extra
 assert "com.google.android.googlequicksearchbox" not in approved
 for name,p in PROFILES.items():
  dest=OUT/name;dest.mkdir(parents=True,exist_ok=True)
  guard='[ "$(getprop ro.serialno)" = "'+p["serial"]+'" ] || { echo "unexpected device serial" >&2; exit 2; }'
  if p["model"]:guard+='\n[ "$(getprop ro.product.model)" = "'+p["model"]+'" ] || exit 2'
  # ro.serialno must match USB serial; on tablet this is an unverified fail-closed precondition.
  s=replace(core,"set -eu","set -eu\n"+guard)
  s=replace(s,"state=/data/local/tmp/kapisma-phone-mode-v1","state="+p["state"])
  s=re.sub(r"(?m)^allow='[^']+'","allow='"+' '.join(sorted(approved))+"'",s,count=1)
  # Refuse to mutate if active Java; keep restoration and legacy journal behavior.
  s=replace(s,'[ ! -e "$state" ] ||', '[ "$mode" != --enter ] || ! ps -A -o NAME | grep -Fxq java || exit 3\n[ ! -e "$state" ] ||')
  # Protect accessibility/IME and check postconditions for the exact user.
  s=replace(s,'before=$(mem)','ime=$(settings get secure default_input_method)\naccess=$(settings get secure enabled_accessibility_services)\nbefore=$(mem)')
  s=replace(s,'for pkg in $allow; do','for pkg in $allow; do\n    case "$ime $access" in *"$pkg/"*) echo "skip active input/accessibility $pkg"; continue;; esac')
  s=replace(s,'cur=$(enabled_state "$pkg")','cur=$(enabled_state "$pkg") || { err=1; continue; }')
  # Add a distinct mutation lock; fail closed after hard interruption.
  s=replace(s,'case "$mode" in\n--status)', '''case "$mode" in
--enter|--leave)
    lock="$state.mutation-lock"
    mkdir "$lock" || { echo 'mutation lock occupied; inspect manually' >&2; exit 3; }
    trap 'rmdir "$lock" 2>/dev/null || true' EXIT
    ;;
esac
case "$mode" in
--status)''')
  s=replace(s,'''trap 'rc=$?; if [ "$rc" -ne 0 ]; then restore || true; fi' EXIT''','''trap 'rc=$?; if [ "$rc" -ne 0 ]; then restore || true; fi; rmdir "$lock" 2>/dev/null || true' EXIT''')
  q=replace(p1,"set -eu","set -eu\n"+guard)
  q=replace(q,"/data/local/tmp/kapisma-phone-mode-v1/journal",p["state"]+"/journal")
  q=replace(q,'case "${1:---plan}" in --plan|--apply) mode="$1";;', 'case "${1:---plan}" in --plan|--apply) mode="${1:---plan}";;')
  # No global kill-all; protect user-specific UID checks and input/accessibility.
  q=replace(q,'before=$(mem)','''lock="'''+p["state"]+'''.mutation-lock"
if [ "$mode" = --apply ]; then
    mkdir "$lock" || exit 3
    trap 'rmdir "$lock" 2>/dev/null || true' EXIT
    [ -f "'''+p["state"]+'''/journal" ] || exit 3
fi
ime=$(settings get secure default_input_method)
access=$(settings get secure enabled_accessibility_services)
before=$(mem)''')
  q=replace(q,'    if ps -A -o NAME | awk -v p="$pkg" \'$1==p || index($1,p":")==1 {f=1} END{exit !f}\'; then','''    case "$ime $access" in *"$pkg/"*) continue;; esac
    uids=$(pm list packages -U --user 0)
    uid=$(printf '%s\\n' "$uids" | awk -v p="package:$pkg" '$1==p {sub(/^uid:/,"",$2);print $2;exit}')
    case "$uid" in ''|*[!0-9]*) continue;; esac
    [ "$(printf '%s\\n' "$uids" | awk -v u="uid:$uid" '$2==u {n++} END{print n+0}')" = 1 ] || continue
    if ps -A -o UID,NAME | awk -v u="$uid" '$1==u {f=1} END{exit !f}'; then''')
  l=replace(launcher,'[[ "$(timeout 8 adb -s "$serial" shell getprop ro.product.model | tr -d \'\\r\')" == 23090RA98I ]] || fail \'unexpected ADB device model\'','[[ "$(timeout 8 adb -s "$serial" shell getprop ro.serialno | tr -d \'\\r\')" == '+p["serial"]+' ]] || fail \'unexpected ADB device serial\'')
  l=l.replace("android_phone_server_mode.sh","server_mode.sh").replace("android_phone_p1_cleanup.sh","p1_cleanup.sh")
  # Do not claim greater heap than previously validated policy.
  daily_src=replace(daily,"set -eu","set -eu\n"+guard)
  daily_src=replace(daily_src,"/data/local/tmp/kapisma-phone-mode-v1",p["state"])
  daily_src=replace(daily_src,'case "${1:---plan}" in --plan|--apply) mode="$1";;', 'case "${1:---plan}" in --plan|--apply) mode="${1:---plan}";;')
  daily_src=replace(daily_src,'before=$(mem)','''lock="'''+p["state"]+'''.mutation-lock"
if [ "$mode" = --apply ]; then
    mkdir "$lock" || exit 3
    trap 'rmdir "$lock" 2>/dev/null || true' EXIT
    [ ! -e "'''+p["state"]+'''" ] || exit 3
fi
before=$(mem)''')
  daily_src=replace(daily_src,'for pkg in $packages; do\n  running "$pkg" || continue','''ime=$(settings get secure default_input_method)
access=$(settings get secure enabled_accessibility_services)
uids=$(pm list packages -U --user 0)
for pkg in $packages; do
  case "$ime $access" in *"$pkg/"*) continue;; esac
  uid=$(printf '%s\\n' "$uids" | awk -v p="package:$pkg" '$1==p {print $2;exit}')
  case "$uid" in uid:[0-9]*) ;; *) continue;; esac
  [ "$(printf '%s\\n' "$uids" | awk -v u="$uid" '$2==u {n++} END{print n+0}')" = 1 ] || continue
  running "$pkg" || continue''')
  files={"server_mode.sh":s,"p1_cleanup.sh":q,"daily.sh":daily_src,"launch.sh":l}
  for fn,src in files.items():(dest/fn).write_bytes((src.rstrip()+"\n").encode())
  manifest={**p,"profile":name,"physical_validation":"pending","sha256":{fn:hashlib.sha256((dest/fn).read_bytes()).hexdigest() for fn in files}}
  (dest/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
  print(name,len(approved),"packages",manifest["sha256"])
if __name__=="__main__":build()
