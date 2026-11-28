import unittest, json, hashlib, re, subprocess
from pathlib import Path
from build_profiles import ROOT,OUT,PROFILES,build
from ultimate import manifest
class UltimateTests(unittest.TestCase):
 def test_manifest_phone(self):self.assertEqual(manifest("phone")[0]["serial"],"HM59FYLJEY59M7O7")
 def test_manifest_tablet(self):self.assertEqual(manifest("tablet")[0]["serial"],"SCTB19202412160567")
 def test_distinct_journal(self):self.assertNotEqual(PROFILES["phone"]["state"],PROFILES["tablet"]["state"])
 def test_reproducible(self):
  before={p:manifest(p)[0]["sha256"] for p in PROFILES}
  build()
  self.assertEqual(before,{p:manifest(p)[0]["sha256"] for p in PROFILES})
 def test_guard_every_helper(self):
  for p,m in PROFILES.items():
   for n in ("server_mode.sh","p1_cleanup.sh","daily.sh"):
    s=(OUT/p/n).read_text()
    self.assertIn('getprop ro.serialno',s);self.assertIn(m["serial"],s);self.assertIn(m["state"],s)
 def test_allowlist_protects_critical(self):
  for p in PROFILES:
   s=(OUT/p/"server_mode.sh").read_text()
   a=set(re.search(r"(?m)^allow='([^']+)'",s)[1].split())
   self.assertEqual(len(a),53)
   self.assertFalse(a & set("com.termux com.whatsapp com.google.android.gms com.google.android.googlequicksearchbox com.google.android.inputmethod.latin com.miui.miwallpaper com.android.phone com.android.systemui com.miui.securitycenter com.turktelekom.bimcell.androidapp io.heckel.ntfy".split()))
 def test_no_blanket_kill(self):
  for p in PROFILES:
   for n in ("server_mode.sh","p1_cleanup.sh","daily.sh"):
    s=(OUT/p/n).read_text();self.assertNotIn("am kill-all",s);self.assertNotIn("svc data",s);self.assertNotIn("settings put",s)
 def test_exact_uid_check(self):
  for p in PROFILES:
   s=(OUT/p/"server_mode.sh").read_text()
   self.assertIn('ps -A -o UID,NAME',s)
   self.assertIn('skip shared uid',s)
 def test_mutation_lock(self):
  for p in PROFILES:
   for n in ("server_mode.sh","p1_cleanup.sh","daily.sh"):
    s=(OUT/p/n).read_text();self.assertIn("mutation-lock",s);self.assertIn('mkdir "$lock"',s)
 def test_legacy_recovery(self):
  for p in PROFILES:
   s=(OUT/p/"server_mode.sh").read_text();self.assertIn("pm default-state --user 0",s);self.assertIn("pm enable --user 0",s);self.assertIn("Java still running; restore refused",s)
 def test_profile_assets(self):
  for p in PROFILES:
   for a,b in (("daily.sh","daily.sh"),("p1_cleanup.sh","p1.sh"),("server_mode.sh","server.sh")):
    self.assertEqual((OUT/p/a).read_bytes(),(OUT.parent/"app/assets"/p/b).read_bytes())
 def test_launch_budget_fixture(self):
  bash=Path(r"C:\Program Files\Git\bin\bash.exe")
  for p,fixture,heap in (("phone","phone-policy-acceptance-sample-3-20261127.txt",3072),("tablet","android-ram-deep-proc-20261126-171433.txt",1920)):
   o=subprocess.check_output([str(bash),str(OUT/p/"launch.sh"),"--plan",str(ROOT/fixture)],text=True)
   self.assertIn("heap_mib="+str(heap),o)
if __name__=="__main__":unittest.main(verbosity=2)
