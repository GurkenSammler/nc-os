# UI-Sounds

Beim Bauen stellt Gradle die Sounds der App zusammen (Task `prepareSounds` in `launcher/app/build.gradle.kts`):

1. Zuerst alle Dateien aus **`default/`**: eigene Töne, errechnet von `scripts/make-sounds.ps1`, frei verwendbar.
2. Danach ersetzt jede gleichnamige Datei aus **`custom/`** den Standard-Sound. Dieser Ordner ist privat und
   steht in `.gitignore`. Hier können eigene Sounds liegen, auch solche, die man nicht weitergeben darf.

| Datei | Wann |
|---|---|
| `sfx_hover.wav` | Fokus wandert (Kacheln, Menüeinträge) |
| `sfx_move.wav` | ◀ ▶ ändert einen Wert, Kachel wird verschoben |
| `sfx_tab.wav` | Ausgeblendete Apps auf-/zuklappen |
| `sfx_press.wav` | OK auf einen Schalter |
| `sfx_enter.wav` | Etwas öffnen (Modul, Eingang, Sprung) |
| `sfx_menu_open.wav` / `sfx_menu_close.wav` | Optionen (langes OK), Dialoge, Menü auf / zu |
| `sfx_open.wav` | App starten |
| `sfx_boot.wav` | Boot-Sequenz beim Einschalten |

Format: WAV (PCM). Der Name muss genau stimmen, fehlende Dateien in `custom/` fallen auf den Standard zurück.
Nach dem Austausch neu bauen und `scripts\install-launcher.ps1 -NoHome -NoScreensaver` ausführen.
Lautstärke und An/Aus stellt man am TV unter SYS.CONFIG › Launcher ein.
