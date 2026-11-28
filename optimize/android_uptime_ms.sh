#!/usr/bin/env bash
# Android may deny app UIDs access to /proc/uptime. Read via authorized local ADB if needed.
set -euo pipefail
[[ $# == 1 && "$1" =~ ^127\.0\.0\.1:[0-9]{1,5}$ ]] || exit 2
raw=$(cat /proc/uptime 2>/dev/null) || raw=$(timeout 3 adb -s "$1" shell cat /proc/uptime)
printf '%s\n' "$raw" | LC_ALL=C awk 'NR==1 && $1 ~ /^[0-9]+\.[0-9]+$/ {v=$1; valid=1} END{if(NR!=1||!valid)exit 2;printf "%.0f\n",v*1000}'
