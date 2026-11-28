import unittest
from evaluate_native_probe import assess,progress,NAMES,MARK
def fixture():
    lines=[];events=[{"event":"forward_verified"}];clients={}
    for i,n in enumerate(NAMES,1):
        events.extend([dict(event="client_started",name=n,pid=100+i),dict(event="client_joined",name=n,pid=100+i,socket_owned=True,client_chat=True)])
        lines.extend([f"{n}[IP hidden] logged in with entity id {i}",f"{n} joined the game"])
        clients[n]="\n".join(f"AIQA_WALK {t*20} {t*2}.0 64.0 1.0" for t in range(1,6))
    lines.extend([f"ULTRA_MAP_COMPLETE kind=treasure index={i}" for i in range(1,5)])
    lines.extend(["ULTRA_MAP_COMPLETE kind=region","UHC başladı!","[Server] KAPISMA_NATIVE_WALK_BEGIN"])
    for t in range(5):
        for n in NAMES:lines.append(f"{n} has the following entity data: [{t*2}.0d, 64.0d, 1.0d]")
    lines.extend(["anti-xray: gönderilen=100 chunk=12 | açığa: 8 tarama","koruma: degerlendirme=10","[Server] KAPISMA_NATIVE_WALK_END","All dimensions are saved","server_mode=restored",MARK,"exit=0"])
    events.extend([dict(event="walk_started"),dict(event="server_complete")]+[dict(event="client_closed",name=n) for n in NAMES]+[dict(event="forward_removed"),dict(event="host_complete",diagnostic_complete=True)])
    phases=["idle","lobby","spawn"]+["exploration"]*12
    rows=[dict(phase=p,timestamp=str(110+i*10),rss_kib="1000",available_kib="3000") for i,p in enumerate(phases)]
    q=[dict(phase=p,start_uptime_ms=str(10000+i*5000),elapsed_ms="1200",result="ok") for i,p in enumerate(phases)]
    thermal=[dict(phase="prelaunch",timestamp="100",status="0")]+[dict(phase=r["phase"],timestamp=str(int(r["timestamp"])-1),status="0") for r in rows]
    return "\n".join(lines),rows,q,thermal,events,clients
class NativeEvidenceTests(unittest.TestCase):
    def test_complete(self):
        r=assess(*fixture());self.assertTrue(r["diagnostic_evidence_pass"],r);self.assertTrue(r["movement_and_packet_coverage_pass"],r);self.assertFalse(r["performance_certified"])
    def test_carpet_rejected(self):
        l,*a=fixture();self.assertFalse(assess(l.replace("[IP hidden]","[local]"),*a)["diagnostic_evidence_pass"])
    def test_missing_packet(self):
        l,*a=fixture();r=assess(l.replace("chunk=12","chunk=0"),*a);self.assertTrue(r["diagnostic_evidence_pass"]);self.assertFalse(r["movement_and_packet_coverage_pass"])
    def test_teleport_not_progress(self):
        self.assertEqual(progress([(0,64,0),(100,64,0),(102,64,0),(102,64,0)]),1)
    def test_missing_socket(self):
        a=list(fixture());next(e for e in a[4] if e["event"]=="client_joined")["socket_owned"]=False;self.assertFalse(assess(*a)["diagnostic_evidence_pass"])
    def test_host_failure(self):
        a=list(fixture());a[4].append({"event":"failure"});self.assertFalse(assess(*a)["diagnostic_evidence_pass"])
if __name__=="__main__":unittest.main()
