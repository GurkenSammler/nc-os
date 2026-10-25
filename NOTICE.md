# Hinweise zu Marken und fremden Bestandteilen

## Marken
NC-OS ist ein privates Projekt und steht in keiner Verbindung zu Sony, Google oder CD PROJEKT RED.
- „Sony“ und „BRAVIA“ sind Marken der Sony Group Corporation.
- „Google TV“ und „Android“ sind Marken von Google LLC.
- „Cyberpunk 2077“ ist eine Marke von CD PROJEKT S.A.

Die Namen werden nur genannt, um zu beschreiben, für welches Gerät die Software gedacht ist und welchen Stil
sie aufgreift. Die Oberfläche ist von Spielmenüs inspiriert. Alle Grafiken, Szenen und Animationen sind im
Code gezeichnet, es sind keine Bilder aus dem Spiel enthalten. App-Logos auf dem Startbildschirm liefern die
installierten Apps selbst zur Laufzeit.

## Enthaltene fremde Bestandteile
| Bestandteil | Lizenz | Ort |
|---|---|---|
| Schrift Rajdhani (Indian Type Foundry) | SIL Open Font License 1.1 | `launcher/app/src/main/res/font/`, Lizenztext `launcher/FONT-LICENSE-OFL.txt` |
| Gradle Wrapper | Apache License 2.0 | `launcher/gradle/wrapper/` |

## Abhängigkeiten (lädt Gradle beim Bauen)
AndroidX / Jetpack Compose, Compose for TV, DataStore, Lifecycle, Media3 ExoPlayer, Kotlin – Apache License 2.0.

## Daten und Dienste
- Wetterdaten: [Open-Meteo.com](https://open-meteo.com/), Lizenz CC BY 4.0. Die Quellenangabe steht in
  SYS.CONFIG › Status.
- Bild- und Toneinstellungen nutzen die IP-Steuerung, die Sony in den Fernsehern selbst anbietet.

## Nicht enthalten (bitte nicht veröffentlichen)
- `sounds/custom/`: eigene oder fremde Sounds, die die Standard-Sounds ersetzen. Fremde Sounds (z. B. aus
  Spielen) dürfen ohne Erlaubnis der Rechteinhaber nicht weitergegeben werden.
- Video-Loops für den Schoner: liegen nur auf dem TV.
- `backups/` und `.device`: gerätespezifische Daten, Screenshots und der Sony-Schlüssel.
