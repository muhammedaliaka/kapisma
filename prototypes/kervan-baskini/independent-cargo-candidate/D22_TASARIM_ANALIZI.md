# D22 — spawn screen design analysis
Baseline D21 actual terrain, 4 slots per role. Fixed set of reachable ground observer cells outside base, 6–20 blocks from each spawn, line sampled every .15 block at eye voxel y=2. Counts are spawn/observer pairs, not unique cells or combat survival. Tested three virtual layouts before modifying production candidate. Equal 6 columns per role and two blocks tall.
| Design | Escort visible /2448 | Attacker visible /2380 | Worst nearest exit (steps) | Min exit directions within 12 steps | Ground reach |
|---|---:|---:|---:|---:|---:|
| Baseline |2184|2312|4|4|2706|
| V1 |1531|1506|4|4|2694|
| V2 |1654|1629|4|4|2694|
| V3 |1481|1450|5|4|2694|
V1 chosen by multi-objective tradeoff: lower exposure than V2, unchanged worst nearest exit unlike V3, similar remaining exposure proportions for both roles. Relative reduction escort29.9%, attacker34.9%. One role spruce, other stone bricks: identical full-cube geometry, different material. Open center passage, 12 total footprints, 24 blocks total. Actual candidate matches selected virtual design exactly; all ground cells except 12 occupied columns remain reachable. No new invulnerability, unchanged timer, score, cargo, spawn position, horse ownership. Existing spawn/horse/road tests all pass.

Route analysis after: safe72 blocks/48s, risky49.911/33.274s; mean attack approach33.417 vs20.627, escort29.056 vs35.667 unchanged. One specific base-center sight metric falls from41/72 to12/72 for safe and45/51 to19/51 for risky. Relative distinction remains (16.7% vs37.3%). Do not treat a sheltered center as representative of attacking players moving out of base. No elevated observer/jump/horse/arrow simulation; no human win-rate claim. Existing conservative ground graph preserves four directions within 12 cardinal steps, not four independent paths in strict graph theoretic sense. Further consider actual bow/swing, climbing, spawn camping and native movement; don't tune timer from these proxies. Android/human deferred by user.
