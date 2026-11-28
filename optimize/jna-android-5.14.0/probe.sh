#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$HOME/jna-android-5.14.0"
jar="$HOME/kapisma-ultra-test-20261126-r4/libraries/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"
stage=/data/local/tmp/kapisma-jna-proof-20261126
serial=127.0.0.1:44247
exec > probe.log 2>&1
finish(){
  rc=$?
  trap - EXIT
  printf 'exit=%s\n' "$rc"
  adb -s "$serial" push probe.log "$stage/probe.log" >/dev/null 2>&1 || true
  exit "$rc"
}
trap finish EXIT
printf '%s  %s\n' '3730ce014f7a6807ae74af78c3b9f5a3cba1f5886316b3d3858493df923efe96' libjnidispatch.so | sha256sum -c -
printf '%s  %s\n' '34ed1e1f27fa896bca50dbc4e99cf3732967cec387a7a0d5e3486c09673fe8c6' "$jar" | sha256sum -c -
java -version
timeout 30 java -Djna.boot.library.path="$PWD" -Djna.nosys=true -Djna.noclasspath=true -Djna.debug_load=true -Djna.debug_load.jna=true -cp "$jar" JnaNativeProbe.java
