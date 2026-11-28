import unittest
from compare_carpet_profiles import compare,FIXED
from test_evaluate_carpet_profile import fixture
def run(mod,**kw):
    m=dict(FIXED,candidate_sha256="a"*64,config_except_view_sha256="b"*64,script_sha256="c"*64,modset_sha256=mod);m.update(kw)
    rows=[dict(phase="exploration",timestamp=str(t),rss_kib="1000",available_kib="3000") for t in (115,125)]
    q=[dict(phase="exploration",start_uptime_ms=str(t),elapsed_ms="1200",result="ok") for t in (5000,10000)]
    th=[dict(phase="prelaunch",timestamp="100",status="0"),dict(phase="exploration",timestamp="110",status="0"),dict(phase="exploration",timestamp="120",status="0")]
    return "\n".join(k+"="+v for k,v in m.items()),fixture(),rows,q,th
class ProfilePairTests(unittest.TestCase):
    def setUp(self):self.a="d"*64;self.b="e"*64
    def test_pair(self):
        r=compare(run(self.a),run(self.b),self.a,self.b);self.assertTrue(r["input_comparison_pass"],r);self.assertFalse(r["performance_certified"])
    def test_density(self):self.assertFalse(compare(run(self.a),run(self.b,profile_mobcap="35"),self.a,self.b)["input_comparison_pass"])
    def test_script_mismatch(self):self.assertFalse(compare(run(self.a),run(self.b,script_sha256="f"*64),self.a,self.b)["input_comparison_pass"])
    def test_wrong_mod(self):self.assertFalse(compare(run(self.a),run(self.b),self.a,"f"*64)["input_comparison_pass"])
    def test_missing_thermal(self):
        r=list(run(self.b));r[-1]=[];self.assertFalse(compare(run(self.a),r,self.a,self.b)["input_comparison_pass"])
    def test_missing_query(self):
        r=list(run(self.b));r[-2]=[];self.assertFalse(compare(run(self.a),r,self.a,self.b)["input_comparison_pass"])
if __name__=="__main__":unittest.main()
