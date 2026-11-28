"""Exercise only the isolated RTS profile, restoring its launch configuration."""
from contextlib import contextmanager
from pathlib import Path
from queue import Empty, Queue
from threading import Thread
from zipfile import ZipFile
import os
import re
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[1]
PACK = ROOT / "world/datapacks/devletler-rts.zip"
PROPERTIES = ROOT / "server.properties"
JAVA = Path(os.environ["JAVA_HOME"]) / "bin/java.exe"
CARPET = next((PROJECT / "tools/test-mods").glob("*carpet*.jar"))


class Server:
    def __init__(self, process, log):
        self.process = process
        self.log = log
        self.lines = Queue()
        Thread(target=self.read, daemon=True).start()

    def read(self):
        for line in self.process.stdout:
            self.log.write(line)
            self.log.flush()
            self.lines.put(line)

    def send(self, command):
        print(">>", command, file=self.log, flush=True)
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()

    def expect(self, expected, timeout=18):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            try:
                line = self.lines.get(timeout=0.25)
            except Empty:
                if self.process.poll() is not None:
                    raise RuntimeError(f"Sunucu durdu. Beklenen: {expected}. Kayıt: {self.log.name}")
                continue
            if expected.casefold() in line.casefold() and not line.startswith(">>"):
                print(f"PASS {expected}", flush=True)
                return
            if ("/ERROR]" in line or "Couldn't load function" in line):
                raise RuntimeError(line.strip())
        raise TimeoutError(f"{expected} · {self.log.name}")


@contextmanager
def run_server(log_name, *, directory=ROOT, extra_mods=()):
    mods = [str(mod) for mod in extra_mods]
    if not (directory / "mods" / CARPET.name).is_file():
        mods.insert(0, str(CARPET))
    additional = [f"-Dfabric.addMods={os.pathsep.join(mods)}"] if mods else []
    with (ROOT / log_name).open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [str(JAVA), "-Xmx2G", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
             *additional, "-jar", "fabric-server-launch.jar", "nogui"],
            cwd=directory, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            encoding="utf-8", errors="replace",
        )
        server = Server(process, log)
        try:
            server.expect("Done (", timeout=90)
            yield server
        finally:
            if process.poll() is None:
                server.send("stop")
                try:
                    process.wait(timeout=25)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    process.wait(timeout=10)


def scenario(name, *, directory=ROOT, extra_mods=(), suffix="final"):
    with run_server(f"{name}_{suffix}.log", directory=directory, extra_mods=extra_mods) as server:
        for command in (ROOT / f"{name}.txt").read_text(encoding="utf-8-sig").splitlines():
            if not command or command.startswith("#"):
                continue
            if command.startswith("await "):
                server.expect(command[6:])
            elif command.startswith("wait "):
                time.sleep(float(command[5:]))
            else:
                server.send(command)
                time.sleep(1.05)


def configure(name, *, single=False):
    preset = ROOT / f"preset-{name}.zip"
    if not single:
        PACK.write_bytes(preset.read_bytes())
        return
    with ZipFile(preset) as original, ZipFile(PACK, "w") as modified:
        for entry in original.infolist():
            data = original.read(entry)
            if entry.filename == "data/krts/function/start.mcfunction":
                data = data.replace(b"#single krts_ctrl 0", b"#single krts_ctrl 1")
            modified.writestr(entry, data)


def restart(name, single):
    configure("classic", single=single)
    with run_server(f"{name}_save.log") as server:
        server.send("function krts:reset")
        for player in ("Restart1", "Restart2"):
            server.send(f"player {player} spawn")
            server.expect(f"{player} joined the game")
        server.send("function krts:start")
        server.expect("Hazırlık başladı.")
        server.send("scoreboard players get #single krts_ctrl")
        server.expect(f"#single has {int(single)}")
        server.send("reload")
        time.sleep(3)
        server.send("execute if score #phase krts_ctrl matches 1 run say RTS_RELOAD_KEEPS_MATCH")
        server.expect("RTS_RELOAD_KEEPS_MATCH")
    with run_server(f"{name}_restore.log") as server:
        if single:
            server.expect("Önceki oturum yeniden açılışta kapatıldı.")
            server.send("execute if score #phase krts_ctrl matches 0 unless entity @e[tag=krts.capital] unless entity @e[tag=krts.unit] run say RTS_SINGLE_CLEAN")
            server.expect("RTS_SINGLE_CLEAN")
            return
        server.expect("Puan, süre ve satın alma durdu.")
        server.send("function krts:resume")
        server.expect("Devam etmek için en az iki kayıtlı oyuncu gerekir.")
        for player in ("Restart1", "Restart2"):
            server.send(f"player {player} spawn")
            server.expect(f"{player} joined the game")
        server.send("function krts:resume")
        server.expect("Maç devam ediyor.")
        server.send("function krts:reset")


if __name__ == "__main__":
    properties = PROPERTIES.read_bytes()
    pack = PACK.read_bytes()
    try:
        with socket.create_connection(("127.0.0.1", 25576), timeout=0.25):
            raise RuntimeError("RTS profili zaten çalışıyor.")
    except OSError:
        pass
    PROPERTIES.write_bytes(re.sub(rb"(?m)^server-port=.*$", b"server-port=25579", properties))
    try:
        if "--restart-only" not in sys.argv:
            configure("classic")
            scenario("rts_playable_smoke")
            configure("diplomacy")
            scenario("diplomacy_game")
        restart("session_single", single=True)
        restart("session_persistent", single=False)
    finally:
        PROPERTIES.write_bytes(properties)
        PACK.write_bytes(pack)
