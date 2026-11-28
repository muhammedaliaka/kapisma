param(
    [ValidateSet('classic', 'diplomacy')][string]$Preset = 'classic',
    [switch]$Local,
    [switch]$Natural,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$address = '127.0.0.1'
if (-not $Local) {
    $addresses = @([Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
        Where-Object { $_.OperationalStatus -eq 'Up' -and ($_.Name -match '^Tailscale$' -or $_.Description -match '^Tailscale(?: Tunnel)?$') } |
        ForEach-Object { $_.GetIPProperties().UnicastAddresses } |
        Where-Object {
            $_.Address.AddressFamily -eq 'InterNetwork' -and
            $_.Address.IPAddressToString -match '^100\.(?:6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.\d{1,3}\.\d{1,3}$'
        } |
        ForEach-Object { $_.Address.IPAddressToString } |
        Select-Object -Unique)
    if ($addresses.Count -ne 1) { throw 'Tailscale bağdaştırıcısında tek bir IPv4 adresi bulunamadı.' }
    $address = $addresses[0]
}
function Resolve-RtsJava {
    $candidates = @()
    foreach ($homePath in @($env:JAVA_HOME, [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine'))) {
        if ($homePath) { $candidates += Join-Path $homePath 'bin/java.exe' }
    }
    $candidates += @(Get-Command java.exe -CommandType Application -All -ErrorAction SilentlyContinue |
        ForEach-Object { $_.Source })
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $info = [Diagnostics.ProcessStartInfo]::new()
        $info.FileName = $candidate
        $info.Arguments = '-version'
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardError = $true
        $info.RedirectStandardOutput = $true
        $process = [Diagnostics.Process]::new()
        $process.StartInfo = $info
        try {
            if (-not $process.Start()) { continue }
            $stderr = $process.StandardError.ReadToEndAsync()
            $stdout = $process.StandardOutput.ReadToEndAsync()
            if (-not $process.WaitForExit(10000)) { $process.Kill(); continue }
            $version = $stderr.GetAwaiter().GetResult() + $stdout.GetAwaiter().GetResult()
            if ($process.ExitCode -eq 0 -and $version -match '(?m)^(?:openjdk|java) version "21(?:[.\-+]|")') {
                return $candidate
            }
        } catch { continue } finally { $process.Dispose() }
    }
    throw 'Java 21 bulunamadı. JAVA_HOME veya PATH üzerinde Java 21 kurulumunu doğrulayın.'
}

function Assert-RtsSettings([string]$settingsText) {
    foreach ($key in @('server-ip', 'server-port', 'online-mode', 'level-name')) {
        if ([regex]::Matches($settingsText, "(?m)^$key=.*$").Count -ne 1) {
            throw "RTS server.properties: $key tek kez tanımlanmalı."
        }
    }
    if ($settingsText -notmatch '(?m)^server-port=25576\r?$' -or
        $settingsText -notmatch '(?m)^level-name=world\r?$') {
        throw 'RTS profili yalnız 25576 portu ve world dünyasıyla başlatılabilir.'
    }
}

function Assert-RtsPack([string]$path, [switch]$NaturalPack) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($path)
    try {
        $entries = @('pack.mcmeta', 'data/krts/function/load.mcfunction', 'data/krts/function/start.mcfunction')
        if ($NaturalPack) { $entries += 'data/krts/function/natural/open.mcfunction' }
        foreach ($name in $entries) {
            if (@($zip.Entries | Where-Object FullName -eq $name).Count -ne 1) {
                throw "RTS veri paketi eksik veya tekrar eden kayıt içeriyor: $name"
            }
        }
    } finally { $zip.Dispose() }
}

function Set-RtsProperty([string]$settingsText, [string]$key, [string]$value) {
    $pattern = '(?m)^' + [regex]::Escape($key) + '=[^\r\n]*'
    $matches = [regex]::Matches($settingsText, $pattern)
    if ($matches.Count -gt 1) { throw "RTS server.properties: $key birden fazla tanımlanmış." }
    if ($matches.Count -eq 0) { return $settingsText.TrimEnd() + "`n$key=$value`n" }
    return [regex]::Replace($settingsText, $pattern, "$key=$value")
}

$lockFile = Join-Path $PSScriptRoot 'rts-launch.lock'
try {
    $lock = [IO.File]::Open($lockFile, 'OpenOrCreate', 'ReadWrite', 'None')
} catch [IO.IOException] {
    if (($_.Exception.GetBaseException().HResult -band 65535) -in @(32, 33)) {
        throw 'Bu RTS profili zaten açılıyor, çalışıyor veya hazırlanıyor.'
    }
    throw
}
try {
    $properties = Join-Path $PSScriptRoot 'server.properties'
    $pack = Join-Path $PSScriptRoot 'world/datapacks/devletler-rts.zip'
    $presetPath = Join-Path $PSScriptRoot "preset-$Preset.zip"
    $settings = [IO.File]::ReadAllBytes($properties)
    Assert-RtsSettings ([Text.Encoding]::ASCII.GetString($settings))
    foreach ($required in @('fabric-server-launch.jar', 'eula.txt',
        'mods/fabric-api-0.116.18+1.21.1.jar',
        'mods/HundredYearsWar-0.8.0r-fix2-1.21.1-fabric.jar',
        'mods/kapisma-rts-rules.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $required) -PathType Leaf)) {
            throw "RTS çalışma dosyası eksik: $required"
        }
    }
    if ($Natural) {
        $world = Join-Path $PSScriptRoot 'world-rts-natural'
        $pack = Join-Path $world 'datapacks/devletler-rts.zip'
        $presetPath = Join-Path $PSScriptRoot "preset-natural-$Preset.zip"
        foreach ($path in @($world, (Join-Path $world 'datapacks'), $pack)) {
            if ((Test-Path -LiteralPath $path) -and
                ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw 'Doğal RTS dünyası için doğrudan profil yolları kullanılmalı.'
            }
        }
        Assert-RtsPack $presetPath -NaturalPack
        if (Test-Path -LiteralPath $pack -PathType Leaf) { Assert-RtsPack $pack -NaturalPack }
    } else {
        Assert-RtsPack $pack
        if (-not $Local) { Assert-RtsPack $presetPath }
    }
    $java = Resolve-RtsJava
    $activePack = if (-not $Local -and -not $Natural) { [IO.File]::ReadAllBytes($pack) }
    $originalIp = ([Text.Encoding]::ASCII.GetString($settings) -split '\r?\n' |
        Where-Object { $_ -like 'server-ip=*' } | Select-Object -First 1) -replace '^server-ip=', ''
    foreach ($endpoint in (@('127.0.0.1', $address, $originalIp) | Where-Object { $_ } | Select-Object -Unique)) {
        $client = [Net.Sockets.TcpClient]::new()
        try { $client.Connect($endpoint, 25576) } catch [Net.Sockets.SocketException] {} finally {
            $connected = $client.Connected
            $client.Dispose()
        }
        if ($connected) { throw 'Devam etmek için çalışan RTS sunucusunu kapatın.' }
    }

    if ($CheckOnly) {
        Write-Output 'RTS bağlantı noktası, Java 21, profil kilidi ve gerekli dosyalar doğrulandı.'
        exit 0
    }
    Push-Location -LiteralPath $PSScriptRoot
    try {
        $configured = [Text.Encoding]::ASCII.GetString($settings) -replace '(?m)^server-ip=.*$', "server-ip=$address"
        $configured = $configured -replace '(?m)^online-mode=.*$', 'online-mode=true'
        if ($Natural) {
            $worldOptions = @{
                'level-name' = 'world-rts-natural'; 'level-type' = 'minecraft\:normal'
                'generator-settings' = '{}'; 'level-seed' = '5743893257777525637'; 'generate-structures' = 'false'
            }
            foreach ($key in $worldOptions.Keys) { $configured = Set-RtsProperty $configured $key $worldOptions[$key] }
            [IO.Directory]::CreateDirectory((Split-Path -Parent $pack)) | Out-Null
            Copy-Item -LiteralPath $presetPath -Destination $pack -Force
            Write-Output "Doğal RTS adresi $($address):25576 · Kural $Preset"
        }
        [IO.File]::WriteAllText($properties, $configured, [Text.Encoding]::ASCII)
        if (-not $Local -and -not $Natural) {
            Copy-Item -LiteralPath $presetPath -Destination $pack -Force
            Write-Output "Tailscale adresi $($address):25576 · Yeni maç kuralı $Preset"
        } elseif (-not $Natural) {
            Write-Output "Yerel adres $($address):25576"
        }
        & $java -Xmx3G -jar fabric-server-launch.jar nogui
        $code = $LASTEXITCODE
    } finally {
        [IO.File]::WriteAllBytes($properties, $settings)
        if (-not $Local -and -not $Natural) { [IO.File]::WriteAllBytes($pack, $activePack) }
        Pop-Location
    }
} finally {
    $lock.Dispose()
}
exit $code
