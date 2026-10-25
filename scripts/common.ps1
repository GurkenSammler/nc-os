# Gemeinsame Helfer fuer alle Skripte. Laden mit:  . "$PSScriptRoot\common.ps1"
#
# state.tsv ist das Gedaechtnis des Projekts. Jede Aenderung am TV wird VORHER dort
# eingetragen, damit restore.ps1 genau das rueckgaengig machen kann. Eine Zeile
# "restore" schliesst eine Runde ab; restore.ps1 betrachtet nur die Zeilen danach.
#   pkg      <paket>  disabled  <kategorie>
#   setting  <ns>     <key>     <alter wert | null>
#   home     <komponente vor unserem launcher>
#   removed  <paket>  <ordner mit gesicherten APKs>
#   grant    appop|admin  <name>   (Freigabe fuer den Launcher; restore nimmt sie zurueck)
#   restore  <zeit>

$ErrorActionPreference = 'Stop'

$Root          = Split-Path $PSScriptRoot -Parent
$Backups       = Join-Path $Root 'backups'
$Screens       = Join-Path $Backups 'screens'
$StateFile     = Join-Path $Backups 'state.tsv'
$DeviceFile    = Join-Path $Root '.device'
$ListsDir      = Join-Path $Root 'debloat'
# Paketname der App: Standard, oder "ncos.applicationId=..." in launcher\gradle.properties.
# Die Klassen liegen immer im Code-Namespace; bei eigenem Paketnamen braucht es den vollen Klassennamen.
$LauncherNs    = 'dev.david.braviahome'
$LauncherPkg   = $LauncherNs
$gradleProps   = Join-Path $Root 'launcher\gradle.properties'
if (Test-Path $gradleProps) {
    $m = Select-String -Path $gradleProps -Pattern '^\s*ncos\.applicationId\s*=\s*(\S+)' | Select-Object -First 1
    if ($m) { $LauncherPkg = $m.Matches[0].Groups[1].Value }
}
$LauncherCls   = if ($LauncherPkg -eq $LauncherNs) { "$LauncherPkg/." } else { "$LauncherPkg/$LauncherNs." }
$LauncherHome  = "${LauncherCls}HomeActivity"
$LauncherDream = "${LauncherCls}dream.ScreensaverService"
$LauncherAdmin = "${LauncherCls}system.StandbyAdmin"
$LauncherQuick = "${LauncherCls}quick.QuickPanelService"
$LauncherTest  = "${LauncherCls}quick.QuickTestReceiver"
$LauncherApk   = Join-Path $Root 'launcher\app\build\outputs\apk\release\app-release.apk'

New-Item -ItemType Directory -Force $Backups, $Screens | Out-Null

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @("$env:LOCALAPPDATA\Microsoft\WinGet\Links\adb.exe",
                     "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")) {
        if (Test-Path $p) { return $p }
    }
    throw 'adb nicht gefunden. Installieren mit: winget install Google.PlatformTools'
}
$Adb = Find-Adb

function Get-Device {
    if (-not (Test-Path $DeviceFile)) { throw 'Kein TV verbunden. Zuerst: scripts\connect.ps1 <IP>' }
    (Get-Content $DeviceFile -TotalCount 1).Trim()
}

# adb mit dem gespeicherten TV. stderr wird zu Text, damit PowerShell 5.1 nicht abbricht.
function Invoke-Adb {
    $dev = Get-Device
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & $Adb -s $dev @args 2>&1 | ForEach-Object { "$_" } }
    finally { $ErrorActionPreference = $prev }
    $script:AdbExit = $LASTEXITCODE
    return $out
}

function Invoke-Shell([string]$Command) { Invoke-Adb shell $Command }

function Test-Tv {
    $r = Invoke-Shell 'echo ok'
    return ($r -join '') -eq 'ok'
}

# Pakete als Liste von Namen. -Disabled = nur ausgeschaltete.
function Get-Packages([switch]$Disabled) {
    $flag = if ($Disabled) { '-d' } else { '' }
    Invoke-Shell "pm list packages $flag" |
        Where-Object { $_ -like 'package:*' } |
        ForEach-Object { $_.Substring(8).Trim() }
}

function Get-HomeComponent {
    $lines = Invoke-Shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' |
        Where-Object { $_.Trim() -ne '' }
    return ($lines | Select-Object -Last 1).Trim()
}

# Wer die Rolle "Startbildschirm" hat (Android 10+). Leer, wenn niemand.
function Get-HomeRoleHolder {
    $lines = Invoke-Shell 'dumpsys role'
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Trim() -eq 'name=android.app.role.HOME') {
            return ($lines[$i + 1].Trim() -replace '^holders=', '')
        }
    }
    return ''
}

# Einzelne Activity/Komponente ("paket/.Klasse") statt ganzes Paket?
function Test-IsComponent([string]$Entry) { return $Entry.Contains('/') }

function Test-ComponentDisabled([string]$Component) {
    $pkg, $cls = $Component.Split('/', 2)
    $full = if ($cls.StartsWith('.')) { $pkg + $cls } else { $cls }
    $inBlock = $false
    foreach ($line in Invoke-Shell "dumpsys package $pkg") {
        $t = $line.Trim()
        if ($t -eq 'disabledComponents:') { $inBlock = $true; continue }
        if ($inBlock) {
            if ($t -eq '' -or $t.EndsWith(':')) { $inBlock = $false; continue }
            if ($t -eq $full) { return $true }
        }
    }
    return $false
}

# Liste lesen: eine Zeile pro Paket, alles nach # ist Kommentar.
function Read-List([string]$Path) {
    if (-not (Test-Path $Path)) { throw "Liste fehlt: $Path" }
    Get-Content $Path | ForEach-Object { ($_ -split '#', 2)[0].Trim() } | Where-Object { $_ -ne '' }
}

function Test-NeverTouch([string]$Package, [string[]]$NeverList) {
    foreach ($pattern in $NeverList) { if ($Package -like $pattern) { return $true } }
    return $false
}

function Add-State {
    $line = ((Get-Date -Format 's'), ($args -join "`t")) -join "`t"
    Add-Content -Path $StateFile -Value $line -Encoding UTF8
}

# Zeilen der aktuellen Runde (nach dem letzten "restore") als Objekte.
function Get-EpochEntries {
    if (-not (Test-Path $StateFile)) { return @() }
    $rows = @(Get-Content $StateFile -Encoding UTF8 | ForEach-Object {
        $f = $_ -split "`t"
        [pscustomobject]@{ Time = $f[0]; Kind = $f[1]; A = $f[2]; B = $f[3]; C = $f[4] }
    })
    $last = -1
    for ($i = 0; $i -lt $rows.Count; $i++) { if ($rows[$i].Kind -eq 'restore') { $last = $i } }
    if ($last -ge 0) { $rows = $rows | Select-Object -Skip ($last + 1) }
    return @($rows)
}

# Aktuellen Wert eines Settings merken (nur beim ersten Mal pro Runde), ohne ihn zu aendern.
# Fuer Werte, die spaeter der Launcher selbst aendert (z. B. Bildschirmschoner-Zeit).
function Save-TvSetting([string]$Namespace, [string]$Key) {
    $known = Get-EpochEntries | Where-Object { $_.Kind -eq 'setting' -and $_.A -eq $Namespace -and $_.B -eq $Key }
    if (-not $known) {
        $old = (Invoke-Shell "settings get $Namespace $Key" | Select-Object -First 1).Trim()
        Add-State setting $Namespace $Key $old
    }
}

# Setting aendern und den alten Wert (nur beim ersten Mal pro Runde) merken.
function Set-TvSetting([string]$Namespace, [string]$Key, [string]$Value) {
    Save-TvSetting $Namespace $Key
    Invoke-Shell "settings put $Namespace $Key $Value" | Out-Null
}

# Screenshot vom TV nach backups\screens\<name>.png, gibt den Pfad zurueck.
function Save-Screenshot([string]$Name = 'screen') {
    $file = Join-Path $Screens ('{0}_{1}.png' -f (Get-Date -Format 'yyyyMMdd_HHmmss'), $Name)
    Invoke-Shell 'screencap -p /sdcard/bravia_shot.png' | Out-Null
    Invoke-Adb pull /sdcard/bravia_shot.png $file | Out-Null
    Invoke-Shell 'rm /sdcard/bravia_shot.png' | Out-Null
    return $file
}

function New-Stamp { Get-Date -Format 'yyyy-MM-dd_HHmmss' }

# Sonys Fernsteuer-API (JSON-RPC, siehe enable-sony-api.ps1). Gibt das Ergebnis als Text zurueck.
$SonyApiPkg = 'com.sony.dtv.webapi.core'
$SonyPskFile = Join-Path $Backups 'sony-api-psk.txt'
function Invoke-SonyApi([string]$Service, [string]$Method, [object[]]$Params = @(), [string]$Version = '1.0') {
    $ip = (Get-Device).Split(':')[0]
    $body = @{ method = $Method; id = 1; params = $Params; version = $Version } | ConvertTo-Json -Depth 8 -Compress
    $headers = @{}
    if (Test-Path $SonyPskFile) { $headers['X-Auth-PSK'] = (Get-Content $SonyPskFile -TotalCount 1).Trim() }
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri "http://$ip/sony/$Service" -Method Post -Body $body `
            -ContentType 'application/json' -Headers $headers -TimeoutSec 8
        return $r.Content
    } catch { return "FEHLER: $($_.Exception.Message)" }
}
