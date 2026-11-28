import unittest
from evaluate_map_parity import assess

def fixture():
    lines=["ULTRA_MAP_MODE incremental=true"]
    for i in range(1,5):
        lines.extend(["ULTRA_MAP_PARITY pixels=16384 scale=2 matched=true",f"ULTRA_MAP_COMPLETE kind=treasure index={i}"])
    lines.extend(["ULTRA_MAP_PARITY pixels=16384 scale=4 matched=true","ULTRA_MAP_COMPLETE kind=region",
                  "UHC başladı!","HARNESS_MAP_PARITY_COMPLETE treasure=4 region=1 performance_certified=false",
                  "Stopping server","All dimensions are saved","server_mode=restored","exit=0"])
    return "\n".join(lines)

class ParityGateTests(unittest.TestCase):
    def test_complete(self):
        r=assess(fixture())
        self.assertTrue(r["map_parity_pass"])
        self.assertFalse(r["performance_certified"])
        self.assertEqual(r["pixels_compared"],81920)
    def test_missing_map(self):
        self.assertFalse(assess(fixture().replace("ULTRA_MAP_PARITY pixels=16384 scale=2 matched=true\n","",1))["map_parity_pass"])
    def test_duplicate(self):
        self.assertFalse(assess(fixture()+"\nULTRA_MAP_COMPLETE kind=region")["map_parity_pass"])
    def test_incorrect_pixel(self):
        self.assertFalse(assess(fixture().replace("matched=true","matched=false",1))["map_parity_pass"])
    def test_no_save(self):
        self.assertFalse(assess(fixture().replace("All dimensions are saved",""))["map_parity_pass"])
    def test_abort(self):
        self.assertFalse(assess(fixture()+"\nHARNESS_ABORT reason=timeout")["map_parity_pass"])
if __name__=="__main__": unittest.main()
