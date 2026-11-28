"""Verify the Windows shortcut binds only Tailscale and restores local settings."""
from pathlib import Path
import re
import socket
import subprocess
import sys
from threading import Thread
from queue import Queue

root = Path(__file__).resolve().parent
properties = root / "server.properties"
pack = root / "world/datapacks/devletler-rts.zip"
original_properties = properties.read_bytes()
original_pack = pack.read_bytes()
launcher = ["powershell.exe", "-NoProfile", "-File", "start_tailnet.ps1"]
local = "--local" in sys.argv
preset = "diplomacy" if "--diplomacy" in sys.argv else "classic"
mode = "local" if local else f"tailscale-{preset}"
command = [*launcher, "-Local"] if local else [*launcher, "-Preset", preset]

with (root / f"{mode}_verified.log").open("w", encoding="utf-8") as log:
    process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               stdin=subprocess.PIPE, text=True, errors="replace")
    output = Queue()

    def collect():
        for line in process.stdout:
            log.write(re.sub(r"(?<!\d)100\.\d+\.\d+\.\d+", "<TAILSCALE-IP>", line))
            log.flush()
            output.put(line)

    reader = Thread(target=collect, daemon=True)
    reader.start()
    try:
        while "adres" not in output.get(timeout=45):
            if process.poll() is not None:
                raise RuntimeError(f"Başlatıcı çıkış kodu: {process.returncode}")
        active_properties = properties.read_bytes()
        active_pack = pack.read_bytes()
        expected_pack = original_pack if local else (root / f"preset-{preset}.zip").read_bytes()
        assert active_pack == expected_pack
        conflicting = ["-Preset", "diplomacy"] if local else ["-Local"]
        blocked = subprocess.run([*launcher, *conflicting], cwd=root, capture_output=True, timeout=15)
        assert blocked.returncode != 0 and b"RTS profili zaten" in blocked.stderr, blocked.stderr[:170]
        assert properties.read_bytes() == active_properties
        assert pack.read_bytes() == active_pack
        print("PASS Açılış sürerken ikinci başlatıcı reddedildi. Etkin ayarlar korundu.", flush=True)
        while "Done (" not in output.get(timeout=90):
            if process.poll() is not None:
                raise RuntimeError(f"Başlatıcı çıkış kodu: {process.returncode}")
        active = properties.read_text(encoding="ascii")
        address = re.search(r"(?m)^server-ip=([\d.]+)$", active)[1]
        assert address == "127.0.0.1" if local else address.startswith("100.")
        assert re.search(r"(?m)^online-mode=true$", active)
        with socket.create_connection((address, 25576), timeout=3):
            print(f"PASS {mode} IPv4 TCP 25576, hesap doğrulaması açık.", flush=True)
        ready_properties = properties.read_bytes()
        preparer = root.parents[1] / "prototypes/devletler-rts/prepare.py"
        blocked_prepare = subprocess.run([sys.executable, "-B", preparer], cwd=root, capture_output=True, timeout=15)
        assert blocked_prepare.returncode != 0 and b"RTS profil kilidi" in blocked_prepare.stderr
        assert properties.read_bytes() == ready_properties and pack.read_bytes() == active_pack
        print("PASS Etkin sunucu profili yeniden hazırlanamadı.", flush=True)
        if not local:
            try:
                with socket.create_connection(("127.0.0.1", 25576), timeout=2):
                    pass
            except OSError:
                print("PASS Tailscale başlatıcısı loopback portuna bağlanmadı.", flush=True)
            else:
                raise RuntimeError("Sunucu loopback üzerinde beklenmedik şekilde açık.")
    finally:
        if process.poll() is None:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=50)
        reader.join(timeout=5)

assert process.returncode == 0, process.returncode
assert properties.read_bytes() == original_properties
assert pack.read_bytes() == original_pack
print("PASS Windows başlatıcısı yerel sunucu ayarlarını ve veri paketini geri yükledi.")
