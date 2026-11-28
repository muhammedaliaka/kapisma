package tr.kapisma.ultraphone;
import android.content.*;
import android.os.Bundle;
public final class ResultReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  if(!"tr.kapisma.ultraphone.RESULT".equals(i.getAction()))return;
  android.content.SharedPreferences p=c.getSharedPreferences(MainActivity.PREF,0);
  String token=i.getStringExtra("token"); if(token==null||!token.equals(p.getString("token","")))return;
  Bundle b=i.getBundleExtra("result");
  String s=b==null?"Termux sonucu alınamadı; başarı doğrulanmadı.":("Exit: "+b.get("exitCode")+"\n"+b.getString("stdout","")+"\n"+b.getString("stderr","")+"\n"+b.getString("errmsg",""));
  p.edit().putLong("start",0).remove("token").putString("output",s).apply();
 }
}