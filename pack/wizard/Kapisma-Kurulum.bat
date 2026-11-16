@echo off
cd /d "%~dp0"
title Kapisma Kurulum Sihirbazi
if not exist "%~dp0kurulum.ps1" echo HATA: kurulum.ps1 yok. Zip i tamamen cikar, sonra tekrar dene. & pause & exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0kurulum.ps1"
if errorlevel 1 echo. & echo Sihirbaz acilamadi. Elle kurulum: istemci\kurulum.bat ^(oyuncu^) veya sunucu\start.bat ^(host^). & pause
exit /b 0
