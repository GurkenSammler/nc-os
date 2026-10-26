# UI sounds

At build time Gradle assembles the app's sounds (task `prepareSounds` in `launcher/app/build.gradle.kts`):

1. First, all files from **`default/`**: original tones computed by `scripts/make-sounds.ps1`, free to use.
2. Then every file with the same name in **`custom/`** replaces the default sound. That folder is private and
   listed in `.gitignore`. Put your own sounds here, including ones you're not allowed to redistribute.

| File | When |
|---|---|
| `sfx_hover.wav` | Focus moves (tiles, menu entries) |
| `sfx_move.wav` | ◀ ▶ changes a value, a tile is moved |
| `sfx_tab.wav` | Expand/collapse hidden apps |
| `sfx_press.wav` | OK on a toggle |
| `sfx_enter.wav` | Opening something (module, input, shortcut) |
| `sfx_menu_open.wav` / `sfx_menu_close.wav` | Options (long OK), dialogs, menu open / close |
| `sfx_open.wav` | Launching an app |
| `sfx_boot.wav` | Boot sequence on power-on |

Format: WAV (PCM). The name must match exactly; files missing from `custom/` fall back to the default.
After swapping, rebuild and run `scripts\install-launcher.ps1 -NoHome -NoScreensaver`.
Volume and on/off are set on the TV under SYS.CONFIG › Launcher.
