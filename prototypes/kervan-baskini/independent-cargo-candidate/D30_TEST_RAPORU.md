# D30 — dynamic grapple obstruction
Plan saved before edits. Named Fabric 8 Carpet actors, manual hook, actual tagged item callback; QA fixture teleports to tunnel roof and adds real stone block in same command after valid roof ray, restores air in finally. Baseline cmd-ff2ba33d076a7c9e Exit0: blocked rope still attached at .6s; unobstructed control still attached after pulse. Fixed cmd-f5a43b25f441a1da Exit0: cancel after obstacle, retained cooldown, removing obstacle cannot refire, unobstructed manual rope persists. New per-actor BiPredicate validation checks actual eye to stored anchor every attached tick (even inactive manual pulse), loaded bounding chunks, arena bounds, first collider anchor block, existing valid-anchor fluid/solid check. No change to duration/acceleration/velocity clamp or cooldown. 345/345 pure including grapple28, voice9/9; combat/respawn/lobby/guard passed. Actual server block fixture, not native input or actual traversal; no proof of automatic mode in live fixture, partial shapes at stored anchor may still need finer contact test, performance cost of eight simultaneous long rays not benchmarked. No new product build/download/deletion or production/live/RTS edits. Atmosphere and optimizer candidate research deferred until after hook verification. Evidence:
C_FREE=115553992704
prototypes/java-integration/build count=1097 bytes=248288846
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
acceptance_caravan_hook_d30_baseline.log PASS=14 SHA256=0525420CBC2FD9FB80C5D48C58F0199E0720E91D2B78397CCC1706FD9648C20D
acceptance_caravan_hook_d30_fixed.log PASS=15 SHA256=037FBA14DC8914E664AABB9F995A972576162051B67DD6DDAD61D52580484EE3
acceptance_runtime_caravan-9f6be7883b8d.log PASS=0 SHA256=9B148A7809E7D7FDD15D516DBF67335B32395EAAC38352235B814D7D88B10A25
acceptance_runtime_caravan-9d63633c82a0.log PASS=0 SHA256=E2DBEB70EC329FA5C045B69F0033AC3331DD98BAD00FEB20F0CE09398489F449
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
