# Installiert den eigenen Launcher, macht ihn zum Startbildschirm und setzt den
# eigenen Bildschirmschoner. Alte Werte werden fuer restore.ps1 gemerkt.
#   scripts\install-launcher.ps1
#   scripts\install-launcher.ps1 -NoHome           (nur installieren/aktualisieren)
param(
    [string]$Apk,
    [switch]$NoHome,
    [switch]$NoScreensaver,
    [switch]$NoQuickPanel
)

. "$PSScriptRoot\common.ps1"
if (-not $Apk) { $Apk = $LauncherApk }
if (-not (Test-Path $Apk)) { throw "APK fehlt: $Apk  -> zuerst bauen: launcher\gradlew.bat assembleRelease" }
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

$out = (Invoke-Adb install -r $Apk) -join ' '
if ($out -notmatch 'Success') { throw "Installation fehlgeschlagen: $out" }
Write-Host 'Launcher installiert.' -ForegroundColor Green

if (-not $NoHome) {
    $current = Get-HomeComponent
    $known = Get-EpochEntries | Where-Object { $_.Kind -eq 'home' }
    if (-not $known -and $current -notlike "$LauncherPkg/*") { Add-State home $current }
    $r = (Invoke-Shell "cmd package set-home-activity $LauncherHome") -join ' '
    Write-Host "set-home-activity: $r"
    Write-Host "HOME jetzt: $(Get-HomeComponent)"
}

if (-not $NoScreensaver) {
    Set-TvSetting secure screensaver_components $LauncherDream
    Set-TvSetting secure screensaver_default_component $LauncherDream
    Set-TvSetting secure screensaver_enabled 1
    Write-Host 'Bildschirmschoner gesetzt.'
}

# Freigaben fuer SYS.CONFIG: Bildschirmschoner-Zeit aendern (WRITE_SETTINGS) und Standby
# (Geraeteadministrator nur mit "Bildschirm sperren"). Beides nimmt restore.ps1 zurueck.
$grants = Get-EpochEntries | Where-Object { $_.Kind -eq 'grant' }
Save-TvSetting system screen_off_timeout
Invoke-Shell "appops set $LauncherPkg WRITE_SETTINGS allow" | Out-Null
if (-not ($grants | Where-Object { $_.A -eq 'appop' -and $_.B -eq 'WRITE_SETTINGS' })) { Add-State grant appop WRITE_SETTINGS }

# TV-Menues (ab v0.10): WLAN-Scan braucht Standort, Bluetooth-Liste BLUETOOTH_CONNECT,
# App-Groessen die Nutzungsstatistik. Ebenfalls in state.tsv, restore.ps1 nimmt sie zurueck.
foreach ($perm in 'android.permission.ACCESS_FINE_LOCATION', 'android.permission.BLUETOOTH_CONNECT') {
    Invoke-Shell "pm grant $LauncherPkg $perm" | Out-Null
    if (-not ($grants | Where-Object { $_.A -eq 'perm' -and $_.B -eq $perm })) { Add-State grant perm $perm }
}
Invoke-Shell "appops set $LauncherPkg GET_USAGE_STATS allow" | Out-Null
if (-not ($grants | Where-Object { $_.A -eq 'appop' -and $_.B -eq 'GET_USAGE_STATS' })) { Add-State grant appop GET_USAGE_STATS }
$admin = (Invoke-Shell "dpm set-active-admin --user 0 $LauncherAdmin") -join ' '
if ($admin -match 'Success|already') {
    if (-not ($grants | Where-Object { $_.A -eq 'admin' })) { Add-State grant admin $LauncherAdmin }
    Write-Host 'Freigaben fuer SYS.CONFIG erteilt (Schoner-Zeit, Standby).'
} else {
    Write-Warning "Standby-Freigabe fehlgeschlagen: $admin"
}

# Schnellmenue: Bedienungshilfe-Dienst, der die Zahnrad-Taste abfaengt. Alte Werte merkt
# Save-TvSetting, restore.ps1 stellt sie wieder her (= Sonys Schnelleinstellungen zurueck).
if (-not $NoQuickPanel) {
    Save-TvSetting secure enabled_accessibility_services
    Save-TvSetting secure accessibility_enabled
    $current = ((Invoke-Shell 'settings get secure enabled_accessibility_services') -join '').Trim()
    $list = @($current.Split(':') | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne $LauncherQuick })
    $list += $LauncherQuick
    Invoke-Shell ("settings put secure enabled_accessibility_services {0}" -f ($list -join ':')) | Out-Null
    Invoke-Shell 'settings put secure accessibility_enabled 1' | Out-Null
    Write-Host 'Schnellmenue aktiv (Zahnrad-Taste).'
}

Invoke-Shell 'input keyevent KEYCODE_HOME' | Out-Null
Start-Sleep -Seconds 2
Write-Host "Screenshot: $(Save-Screenshot 'launcher')"
