import unittest
from evaluate_preload_probe import assess
from evaluate_chunk_route import expected
from test_evaluate_chunk_route import fixture as route_fixture
def fixture():
    log,rows,thermal=route_fixture()
    log=log.split("[Server] KAPISMA_ROUTE_STEP pass=first step=2")[0]
    preload=[];marks=[]
    for i,(x,z) in enumerate(expected(1).values()):
        preload.append(dict(target=str(i),x=str(x),z=str(z),prepare_ms="1000",result="ready"))
        marks.append(f"[Server] KAPISMA_PRELOAD_READY target={i} x={x} z={z}")
    log="\n".join(marks+["[Server] KAPISMA_PRELOAD_ALL_READY",log,"No force loaded chunks were found in fantasy:test","HARNESS_PRELOAD_COMPLETE centers=4 route_steps=1 performance_certified=false","All dimensions are saved","server_mode=restored","exit=0"])
    return log,preload,rows[:1],[dict(phase="route_first",elapsed_ms="1200",result="ok")],thermal[:2]
class PreloadTests(unittest.TestCase):
    def test_ok(self):
        r=assess(*fixture());self.assertTrue(r["probe_evidence_pass"],r);self.assertFalse(r["performance_certified"])
    def test_timeout(self):
        l,p,m,q,t=fixture();q[0]["elapsed_ms"]="15001";self.assertFalse(assess(l,p,m,q,t)["probe_evidence_pass"])
    def test_missing_ready(self):
        l,p,m,q,t=fixture();self.assertFalse(assess(l.replace("[Server] KAPISMA_PRELOAD_ALL_READY","not verified"),p,m,q,t)["probe_evidence_pass"])
    def test_unreleased(self):
        l,p,m,q,t=fixture();self.assertFalse(assess(l.replace("No force loaded chunks were found in fantasy:test","4 force loaded chunks"),p,m,q,t)["probe_evidence_pass"])
    def test_wrong_target(self):
        l,p,m,q,t=fixture();p[0]["z"]="0";self.assertFalse(assess(l,p,m,q,t)["probe_evidence_pass"])
if __name__=="__main__":unittest.main()
