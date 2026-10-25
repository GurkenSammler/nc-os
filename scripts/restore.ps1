# Macht alles rueckgaengig, was debloat.ps1 und install-launcher.ps1 in dieser Runde
# geaendert haben: Pakete wieder an, alte Settings zurueck, alter Startbildschirm.
# Deine Apps, Konten und Einstellungen bleiben.
#   scripts\restore.ps1                   (eigener Launcher bleibt installiert)
#   scripts\restore.ps1 -RemoveLauncher   (eigenen Launcher auch deinstallieren)
param([switch]$RemoveLauncher)

. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

$entries = Get-EpochEntries
if ($entries.Count -eq 0) { Write-Host 'Nichts zurueckzusetzen.'; exit 0 }

# 1. Pakete in umgekehrter Reihenfolge wieder einschalten.
$pkgs = @($entries | Where-Object { $_.Kind -eq 'pkg' -and $_.B -eq 'disabled' } | ForEach-Object { $_.A } | Select-Object -Unique)
[array]::Reverse($pkgs)
foreach ($pkg in $pkgs) {
    $out = (Invoke-Shell "pm enable $pkg") -join ' '
    Write-Host "  an: $pkg  ($out)" -ForegroundColor DarkGray
}

# 1b. Geloeschte Apps aus der APK-Sicherung neu installieren (ohne ihre alten Daten).
foreach ($r in ($entries | Where-Object { $_.Kind -eq 'removed' })) {
    $apks = @(Get-ChildItem $r.B -Filter *.apk -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    if ($apks.Count -eq 0) { Write-Warning "Keine Sicherung fuer $($r.A) in $($r.B)"; continue }
    $out = (Invoke-Adb install-multiple -r @apks) -join ' '
    Write-Host "  neu installiert: $($r.A)  ($out)" -ForegroundColor DarkGray
}

# 2. Settings auf den ersten gemerkten Wert zurueck.
$settings = $entries | Where-Object { $_.Kind -eq 'setting' } | Group-Object A, B | ForEach-Object { $_.Group[0] }
foreach ($s in $settings) {
    if ($s.C -eq 'null' -or [string]::IsNullOrEmpty($s.C)) {
        Invoke-Shell "settings delete $($s.A) $($s.B)" | Out-Null
    } else {
        Invoke-Shell "settings put $($s.A) $($s.B) $($s.C)" | Out-Null
    }
    Write-Host "  setting $($s.A) $($s.B) = $($s.C)" -ForegroundColor DarkGray
}

# 2b. Freigaben fuer den Launcher zuruecknehmen (vor dem Deinstallieren: ein aktiver
#     Geraeteadministrator verhindert sonst die Deinstallation).
foreach ($g in ($entries | Where-Object { $_.Kind -eq 'grant' })) {
    if ($g.A -eq 'admin') { Invoke-Shell "dpm remove-active-admin --user 0 $($g.B)" | Out-Host }
    if ($g.A -eq 'appop') { Invoke-Shell "appops set $LauncherPkg $($g.B) default" | Out-Null }
    if ($g.A -eq 'perm') { Invoke-Shell "pm revoke $LauncherPkg $($g.B)" | Out-Null }
    Write-Host "  Freigabe entfernt: $($g.A) $($g.B)" -ForegroundColor DarkGray
}

# 3. Startbildschirm zurueck.
$oldHome = $entries | Where-Object { $_.Kind -eq 'home' } | Select-Object -First 1
if ($RemoveLauncher) {
    Invoke-Adb uninstall $LauncherPkg | Out-Host
}
if ($oldHome) {
    Invoke-Shell "cmd package set-home-activity $($oldHome.A)" | Out-Host
}

Add-State restore (Get-Date -Format 's')
Invoke-Shell 'input keyevent KEYCODE_HOME' | Out-Null
Start-Sleep -Seconds 2
Write-Host "Fertig. HOME: $(Get-HomeComponent)" -ForegroundColor Green
Write-Host "Screenshot: $(Save-Screenshot 'restore')"
