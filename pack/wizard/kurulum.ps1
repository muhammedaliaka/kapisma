# Kapisma Kurulum Sihirbazi  (Windows PowerShell 5.1+; Windows 10/11'de hazir gelir)
# Cift tikla:  Kapisma-Kurulum.bat   (bu dosyayi dogrudan acmana gerek yok)
#
# Oyuncu:  modlari dogru klasore kopyalar, Fabric 1.21.1 surumunu (yoksa) ekler, sesli sohbet modunu dogrulamali indirir,
#          istersen sunucuyu "Coklu Oyuncu" listesine ekler. Eski modlar SILINMEZ, mods\_kapisma_eski'ye tasinir.
# Sunucu:  sunucu klasorunu istedigin yere kurar, oyuncu adlarini beyaz listeye yazar (offline-mode'da ad taklidini engeller),
#          bellek ayari, guvenlik duvari, adres gosterme, baslatma.
# Otomatik test icin: -Headless -Role Player|Server ...   (bkz. tools/test_wizard.py)
param(
  [switch]$Headless,
  [ValidateSet('', 'Player', 'Server')][string]$Role = '',
  [string]$McDir = '',
  [string]$ServerDir = '',
  [string]$Names = '',
  [string]$Ram = '',
  [string]$Address = '',
  [switch]$AcceptEula,
  [switch]$NoStart,
  [switch]$NoFirewall,
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
$script:LogBox = $null

function Log([string]$t) {
  if ($script:LogBox) { $script:LogBox.AppendText($t + "`r`n"); [System.Windows.Forms.Application]::DoEvents() }
  else { Write-Host $t }
}

# ---------------------------------------------------------------- ortak yardimcilar
function Test-Name([string]$n) { return $n -match '^[A-Za-z0-9_]{3,16}$' }

function Get-Addresses {
  $out = @()
  try {
    $ts = 'C:\Program Files\Tailscale\tailscale.exe'
    if (Test-Path $ts) { $ip = (& $ts ip -4 2>$null | Select-Object -First 1); if ($ip) { $out += "$ip   (Tailscale - arkadaşlarına bunu ver)" } }
  } catch {}
  try {
    Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.IPAddress -notlike '100.*' } |
      ForEach-Object { $out += "$($_.IPAddress)   (yerel ağ: aynı evdeki/okuldaki oyuncular)" }
  } catch {}
  if ($out.Count -eq 0) { $out += 'Adres bulunamadı. Tailscale kur: https://tailscale.com' }
  return $out
}

function Get-VoiceChat([string]$modsDir) {
  $target = Join-Path $modsDir $VcFile
  if (Test-Path $target) { return $true }                      # paketle zaten kopyalandi
  $bundled = Join-Path $Root ('istemci\mods\' + $VcFile)
  if (Test-Path $bundled) { Copy-Item $bundled $target -Force; Log "Kuruldu: $VcFile (paketten)"; return $true }
  Log 'Sesli sohbet modu indiriliyor (Simple Voice Chat, doğrulamalı)...'
  try {
    Invoke-WebRequest -UseBasicParsing -Uri $VcUrl -OutFile ($target + '.part')
    $h = (Get-FileHash ($target + '.part') -Algorithm SHA512).Hash.ToLower()
    if ($h -ne $VcSha) { Remove-Item ($target + '.part') -Force; throw 'doğrulama (SHA-512) başarısız, dosya silindi' }
    Move-Item ($target + '.part') $target -Force
    Log "Kuruldu: $VcFile (sesli sohbet)"
    return $true
  } catch {
    Log "UYARI: Sesli sohbet modu indirilemedi ($($_.Exception.Message))."
    Log '       Elle indir ve mods klasörüne koy: https://modrinth.com/plugin/simple-voice-chat/versions?g=1.21.1&l=fabric'
    return $false
  }
}

# servers.dat (Minecraft "Coklu Oyuncu" listesi) - sadece dosya YOKSA yazilir (var olan listeyi bozmamak icin)
function New-ServersDat([string]$path, [string]$name, [string]$ip) {
  $b = New-Object 'System.Collections.Generic.List[byte]'
  function AddStr($l, [string]$s) { $x = [Text.Encoding]::UTF8.GetBytes($s); $l.Add([byte]($x.Length -shr 8)); $l.Add([byte]($x.Length -band 255)); $l.AddRange([byte[]]$x) }
  $b.Add(10); AddStr $b ''                                  # kok compound
  $b.Add(9); AddStr $b 'servers'; $b.Add(10)                # liste, elemanlar compound
  $b.Add(0); $b.Add(0); $b.Add(0); $b.Add(1)               # 1 eleman
  $b.Add(8); AddStr $b 'name'; AddStr $b $name
  $b.Add(8); AddStr $b 'ip'; AddStr $b $ip
  $b.Add(0)                                                 # eleman sonu
  $b.Add(0)                                                 # kok sonu
  [IO.File]::WriteAllBytes($path, $b.ToArray())
}

# ---------------------------------------------------------------- OYUNCU
function Install-Player([string]$mc, [string]$address) {
  if (-not $mc) { throw 'Minecraft klasörü boş.' }
  $src = Join-Path $Root 'istemci\mods'
  if (-not (Test-Path $src)) { throw "Paket eksik: $src (zip'i tamamen çıkardın mı?)" }
  if (-not (Test-Path $mc)) { New-Item -ItemType Directory -Force $mc | Out-Null; Log "Minecraft klasörü yoktu, oluşturuldu: $mc" }
  $mods = Join-Path $mc 'mods'
  New-Item -ItemType Directory -Force $mods | Out-Null
  $old = Join-Path $mods '_kapisma_eski'
  Log "Hedef: $mods"
  foreach ($p in 'kapisma-hud', 'fabric-api', 'Origins', 'voicechat') {
    Get-ChildItem -Path $mods -Filter "$p*.jar" -File -ErrorAction SilentlyContinue | ForEach-Object {
      New-Item -ItemType Directory -Force $old | Out-Null
      Move-Item $_.FullName (Join-Path $old $_.Name) -Force
      Log "Eski dosya kenara alındı: $($_.Name)"
    }
  }
  Get-ChildItem $src -Filter *.jar -File | ForEach-Object { Copy-Item $_.FullName $mods -Force; Log "Kuruldu: $($_.Name)" }
  $vcOk = Get-VoiceChat $mods

  # Fabric 1.21.1 surumu: varsa dokunma (TLauncher/resmi launcher), yoksa ekle
  $fab = $null
  $vdir = Join-Path $mc 'versions'
  if (Test-Path $vdir) { $fab = Get-ChildItem $vdir -Directory | Where-Object { $_.Name -match 'fabric' -and $_.Name -match '1\.21\.1' } | Select-Object -First 1 }
  if ($fab) { $fabName = $fab.Name; Log "Fabric 1.21.1 sürümü zaten var: $fabName" }
  else {
    $json = Join-Path $Root ('istemci\' + $FabricName + '.json')
    if (-not (Test-Path $json)) { throw 'Paketteki Fabric sürüm dosyası eksik.' }
    $fabName = $FabricName
    $d = Join-Path $vdir $fabName
    New-Item -ItemType Directory -Force $d | Out-Null
    Copy-Item $json (Join-Path $d ($fabName + '.json')) -Force
    Log "Fabric 1.21.1 sürümü eklendi: $fabName (kütüphaneleri launcher ilk açılışta kendisi indirir)"
  }

  # resmi Minecraft Launcher profili (varsa; TLauncher bunu gerektirmez, sürümleri klasorden okur)
  $lp = Join-Path $mc 'launcher_profiles.json'
  if (Test-Path $lp) {
    try {
      Copy-Item $lp ($lp + '.kapisma_yedek') -Force
      $j = Get-Content -Raw -Encoding UTF8 $lp | ConvertFrom-Json
      if ($j.profiles -and -not ($j.profiles.PSObject.Properties.Name -contains 'kapisma')) {
        $prof = [pscustomobject]@{ name = 'Kapisma'; type = 'custom'; lastVersionId = $fabName; icon = 'Furnace' }
        $j.profiles | Add-Member -NotePropertyName 'kapisma' -NotePropertyValue $prof
        [IO.File]::WriteAllText($lp, ($j | ConvertTo-Json -Depth 30), (New-Object Text.UTF8Encoding($false)))
        Log "Resmi launcher'a 'Kapisma' profili eklendi (yedek: launcher_profiles.json.kapisma_yedek)"
      }
    } catch { Log "Not: launcher profili eklenemedi ($($_.Exception.Message)); TLauncher kullanıyorsan sorun değil." }
  }

  # Coklu Oyuncu listesi
  if ($address) {
    $sd = Join-Path $mc 'servers.dat'
    if (-not (Test-Path $sd)) { New-ServersDat $sd 'Kapisma' $address; Log "Sunucu 'Çoklu Oyuncu' listesine eklendi: $address" }
    else { Log "Çoklu Oyuncu listen zaten var; sunucuyu elle ekle (adres: $address)" }
  }
  Log ''
  if ($vcOk) { Log ("HAZIR. Launcher > sürüm: $fabName > Oyna.") } else { Log "TAMAMLANDI (ses modu hariç). Launcher > sürüm: $fabName > Oyna." }
  return $true
}

# ---------------------------------------------------------------- SUNUCU
function Install-Server([string]$dest, [string[]]$names, [string]$ramGb, [bool]$eula, [bool]$firewall, [bool]$start) {
  $src = Join-Path $Root 'sunucu'
  if (-not (Test-Path $src)) { throw "Paket eksik: $src (zip'i tamamen çıkardın mı?)" }
  if (-not $dest) { throw 'Sunucu klasörü boş.' }
  New-Item -ItemType Directory -Force $dest | Out-Null
  $keep = @('server.properties', 'eula.txt', 'izinli_oyuncular.txt', 'ram_mb.txt', 'profil.txt')
  $n = 0
  Get-ChildItem $src -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($src.Length).TrimStart('\')
    $t = Join-Path $dest $rel
    if ((Test-Path $t) -and ($keep -contains $rel -or $rel -like 'config\*')) { return }     # ev sahibinin ayarini ezme
    New-Item -ItemType Directory -Force (Split-Path -Parent $t) | Out-Null
    Copy-Item $_.FullName $t -Force
    $n++
  }
  Log "Sunucu dosyaları kopyalandı: $dest ($n dosya)"

  $good = @(); foreach ($x in $names) { $x = $x.Trim(); if (-not $x) { continue }; if (Test-Name $x) { $good += $x } else { Log "Atlandı (geçersiz Minecraft adı): $x" } }
  if ($good.Count -gt 0) {
    $txt = "# Sadece bu adlar sunucuya girebilir (satır başına bir ad). Boş bırakırsan beyaz liste kapalı olur.`r`n" + ($good -join "`r`n") + "`r`n"
    [IO.File]::WriteAllText((Join-Path $dest 'izinli_oyuncular.txt'), $txt, (New-Object Text.UTF8Encoding($false)))
    Log ("İzinli oyuncular yazıldı ($($good.Count) kişi): " + ($good -join ', '))
  } else { Log 'UYARI: Oyuncu adı girmedin; beyaz liste KAPALI. Sunucuyu sadece Tailscale/yerel ağda aç (herkes her adla girebilir).' }

  if ($ramGb) { [IO.File]::WriteAllText((Join-Path $dest 'ram_mb.txt'), ([int]([double]$ramGb * 1024)).ToString() + "`r`n"); Log "Bellek: $ramGb GB" }
  if ($eula) {
    [IO.File]::WriteAllText((Join-Path $dest 'eula.txt'), "#Mojang EULA https://aka.ms/MinecraftEULA (kurulum sihirbazinda kullanici onayladi)`r`neula=true`r`n")
    Log 'Mojang EULA onayın kaydedildi.'
  }
  Log ''
  Log 'Arkadaşlarının yazacağı adres:'
  Get-Addresses | ForEach-Object { Log ('   ' + $_) }
  if ($firewall) {
    Log 'Güvenlik duvarı izni isteniyor (açılan pencerede Evet de)...'
    Start-Process -FilePath (Join-Path $dest 'guvenlik_duvari_ac.bat') -WorkingDirectory $dest
  }
  if ($start) {
    Log 'Sunucu penceresi açılıyor. "Done" yazısını görünce hazır. İlk açılış 1-3 dk sürer.'
    Start-Process -FilePath (Join-Path $dest 'start.bat') -WorkingDirectory $dest
  }
  return $true
}

# ---------------------------------------------------------------- GUI
function Show-Page([string]$which) {
  $script:pgHome.Visible = ($which -eq 'home')
  $script:pgPlayer.Visible = ($which -eq 'player')
  $script:pgServer.Visible = ($which -eq 'server')
  $script:LogBox.Visible = ($which -ne 'home')
}

function Select-Folder($tb) {
  $d = New-Object System.Windows.Forms.FolderBrowserDialog
  $d.SelectedPath = $tb.Text
  if ($d.ShowDialog() -eq 'OK') { $tb.Text = $d.SelectedPath }
}

# test: kontrolun kendi gorunur bayragi (form henuz gosterilmediginde Control.Visible hep false doner)
function Get-Vis($c) {
  $m = [System.Windows.Forms.Control].GetMethod('GetState', [Reflection.BindingFlags]'NonPublic,Instance', $null, @([int]), $null)
  return [bool]$m.Invoke($c, @(2))
}

# test: dugmeye gercekten tikla (form gosterilmeden de calisir)
function Invoke-Click($btn) {
  $m = [System.Windows.Forms.Control].GetMethod('OnClick', [Reflection.BindingFlags]'NonPublic,Instance')
  [void]$m.Invoke($btn, @([EventArgs]::Empty))
}

function New-WizardForm {
  Add-Type -AssemblyName System.Windows.Forms
  Add-Type -AssemblyName System.Drawing
  [System.Windows.Forms.Application]::EnableVisualStyles()
  function C($type, $text, $x, $y, $w, $h) {
    $c = New-Object $type
    $c.Text = $text; $c.Location = New-Object Drawing.Point($x, $y); $c.Size = New-Object Drawing.Size($w, $h)
    return $c
  }
  $font = New-Object Drawing.Font('Segoe UI', 10)
  $big = New-Object Drawing.Font('Segoe UI', 12, [Drawing.FontStyle]::Bold)
  $f = C 'System.Windows.Forms.Form' 'Kapışma Kurulum Sihirbazı' 0 0 760 640
  $f.StartPosition = 'CenterScreen'; $f.Font = $font; $f.FormBorderStyle = 'FixedDialog'; $f.MaximizeBox = $false

  # ---- ana sayfa
  $script:pgHome = C 'System.Windows.Forms.Panel' '' 0 0 744 400
  $t1 = C 'System.Windows.Forms.Label' 'Kapışma - kurulum sihirbazı' 24 20 700 34; $t1.Font = New-Object Drawing.Font('Segoe UI', 18, [Drawing.FontStyle]::Bold)
  $t2 = C 'System.Windows.Forms.Label' ("Ne yapmak istiyorsun?`r`n`r`nSen sadece oyuna gireceksen 'Oyuncuyum' de: modlar doğru klasöre kendiliğinden kurulur.`r`nSunucuyu sen açacaksan 'Sunucu kuracağım' de.") 28 70 690 100
  $bp = C 'System.Windows.Forms.Button' "Oyuncuyum`r`n(arkadaşımın sunucusuna gireceğim)" 28 190 330 110; $bp.Font = $big
  $bs = C 'System.Windows.Forms.Button' "Sunucu kuracağım`r`n(host benim)" 380 190 330 110; $bs.Font = $big
  $t3 = C 'System.Windows.Forms.Label' "İnternetten sadece 'Simple Voice Chat' (sesli sohbet) indirilir ve SHA-512 ile doğrulanır.`r`nEski modların silinmez; mods\_kapisma_eski klasörüne taşınır." 28 330 690 60
  $script:pgHome.Controls.AddRange(@($t1, $t2, $bp, $bs, $t3))

  # ---- oyuncu sayfasi
  $script:pgPlayer = C 'System.Windows.Forms.Panel' '' 0 0 744 400; $script:pgPlayer.Visible = $false
  $p1 = C 'System.Windows.Forms.Label' 'Oyuncu kurulumu' 24 14 500 30; $p1.Font = $big
  $p2 = C 'System.Windows.Forms.Label' "Minecraft klasörü (TLauncher / resmi launcher aynı klasörü kullanır):" 28 54 690 24
  $script:tbMc = C 'System.Windows.Forms.TextBox' (Join-Path $env:APPDATA '.minecraft') 28 80 560 28
  $pBrowse = C 'System.Windows.Forms.Button' 'Seç...' 600 78 110 30
  $p3 = C 'System.Windows.Forms.Label' "Sunucu adresi (hostun verdiği; örn. 100.64.1.2 - boş bırakabilirsin):" 28 124 690 24
  $script:tbAddr = C 'System.Windows.Forms.TextBox' '' 28 150 560 28
  $p4 = C 'System.Windows.Forms.Label' "Önce Tailscale'i kur ve hostun davetini kabul et: https://tailscale.com" 28 190 690 24
  $script:btnPInstall = C 'System.Windows.Forms.Button' 'Kur' 28 232 200 50; $script:btnPInstall.Font = $big
  $pBack = C 'System.Windows.Forms.Button' 'Geri' 250 232 110 50
  $script:pgPlayer.Controls.AddRange(@($p1, $p2, $script:tbMc, $pBrowse, $p3, $script:tbAddr, $p4, $script:btnPInstall, $pBack))

  # ---- sunucu sayfasi
  $script:pgServer = C 'System.Windows.Forms.Panel' '' 0 0 744 400; $script:pgServer.Visible = $false
  $s1 = C 'System.Windows.Forms.Label' 'Sunucu kurulumu' 24 10 500 30; $s1.Font = $big
  $s2 = C 'System.Windows.Forms.Label' 'Sunucu nereye kurulsun:' 28 46 300 22
  $script:tbDest = C 'System.Windows.Forms.TextBox' (Join-Path $env:USERPROFILE 'kapisma-sunucu') 28 70 560 28
  $sBrowse = C 'System.Windows.Forms.Button' 'Seç...' 600 68 110 30
  $s3 = C 'System.Windows.Forms.Label' "Girmesine izin verilen oyuncular (her satıra bir Minecraft adı, SEN DAHİL):" 28 108 690 22
  $script:tbNames = C 'System.Windows.Forms.TextBox' '' 28 132 330 120; $script:tbNames.Multiline = $true; $script:tbNames.ScrollBars = 'Vertical'
  $s4 = C 'System.Windows.Forms.Label' 'Bellek (RAM):' 380 132 200 22
  $script:cbRam = C 'System.Windows.Forms.ComboBox' '' 380 156 200 28; $script:cbRam.DropDownStyle = 'DropDownList'
  [void]$script:cbRam.Items.AddRange(@('Otomatik (önerilir)', '2 GB', '3 GB', '4 GB', '6 GB', '8 GB')); $script:cbRam.SelectedIndex = 0
  $script:ckEula = C 'System.Windows.Forms.CheckBox' "Mojang EULA'yı (aka.ms/MinecraftEULA) okudum, kabul ediyorum" 28 262 690 26
  $script:ckFw = C 'System.Windows.Forms.CheckBox' 'Güvenlik duvarı izni ver (oyun TCP 25565, ses UDP 24454; yönetici izni sorar)' 28 290 690 26; $script:ckFw.Checked = $true
  $script:ckStart = C 'System.Windows.Forms.CheckBox' 'Kurulunca sunucuyu başlat' 28 318 400 26; $script:ckStart.Checked = $true
  $script:btnSInstall = C 'System.Windows.Forms.Button' 'Kur' 28 352 200 44; $script:btnSInstall.Font = $big
  $sBack = C 'System.Windows.Forms.Button' 'Geri' 250 352 110 44
  $script:pgServer.Controls.AddRange(@($s1, $s2, $script:tbDest, $sBrowse, $s3, $script:tbNames, $s4, $script:cbRam, $script:ckEula, $script:ckFw, $script:ckStart, $script:btnSInstall, $sBack))

  $script:LogBox = C 'System.Windows.Forms.TextBox' '' 14 406 716 188
  $script:LogBox.Multiline = $true; $script:LogBox.ReadOnly = $true; $script:LogBox.ScrollBars = 'Vertical'; $script:LogBox.Font = New-Object Drawing.Font('Consolas', 9)
  $script:LogBox.Visible = $false
  $f.Controls.AddRange(@($script:pgHome, $script:pgPlayer, $script:pgServer, $script:LogBox))

  $script:btnHomePlayer = $bp; $script:btnHomeServer = $bs; $script:btnBackP = $pBack; $script:btnBackS = $sBack
  $bp.Add_Click({ Show-Page 'player' })
  $bs.Add_Click({ Show-Page 'server' })
  $pBack.Add_Click({ Show-Page 'home' })
  $sBack.Add_Click({ Show-Page 'home' })
  $pBrowse.Add_Click({ Select-Folder $script:tbMc })
  $sBrowse.Add_Click({ Select-Folder $script:tbDest })

  $script:btnPInstall.Add_Click({
    $script:btnPInstall.Enabled = $false
    try { [void](Install-Player $script:tbMc.Text.Trim() $script:tbAddr.Text.Trim()) }
    catch { Log ("HATA: " + $_.Exception.Message); [void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Kapışma', 'OK', 'Error') }
    finally { $script:btnPInstall.Enabled = $true }
  })
  $script:btnSInstall.Add_Click({
    if (-not $script:ckEula.Checked) { [void][System.Windows.Forms.MessageBox]::Show("Sunucuyu çalıştırmak için Mojang EULA'yı kabul etmelisin (kutucuğu işaretle).", 'Kapışma'); return }
    $script:btnSInstall.Enabled = $false
    try {
      $ram = ''
      if ($script:cbRam.SelectedIndex -gt 0) { $ram = ($script:cbRam.SelectedItem -replace ' GB', '') }
      $names = $script:tbNames.Text -split "[\r\n,;]+"
      [void](Install-Server $script:tbDest.Text.Trim() $names $ram $true $script:ckFw.Checked $script:ckStart.Checked)
    } catch { Log ("HATA: " + $_.Exception.Message); [void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Kapışma', 'OK', 'Error') }
    finally { $script:btnSInstall.Enabled = $true }
  })
  return $f
}

# ---------------------------------------------------------------- giris
if ($SelfTest) {
  $f = New-WizardForm
  function Expect($c, $what) { if (-not $c) { Write-Host "SELFTEST HATA: $what"; exit 3 } }
  Expect ((Get-Vis $script:pgHome) -and -not (Get-Vis $script:pgPlayer) -and -not (Get-Vis $script:pgServer)) 'baslangic: sadece ana sayfa gorunmeli'
  Invoke-Click $script:btnHomePlayer
  Expect ((Get-Vis $script:pgPlayer) -and -not (Get-Vis $script:pgHome) -and (Get-Vis $script:LogBox)) 'Oyuncuyum tiklaninca oyuncu sayfasi acilmali'
  Invoke-Click $script:btnBackP
  Expect ((Get-Vis $script:pgHome) -and -not (Get-Vis $script:pgPlayer) -and -not (Get-Vis $script:LogBox)) 'Geri tiklaninca ana sayfa donmeli'
  Invoke-Click $script:btnHomeServer
  Expect ((Get-Vis $script:pgServer) -and -not (Get-Vis $script:pgHome)) 'Sunucu kuracagim tiklaninca sunucu sayfasi acilmali'
  Invoke-Click $script:btnBackS
  Expect (Get-Vis $script:pgHome) 'Geri (sunucu) ana sayfaya donmeli'
  # Kur dugmeleri: gecici klasore gercek kurulum
  if ($McDir) {
    $script:tbMc.Text = $McDir; $script:tbAddr.Text = '10.1.2.3'
    Invoke-Click $script:btnPInstall
    Expect (Test-Path (Join-Path $McDir 'mods')) 'Oyuncu Kur dugmesi mods klasorunu olusturmali'
  }
  if ($ServerDir) {
    $script:tbDest.Text = $ServerDir; $script:tbNames.Text = "Ahmet_34`r`nZeynep"; $script:ckEula.Checked = $true; $script:ckFw.Checked = $false; $script:ckStart.Checked = $false
    Invoke-Click $script:btnSInstall
    Expect (Test-Path (Join-Path $ServerDir 'start.bat')) 'Sunucu Kur dugmesi start.bat kopyalamali'
    Expect ((Get-Content (Join-Path $ServerDir 'eula.txt') -Raw) -match 'eula=true') 'EULA onayi yazilmali'
  }
  "SELFTEST OK: form kuruldu, sayfa gecisleri ve Kur dugmeleri calisti"
  $f.Dispose()
  exit 0
}
if ($Headless) {
  if ($Role -eq 'Player') { [void](Install-Player $McDir $Address) }
  elseif ($Role -eq 'Server') {
    [void](Install-Server $ServerDir ($Names -split '[,;]+') $Ram $AcceptEula.IsPresent (-not $NoFirewall.IsPresent) (-not $NoStart.IsPresent))
  } else { Write-Host '-Role Player veya Server gerekli'; exit 2 }
  exit 0
}
$form = New-WizardForm
[void]$form.ShowDialog()
