# D36 — natural worldgen and optimized native acceptance
Plan saved before changes. QA-only --worldgen-proof mode, fresh dedicated normal worlds per profile, identical seed42, structures true, view/sim3, JVM 256/2048 MiB and 4 active CPUs. Real NoiseBasedChunkGenerator, target r.2.2.mca absent before forcing, zero loaded targets, same 24 remote chunks (64..69,64..67), getChunkNow full only (no synchronous request), exact 24 region header entries after save. Both profiles Exit0 cmd-699a9f327bde4f88. Baseline/noisium boot through registry smoke22.859/22.265s; target FULL observed3.406/3.000s, process CPU delta12516/10469ms, one sample each heap409/375MiB, not peak RSS. One A/B sequential run with concurrent machine activity possible; console poll delayed until all full, time includes command/queue/poll overhead, generated support chunks not counted, no statistical superiority or equivalence of terrain content claimed. ~11.9% observed elapsed reduction, ~16.4% CPU delta reduction. Both fresh QA worlds retained, no new app builds. Actual four optimizer native full match cmd-dd13b5f7036bf8e6 Exit0: two Java clients, actual W/WA/WD, blocked/resumed wagon, maps, both same-UUID horse recalls, exterior hook denied, boundary return, persistent foliage, walking theft, natural19vs20 and exact scores, two framebuffer captures (not visually re-inspected in D36), clean ledger/shutdown. 350 pure +9 voice. Protected hashes unchanged in all three runs; RTS starting hash differs from D35, attribution not established, no intervention. No live/KOD/RTS modifications by this lane, no new mod download or release archive, no deletion. No human or Android proof. Four-mod profile remains preferred candidate, no production install. Art improvements still pending. Evidence:
C_FREE=113888706560
prototypes/java-integration/build count=1207 bytes=275379517
prototypes/java-integration/build/runtime-worldgen-d36-none count=35 bytes=11453378
prototypes/java-integration/build/runtime-worldgen-d36-noisium count=36 bytes=11688676
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
acceptance_caravan_worldgen_d36_none.log PASS=8 SHA256=922B2A713062B73BB562DCF27346C5A3328B88AA1E2EF08CF9487C35F608204A
PROTECTED_BEFORE {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "796418f490a60031b82a63d8b21179a4ccda03c399575f67729aaa9022ddd75a", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
WORLDGEN_RESULT seconds=3.406 cpuDeltaMs=12516 samples=1 sampledHeapMaxMiB=409
PROTECTED_AFTER {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "796418f490a60031b82a63d8b21179a4ccda03c399575f67729aaa9022ddd75a", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
acceptance_caravan_worldgen_d36_noisium.log PASS=8 SHA256=5C943B1A35DC53AC9B927413F3F2FC43A44BD63AD216B4A04616142565EC07EA
PROTECTED_BEFORE {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "796418f490a60031b82a63d8b21179a4ccda03c399575f67729aaa9022ddd75a", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
WORLDGEN_RESULT seconds=3.0 cpuDeltaMs=10469 samples=1 sampledHeapMaxMiB=375
PROTECTED_AFTER {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "796418f490a60031b82a63d8b21179a4ccda03c399575f67729aaa9022ddd75a", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
acceptance_caravan_native_optimizers4_d36.log PASS=35 SHA256=F0769263789329D376C0CBC86B15559590A74EB8A2303BCF02120110AE91A992
PROTECTED_BEFORE {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "796418f490a60031b82a63d8b21179a4ccda03c399575f67729aaa9022ddd75a", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
PROTECTED_AFTER {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "796418f490a60031b82a63d8b21179a4ccda03c399575f67729aaa9022ddd75a", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
acceptance_runtime_caravan-40ccb3be643d.log PASS=0 SHA256=B9C48921AA79B5DD9A70F217DB310D6345BEFCA45D65BDE8732A61DACF42703D
acceptance_runtime_caravan-5c194940662b.log PASS=0 SHA256=45FEFEFF4DA1058D8C025B9E1335855E35D0C59B0AB5405953D3E7D8BF13363D
	- noisium 2.3.0+mc1.21-1.21.1
acceptance_runtime_caravan-f35952bb6ae6.log PASS=0 SHA256=35DB7C26976C855CB5DCBB61249E28B31573CAA6FD610A68F7D662B9E04209F1
	- noisium 2.3.0+mc1.21-1.21.1
[01:10:33] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=133 pinned=24
