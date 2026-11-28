import unittest
from profile import plan

class ProfileTests(unittest.TestCase):
    def test_3gb(self):
        p = plan(3072, 2700, 4, "android")
        self.assertTrue(p["ready"])
        self.assertEqual(p["heap_mib"], 1280)
        self.assertEqual(p["properties_caps"], {"max-players": 4, "view-distance": 4, "simulation-distance": 3})
        self.assertIn("-XX:ActiveProcessorCount=4", p["java_flags"])
    def test_3gb_busy(self):
        self.assertFalse(plan(3072, 1800, 4, "android")["ready"])
    def test_6gb(self):
        p = plan(6144, 4800, 8, "android")
        self.assertTrue(p["ready"])
        self.assertEqual(p["profile"], "mobile6")
        self.assertLessEqual(p["heap_mib"], 3072)
    def test_pc(self):
        for total in (12288, 16384, 32768):
            p = plan(total, total - 2048, 16, "desktop")
            self.assertEqual(p["heap_mib"], 4096)
            self.assertEqual(p["properties_caps"]["max-players"], 4)
    def test_never_exceeds_available(self):
        for platform in ("android", "desktop"):
            for total in (2800, 3072, 4096, 6144, 12288, 16384):
                for avail in range(128, total+1, 128):
                    p = plan(total, avail, 8, platform)
                    if p["ready"]:
                        self.assertLessEqual(p["heap_mib"]+p["native_reserve_mib"]+p["os_reserve_mib"], avail)
    def test_invalid(self):
        for args in [(0,0,4,"android"),(3072,3073,4,"android"),(3072,2700,0,"android"),(3072,2700,4,"other")]:
            with self.assertRaises(ValueError):
                plan(*args)
    def test_flags_no_dangerous_shortcuts(self):
        p = plan(6144, 4800, 8, "android")
        self.assertFalse(any("DisableExplicitGC" in x or "AlwaysPreTouch" in x for x in p["java_flags"]))
        self.assertNotIn("online-mode", p["properties_caps"])
        self.assertNotIn("max-tick-time", p["properties_caps"])

if __name__ == "__main__":
    unittest.main()
