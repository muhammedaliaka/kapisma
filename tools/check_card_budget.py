#!/usr/bin/env python3
"""Check actual per-match shared material reservations, not command output echoes."""
import re, sys
from pathlib import Path
cases = []
for line in Path(sys.argv[1]).read_text(encoding="utf-8-sig").splitlines():
    if "[Server thread/INFO]:" not in line:
        continue
    if m := re.search(r"\[Server\] MATRIS (\w+ \d+ \d+ \d+ ([01]))", line):
        cases.append({"case": m[1], "expected": 1+int(m[2]), "budget": [], "close": 0})
    if not cases:
        continue
    if m := re.search(r"Tombala ortak kaynak bütçesi: (\d+) / 25 hedef, hedef başına toplam miktar (\d+)", line):
        cases[-1]["budget"].append(tuple(map(int,m.groups())))
    if "Arena depolama kapandı:" in line:
        cases[-1]["close"] += 1
n = int(sys.argv[2])
passed = 0
for c in cases:
    ok = c["budget"] == [(25,c["expected"])] and c["close"] == 1
    print(c["case"], "PASS" if ok else "FAIL", c["budget"], "close", c["close"])
    passed += ok
sys.exit(0 if passed == len(cases) == n else 1)
