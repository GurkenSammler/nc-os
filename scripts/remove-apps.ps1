# Loescht alle selbst installierten Apps, die nicht in debloat\keep-apps.txt stehen.
# Vorher wird jede APK nach backups\apks\<paket>\ gesichert; restore.ps1 installiert sie
# von dort wieder (App-Daten und Logins sind dann aber weg).
#   scripts\remove-apps.ps1 -DryRun     (zeigt nur, was geloescht wuerde)
#   scripts\remove-apps.ps1
param([switch]$DryRun)

. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

$keep  = Read-List (Join-Path $ListsDir 'keep-apps.txt')
$never = Read-List (Join-Path $ListsDir 'never-touch.txt')
$userApps = Invoke-Shell 'pm list packages -3' | Where-Object { $_ -like 'package:*' } | ForEach-Object { $_.Substring(8).Trim() }
$targets = @($userApps | Where-Object { $keep -notcontains $_ -and -not (Test-NeverTouch $_ $never) } | Sort-Object)

if ($targets.Count -eq 0) { Write-Host 'Keine ueberfluessigen Apps mehr.' -ForegroundColor Green; return }

foreach ($pkg in $targets) {
    if ($DryRun) { Write-Host "  [dry] $pkg"; continue }

    $dir = Join-Path $Backups "apks\$pkg"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $paths = @(Invoke-Shell "pm path $pkg" | Where-Object { $_ -like 'package:*' } | ForEach-Object { $_.Substring(8).Trim() })
    foreach ($p in $paths) { Invoke-Adb pull $p (Join-Path $dir (Split-Path $p -Leaf)) | Out-Null }
    $saved = @(Get-ChildItem $dir -Filter *.apk)
    if ($paths.Count -eq 0 -or $saved.Count -ne $paths.Count) {
        throw "Sicherung von $pkg unvollstaendig ($($saved.Count)/$($paths.Count) APKs) - nichts geloescht."
    }

    Add-State removed $pkg $dir
    $out = (Invoke-Adb uninstall $pkg) -join ' '
    if ($out -notmatch 'Success') { throw "${pkg}: $out" }
    $mb = [math]::Round(($saved | Measure-Object Length -Sum).Sum / 1MB)
    Write-Host "  geloescht: $pkg (APK gesichert, $mb MB)" -ForegroundColor DarkGray
}
if (-not $DryRun) { Write-Host "Speicher jetzt: $((Invoke-Shell 'df -h /data' | Select-Object -Last 1))" }
