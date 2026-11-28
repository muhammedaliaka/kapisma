import unittest
from evaluate_native_route import assess,NATIVE_MARK,NAMES
P="[12:00:00] [Server thread/INFO]: "
def fixture():
    l=[]
    def mark(s):l.append(P+"[Not Secure] [Server] "+s)
    dim="fantasy:fixture"
    mark("KAPISMA_NATIVE_ROUTE_DIMENSION")
    l.append(P+"No force loaded chunks were found in "+dim);mark("KAPISMA_NATIVE_ROUTE_EMPTY")
    mark("KAPISMA_NATIVE_ROUTE_TICKETS");mark("KAPISMA_NATIVE_ROUTE_BUILT")
    for x in range(-64,160,16):
        for z in (-57,-45,-33,-21):mark(f"KAPISMA_ROUTE_LANE_{x}_{z}")
    mark("KAPISMA_NATIVE_ROUTE_VERIFIED");mark("KAPISMA_NATIVE_ROUTE_PLACED")
    for i in range(1,5):mark(f"KAPISMA_ROUTE_PLAYER_{i}")
    mark("KAPISMA_NATIVE_ROUTE_PLAYERS");l.append(P+"No force loaded chunks were found in "+dim);mark("KAPISMA_NATIVE_ROUTE_RELEASED")
    mark(f"HARNESS_NATIVE_ROUTE_SETUP dimension={dim} lanes=4 chunks=42 setup_teleports=4 tickets_released=true")
    mark("KAPISMA_NATIVE_WALK_READY");mark("KAPISMA_NATIVE_WALK_BEGIN")
    for t in range(13):
        for i,n in enumerate(NAMES):
            stamp=f"[12:{(t*7)//60:02}:{(t*7)%60:02}] [Server thread/INFO]: "
            l.append(stamp+f"{n} has the following entity data: [{-59.5+t*3}d, 240.0d, {-57.5+i*12}d]")
            l.append(P+f"{n} has the following entity data: 0")
            l.append(P+f'{n} has the following entity data: "{dim}"')
    mark("KAPISMA_NATIVE_WALK_END")
    for kind in ("health","entities"):
        mark("KAPISMA_PROFILE_BEGIN kind="+kind+" ticks=100")
        l.append(P+"Average tick time: 25.0ms")
        if kind=="health":
            l.extend([P+dim+":",P+"- Entities: 10.0ms",P+"The Rest, whatever that might be: 15.0ms"])
        else:
            l.extend([P+"Top 10 counts:",P+"- zombie in "+dim+": 12.0",P+"Top 10 CPU hogs:",P+"- zombie in "+dim+": 5.0ms"])
        mark("KAPISMA_PROFILE_END kind="+kind)
    l.append(P+"koruma: aktif(overworld)=false zorla=null aralik=8 degerlendirme=80 gizleme=40 ihlal={}")
    mark("KAPISMA_NATIVE_PROFILE_COMPLETE")
    l.extend([P+"All dimensions are saved","server_mode=restored",NATIVE_MARK,"exit=0"])
    return "\n".join(l)
class RouteEvidenceTests(unittest.TestCase):
    def test_complete(self):
        r=assess(fixture());self.assertTrue(r["route_evidence_pass"],r);self.assertFalse(r["performance_certified"])
    def test_missing_lane(self):self.assertFalse(assess(fixture().replace("KAPISMA_ROUTE_LANE_-64_-57","wrong"))["route_evidence_pass"])
    def test_wrong_mode(self):self.assertFalse(assess(fixture().replace("KodNativeQA1 has the following entity data: 0","KodNativeQA1 has the following entity data: 1"))["route_evidence_pass"])
    def test_guard_zero(self):self.assertFalse(assess(fixture().replace("gizleme=40","gizleme=0"))["route_evidence_pass"])
    def test_teleport(self):self.assertFalse(assess(fixture().replace("KAPISMA_NATIVE_WALK_BEGIN","KAPISMA_NATIVE_WALK_BEGIN\n"+P+"Teleported KodNativeQA1"))["route_evidence_pass"])
    def test_missing_profile(self):self.assertFalse(assess(fixture().replace("Top 10 CPU hogs:","missing"))["route_evidence_pass"])
if __name__=="__main__":unittest.main()
