import unittest
from evaluate_thermal_trace import assess
class ThermalTraceTests(unittest.TestCase):
    def setUp(self):
        self.rows=[dict(timestamp="100",phase="prelaunch",status="0"),dict(timestamp="110",phase="exploration",status="2")]
        self.samples=[dict(timestamp="115",phase="exploration")]
    def test_ok(self): self.assertTrue(assess(self.rows,self.samples)["pass"])
    def test_missing(self): self.assertFalse(assess([],self.samples)["pass"])
    def test_severe(self):
        self.rows[-1]["status"]="3";self.assertFalse(assess(self.rows,self.samples)["pass"])
    def test_late(self):
        self.samples[0]["timestamp"]="145";self.assertFalse(assess(self.rows,self.samples)["pass"])
    def test_future(self):
        self.samples[0]["timestamp"]="109";self.assertFalse(assess(self.rows,self.samples)["pass"])
    def test_unknown(self):
        self.rows[-1]["status"]="UNKNOWN";self.assertFalse(assess(self.rows,self.samples)["pass"])
    def test_wrong_phase(self):
        self.rows[-1]["phase"]="pvp";self.assertFalse(assess(self.rows,self.samples)["pass"])
if __name__=="__main__":unittest.main()
