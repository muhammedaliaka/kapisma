"""Create separate dual-device app source tree, preserving the old phone APK."""
from pathlib import Path
import shutil
from build_profiles import ROOT,OUT
dst=Path(__file__).parent/"app"
src=ROOT/"phone-cleaner-app"
dst.mkdir(exist_ok=True)
shutil.copytree(src/"src",dst/"src",dirs_exist_ok=True)
m=(src/"AndroidManifest.xml").read_text(encoding="utf-8-sig").replace('android:versionCode="2" android:versionName="1.1"','android:versionCode="3" android:versionName="2.0"').replace('android:label="ULTRA Phone"','android:label="ULTRA Ultimate"')
(dst/"AndroidManifest.xml").write_text(m,encoding="utf-8")
for profile in ("phone","tablet"):
 folder=dst/"assets"/profile;folder.mkdir(parents=True,exist_ok=True)
 for a,b in (("daily.sh","daily.sh"),("server_mode.sh","server.sh"),("p1_cleanup.sh","p1.sh")):shutil.copyfile(OUT/profile/a,folder/b)
p=dst/"src/tr/kapisma/ultraphone/MainActivity.java";s=p.read_text(encoding="utf-8-sig")
s=s.replace('ULTRA PHONE','ULTRA ULTIMATE')
s=s.replace('EditText port; TextView result;','EditText port; TextView result; Spinner device;')
s=s.replace('port=new EditText(this);','device=new Spinner(this); device.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Telefon","Tablet"})); root.addView(device);\n        port=new EditText(this);')
s=s.replace('String script=asset(mode.equals("daily")?"daily.sh":mode.equals("p1")?"p1.sh":"server.sh");','String profile=device.getSelectedItemPosition()==0?"phone":"tablet";\n            String expected=profile.equals("phone")?"HM59FYLJEY59M7O7":"SCTB19202412160567";\n            String script=asset(profile+"/"+(mode.equals("daily")?"daily.sh":mode.equals("p1")?"p1.sh":"server.sh"));')
old='shell getprop ro.product.model | tr -d \'\\\\r\')\\" = 23090RA98I;'
new='shell getprop ro.serialno | tr -d \'\\\\r\')\\" = "+expected+";'
if old not in s:raise ValueError("serial check template changed")
s=s.replace(old,new)
p.write_text(s,encoding="utf-8")
print("Dual-profile app source staged")
