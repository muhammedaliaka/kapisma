#!/system/bin/sh
# Non-destructive daily mode. Only Android's background-process kill API.
set -eu
[ "$(getprop ro.serialno)" = "SCTB19202412160567" ] || { echo "unexpected device serial" >&2; exit 2; }
[ "$(id -u)" = 2000 ] && [ "$(am get-current-user)" = 0 ] || exit 2
case "${1:---plan}" in --plan|--apply) mode="${1:---plan}";; *) exit 2;; esac
[ ! -e /data/local/tmp/kapisma-server-mode-v1 ] || { echo 'Server mode active: daily cleanup blocked'; exit 3; }
mem(){ awk '/^MemAvailable:/ {print $2;exit}' /proc/meminfo; }
packages='com.instagram.android com.facebook.katana com.twitter.android com.linkedin.android com.openai.chatgpt com.abtnprojects.ambatana cn.wps.moffice_eng cn.wps.xiaomi.abroad.lite com.microsoft.office.outlook com.microsoft.teams com.anthropic.claude com.android.chrome com.google.android.googlequicksearchbox com.google.android.youtube com.google.android.apps.youtube.music com.google.android.apps.photos com.netflix.mediaclient com.github.android com.busuu.android.enc com.spotify.music com.suno.android com.opera.browser com.sahibinden com.amazon.mShop.android.shopping'
running(){ ps -A -o NAME | awk -v p="$1" '$1==p || index($1,p":")==1 {found=1} END {exit !found}'; }
lock="/data/local/tmp/kapisma-server-mode-v1.mutation-lock"
if [ "$mode" = --apply ]; then
    mkdir "$lock" || exit 3
    trap 'rmdir "$lock" 2>/dev/null || true' EXIT
    [ ! -e "/data/local/tmp/kapisma-server-mode-v1" ] || exit 3
fi
before=$(mem)
attempted=0; errors=0; still=0
ime=$(settings get secure default_input_method)
access=$(settings get secure enabled_accessibility_services)
uids=$(pm list packages -U --user 0)
for pkg in $packages; do
  case "$ime $access" in *"$pkg/"*) continue;; esac
  uid=$(printf '%s\n' "$uids" | awk -v p="package:$pkg" '$1==p {print $2;exit}')
  case "$uid" in uid:[0-9]*) ;; *) continue;; esac
  [ "$(printf '%s\n' "$uids" | awk -v u="$uid" '$2==u {n++} END{print n+0}')" = 1 ] || continue
  running "$pkg" || continue
  echo "$mode $pkg"
  if [ "$mode" = --apply ]; then
    attempted=$((attempted+1))
    am kill --user 0 "$pkg" || errors=$((errors+1))
  fi
done
if [ "$mode" = --apply ]; then
  sleep 3
  for pkg in $packages; do
    if running "$pkg"; then echo "remaining $pkg"; still=$((still+1)); fi
  done
fi
after=$(mem)
echo "mode=$mode before_kib=$before after_kib=$after delta_kib=$((after-before)) attempted=$attempted remaining=$still failures=$errors"
[ "$errors" = 0 ]
