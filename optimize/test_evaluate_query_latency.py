import unittest
from evaluate_query_latency import assess
class LatencyTests(unittest.TestCase):
    def setUp(self):
        self.lat=[dict(phase="idle",start_uptime_ms="100",elapsed_ms="1200",result="ok"),dict(phase="exploration",start_uptime_ms="2000",elapsed_ms="1400",result="ok")]
        self.metrics=[dict(phase="idle"),dict(phase="exploration")]
    def test_good(self):self.assertTrue(assess(self.lat,self.metrics)["latency_gate_pass"])
    def test_slow(self):
        self.lat[-1]["elapsed_ms"]="12520";r=assess(self.lat,self.metrics);self.assertTrue(r["coverage_pass"]);self.assertFalse(r["latency_gate_pass"])
    def test_timeout(self):
        self.lat[-1]["result"]="timeout";self.assertFalse(assess(self.lat,self.metrics[:-1])["coverage_pass"])
    def test_missing(self):self.assertFalse(assess(None,self.metrics)["coverage_pass"])
    def test_overlap(self):
        self.lat[-1]["start_uptime_ms"]="1100";self.assertFalse(assess(self.lat,self.metrics)["coverage_pass"])
    def test_phase_mismatch(self):self.assertFalse(assess(self.lat,self.metrics[::-1])["coverage_pass"])
if __name__=="__main__":unittest.main()
