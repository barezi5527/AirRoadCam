# AirRoadCam

[![Release](https://img.shields.io/github/v/release/barezi5527/AirRoadCam?style=flat-square&label=Release&color=2ea44f)](https://github.com/barezi5527/AirRoadCam/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/barezi5527/AirRoadCam/total?style=flat-square&label=Downloads&color=0969da)](https://github.com/barezi5527/AirRoadCam/releases)
[![License](https://img.shields.io/badge/License-PolyForm%20Noncommercial%201.0.0-lightgrey?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B%20(API%2029)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)

Native Android-Dashcam-App mit Kotlin, Jetpack Compose und CameraX. Aufnahmen bleiben ausschließlich lokal auf dem Gerät.

## Download

[![Downloads gesamt](https://img.shields.io/github/downloads/barezi5527/AirRoadCam/total?style=flat-square&label=Downloads%20gesamt&color=0969da)](https://github.com/barezi5527/AirRoadCam/releases)
[![Downloads AirRoadCam 1.0](https://img.shields.io/github/downloads/barezi5527/AirRoadCam/v1.0-release/total?style=flat-square&label=AirRoadCam%201.0&color=2ea44f)](https://github.com/barezi5527/AirRoadCam/releases/tag/v1.0-release)

Die aktuelle APK steht unter [Releases](https://github.com/barezi5527/AirRoadCam/releases/latest) bereit.

**Voraussetzungen:** Android 10 (API 29) oder neuer.

**Installation:** APK herunterladen, Installation aus unbekannten Quellen erlauben und die Datei öffnen. Eine zuvor installierte Debug-Version muss wegen der abweichenden Signatur vorher deinstalliert werden.

## Funktionen

- **Aufnahme:** Hintergrundaufnahme mit Schleifen-Segmenten, Qualitätswahl, HEVC und optionalem Ton
- **Fotos:** Schnappschüsse jederzeit möglich, auch während einer laufenden Videoaufnahme
- **Wasserzeichen:** Datum, Uhrzeit und GPS-Koordinaten optional in Videos und Fotos
- **Verwaltung:** Clips und Fotos lokal speichern, abspielen, teilen, schützen und löschen
- **Speicher:** Aufbewahrungsdauer, Speicherlimit und Schutz vor Überschreiben
- **Sicherheit:** G-Sensor-Erkennung mit Ereignisaufnahme und geschützten Notfall-Clips
- **Navigation:** Optionales GPS-Geschwindigkeits-Overlay in Vorschau und Video
- **Bedienung:** Autostart, Startschutz, Berechtigungsübersicht, G-Sensor-Test sowie dunkles und helles Design
- **Ansichten:** Überarbeitetes Hoch- und Querformat inklusive Landschafts-Dashcam-Modus

Alle Aufnahmen bleiben lokal auf dem Gerät. Es werden keine Routen oder Standortverläufe gespeichert.

## Screenshots

Screenshots der App befinden sich im Ordner [`screenshots/`](screenshots/).

## Build

Projekt in Android Studio öffnen und mit einem installierten Android SDK (API 35/36) synchronisieren. Voraussetzung ist ein JDK, das von der verwendeten Android-Gradle-Plugin-Version unterstützt wird.

Release-Builds werden über eine lokale, nicht eingecheckte `keystore.properties` signiert. Ohne diese Datei lässt sich die App weiterhin als Debug-Build bauen.

## Lizenz

Der Quellcode ist zur Einsicht öffentlich zugänglich („source available“). Die Nutzung und Weitergabe ist nur für nichtkommerzielle Zwecke unter der [PolyForm Noncommercial License 1.0.0](LICENSE) gestattet.

Für kommerzielle Nutzung, den Einsatz des Codes als Basis eigener Produkte oder Lizenzanfragen wende dich per Issue oder E-Mail an den Repositoriums-Inhaber.
