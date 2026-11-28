import unittest
from analyze_gc_heap import assess
def row(t,i,after=100,capacity=512):
    return f"[2026-11-26T22:00:00.000+0300][{t}.000s][info][gc          ] GC({i}) Pause Young (Normal) (G1 Evacuation Pause) 300M->{after}M({capacity}M) 12.345ms"
class HeapTests(unittest.TestCase):
    def test_window(self):
        r=assess("\n".join([row(1,0),row(40,1,200),row(60,2,300)]))
        self.assertTrue(r["evidence_pass"]);self.assertEqual(r["last_30s_of_gc_records"]["post_gc_mib_median"],250);self.assertEqual(r["all"]["pause_total_ms"],37.035)
    def test_invalid(self):self.assertFalse(assess(row(1,0,600))["evidence_pass"])
    def test_duplicate(self):self.assertFalse(assess(row(1,0)+"\n"+row(1,0))["evidence_pass"])
    def test_same_gc_multiple_pauses(self):self.assertTrue(assess(row(1,0)+"\n"+row(2,0))["evidence_pass"])
    def test_no_records(self):self.assertFalse(assess("no data")["evidence_pass"])
if __name__=="__main__":unittest.main()
