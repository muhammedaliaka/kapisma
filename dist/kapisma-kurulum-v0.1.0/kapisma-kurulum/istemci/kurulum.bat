@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title Kapisma Istemci Kurulumu
chcp 65001 >nul

rem ==================================================================
rem  Kapisma istemci kurulumu (Windows / TLauncher / resmi launcher)
rem  Kullanim: cift tikla. Farkli bir .minecraft klasoru icin:  kurulum.bat "D:\oyun\.minecraft"
rem ==================================================================
set "MC=%~1"
if "%MC%"=="" set "MC=%APPDATA%\.minecraft"
if not exist "%MC%" (
  echo HATA: Minecraft klasoru bulunamadi: %MC%
  echo Once TLauncher'i ac, Fabric 1.21.1 surumunu bir kere baslat; sonra bu dosyayi tekrar calistir.
  pause
  exit /b 1
)
set "MODS=%MC%\mods"
if not exist "%MODS%" mkdir "%MODS%"
set "OLD=%MODS%\_kapisma_eski"

echo.
echo Hedef: %MODS%
echo.

rem ---- eski surumleri kenara al (silmez, _kapisma_eski klasorune tasir) ----
for %%p in (kapisma-hud fabric-api Origins voicechat) do (
  for %%f in ("%MODS%\%%p*.jar") do (
    if exist "%%~f" (
      if not exist "%OLD%" mkdir "%OLD%"
      move /y "%%~f" "%OLD%\" >nul
      echo Eski dosya kenara alindi: %%~nxf
    )
  )
)

rem ---- paketteki modlar ----
for %%f in ("%~dp0mods\*.jar") do (
  copy /y "%%~f" "%MODS%\" >nul
  echo Kuruldu: %%~nxf
)

rem ---- ses modu (dogrulamali indirme) ----
set "VCOK=0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $d='%MODS%\voicechat-fabric-1.21.1-2.6.27.jar'; $src='%~dp0mods\voicechat-fabric-1.21.1-2.6.27.jar'; if (Test-Path $src) { Copy-Item $src $d -Force } else { Invoke-WebRequest -UseBasicParsing -Uri 'https://cdn.modrinth.com/data/9eGKb6K1/versions/dyhq1XyO/voicechat-fabric-1.21.1-2.6.27.jar' -OutFile ($d + '.part'); $h=(Get-FileHash ($d + '.part') -Algorithm SHA512).Hash.ToLower(); if ($h -ne '4ab4e18279bb17b999d67271f45e6eccaaf30b5c86a27693860eaf8dd8d3d3b26030aabcbcfcc51d87b9a28169d753ce96cc8a9e0ceba3619aee9fd1ffc9413c') { Remove-Item ($d + '.part') -Force; throw 'dogrulama basarisiz' }; Move-Item ($d + '.part') $d -Force }"
if not errorlevel 1 set "VCOK=1"
if "!VCOK!"=="1" (
  echo Kuruldu: voicechat-fabric-1.21.1-2.6.27.jar ^(sesli sohbet^)
) else (
  echo.
  echo UYARI: Sesli sohbet modu indirilemedi. Elle indirip %MODS% klasorune koy:
  echo   https://modrinth.com/plugin/simple-voice-chat/versions?g=1.21.1^&l=fabric   ^(dosya: voicechat-fabric-1.21.1-2.6.27.jar^)
)

rem ---- Fabric surumu var mi? ----
set "FAB="
for /d %%v in ("%MC%\versions\*fabric*1.21.1*") do set "FAB=%%~nxv"
echo.
if defined FAB (
  echo Fabric surumu bulundu: !FAB!
) else (
  echo DIKKAT: Fabric 1.21.1 surumu henuz yok. TLauncher'da surum listesinden "Fabric 1.21.1"
  echo secip bir kere baslat ^(TLauncher kendisi indirir^), sonra oyuna gir.
)
echo.
echo ================= HAZIR =================
echo 1^) TLauncher ^> surum: Fabric 1.21.1 ^> Oyna
echo 2^) Coklu Oyuncu ^> Sunucu Ekle ^> hostun verdigi adres ^(Tailscale 100.x.x.x^)
echo 3^) Oyunda V tusu: mikrofon / sesli sohbet ayarlari
echo =========================================
pause
exit /b 0
