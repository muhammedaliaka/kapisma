@echo off
rem Windows guvenlik duvarinda Kapisma portlarini acar: oyun TCP 25565, ses UDP 24454.
rem Yonetici izni ister (UAC penceresine Evet de).
net session >nul 2>&1
if errorlevel 1 (
  echo Yonetici izni isteniyor...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
netsh advfirewall firewall delete rule name="Kapisma Oyun" >nul 2>&1
netsh advfirewall firewall delete rule name="Kapisma Ses" >nul 2>&1
netsh advfirewall firewall add rule name="Kapisma Oyun" dir=in action=allow protocol=TCP localport=25565
netsh advfirewall firewall add rule name="Kapisma Ses" dir=in action=allow protocol=UDP localport=24454
echo.
echo Tamam: TCP 25565 (oyun) ve UDP 24454 (ses) acildi.
echo Not: Internete port yonlendirme YAPMA. Arkadaslarini Tailscale agina davet et.
pause
