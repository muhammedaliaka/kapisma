# Kapisma - SUNUCU AYARLAMA  (Windows PowerShell 5.1+)
# Cift tikla: Sunucu-Ayarla.bat   (zip'i once bir klasore cikar; bu klasor sunucunun kendisidir)
#
# Tek dugme: izinli oyuncu adlarini yazar (offline-mode'da ad taklidini engeller), bellegi ayarlar, Mojang EULA onayini
# kaydeder, guvenlik duvarini acar, sunucuyu baslatir ve arkadaslarinin yazacagi adresi gosterir.
# Bir kere yapman yeter; sonra sunucuyu hep start.bat ile acarsin.
# Otomatik test icin: -Headless ...  |  -SelfTest   (bkz. tools/test_wizard.py)
param(
  [switch]$Headless,
  [string]$Names = '',
  [string]$Ram = '',
  [switch]$AcceptEula,
  [switch]$NoStart,
  [switch]$NoFirewall,
  [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogFile = Join-Path $env:TEMP 'kapisma-sunucu-ayarla.txt'
$script:LogBox = $null
$script:Busy = $false
$script:DoneCount = 0

function Log([string]$t) {
  try { Add-Content -Path $LogFile -Value ((Get-Date -Format 'HH:mm:ss') + ' ' + $t) -Encoding UTF8 } catch {}
  if ($script:LogBox) { $script:LogBox.AppendText($t + "`r`n"); [System.Windows.Forms.Application]::DoEvents() }
  else { Write-Host $t }
}

function Test-Name([string]$n) { return $n -match '^[A-Za-z0-9_]{3,16}$' }

function Test-Pack { return ((Test-Path (Join-Path $Root 'mods')) -and (Test-Path (Join-Path $Root 'start.bat')) -and (Test-Path (Join-Path $Root 'fabric-server-launch.jar'))) }

function Get-Addresses {
  $out = @()
  try {
    $ts = 'C:\Program Files\Tailscale\tailscale.exe'
    if (Test-Path $ts) { $ip = (& $ts ip -4 2>$null | Select-Object -First 1); if ($ip) { $out += "$ip   (Tailscale - arkadaşlarına BUNU ver)" } }
  } catch {}
  try {
    Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.IPAddress -notlike '100.*' } |
      ForEach-Object { $out += "$($_.IPAddress)   (yerel ağ: aynı evdeki oyuncular)" }
  } catch {}
  if ($out.Count -eq 0) { $out += "Adres bulunamadı. Tailscale'i kur: https://tailscale.com" }
  return $out
}

function Read-ExistingNames {
  $f = Join-Path $Root 'izinli_oyuncular.txt'
  if (-not (Test-Path $f)) { return '' }
  $l = @(Get-Content $f -Encoding UTF8 | Where-Object { $_.Trim() -and -not $_.StartsWith('#') })
  return ($l -join "`r`n")
}

function Test-EulaOk {
  $f = Join-Path $Root 'eula.txt'
  return ((Test-Path $f) -and ((Get-Content $f -Raw) -match '(?m)^\s*eula\s*=\s*true'))
}

# ---------------------------------------------------------------- AYARLA
function Set-Server([string[]]$names, [string]$ramGb, [bool]$eula, [bool]$firewall, [bool]$start) {
  if (-not (Test-Pack)) { throw "Sunucu dosyaları bulunamadı. Zip'i önce bir klasöre ÇIKAR, sonra bu dosyayı çıkan klasörden çalıştır." }
  $good = @()
  foreach ($x in $names) { $x = $x.Trim(); if (-not $x) { continue }; if (Test-Name $x) { $good += $x } else { Log "Atlandı (geçersiz Minecraft adı): $x" } }
  if ($good.Count -gt 0) {
    $txt = "# Sadece bu adlar sunucuya girebilir (satır başına bir ad). Boş bırakırsan beyaz liste kapalı olur.`r`n" + ($good -join "`r`n") + "`r`n"
    [IO.File]::WriteAllText((Join-Path $Root 'izinli_oyuncular.txt'), $txt, (New-Object Text.UTF8Encoding($false)))
    Log ("İzinli oyuncular yazıldı (" + $good.Count + " kişi): " + ($good -join ', '))
  } else { Log "UYARI: Oyuncu adı girmedin; beyaz liste KAPALI. Sunucuyu sadece Tailscale'li/yerel ağda aç." }
  if ($ramGb) { [IO.File]::WriteAllText((Join-Path $Root 'ram_mb.txt'), ([int]([double]$ramGb * 1024)).ToString() + "`r`n"); Log "Bellek: $ramGb GB" }
  if ($eula) {
    [IO.File]::WriteAllText((Join-Path $Root 'eula.txt'), "#Mojang EULA https://aka.ms/MinecraftEULA (kurulumda kullanici onayladi)`r`neula=true`r`n")
    Log 'Mojang EULA onayın kaydedildi.'
  }
  Log ''
  Log 'Arkadaşlarının Minecraft''a yazacağı adres:'
  foreach ($a in (Get-Addresses)) { Log ('   ' + $a) }
  if ($firewall) {
    Log 'Güvenlik duvarı izni isteniyor (açılan pencerede Evet de)...'
    Start-Process -FilePath (Join-Path $Root 'guvenlik_duvari_ac.bat') -WorkingDirectory $Root
  }
  if ($start) {
    Log 'Sunucu penceresi açılıyor. "Done" yazısını görünce hazır (ilk açılış 1-3 dk).'
    Start-Process -FilePath (Join-Path $Root 'start.bat') -WorkingDirectory $Root
  }
  $script:DoneCount++
  Log ''
  Log 'TAMAM. Bundan sonra sunucuyu sadece start.bat ile aç.'
}

# ---------------------------------------------------------------- GUI
function Start-Setup {
  if ($script:Busy -or $script:DoneCount -gt 0) { return }
  if (-not $script:ckEula.Checked) { [void][System.Windows.Forms.MessageBox]::Show("Sunucuyu çalıştırmak için Mojang EULA'yı kabul etmelisin (kutucuğu işaretle).", 'Kapışma'); return }
  $script:Busy = $true
  $script:btnMain.Enabled = $false
  $script:btnMain.Text = 'AYARLANIYOR...'
  try {
    $ram = ''
    if ($script:cbRam.SelectedIndex -gt 0) { $ram = ($script:cbRam.SelectedItem -replace ' GB', '') }
    Set-Server ($script:tbNames.Text -split '[\r\n,;]+') $ram $true $script:ckFw.Checked $script:ckStart.Checked
    $script:btnMain.Text = 'KURULDU'
    $script:btnMain.BackColor = [System.Drawing.Color]::FromArgb(198, 239, 206)
  } catch {
    Log ('HATA: ' + $_.Exception.Message)
    Log "(Ayrıntı günlüğü: $LogFile)"
    [void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Kapışma', 'OK', 'Error')
    $script:btnMain.Text = 'TEKRAR DENE'
    $script:btnMain.Enabled = $true
  } finally { $script:Busy = $false }
}

function New-ServerForm {
  Add-Type -AssemblyName System.Windows.Forms
  Add-Type -AssemblyName System.Drawing
  [System.Windows.Forms.Application]::EnableVisualStyles()
  function C($type, $text, $x, $y, $w, $h) {
    $c = New-Object $type
    $c.Text = $text; $c.Location = New-Object Drawing.Point($x, $y); $c.Size = New-Object Drawing.Size($w, $h)
    return $c
  }
  $f = C 'System.Windows.Forms.Form' 'Kapışma - Sunucu Ayarlama' 0 0 560 640
  $f.StartPosition = 'CenterScreen'; $f.FormBorderStyle = 'FixedDialog'; $f.MaximizeBox = $false
  $f.Font = New-Object Drawing.Font('Segoe UI', 10)
  $t1 = C 'System.Windows.Forms.Label' 'Kapışma sunucusu' 20 12 500 40; $t1.Font = New-Object Drawing.Font('Segoe UI', 20, [Drawing.FontStyle]::Bold)
  $t2 = C 'System.Windows.Forms.Label' 'Bir kere ayarla; sonra sunucuyu hep start.bat ile açarsın.' 22 54 500 24
  $t3 = C 'System.Windows.Forms.Label' 'Girebilecek oyuncular (her satıra bir Minecraft adı, SEN DAHİL):' 22 88 500 22
  $script:tbNames = C 'System.Windows.Forms.TextBox' (Read-ExistingNames) 22 112 300 110
  $script:tbNames.Multiline = $true; $script:tbNames.ScrollBars = 'Vertical'; $script:tbNames.AcceptsReturn = $true
  $t4 = C 'System.Windows.Forms.Label' 'Bellek (RAM):' 342 112 170 22
  $script:cbRam = C 'System.Windows.Forms.ComboBox' '' 342 136 170 28; $script:cbRam.DropDownStyle = 'DropDownList'
  [void]$script:cbRam.Items.AddRange(@('Otomatik (önerilir)', '2 GB', '3 GB', '4 GB', '6 GB', '8 GB')); $script:cbRam.SelectedIndex = 0
  $script:ckEula = C 'System.Windows.Forms.CheckBox' "Mojang EULA'yı (aka.ms/MinecraftEULA) okudum, kabul ediyorum" 22 234 510 26
  $script:ckEula.Checked = (Test-EulaOk)
  $script:ckFw = C 'System.Windows.Forms.CheckBox' 'Güvenlik duvarı izni ver (yönetici izni sorar)' 22 262 510 26; $script:ckFw.Checked = $true
  $script:ckStart = C 'System.Windows.Forms.CheckBox' 'Ayarlayınca sunucuyu başlat' 22 290 510 26; $script:ckStart.Checked = $true
  $script:btnMain = C 'System.Windows.Forms.Button' 'KUR VE BAŞLAT' 22 326 510 70
  $script:btnMain.Font = New-Object Drawing.Font('Segoe UI', 16, [Drawing.FontStyle]::Bold)
  $script:LogBox = C 'System.Windows.Forms.TextBox' '' 22 410 510 160
  $script:LogBox.Multiline = $true; $script:LogBox.ReadOnly = $true; $script:LogBox.ScrollBars = 'Vertical'; $script:LogBox.Font = New-Object Drawing.Font('Consolas', 9)
  $btnClose = C 'System.Windows.Forms.Button' 'Kapat' 432 576 100 32
  $f.Controls.AddRange(@($t1, $t2, $t3, $script:tbNames, $t4, $script:cbRam, $script:ckEula, $script:ckFw, $script:ckStart, $script:btnMain, $script:LogBox, $btnClose))
  $script:form = $f
  $script:btnMain.Add_Click({ Start-Setup })
  $btnClose.Add_Click({ $script:form.Close() })
  if (-not (Test-Pack)) { $script:btnMain.Text = "PAKET EKSİK`r`nZip'i önce çıkar"; $script:btnMain.Enabled = $false }
  return $f
}

function Invoke-Click($btn) {
  $m = [System.Windows.Forms.Control].GetMethod('OnClick', [Reflection.BindingFlags]'NonPublic,Instance')
  [void]$m.Invoke($btn, @([EventArgs]::Empty))
}

# ---------------------------------------------------------------- giris
if ($Headless) {
  try { Set-Server ($Names -split '[,;]+') $Ram $AcceptEula.IsPresent (-not $NoFirewall.IsPresent) (-not $NoStart.IsPresent); exit 0 }
  catch { Write-Host ('HATA: ' + $_.Exception.Message); exit 1 }
}
if ($SelfTest) {
  function Expect($c, $what) { if (-not $c) { Write-Host "SELFTEST HATA: $what"; exit 3 } }
  $f = New-ServerForm
  Expect ($script:btnMain.Enabled) 'paket tamamken dugme acik olmali'
  $script:tbNames.Text = "Ahmet_34`r`nZeynep"; $script:ckEula.Checked = $true; $script:ckFw.Checked = $false; $script:ckStart.Checked = $false
  Invoke-Click $script:btnMain
  Expect ($script:DoneCount -eq 1) 'tiklayinca ayar bir kez uygulanmali'
  Expect (-not $script:btnMain.Enabled -and $script:btnMain.Text -eq 'KURULDU') 'sonra dugme kapali (KURULDU) olmali'
  Invoke-Click $script:btnMain
  Expect ($script:DoneCount -eq 1) 'tekrar tiklamak yeniden calistirmamali'
  Expect ((Read-ExistingNames) -match 'Ahmet_34') 'izinli_oyuncular.txt yazilmali'
  $f.Dispose()
  'SELFTEST OK: tek dugme, tek ayar'
  exit 0
}
$form = New-ServerForm
[void]$form.ShowDialog()
