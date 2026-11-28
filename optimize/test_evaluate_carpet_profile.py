import unittest
from evaluate_carpet_profile import assess,MARK
P="[12:00:00] [Server thread/INFO]: "
def fixture():
    return "\n".join(P+s for s in (
        "[Not Secure] [Server] KAPISMA_PROFILE_BEGIN kind=health ticks=100",
        "Average tick time: 90.0ms","Async Tasks: 2.0ms","fantasy:arena:","- Entities: 80.0ms",
        "The Rest, whatever that might be: 8.0ms","[Not Secure] [Server] KAPISMA_PROFILE_END kind=health",
        "[Not Secure] [Server] KAPISMA_PROFILE_BEGIN kind=entities ticks=100",
        "Average tick time: 100.0ms","Top 10 counts:","- zombie in fantasy:arena: 40.0",
        "Top 10 CPU hogs:","- zombie in fantasy:arena: 20.0ms",
        "[Not Secure] [Server] KAPISMA_PROFILE_END kind=entities","All dimensions are saved"
    ))+"\nserver_mode=restored\n"+MARK+"\nexit=0\n"
class ProfileTests(unittest.TestCase):
    def test_complete(self):
        r=assess(fixture());self.assertTrue(r["evidence_pass"],r);self.assertFalse(r["performance_certified"]);self.assertEqual(r["profiles"]["health"]["sections"]["fantasy:arena/Entities"],80)
    def test_missing_counts(self):self.assertFalse(assess(fixture().replace("- zombie in fantasy:arena: 40.0","missing"))["evidence_pass"])
    def test_missing_end(self):self.assertFalse(assess(fixture().replace("KAPISMA_PROFILE_END kind=entities","missing"))["evidence_pass"])
    def test_duplicate_mean(self):self.assertFalse(assess(fixture().replace("Average tick time: 90.0ms","Average tick time: 90.0ms\n"+P+"Average tick time: 90.0ms"))["evidence_pass"])
    def test_wrong_unit(self):self.assertFalse(assess(fixture().replace("40.0\n","40.0ms\n"))["evidence_pass"])
    def test_missing_restore(self):self.assertFalse(assess(fixture().replace("server_mode=restored",""))["evidence_pass"])
if __name__=="__main__":unittest.main()
