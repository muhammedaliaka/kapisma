#!/system/bin/sh
# Explicit temporary dedicated-server mode. Android shell, not root.
# No uninstall, data deletion, device policy, system-app or permission changes.
set -eu
[ "$(getprop ro.serialno)" = "SCTB19202412160567" ] || { echo "unexpected device serial" >&2; exit 2; }
[ "$(id -u)" = 2000 ] && [ "$(am get-current-user)" = 0 ] || { echo 'shell user 0 required' >&2; exit 2; }
mode="${1:---status}"
state=/data/local/tmp/kapisma-server-mode-v1
allow='ai.leonardo.leonardo ai.meshy.app ai.perplexity.app.android all.documentreader.filereader.office.viewer cn.wps.moffice_eng cn.wps.xiaomi.abroad.lite com.abtnprojects.ambatana com.allfiles.viewerer com.amazon.avod.thirdpartyclient com.amazon.mShop.android.shopping com.anthropic.claude com.bigwinepot.nwdn.international com.busuu.android.enc com.canva.editor com.codespaceapps.aichat com.dephotos.crello com.document.pdf.word.excel.ppt.editor.office.reader.viewer com.facebook.katana com.figma.mirror com.gamma.scan com.github.android com.google.android.apps.bard com.google.android.apps.docs.editors.docs com.google.android.apps.docs.editors.sheets com.hubx.imagination com.inforcegames.app3dmodelling com.instagram.android com.linkedin.android com.mi.global.bbs com.mi.global.shop com.microsoft.bing com.microsoft.copilot com.microsoft.office.outlook com.microsoft.teams com.moonshot.kimichat com.netflix.mediaclient com.openai.chatgpt com.opera.browser com.overwolf.statsroyale com.sahibinden com.spotify.music com.suno.android com.supercell.clashroyale com.trendyol.go com.trt.tabii.android com.twitter.android com.valvesoftware.android.steam.community com.vjsjlqvlmp.wearewarriors com.wbd.stream com.womboai.wombodream kling.ai.video.chat pdf.pdfreader.viewer.editor.free ru.iiec.pydroid3'
mem(){ awk '/^MemAvailable:/ {print $2; exit}' /proc/meminfo; }
# Legacy recovery only: a previous denied disable attempt journaled Google Search.
# Never include it in the live disable allowlist.
allowed(){ case " $allow com.google.android.googlequicksearchbox " in *" $1 "*) return 0;; *) return 1;; esac; }
enabled_state(){
    dumpsys package "$1" | awk '/^[[:space:]]*User 0:/ {for(i=1;i<=NF;i++) if($i~/^enabled=[0-9]+$/ && !found){sub(/^enabled=/,"",$i); value=$i; found=1}} END {if(found) print value; else exit 1}'
}
restore(){
    [ -d "$state" ] || { echo 'server_mode=inactive'; return 0; }
    [ -f "$state/journal" ] || { echo 'missing journal; inspect state' >&2; return 3; }
    err=0
    while read -r pkg old extra; do
        if [ -n "${extra:-}" ] || ! allowed "$pkg"; then err=1; continue; fi
        case "$old" in 0|1) ;; *) err=1; continue;; esac
        cur=$(enabled_state "$pkg") || { err=1; continue; }
        if [ "$cur" = "$old" ]; then continue; fi
        if [ "$cur" != 3 ]; then echo "unexpected state $pkg $cur" >&2; err=1; continue; fi
        if [ "$old" = 0 ]; then pm default-state --user 0 "$pkg" || err=1
        else pm enable --user 0 "$pkg" || err=1; fi
        [ "$(enabled_state "$pkg")" = "$old" ] || err=1
    done < "$state/journal"
    if [ "$err" -ne 0 ]; then echo 'restore incomplete; journal retained' >&2; return 3; fi
    rm -f "$state/journal"
    rmdir "$state"
    echo 'server_mode=restored'
}
case "$mode" in
--enter|--leave)
    lock="$state.mutation-lock"
    mkdir "$lock" || { echo 'mutation lock occupied; inspect manually' >&2; exit 3; }
    trap 'rmdir "$lock" 2>/dev/null || true' EXIT
    ;;
esac
case "$mode" in
--status)
    if [ -d "$state" ]; then echo 'server_mode=active_or_recovery_required'; cat "$state/journal"; else echo 'server_mode=inactive'; fi
    echo "available_kib=$(mem)"; exit 0;;
--leave)
    if ps -A -o NAME | grep -Fxq java; then echo "Java still running; restore refused" >&2; exit 3; fi
    restore; exit $?;;
--plan|--enter) ;;
*) exit 2;;
esac
[ "$mode" != --enter ] || ! ps -A -o NAME | grep -Fxq java || exit 3
[ ! -e "$state" ] || { echo 'previous session needs recovery; do not overwrite' >&2; exit 3; }
# Never touch an active device-admin package, a shared UID, or a pre-disabled app.
third=$(pm list packages -3 -e --user 0)
uids=$(pm list packages -U --user 0)
policy=$(dumpsys device_policy)
owners=$(dpm list-owners)
[ -n "$third" ] && [ -n "$uids" ] && [ -n "$policy" ] || exit 3
[ "$owners" = 'no owners' ] || { echo 'managed device; dedicated mode refused' >&2; exit 3; }
admins=$(printf '%s\n' "$policy" | awk '/Enabled Device Admins \(User 0,/ {f=1; found=1; next} f && /mPasswordOwner=/ {f=0; end=1} f && NF {print} END {if(!found || !end) exit 1}') || exit 3
[ -z "$admins" ] || { echo 'active device admins; dedicated mode refused' >&2; exit 3; }
protected=$(printf '%s\n' "$policy" | awk '/PersonalAppsSuspensionHelper/ {f=1;next} /Packages subject to suspension:/ {f=0} f {print}')
if [ "$mode" = --enter ]; then
    dumpsys activity services com.termux | grep 'isForeground=true' >/dev/null || { echo 'Termux foreground service required' >&2; exit 3; }
    umask 077
    mkdir "$state" || exit 3
    : > "$state/journal"
    # Roll back on all ordinary shell failures. A persistent journal survives a crash.
    trap 'rc=$?; if [ "$rc" -ne 0 ]; then restore || true; fi; rmdir "$lock" 2>/dev/null || true' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM HUP
fi
ime=$(settings get secure default_input_method)
access=$(settings get secure enabled_accessibility_services)
before=$(mem)
n=0
for pkg in $allow; do
    case "$ime $access" in *"$pkg/"*) echo "skip active input/accessibility $pkg"; continue;; esac
    printf '%s\n' "$third" | grep -Fxq "package:$pkg" || continue
    if printf '%s\n' "$protected" | grep -Fq "$pkg"; then echo "skip policy $pkg"; continue; fi
    uid=$(printf '%s\n' "$uids" | awk -v p="package:$pkg" '$1==p {print $2; exit}')
    case "$uid" in uid:[0-9]*) ;; *) echo "skip unknown uid $pkg"; continue;; esac
    count=$(printf '%s\n' "$uids" | awk -v u="$uid" '$2==u {n++} END{print n+0}')
    [ "$count" = 1 ] || { echo "skip shared uid $pkg"; continue; }
    old=$(enabled_state "$pkg")
    case "$old" in 0|1) ;; *) echo "skip state $pkg $old"; continue;; esac
    echo "$mode $pkg original=$old"
    if [ "$mode" = --enter ]; then
        printf '%s %s\n' "$pkg" "$old" >> "$state/journal"
        pm disable-user --user 0 "$pkg"
        [ "$(enabled_state "$pkg")" = 3 ] || exit 4
    fi
    n=$((n+1))
done
if [ "$mode" = --enter ]; then
    # HyperOS rejects disable-user for Google Search; keep it enabled.
    am kill --user 0 com.google.android.googlequicksearchbox || true
    sleep 2
    running=$(ps -A -o UID,NAME)
    while read -r pkg old; do
        uid=$(printf '%s\n' "$uids" | awk -v p="package:$pkg" '$1==p {sub(/^uid:/,"",$2);print $2;exit}')
        case "$uid" in ''|*[!0-9]*) exit 4;; esac
        if printf '%s\n' "$running" | awk -v u="$uid" '$1==u {f=1} END{exit !f}'; then
            echo "user 0 process still active: $pkg" >&2; exit 4
        fi
        if printf '%s\n' "$running" | awk -v p="$pkg" -v u="$uid" '$1!=u && ($2==p || index($2,p":")==1) {f=1} END{exit !f}'; then
            echo "other-profile process preserved: $pkg"
        fi
    done < "$state/journal"
    dumpsys activity services com.termux | grep 'isForeground=true' >/dev/null || exit 4
fi
after=$(mem)
echo "mode=$mode packages=$n before_kib=$before after_kib=$after delta_kib=$((after-before))"
