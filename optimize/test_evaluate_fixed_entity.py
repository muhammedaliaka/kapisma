import unittest
from evaluate_fixed_entity import assess, SPECIES, MARK
from test_evaluate_carpet_profile import P
def fixture():
    l=[]
    def mark(s):l.append(P+"[Not Secure] [Server] "+s)
    l.append(P+"No force loaded chunks were found in minecraft:overworld")
    mark("KAPISMA_FIXTURE_EMPTY_TICKETS");mark("KAPISMA_FIXTURE_LOADED")
    for i in range(4):
        mark(f"KAPISMA_FIXTURE_ROOM_{i}");mark(f"KAPISMA_FIXTURE_CAGE_{i}")
    mark("KAPISMA_FIXTURE_PLAYERS_BARRIER")
    for s in SPECIES:mark("KAPISMA_FIXTURE_SUMMON_"+s)
    for label in ("before","health","entities"):
        if label!="before":
            mark(f"KAPISMA_PROFILE_BEGIN kind={label} ticks=100")
            l.append(P+"Average tick time: 30.0ms")
            if label=="health":l.extend([P+"overworld:",P+"- Entities: 20.0ms",P+"The Rest, whatever that might be: 10.0ms"])
            else:
                l.append(P+"Top 10 counts:")
                l.extend(P+f"- {s} in overworld: 16.0" for s in SPECIES)
                l.append(P+"- player in overworld: 4.0")
                l.append(P+"Top 10 CPU hogs:")
                l.extend(P+f"- {s} in overworld: 4.0ms" for s in SPECIES)
                l.append(P+"- player in overworld: 4.0ms")
            mark("KAPISMA_PROFILE_END kind="+label)
        l.extend(P+f"{s}_{label} has 16 [ultraFixture]" for s in SPECIES)
        for n in range(1,5):mark(f"KAPISMA_FIXTURE_PLAYER_{label}_{n}")
        mark("KAPISMA_FIXTURE_NO_EXTRA_"+label);mark("KAPISMA_FIXTURE_COUNTS_"+label)
    l.append(P+"No force loaded chunks were found in minecraft:overworld");mark("KAPISMA_FIXTURE_TICKETS_RELEASED")
    l.extend([P+"All dimensions are saved","server_mode=restored",MARK,"HARNESS_CARPET_PROFILE_COMPLETE health_ticks=100 entities_ticks=100 performance_certified=false","exit=0"])
    phases=("idle","lobby","exploration","exploration")
    rows=[dict(phase=p,timestamp=str(110+i*10),rss_kib="1000",available_kib="3000") for i,p in enumerate(phases)]
    q=[dict(phase=p,start_uptime_ms=str(10000+i*5000),elapsed_ms="1200",result="ok") for i,p in enumerate(phases)]
    t=[dict(phase="prelaunch",timestamp="100",status="0")]+[dict(phase=r["phase"],timestamp=str(int(r["timestamp"])-1),status="0") for r in rows]
    m=dict(fixed_entity="1",fixture_world_seed="20261126",fixture_level_type="flat",view="3",simulation="3",profile_mobcap="70",carpet_profile="1",route_mode="0",fixed_heap_mib="2048",jfr="0")
    m.update({k:"a"*64 for k in ("fixture_sha256","candidate_sha256","config_except_view_sha256","modset_sha256","script_sha256")})
    return "\n".join(l),rows,q,t,"\n".join(k+"="+v for k,v in m.items())
class FixedEntityTests(unittest.TestCase):
    def test_good(self):
        r=assess(*fixture());self.assertTrue(r["fixture_evidence_pass"],r);self.assertFalse(r["performance_certified"])
    def test_missing_mob(self):
        l,*a=fixture();self.assertFalse(assess(l.replace("zombie_health has 16","zombie_health has 15"),*a)["fixture_evidence_pass"])
    def test_profile_mismatch(self):
        l,*a=fixture();self.assertFalse(assess(l.replace("zombie in overworld: 16.0","zombie in overworld: 15.9"),*a)["fixture_evidence_pass"])
    def test_wrong_mode(self):
        l,*a=fixture();self.assertFalse(assess(l+"\nUHC başladı!",*a)["fixture_evidence_pass"])
    def test_extra(self):
        l,*a=fixture();self.assertFalse(assess(l.replace("KAPISMA_FIXTURE_NO_EXTRA_health","failed"),*a)["fixture_evidence_pass"])
    def test_bad_order(self):
        l,*a=fixture();self.assertFalse(assess(l.replace("KAPISMA_FIXTURE_ROOM_1","KAPISMA_FIXTURE_ROOM_3").replace("KAPISMA_FIXTURE_ROOM_2","KAPISMA_FIXTURE_ROOM_1"),*a)["fixture_evidence_pass"])
if __name__=="__main__":unittest.main()
