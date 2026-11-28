import unittest
from compare_fixed_entity import compare
from test_evaluate_fixed_entity import fixture
class FixedCompareTests(unittest.TestCase):
    def setUp(self):
        self.a="a"*64;self.b="b"*64
        self.base=fixture();c=list(fixture())
        c[-1]=c[-1].replace("modset_sha256="+self.a,"modset_sha256="+self.b);self.c=c
    def test_equal(self):
        r=compare(self.base,self.c,self.a,self.b);self.assertTrue(r["comparison_evidence_pass"],r);self.assertEqual(r["metrics"]["mob_cost_delta_percent"],0);self.assertFalse(r["performance_certified"])
    def test_fixture_hash(self):
        self.c[-1]=self.c[-1].replace("fixture_sha256="+self.a,"fixture_sha256="+self.b)
        self.assertFalse(compare(self.base,self.c,self.a,self.b)["comparison_evidence_pass"])
    def test_population(self):
        self.c[0]=self.c[0].replace("bat_entities has 16","bat_entities has 15")
        self.assertFalse(compare(self.base,self.c,self.a,self.b)["comparison_evidence_pass"])
    def test_same_modset(self):self.assertFalse(compare(self.base,self.base,self.a,self.a)["comparison_evidence_pass"])
if __name__=="__main__":unittest.main()
