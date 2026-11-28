#!/usr/bin/env python3
"""Create a versioned candidate from a verified prior server ZIP, without overwriting releases."""
import argparse
import copy
import hashlib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("base", type=Path)
parser.add_argument("tag")
parser.add_argument("--jar", type=Path, default=root / "mod/kapisma/build/libs/kapisma-0.1.0.jar")
parser.add_argument("--expect-sha256", help="Refuse to package a JAR other than the tested build")
parser.add_argument("--safe-restart-wrapper", action="store_true", help="Stop launch loops on nonzero JVM exit; candidate only")
args = parser.parse_args()
if not args.tag or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in args.tag):
    parser.error("Invalid build tag")
jar = args.jar.read_bytes()
hash_jar = hashlib.sha256(jar).hexdigest()
if args.expect_sha256 and hash_jar != args.expect_sha256.lower():
    parser.error("JAR SHA-256 does not match the tested build")
out = root / "release" / f"kapisma-sunucu-{args.tag}-aday.zip"
if out.exists():
    parser.error("Candidate already exists; use a new tag")
j = "kapisma-sunucu/mods/kapisma-0.1.0.jar"
m = "kapisma-sunucu/PAKET_BILGISI.txt"
def replace_once(data, old, new):
    assert data.count(old) == 1, "Launcher pattern mismatch"
    return data.replace(old,new,1)

with zipfile.ZipFile(args.base) as source:
    if source.testzip() is not None:
        raise ValueError("Source ZIP CRC failed")
    old = hashlib.sha256(source.read(j)).hexdigest()
    manifest = source.read(m)
    if old.encode() not in manifest:
        raise ValueError("Old manifest/JAR mismatch")
    updated = manifest.replace(old.encode(), hash_jar.encode())
    updated += f"\r\nBuild: {args.tag} (test candidate, not deployed)\r\n".encode()
    changes = {j: jar, m: updated}
    if args.safe_restart_wrapper:
        bat = "kapisma-sunucu/start.bat"
        sh = "kapisma-sunucu/start.sh"
        b = source.read(bat)
        nl = b"\r\n" if b"\r\n" in b else b"\n"
        b = replace_once(b,
            nl.join([b'"!JAVA!" !FLAGS! -jar fabric-server-launch.jar nogui',b'if exist kapisma_next.json (']),
            nl.join([b'"!JAVA!" !FLAGS! -jar fabric-server-launch.jar nogui',b'set "RUN_EXIT=!ERRORLEVEL!"',
                     b'if not "!RUN_EXIT!"=="0" (',b'  echo Sunucu hata ile kapandi. Kod: !RUN_EXIT!. Otomatik yeniden baslatma durduruldu.',
                     b'  pause',b'  exit /b !RUN_EXIT!',b')',b'if exist kapisma_next.json (']))
        a = source.read(sh)
        a = replace_once(a,
            b'  "$JAVA" $FLAGS -jar fabric-server-launch.jar nogui\n  if [ -f kapisma_next.json ]; then',
            b'  "$JAVA" $FLAGS -jar fabric-server-launch.jar nogui\n  RUN_EXIT=$?\n  if [ "$RUN_EXIT" -ne 0 ]; then\n    say "Sunucu hata ile kapandi. Kod: $RUN_EXIT. Otomatik yeniden baslatma durduruldu."\n    exit "$RUN_EXIT"\n  fi\n  if [ -f kapisma_next.json ]; then')
        changes.update({bat:b,sh:a})
    with zipfile.ZipFile(out, "x") as target:
        for entry in source.infolist():
            data = changes.get(entry.filename)
            if data is None: data = source.read(entry)
            target.writestr(copy.copy(entry), data)
with zipfile.ZipFile(args.base) as source, zipfile.ZipFile(out) as target:
    assert target.testzip() is None
    assert target.namelist() == source.namelist()
    assert target.read(j) == jar and hash_jar.encode() in target.read(m)
    assert all(target.read(n) == (changes[n] if n in changes else source.read(n)) for n in target.namelist())
digest = hashlib.sha256(out.read_bytes()).hexdigest()
out.with_suffix(".sha256").write_text(f"{digest}  {out.name}\n", encoding="ascii")
print(f"JAR {hash_jar}\nZIP {digest}\n{out}\nCRC + contents verified")
