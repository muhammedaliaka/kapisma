import unittest
from check_stress import assess, PHASES

def sample(mspt="20", p95="30", p99="80", rss=1400, free=1000):
    return "\n".join(f"  12:00:00 {phase} mspt={mspt} p95={p95} p99={p99}  bos_ram={free}MB java={rss}MB" for phase in sorted(PHASES)) + "\nSunucu 'Can't keep up' uyarisi: 0 kez\nHata satiri: 0\nexit=0\n"

class StressGateTests(unittest.TestCase):
    def test_pass_not_certified(self):
        r=assess(sample())
        self.assertTrue(r["sample_gate_pass"])
        self.assertFalse(r["certified"])
    def test_missing(self):
        self.assertFalse(assess(sample(mspt="None"))["sample_gate_pass"])
    def test_slow(self):
        self.assertFalse(assess(sample(p95="51"))["sample_gate_pass"])
        self.assertFalse(assess(sample(p99="101"))["sample_gate_pass"])
    def test_memory(self):
        self.assertFalse(assess(sample(rss=1701))["sample_gate_pass"])
        self.assertFalse(assess(sample(free=511))["sample_gate_pass"])
    def test_no_summary(self):
        self.assertFalse(assess(sample().replace("Hata satiri: 0",""))["sample_gate_pass"])
        self.assertFalse(assess(sample().replace("exit=0","exit=1"))["sample_gate_pass"])
    def test_warnings(self):
        self.assertFalse(assess(sample().replace("0 kez","1 kez"))["sample_gate_pass"])
    def test_empty(self):
        self.assertFalse(assess("")["sample_gate_pass"])
