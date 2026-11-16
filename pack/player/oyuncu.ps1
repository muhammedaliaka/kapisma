# Kapisma - OYUNCU KURULUMU  (Windows PowerShell 5.1+; Windows 10/11'de hazir gelir)
# Cift tikla: Kapisma-Oyuncu-Kur.bat   (bu dosyayi dogrudan acmana gerek yok)
#
# Tek dugme: modlari .minecraft\mods klasorune kurar, Fabric 1.21.1 surumunu (yoksa) ekler, sesli sohbet modunu
# dogrulamali indirir. Zaten kuruluysa hicbir sey yapmaz. Eski modlar SILINMEZ: mods\_kapisma_eski klasorune tasinir.
# Otomatik test icin: -Headless -McDir <klasor>   |   -SelfTest -McDir <gecici klasor>   (bkz. tools/test_wizard.py)
param(
  [switch]$Headless,
  [string]$McDir = '',
  [string]$Address = '',
  [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$VcFile = '@@VC_FILE@@'
$VcUrl = '@@VC_URL@@'
$VcSha = '@@VC_SHA512@@'
$FabricName = 'fabric-loader-0.20.1-1.21.1'
$LogFile = Join-Path $env:TEMP 'kapisma-oyuncu-kurulum.txt'
$script:LogBox = $null
$script:InstallCount = 0
$script:LastFab = ''
$script:Busy = $false
$script:StateNow = ''
$script:McCur = ''
$script:Addr = $Address

function Log([string]$t) {
  try { Add-Content -Path $LogFile -Value ((Get-Date -Format 'HH:mm:ss') + ' ' + $t) -Encoding UTF8 } catch {}
  if ($script:LogBox) { $script:LogBox.AppendText($t + "`r`n"); [System.Windows.Forms.Application]::DoEvents() }
  else { Write-Host $t }
}

# ---------------------------------------------------------------- yardimcilar
function Get-PackJars {
  $d = Join-Path $Root 'mods'
  if (-not (Test-Path $d)) { return @() }
  return @(Get-ChildItem $d -Filter *.jar -File)
}

function Find-McDir {
  if ($McDir) { return $McDir }
  $d = Join-Path $env:APPDATA '.minecraft'
  if (Test-Path $d) { return $d }
  return ''
}

function Get-FabricVersion([string]$mc) {
  $vdir = Join-Path $mc 'versions'
  if (Test-Path $vdir) {
    $x = Get-ChildItem $vdir -Directory | Where-Object { $_.Name -match 'fabric' -and $_.Name -match '1\.21\.1' } | Select-Object -First 1
    if ($x) { return $x.Name }
  }
  return $null
}

# 'new' (hic yok) | 'update' (eksik/eski var) | 'ok' (her sey kurulu ve guncel)
function Get-State([string]$mc) {
  $mods = Join-Path $mc 'mods'
  $same = $true; $any = $false
  foreach ($j in (Get-PackJars)) {
    $t = Join-Path $mods $j.Name
    if (Test-Path $t) { $any = $true; if ((Get-Item $t).Length -ne $j.Length) { $same = $false } } else { $same = $false }
  }
  if (Test-Path (Join-Path $mods $VcFile)) { $any = $true } else { $same = $false }
  if (-not (Get-FabricVersion $mc)) { $same = $false }
  if ($same) { return 'ok' }
  if ($any) { return 'update' }
  return 'new'
}

function Get-VoiceChat([string]$modsDir) {
  $target = Join-Path $modsDir $VcFile
  if (Test-Path $target) { return $true }
  Log 'Sesli sohbet modu indiriliyor (Simple Voice Chat, doğrulamalı)...'
  try {
    Invoke-WebRequest -UseBasicParsing -Uri $VcUrl -OutFile ($target + '.part')
    $h = (Get-FileHash ($target + '.part') -Algorithm SHA512).Hash.ToLower()
    if ($h -ne $VcSha) { Remove-Item ($target + '.part') -Force; throw 'doğrulama (SHA-512) başarısız, dosya silindi' }
    Move-Item ($target + '.part') $target -Force
    Log "Kuruldu: $VcFile"
    return $true
  } catch {
    Log "UYARI: Sesli sohbet modu indirilemedi ($($_.Exception.Message))."
    Log 'Elle indirip mods klasörüne koy: https://modrinth.com/plugin/simple-voice-chat/versions?g=1.21.1&l=fabric'
    return $false
  }
}

# ---------------------------------------------------------------- KURULUM
function Install-Player([string]$mc, [string]$address) {
  $jars = @(Get-PackJars)
  if ($jars.Count -eq 0) { throw "Paketin 'mods' klasörü bulunamadı. Zip'i önce bir klasöre ÇIKAR, sonra çalıştır." }
  if (-not (Test-Path $mc)) { New-Item -ItemType Directory -Force $mc | Out-Null }
  $mods = Join-Path $mc 'mods'
  New-Item -ItemType Directory -Force $mods | Out-Null
  $old = Join-Path $mods '_kapisma_eski'
  Log "Mod klasörü: $mods"

  # 1) farkli surumdeki eski dosyalari kenara al (silme yok); ayni dosyaya dokunma
  foreach ($p in 'kapisma-hud-', 'fabric-api-', 'Origins-', 'voicechat-') {
    foreach ($f in @(Get-ChildItem $mods -Filter "$p*.jar" -File -ErrorAction SilentlyContinue)) {
      $same = $false
      $pk = $jars | Where-Object { $_.Name -eq $f.Name } | Select-Object -First 1
      if ($pk -and $pk.Length -eq $f.Length -and (Get-FileHash $pk.FullName).Hash -eq (Get-FileHash $f.FullName).Hash) { $same = $true }
      if ($f.Name -eq $VcFile) { $same = $true }
      if (-not $same) {
        New-Item -ItemType Directory -Force $old | Out-Null
        $dst = Join-Path $old $f.Name
        if (Test-Path $dst) { $dst = $dst + '.' + (Get-Date -Format 'yyyyMMddHHmmss') }
        Move-Item $f.FullName $dst -Force
        Log "Eski sürüm kenara alındı: $($f.Name)"
      }
    }
  }

  # 2) paketteki modlar
  foreach ($j in $jars) {
    $t = Join-Path $mods $j.Name
    if ((Test-Path $t) -and (Get-FileHash $t).Hash -eq (Get-FileHash $j.FullName).Hash) { continue }
    Copy-Item $j.FullName $t -Force
    Log "Kuruldu: $($j.Name)"
  }
  $vcOk = $true
  if (-not (Test-Path (Join-Path $mods $VcFile))) { $vcOk = Get-VoiceChat $mods }

  # 3) dogrulama: her mod gercekten dogru klasorde mi
  $missing = @()
  foreach ($j in $jars) { if (-not (Test-Path (Join-Path $mods $j.Name))) { $missing += $j.Name } }
  if ($missing.Count -gt 0) { throw ('Şu dosyalar mods klasörüne yazılamadı: ' + ($missing -join ', ') + ". Klasör yazma izni var mı? ($mods)") }
  Log ("Doğrulandı: " + ($jars.Count + $(if ($vcOk) { 1 } else { 0 })) + " mod doğru klasörde.")

  # 4) Fabric 1.21.1 surumu: varsa dokunma (TLauncher / resmi launcher), yoksa ekle
  $fabName = Get-FabricVersion $mc
  if ($fabName) { Log "Fabric 1.21.1 sürümü hazır: $fabName" }
  else {
    $json = Join-Path $Root ($FabricName + '.json')
    if (-not (Test-Path $json)) { throw 'Paketteki Fabric sürüm dosyası eksik.' }
    $fabName = $FabricName
    $d = Join-Path (Join-Path $mc 'versions') $fabName
    New-Item -ItemType Directory -Force $d | Out-Null
    Copy-Item $json (Join-Path $d ($fabName + '.json')) -Force
    Log "Fabric 1.21.1 sürümü eklendi: $fabName"
  }

  # 5) resmi Minecraft Launcher icin profil (TLauncher bunu gerektirmez)
  $lp = Join-Path $mc 'launcher_profiles.json'
  if (Test-Path $lp) {
    try {
      $j = Get-Content -Raw -Encoding UTF8 $lp | ConvertFrom-Json
      if ($j.profiles -and -not ($j.profiles.PSObject.Properties.Name -contains 'kapisma')) {
        Copy-Item $lp ($lp + '.kapisma_yedek') -Force
        $prof = [pscustomobject]@{ name = 'Kapisma'; type = 'custom'; lastVersionId = $fabName; icon = 'Furnace' }
        $j.profiles | Add-Member -NotePropertyName 'kapisma' -NotePropertyValue $prof
        [IO.File]::WriteAllText($lp, ($j | ConvertTo-Json -Depth 30), (New-Object Text.UTF8Encoding($false)))
        Log "Resmi launcher'a 'Kapisma' profili eklendi."
      }
    } catch { }
  }

  # 6) sunucu adresi (host paketin yanina sunucu_adresi.txt koyduysa) panoya
  $addr = $address
  if (-not $addr) {
    $af = Join-Path $Root 'sunucu_adresi.txt'
    if (Test-Path $af) { $addr = (Get-Content $af -Encoding UTF8 | Where-Object { $_.Trim() -and -not $_.StartsWith('#') } | Select-Object -First 1) }
  }
  if ($addr) {
    try { Set-Clipboard -Value $addr.Trim(); Log "Sunucu adresi panoya kopyalandı: $($addr.Trim())  (Çoklu Oyuncu > Sunucu Ekle > yapıştır)" } catch { Log "Sunucu adresi: $addr" }
  }
  $script:LastFab = $fabName
  $script:InstallCount++
  Log ''
  Log 'KURULUM TAMAM.'
}

# ---------------------------------------------------------------- GUI
function Set-Ui {
  $b = $script:btnMain
  $mc = $script:McCur
  $script:lblPath.Text = 'Minecraft klasörü: ' + $(if ($mc) { $mc } else { '(bulunamadı)' })
  $b.BackColor = [System.Drawing.SystemColors]::Control
  if (-not $mc -or -not (Test-Path $mc)) { $script:StateNow = 'nomc'; $b.Text = "MINECRAFT KLASÖRÜ BULUNAMADI`r`n('değiştir'e basıp seç)"; $b.Enabled = $false; return }
  if ((Get-PackJars).Count -eq 0) { $script:StateNow = 'nopack'; $b.Text = "PAKET EKSİK`r`nZip'i önce çıkar"; $b.Enabled = $false; return }
  $script:StateNow = Get-State $mc
  if ($script:StateNow -eq 'ok') { $b.Text = 'KURULDU - HER ŞEY HAZIR'; $b.Enabled = $false; $b.BackColor = [System.Drawing.Color]::FromArgb(198, 239, 206) }
  elseif ($script:StateNow -eq 'update') { $b.Text = 'GÜNCELLE'; $b.Enabled = $true }
  else { $b.Text = 'KUR'; $b.Enabled = $true }
}

function Select-McFolder {
  $d = New-Object System.Windows.Forms.FolderBrowserDialog
  $d.Description = 'Minecraft klasörünü seç (içinde "versions" ve "mods" bulunan .minecraft klasörü)'
  if ($script:McCur) { $d.SelectedPath = $script:McCur }
  if ($d.ShowDialog() -eq 'OK') { $script:McCur = $d.SelectedPath; Set-Ui }
}

function Start-Install {
  if ($script:Busy -or $script:StateNow -eq 'ok' -or $script:StateNow -eq 'nomc' -or $script:StateNow -eq 'nopack') { return }
  $script:Busy = $true
  $script:btnMain.Enabled = $false
  $script:btnMain.Text = 'KURULUYOR...'
  try {
    Install-Player $script:McCur $script:Addr
    Log "SIRADAKİ: TLauncher'ı aç > sürüm: $($script:LastFab) > Oyna > Çoklu Oyuncu > hostun adresi."
  } catch {
    Log ('HATA: ' + $_.Exception.Message)
    Log "(Ayrıntı günlüğü: $LogFile)"
    [void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Kapışma', 'OK', 'Error')
  } finally {
    $script:Busy = $false
    Set-Ui
  }
}

function New-PlayerForm {
  Add-Type -AssemblyName System.Windows.Forms
  Add-Type -AssemblyName System.Drawing
  [System.Windows.Forms.Application]::EnableVisualStyles()
  function C($type, $text, $x, $y, $w, $h) {
    $c = New-Object $type
    $c.Text = $text; $c.Location = New-Object Drawing.Point($x, $y); $c.Size = New-Object Drawing.Size($w, $h)
    return $c
  }
  $f = C 'System.Windows.Forms.Form' 'Kapışma - Oyuncu Kurulumu' 0 0 540 470
  $f.StartPosition = 'CenterScreen'; $f.FormBorderStyle = 'FixedDialog'; $f.MaximizeBox = $false
  $f.Font = New-Object Drawing.Font('Segoe UI', 10)
  $t1 = C 'System.Windows.Forms.Label' 'Kapışma' 20 14 480 40; $t1.Font = New-Object Drawing.Font('Segoe UI', 20, [Drawing.FontStyle]::Bold)
  $t2 = C 'System.Windows.Forms.Label' 'Arkadaşının sunucusuna girmek için gereken her şeyi kurar. Tek düğme.' 22 58 490 24
  $script:btnMain = C 'System.Windows.Forms.Button' 'KUR' 22 94 490 90
  $script:btnMain.Font = New-Object Drawing.Font('Segoe UI', 16, [Drawing.FontStyle]::Bold)
  $script:LogBox = C 'System.Windows.Forms.TextBox' '' 22 198 490 170
  $script:LogBox.Multiline = $true; $script:LogBox.ReadOnly = $true; $script:LogBox.ScrollBars = 'Vertical'
  $script:LogBox.Font = New-Object Drawing.Font('Consolas', 9)
  $script:lblPath = C 'System.Windows.Forms.Label' '' 22 380 380 22
  $script:lblPath.ForeColor = [System.Drawing.Color]::Gray; $script:lblPath.Font = New-Object Drawing.Font('Segoe UI', 8.5)
  $lnk = C 'System.Windows.Forms.LinkLabel' 'değiştir' 410 380 100 22; $lnk.Font = New-Object Drawing.Font('Segoe UI', 8.5)
  $btnClose = C 'System.Windows.Forms.Button' 'Kapat' 412 404 100 32
  $f.Controls.AddRange(@($t1, $t2, $script:btnMain, $script:LogBox, $script:lblPath, $lnk, $btnClose))
  $script:lnkChange = $lnk; $script:btnClose = $btnClose; $script:form = $f
  $script:btnMain.Add_Click({ Start-Install })
  $lnk.Add_Click({ Select-McFolder })
  $btnClose.Add_Click({ $script:form.Close() })
  $script:McCur = Find-McDir
  Set-Ui
  return $f
}

# test: dugmeye gercekten tikla (form gosterilmeden de calisir)
function Invoke-Click($btn) {
  $m = [System.Windows.Forms.Control].GetMethod('OnClick', [Reflection.BindingFlags]'NonPublic,Instance')
  [void]$m.Invoke($btn, @([EventArgs]::Empty))
}

# ---------------------------------------------------------------- giris
if ($Headless) {
  try { $mc = Find-McDir; if (-not $mc) { throw 'Minecraft klasörü bulunamadı' }; Install-Player $mc $Address; exit 0 }
  catch { Write-Host ('HATA: ' + $_.Exception.Message); exit 1 }
}
if ($SelfTest) {
  function Expect($c, $what) { if (-not $c) { Write-Host "SELFTEST HATA: $what"; exit 3 } }
  $f = New-PlayerForm
  Expect ($script:StateNow -eq 'new' -and $script:btnMain.Enabled -and $script:btnMain.Text -eq 'KUR') "temiz klasorde dugme 'KUR' ve acik olmali (durum: $($script:StateNow))"
  Invoke-Click $script:btnMain
  Expect ($script:InstallCount -eq 1) 'tiklayinca kurulum bir kez calismali'
  Expect ($script:StateNow -eq 'ok' -and -not $script:btnMain.Enabled) 'kurulumdan sonra dugme kapali (KURULDU) olmali'
  Invoke-Click $script:btnMain
  Invoke-Click $script:btnMain
  Expect ($script:InstallCount -eq 1) 'tekrar tiklamak kurulumu yeniden calistirmamali'
  $f.Dispose()
  $f2 = New-PlayerForm
  Expect ($script:StateNow -eq 'ok' -and -not $script:btnMain.Enabled) 'ikinci acilista zaten kurulu: dugme kapali olmali'
  $f2.Dispose()
  "SELFTEST OK: tek dugme, tek kurulum, ikinci acilista kapali"
  exit 0
}
$form = New-PlayerForm
if ($script:StateNow -eq 'ok') { Log 'Her şey zaten kurulu. Yapacak bir şey yok.'; Log "TLauncher'ı aç > sürüm: $(Get-FabricVersion $script:McCur) > Oyna." }
[void]$form.ShowDialog()
