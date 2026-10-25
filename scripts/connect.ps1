# Verbindet den PC per WLAN-ADB mit dem TV und merkt sich die Adresse in .device
#   scripts\connect.ps1 192.168.178.42                     (klassisch, Port 5555)
#   scripts\connect.ps1 192.168.178.42:41234               (Port aus "Kabelloses Debugging")
#   scripts\connect.ps1 192.168.178.42:41234 -PairPort 37000 -Code 123456   (einmal koppeln)
param(
    [Parameter(Mandatory)][string]$Ip,
    [int]$PairPort,
    [string]$Code
)

. "$PSScriptRoot\common.ps1"

$target = if ($Ip -match ':') { $Ip } else { "${Ip}:5555" }
if ($PairPort) {
    if (-not $Code) { throw 'Zum Koppeln auch -Code <6-stelliger Code vom TV> angeben.' }
    $hostOnly = $target.Split(':')[0]
    & $Adb pair "${hostOnly}:$PairPort" $Code | Out-Host
}
& $Adb connect $target | Out-Host

$prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
$state = (& $Adb -s $target get-state 2>&1 | ForEach-Object { "$_" }) -join ' '
$ErrorActionPreference = $prev

if ($state -match 'unauthorized') {
    Write-Host 'Am TV erscheint eine Abfrage: "Immer von diesem Computer zulassen" ankreuzen und OK. Dann dieses Skript nochmal starten.' -ForegroundColor Yellow
    exit 1
}
if ($state -notmatch '^device') {
    Write-Host "TV nicht erreichbar ($state). Ist USB-Debugging an und die IP richtig?" -ForegroundColor Red
    exit 1
}

Set-Content -Path $DeviceFile -Value $target -Encoding ASCII
$model   = (Invoke-Shell 'getprop ro.product.model') -join ''
$android = (Invoke-Shell 'getprop ro.build.version.release') -join ''
$build   = (Invoke-Shell 'getprop ro.build.id') -join ''
Write-Host "Verbunden: $model, Android $android, Build $build ($target)" -ForegroundColor Green
