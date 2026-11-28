#!/usr/bin/env bash
# Read-only thermal gate. 0=below severe, 6=severe or unavailable.
# No thermal settings are changed; the caller owns graceful shutdown.
set -euo pipefail
thermal_status() {
  LC_ALL=C awk '
    /^[[:space:]]*Thermal Status:[[:space:]]*/ {
      n++; sub(/^[[:space:]]*Thermal Status:[[:space:]]*/, "");
      sub(/[[:space:]]*$/, ""); v=$0
    }
    END { if(n==1 && v ~ /^[0-6]$/) print v; else exit 1 }
  '
}
mode="${1:-}"
case "$mode" in
  --stdin) [[ $# == 1 ]] || exit 6
    status=$(thermal_status) || { echo "thermal_status=UNKNOWN"; exit 6; };;
  --device) [[ $# == 2 && "$2" =~ ^127\.0\.0\.1:[0-9]{1,5}$ ]] || exit 6
    raw=$(timeout 8 adb -s "$2" shell dumpsys thermalservice) || { echo "thermal_status=UNKNOWN"; exit 6; }
    status=$(printf '%s\n' "$raw" | thermal_status) || { echo "thermal_status=UNKNOWN"; exit 6; };;
  *) exit 6;;
esac
printf 'thermal_status=%s\n' "$status"
(( status < 3 )) || exit 6
