# NC-OS – custom launcher + debloat for Sony BRAVIA (Google TV)

🇬🇧 English · [🇩🇪 Deutsch](README.de.md)

A home-made home screen styled like a cyberpunk game menu, its own settings for picture, sound, Wi-Fi and more,
its own quick menu, its own screensavers and a careful, **fully reversible** debloat: no root, nothing is deleted
or flashed. Packages are only disabled (`pm disable-user`), and every change is written to `backups/state.tsv`
before it happens.

Built and tested on a **Sony KD-50X80J** (platform “BRAVIA 4K VH2”, Android 12, Google TV).
Other BRAVIA models probably work similarly, but the debloat lists only apply to this device
(see [Other models](#other-models)).

> Not affiliated with Sony, Google or CD PROJEKT RED. Trademarks belong to their owners, see [NOTICE.md](NOTICE.md).
> Use at your own risk – everything can be rolled back by script or factory reset.

| Home screen | SYS.CONFIG › Picture | Quick menu (gear button) |
|---|---|---|
| ![Home screen](docs/screenshots/home.jpg) | ![Picture settings](docs/screenshots/sysconfig-picture.jpg) | ![Quick menu](docs/screenshots/quick-menu.jpg) |

## What's inside
- **Home screen** `//SYS.MENU`: app tiles (reorder and hide with a long OK), HDMI inputs, clock, weather,
  tech readout, boot sequence on power-on, UI sounds and glitch effects.
- **SYS.CONFIG** (the “Settings” tile), 14 modules:

  | Module | Contents |
  |---|---|
  | 01 Quick Control | Volume, mute, start screensaver, standby (press OK twice) |
  | 02 Picture | Picture mode, brightness, contrast, color, hue, sharpness, color temperature, light sensor, local dimming, HDR, color space, **Picture Off** (screen off, sound keeps playing) |
  | 03 Sound | Volume, mute, sound output (TV / ARC / audio system), TV position, active outputs |
  | 04 Network | Wi-Fi status, signal, band and channel, IP, router, DNS, latency test, nearby networks |
  | 05 Bluetooth | Paired devices with connection status |
  | 06 Apps & Storage | Storage used/free, size of every app, jump to app info |
  | 07 Look | 5 color schemes (RED, AMBER, ACID, VIOLET, ICE), custom title, scanlines, background, logo tint, glitch strength |
  | 08 Layout | Tile size, labels, tech readout, 12/24 h, date format |
  | 09 Inputs & Start | Rename and hide HDMI inputs, power-on target (input or app) |
  | 10 Screensaver | Style, clip, switch interval, clock overlay, timeout, preview |
  | 11 Launcher | UI sounds, boot sequence, weather location, app order, backup export/import |
  | 12 Quick Menu | Quick menu on/off, learn the button, choose contents |
  | 13 TV System | Shortcuts to the remaining Android/Sony pages (date, language, accessibility …) |
  | 14 Status | Device, Android, network, storage, uptime, disabled packages, weather |

  Controls: ◀ ▶ change values, OK selects, Back jumps from the panel to the module list.
- **Quick menu** `//QUICK.CTRL` instead of Sony's quick settings: opens with the gear button on top of the
  running app (Netflix, HDMI …). Contents: volume, mute, picture mode, brightness, Picture Off, sleep timer,
  screensaver, standby, HDMI inputs, jump to SYS.CONFIG.
- **Screensavers**: WORLD CLOCK, CODE RAIN, NIGHT CITY, BREACH GRID (all drawn in code),
  **VIDEO LOOP** (your own videos) and SHUFFLE (switches with a glitch).

## Requirements
- Windows with PowerShell 5.1 (preinstalled) or 7.
- `adb` (Android Platform Tools), e.g. `winget install Google.PlatformTools`.
- To build: JDK 17 or newer and the Android SDK (platform 35). Gradle downloads the rest itself.
  Gradle finds the SDK via `ANDROID_HOME` or `sdk.dir=<path>` in `launcher/local.properties`.
  If Gradle can't find the JDK: `org.gradle.java.home=<path>` in `%USERPROFILE%\.gradle\gradle.properties`.
- On the TV: Settings → System → About → press **Build** 7× → Developer options → **USB debugging on**.

## Quick start
```powershell
scripts\connect.ps1 <TV-IP>                # confirm "Always allow" on the TV
scripts\inventory.ps1                      # read-only, changes nothing
launcher\gradlew.bat -p launcher assembleRelease
scripts\apply-all.ps1                      # launcher as home screen + debloat in steps of 5 + check
scripts\verify.ps1                         # checks everything, also after a reboot
```
Optional afterwards:
```powershell
scripts\enable-sony-api.ps1                # make picture/sound changeable in SYS.CONFIG (see below)
scripts\push-loops.ps1 "D:\Videos\loop.mp4"  # your own video loops for the screensaver
scripts\set-weather.ps1 Berlin             # weather location (can also be set on the TV)
```
When you're done, **turn USB debugging off again**: network ADB is unencrypted.

## Scripts
| Script | Purpose |
|---|---|
| `connect.ps1` | Connect Wi-Fi ADB (also with a pairing code), remembers the address in `.device` |
| `inventory.ps1` | Inventory of all packages, settings and components, changes nothing |
| `apply-all.ps1` | Apply everything in the right order, stops at the first error |
| `debloat.ps1` | Process one list from `debloat/` (`-Category`, `-Max`, `-DryRun`) |
| `install-launcher.ps1` | Install the launcher, set it as home screen, screensaver and quick menu, grant permissions (`-NoHome`, `-NoScreensaver`, `-NoQuickPanel`) |
| `verify.ps1` | Check the current state, changes nothing |
| `restore.ps1` | Roll back everything from `state.tsv` (`-RemoveLauncher` also removes the app) |
| `remove-apps.ps1` | Delete user-installed apps that are not in `debloat/keep-apps.txt` (backs up the APKs first) |
| `hide-apps.ps1` | Hide apps in the launcher (they stay installed) |
| `enable-sony-api.ps1` | Turn on Sony's remote control API and hand the key to the launcher (`-Test`) |
| `push-loops.ps1` | Copy video loops to the TV (`-List`, `-Remove`, `-Clear`) |
| `backup-launcher.ps1` | Back up the launcher settings or move them to another TV (`-Pull`, `-Push`) |
| `set-weather.ps1` | Set the weather location (Open-Meteo, no account) |
| `make-sounds.ps1` | Regenerate the built-in default UI sounds |

`debloat/keep-apps.txt` is an example list (Apple TV, YouTube, Netflix, Prime Video, Crunchyroll) and should
be adapted to your own apps before running `remove-apps.ps1`. `remove-apps.ps1` is deliberately not part of
`apply-all.ps1`.

## Picture and sound via Sony's API
A normal app can only change Sony's picture and sound values through Sony's own remote control interface
(JSON-RPC on port 80, package `com.sony.dtv.webapi.core`).
1. `scripts\enable-sony-api.ps1` turns the package on, creates a key (in `backups/`, private)
   and hands it to the launcher. The API only starts after a TV reboot.
2. Once on the TV: Settings → Network & Internet → Home network → IP control →
   Authentication “Normal and Pre-Shared Key”, then enter the key.
3. `scripts\enable-sony-api.ps1 -Test` reports that the key is valid.

Reading works without a key, changing values needs it. Side effect: DIAL (“cast to TV” from phone apps) is
active again. Without this step, 02 Picture and 03 Sound only display the values.

## UI sounds
The build puts the sounds from `sounds/default/` into the app: original tones computed by
`scripts/make-sounds.ps1`, free to use. Any file with the same name in **`sounds/custom/`** replaces the
default sound. That folder is private and not in the repo. Details: [sounds/README.md](sounds/README.md).

## Video loops
The **VIDEO LOOP** style plays your own clips endlessly (ExoPlayer, seamless loop) with clock, scanlines and
glitch on top. Clips don't belong in the repo or the app: `scripts\push-loops.ps1 <file|folder>` copies them into
the app folder on the TV and leaves at least 600 MB free. H.264/HEVC/VP9/AV1 up to 4K60 play fine, 1080p is enough.

## Resetting
**Level 1 – undo everything, apps and accounts stay:**
```powershell
scripts\restore.ps1                   # your launcher stays installed
scripts\restore.ps1 -RemoveLauncher   # also remove the launcher
```
This re-enables the packages, restores settings and the old home screen and revokes all permissions.
Sony's IP control stays on (there is no interface to turn it off).

**Level 2 – before selling (original state):** optionally level 1 first, then on the TV
Settings → System → About → Reset → **Factory data reset**. Since nothing was deleted or flashed, everything
is original afterwards.

**Emergency:** hold the power button on the remote for about 5 s (reboot), then `scripts\connect.ps1 <IP>` and
`scripts\restore.ps1`.

## Other models
The lists in `debloat/` come from the inventory of a KD-50X80J. On other devices, run `scripts\inventory.ps1`
first, check the lists and test with `debloat.ps1 -DryRun`.
`debloat/never-touch.txt` protects packages the TV can't run without (tuner, HDMI, Wi-Fi, remote …).

## For developers
- Architecture and TV quirks: [docs/TECHNICAL.md](docs/TECHNICAL.md), versions: [docs/CHANGELOG.md](docs/CHANGELOG.md).
- Custom package name (e.g. for a second install): `ncos.applicationId=…` in `launcher/gradle.properties`.
  The scripts pick it up.
- Release signing: `launcher/keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`
  (not in the repo). Without that file the build is signed with the local debug key.

## License
Code and the default sounds: [MIT](LICENSE). Font Rajdhani: SIL OFL 1.1. Further notices and trademarks:
[NOTICE.md](NOTICE.md).
