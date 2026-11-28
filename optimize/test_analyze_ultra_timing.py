import unittest
from analyze_ultra_timing import summarize

class TimingTests(unittest.TestCase):
    def test_nested_phases_stay_separate(self):
        result=summarize("""[Server thread/INFO]: ULTRA_TIMING phase=map.biome_preview elapsed_ns=6000000
[Server thread/INFO]: ULTRA_TIMING phase=map.biome_preview elapsed_ns=14000000
[Server thread/INFO]: ULTRA_TIMING phase=uhc.stock_maps elapsed_ns=21000000
[Server thread/INFO]: ULTRA_TIMING phase=arena.prepare elapsed_ns=23000000""")
        self.assertEqual(result["valid_records"],4)
        self.assertEqual(result["phases"]["map.biome_preview"],{"count":2,"total_ms":20.0,"max_ms":14.0,"mean_ms":10.0})
        self.assertEqual(result["phases"]["arena.prepare"]["total_ms"],23)
    def test_ignore_unknown_and_malformed(self):
        self.assertEqual(summarize("ULTRA_TIMING phase=other elapsed_ns=123\nULTRA_TIMING phase=map.create elapsed_ns=-5\nULTRA_TIMING phase=map.create elapsed_ns=5junk")["valid_records"],0)
    def test_preview_slice_overruns(self):
        r=summarize("ULTRA_TIMING phase=map.preview_slice elapsed_ns=9000000")
        self.assertEqual(r["phases"]["map.preview_slice"]["max_ms"],9)
    def test_chunk_preparation_phases(self):
        names=["arena.candidate","arena.candidate_slice","arena.initial_candidates","uhc.village_candidates","uhc.place_chests","uhc.find_depot"]
        r=summarize("\n".join("ULTRA_TIMING phase="+name+" elapsed_ns=6000000" for name in names))
        self.assertEqual(set(r["phases"]),set(names))
        self.assertEqual(r["valid_records"],6)
    def test_running_phases(self):
        names=["running.mode","running.minimap","running.compass"]
        r=summarize("\n".join("ULTRA_TIMING phase="+name+" elapsed_ns=2000000" for name in names))
        self.assertEqual(set(r["phases"]),set(names))
    def test_empty(self):
        self.assertEqual(summarize("")["phases"],{})
if __name__=="__main__": unittest.main()
