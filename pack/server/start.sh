#!/usr/bin/env bash
# Kapisma sunucu baslatici: Linux, macOS ve Android tablet (Termux).
# Tek komut:  bash start.sh      (ilk seferde gerekli her seyi kendisi halleder)
#   1) Mojang EULA onayi   2) Java 21 (yoksa kurar/indirir)   3) Ses modu (yoksa indirir)
#   4) RAM'e gore ayar (tablet dahil)   5) Manhunt yeni dunya dongusu
cd "$(dirname "$0")" || exit 1

say() { printf '%s\n' "$*"; }

# ---------- 1) EULA ----------
if ! grep -qi '^eula=true' eula.txt 2>/dev/null; then
  say ""
  say "Minecraft sunucusunu calistirmak icin Mojang EULA metnini kabul etmelisin:"
  say "   https://aka.ms/MinecraftEULA"
  printf 'Kabul ediyor musun? [E/H]: '
  read -r acc
  case "$acc" in
    [Ee]*|[Yy]*) printf 'eula=true\n' > eula.txt ;;   # E / Evet / Y / Yes (satir sonu \r olsa da calisir)
    *) say "Kabul edilmedi. Sunucu acilmayacak."; exit 1 ;;
  esac
fi

# ---------- 2) Java 21 ----------
java_major() {   # $1 = java yolu -> ana surum numarasi (bulunamazsa 0)
  local v
  v=$("$1" -version 2>&1 | grep -m1 ' version "' | sed -E 's/.*version "([0-9]+).*/\1/')
  case "$v" in ''|*[!0-9]*) echo 0 ;; *) echo "$v" ;; esac
}
find_java() {
  JAVA=""
  local c
  for c in "./runtime/bin/java" "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java 2>/dev/null)" "${PREFIX:+$PREFIX/lib/jvm/java-21-openjdk/bin/java}"; do
    [ -n "$c" ] && [ -x "$c" ] || continue
    if [ "$(java_major "$c")" -ge 21 ]; then JAVA="$c"; return 0; fi
  done
  c=$(find ./runtime -type f -name java -path '*/bin/java' 2>/dev/null | head -n 1)
  if [ -n "$c" ] && [ "$(java_major "$c")" -ge 21 ]; then JAVA="$c"; return 0; fi
  return 1
}
get_java() {
  say ""
  say "Java 21 bulunamadi."
  if [ -n "$PREFIX" ] && command -v pkg >/dev/null 2>&1; then          # Termux (Android)
    say "Termux algilandi: openjdk-21 kuruluyor..."
    pkg install -y openjdk-21 curl unzip
    return
  fi
  if command -v apt-get >/dev/null 2>&1 && [ "$(id -u)" = "0" ]; then   # Debian/Ubuntu (root)
    say "apt ile openjdk-21 kuruluyor..."
    apt-get update -y && apt-get install -y openjdk-21-jre-headless && return
  fi
  local os arch url
  case "$(uname -s)" in Darwin) os=mac ;; *) os=linux ;; esac
  case "$(uname -m)" in x86_64|amd64) arch=x64 ;; aarch64|arm64) arch=aarch64 ;; *) arch="" ;; esac
  if [ -z "$arch" ]; then say "Bu islemci icin otomatik Java yok. Java 21 kur: https://adoptium.net"; return; fi
  url="https://api.adoptium.net/v3/binary/latest/21/ga/$os/$arch/jre/hotspot/normal/eclipse"
  say "Java 21 indiriliyor (yaklasik 50 MB, bir kere)..."
  rm -rf runtime runtime_tmp jre21.tgz
  if curl -fL --retry 3 -o jre21.tgz "$url" && mkdir runtime_tmp && tar -xzf jre21.tgz -C runtime_tmp; then
    mkdir runtime
    mv runtime_tmp/*/* runtime/ 2>/dev/null
    [ -d runtime/Contents/Home ] && { mv runtime/Contents/Home/* runtime/ 2>/dev/null; }
  fi
  rm -rf runtime_tmp jre21.tgz
}
if ! find_java; then
  get_java
  find_java
fi
if [ -z "$JAVA" ]; then
  say ""
  say "HATA: Java 21 bulunamadi ve kurulamadi. Elle kur (https://adoptium.net) ve bu komutu tekrar calistir."
  exit 1
fi

# ---------- 3) Ses modu ----------
VC_FILE="@@VC_FILE@@"
if [ ! -f "mods/$VC_FILE" ]; then
  say ""
  say "Sesli sohbet modu indiriliyor (Simple Voice Chat, dogrulamali)..."
  mkdir -p mods
  if curl -fL --retry 3 -o "mods/$VC_FILE.part" "@@VC_URL@@"; then
    if command -v sha512sum >/dev/null 2>&1; then H=$(sha512sum "mods/$VC_FILE.part" | cut -d' ' -f1); else H=$(shasum -a 512 "mods/$VC_FILE.part" | cut -d' ' -f1); fi
    if [ "$H" = "@@VC_SHA512@@" ]; then mv "mods/$VC_FILE.part" "mods/$VC_FILE"; else rm -f "mods/$VC_FILE.part"; say "UYARI: dogrulama basarisiz, dosya silindi."; fi
  else
    rm -f "mods/$VC_FILE.part"
  fi
  [ -f "mods/$VC_FILE" ] || say "UYARI: Ses modu indirilemedi. Sunucu sesli sohbet OLMADAN acilacak. Elle indir (mods klasorune): https://modrinth.com/plugin/simple-voice-chat/versions?g=1.21.1&l=fabric"
fi

# ---------- 4) Bellek ----------
TOTALMB=4096; AVAILMB=0
if [ -r /proc/meminfo ]; then
  TOTALMB=$(awk '/^MemTotal:/ {print int($2/1024)}' /proc/meminfo)
  AVAILMB=$(awk '/^MemAvailable:/ {print int($2/1024)}' /proc/meminfo)
elif command -v sysctl >/dev/null 2>&1; then
  TOTALMB=$(( $(sysctl -n hw.memsize 2>/dev/null || echo 4294967296) / 1048576 ))
fi
[ -n "$TOTALMB" ] && [ "$TOTALMB" -gt 0 ] 2>/dev/null || TOTALMB=4096
MAXMB=$(( TOTALMB * 55 / 100 ))
if [ -n "$AVAILMB" ] && [ "$AVAILMB" -gt 0 ] 2>/dev/null; then
  A=$(( AVAILMB * 85 / 100 )); [ "$A" -lt "$MAXMB" ] && MAXMB=$A
fi
[ "$MAXMB" -gt 6144 ] && MAXMB=6144
[ "$MAXMB" -lt 1280 ] && MAXMB=1280
if [ -f ram_mb.txt ]; then
  R=$(head -n 1 ram_mb.txt | tr -dc '0-9')
  [ -n "$R" ] && MAXMB=$R
fi
MINMB=$(( MAXMB / 2 ))
[ -f profil.txt ] && export KAPISMA_PROFILE="$(head -n 1 profil.txt | tr -d '\r\n ')"

FLAGS="-XX:+UnlockExperimentalVMOptions -Xms${MINMB}M -Xmx${MAXMB}M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=130 -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
if [ "$MAXMB" -ge 3072 ]; then
  FLAGS="$FLAGS -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:MaxTenuringThreshold=1"
fi

# Android: ekran kapaninca Termux uyutulmasin
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock

say ""
say "================================================"
say " Kapisma sunucusu aciliyor"
say " Java: $JAVA"
say " Bellek: ${MINMB} - ${MAXMB} MB   (cihazda ${TOTALMB} MB var)"
say " Oyuncu adresi: bu cihazin Tailscale / yerel IP adresi, port 25565"
say " Ses: UDP 24454 portu acik olmali"
say "================================================"
say ""

# ---------- 5) Calistir (Manhunt yeni dunya isterse sunucu kapanir, dongu yeniden acar) ----------
while true; do
  # shellcheck disable=SC2086
  "$JAVA" $FLAGS -jar fabric-server-launch.jar nogui
  if [ -f kapisma_next.json ]; then
    say "Yeni dunya hazirlaniyor, sunucu yeniden aciliyor..."
    continue
  fi
  break
done
say "Sunucu kapandi."
