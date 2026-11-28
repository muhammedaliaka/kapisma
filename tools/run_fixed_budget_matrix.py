#!/usr/bin/env python3
"""Repeat the exact twelve prior seeds; record safe refusals instead of aborting the matrix.
Only a dedicated test directory is accepted; seed file bytes restored in finally.
"""
import argparse, hashlib, json, re, subprocess, sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
ap = argparse.ArgumentParser()
ap.add_argument("--dir", default="server_test")
ap.add_argument("--xmx", default="3G")
a = ap.parse_args()
directory = (root / a.dir).resolve()
if directory not in [(root / name).resolve() for name in ("server_test", "server_test_order")]:
    ap.error("Only dedicated normal-world test fixtures are permitted")
source = root / "tools/last_bingo_seed_matrix_resource_io_raw.log"
rows = []
for line in source.read_text(encoding="utf-8-sig").splitlines():
    if "[Server thread/INFO]:" not in line: continue
    if m := re.search(r"\[Server\] MATRIS (\w+) (\d+) (\d+) (\d+) ([01])", line):
        rows.append([*m.groups(), None])
    if rows and (m := re.search(r"BINGO dunyasi acildi: seed=(-?\d+)",line)):
        rows[-1][-1] = m[1]
assert len(rows)==12 and all(r[-1] for r in rows)
cfg_path = directory / "config/kapisma/seeds.json"
original = cfg_path.read_bytes()
cfg = json.loads(original.decode("utf-8-sig"))
commands = ["player Bot1 spawn", "player Bot2 spawn", "wait 2", "kapisma game BINGO", "kapisma origins off", "kapisma team solo", "kapisma rule BINGO minimap 0"]
for i,(cls,border,diff,tasks,shared,seed) in enumerate(rows):
    key = f"matrix_{i}"
    matches=[entry for entry in cfg["classes"][cls] if str(entry["seed"])==seed]
    if not matches:
        matches=[entry for entries in cfg["classes"].values() for entry in entries if str(entry["seed"])==seed]
    assert matches and all(m==matches[0] for m in matches), (cls,seed)
    matches=matches[:1]
    cfg["classes"][key]=matches
    cfg.setdefault("class_names",{})[key]=key
    commands += [f"say MATRIS {cls} {border} {diff} {tasks} {shared}", f"say EXPECTED_SEED {seed}", f"kapisma seedclass {key}", f"kapisma rule BINGO borderStart {border}", f"kapisma rule BINGO difficulty {diff}", f"kapisma rule BINGO tasks {tasks}", f"kapisma rule BINGO sharedItems {shared}", "kapisma start", "await_regex Tombala başladı!|Bu seed ve sınırda 25 farklı hedef doğrulanamadı|Tombala dünyası zamanında doğrulanamadı", "kapisma stop", "await_regex Arena depolama kapandı:"]
scenario=root/"tools/scenarios/bingo_budget_exact_matrix.txt"
scenario.write_text("\n".join(commands)+"\n",encoding="utf-8")
log=root/"tools/last_bingo_budget_exact_matrix.log"
raw=root/"tools/last_bingo_budget_exact_matrix_raw.log"
try:
    cfg_path.write_text(json.dumps(cfg,ensure_ascii=False,indent=2),encoding="utf-8")
    print("FIXTURE",directory,"JAR",hashlib.sha256((directory/"mods/kapisma-0.1.0.jar").read_bytes()).hexdigest(),flush=True)
    with log.open("w",encoding="utf-8") as out:
        proc=subprocess.Popen([sys.executable,str(root/"tools/mctest.py"),str(scenario),"--dir",str(directory),"--xmx",a.xmx,"--command-delay","0.65"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding="utf-8",errors="replace")
        for line in proc.stdout:
            out.write(line);out.flush()
            print(line,end="",flush=True)
        code=proc.wait()
    raw.write_bytes((directory/"logs/latest.log").read_bytes())
finally:
    cfg_path.write_bytes(original)
    assert cfg_path.read_bytes()==original
# Independent verification from Minecraft's own log.
cases=[]
for line in raw.read_text(encoding="utf-8-sig").splitlines():
    if "[Server thread/INFO]:" not in line: continue
    if m:=re.search(r"\[Server\] MATRIS (\w+ \d+ \d+ \d+ ([01]))",line):
        cases.append(dict(case=m[1],qty=int(m[2])+1,expected=None,actual=None,budgets=[],cards=[],start=0,stop=0,close=0,refused=0))
    if not cases: continue
    c=cases[-1]
    if m:=re.search(r"EXPECTED_SEED (-?\d+)",line): c["expected"]=m[1]
    if m:=re.search(r"BINGO dunyasi acildi: seed=(-?\d+)",line): c["actual"]=m[1]
    if m:=re.search(r"Tombala ortak kaynak bütçesi: (\d+) / 25 hedef, hedef başına toplam miktar (\d+)",line): c["budgets"].append(tuple(map(int,m.groups())))
    if "Kart icerigi: " in line: c["cards"].append(re.findall(r"(GOREV:[^=]+|[a-z0-9_]+)=\d+[,.]\d+(?:\s|$)",line.split("Kart icerigi: ",1)[1]))
    if "Tombala başladı!" in line: c["start"]+=1
    if "BINGO kapandi" in line: c["stop"]+=1
    if "Arena depolama kapandı:" in line: c["close"]+=1
    if "Bu seed ve sınırda 25 farklı hedef doğrulanamadı" in line: c["refused"]+=1
passed=refused=0
for c in cases:
    base=c["expected"]==c["actual"] and c["actual"] is not None and c["close"]==c["stop"]==1
    ok=base and c["budgets"]==[(25,c["qty"])] and len(c["cards"])==1 and len(set(c["cards"][0]))==len(c["cards"][0])==25 and c["start"]==1
    reject=base and c["start"]==0 and c["refused"]==1 and len(c["budgets"])==1 and c["budgets"][0][0]<25
    passed+=ok;refused+=reject
    print(c["case"],"PASS" if ok else "SAFE_REFUSAL" if reject else "FAIL",c["actual"],c["budgets"],"start/close",c["start"],c["close"])
print(f"EXACT MATRIX: {passed}/12 started, {refused}/12 safe refusals, {len(cases)}/12 executed, runner exit {code}")
sys.exit(0 if code==0 and passed==len(cases)==12 else 1)
