import io,json,hashlib,struct,unittest,zipfile
from verify_performance_mod import inspect_blob
def fixture(id="lithium",major=65,env="*"):
    b=io.BytesIO()
    with zipfile.ZipFile(b,"w") as z:
        z.writestr("fabric.mod.json",json.dumps(dict(id=id,version="1",environment=env)))
        z.writestr("Test.class",b"\xca\xfe\xba\xbe"+struct.pack(">HH",0,major))
    data=b.getvalue()
    return data,dict(id="lithium",bytes=len(data),sha512=hashlib.sha512(data).hexdigest())
class IntakeTests(unittest.TestCase):
    def test_ok(self):
        r=inspect_blob(*fixture());self.assertTrue(r["intake_pass"]);self.assertFalse(r["runtime_certified"])
    def test_bad_hash(self):
        data,m=fixture();m["sha512"]="0"*128;self.assertFalse(inspect_blob(data,m)["intake_pass"])
    def test_wrong_id(self):self.assertFalse(inspect_blob(*fixture(id="wrong"))["intake_pass"])
    def test_java22(self):self.assertFalse(inspect_blob(*fixture(major=66))["intake_pass"])
    def test_client_only(self):self.assertFalse(inspect_blob(*fixture(env="client"))["intake_pass"])
if __name__=="__main__":unittest.main()
