# Air-RoadCam

Native Android-Dashcam-App mit Kotlin, Jetpack Compose und CameraX.

## Download

Die aktuelle APK steht unter [Releases](https://github.com/barezi5527/RoadCam/releases/latest) bereit.

Voraussetzung: Android 10 (API 29) oder neuer. Zum Installieren die APK öffnen und die Installation aus unbekannten Quellen erlauben.

## Aktueller Umfang

- minimalistische Oberfläche mit Kameravorschau, Aufnahmestatus und Umschalter für dunkles/helles Design
- Hintergrundaufnahme mit Schleifen-Segmenten, Qualitätswahl, HEVC und optionalem Ton
- Fotoaufnahme jederzeit möglich, auch während laufender Aufnahme
- Clips und Fotos lokal speichern, abspielen, teilen, schützen und löschen
- Speicherverwaltung mit Aufbewahrungsdauer, Speicherlimit und Schutz vor Überschreiben
- G-Sensor-Erkennung mit optionaler Ereignisaufnahme und geschützten Notfall-Clips
- optionales GPS-Geschwindigkeits-Overlay in Vorschau und Video
- Autostart, Startschutz, Berechtigungsübersicht und G-Sensor-Test

Alle Aufnahmen bleiben lokal auf dem Gerät. Es werden keine Routen oder Standortverläufe gespeichert.

## Build

Projekt in Android Studio öffnen und mit einem installierten Android SDK (API 35/36) synchronisieren. Voraussetzung ist ein JDK, das von der verwendeten Android-Gradle-Plugin-Version unterstützt wird.

Release-Builds werden über eine lokale, nicht eingecheckte `keystore.properties` signiert. Ohne diese Datei lässt sich die App weiterhin als Debug-Build bauen.

## Lizenz

Der Quellcode ist zur Einsicht öffentlich zugänglich („source available“). Die Nutzung und Weitergabe ist nur für nichtkommerzielle Zwecke unter der [PolyForm Noncommercial License 1.0.0](LICENSE) gestattet.

Für kommerzielle Nutzung, den Einsatz des Codes als Basis eigener Produkte oder Lizenzanfragen wende dich per Issue oder E-Mail an den Repositoriums-Inhaber.
