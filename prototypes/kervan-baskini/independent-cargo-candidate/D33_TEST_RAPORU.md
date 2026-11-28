# D33 — optimizer screening and simultaneous auto-hook
Plan saved before work. Official Modrinth 1.21.1 Fabric ModernFix 5.25.1, Noisium 2.3.0 and ServerCore 1.5.19 downloaded only into candidate folder; exact hashes/sizes pinned in D33_OPTIMIZER_CANDIDATES.json, embedded manifests inspected. ServerCore NOT executed: dynamic simulation/view/chunk distance, mobcap, activation range, merge and other behavior-sensitive options require separate config and regression; declares Lithium mixin.alloc.chunk_ticking false. ModernFix and Noisium independent named Fabric profiles cmd-9e7f327705204fc0 Exit0 both, three short start/stop matches each, eight Carpet actors, same-target race1/4 and distinct4, 90 entities and24 maps released per profile; no stale maps, clean shutdown and protected hashes. Active sample MSPT ModernFix1.920726/1.160742/1.547659, heap402/587/579 used,684 committed; Noisium1.780633/1.673472/1.221747, heap550/328/359 used,630 committed. No matched fresh baseline, single JVM per profile, no inference of speed or memory superiority, no worldgen benchmark (Noisium target). D33 automatic hook real eight Carpet actors, server command invokes eight tagged item callbacks in same tick; four samples (attached,positive upward velocity,max speed): (8,8,.01689215),(8,8,.03212254),(8,8,.04030332),(8,0,.07840000). All below .72 at sampled ticks; all eight detached on teleport. No native mouse/movement proof, no full ascent or separate obstruction in automatic mode, no overhead benchmark. cmd-050893ebbb30bc9f Exit0; combat/respawn/return and guard passed. 350 pure +9 voice unchanged. Only QA harness and candidate files changed, no product sources/build/live/RTS modifications or deletion. Existing 10 ZIP retained. Exact proof:
C_FREE=111690166272
prototypes/java-integration/build count=1119 bytes=249879240
prototypes/kervan-baskini/independent-cargo-candidate/build count=42 bytes=224918
release count=26 bytes=87534634
ZIP_COUNT=10
prototypes/kervan-baskini/independent-cargo-candidate/external-mod-candidates count=7 bytes=5534524
acceptance_caravan_external_d33_modernfix.log PASS=20 SHA256=354B7E55045AE4B44971D029F4FFF9AB4B34DD9C40A6550AD4C3776006DEE136
acceptance_caravan_external_d33_noisium.log PASS=20 SHA256=994B9C8F3F501FED2838FD331FAAE6F0611CD61A0C663734C91B3FDF83FCE60F
acceptance_caravan_auto8_d33_console.log PASS=15 SHA256=661B6376B45BE8D91BA393790023220BFE278392073EC5A7B5DC6387252FCE67
acceptance_runtime_caravan-fdfecc1a760c.log PASS=0 SHA256=58BBE5050DDC9FEBB27F7EA7DB0DAFE917BFEAE6A2E65521049F7CBC44E94BFC
	- modernfix 5.25.1+mc1.21.1
[00:40:57] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=138 pinned=24
[00:41:22] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=127 pinned=24
[00:41:47] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=127 pinned=24
acceptance_runtime_caravan-b8765a3f5a7e.log PASS=0 SHA256=AD5B402F6FFE680940CD361F41338FB054C2CC2F578B19BF3D61E8D40A0AD86B
	- noisium 2.3.0+mc1.21-1.21.1
[00:42:37] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=131 pinned=24
[00:43:02] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=127 pinned=24
[00:43:28] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=127 pinned=24
acceptance_runtime_caravan-59b2ce3e5b7f.log PASS=0 SHA256=EA8BB5BE2C817F1BC70C77C4CAD23BCC092E18EDE1415E95F20CB7D5BA19B700
[00:45:11] [Server thread/INFO]: CARAVAN_PREP scans=63684 ticks=133 pinned=24
9708483619C3DAB78FF3A536D170A8C5C28C42564BA99045EF634DC4FF880463 %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\mod\kapisma\build\libs\kapisma-0.1.0.jar
BB92690DE52DCA8BD5369BD53E8D18E4A5F58417534187895028016EB49B532E %USERPROFILE%\AppData\Roaming\.minecraft\kapisma\server\mods\kapisma-0.1.0.jar
