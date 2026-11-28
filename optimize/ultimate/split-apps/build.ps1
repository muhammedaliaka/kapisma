$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$sdk="$env:LOCALAPPDATA\Android\Sdk"; $bt="$sdk\build-tools\36.0.0"; $j=Join-Path $env:JAVA_HOME 'bin'; $android="$sdk\platforms\android-35\android.jar"
function Checked { param([scriptblock]$cmd) & $cmd; if($LASTEXITCODE -ne 0){throw "native command failed $LASTEXITCODE"} }
$b=Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force "$b/bridge/classes","$b/bridge/dex" | Out-Null
Checked { & "$j\javac.exe" --release 8 -encoding UTF-8 -classpath $android -d "$b/bridge/classes" Bridge.java }
$cls=@(Get-ChildItem "$b/bridge/classes" -Recurse -Filter *.class | ForEach-Object {$_.FullName})
Checked { & "$bt\d8.bat" --lib $android --min-api 26 --output "$b/bridge/dex" @cls }
Checked { & "$j\jar.exe" cf "$b/bridge.jar" -C "$b/bridge/dex" classes.dex }
$enc=New-Object System.Text.UTF8Encoding($false)
$template=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'MainActivity.java.in'))
foreach($type in @('cleaner','server')){
 $id="tr.kapisma.ultra.$type"; $label=if($type -eq 'cleaner'){'ULTRA Cleaner'}else{'ULTRA Server'}
 $isServer=if($type -eq 'server'){'true'}else{'false'}
 $dir="$b/$type";New-Item -ItemType Directory -Force "$dir/classes","$dir/dex","$dir/res/drawable" | Out-Null
 $source=$template.Replace('PACKAGE_ID',$id).Replace('SERVER_VALUE',$isServer)
 [IO.File]::WriteAllText("$dir/MainActivity.java",$source,$enc)
 $bg=if($type -eq 'cleaner'){'#207D69'}else{'#345B87'}
 $shape=if($type -eq 'cleaner'){'M5,16 L13,8 L16,11 L8,19 Z M12,7 L15,4 L20,9 L17,12 Z'}else{'M5,5 L19,5 L19,10 L5,10 Z M5,13 L19,13 L19,18 L5,18 Z'}
 $icon="<vector xmlns:android=`"http://schemas.android.com/apk/res/android`" android:width=`"48dp`" android:height=`"48dp`" android:viewportWidth=`"24`" android:viewportHeight=`"24`"><path android:fillColor=`"$bg`" android:pathData=`"M0,0 H24 V24 H0 Z`"/><path android:fillColor=`"#FFFFFF`" android:pathData=`"$shape`"/></vector>"
 [IO.File]::WriteAllText("$dir/res/drawable/ic_launcher.xml",$icon,$enc)
 $manifest="<manifest xmlns:android=`"http://schemas.android.com/apk/res/android`" package=`"$id`" android:versionCode=`"1`" android:versionName=`"1.0`"><uses-sdk android:minSdkVersion=`"26`" android:targetSdkVersion=`"35`"/><uses-permission android:name=`"android.permission.INTERNET`"/><application android:label=`"$label`" android:icon=`"@drawable/ic_launcher`" android:allowBackup=`"false`" android:theme=`"@android:style/Theme.Material.NoActionBar`"><activity android:name=`".MainActivity`" android:exported=`"true`"><intent-filter><action android:name=`"android.intent.action.MAIN`"/><category android:name=`"android.intent.category.LAUNCHER`"/></intent-filter></activity></application></manifest>"
 [IO.File]::WriteAllText("$dir/AndroidManifest.xml",$manifest,$enc)
 Checked { & "$j\javac.exe" --release 8 -encoding UTF-8 -classpath $android -d "$dir/classes" "$dir/MainActivity.java" }
 $cls=@(Get-ChildItem "$dir/classes" -Recurse -Filter *.class | ForEach-Object {$_.FullName})
 Checked { & "$bt\d8.bat" --lib $android --min-api 26 --output "$dir/dex" @cls }
 Checked { & "$bt\aapt2.exe" compile --dir "$dir/res" -o "$dir/resources.zip" }
 Checked { & "$bt\aapt2.exe" link -I $android --manifest "$dir/AndroidManifest.xml" "$dir/resources.zip" -o "$dir/unsigned.apk" }
 Checked { & "$j\jar.exe" uf "$dir/unsigned.apk" -C "$dir/dex" classes.dex }
 Checked { & "$bt\zipalign.exe" -f 4 "$dir/unsigned.apk" "$dir/aligned.apk" }
 Checked { & "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "$b/$label.apk" "$dir/aligned.apk" }
 Checked { & "$bt\apksigner.bat" verify --verbose "$b/$label.apk" }
 Get-FileHash "$b/$label.apk" | Format-List
}
