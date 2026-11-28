import unittest
from evaluate_xray_skip import assess
def line(r,s,c,ms,enabled="true"):
    return f"anti-xray: gönderilen=400 chunk=100 gizli=3000 cevher=3000 sahte=0 ort=4.00ms | açığa: {r} tarama, 0 paketli, {c} hücre, ort {ms}ms, gönderilen paket=0 (0 blok) | bos tarama atlandi={s} etkin={enabled}"
def fixture():
    return "\n".join([line(100,30,1000,"1.000"),"[Server] KAPISMA_NATIVE_WALK_READY",line(400,280,2000,"0.300"),"[Server] KAPISMA_NATIVE_WALK_END",line(500,370,3000,"0.250"),"exit=0"])
class SkipTelemetryTests(unittest.TestCase):
    def test_delta(self):
        r=assess(fixture(),True);self.assertTrue(r["telemetry_pass"],r)
        self.assertEqual(r["intervals"]["walk"]["skips"],250)
        self.assertEqual(r["intervals"]["walk"]["bfs_executed"],50)
        self.assertAlmostEqual(r["intervals"]["walk"]["approx_total_reveal_ms"],20)
        self.assertFalse(r["performance_certified"])
    def test_flag(self):self.assertFalse(assess(fixture(),False)["telemetry_pass"])
    def test_impossible(self):self.assertFalse(assess(fixture().replace("atlandi=280","atlandi=401"),True)["telemetry_pass"])
    def test_nonmonotone(self):self.assertFalse(assess(fixture().replace("atlandi=370","atlandi=200"),True)["telemetry_pass"])
    def test_no_skip(self):self.assertFalse(assess(fixture().replace("atlandi=280","atlandi=30"),True)["telemetry_pass"])
    def test_missing_marker(self):self.assertFalse(assess(fixture().replace("KAPISMA_NATIVE_WALK_END","no"),True)["telemetry_pass"])
if __name__=="__main__":unittest.main()
