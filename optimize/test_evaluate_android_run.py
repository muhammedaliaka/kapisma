import unittest
from evaluate_android_run import assess,PHASES

LOG="There are 4 of a max of 4 players online\nUHC başladı!\nStopping server\nAll dimensions are saved\nserver_mode=restored\nexit=0\n"
def rows():
    return [dict(phase=p,response="Average time per tick: 15.0ms P95: 25.0ms P99: 45.0ms",
        available_kib="1048576",rss_kib="1000000",battery_deci_c="200") for p in sorted(PHASES)]
class GateTests(unittest.TestCase):
    def test_clean(self):
        self.assertTrue(assess(LOG,rows())["sample_gate_pass"])
        self.assertFalse(assess(LOG,rows())["human_certified"])
    def test_stall(self):
        self.assertFalse(assess(LOG+"Can't keep up! Running 13473ms",rows())["sample_gate_pass"])
    def test_missing_telemetry(self):
        r=rows(); r[0]["rss_kib"]="UNKNOWN"
        self.assertFalse(assess(LOG,r)["sample_gate_pass"])
    def test_bad_percentile(self):
        r=rows(); r[0]["response"]="Average time per tick: 10ms P95: 20ms P99: 1000ms"
        self.assertFalse(assess(LOG,r)["sample_gate_pass"])
    def test_incomplete(self):
        self.assertFalse(assess(LOG,rows()[:-1])["sample_gate_pass"])
        self.assertFalse(assess(LOG.replace("server_mode=restored",""),rows())["sample_gate_pass"])
    def test_uninitialized_stats(self):
        r=rows(); r[0]["response"]="Average time per tick: 0ms P95: 0ms P99: 0ms"
        self.assertFalse(assess(LOG,r)["sample_gate_pass"])
    def test_crash_with_restoration(self):
        r=assess(LOG.replace("exit=0","exit=5")+"Segmentation fault",rows())
        self.assertIn("normal stop not proven",r["failures"])
        self.assertNotIn("restore not proven",r["failures"])
        self.assertFalse(r["sample_gate_pass"])
    def test_timeout_can_save_cleanly(self):
        r=assess(LOG.replace("exit=0","exit=7"),rows())
        self.assertFalse(r["scenario_exit_ok"])
        self.assertTrue(r["clean_shutdown_logged"])
        self.assertTrue(r["restore_logged"])
        self.assertFalse(r["sample_gate_pass"])
    def test_exit_zero_without_save_is_not_success(self):
        r=assess(LOG.replace("All dimensions are saved",""),rows())
        self.assertFalse(r["sample_gate_pass"])
        self.assertFalse(r["clean_shutdown_logged"])
    def test_stronger_gate_rejects_minimal_samples(self):
        r=assess(LOG,rows())
        self.assertTrue(r["sample_gate_pass"])
        self.assertFalse(r["scenario_gate_pass"])
        self.assertIn("insufficient samples: exploration",r["scenario_failures"])
    def test_stronger_gate_complete(self):
        from test_analyze_bot_activity import fixture
        full=[]
        for p,n in (("idle",1),("lobby",1),("spawn",1),("exploration",12),("pvp",4)):
            for _ in range(n):
                row=rows()[0].copy();row.update(phase=p,timestamp=str(100+len(full)));full.append(row)
        log=LOG+fixture()+"\nHARNESS_SCENARIO_COMPLETE\n"
        thermal=[dict(timestamp="99",phase="prelaunch",status="0")]+[
            dict(timestamp=r["timestamp"],phase=r["phase"],status="0") for r in full]
        self.assertTrue(assess(log,full,thermal)["scenario_gate_pass"])
        tagged=log+"\nHARNESS_QUERY_LATENCY_V1\n"
        queries=[dict(phase=r["phase"],start_uptime_ms=str(i*5000),elapsed_ms="1200",result="ok") for i,r in enumerate(full)]
        self.assertFalse(assess(tagged,full,thermal)["scenario_gate_pass"])
        self.assertTrue(assess(tagged,full,thermal,queries)["scenario_gate_pass"])
        queries[-1]["elapsed_ms"]="4000"
        self.assertFalse(assess(tagged,full,thermal,queries)["scenario_gate_pass"])
        self.assertFalse(assess(log,full)["scenario_gate_pass"])
        full[-1]["timestamp"]=full[-2]["timestamp"]
        self.assertFalse(assess(log,full,thermal)["scenario_gate_pass"])
    def test_native_error(self):
        self.assertFalse(assess(LOG+"java.lang.UnsatisfiedLinkError",rows())["sample_gate_pass"])
if __name__=="__main__": unittest.main()
