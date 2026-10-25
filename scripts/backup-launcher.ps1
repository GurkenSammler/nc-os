# Launcher-Einstellungen sichern bzw. auf einen (anderen) TV bringen.
# Die Datei schreibt/liest der Launcher selbst: SYS.CONFIG > LAUNCHER > EXPORT / IMPORT BACKUP.
#   scripts\backup-launcher.ps1 -Pull              (TV -> backups\launcher\nc-os-backup_<datum>.json)
#   scripts\backup-launcher.ps1 -Push <datei.json> (PC -> TV, danach am TV IMPORT BACKUP waehlen)
param(
    [switch]$Pull,
    [string]$Push
)

. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

$remoteDir = "/sdcard/Android/data/$LauncherPkg/files"
$remote = "$remoteDir/nc-os-backup.json"
$localDir = Join-Path $Backups 'launcher'
New-Item -ItemType Directory -Force $localDir | Out-Null

if ($Pull) {
    $exists = (Invoke-Shell "ls $remote 2>/dev/null") -join ''
    if (-not $exists) { throw 'Kein Backup auf dem TV. Am TV: SYS.CONFIG > LAUNCHER > EXPORT BACKUP.' }
    $local = Join-Path $localDir ('nc-os-backup_{0}.json' -f (New-Stamp))
    Invoke-Adb pull $remote $local | Out-Null
    Write-Host "Gesichert: $local" -ForegroundColor Green
} elseif ($Push) {
    if (-not (Test-Path $Push)) { throw "Datei nicht gefunden: $Push" }
    Invoke-Shell "mkdir -p $remoteDir" | Out-Null
    Invoke-Adb push $Push $remote | Out-Null
    Write-Host 'Auf den TV kopiert. Am TV: SYS.CONFIG > LAUNCHER > IMPORT BACKUP (zweimal OK).' -ForegroundColor Green
} else {
    Write-Host 'Bitte -Pull oder -Push <datei.json> angeben.'
}
