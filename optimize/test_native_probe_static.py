"""Offline validation of pinned client adapter and bounded command protocol."""
import ast,hashlib,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parent
class NativeFixtureTests(unittest.TestCase):
    def test_pinned_adapter(self):
        probe=(ROOT/"native_android_probe.py").read_text(encoding="utf-8")
        ast.parse(probe)
        adapter=ROOT/"native_client_adapter.py"
        ast.parse(adapter.read_text(encoding="utf-8"))
        self.assertIn(hashlib.sha256(adapter.read_bytes()).hexdigest(),probe)
    def test_origins_required_and_pinned(self):
        adapter=(ROOT/"native_client_adapter.py").read_text(encoding="utf-8")
        self.assertIn("map(str,[API,qa,origins])",adapter)
        self.assertIn("c7e06f8fd49a279d566c3e778ff0c71bbb371f1c4358ab9b2190d1e3b6c36d33",adapter)
    def test_no_public_listener(self):
        probe=(ROOT/"native_android_probe.py").read_text(encoding="utf-8")
        self.assertIn('"--no-rebind"',probe)
        self.assertIn('"127.0.0.1:"+str(PORT)',probe)
        self.assertIn('"--remove","tcp:"+str(PORT)',probe)
    def test_bounded_native_path(self):
        fixture=(ROOT/"native_client_fixture.sh").read_text(encoding="utf-8")
        self.assertNotIn("player ultrabot",fixture)
        self.assertNotIn("debug ack",fixture)
        self.assertIn("host_abort; sample spawn",fixture)
        self.assertIn('sample exploration',fixture)
    def test_mode_gate(self):
        harness=(ROOT/"run_android_stress.sh").read_text(encoding="utf-8")
        self.assertIn('$(property online-mode)',harness)
        self.assertIn('$(property server-ip)" == 127.0.0.1',harness)
        self.assertIn('HARNESS_NATIVE_COMPLETE players=4 exploration_samples=12 performance_certified=false',harness)
class JoinEvidenceTests(unittest.TestCase):
    def setUp(self):
        from native_android_probe import native_join_verified
        self.verify=native_join_verified
        self.log="KodNativeQA1[IP hidden] logged in with entity id 30 at (76.5, 146.0, -15.5)\nKodNativeQA1 joined the game"
        self.chat="[Render thread/INFO]: [System] [CHAT] Welcome"
        self.net="TCP 127.0.0.1:55279 127.0.0.1:25587 ESTABLISHED 17456"
    def test_ip_hidden(self):self.assertTrue(self.verify(self.log,"KodNativeQA1",17456,self.chat,self.net))
    def test_fake_local(self):self.assertFalse(self.verify(self.log.replace("IP hidden","local"),"KodNativeQA1",17456,self.chat,self.net))
    def test_wrong_pid(self):self.assertFalse(self.verify(self.log,"KodNativeQA1",999,self.chat,self.net))
    def test_no_chat(self):self.assertFalse(self.verify(self.log,"KodNativeQA1",17456,"",self.net))
    def test_wrong_endpoint(self):self.assertFalse(self.verify(self.log,"KodNativeQA1",17456,self.chat,self.net.replace("127.0.0.1:25587","192.0.2.1:25587")))
    def test_registry_error(self):self.assertFalse(self.verify(self.log,"KodNativeQA1",17456,self.chat+" Registry remapping failed!",self.net))
if __name__=="__main__":unittest.main()
