#!/data/data/com.termux/files/usr/bin/bash
set -u
cd "$HOME/kapisma-ultra-test-20261126-r19" || exit 2
out=uptime-preflight.log
[[ ! -e "$out" ]] || exit 2
exec > "$out" 2>&1
finish(){ rc=$?; trap - EXIT; echo "exit=$rc"; adb -s 127.0.0.1:44247 push "$out" /data/local/tmp/kapisma-ultra-test-20261126-r19/ >/dev/null 2>&1 || true; exit "$rc"; }
trap finish EXIT
cat /proc/uptime && echo direct_read=ok || echo direct_read=failed
a=$(bash android_uptime_ms.sh 127.0.0.1:44247) || exit 3
sleep 1
b=$(bash android_uptime_ms.sh 127.0.0.1:44247) || exit 3
[[ "$a" =~ ^[0-9]+$ && "$b" =~ ^[0-9]+$ ]] && ((b>a)) || exit 4
printf 'CLOCK_OK first=%s second=%s delta_ms=%s\n' "$a" "$b" "$((b-a))"
command -v jcmd || true
command -v pgrep || true
