"""Read the technology menu received by a real local Minecraft client."""

import socket
import subprocess
import sys
import time

from settings import LAB_PORT
from verify_formation import LAB, check, send, wait_for

SERVER_LOG = LAB / "server-technology.out"
CLIENT_LOG = LAB / "RtsQA1-technology.out"


def verify():
    with socket.socket() as port:
        port.bind(("127.0.0.1", LAB_PORT))
    previous_log_time = SERVER_LOG.stat().st_mtime_ns if SERVER_LOG.is_file() else 0
    process = subprocess.Popen([sys.executable, "-B", LAB / "start_single.py", "--diplomacy", "--technology"])
    try:
        for _ in range(100):
            if SERVER_LOG.is_file() and SERVER_LOG.stat().st_mtime_ns != previous_log_time:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError("Teknoloji test sunucusu açılamadı.")
        wait_for(SERVER_LOG, "RTS_QA_COMMAND_INBOX_READY", process, timeout=120)
        send("function krts:start")
        wait_for(SERVER_LOG, "Hazırlık başladı.", process)
        send("scoreboard players set #left krts_ctrl 1")
        wait_for(SERVER_LOG, "Merkez açık.", process)
        check(SERVER_LOG, process, "if score RtsQA1 krts_tech matches 0", "RTS_CLIENT_TECH_MENU_READY")

        send("execute as RtsQA1 run trigger krts_menu")
        wait_for(CLIENT_LOG, "[Teknoloji]", process)
        send("execute as RtsQA1 run trigger krts_tech set 5")
        wait_for(CLIENT_LOG, "Saha Tıbbı · Önce Lojistik", process)
        wait_for(CLIENT_LOG, "Talim Doktrini · Önce Okçuluk", process)
        print("PASS İstemci iki önkoşulu da aldı.", flush=True)

        send("scoreboard players set #g1 krts_ctrl 17", "execute as RtsQA1 run trigger krts_tech set 1")
        wait_for(CLIENT_LOG, "Lojistik tamamlandı.", process)
        send("execute as RtsQA1 run trigger krts_tech set 5")
        wait_for(CLIENT_LOG, "[Saha Tıbbı · 9 altın]", process)
        send("execute as RtsQA1 run trigger krts_tech set 3")
        wait_for(CLIENT_LOG, "Saha Tıbbı tamamlandı.", process)
        send("execute as RtsQA1 run trigger krts_tech set 5")
        wait_for(CLIENT_LOG, "Saha Tıbbı · Açıldı", process)
        print("PASS İstemci araştırılabilir ve açılmış durumlarını aldı.", flush=True)
        send("function krts:reset")
        wait_for(SERVER_LOG, "Prototip maçı sıfırlandı.", process)
    finally:
        if process.poll() is None:
            send("stop")
            try:
                process.wait(timeout=50)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=10)


if __name__ == "__main__":
    verify()
