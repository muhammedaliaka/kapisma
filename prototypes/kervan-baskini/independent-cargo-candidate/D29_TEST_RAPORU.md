# D29 — actual third-party bench seat guard
D29 plan before edits. Real PolyDecorations dark_oak_bench registered in named Fabric arena, 8 Carpet players. Exact known BlockHitResult passed to ServerPlayerGameMode.useItemOn with empty hand returned FAIL/NONE in baseline/fixed. Direct QA-only block state useWithoutItem positive control returned SUCCESS/polydecorations:seat, baseline player remained passenger after .4s. This is not a native-client exploit reproduction; positive control intentionally creates foreign seat via mod's own routine. Fixed CaravanHostAdapter tick allows only authorized actual driver seat or roster-owned horse for living, non-spectator, non-carrier, non-waiting participants; otherwise stopRiding and hook.cancel. No mod-dependent source or global settings; valid own horse still mountable and wagon seat remains after .5s. No crate scored, existing combat/respawn, lobby journal, and integrity guard passed. Pure 339 Kervan +9 voice. Baseline cmd-cf3cde89f003516a Exit0, fixed cmd-1c0f33324e22ca4a Exit0. Foreign seat forced positive control only, no actual virtual-entity packet proof, no carrier-specific forced seat test or full native regression after guard. No permanent furniture added; D28 visual screenshots partly obscured by HUD, not full visual acceptance. D28 combined optimizer passes but baseline guard failed, not production selected. No new product build/download/deletion. Existing old 10 ZIPs retained. Evidence:
C_FREE=115586080768
prototypes/java-integration/build count=1095 bytes=248280389
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
prototypes/kervan-baskini/independent-cargo-candidate/external-mod-candidates count=4 bytes=3949730
acceptance_caravan_bench_d29_baseline.log PASS=14 SHA256=D4487AFD00F9D2568CC721000B21E7581F4D60A48F33C6FB0D73C9A9DA51D7F8
acceptance_caravan_bench_d29_fixed.log PASS=16 SHA256=81651A16FAC96BBACFF50447FCA808B41791FA17F90AF00262506CEDC81D04F1
acceptance_runtime_caravan-737c012c8e73.log PASS=0 SHA256=57220F59142495D4F4783277D421C22262563BF781C5D5451CD316F37D4A5370
acceptance_runtime_caravan-7568092bdeb1.log PASS=0 SHA256=42216116C936F5A6E24476631754DE78EAA272F640A019BB312B3F77BD9CEF5D
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
