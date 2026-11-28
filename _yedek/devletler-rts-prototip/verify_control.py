"""Verify HYW's live server authorization entry point for Kapışma RTS units."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile

from verify_local import ROOT, PROJECT, JAVA, run_server, scenario
from laboratuvar.settings import LAB_PORT

LAB = ROOT / "laboratuvar/server"
EXPECTED = {
    1: "own=false capital=false foreign=false",
    2: "own=true capital=false foreign=false",
    3: "own=false capital=false foreign=false",
    4: "own=true capital=false foreign=false",
    5: "own=false capital=false foreign=false",
}


def build_test(temp):
    mapped = next((ROOT / ".fabric/remappedJars").rglob("server-intermediary.jar"))
    loader = next((ROOT / "libraries/net/fabricmc/fabric-loader/0.20.1").glob("fabric-loader-0.20.1.jar"))
    hyw = next((ROOT / "mods").glob("Hundred*.jar"))
    api = next((ROOT / "mods").glob("fabric-api-*.jar"))
    libraries = [mapped, loader, hyw]
    with ZipFile(api) as fabric:
        for entry in fabric.namelist():
            if "fabric-lifecycle-events-v1-" in entry or "fabric-api-base-" in entry:
                module = temp / Path(entry).name
                module.write_bytes(fabric.read(entry))
                libraries.append(module)
    classes = temp / "classes"
    classes.mkdir()
    subprocess.run([JAVA.with_name("javac.exe"), "-proc:none", "--release", "21", "-d", classes,
                    "-cp", os.pathsep.join(map(str, libraries)), ROOT / "laboratuvar/ControlServerTest.java"], check=True)
    output = temp / "control-proof.jar"
    with ZipFile(output, "w", ZIP_DEFLATED) as mod:
        for item in classes.rglob("*.class"):
            mod.write(item, item.relative_to(classes).as_posix())
        mod.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "kapisma_rts_control_test",
                    "version": "1.0.0", "environment": "server",
                    "entrypoints": {"server": ["kapisma.rts.qa.ControlServerTest"]}}))
    return output


def verify(label, adapter, test_mod):
    try:
        sock = socket.create_connection(("127.0.0.1", LAB_PORT), timeout=0.5)
    except OSError:
        pass
    else:
        sock.close()
        raise RuntimeError("Laboratuvar zaten çalışıyor.")
    shutil.copy2(ROOT / "preset-classic.zip", LAB / "world/datapacks/devletler-rts.zip")
    log_name = f"command_controls_{label}.log"
    with run_server(log_name, directory=LAB, extra_mods=(adapter, test_mod)) as server:
        server.send("function krts:reset")
        for player in ("Control1", "Control2", "Control3"):
            server.send(f"player {player} spawn")
            server.expect(f"{player} joined the game")
            server.send(f"gamemode adventure {player}")
        server.send("function krts:start")
        server.expect("Hazırlık başladı.")
        for stage, changed in enumerate((None, "function krts:begin", "function krts:pause", "function krts:resume",
                                         "gamemode spectator Control1"), 1):
            if changed:
                server.send(changed)
            server.send(f"scoreboard players set #qa_control krts_ctrl {stage}")
            server.expect(f"RTS_COMMAND_PROOF_{stage}")
        server.send("gamemode adventure Control1")
        server.send("function krts:reset")
    log = (ROOT / log_name).read_text(encoding="utf-8")
    observed = {int(test): flags for test, flags in re.findall(r"RTS_COMMAND_PROOF_(\d+) (own=.*)", log)}
    print(observed, flush=True)
    if observed != EXPECTED:
        raise AssertionError(f"Beklenen komuta kuralları karşılanmadı: {observed}")


def check_candidate(path, test_mod):
    sys.path.insert(0, str(PROJECT / "prototypes/devletler-rts"))
    from prepare import build_adapter

    build_adapter(next((ROOT / "mods").glob("Hundred*.jar")), path)
    verify("candidate", path, test_mod)
    env = {**os.environ, "KRTS_SERVER_QA_ADAPTER": str(path)}
    subprocess.run([sys.executable, "-B", ROOT / "laboratuvar/verify_formation.py"], env=env, check=True)
    shutil.copy2(ROOT / "preset-diplomacy.zip", LAB / "world/datapacks/devletler-rts.zip")
    scenario("diplomacy_game", directory=LAB, extra_mods=(path,), suffix="controller")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("label")
    parser.add_argument("--adapter", type=Path, default=ROOT / "mods/kapisma-rts-rules.jar")
    parser.add_argument("--candidate", action="store_true")
    options = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="kapisma-rts-control-") as directory:
        temp = Path(directory)
        test_mod = build_test(temp)
        if options.candidate:
            check_candidate(temp / "kapisma-rts-rules.jar", test_mod)
            import msvcrt
            with (ROOT / "rts-launch.lock").open("a+b") as lock:
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
                os.replace(temp / "kapisma-rts-rules.jar", options.adapter.resolve())
        else:
            verify(options.label, options.adapter.resolve(), test_mod)
