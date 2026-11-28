import unittest
from analyze_android_jfr import summarize, window

def sample(thread, methods):
    return {"type":"jdk.ExecutionSample","values":{"sampledThread":{"javaName":thread},
      "stackTrace":{"frames":[{"method":{"type":{"name":owner},"name":name}} for owner,name in methods]}}}

class ProfileTests(unittest.TestCase):
    def test_server_only(self):
        frames=[("net/minecraft/BiomeManager","getBiome"),("tr/kapisma/game/GameWorld","centeredMap")]
        r=summarize([sample("Server thread",frames),sample("Worker-Main-1",frames)])
        self.assertEqual(r["execution_samples"],2)
        self.assertEqual(r["server_samples"],1)
        self.assertEqual(r["overlapping_family_counts"]["map_preview"],1)
        self.assertEqual(r["overlapping_family_counts"]["biome"],1)
        self.assertEqual(r["server_leaf"][0],("net.minecraft.BiomeManager.getBiome",1))
    def test_time_window(self):
        events=[{"values":{"startTime":f"2026-11-26T15:02:{s:02d}Z"}} for s in (19,20,36,37)]
        self.assertEqual(window(events,"2026-11-26T15:02:20Z","2026-11-26T15:02:37Z"),events[1:3])
        with self.assertRaises(ValueError): window(events,"2026-11-26T15:02:20",None)
    def test_intermediary_mapping(self):
        r=summarize([sample("Server thread",[("net/minecraft/class_1806","method_8002")])],
          ({"net.minecraft.class_1806":"net.minecraft.world.item.MapItem"},
           {("net.minecraft.class_1806","method_8002"):"renderBiomePreviewMap"}))
        self.assertEqual(r["overlapping_family_counts"]["map_preview"],1)
    def test_recursion_not_double_counted(self):
        frames=[("a","b"),("a","b")]
        r=summarize([sample("Server thread",frames)])
        self.assertEqual(r["server_inclusive"],[("a.b",1)])
    def test_gc_and_missing_stack(self):
        r=summarize([sample("Server thread",[]),{"type":"jdk.GarbageCollection","values":{"duration":"PT0.01S"}}])
        self.assertEqual(r["gc_count"],1)
        self.assertEqual(r["server_samples"],1)
        self.assertEqual(r["server_leaf"],[])
if __name__=="__main__": unittest.main()
