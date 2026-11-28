# D20 — geometry-based design analysis
Scope update: user explicitly defers physical Android and real-human acceptance for now; keep as unverified, not blockers for current development.

## Measured D19 baseline and D20 portals
| Metric | SAFE baseline | RISKY baseline | SAFE D20 | RISKY D20 |
|---|---:|---:|---:|---:|
| Route blocks |72.000|49.911|72.000|49.911|
| Continuous drive seconds @1.5 block/s |48.000|33.274|48.000|33.274|
| Attack-base eye-level visible route samples |42/72|45/51|41/72|45/51|
| Mean attack ground steps to <=3.5 of wagon |33.417|20.627|33.417|20.627|
| Mean escort ground steps to <=3.5 of wagon |29.056|35.667|29.056|35.667|
| Max attack steps |42|34|42|34|
| Max escort steps |57|57|57|57|
Both bases can reach 2706 ground cells by cardinal movement, with 2 blocks headroom, and all sampled wagon positions have an approach from both bases. No jump, diagonal speed, grapple, horse, combat, elevation or dynamic obstacle modeling. LOS sampled from one base center at fixed voxel eye layer, not universal visibility or a win-rate estimate. Approach radius is the rules radius, not exact live hit/LOS acceptance. Ground graph allows a single block-wide cell; not proof of horse access. Do not extrapolate to humans.

## Design conclusions
Risky route saves 14.726s (~30.7% travel time), has shorter attacker approach cost and higher base-center LOS. Safe route is more accessible for escort reinforcement on average. Keep speed and route geometry rather than arbitrary balancing. 300s leg timer is over six times uninterrupted safe travel and over nine times risky travel; this leaves considerable time for repeated fights. Do not tune timer solely from geometry. Current scoring rewards delivered cargo first, so speed is secondary unless all previous criteria tie. Role swap removes fixed team-side advantage, but does not establish player skill balance.
D20 masonry entrance portals: 7-block aperture, four clear blocks above ground; terrain and route geometry test passed. Before/after ground graph unchanged; safe LOS reduced one sample, risky unchanged. No arbitrary new cover or hill modifications until measured.

## Follow-up (D21)
- `NativeArenaGame.tickCaravan2` waiting spectator confinement compares pos to role base center (distanceSquared >9), then `spawn(p)` uses slot-specific offsets. Outer two spawn offsets (+/-2.7, -2) give 11.29 >9; may trigger redundant teleport every tick for those slots. Reproduce with 4v4 and waiting player, then fix using actual spawn point (or role base bounds). Do not change respawn delay. Check spectator camera and visibility confinement; no live changes until reproduced.
- Combat rules have no explicit spawn grace visible in allowDamage; verify actual hurt resistance and spawn camping using controlled server actors, then decide whether bounded, action-cancelled grace is justified. No protection added yet; avoid free combat advantage. Test nonlethal, friendly, enemy, environment and break cases.
- Inspect arena border presentation and tunnel framebuffer before additional decoration. No new build or release. Human and Android deferred by user.
