# D34 — combined optimizers and conservative ServerCore
Plan saved first. Four pinned mods Lithium/FerriteCore/ModernFix/Noisium loaded together in named Fabric cmd-5a4550037ec59ef2 Exit0. Three short start/stop cycles, eight Carpet actors, 90 entities/24 transient maps cleaned, vanilla map persisted, empty journal, guard unchanged. Active samples MSPT4.098701/1.760644/1.27274, used502/271/461MiB, committed660MiB. Not a paired benchmark; no claim of benefit. ServerCore exact version config annotation audit recorded in D34_SERVERCORE_CONFIG.md. Conservative QA-only config and optimization YAML explicitly set behavior-sensitive switches false. cmd-7877f0cff92e4ede Exit0: actual running mod config read using QA-only reflection, 14 flags false, eight actors, actual combat/respawn/return/guard checks passed. All five optional no-full-parity optimizations disabled, so no demonstrated optimization benefit. No ServerCore combination with the four tested yet; its Lithium override must be considered. 350 pure and voice9 passed. No native full match on optimizers4 or ServerCore, no worldgen benchmark or architecture work in this step. Existing build reused, no product build, no live/KOD/RTS changes, no downloads/deletion. Existing 10 ZIPs retained. Evidence:
C_FREE=114025082880
prototypes/java-integration/build count=1128 bytes=251604044
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
prototypes/kervan-baskini/independent-cargo-candidate/external-mod-candidates count=7 bytes=5534524
acceptance_caravan_optimizers4_d34.log PASS=20 SHA256=28DB77379BF516A6E1DC5D18A63E4295FA558BAAA930D8D2074D5B5F7FBB5C11
acceptance_caravan_servercore_d34.log PASS=13 SHA256=16F5691F594166CA27C5C19CDA8AE0416F8A739BB92C061DEE905C5E268CDDE0
acceptance_runtime_caravan-e6dd2b7949a9.log PASS=0 SHA256=039B572C9FF79400318DD2F060618B14B3783D11CA0D15A51FC04E74BF07C974
	- ferritecore 7.0.3
	- lithium 0.15.4+mc1.21.1
	- modernfix 5.25.1+mc1.21.1
	- noisium 2.3.0+mc1.21-1.21.1
[00:48:33] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=136 pinned=24
[00:48:58] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=127 pinned=24
[00:49:25] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=129 pinned=24
acceptance_runtime_caravan-048c357c8fca.log PASS=0 SHA256=C7CBDAF1AE21F17FEECB0D389DD2307F4F89654F223C3AB99E5FBE82182B4821
	- servercore 1.5.19+1.21.1
[00:51:09] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=137 pinned=24
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
config.yml 038D93612D468C03564AE5D60E37D9A654BA3F1C1DC65928C832D852B8631690
optimizations.yml 0291C14F375E75BF45DFF39E93C378776361EB33E7F18060162D9FE78DDD0BA7

## Five-mod follow-up
All five pinned mods loaded in cmd-f8afa7b1aba807f3. ServerCore 14 flags OFF, eight automatic hooks launched, sampled upward velocity/speed bounds, all detached on teleport, damage/respawn/return and clean shutdown passed (15 PASS). Final guard assertion FAILED, Exit1; preserve and do not count as accepted. Console SHA256 4B4A99D62394FD7A6779C83743668D3E368DF17795E6C1575806ECD69679AA06; runtime SHA256 8667BBA5857B27C00E7B5F68652220D616DDD58A1533FB8E7C5BE8823C6A9623. Post-inspection live jar BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E unchanged vs prior, RTS currently ACBD3AD04952AB58BFC3B85E4D96213B5F01B40CE2353D2FA1759C28E97D9394; original guard values not emitted, so exact changed path/attribution unproven. No protected files restored or touched. Guard strengthened with before/after exact hash logging; equality assertion remains. Reviewed generated ServerCore YAML accepted only by exact hash, preserving prior config. Combined five profile remains unaccepted. No new product build, no download, no deletion. C free114032230400 bytes; release26 files87534634 bytes/10 ZIP preexisting; integration1128 files251626071 bytes. Worldgen and art deferred.
