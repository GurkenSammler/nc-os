# Prueft den aktuellen Zustand, aendert NICHTS.
#   scripts\verify.ps1
. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

$ok = $true
function Check([bool]$Pass, [string]$Text) {
    if ($Pass) { Write-Host "  OK    $Text" -ForegroundColor Green }
    else       { Write-Host "  FEHLT $Text" -ForegroundColor Red; $script:ok = $false }
}

$homeNow = Get-HomeComponent
Check ($homeNow -like "$LauncherPkg/*") "Startbildschirm ist unser Launcher ($homeNow)"

$version = (Invoke-Shell "dumpsys package $LauncherPkg" | Where-Object { $_ -match 'versionName=' } | Select-Object -First 1)
Check ([bool]$version) "Launcher installiert $($version.Trim())"

$dream = (Invoke-Shell 'settings get secure screensaver_components') -join ''
Check ($dream -eq $LauncherDream) "Bildschirmschoner ist unserer ($dream)"

# Hat Sony etwas wieder eingeschaltet?
$disabledNow = @(Get-Packages -Disabled)
# Pro Paket zaehlt der letzte Eintrag: "enabled" (z. B. Sony-API) heisst absichtlich wieder an.
$expected = @(Get-EpochEntries | Where-Object { $_.Kind -eq 'pkg' } | Group-Object A |
    Where-Object { $_.Group[-1].B -eq 'disabled' } | ForEach-Object { $_.Name })
$back = @($expected | Where-Object {
    if (Test-IsComponent $_) { -not (Test-ComponentDisabled $_) } else { $disabledNow -notcontains $_ }
})
Check ($back.Count -eq 0) ("{0} Pakete ausgeschaltet, davon wieder an: {1}" -f $expected.Count, ($(if ($back) { $back -join ', ' } else { 'keins' })))

$mem = Invoke-Shell 'dumpsys meminfo' | Where-Object { $_ -match '^\s*(Total RAM|Free RAM|Used RAM)' }
$mem | ForEach-Object { Write-Host "  $($_.Trim())" }

Write-Host "  Screenshot: $(Save-Screenshot 'verify')"
if ($ok) { Write-Host 'Alles in Ordnung.' -ForegroundColor Green } else { exit 1 }
