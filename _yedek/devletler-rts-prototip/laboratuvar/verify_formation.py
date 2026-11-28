"""Send the native HYW formation order from a real Minecraft client and check both destinations."""
import argparse
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import time

LAB = Path(__file__).resolve().parent
RTS = LAB.parent
JAVA = Path(os.environ["JAVA_HOME"]) / "bin"
MODS = RTS / "mods"
COMMANDS = LAB / "server.commands"
POSITIONS = re.compile(r"(Archer|Militia) has the following entity data: \[([-.0-9]+)d, ([-.0-9]+)d, ([-.0-9]+)d\]")
FORMATION_SLOTS = re.compile(r"(Archer|Militia) has the following entity data: (\d+)\s*$", re.MULTILINE)
FORMATION_GROUPS = re.compile(r"(Archer|Militia) has the following entity data: (\[I;[^\]]+\])")


def wait_for(path, marker, process, timeout=30):
    end = time.monotonic() + timeout
    while time.monotonic() < end and process.poll() is None:
        if path.is_file():
            log = path.read_text(encoding="utf-8", errors="replace")
            if marker.casefold() in log.casefold():
                return log
        time.sleep(0.3)
    raise RuntimeError(f"Beklenen kayıt bulunamadı: {marker} ({path.name})")


def send(*commands):
    with COMMANDS.open("a", encoding="utf-8") as inbox:
        inbox.write("\n".join(commands) + "\n")


def check(log, process, condition, marker, attempts=10):
    for attempt in range(attempts):
        end = f"{marker}_PROBE_{attempt}"
        send(f"execute {condition} run say {marker}", f"say {end}")
        snapshot = wait_for(log, end, process)
        if any(line.endswith(f"[Server] {marker}") for line in snapshot.splitlines()):
            print(f"PASS {marker}", flush=True)
            return
    raise RuntimeError(f"Beklenen durum oluşmadı: {marker}")


def prepare_npc_roles(log, process):
    check(log, process, "if score RtsQA1 krts_job matches 0 if score RtsQA1 krts_watch matches 0",
          "RTS_NPC_CONTROLS_READY")
    send("execute as RtsQA1 run trigger krts_job set 2",
         "execute as RtsQA1 run trigger krts_watch set 2",
         "execute as RtsQA1 run trigger krts_npc set 1")
    check(log, process, "if score RtsQA1 krts_auto matches 1 if score RtsQA1 krts_focus matches 2 "
          "if score RtsQA1 krts_defense matches 2 if entity @e[tag=krts.yard,tag=krts.s1]",
          "RTS_NPC_TRAINING_AND_PATROL_ENABLED")
    send("execute as RtsQA1 run trigger krts_npc set 5")
    guard = "@e[tag=krts.unit,tag=krts.s1,scores={krts_guarding=1},limit=1]"
    trainee = "@e[tag=krts.unit,tag=krts.s1,scores={krts_training=1},limit=1]"
    check(log, process, f"if entity {guard} if entity {trainee} unless score {guard} krts_training matches 1",
          "RTS_NPC_DISTINCT_ASSIGNMENTS_BEFORE_ORDER")
    send(f"tag {guard} add krts.qa_guard", f"tag {trainee} add krts.qa_trainee",
         "tp RtsQA1 0.5 -60 -50.5")


def check_npc_orders_released(log, process):
    nearby = "x=0.5,y=-60,z=-63.5,distance=..18,limit=1"
    guard = f"@e[tag=krts.qa_guard,{nearby}]"
    trainee = f"@e[tag=krts.qa_trainee,{nearby}]"
    check(log, process, f"if score RtsQA1 krts_auto matches 1 if score {guard} krts_guarding matches 0 "
          f"if score {trainee} krts_training matches 0", "RTS_NPC_NATIVE_ORDER_PRECEDENCE_NEAR_BASE", attempts=5)


def build(temp):
    game = next((LAB / "RtsQA1/.fabric/remappedJars").rglob("client-intermediary.jar"))
    loader = next((RTS / "libraries/net/fabricmc/fabric-loader/0.20.1").glob("fabric-loader-0.20.1.jar"))
    brigadier = RTS / "libraries/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar"
    hyw = next(MODS.glob("Hundred*.jar"))
    api = next(MODS.glob("fabric-api-*.jar"))
    extracted = []
    with ZipFile(api) as libraries:
        for entry in libraries.namelist():
            if any(module in entry for module in ("fabric-lifecycle-events-v1-", "fabric-api-base-", "fabric-message-api-v1-")):
                module = temp / Path(entry).name
                module.write_bytes(libraries.read(entry))
                extracted.append(module)
    classes = temp / "classes"
    classes.mkdir()
    subprocess.run([JAVA / "javac.exe", "-proc:none", "--release", "21", "-d", classes,
                    "-cp", os.pathsep.join(map(str, [game, loader, brigadier, hyw, *extracted])),
                    LAB / "FormationClientTest.java"], check=True)
    artifact = temp / "kapisma-rts-formation-test.jar"
    with ZipFile(artifact, "w", ZIP_DEFLATED) as mod:
        for item in classes.rglob("*.class"):
            mod.write(item, item.relative_to(classes).as_posix())
        mod.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "kapisma_rts_formation_test",
                     "version": "1.0.0", "environment": "client",
                     "entrypoints": {"client": ["kapisma.rts.qa.FormationClientTest"]}}))
    return artifact


def verify(artifact, with_npc=False):
    suffix = "-formation-npc" if with_npc else "-formation"
    server_log = LAB / f"server{suffix}.out"
    client_log = LAB / f"RtsQA1{suffix}.out"
    env = {**os.environ, "KRTS_FORMATION_QA_JAR": str(artifact), "KRTS_FORMATION_QA_NPC": str(with_npc).lower()}
    old_log_time = server_log.stat().st_mtime_ns if server_log.is_file() else 0
    process = subprocess.Popen([sys.executable, "-B", LAB / "start_single.py"], env=env)
    try:
        for _ in range(100):
            if server_log.is_file() and server_log.stat().st_mtime_ns != old_log_time:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError("Formasyon deneme sunucusu açılmadı.")
        wait_for(server_log, "rtsdummy joined the game", process, timeout=120)
        wait_for(server_log, "RTS_QA_COMMAND_INBOX_READY", process)
        print("PASS Yerel Minecraft istemcisi sunucuya bağlandı.", flush=True)
        send("function krts:start")
        wait_for(server_log, "Hazırlık başladı.", process)
        send("scoreboard players get RtsQA1 krts_slot", "scoreboard players set #left krts_ctrl 1")
        wait_for(server_log, "RtsQA1 has 1 [krts_slot]", process)
        wait_for(server_log, "Merkez açık.", process)
        militia = "@e[tag=krts.s1,tag=krts.unit,type=hundred_years_war:militia,limit=1]"
        archer = "@e[tag=krts.s1,tag=krts.unit,type=hundred_years_war:archer,limit=1]"
        if with_npc:
            prepare_npc_roles(server_log, process)
        else:
            send("tp RtsQA1 0.5 -60 -14.5", f"tp {militia} -2.5 -60 -11.5", f"tp {archer} 2.5 -60 -11.5")
        wait_for(client_log, "RTS_FORMATION_HYW_ORDER_SENT", process)
        if with_npc:
            check_npc_orders_released(server_log, process)
        target_z = -36.5 if with_npc else 0.5
        positions = {}
        for attempt in range(22 if with_npc else 12):
            time.sleep(1)
            marker = f"RTS_FORMATION_SERVER_POSITIONS_{attempt}"
            send(f"say {marker}", f"data get entity {militia} Pos", f"data get entity {archer} Pos",
                 f"say {marker}_END")
            log = wait_for(server_log, f"{marker}_END", process)
            recent = log.rsplit(f"{marker}\n", 1)[-1]
            positions = {unit: (float(x), float(z)) for unit, x, _, z in POSITIONS.findall(recent)}
            if len(positions) != 2:
                continue
            (_, first_z), (_, second_z) = positions.values()
            if abs(first_z - target_z) < 1.6 and abs(second_z - target_z) < 1.6 and math.dist(*positions.values()) > 0.75:
                send("say RTS_FORMATION_METADATA", f"data get entity {militia} FormationType",
                     f"data get entity {archer} FormationType", f"data get entity {militia} FormationGroupId",
                     f"data get entity {archer} FormationGroupId", f"data get entity {militia} FormationSlotOrder",
                     f"data get entity {archer} FormationSlotOrder", "say RTS_FORMATION_METADATA_END")
                metadata = wait_for(server_log, "RTS_FORMATION_METADATA_END", process).rsplit("RTS_FORMATION_METADATA\n", 1)[-1]
                groups = dict(FORMATION_GROUPS.findall(metadata))
                slots = dict(FORMATION_SLOTS.findall(metadata))
                if (metadata.count('has the following entity data: "dragged"') != 2
                        or len(groups) != 2 or len(set(groups.values())) != 1
                        or len(slots) != 2 or len(set(slots.values())) != 2):
                    raise RuntimeError(f"Sunucu formasyon kimliği veya mevzi sırası beklenen biçimde değil: {groups}, {slots}")
                print(f"PASS Aynı HYW formasyonunda ayrı mevziler: {positions} (sıralar: {slots})", flush=True)
                send("say RTS_FORMATION_SERVER_DISTINCT_SLOTS_PASS", "function krts:reset")
                wait_for(server_log, "RTS_FORMATION_SERVER_DISTINCT_SLOTS_PASS", process)
                return
        raise RuntimeError(f"Birlikler çizgi üzerinde ayrı konumlara ulaşmadı: {positions}")
    finally:
        if process.poll() is None:
            send("stop")
            try:
                process.wait(timeout=50)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=10)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--with-npc", action="store_true")
    options = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="kapisma-rts-formation-") as directory:
        verify(build(Path(directory)), options.with_npc)
