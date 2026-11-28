#!/usr/bin/env bash
# Best-effort bounded diagnostic of this harness's own JVM only. No signals or JFR.
set -euo pipefail
[[ $# == 3 && "$1" =~ ^[1-9][0-9]*$ && "$2" =~ ^[a-z_]+$ && "$3" =~ ^[12]$ ]] || exit 2
parent="$1"; phase="$2"; serial="$3"
[[ "${PWD##*/}" =~ ^kapisma-ultra-test-[0-9]{8}-r[0-9]+$ ]] || exit 2
[[ -f fabric-server-launch.jar && -p cmds && ! -e "thread-probe-$serial.txt" ]] || exit 2
command -v jcmd >/dev/null || { echo 'jcmd unavailable'; exit 3; }
mapfile -t candidates < <(pgrep -P "$parent" -x java || true)
[[ "${#candidates[@]}" == 1 ]] || { echo 'unique Java child not found'; exit 3; }
target="${candidates[0]}"
[[ "$(readlink -f "/proc/$target/cwd")" == "$(pwd -P)" ]] || exit 3
tr '\0' '\n' < "/proc/$target/cmdline" | grep -Fxq 'fabric-server-launch.jar' || exit 3
out="thread-probe-$serial.txt"
printf 'phase=%s timestamp=%s target_pid=%s\n' "$phase" "$(date +%s)" "$target" > "$out"
rc=0
# Limit output and duration. A failed attach is evidence, not a reason to retry repeatedly.
(ulimit -f 1024; timeout 4 jcmd "$target" Thread.print -l) >> "$out" 2>&1 || rc=$?
printf 'probe_exit=%s\n' "$rc" >> "$out"
exit "$rc"
