# RoadCam

Native Android-Dashcam-Prototyp mit Kotlin, Jetpack Compose und CameraX.

## Aktueller Umfang

- dunkle, moderne Oberfläche mit Kameravorschau und Aufnahme-Status
- Videoaufnahme mit 3-Minuten-Segmenten in FHD, wenn das Gerät dies unterstützt
- Clips lokal speichern, abspielen, schützen und löschen
- Schleifen-Speicherverwaltung: bis zu 10 GB für Aufnahmen, mindestens 1 GB Speicherreserve; geschützte Clips werden übersprungen
- Audio und GPS sind nicht aktiviert

Die Aufnahme wird in dieser ersten Version an die sichtbare App und deren Kamera-Lifecycle gebunden. Aufnahme bei ausgeschaltetem Display und eine dauerhafte Benachrichtigung benötigen als nächsten Schritt einen Kamera-Foreground-Service und Tests auf Zielgeräten.

## Build

Projekt in Android Studio öffnen und mit einem installierten Android SDK (API 35) synchronisieren. Voraussetzung ist ein JDK, das von der verwendeten Android-Gradle-Plugin-Version unterstützt wird. Die Mindestversion der App ist Android 10 (API 29).
