package tr.kapisma.ultraphone;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MainActivity extends Activity {
    static final String PERM="com.termux.permission.RUN_COMMAND";
    static final String PREF="ultra_result";
    EditText port; TextView result; Spinner device;
    final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->refresh();
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);
        root.setOrientation(1); root.setPadding(32,40,32,32); scroll.addView(root); setContentView(scroll);
        TextView title=new TextView(this);title.setText("ULTRA ULTIMATE");title.setTextSize(30);root.addView(title);
        TextView desc=new TextView(this);desc.setText("Günlük temizlik • Sunucu modu\nRoot yok. Uygulama verileri silinmez.\nTermux ve önceden eşleştirilmiş yerel ADB gerekir.");root.addView(desc);
        device=new Spinner(this); device.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Telefon","Tablet"})); root.addView(device);
        port=new EditText(this);port.setHint("Yerel ADB bağlantı portu");port.setInputType(2);
        port.setText(getPreferences(0).getString("port",""));root.addView(port);
        button(root,"Günlük temizlik",()->run("daily","--apply"));
        button(root,"Sunucu modu ön kontrolü",()->run("server","--plan"));
        button(root,"Sunucu modu durumu",()->run("server","--status"));
        button(root,"Sunucu modu P1 bellek temizliği",()->run("p1","--apply"));
        button(root,"Sunucu modundan güvenli çıkış",()->new AlertDialog.Builder(this).setTitle("Geri yükleme").setMessage("Yalnız sunucu kapalıysa kullan. Önceki uygulama durumları geri yüklenir.").setNegativeButton("Vazgeç",null).setPositiveButton("Geri yükle",(d,w)->run("server","--leave")).show());
        TextView note=new TextView(this);note.setText("Sunucu modu otomatik başlatıcıya bağlanmadan burada etkinleştirilemez.\nİlk kullanım: Termux RUN_COMMAND izni ve mevcut yerel ADB bağlantısı gerekir. Başka uygulamalara bu izni verme.");root.addView(note);
        result=new TextView(this);result.setTextIsSelectable(true);result.setTextSize(13);root.addView(result);refresh();
    }
    void button(LinearLayout l,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());l.addView(b);}
    @Override protected void onResume(){super.onResume();getSharedPreferences(PREF,0).registerOnSharedPreferenceChangeListener(listener);refresh();}
    @Override protected void onPause(){getSharedPreferences(PREF,0).unregisterOnSharedPreferenceChangeListener(listener);super.onPause();}
    void refresh(){if(result!=null)result.setText(getSharedPreferences(PREF,0).getString("output","Henüz işlem yok."));}
    String asset(String name)throws IOException{try(InputStream i=getAssets().open(name);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=i.read(b))!=-1)o.write(b,0,n);return o.toString("UTF-8");}}
    String quote(String s){return "'"+s.replace("'","'\"'\"'")+"'";}
    void run(String mode,String action){
        String raw=port.getText().toString().trim();
        if(!raw.matches("[0-9]{1,5}")||Integer.parseInt(raw)<1||Integer.parseInt(raw)>65535){result.setText("Geçerli yerel ADB portu gir.");return;}
        if(checkSelfPermission(PERM)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{PERM},12);result.setText("Termux komut iznini onayla; sonra işlemi tekrar seç.");return;}
        android.content.SharedPreferences p=getSharedPreferences(PREF,0);
        long start=p.getLong("start",0);if(start>0&&System.currentTimeMillis()-start<90000){result.setText("Önceki işlem henüz sonuçlanmadı.");return;}
        try{
            String profile=device.getSelectedItemPosition()==0?"phone":"tablet";
            String expected=profile.equals("phone")?"HM59FYLJEY59M7O7":"SCTB19202412160567";
            String script=asset(profile+"/"+(mode.equals("daily")?"daily.sh":mode.equals("p1")?"p1.sh":"server.sh"));
            String serial="127.0.0.1:"+Integer.parseInt(raw);
            String cmd="set -e; command -v adb >/dev/null; test \"$(timeout 8 adb -s "+serial+" get-state)\" = device; test \"$(timeout 8 adb -s "+serial+" shell getprop ro.serialno | tr -d '\\r')\" = "+expected+"; printf %s "+quote(script)+" | timeout 75 adb -s "+serial+" shell sh -s -- "+action;
            String token=UUID.randomUUID().toString();
            getPreferences(0).edit().putString("port",raw).apply();
            p.edit().putString("token",token).putLong("start",System.currentTimeMillis()).putString("output","İşlem gönderildi; gerçek sonuç bekleniyor.").apply();
            Intent callback=new Intent(this,ResultReceiver.class).setAction("tr.kapisma.ultraphone.RESULT").putExtra("token",token);
            PendingIntent pi=PendingIntent.getBroadcast(this,0,callback,PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_MUTABLE|PendingIntent.FLAG_ONE_SHOT);
            Intent i=new Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService");
            i.putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-c",cmd});
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND",true);
            i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",pi);
            startService(i);
        }catch(Exception e){p.edit().putLong("start",0).putString("output","Başlatma başarısız: "+e.getClass().getSimpleName()+". Termux ayarlarını kontrol et.").apply();}
    }
}
