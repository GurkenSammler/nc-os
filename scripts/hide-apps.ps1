# Blendet Apps im eigenen Launcher aus (sie bleiben installiert und ueber die Kachel
# "Ausgeblendet" erreichbar). Ohne Angabe: alles ausser debloat\keep-apps.txt.
#   scripts\hide-apps.ps1
#   scripts\hide-apps.ps1 com.android.vending com.sony.dtv.timers
param([string[]]$Packages)

. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

if (-not $Packages) {
    $keep = Read-List (Join-Path $ListsDir 'keep-apps.txt')
    $Packages = @(Invoke-Shell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER' |
        Where-Object { $_ -match '/' } | ForEach-Object { $_.Trim().Split('/')[0] } | Select-Object -Unique |
        Where-Object { $keep -notcontains $_ -and $_ -ne $LauncherPkg })
}
if (-not $Packages) { Write-Host 'Nichts auszublenden.'; return }

# Der Launcher liest diese Datei beim naechsten Oeffnen ein und loescht sie dann.
$dir = "/sdcard/Android/data/$LauncherPkg/files"
Invoke-Shell "mkdir -p $dir" | Out-Null
Invoke-Shell ("printf '%s\n' {0} > $dir/hide.txt" -f ($Packages -join ' ')) | Out-Null
Invoke-Shell "am force-stop $LauncherPkg" | Out-Null
Invoke-Shell 'input keyevent KEYCODE_HOME' | Out-Null
Start-Sleep -Seconds 3

$left = (Invoke-Shell "ls $dir/hide.txt 2>/dev/null") -join ''
if ($left) { Write-Warning 'Launcher hat die Liste (noch) nicht eingelesen.' }
else { Write-Host "Ausgeblendet: $($Packages -join ', ')" -ForegroundColor Green }
Write-Host "Screenshot: $(Save-Screenshot 'hidden')"
