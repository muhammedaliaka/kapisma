# D27 — external mod compatibility screening
User authorizes external mod downloads/configuration, visual/content expansion and server optimization. D27 plan saved before changes. Official pinned candidate Lithium 0.15.4 and FerriteCore 7.0.3 downloaded, SHA512 and size verified against Modrinth API; dependencies/version/manifest audited. Existing PolyDecorations 0.4.5 and Polymer 0.9.19 preserved. Only QA harness changed (explicit whitelisted profile, hash/size enforcement, Polymer auto-host disabled, eight-actor lobby readiness). Default none unchanged. Four sequential named Fabric JVMs, same 256/2048MiB heap bounds, 4 active CPU count, same fixture/port/seed, 3 short start/stop matches per profile, 8 Carpet actors. All profiles Exit0 cmd-399704d924cd88c5, 12 matches total. No actual native client or real human proof, no 45-second soak, no forced GC, RSS or peak memory measurement, single JVM per profile, same reused QA world and no load isolation from concurrent external processes. Each of three active observations is an average over a tick window, not independent replicate. No claim of statistically established improvement.

| Profile | active sample mean MSPT | active heap used MiB range | heap committed MiB | decision |
|---|---:|---:|---:|---|
| none |1.245|417–428|640|baseline|
| Lithium |1.280|288–482|527|compatibility pass; no demonstrated tick gain|
| FerriteCore |1.652|319–355|460|compatibility pass; promising lower sampled heap, slower sampled ticks|
| PolyDecorations+Polymer |1.814|353–626|747|compatibility pass without actual furniture; additional memory cost|

Per profile 90 captured entities removed/unregistered, 24 map buffers released and absent from disk, normal control map saved, empty journal; same-target race 1/4, four distinct carriers. Decor no new furniture or client pack acceptance yet; auto-host disabled, no public port configured. Before enabling for production: native client with actual placed objects, resource-pack generation/delivery and refusal UX, collision, protection, clean world removal; test Analog/Origins/voice compatibility in authorized integrated candidate. Benchmark combined optimizers with repeated controlled A/B 45s active or longer; test high load beyond 8 actors only after explicit fixture capacity changes, compare world generation separately. No performance settings that alter gameplay enabled. ModernFix/Noisium/ServerCore candidate versions identified but not downloaded; dynamic settings require game regression. Existing release ZIP count unchanged (10 preexisting, retention issue not resolved, no deletion). No product build, live/RTS untouched. Evidence:
C_FREE=120957665280
prototypes/java-integration/build count=1083 bytes=246895911
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
prototypes/kervan-baskini/independent-cargo-candidate/external-mod-candidates count=4 bytes=3949730
none PASS_COUNT=20
OBS cycle-1-active tick=549 mspt=1.554178 p95=2.1688 heapUsedMiB=428 heapCommittedMiB=640
OBS cycle-2-active tick=1017 mspt=1.105593 p95=1.4637 heapUsedMiB=417 heapCommittedMiB=640
OBS cycle-3-active tick=1478 mspt=1.07606 p95=1.4846 heapUsedMiB=421 heapCommittedMiB=640
6D57FD3C56AE4F298B141782AE9100B4FD9360DD99F9383FCE243EE50AD7C372
lithium PASS_COUNT=20
OBS cycle-1-active tick=563 mspt=1.653569 p95=2.3627 heapUsedMiB=288 heapCommittedMiB=527
OBS cycle-2-active tick=1028 mspt=1.120209 p95=1.547 heapUsedMiB=482 heapCommittedMiB=527
OBS cycle-3-active tick=1493 mspt=1.06518 p95=1.5044 heapUsedMiB=439 heapCommittedMiB=527
F2C3342967E743AB0DDFCB933C9C6D8FEF77C8AF2DF7A1428C82810B5754199E
ferrite-core PASS_COUNT=20
OBS cycle-1-active tick=549 mspt=1.741681 p95=2.7337 heapUsedMiB=323 heapCommittedMiB=460
OBS cycle-2-active tick=1018 mspt=1.650022 p95=2.3633 heapUsedMiB=319 heapCommittedMiB=460
OBS cycle-3-active tick=1494 mspt=1.565658 p95=2.3453 heapUsedMiB=355 heapCommittedMiB=460
72008395825FE7AB2234E9C938FBAB59BC6E87A409C900BFCA2EED79E5115D8C
decor PASS_COUNT=20
OBS cycle-1-active tick=558 mspt=2.265513 p95=3.9161 heapUsedMiB=501 heapCommittedMiB=747
OBS cycle-2-active tick=1034 mspt=1.519355 p95=2.1949 heapUsedMiB=353 heapCommittedMiB=747
OBS cycle-3-active tick=1507 mspt=1.657158 p95=2.2136 heapUsedMiB=626 heapCommittedMiB=747
4C635FBF0FCEB9D57FADE63FD0DB12FA46FFC44649094ABD776A7CB295DDA696
59738ffa0252 SHA256=91D757C12F94F7FE765C48325E012FABCC4DE09D157F6905B7B5BB2D7C86AF0B
ab6b7ced3dd1 SHA256=BEFA71BAFA91A05448B698B67DF01995C59D8516CAEE4988AEBD721FB22436A8
	- lithium 0.15.4+mc1.21.1
b7084412662f SHA256=94442A4694A490DBF334DC8624FAAC7B24B8BBC94EFBD4CA0EF90784D1765741
	- ferritecore 7.0.3
ad58b052bd6e SHA256=8BF3877A72636E94699F58858D952195A4757421137655A87E53F8BD488D3B32
	- polydecorations 0.4.5+1.21.1
	- polymer-bundled 0.9.19+1.21.1
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
