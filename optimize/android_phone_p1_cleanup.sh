#!/system/bin/sh
# Only reclaim optional background apps; never disable system packages or force swap.
set -eu
[ "$(id -u)" = 2000 ] && [ "$(am get-current-user)" = 0 ] || exit 2
[ -f /data/local/tmp/kapisma-phone-mode-v1/journal ] || exit 3
case "${1:---plan}" in --plan|--apply) mode="$1";; *) exit 2;; esac
mem(){ awk '/^MemAvailable:/ {print $2;exit}' /proc/meminfo; }
before=$(mem)
for pkg in com.google.android.gm com.google.android.apps.maps com.google.android.apps.docs com.android.chrome com.google.android.apps.chromecast.app com.google.android.apps.photos com.google.android.apps.tachyon com.google.android.youtube com.google.android.apps.youtube.music com.google.android.videos com.miui.gallery com.miui.notes ru.iiec.pydroid3 com.google.android.apps.nbu.files com.miui.calculator com.miui.weather2 com.miui.mediaeditor com.miui.screenrecorder com.xiaomi.scanner com.miui.compass com.android.soundrecorder com.mi.global.shop com.duokan.phone.remotecontroller com.rhmsoft.edit com.mi.appfinder com.xiaomi.mipicks com.xiaomi.glgm com.android.thememanager com.facebook.appmanager com.facebook.services com.microsoft.appmanager; do
    if ps -A -o NAME | awk -v p="$pkg" '$1==p || index($1,p":")==1 {f=1} END{exit !f}'; then
        echo "$mode $pkg"
        if [ "$mode" = --apply ]; then am kill --user 0 "$pkg"; fi
    fi
done
[ "$mode" != --apply ] || sleep 3
after=$(mem)
echo "before_kib=$before after_kib=$after delta_kib=$((after-before))"
