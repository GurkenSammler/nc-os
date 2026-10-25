# Setzt den Ort fuer das Wetter im Launcher. Sucht die Stadt bei Open-Meteo (kostenlos,
# ohne Konto) und legt "breite;laenge;NAME" als weather.txt in den App-Ordner auf dem TV.
#   scripts\set-weather.ps1 Leipzig
#   scripts\set-weather.ps1 -Remove        (Wetter wieder aus)
param([string]$City, [switch]$Remove)

. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }
$file = "/sdcard/Android/data/$LauncherPkg/files/weather.txt"

if ($Remove) {
    Invoke-Shell "rm -f $file" | Out-Null
    Write-Host 'Wetter entfernt.' -ForegroundColor Green
    return
}
if (-not $City) { throw 'Stadt angeben, z. B.: scripts\set-weather.ps1 Leipzig' }

$q = [uri]::EscapeDataString($City)
$res = Invoke-RestMethod "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=de&format=json"
if (-not $res.results) { throw "Stadt '$City' nicht gefunden." }
$hit = $res.results[0]
$inv = [Globalization.CultureInfo]::InvariantCulture
$line = '{0};{1};{2}' -f $hit.latitude.ToString($inv), $hit.longitude.ToString($inv), $hit.name.ToUpper()

Invoke-Shell "mkdir -p /sdcard/Android/data/$LauncherPkg/files" | Out-Null
Invoke-Shell "echo '$line' > $file" | Out-Null
Write-Host ("Wetter-Ort gesetzt: {0}, {1} ({2})" -f $hit.name, $hit.country, $line) -ForegroundColor Green
Write-Host 'Der Launcher holt das Wetter beim naechsten Aktualisieren (spaetestens nach 10 Sekunden).'
