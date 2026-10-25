# Changelog

## 1.0 – 2026-10-25
- Veröffentlichungsfertig gemacht: App heißt NC-OS, eigene Standard-UI-Sounds (`sounds/default`, errechnet),
  private Sounds in `sounds/custom` ersetzen sie beim Bauen, Paketname per `ncos.applicationId` änderbar,
  optionale Release-Signatur, Lizenz und Hinweise, Quellenangabe für Open-Meteo im Status-Modul.
- Bildschirmschoner-Texte ohne Spielbegriffe.

## 0.10 – 2026-10-25
- SYS.CONFIG: 04 Network (Status, Signal, Scan, Antwortzeit), 05 Bluetooth (gekoppelte Geräte),
  06 Apps & Storage (Speicher, App-Größen). Neue Freigaben für Standort, Bluetooth und Nutzungsstatistik.

## 0.9 – 2026-10-25
- SYS.CONFIG: 02 Picture und 03 Sound über Sonys Fernsteuer-API (`enable-sony-api.ps1`), Picture Off.
- Schnellmenü: Bildmodus, Helligkeit, Picture Off.

## 0.8 – 2026-10-24
- Bildschirmschoner VIDEO LOOP mit eigenen Clips (ExoPlayer, nahtlose Schleife), `push-loops.ps1`.

## 0.7 – 2026-10-24
- Bildschirmschoner CODE RAIN, NIGHT CITY, BREACH GRID und SHUFFLE (Wechsel mit Glitch).

## 0.6 – 2026-10-24
- SYS.CONFIG mit 9 Modulen: Farbschemata, eigener Titel, Layout, HDMI umbenennen/ausblenden, Start-Ziel beim
  Einschalten, Backup Export/Import, Schnellmenü-Taste anlernen und Inhalt wählen.

## 0.5 – 2026-10-24
- Eigenes Schnellmenü `//QUICK.CTRL` statt Sonys Schnelleinstellungen (Zahnrad-Taste).

## 0.4 – 2026-10-24
- Eigene Einstellungsseite SYS.CONFIG statt der Kachel „Einstellungen“.

## 0.3 – 2026-10-24
- Design im Stil eines Cyberpunk-Spielmenüs: Farben, Schrift, abgeschrägte Rahmen, Scanlines, Glitch,
  Boot-Sequenz, UI-Sounds, WORLD CLOCK-Schoner, Wetter.

## 0.1 – 2026-10-24
- Debloat-Skripte mit `state.tsv` und `restore.ps1`, eigener Launcher als Startbildschirm, eigener Schoner.
