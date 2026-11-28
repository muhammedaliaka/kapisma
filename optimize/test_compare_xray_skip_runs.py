import unittest
from compare_xray_skip_runs import REQUIRED,input_failures,read_manifest
def pair():
    a={k:"verified" for k in REQUIRED}
    a.update(native_route="1",native_mode="1",view="3",simulation="3")
    b=dict(a,xray_empty_skip="1")
    return a,b
class SkipComparisonTests(unittest.TestCase):
    def test_only_flag(self):self.assertEqual(input_failures(*pair()),[])
    def test_jar_difference(self):
        a,b=pair();b["candidate_sha256"]="other";self.assertTrue(input_failures(a,b))
    def test_config_difference(self):
        a,b=pair();b["fixed_heap_mib"]="1024";self.assertTrue(input_failures(a,b))
    def test_missing_field(self):
        a,b=pair();a.pop("script_sha256");self.assertTrue(input_failures(a,b))
    def test_wrong_flag(self):
        a,b=pair();b["xray_empty_skip"]="0";self.assertTrue(input_failures(a,b))
    def test_duplicate_manifest(self):
        with self.assertRaises(ValueError):read_manifest("key=value\nkey=other")
if __name__=="__main__":unittest.main()
