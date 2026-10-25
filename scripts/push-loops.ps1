# Eigene Video-Loops fuer den Bildschirmschoner (SYS.CONFIG > SCREENSAVER > Style VIDEO LOOP).
# Die Clips liegen im App-Ordner des Launchers und verschwinden mit ihm (restore -RemoveLauncher).
#   scripts\push-loops.ps1 <datei|ordner> [...]   kopiert .mp4/.mkv/.webm/.mov auf den TV
#   scripts\push-loops.ps1 -List                  zeigt die Clips auf dem TV
#   scripts\push-loops.ps1 -Remove <name>         loescht einen Clip
#   scripts\push-loops.ps1 -Clear                 loescht alle Clips
# Gut laufen H.264/HEVC bis 4K60. /data hat nur 4 GB: 1080p reicht meist und spart Platz.
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Path,
    [switch]$List,
    [string]$Remove,
    [switch]$Clear,
    [int]$KeepFreeMB = 600
)

. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

$remoteDir = "/sdcard/Android/data/$LauncherPkg/files/loops"
$extensions = '.mp4', '.mkv', '.webm', '.mov'

function Get-RemoteClips {
    # Ausgabe von ls -l: Rechte Links Besitzer Gruppe Groesse Datum Zeit Name
    Invoke-Shell "ls -l $remoteDir 2>/dev/null" | ForEach-Object {
        $p = "$_" -split '\s+', 8
        if ($p.Count -eq 8 -and $p[0] -like '-*') { [pscustomobject]@{ Name = $p[7]; MB = [math]::Round([int64]$p[4] / 1MB, 1); Bytes = [int64]$p[4] } }
    }
}

function Get-FreeMB {
    $line = (Invoke-Shell 'df -k /data' | Select-Object -Last 1)
    [int]([int64](("$line" -split '\s+')[3]) / 1024)
}

if ($List) {
    $clips = @(Get-RemoteClips)
    if (-not $clips) { Write-Host 'Keine Clips auf dem TV.'; return }
    $clips | Format-Table Name, MB -AutoSize
    Write-Host ("{0} Clips, {1} MB. Frei auf /data: {2} MB" -f $clips.Count, ($clips | Measure-Object MB -Sum).Sum, (Get-FreeMB))
    return
}

if ($Clear) {
    Invoke-Shell "rm -f $remoteDir/*" | Out-Null
    Write-Host 'Alle Clips geloescht.' -ForegroundColor Green
    return
}

if ($Remove) {
    if ($Remove -match '[/\\]') { throw 'Nur den Dateinamen angeben (siehe -List).' }
    Invoke-Shell "rm -f '$remoteDir/$Remove'" | Out-Null
    Write-Host "Geloescht: $Remove" -ForegroundColor Green
    return
}

if (-not $Path) { Write-Host 'Bitte Dateien/Ordner angeben, oder -List / -Remove <name> / -Clear.'; return }

$files = foreach ($p in $Path) {
    if (Test-Path $p -PathType Container) { Get-ChildItem $p -File | Where-Object { $_.Extension.ToLower() -in $extensions } }
    elseif (Test-Path $p) { Get-Item $p }
    else { throw "Nicht gefunden: $p" }
}
if (-not $files) { throw 'Keine Videos gefunden (.mp4 .mkv .webm .mov).' }

Invoke-Shell "mkdir -p $remoteDir" | Out-Null
$onTv = @{}
Get-RemoteClips | ForEach-Object { $onTv[$_.Name] = $_.Bytes }

foreach ($f in $files) {
    if ($f.Name -match "['`"\s]") { Write-Warning "Uebersprungen (Leerzeichen/Anfuehrungszeichen im Namen): $($f.Name)"; continue }
    if ($onTv[$f.Name] -eq $f.Length) { Write-Host "Schon da: $($f.Name)"; continue }
    $freeAfter = (Get-FreeMB) - [int]($f.Length / 1MB)
    if ($freeAfter -lt $KeepFreeMB) {
        Write-Warning ("Zu wenig Platz fuer {0} ({1:N0} MB): danach nur {2} MB frei, Minimum {3} MB." -f $f.Name, ($f.Length / 1MB), $freeAfter, $KeepFreeMB)
        continue
    }
    Write-Host ("Kopiere {0} ({1:N0} MB) ..." -f $f.Name, ($f.Length / 1MB))
    $out = Invoke-Adb push $f.FullName "$remoteDir/$($f.Name)"
    if ($script:AdbExit -ne 0) { throw "adb push fehlgeschlagen: $($out -join ' ')" }
}

Write-Host ("Fertig. Frei auf /data: {0} MB. Am TV: SYS.CONFIG > SCREENSAVER > Style VIDEO LOOP." -f (Get-FreeMB)) -ForegroundColor Green
