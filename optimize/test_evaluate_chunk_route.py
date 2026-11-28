import unittest
from evaluate_chunk_route import assess,STEPS,expected
def fixture():
    lines=[];rows=[];thermal=[dict(timestamp="1",phase="prelaunch",status="0")]
    for i,(p,s) in enumerate(STEPS):
        lines.append(f"[Server] KAPISMA_ROUTE_STEP pass={p} step={s}")
        for name,(x,z) in expected(s).items():
            lines.extend([f"{name} has the following entity data: [{x}.0d, 180.0d, {z}.0d]",f"{name} has the following entity data: 0"])
        phase="route_"+p;t=str(10+i)
        rows.append(dict(phase=phase,timestamp=t,available_kib="2000000",rss_kib="1000000",battery_deci_c="200",response="Average time per tick: 60.0ms P95: 100.0ms P99: 150.0ms"))
        thermal.append(dict(timestamp=t,phase=phase,status="0"))
    lines+=["HARNESS_ROUTE_COMPLETE first=12 revisit=12 performance_certified=false","All dimensions are saved","server_mode=restored","exit=0"]
    return "\n".join(lines),rows,thermal
class RouteTests(unittest.TestCase):
    def test_complete(self):
        r=assess(*fixture());self.assertTrue(r["route_evidence_pass"]);self.assertFalse(r["performance_certified"])
    def test_wrong_position(self):
        l,r,t=fixture();self.assertFalse(assess(l.replace("[16.0d, 180.0d, -136.0d]","[17.0d, 180.0d, -136.0d]",1),r,t)["route_evidence_pass"])
    def test_missing_step(self):
        l,r,t=fixture();self.assertFalse(assess(l.replace("KAPISMA_ROUTE_STEP pass=first step=1\n","",1),r,t)["route_evidence_pass"])
    def test_missing_metrics(self):
        l,r,t=fixture();self.assertFalse(assess(l,r[:-1],t)["route_evidence_pass"])
    def test_thermal_failure(self):
        l,r,t=fixture();t[-1]["status"]="3";self.assertFalse(assess(l,r,t)["route_evidence_pass"])
if __name__=="__main__":unittest.main()
