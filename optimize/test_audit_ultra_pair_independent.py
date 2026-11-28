import unittest
from pathlib import Path
import audit_ultra_pair_independent as a

class PairAudit(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=Path(__file__).parent
        cls.t=(cls.root/"android-ultra-stress-R37.tsv").read_text(encoding="utf-8-sig")
        cls.m=a.manifest((cls.root/"android-ultra-stress-R37-experiment.txt").read_text(encoding="utf-8-sig"))
    def pair(self):
        return dict(self.m,xray_empty_skip="0"),dict(self.m,xray_empty_skip="1")
    def test_real_r37_metrics(self):
        r=a.samples(self.t)
        self.assertEqual(r["all_windows"],{"mean_of_window_means_ms":15.7,"max_window_p99_ms":97.4})
        self.assertEqual(r["first_six_windows"]["mean_of_window_means_ms"],21.4)
        self.assertEqual(r["last_six_windows"]["mean_of_window_means_ms"],10.0)
    def test_matching_manifest_not_certificate(self):
        x,y=self.pair()
        r=a.assess(x,y,self.t,self.t)
        self.assertTrue(r["manifest_match"])
        self.assertFalse(r["performance_certified"])
    def test_each_required_difference(self):
        for k in a.REQUIRED:
            with self.subTest(k=k):
                x,y=self.pair();y[k]="different"
                self.assertIn("different:"+k,a.assess(x,y,self.t,self.t)["blockers"])
    def test_missing_fields(self):
        for k in a.REQUIRED:
            with self.subTest(k=k):
                x,y=self.pair();del x[k];del y[k]
                self.assertIn("missing:"+k,a.assess(x,y,self.t,self.t)["blockers"])
    def test_flag(self):
        x,y=self.pair();del y[a.FLAG]
        self.assertFalse(a.assess(x,y,self.t,self.t)["manifest_match"])
    def test_duplicate_key(self):
        with self.assertRaises(ValueError):a.manifest("a=1\na=2")
    def test_truncated_route(self):
        with self.assertRaises(ValueError):a.samples("\n".join(self.t.splitlines()[:-1]))
    def test_bad_percentile(self):
        with self.assertRaises(ValueError):a.samples(self.t.replace("P99: 71.5ms","P99: 1.0ms"))
    def test_bad_sample_count(self):
        with self.assertRaises(ValueError):a.samples(self.t.replace("sample: 100","sample: 99"))
    def test_duplicate_timestamp(self):
        with self.assertRaises(ValueError):a.samples(self.t.replace("1795724909","1795724902"))
if __name__=="__main__":unittest.main()
