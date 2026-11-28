#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$HOME/jna-android-5.14.0"
lib="$HOME/kapisma-ultra-test-20261126-r4/libraries"
stage=/data/local/tmp/kapisma-jna-proof-20261126
serial=127.0.0.1:44247
exec > oshi-probe.log 2>&1
finish(){
  rc=$?
  trap - EXIT
  printf 'exit=%s\n' "$rc"
  adb -s "$serial" push oshi-probe.log "$stage/oshi-probe.log" >/dev/null 2>&1 || true
  exit "$rc"
}
trap finish EXIT
printf '%s  %s\n' '3730ce014f7a6807ae74af78c3b9f5a3cba1f5886316b3d3858493df923efe96' libjnidispatch.so | sha256sum -c -
cp="$lib/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar:$lib/net/java/dev/jna/jna-platform/5.14.0/jna-platform-5.14.0.jar:$lib/com/github/oshi/oshi-core/6.4.10/oshi-core-6.4.10.jar:$lib/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar"
timeout 30 java -Djna.boot.library.path="$PWD" -Djna.nosys=true -Djna.noclasspath=true -cp "$cp" OshiProbe.java
