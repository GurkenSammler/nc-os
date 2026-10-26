# Technical notes and quirks

## Layout
| Folder | Contents |
|---|---|
| `scripts/` | PowerShell scripts, shared helpers in `common.ps1` (ASCII only, so PowerShell 5.1 can read it) |
| `debloat/` | Package lists per category, `never-touch.txt`, `keep-apps.txt` |
| `launcher/` | Android app (Kotlin, Jetpack Compose for TV, AGP 8.7, compileSdk 35, minSdk 28) |
| `sounds/` | `default/` (in the repo), `custom/` (private) – see `sounds/README.md` |
| `backups/` | **private**: `state.tsv`, inventories, screenshots, APK backups, Sony key |
| `docs/` | these notes, changelog, screenshots |

### Launcher (`launcher/app/src/main/java/dev/david/braviahome/`)
- `NcApp` loads the settings once per process into `Live.settings` (DataStore, `data/SettingsStore.kt`).
  Launcher, quick menu and screensaver read the same data, changes apply immediately.
- `ui/` home screen, SYS.CONFIG (`ConfigScreen`, `ConfigPanels`, `TvPanels`), widgets, theme (`Palettes`).
- `quick/` quick menu: accessibility service `QuickPanelService` + overlay window with Compose.
- `dream/` screensaver: `DreamView` (scene, clock, scanlines, transitions) + one scene per style,
  `VideoLoopScene` with ExoPlayer on a `SurfaceView` underneath.
- `system/` TV integration: `TvControls` (volume, standby, shortcuts), `SonyApi`/`SonyTv` (picture/sound),
  `NetworkInfo`, `BluetoothInfo`, `StorageInfo`, `AppDetails` (APP INFO window).
- `data/BackupManager` exports all settings generically as JSON (new settings are included automatically).
  The Sony key lives in a separate store and is not part of the backup.

### `state.tsv`
Every change on the TV is written down **before** it happens; `restore.ps1` rolls back exactly that. A `restore`
line closes a round.
```
pkg      <package>  disabled|enabled  <category>   (per package, the last entry counts)
setting  <ns>       <key>             <old value>
home     <component before the custom launcher>
removed  <package>  <folder with backed-up APKs>
grant    appop|admin|perm  <name>
restore  <time>
```

## Permissions (all granted via adb in `install-launcher.ps1`, all revoked by `restore.ps1`)
| Permission | Used for |
|---|---|
| `appops WRITE_SETTINGS` | Screensaver timeout |
| Device admin `StandbyAdmin` (only “lock screen”) | Standby. Blocks uninstalling; `restore.ps1 -RemoveLauncher` removes it first |
| Accessibility service `QuickPanelService` | Intercepting the gear button |
| `ACCESS_FINE_LOCATION` | Wi-Fi name and scan |
| `BLUETOOTH_CONNECT` | Paired devices |
| `appops GET_USAGE_STATS` | App sizes, “last used” and screen time in the APP INFO window |

## Test switches via adb
`QuickTestReceiver` (only with the DUMP permission, i.e. shell/system):
```
adb shell am broadcast -n dev.david.braviahome/.quick.QuickTestReceiver              # quick menu open
adb shell am broadcast -n ... --ez hide true                                        # close
adb shell am broadcast -n ... --es saver CODE_RAIN [--ei shuffle 1]                 # screensaver style
adb shell am broadcast -n ... --es saver VIDEO_LOOP --es clip <file>|ALL [--ez sound true]
adb shell am broadcast -n ... --es psk <key>                                        # Sony key
adb shell am start -n com.android.systemui/.Somnambulator                           # screensaver now
adb shell am start -n dev.david.braviahome/.HomeActivity --ei bootMs 15000          # boot in slow motion
adb shell am start -n dev.david.braviahome/.HomeActivity --ei glitchFrameMs 1500    # glitch in slow motion
adb shell am start -n dev.david.braviahome/.HomeActivity --ez openConfig true       # SYS.CONFIG
```
Logs: `logcat -s NcLoop` (video), `logcat -s NcSony` (Sony API).

## Lessons learned on the KD-50X80J
- **HOME follows intent priority, not the setting.** Google TV (`launcherx`) has priority 2, the setup's
  fallback home (`setupwraith`) 1, your own apps at most 0. Both must be disabled, setup first –
  otherwise the screen goes black.
- **Shell can't disable single components** (“Shell cannot change component state”), only whole packages.
- **/data has only 4 GB.** Below 5 % free, every install fails; `adb shell pm trim-caches 8G` helps.
- **Gear button** = scancode `0xC3`. Sony routes it to SystemUI in `PhoneWindowManager.interceptKeyBeforeDispatching`;
  accessibility key filters see it first. Keys simulated with `adb input keyevent` do **not** pass through the
  filter, so the real button can't be reproduced via adb.
- **SCREEN_ON only arrives after `onResume`.** That's why the boot sequence hangs off SCREEN_OFF + a 900 ms delay.
- **Screenshots** of HDMI and Netflix are blank (copy protection). Videos in the custom screensaver are visible.
- **Standby:** ADB drops and comes back after about 30 s; `input keyevent KEYCODE_WAKEUP` wakes the TV.
- **Sony API:** `get*` calls need no key, `set*` calls need the `X-Auth-PSK` header. With the screen off it
  answers with error 40005. The key dialog crashes when opened via `HomeNetworkSettingsActivity`; it works
  directly with `am start -n com.sony.dtv.homenetwork/.PreSharedKeyActivity`. The API server only starts at boot.
  Sound and speaker values come without a list of options; the valid values are in `SonyTv.kt`.
- **Blind key sequences are dangerous:** ◀ ▶ change values in SYS.CONFIG, OK triggers actions. Take a screenshot
  before every simulated key press; `--ez openConfig true` opens the last open module, not always 01.
- **Video decoder:** H.264/HEVC/VP9/AV1 up to 4096×2304; a 4K60 clip plays without dropped frames (about 20 % of one core).

## Toolchain
- Android SDK under `%LOCALAPPDATA%\Android\Sdk`. Install packages with a semicolon in their name via
  `android.exe --sdk=... sdk install 'platforms;android-35'`; `sdkmanager.bat` swallows the `;`.
- PowerShell 5.1: adb output on stderr otherwise turns into errors (`Invoke-Adb` sets `EAP=Continue`);
  `Write-Host` doesn't reach `Out-String`, so loops are driven by return values.
