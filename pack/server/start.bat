@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title Kapisma Sunucu
chcp 65001 >nul

rem ==================================================================
rem  Kapisma sunucu baslatici (Windows). Tek tikla: gerekli her seyi kendisi halleder.
rem   1) Mojang EULA onayi    2) Java 21 (yoksa indirir)    3) Ses modu (yoksa indirir)
rem   4) RAM'e gore ayar (tablet / eski bilgisayar dahil)    5) Manhunt yeni dunya dongusu
rem ==================================================================

rem ---------- 1) EULA ----------
findstr /i /c:"eula=true" eula.txt >nul 2>&1
if not errorlevel 1 goto eula_ok
echo.
echo Minecraft sunucusunu calistirmak icin Mojang EULA metnini kabul etmelisin:
echo    https://aka.ms/MinecraftEULA
set "ACC="
set /p ACC="Kabul ediyor musun? [E/H]: "
if /i not "!ACC!"=="E" goto eula_no
> eula.txt echo eula=true
goto eula_ok
:eula_no
echo Kabul edilmedi. Sunucu acilmayacak.
pause
exit /b 1
:eula_ok

rem ---------- 2) Java 21 ----------
set "JAVA="
call :checkjava "%~dp0runtime\bin\java.exe"
if not defined JAVA if defined JAVA_HOME call :checkjava "%JAVA_HOME%\bin\java.exe"
if not defined JAVA call :checkjava "java"
if not defined JAVA if exist "%APPDATA%\.minecraft\runtime" (
  for /r "%APPDATA%\.minecraft\runtime" %%j in (java.ex?) do call :checkjava "%%j"
)
if not defined JAVA call :getjava
if not defined JAVA (
  echo.
  echo HATA: Java 21 bulunamadi ve indirilemedi.
  echo Elle kur: https://adoptium.net/temurin/releases/?version=21  ^(JRE, Windows^)
  echo Sonra bu dosyayi tekrar calistir.
  pause
  exit /b 1
)

rem ---------- 3) Ses modu ----------
if not exist "mods\@@VC_FILE@@" call :getvoice

rem ---------- 4) Bellek ----------
set "TOTALMB="
for /f %%m in ('powershell -NoProfile -Command "[math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1MB)" 2^>nul') do set "TOTALMB=%%m"
if not defined TOTALMB set "TOTALMB=4096"
set /a MAXMB=TOTALMB*55/100
if !MAXMB! GTR 6144 set "MAXMB=6144"
if !MAXMB! LSS 1536 set "MAXMB=1536"
if exist ram_mb.txt set /p MAXMB=<ram_mb.txt
set /a MINMB=MAXMB/2
if exist profil.txt set /p KAPISMA_PROFILE=<profil.txt

set "FLAGS=-XX:+UnlockExperimentalVMOptions -Xms!MINMB!M -Xmx!MAXMB!M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=130 -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
if !MAXMB! GEQ 3072 set "FLAGS=!FLAGS! -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:MaxTenuringThreshold=1"

echo.
echo ================================================
echo  Kapisma sunucusu aciliyor
echo  Java: !JAVA!
echo  Bellek: !MINMB! - !MAXMB! MB   ^(cihazda !TOTALMB! MB var^)
echo  Oyuncu adresi: bu bilgisayarin Tailscale / yerel IP adresi, port 25565
echo  Ses: UDP 24454 portu acik olmali ^(guvenlik_duvari_ac.bat^)
echo ================================================
echo.

rem ---------- 5) Calistir (Manhunt yeni dunya isterse sunucu kapanir, dongu yeniden acar) ----------
:loop
"!JAVA!" !FLAGS! -jar fabric-server-launch.jar nogui
if exist kapisma_next.json (
  echo Yeni dunya hazirlaniyor, sunucu yeniden aciliyor...
  goto loop
)
echo Sunucu kapandi.
pause
exit /b 0

rem ====================== alt programlar ======================
:checkjava
if defined JAVA exit /b 0
if /i not "%~1"=="java" if not exist "%~1" exit /b 0
"%~1" -version >"%TEMP%\kapisma_jv.txt" 2>&1
if errorlevel 1 exit /b 0
set "JV="
for /f "tokens=3" %%v in ('findstr /i /c:" version " "%TEMP%\kapisma_jv.txt"') do if not defined JV set "JV=%%~v"
del "%TEMP%\kapisma_jv.txt" >nul 2>&1
if not defined JV exit /b 0
set "JM="
for /f "delims=." %%m in ("!JV!") do set "JM=%%m"
if not defined JM exit /b 0
set "JMN=0"
set /a JMN=JM 2>nul
if !JMN! GEQ 21 set "JAVA=%~1"
exit /b 0

:getjava
echo.
echo Java 21 bulunamadi. Indiriliyor ^(yaklasik 50 MB, bir kere^)...
set "ARCH=x64"
if /i "%PROCESSOR_ARCHITECTURE%"=="ARM64" set "ARCH=aarch64"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/%ARCH%/jre/hotspot/normal/eclipse' -OutFile 'jre21.zip'; if (Test-Path 'runtime_tmp') { Remove-Item 'runtime_tmp' -Recurse -Force }; Expand-Archive -Path 'jre21.zip' -DestinationPath 'runtime_tmp' -Force; $d = Get-ChildItem 'runtime_tmp' -Directory | Select-Object -First 1; if (Test-Path 'runtime') { Remove-Item 'runtime' -Recurse -Force }; Move-Item $d.FullName 'runtime'; Remove-Item 'runtime_tmp' -Recurse -Force; Remove-Item 'jre21.zip' -Force"
if errorlevel 1 (
  echo Java indirme hatasi.
  exit /b 0
)
call :checkjava "%~dp0runtime\bin\java.exe"
exit /b 0

:getvoice
echo.
echo Sesli sohbet modu indiriliyor ^(Simple Voice Chat, dogrulamali^)...
if not exist mods mkdir mods
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri '@@VC_URL@@' -OutFile 'mods\@@VC_FILE@@.part'; $h=(Get-FileHash 'mods\@@VC_FILE@@.part' -Algorithm SHA512).Hash.ToLower(); if ($h -ne '@@VC_SHA512@@') { Remove-Item 'mods\@@VC_FILE@@.part' -Force; throw 'dogrulama basarisiz' }; Move-Item 'mods\@@VC_FILE@@.part' 'mods\@@VC_FILE@@' -Force"
if errorlevel 1 (
  echo UYARI: Ses modu indirilemedi. Sunucu sesli sohbet OLMADAN acilacak.
  echo Elle indir ve mods klasorune koy: https://modrinth.com/plugin/simple-voice-chat/versions?g=1.21.1^&l=fabric
  echo Dosya adi: @@VC_FILE@@
)
exit /b 0
