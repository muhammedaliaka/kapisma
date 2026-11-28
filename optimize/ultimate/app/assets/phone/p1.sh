#!/system/bin/sh
# Only reclaim optional background apps; never disable system packages or force swap.
set -eu
[ "$(getprop ro.serialno)" = "HM59FYLJEY59M7O7" ] || { echo "unexpected device serial" >&2; exit 2; }
[ "$(getprop ro.product.model)" = "23090RA98I" ] || exit 2
[ "$(id -u)" = 2000 ] && [ "$(am get-current-user)" = 0 ] || exit 2
[ -f /data/local/tmp/kapisma-phone-mode-v1/journal ] || exit 3
case "${1:---plan}" in --plan|--apply) mode="${1:---plan}";; *) exit 2;; esac
mem(){ awk '/^MemAvailable:/ {print $2;exit}' /proc/meminfo; }
lock="/data/local/tmp/kapisma-phone-mode-v1.mutation-lock"
if [ "$mode" = --apply ]; then
    mkdir "$lock" || exit 3
    trap 'rmdir "$lock" 2>/dev/null || true' EXIT
    [ -f "/data/local/tmp/kapisma-phone-mode-v1/journal" ] || exit 3
fi
ime=$(settings get secure default_input_method)
access=$(settings get secure enabled_accessibility_services)
before=$(mem)
for pkg in com.google.android.gm com.google.android.apps.maps com.google.android.apps.docs com.android.chrome com.google.android.apps.chromecast.app com.google.android.apps.photos com.google.android.apps.tachyon com.google.android.youtube com.google.android.apps.youtube.music com.google.android.videos com.miui.gallery com.miui.notes ru.iiec.pydroid3 com.google.android.apps.nbu.files com.miui.calculator com.miui.weather2 com.miui.mediaeditor com.miui.screenrecorder com.xiaomi.scanner com.miui.compass com.android.soundrecorder com.mi.global.shop com.duokan.phone.remotecontroller com.rhmsoft.edit com.mi.appfinder com.xiaomi.mipicks com.xiaomi.glgm com.android.thememanager com.facebook.appmanager com.facebook.services com.microsoft.appmanager; do
    case "$ime $access" in *"$pkg/"*) continue;; esac
    uids=$(pm list packages -U --user 0)
    uid=$(printf '%s\n' "$uids" | awk -v p="package:$pkg" '$1==p {sub(/^uid:/,"",$2);print $2;exit}')
    case "$uid" in ''|*[!0-9]*) continue;; esac
    [ "$(printf '%s\n' "$uids" | awk -v u="uid:$uid" '$2==u {n++} END{print n+0}')" = 1 ] || continue
    if ps -A -o UID,NAME | awk -v u="$uid" '$1==u {f=1} END{exit !f}'; then
        echo "$mode $pkg"
        if [ "$mode" = --apply ]; then am kill --user 0 "$pkg"; fi
    fi
done
[ "$mode" != --apply ] || sleep 3
after=$(mem)
echo "before_kib=$before after_kib=$after delta_kib=$((after-before))"
