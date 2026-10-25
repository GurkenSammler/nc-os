# Technik und Eigenheiten

## Aufbau
| Ordner | Inhalt |
|---|---|
| `scripts/` | PowerShell-Skripte, gemeinsame Helfer in `common.ps1` (nur ASCII, damit PowerShell 5.1 sie liest) |
| `debloat/` | Paketlisten pro Kategorie, `never-touch.txt`, `keep-apps.txt` |
| `launcher/` | Android-App (Kotlin, Jetpack Compose for TV, AGP 8.7, compileSdk 35, minSdk 28) |
| `sounds/` | `default/` (im Repo), `custom/` (privat) – siehe `sounds/README.md` |
| `backups/` | **privat**: `state.tsv`, Bestandsaufnahmen, Screenshots, APK-Sicherungen, Sony-Schlüssel |
| `docs/` | diese Notizen, Changelog, Screenshots |

### Launcher (`launcher/app/src/main/java/dev/david/braviahome/`)
- `NcApp` lädt die Einstellungen einmal pro Prozess nach `Live.settings` (DataStore, `data/SettingsStore.kt`).
  Launcher, Schnellmenü und Schoner lesen dasselbe, Änderungen gelten sofort.
- `ui/` Startbildschirm, SYS.CONFIG (`ConfigScreen`, `ConfigPanels`, `TvPanels`), Widgets, Theme (`Palettes`).
- `quick/` Schnellmenü: Bedienungshilfe-Dienst `QuickPanelService` + Overlay-Fenster mit Compose.
- `dream/` Bildschirmschoner: `DreamView` (Szene, Uhr, Scanlines, Wechsel) + eine Szene pro Stil,
  `VideoLoopScene` mit ExoPlayer auf einer `SurfaceView` darunter.
- `system/` TV-Anbindung: `TvControls` (Lautstärke, Standby, Sprünge), `SonyApi`/`SonyTv` (Bild/Ton),
  `NetworkInfo`, `BluetoothInfo`, `StorageInfo`.
- `data/BackupManager` exportiert alle Einstellungen generisch als JSON (neue Einstellungen kommen automatisch mit).
  Der Sony-Schlüssel liegt in einem eigenen Speicher und ist nicht im Backup.

### `state.tsv`
Jede Änderung am TV wird **vorher** eingetragen; `restore.ps1` dreht genau das zurück. Eine Zeile `restore`
schließt eine Runde ab.
```
pkg      <paket>  disabled|enabled  <kategorie>   (pro Paket zählt der letzte Eintrag)
setting  <ns>     <key>             <alter wert>
home     <komponente vor dem eigenen Launcher>
removed  <paket>  <ordner mit gesicherten APKs>
grant    appop|admin|perm  <name>
restore  <zeit>
```

## Freigaben (alle per adb in `install-launcher.ps1`, alle nimmt `restore.ps1` zurück)
| Freigabe | Wofür |
|---|---|
| `appops WRITE_SETTINGS` | Bildschirmschoner-Wartezeit |
| Geräteadministrator `StandbyAdmin` (nur „Bildschirm sperren“) | Standby. Blockiert das Deinstallieren, `restore.ps1 -RemoveLauncher` entfernt ihn vorher |
| Bedienungshilfe `QuickPanelService` | Zahnrad-Taste abfangen |
| `ACCESS_FINE_LOCATION` | WLAN-Name und Scan |
| `BLUETOOTH_CONNECT` | Gekoppelte Geräte |
| `appops GET_USAGE_STATS` | App-Größen |

## Test-Schalter per adb
`QuickTestReceiver` (nur mit DUMP-Berechtigung, also Shell/System):
```
adb shell am broadcast -n dev.david.braviahome/.quick.QuickTestReceiver              # Schnellmenü auf
adb shell am broadcast -n ... --ez hide true                                        # zu
adb shell am broadcast -n ... --es saver CODE_RAIN [--ei shuffle 1]                 # Schoner-Stil
adb shell am broadcast -n ... --es saver VIDEO_LOOP --es clip <datei>|ALL [--ez sound true]
adb shell am broadcast -n ... --es psk <schluessel>                                 # Sony-Schlüssel
adb shell am start -n com.android.systemui/.Somnambulator                           # Schoner jetzt
adb shell am start -n dev.david.braviahome/.HomeActivity --ei bootMs 15000          # Boot in Zeitlupe
adb shell am start -n dev.david.braviahome/.HomeActivity --ei glitchFrameMs 1500    # Glitch in Zeitlupe
adb shell am start -n dev.david.braviahome/.HomeActivity --ez openConfig true       # SYS.CONFIG
```
Logs: `logcat -s NcLoop` (Video), `logcat -s NcSony` (Sony-API).

## Was auf dem KD-50X80J gelernt wurde
- **HOME geht nach Intent-Priorität, nicht nach Einstellung.** Google TV (`launcherx`) hat Priorität 2, die
  Not-Startseite der Einrichtung (`setupwraith`) 1, eigene Apps höchstens 0. Beide müssen aus, Einrichtung zuerst –
  sonst ist der Bildschirm schwarz.
- **Shell darf keine einzelnen Komponenten abschalten** („Shell cannot change component state“), nur ganze Pakete.
- **/data hat nur 4 GB.** Unter 5 % frei schlägt jede Installation fehl; `adb shell pm trim-caches 8G` hilft.
- **Zahnrad-Taste** = Scancode `0xC3`. Sony leitet sie in `PhoneWindowManager.interceptKeyBeforeDispatching` an die
  SystemUI; Bedienungshilfe-Filter sehen sie vorher. Per `adb input keyevent` simulierte Tasten laufen **nicht**
  durch den Filter, die echte Taste lässt sich per adb nicht nachstellen.
- **SCREEN_ON kommt erst nach `onResume`.** Die Boot-Sequenz hängt deshalb an SCREEN_OFF + 900 ms Verzögerung.
- **Screenshots** von HDMI und Netflix sind leer (Kopierschutz). Videos im eigenen Schoner sind sichtbar.
- **Standby:** ADB ist erst weg und nach ca. 30 s wieder da; `input keyevent KEYCODE_WAKEUP` weckt den TV.
- **Sony-API:** `get*` brauchen keinen Schlüssel, `set*` den Header `X-Auth-PSK`. Bei ausgeschaltetem Bildschirm
  antwortet sie mit Fehler 40005. Der Schlüssel-Dialog stürzt ab, wenn man ihn über `HomeNetworkSettingsActivity`
  öffnet; direkt geht `am start -n com.sony.dtv.homenetwork/.PreSharedKeyActivity`. Der API-Server startet nur beim
  Hochfahren. Ton- und Lautsprecherwerte kommen ohne Auswahlliste, die gültigen Werte stehen in `SonyTv.kt`.
- **Blinde Tastenfolgen sind gefährlich:** ◀ ▶ ändern in SYS.CONFIG Werte, OK löst aus. Vor jedem simulierten
  Tastendruck einen Screenshot machen; `--ez openConfig true` öffnet das zuletzt offene Modul, nicht immer 01.
- **Videodecoder:** H.264/HEVC/VP9/AV1 bis 4096×2304, ein 4K60-Clip läuft ohne verlorene Bilder (ca. 20 % eines Kerns).

## Toolchain
- Android SDK unter `%LOCALAPPDATA%\Android\Sdk`. Pakete mit Semikolon im Namen lieber über
  `android.exe --sdk=... sdk install 'platforms;android-35'` installieren, `sdkmanager.bat` verschluckt das `;`.
- PowerShell 5.1: adb-Ausgaben auf stderr werden sonst zu Fehlern (`Invoke-Adb` setzt `EAP=Continue`);
  `Write-Host` erreicht kein `Out-String`, Schleifen deshalb über Rückgabewerte steuern.
