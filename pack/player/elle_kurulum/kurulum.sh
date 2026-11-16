#!/usr/bin/env bash
# Kapisma istemci kurulumu (Linux / macOS). Kullanim: bash kurulum.sh [~/.minecraft]
cd "$(dirname "$0")" || exit 1
HERE="$(cd .. && pwd)"
case "$(uname -s)" in Darwin) DEF="$HOME/Library/Application Support/minecraft" ;; *) DEF="$HOME/.minecraft" ;; esac
MC="${1:-$DEF}"
if [ ! -d "$MC" ]; then echo "HATA: Minecraft klasoru yok: $MC (once Minecraft'i bir kere ac)"; exit 1; fi
MODS="$MC/mods"; OLD="$MODS/_kapisma_eski"
mkdir -p "$MODS"
echo "Hedef: $MODS"

for p in kapisma-hud fabric-api Origins voicechat; do
  for f in "$MODS"/$p*.jar; do
    [ -e "$f" ] || continue
    mkdir -p "$OLD"; mv -f "$f" "$OLD/"; echo "Eski dosya kenara alindi: $(basename "$f")"
  done
done
for f in "$HERE"/mods/*.jar; do
  [ -e "$f" ] || continue
  cp -f "$f" "$MODS/"; echo "Kuruldu: $(basename "$f")"
done

VC_FILE="@@VC_FILE@@"
if [ ! -f "$MODS/$VC_FILE" ]; then
  if curl -fL --retry 3 -o "$MODS/$VC_FILE.part" "@@VC_URL@@"; then
    if command -v sha512sum >/dev/null 2>&1; then H=$(sha512sum "$MODS/$VC_FILE.part" | cut -d' ' -f1); else H=$(shasum -a 512 "$MODS/$VC_FILE.part" | cut -d' ' -f1); fi
    if [ "$H" = "@@VC_SHA512@@" ]; then mv "$MODS/$VC_FILE.part" "$MODS/$VC_FILE"; echo "Kuruldu: $VC_FILE (sesli sohbet)"; else rm -f "$MODS/$VC_FILE.part"; fi
  fi
  [ -f "$MODS/$VC_FILE" ] || echo "UYARI: Ses modu indirilemedi. Elle indir: https://modrinth.com/plugin/simple-voice-chat/versions?g=1.21.1&l=fabric ($VC_FILE)"
fi
echo
echo "HAZIR: Fabric 1.21.1 profiliyle oyna, hostun adresini ekle. Oyunda V tusu: ses ayarlari."
