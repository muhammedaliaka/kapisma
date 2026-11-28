import unittest
from compare_chunk_routes import compare,FIXED
from test_evaluate_chunk_route import fixture
def meta(view):
    m=dict(FIXED,view=str(view))
    for k in ("candidate_sha256","config_except_view_sha256","modset_sha256","script_sha256"):m[k]="a"*64
    return "\n".join(k+"="+v for k,v in m.items())
class CompareTests(unittest.TestCase):
    def test_pair(self):
        r=compare((meta(4),*fixture()),(meta(3),*fixture()));self.assertTrue(r["comparison_valid"]);self.assertFalse(r["performance_certified"])
    def test_different_hash(self):
        self.assertFalse(compare((meta(4),*fixture()),(meta(3).replace("a"*64,"b"*64,1),*fixture()))["comparison_valid"])
    def test_duplicate(self):
        self.assertFalse(compare((meta(4)+"\nview=4",*fixture()),(meta(3),*fixture()))["comparison_valid"])
    def test_wrong_order(self):
        self.assertFalse(compare((meta(3),*fixture()),(meta(4),*fixture()))["comparison_valid"])
    def test_missing_metadata(self):
        self.assertFalse(compare(("",*fixture()),(meta(3),*fixture()))["comparison_valid"])
    def test_incomplete_evidence(self):
        l,r,t=fixture();self.assertFalse(compare((meta(4),l,r[:-1],t),(meta(3),*fixture()))["comparison_valid"])
if __name__=="__main__":unittest.main()
