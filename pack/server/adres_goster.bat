@echo off
rem Arkadaslarin baglanacagi adresleri gosterir.
echo.
set "TS=C:\Program Files\Tailscale\tailscale.exe"
if exist "%TS%" (
  echo Tailscale adresin ^(arkadaslar bunu Minecraft'a yazacak^):
  "%TS%" ip -4
) else (
  echo Tailscale kurulu degil. https://tailscale.com adresinden kur, giris yap, arkadaslarini agina davet et.
)
echo.
echo Ayni evdeki/ayni agdaki oyuncular icin yerel adresler:
ipconfig | findstr /i "IPv4"
echo.
echo Port varsayilan 25565; adrese ayrica yazmana gerek yok.
pause
