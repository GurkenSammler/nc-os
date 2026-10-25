# Schaltet Sonys Fernsteuer-API (com.sony.dtv.webapi.core) wieder ein, damit der Launcher Bild- und
# Tonwerte aendern kann (SYS.CONFIG > PICTURE / SOUND), und gibt der App den Schluessel (PSK).
# Einmalig danach am TV: Einstellungen > Netzwerk & Internet > Heimnetzwerk > IP-Steuerung >
#   Authentifizierung "Pre-Shared Key", Pre-Shared Key = der ausgegebene Schluessel.
#   scripts\enable-sony-api.ps1             Paket an, Schluessel erzeugen (oder vorhandenen nehmen)
#   scripts\enable-sony-api.ps1 -Psk <key>  eigenen Schluessel setzen
#   scripts\enable-sony-api.ps1 -Test       nur pruefen, ob die API antwortet
# Nebenwirkung: DIAL ("Auf TV abspielen" aus Handy-Apps) ist damit wieder da.
# restore.ps1 laesst die IP-Steuerung an; erst der Werksreset setzt sie zurueck.
param(
    [string]$Psk,
    [switch]$Test
)

. "$PSScriptRoot\common.ps1"
if (-not (Test-Tv)) { throw 'TV antwortet nicht. Zuerst scripts\connect.ps1 <IP>' }

if (-not $Test) {
    if ((Get-Packages -Disabled) -contains $SonyApiPkg) {
        Add-State pkg $SonyApiPkg enabled sony-api
        $out = (Invoke-Shell "pm enable $SonyApiPkg") -join ' '
        Write-Host "Sony-API eingeschaltet: $out" -ForegroundColor Green
    } else {
        Write-Host 'Sony-API ist schon an.'
    }

    if (-not $Psk) {
        $Psk = if (Test-Path $SonyPskFile) { (Get-Content $SonyPskFile -TotalCount 1).Trim() }
               else { -join ((1..10) | ForEach-Object { 'abcdefghijkmnpqrstuvwxyz23456789'[(Get-Random -Maximum 32)] }) }
    }
    if ($Psk -notmatch '^[A-Za-z0-9]{4,32}$') { throw 'Schluessel: 4-32 Zeichen, nur Buchstaben und Ziffern.' }
    Set-Content -Path $SonyPskFile -Value $Psk -Encoding ascii
    Invoke-Shell "am broadcast -n $LauncherTest --es psk $Psk" | Out-Null
    Write-Host "Schluessel an den Launcher gegeben (gemerkt in $SonyPskFile)."
    Write-Host ''
    Write-Host 'Einmalig am TV: Einstellungen > Netzwerk & Internet > Heimnetzwerk > IP-Steuerung' -ForegroundColor Yellow
    Write-Host "  Authentifizierung: Pre-Shared Key    Pre-Shared Key: $Psk" -ForegroundColor Yellow
    Write-Host ''
}

# Antwortet die API? (getSupportedApiInfo braucht keinen Schluessel)
$info = $null
foreach ($i in 1..10) {
    $info = Invoke-SonyApi guide getSupportedApiInfo @(@{ services = @('system', 'video', 'audio') })
    if ($info -notlike 'FEHLER*') { break }
    Start-Sleep -Seconds 2
}
if ($info -like 'FEHLER*') { Write-Warning "API antwortet nicht: $info"; return }
$apis = ($info | ConvertFrom-Json).result[0]
foreach ($svc in $apis) {
    $names = $svc.apis | ForEach-Object { $_.name } | Sort-Object
    Write-Host ("{0,-7} {1}" -f $svc.service, ($names -join ', '))
}

# Gilt der Schluessel? Kontrast auf seinen eigenen Wert setzen (aendert nichts, braucht aber den PSK).
$pq = Invoke-SonyApi video getPictureQualitySettings @(@{ target = 'contrast' })
$contrast = (($pq | ConvertFrom-Json).result[0] | Where-Object target -eq 'contrast').currentValue
$set = Invoke-SonyApi video setPictureQualitySettings @(@{ settings = @(@{ target = 'contrast'; value = "$contrast" }) })
if ($set -match '40005') {
    Write-Warning 'TV ist im Standby (Bildschirm aus) - Test bei eingeschaltetem TV wiederholen.'
} elseif ($set -like 'FEHLER*' -or $set -match '"error"') {
    Write-Warning "Schluessel wird noch abgelehnt - am TV unter IP-Steuerung eintragen (siehe oben). $set"
} else {
    Write-Host 'Schluessel gilt - Bild und Ton lassen sich aus dem Launcher aendern.' -ForegroundColor Green
}
