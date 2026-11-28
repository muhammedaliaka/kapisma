"""Verify existing installed app uses updated on-device helper, then restore."""
import time,json
from test_apps import adb,check,active,disabled,find,tap,tap_node,DUMP
from ram_cpu_acceptance import helper,sample,OUT
def main():
 check();OUT.mkdir(parents=True,exist_ok=True)
 if active():raise RuntimeError("unexpected active mode")
 original=disabled()
 try:
  adb("shell","am","start","-n","tr.kapisma.ultra.server/.MainActivity")
  find("ULTRA Server")
  tap("Durumu kontrol et");find("Sunucu modu kapalı",timeout=30)
  tap("SUNUCU MODUNU AÇ");find("Sunucu modu aktif",timeout=120)
  if not active():raise RuntimeError("no active journal")
  print("UPDATED_APP_ENTER_PASS",flush=True)
  adb("shell","input","keyevent","223");time.sleep(3)
  result=sample("integrated")
  (OUT/"integrated.json").write_text(json.dumps(result,indent=2),encoding="utf-8")
  adb("shell","input","keyevent","224")
  # Re-open the app if needed; no lockscreen bypass.
  adb("shell","am","start","-n","tr.kapisma.ultra.server/.MainActivity")
  close=find("SUNUCU MODUNU KAPAT",timeout=15)
  tap_node(close)
  if not active():raise RuntimeError("first tap prematurely restored")
  tap_node(close)
  find("Sunucu modu kapalı",timeout=120)
  print("UPDATED_APP_DOUBLE_CLOSE_PASS",flush=True)
 finally:
  if active():print(helper("server_mode.sh","--leave").splitlines()[-1],flush=True)
  diff=disabled()^original;print("FINAL_RESTORE_DIFF",len(diff),flush=True)
  if diff:raise RuntimeError("restoration mismatch")
  adb("shell","rm","-f",DUMP)
  adb("shell","input","keyevent","3")
  print("mobile_data="+adb("shell","settings","get","global","mobile_data"),"airplane_mode="+adb("shell","settings","get","global","airplane_mode_on"),flush=True)
if __name__=="__main__":main()
