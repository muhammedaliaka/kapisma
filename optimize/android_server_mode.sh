#!/system/bin/sh
# Explicit temporary dedicated-server mode. Android shell, not root.
# No uninstall, data deletion, device policy, system-app or permission changes.
set -eu
[ "$(id -u)" = 2000 ] && [ "$(am get-current-user)" = 0 ] || { echo 'shell user 0 required' >&2; exit 2; }
mode="${1:---status}"
state=/data/local/tmp/kapisma-server-mode-v1
allow='com.openai.chatgpt com.abtnprojects.ambatana com.figma.mirror com.canva.editor com.dephotos.crello ai.leonardo.leonardo com.netflix.mediaclient com.supercell.clashroyale com.overwolf.statsroyale com.anthropic.claude com.microsoft.copilot com.microsoft.bing ai.perplexity.app.android com.google.android.apps.bard com.moonshot.kimichat kling.ai.video.chat com.womboai.wombodream com.hubx.imagination com.codespaceapps.aichat com.allfiles.viewerer com.document.pdf.word.excel.ppt.editor.office.reader.viewer pdf.pdfreader.viewer.editor.free all.documentreader.filereader.office.viewer com.inforcegames.app3dmodelling com.bigwinepot.nwdn.international'
mem(){ awk '/^MemAvailable:/ {print $2; exit}' /proc/meminfo; }
allowed(){ case " $allow " in *" $1 "*) return 0;; *) return 1;; esac; }
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
        cur=$(enabled_state "$pkg")
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
--status)
    if [ -d "$state" ]; then echo 'server_mode=active_or_recovery_required'; cat "$state/journal"; else echo 'server_mode=inactive'; fi
    echo "available_kib=$(mem)"; exit 0;;
--leave) restore; exit $?;;
--plan|--enter) ;;
*) exit 2;;
esac
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
    trap 'rc=$?; if [ "$rc" -ne 0 ]; then restore || true; fi' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM HUP
fi
before=$(mem)
n=0
for pkg in $allow; do
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
    sleep 2
    running=$(ps -A -o NAME)
    while read -r pkg old; do
        if printf '%s\n' "$running" | awk -v p="$pkg" '$1==p || index($1,p":")==1 {f=1} END{exit !f}'; then
            echo "process still active: $pkg" >&2; exit 4
        fi
    done < "$state/journal"
    dumpsys activity services com.termux | grep 'isForeground=true' >/dev/null || exit 4
fi
after=$(mem)
echo "mode=$mode packages=$n before_kib=$before after_kib=$after delta_kib=$((after-before))"
