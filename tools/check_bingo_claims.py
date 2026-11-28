#!/usr/bin/env python3
"""Independent real-log claim ownership verification (command echoes ignored)."""
import re, sys
from pathlib import Path
cases = {}
case = None
for line in Path(sys.argv[1]).read_text(encoding="utf-8-sig").splitlines():
    if "[Server thread/INFO]:" not in line: continue
    if match := re.search(r"\[Server\] TEAMCASE (\w+)",line):
        case = match[1]
        cases[case] = []
    if case and (match := re.search(r"\[Kapışma\] (.*?) aldı: (.*?)\s+\((\d+) eşya,",line)):
        cases[case].append((match[1],match[2],int(match[3])))
expected={"ORDER":1,"SOLO_SHARED":2,"DUO_SHARED":2,"DUO_EXCLUSIVE":1}
ok=set(cases)==set(expected)
for case,n in expected.items():
    values=cases.get(case,[])
    passed=(len(values)==n and len({v[0] for v in values})==n and len({v[1] for v in values})==1 and all(v[2]==1 for v in values))
    if case=="ORDER": passed=passed and values[0][0]=="Bot2" if values else False
    if case=="SOLO_SHARED": passed=passed and {v[0] for v in values}=={"Bot1","Bot2"}
    ok=ok and passed
    print(case, "PASS" if passed else "FAIL", values)
sys.exit(0 if ok else 1)
