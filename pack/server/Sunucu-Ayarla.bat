@echo off
cd /d "%~dp0"
title Kapisma Sunucu Ayarlama
if not exist "%~dp0sunucu.ps1" echo HATA: sunucu.ps1 yok. Zip i once bir klasore cikar, sonra tekrar dene. & pause & exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0sunucu.ps1"
if errorlevel 1 echo. & echo Pencere acilamadi. Elle: start.bat ile ac; izinli adlari izinli_oyuncular.txt dosyasina yaz. & pause
exit /b 0
