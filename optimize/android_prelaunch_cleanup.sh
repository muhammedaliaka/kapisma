#!/system/bin/sh
# Execute as authorized ADB shell, not root. Only safe-to-kill background processes.
# No package disable, uninstall, app-data deletion, storage cache or kernel drop_caches.
set -eu
mode="${1:---dry-run}"
case "$mode" in --dry-run|--apply|--apply-deep) ;; *) echo "invalid mode" >&2; exit 2;; esac
[ "$(id -u)" = 2000 ] || { echo "authorized Android shell UID 2000 required" >&2; exit 2; }
before=$(awk '/^MemAvailable:/ {print $2; exit}' /proc/meminfo)
[ -n "$before" ] || exit 2
# Explicit nonessential, user-facing app list. No security, accessibility, parental,
# communications, launcher, Google Play services, update, network or Termux packages.
packages='com.android.chrome com.google.android.googlequicksearchbox com.openai.chatgpt com.google.android.apps.maps com.google.android.apps.photos com.google.android.projection.gearhead com.google.android.videos com.google.android.youtube com.google.android.apps.youtube.music com.allfiles.viewerer com.document.pdf.word.excel.ppt.editor.office.reader.viewer pdf.pdfreader.viewer.editor.free com.canva.editor com.dephotos.crello com.figma.mirror ai.leonardo.leonardo com.netflix.mediaclient com.microsoft.office.powerpoint com.microsoft.office.officehubrow com.supercell.clashroyale com.overwolf.statsroyale com.replit.app com.github.android com.microsoft.copilot com.microsoft.bing ai.perplexity.app.android com.anthropic.claude com.abtnprojects.ambatana'
running=$(ps -A -o NAME)
attempted=0
failures=0
for pkg in $packages; do
    if printf '%s\n' "$running" | awk -v p="$pkg" '$1==p || index($1,p":")==1 {found=1} END {exit !found}'; then
        printf '%s %s\n' "$mode" "$pkg"
        if [ "$mode" != --dry-run ]; then
            attempted=$((attempted+1))
            if ! am kill --user 0 "$pkg"; then failures=$((failures+1)); fi
        fi
    fi
done
# Optional, one-shot Android-native cached process sweep. No force-stop.
if [ "$mode" = --apply-deep ]; then
    if dumpsys activity services com.termux | grep -q 'isForeground=true'; then
        if am kill-all; then
            echo 'deep_sweep=completed'
        else
            failures=$((failures+1))
        fi
        if ! dumpsys activity services com.termux | grep -q 'isForeground=true'; then
            echo 'Termux foreground service not verified after sweep' >&2
            failures=$((failures+1))
        fi
    else
        echo 'deep_sweep=skipped_termux_not_foreground'
    fi
fi
if [ "$mode" != --dry-run ]; then sleep 2; fi
after=$(awk '/^MemAvailable:/ {print $2; exit}' /proc/meminfo)
printf 'before_kib=%s after_kib=%s delta_kib=%s attempted=%s failures=%s\n' "$before" "$after" "$((after-before))" "$attempted" "$failures"
# am kill does not guarantee every process stops: foreground/bound services are retained.
[ "$failures" -eq 0 ] || exit 3
