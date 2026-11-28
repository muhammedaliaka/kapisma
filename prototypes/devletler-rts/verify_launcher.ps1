# Read-only acceptance of the installed RTS launcher (no Minecraft world startup).
param([switch]$SkipTailnet, [switch]$WithNatural)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../_yedek/devletler-rts-prototip')).Path
$installed = Join-Path $root 'start_tailnet.ps1'
$source = Get-Content (Join-Path $PSScriptRoot 'start_tailnet.ps1') -Raw -Encoding UTF8
$installedSource = [IO.File]::ReadAllText($installed)
if ($source.Replace("`r`n", "`n") -cne $installedSource.Replace("`r`n", "`n")) { throw 'Installed/source mismatch' }
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseInput($source, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw ($errors | Out-String) }
foreach ($node in $ast.FindAll({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst]}, $false)) {
    . ([scriptblock]::Create($node.Extent.Text))
}
function Expect-Rejection([scriptblock]$action) {
    $rejected = $false
    try { & $action } catch { $rejected = $true }
    if (-not $rejected) { throw 'Invalid fixture accepted' }
}
function Hashes {
    $paths = @('server.properties', 'world/datapacks/devletler-rts.zip', 'preset-classic.zip', 'preset-diplomacy.zip')
    if ($WithNatural) { $paths += 'preset-natural-classic.zip', 'preset-natural-diplomacy.zip' }
    ($paths + 'mods/kapisma-rts-rules.jar') |
        ForEach-Object { (Get-FileHash (Join-Path $root $_)).Hash }
}
function Invoke-Check([string]$mode, [string]$expected = '') {
    $ErrorActionPreference = 'Continue'
    if ($mode -eq 'natural') {
        $output = & powershell.exe -NoProfile -File $installed -Natural -Local -CheckOnly 2>&1
    } elseif ($mode -eq 'local') {
        $output = & powershell.exe -NoProfile -File $installed -Local -CheckOnly 2>&1
    } else {
        $output = & powershell.exe -NoProfile -File $installed -Preset $mode -CheckOnly 2>&1
    }
    $rc = $LASTEXITCODE
    if ($expected) {
        if ($rc -eq 0 -or ($output | Out-String) -notmatch $expected) { throw "Unexpected rejection: $output ($rc)" }
    } elseif ($rc -ne 0) { throw "Unexpected failure: $output ($rc)" }
    Write-Output "PASS check $mode $expected"
}

$before = @(Hashes)
$settings = Get-Content (Join-Path $root 'server.properties') -Raw
Assert-RtsSettings $settings
'PASS actual settings'
foreach ($bad in @($settings.Replace('server-port=25576','server-port=25577'),
        ($settings + "`nserver-ip=0.0.0.0"),
        $settings.Replace('online-mode=true','online-mode-missing=true'),
        $settings.Replace('level-name=world','level-name=other'))) {
    Expect-Rejection { Assert-RtsSettings $bad }
}
'PASS four malformed settings rejected'
foreach ($pack in @('world/datapacks/devletler-rts.zip','preset-classic.zip','preset-diplomacy.zip')) {
    Assert-RtsPack (Join-Path $root $pack)
    "PASS archive structure $pack"
}
Expect-Rejection { Assert-RtsPack (Join-Path $root 'server.properties') }
'PASS non-ZIP rejected'
$oldHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = Join-Path $root '__nonexistent_java_home__'
    $java = Resolve-RtsJava
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'No usable Java' }
    'PASS invalid JAVA_HOME fallback'
} finally { $env:JAVA_HOME = $oldHome }

Invoke-Check local
if ($WithNatural) {
    Assert-RtsPack (Join-Path $root 'preset-natural-classic.zip') -NaturalPack
    Assert-RtsPack (Join-Path $root 'preset-natural-diplomacy.zip') -NaturalPack
    Expect-Rejection { Assert-RtsPack (Join-Path $root 'preset-classic.zip') -NaturalPack }
    $naturalSettings = Set-RtsProperty $settings 'level-type' 'minecraft\:normal'
    if ($naturalSettings -notmatch '(?m)^level-type=minecraft\\:normal\r?$') { throw 'Natural level type missing' }
    Invoke-Check natural
}
if (-not $SkipTailnet) { Invoke-Check classic; Invoke-Check diplomacy }
$lock = [IO.File]::Open((Join-Path $root 'rts-launch.lock'),'Open','ReadWrite','None')
try { Invoke-Check local 'RTS profili zaten' } finally { $lock.Dispose() }
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,25576)
try {
    $listener.Start()
    Invoke-Check local 'RTS sunucusunu'
} finally { $listener.Stop() }
$after = @(Hashes)
if (($before -join ',') -ne ($after -join ',')) { throw 'Profile files changed during checks' }
"PASS $($before.Count) profile hashes unchanged"
"ADAPTER=$($after[-1])"
'PASS launcher acceptance'
