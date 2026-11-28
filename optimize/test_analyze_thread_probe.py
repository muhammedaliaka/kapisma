import unittest
from analyze_thread_probe import assess
FIXTURE='''Full thread dump OpenJDK
"Server thread" #60 runnable
 java.lang.Thread.State: TIMED_WAITING (parking)
 at net.minecraft.util.thread.BlockableEventLoop.managedBlock(A.java:1)
 at net.minecraft.server.level.ServerChunkCache.getChunk(A.java:2)
 at net.minecraft.world.level.LevelReader.getChunk(A.java:3)
 at net.minecraft.world.entity.Entity.getBlockPosBelowThatAffectsMyMovement(A.java:4)
 at net.minecraft.world.entity.LivingEntity.travel(A.java:5)
 at carpet.patches.EntityPlayerMPFake.tick(A.java:6)
"Worker-Main-4" #58 runnable
 java.lang.Thread.State: RUNNABLE
 at net.minecraft.world.level.chunk.status.ChunkStatusTasks.generateSurface(A.java:7)
probe_exit=0
'''
class ProbeTests(unittest.TestCase):
    def test_complete(self):
        r=assess(FIXTURE)
        self.assertTrue(r["complete_snapshot"]);self.assertTrue(r["movement_chunk_wait_observed"])
        self.assertTrue(r["carpet_fake_player_path"]);self.assertTrue(r["surface_generation_worker_observed"])
    def test_incomplete(self):
        r=assess(FIXTURE.replace("probe_exit=0","probe_exit=124"))
        self.assertFalse(r["complete_snapshot"]);self.assertFalse(r["server_chunk_wait_observed"])
    def test_idle_worker(self):
        r=assess(FIXTURE.replace("State: RUNNABLE","State: WAITING"))
        self.assertFalse(r["surface_generation_worker_observed"])
    def test_nonmovement_wait(self):
        r=assess(FIXTURE.replace("LivingEntity.travel","LivingEntity.other"))
        self.assertTrue(r["server_chunk_wait_observed"]);self.assertFalse(r["movement_chunk_wait_observed"])
    def test_mob_collision_and_apoli(self):
        trace=FIXTURE.replace("at net.minecraft.util.thread.BlockableEventLoop.managedBlock(A.java:1)","at net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase.getCollisionShape(A.java:1)\n at net.minecraft.world.level.BlockCollisions.wrapOperation$apoli$overrideCollisionShapeQuery(A.java:2)\n at net.minecraft.world.entity.Mob.aiStep(A.java:3)")
        r=assess(trace);self.assertTrue(r["entity_collision_observed"]);self.assertTrue(r["apoli_collision_hook_observed"]);self.assertTrue(r["mob_tick_observed"]);self.assertFalse(r["movement_chunk_wait_observed"])
    def test_deep_worker_frames(self):
        trace=FIXTURE.replace(" at net.minecraft.world.level.chunk.status.ChunkStatusTasks.generateSurface",(" at java.lang.Thread.run(A.java:1)\n"*20)+" at net.minecraft.world.level.chunk.status.ChunkStatusTasks.generateSurface")
        r=assess(trace);self.assertTrue(r["surface_generation_worker_observed"]);self.assertTrue(r["worldgen_worker_observed"])
    def test_mapping(self):
        r=assess(FIXTURE.replace("net.minecraft.world.entity.LivingEntity.travel","net.minecraft.class_1309.method_6091"),({"net.minecraft.class_1309":"net.minecraft.world.entity.LivingEntity"},{("net.minecraft.class_1309","method_6091"):"travel"}))
        self.assertTrue(r["movement_chunk_wait_observed"])
if __name__=="__main__":unittest.main()
