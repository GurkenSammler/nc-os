# Bestandsaufnahme: liest alles vom TV und speichert es, aendert NICHTS.
#   scripts\inventory.ps1           -> backups\<datum>_inventory\
param([string]$Label = 'inventory')

. "$PSScriptRoot\common.ps1"

$dir = Join-Path $Backups ("{0}_{1}" -f (New-Stamp), $Label)
New-Item -ItemType Directory -Force $dir | Out-Null

function Save([string]$Name, [string]$Command) {
    Invoke-Shell $Command | Set-Content -Path (Join-Path $dir $Name) -Encoding UTF8
}

Save 'props.txt'             'getprop'
Save 'packages-all.txt'      'pm list packages -f -u'
Save 'packages-system.txt'   'pm list packages -s'
Save 'packages-3rdparty.txt' 'pm list packages -3'
Save 'packages-disabled.txt' 'pm list packages -d'
Save 'packages-enabled.txt'  'pm list packages -e'
Save 'home.txt'              'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
Save 'home-candidates.txt'   'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
Save 'leanback-apps.txt'     'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER'
Save 'settings-secure.txt'   'settings list secure'
Save 'settings-global.txt'   'settings list global'
Save 'settings-system.txt'   'settings list system'
Save 'tv_input.txt'          'dumpsys tv_input'
Save 'dreams.txt'            'dumpsys dreams'
Save 'meminfo.txt'           'dumpsys meminfo'
Save 'processes.txt'         'ps -A -o PID,RSS,NAME'
Save 'disk.txt'              'df -h /data /system'

$mem = Get-Content (Join-Path $dir 'meminfo.txt') | Where-Object { $_ -match '^\s*(Total RAM|Free RAM|Used RAM)' }
$all = (Get-Content (Join-Path $dir 'packages-all.txt')).Count
$sys = (Get-Content (Join-Path $dir 'packages-system.txt')).Count
$dis = (Get-Content (Join-Path $dir 'packages-disabled.txt')).Count

Write-Host "Gespeichert in $dir" -ForegroundColor Green
Write-Host "Pakete: $all gesamt, $sys System, $dis ausgeschaltet"
Write-Host "HOME:   $(Get-HomeComponent)"
$mem | ForEach-Object { Write-Host $_.Trim() }
