# Notices on trademarks and third-party components

## Trademarks
NC-OS is a private project and is not affiliated with Sony, Google or CD PROJEKT RED.
- “Sony” and “BRAVIA” are trademarks of Sony Group Corporation.
- “Google TV” and “Android” are trademarks of Google LLC.
- “Cyberpunk 2077” is a trademark of CD PROJEKT S.A.

The names are only used to describe which device the software is meant for and which style it draws on. The
interface is inspired by game menus. All graphics, scenes and animations are drawn in code; no images from the
game are included. App logos on the home screen are supplied at runtime by the installed apps themselves.

## Included third-party components
| Component | License | Location |
|---|---|---|
| Font Rajdhani (Indian Type Foundry) | SIL Open Font License 1.1 | `launcher/app/src/main/res/font/`, license text `launcher/FONT-LICENSE-OFL.txt` |
| Gradle Wrapper | Apache License 2.0 | `launcher/gradle/wrapper/` |

## Dependencies (downloaded by Gradle at build time)
AndroidX / Jetpack Compose, Compose for TV, DataStore, Lifecycle, Media3 ExoPlayer, Kotlin – Apache License 2.0.

## Data and services
- Weather data: [Open-Meteo.com](https://open-meteo.com/), license CC BY 4.0. The attribution is shown in
  SYS.CONFIG › Status.
- Picture and sound settings use the IP control that Sony itself offers in its TVs.

## Not included (please don't publish)
- `sounds/custom/`: your own or third-party sounds that replace the default sounds. Third-party sounds
  (e.g. from games) must not be redistributed without the rights holders' permission.
- Video loops for the screensaver: they only live on the TV.
- `backups/` and `.device`: device-specific data, screenshots and the Sony key.
