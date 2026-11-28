# D35 — five-mod retry and matched sustained screening
Plan saved first. Five-mod combat retry cmd-cf0950736a144827 Exit0: ServerCore effective14 flags OFF, eight same-tick automatic hooks, bounded sampled speeds, teleport detaches, damage/respawn/return and cleanup. Protected before/after exact hashes match. D34 failed evidence retained. Sustained cmd-171c0a11d1d257be both profiles Exit0, 3x45.4s active per profile, 30 samples each, eight Carpet actors, four distinct carriers, equal route progress25.325 per match, 90 entities/24 maps cleaned per profile; no stale transient maps persisted. Four mods mean MSPT1.086, max sampled mean1.953, max sampled p95 3.934, heap used240–469MiB, committed540. Five mods mean1.192, max3.512, max sampled p95 15.733, heap used234–446MiB, committed510–514. Four-mod profile remains preferred provisional candidate: lower measured sampled latency and fewer dependencies; five is compatible but no proven net benefit. ~9.8% higher sampled mean with five, not causal proof; single sequential JVM per profile, no randomized order, no RSS/peak system memory, flat world, no real users or 50-person load. ServerCore optional optimizations disabled; no global production config. Natural world benchmark design recorded separately, not executed. Native full match with optimizers/art deferred. No product source change, downloads, new product build, deletion or live/RTS edits. Evidence:
C_FREE=114018426880
prototypes/java-integration/build count=1130 bytes=251758561
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
acceptance_caravan_optimizers5_d35.log PASS=16 SHA256=B5C57BF75D6B19E11A56FBC0D0175915AB81C5D3C08AC6455E0BEBB30BBD904F
PROTECTED_BEFORE {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "acbd3ad04952ab58bfc3b85e4d96213b5f01b40ce2353d2fa1759c28e97d9394", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
PROTECTED_AFTER {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "acbd3ad04952ab58bfc3b85e4d96213b5f01b40ce2353d2fa1759c28e97d9394", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
acceptance_caravan_sustained_d35_optimizers4.log PASS=24 SHA256=4A7E07F3A5E01DCD309BBC5E883053DC054159D94BC2E418E540655F4CDD51DE
PROTECTED_BEFORE {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "acbd3ad04952ab58bfc3b85e4d96213b5f01b40ce2353d2fa1759c28e97d9394", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
SOAK_SUMMARY seconds=136.2 samples=30 msptMean=1.086 msptMax=1.953 sampleP95Max=3.934 heapMinMiB=240 heapMaxMiB=469
PROTECTED_AFTER {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "acbd3ad04952ab58bfc3b85e4d96213b5f01b40ce2353d2fa1759c28e97d9394", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
acceptance_caravan_sustained_d35_optimizers5.log PASS=24 SHA256=7C635C972BA56E9CDC7E9775C376DFA04E2556BDCF3746B6757C2FCD59ECB541
PROTECTED_BEFORE {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "acbd3ad04952ab58bfc3b85e4d96213b5f01b40ce2353d2fa1759c28e97d9394", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
SOAK_SUMMARY seconds=136.2 samples=30 msptMean=1.192 msptMax=3.512 sampleP95Max=15.733 heapMinMiB=234 heapMaxMiB=446
PROTECTED_AFTER {"_yedek\\devletler-rts-prototip\\mods\\kapisma-rts-rules.jar": "acbd3ad04952ab58bfc3b85e4d96213b5f01b40ce2353d2fa1759c28e97d9394", "server\\mods\\kapisma-0.1.0.jar": "bb92690de52dca8bd5369bd53e8d18e4a5f58417534187895028016eb49b532e"}
acceptance_runtime_caravan-1cb39f095cee.log PASS=0 SHA256=FA714325432127CBD9A7D1ADC93E3E04FA03258DB983660719C397BD2345B8B3
[00:56:45] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=130 pinned=24
acceptance_runtime_caravan-7ef19a7ef8ea.log PASS=0 SHA256=A848E272CC1F632AFCA50D2F20015DAE6C2016F5F43D797743FEE57DB4959EA8
[00:57:56] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=131 pinned=24
[00:59:01] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=127 pinned=24
[01:00:07] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=127 pinned=24
acceptance_runtime_caravan-2164293ed2df.log PASS=0 SHA256=305E7DBC8368ECA4FC1EA46CA4868AF0FB5D7A08434676B44B8FA7CDF6D6E01C
[01:01:36] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=130 pinned=24
[01:02:41] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=128 pinned=24
[01:03:47] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=128 pinned=24
