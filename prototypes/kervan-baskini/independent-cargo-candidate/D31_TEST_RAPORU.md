# D31 — scenic perimeter
D31 plan saved before change. Separate renderCell delegates unchanged playable cell for 73x49, adds deterministic layered 87x61 perimeter, 1730 new columns/11408 solid blocks, heights1–11, 500 columns height>=8. No map/route/cargo/hook or gameplay bounds change. Same 24 pinned chunks; only experimental Caravan2 world border radius96/size192, center .5,.5. Scan budget 63684 vs42924, +48.36%; still 512 blocks/3ms per slice, no measured baseline prep ticks. First native test cmd-06144b62b1b9da4b Exit1 after actual prep132 ticks and scene blocks passed; vanilla worldborder command queried overworld, not arena. Kept failure, corrected QA-only direct level border getter. Second cmd-bc89caac913f5e79 Exit0, both native clients received maps, physical scenery, actual arena border192 and forced outsider returned to old base, tunnel and extraction framebuffer proofs inspected. Extraction image shows ridge behind orange shelter, no blue border in this view; hillside patterned/terraced, no foliage yet, tunnel same. Actual native W/WD/WA, obstacle stops/resumes, walking theft, natural round results19 vs20, score messages once, clean ledger and shutdown, protected artifact guard unchanged. 349 pure incl terrain30, voice9 separate. Horse boundary recall and hook out-of-bounds not separately re-run in this native fixture; no human/Android, no wider client FPS/GC benchmark. Existing world border/arena separation verified for player only. No production build/download/deletion. D31 scenery not approved as final art; consider more organic slopes and sparse passive foliage outside playable area, no external resource-pack dependency. Exact proof:
C_FREE=115553480704
prototypes/java-integration/build count=1105 bytes=248918832
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
acceptance_caravan_scenic_d31_console.log PASS=11 SHA256=49A8D9D164B6725BF2C528E0BDB3A3A3055F484095DB18C77D85EC9CF443EAAF
acceptance_caravan_scenic_d31_v2_console.log PASS=32 SHA256=9419A4E94FED0BD1F4020C69D484B66A2C18A5BEF2AD42FEEF361943971DF723
acceptance_runtime_caravan-893b87acd13f.log PASS=0 SHA256=5C2BE4A25FF05AA038006B82FB1C5E3328D2AF190862D75B53C011B6656994D9
[00:15:21] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=132 pinned=24
acceptance_runtime_caravan-b4effc30c0a6.log PASS=0 SHA256=3307D3C518549561752CE45D6AE24EA46AAEFC0896C3D9781A44C47E837397EA
[00:17:16] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=131 pinned=24
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
