import unittest
from analyze_bot_activity import assess,EXPECTED

def fixture(times=(0,10)):
    lines=["UHC başladı!"]
    for t in times:
        for name,(vx,vz,yaw) in EXPECTED.items():
            lines += [f"{name} has the following entity data: [{vx*t}.0d, 65.0d, {vz*t}.0d]",
                      f"{name} has the following entity data: [{yaw}.0f, 0.0f]",
                      f"{name} has the following entity data: 0"]
    return "\n".join(lines+["Teleported ultrabot2 to ultrabot1"])

class ActivityTests(unittest.TestCase):
    def test_all_four(self):
        self.assertTrue(assess(fixture())["all_four_observed_movement"])
    def test_wrong_direction(self):
        self.assertFalse(assess(fixture().replace("[-90.0f, 0.0f]","[0.0f, 0.0f]"))["all_four_observed_movement"])
    def test_incomplete(self):
        self.assertFalse(assess(fixture().replace("Teleported ultrabot2 to ultrabot1",""))["all_four_observed_movement"])
    def test_no_spectator(self):
        self.assertFalse(assess(fixture().replace("ultrabot4 has the following entity data: 0","ultrabot4 has the following entity data: 3"))["all_four_observed_movement"])
    def test_six_trace_checkpoints(self):
        r=assess(fixture((0,10,20,30,40,50)))
        self.assertTrue(r["all_four_trace_coverage"])
        self.assertTrue(r["all_four_sustained_sampled_movement"])
    def test_two_points_not_full_trace(self):
        r=assess(fixture())
        self.assertTrue(r["all_four_observed_movement"])
        self.assertFalse(r["all_four_trace_coverage"])
    def test_only_one_moving_segment(self):
        r=assess(fixture((0,0,0,0,0,10)))
        self.assertTrue(r["all_four_trace_coverage"])
        self.assertFalse(r["all_four_sustained_sampled_movement"])
    def test_movement_end_without_teleport(self):
        r=assess(fixture((0,10,20,30,40,50)).replace("Teleported ultrabot2 to ultrabot1","[Server] KAPISMA_MOVEMENT_END"))
        self.assertTrue(r["all_four_sustained_sampled_movement"])
    def test_teleport_excluded(self):
        self.assertEqual(assess(fixture()+"\nultrabot1 has the following entity data: [0.0d, 65.0d, 500.0d]")["players"]["ultrabot1"]["dz"],-10)
if __name__=="__main__": unittest.main()
