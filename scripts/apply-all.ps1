# Wendet alles in der richtigen Reihenfolge an: Launcher installieren + als HOME setzen,
# dann jede Debloat-Liste in 5er-Batches. Bricht beim ersten Fehler ab.
#   scripts\apply-all.ps1
param([int]$Max = 5)

. "$PSScriptRoot\common.ps1"

# googletv-home zuerst: danach ist unser Launcher sicher der Startbildschirm.
$order = 'googletv-home', 'ads-tracking', 'sony-extras', 'assistant', 'chromecast', 'updates', 'sony-b2b', 'unused-apps'

& "$PSScriptRoot\install-launcher.ps1"

foreach ($cat in $order) {
    Write-Host "=== $cat" -ForegroundColor Cyan
    do {
        $left = & "$PSScriptRoot\debloat.ps1" -Category $cat -Max $Max | Select-Object -Last 1
    } until ($left -eq 0)
}

& "$PSScriptRoot\verify.ps1"
