# Schaltet Pakete aus einer oder mehreren Listen in debloat\ aus. Immer nur -Max Stueck
# pro Aufruf, danach Gesundheitscheck + Screenshot. Nochmal aufrufen fuer den naechsten Batch.
#   scripts\debloat.ps1 -Category ads-tracking            (die naechsten 5)
#   scripts\debloat.ps1 -Category sony-extras -Max 10
#   scripts\debloat.ps1 -Category ads-tracking -DryRun    (zeigt nur, was passieren wuerde)
#
# Es wird nur "pm disable-user --user 0" benutzt: nichts wird geloescht, alles ist
# mit restore.ps1 oder einem Werksreset rueckgaengig zu machen.
param(
    [Parameter(Mandatory)][string[]]$Category,
    [int]$Max = 5,
    [switch]$DryRun
)

. "$PSScriptRoot\common.ps1"

if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

$never     = Read-List (Join-Path $ListsDir 'never-touch.txt')
$installed = @(Get-Packages)
$disabled  = @(Get-Packages -Disabled)
$homePkg   = (Get-HomeComponent).Split('/')[0]
# Google TV gewinnt per Prioritaet, auch wenn wir die Rolle haben. Ist unser Launcher
# Rolleninhaber, darf der aktuelle HOME-Gewinner deshalb ausgeschaltet werden.
$weAreHome = (Get-HomeRoleHolder) -eq $LauncherPkg -and $installed -contains $LauncherPkg

$todo = foreach ($cat in $Category) {
    foreach ($pkg in Read-List (Join-Path $ListsDir "$cat.txt")) {
        [pscustomobject]@{ Package = $pkg; Category = $cat }
    }
}

$queue = foreach ($item in $todo) {
    $entry = $item.Package
    $pkg = $entry.Split('/')[0]
    if (Test-NeverTouch $entry $never) { Write-Warning "$entry steht in never-touch.txt -> uebersprungen"; continue }
    if ($installed -notcontains $pkg)  { continue }
    if (Test-IsComponent $entry) {
        if (Test-ComponentDisabled $entry) { continue }
    } elseif ($disabled -contains $pkg) {
        continue
    }
    if ($pkg -eq $homePkg -and -not $weAreHome) {
        throw "$pkg ist gerade der Startbildschirm. Zuerst den eigenen Launcher installieren: scripts\install-launcher.ps1"
    }
    $item
}
$queue = @($queue)

# Rueckgabewert (fuer apply-all.ps1): Anzahl der noch offenen Pakete.
if ($queue.Count -eq 0) {
    Write-Host "Nichts mehr zu tun in: $($Category -join ', ')" -ForegroundColor Green
    return 0
}

$batch = @($queue | Select-Object -First $Max)
Write-Host ("{0} offen, dieser Batch: {1}" -f $queue.Count, $batch.Count)

foreach ($item in $batch) {
    if ($DryRun) { Write-Host "  [dry] $($item.Package)"; continue }
    Add-State pkg $item.Package disabled $item.Category
    $out = (Invoke-Shell "pm disable-user --user 0 $($item.Package)") -join ' '
    if ($out -match 'disabled-user') {
        Write-Host "  aus: $($item.Package)" -ForegroundColor DarkGray
    } else {
        # Nicht weitermachen: spaetere Eintraege koennen auf diesen angewiesen sein
        # (z. B. erst Recovery-HOME aus, dann Google TV).
        Add-State pkg $item.Package failed $item.Category
        throw "$($item.Package) liess sich nicht ausschalten, Batch abgebrochen: $out"
    }
}
if ($DryRun) { return $queue.Count }

Start-Sleep -Seconds 2
if (-not (Test-Tv)) { throw 'TV antwortet nach dem Batch nicht mehr! scripts\restore.ps1 ausfuehren, sobald er wieder da ist.' }
$shot = Save-Screenshot "debloat_$($Category -join '+')"
Write-Host "TV ok. HOME: $(Get-HomeComponent)"
Write-Host "Screenshot: $shot"
Write-Host ("Noch offen: {0}" -f ($queue.Count - $batch.Count))
return ($queue.Count - $batch.Count)
