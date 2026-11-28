@echo off
cd /d "%~dp0"
title Kapisma Oyuncu Kurulumu
if not exist "%~dp0oyuncu.ps1" (
  echo HATA: oyuncu.ps1 yok. Zip i once bir klasore cikar, sonra tekrar dene.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0oyuncu.ps1" %*
if errorlevel 1 (
  echo.
  echo Kurulum basarisiz. Elle kurulum: elle_kurulum\kurulum.bat dosyasina cift tikla.
  pause
  exit /b 1
)
exit /b 0
