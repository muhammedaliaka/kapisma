import unittest
from thermal_guard import assess
class ThermalTests(unittest.TestCase):
    def test_severe(self): self.assertTrue(assess("Thermal Status: 3")["stop"])
    def test_unknown(self): self.assertTrue(assess("no data")["stop"])
    def test_duplicate(self): self.assertTrue(assess("Thermal Status: 0\nThermal Status: 3")["stop"])
    def test_invalid(self): self.assertTrue(assess("Thermal Status: 7")["stop"])
    def test_crlf(self): self.assertFalse(assess("Thermal Status: 0\r\nCached temperatures:\r\n")["stop"])
    def test_normal(self): self.assertFalse(assess("Thermal Status: 0")["stop"])
    def test_cached_only(self):
        r=assess("Thermal Status: 0\nCached temperatures:\n Temperature{mValue=43.5, mType=0, mName=soc, mStatus=0}\n Temperature{mValue=102, mType=-1, mName=vendor, mStatus=0}\nHAL temperatures:\n Temperature{mValue=99, mType=0, mName=soc, mStatus=0}\n")
        self.assertEqual(r["readings_c"],{"soc":43.5})
if __name__=="__main__":unittest.main()
