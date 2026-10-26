# Changelog

## 1.1 – 2026-10-25
- A long OK no longer triggers “Move” (or “Unhide”) in the options menu right away: the menu ignores OK until
  the button has been released once.
- Own APP INFO window instead of Android's info page (from the options menu and from SYS.CONFIG > 06): version,
  source, storage, last used, screen time (7 days), granted permissions, install date.
  Force stop, clear data and uninstall still go through “System Page” (only Android may do those).

## 1.0 – 2026-10-25
- Made ready for release: the app is called NC-OS, own default UI sounds (`sounds/default`, computed),
  private sounds in `sounds/custom` replace them at build time, package name changeable via `ncos.applicationId`,
  optional release signing, license and notices, attribution for Open-Meteo in the Status module.
- Screensaver texts without game terms.

## 0.10 – 2026-10-25
- SYS.CONFIG: 04 Network (status, signal, scan, latency), 05 Bluetooth (paired devices),
  06 Apps & Storage (storage, app sizes). New permissions for location, Bluetooth and usage stats.

## 0.9 – 2026-10-25
- SYS.CONFIG: 02 Picture and 03 Sound via Sony's remote control API (`enable-sony-api.ps1`), Picture Off.
- Quick menu: picture mode, brightness, Picture Off.

## 0.8 – 2026-10-24
- VIDEO LOOP screensaver with your own clips (ExoPlayer, seamless loop), `push-loops.ps1`.

## 0.7 – 2026-10-24
- Screensavers CODE RAIN, NIGHT CITY, BREACH GRID and SHUFFLE (switches with a glitch).

## 0.6 – 2026-10-24
- SYS.CONFIG with 9 modules: color schemes, custom title, layout, rename/hide HDMI, power-on target,
  backup export/import, learn the quick menu button and choose its contents.

## 0.5 – 2026-10-24
- Own quick menu `//QUICK.CTRL` instead of Sony's quick settings (gear button).

## 0.4 – 2026-10-24
- Own settings page SYS.CONFIG instead of the “Settings” tile.

## 0.3 – 2026-10-24
- Design in the style of a cyberpunk game menu: colors, font, beveled frames, scanlines, glitch,
  boot sequence, UI sounds, WORLD CLOCK screensaver, weather.

## 0.1 – 2026-10-24
- Debloat scripts with `state.tsv` and `restore.ps1`, own launcher as home screen, own screensaver.
