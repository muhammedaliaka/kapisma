#!/usr/bin/env python3
"""Independent verification of actual Minecraft server log (not the scenario echo)."""
import re, sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8-sig")
expected = int(sys.argv[2]) if len(sys.argv) > 2 else 12
cases = []
current = None
for line in text.splitlines():
    if "[Server thread/INFO]:" not in line:
        continue
    if match := re.search(r"\[Server\] MATRIS (\w+ \d+ \d+ \d+ \d+)", line):
        current = {"case": match.group(1), "cards": [], "totals": [], "started": 0, "stopped": 0}
        cases.append(current)
    if current is None:
        continue
    if match := re.search(r"Tombala karti: (\d+) eşya \+ (\d+) görev", line):
        current["totals"].append(tuple(map(int, match.groups())))
    if "Kart icerigi: " in line:
        body = line.split("Kart icerigi: ", 1)[1]
        names = re.findall(r"(GOREV:[^=]+|[a-z0-9_]+)=\d+[,.]\d+(?:\s|$)", body)
        current["cards"].append(names)
    if "Tombala başladı!" in line:
        current["started"] += 1
    if "BINGO kapandi" in line:
        current["stopped"] += 1
passed = 0
for case in cases:
    ok = (len(case["totals"]) == 1 and sum(case["totals"][0]) == 25
          and len(case["cards"]) == 1 and len(case["cards"][0]) == len(set(case["cards"][0])) == 25
          and case["started"] == case["stopped"] == 1)
    passed += ok
    print(case["case"], "PASS" if ok else "INCOMPLETE/FAIL", case["totals"],
          "card_sizes", [len(c) for c in case["cards"]], "start/stop", case["started"], case["stopped"])
unique = len({case["case"] for case in cases})
print(f"MATRIX {passed}/{len(cases)} cases; {unique} unique combinations")
sys.exit(0 if passed == unique == len(cases) == expected else 1)
