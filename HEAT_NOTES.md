# Notizen: Wärmeentwicklung reduzieren

Stand: nach Commit `87deb38` plus aktuellen, noch nicht committeten Wärmemaßnahmen. Zwei Agenten haben den Code unabhängig geprüft (Kamera/Encoder sowie UI/Hintergrund).

## Ziel

Daueraufnahme über 75 Minuten ohne starke Erwärmung. Die Aufnahmequalität (Kennzeichenlesbarkeit) muss brauchbar bleiben.

## Messplan vor der Umsetzung

- `PowerManager.getCurrentThermalStatus()` (API 29+) und optional `getThermalHeadroom()` (API 30+) im Service loggen.
- `BatteryManager.EXTRA_TEMPERATURE` und `dumpsys thermalservice` als second opinion.
- Modi vergleichen: Bereitschaft, Daueraufnahme 720p/1080p, Event-Standby, GPS/Wasserzeichen an/aus, Overlap an/aus, Display an/aus.
- Pro Messreihe nur eine Änderung, um A/B-Effekte zu sehen.

## Bereits umgesetzt (`519dbbc`)

- Overlay auf eigenem `HandlerThread`.
- Overlay-Texte nur einmal pro Sekunde berechnet/gemessen.
- `ImageCapture` auf 1920×1080 begrenzt.
- `PreviewView` auf `ImplementationMode.PERFORMANCE`.

## Ideen – offen, nicht umgesetzt

### Sofort, geringes Risiko

- `setSpeed()` emittiert bei jedem 1-Hz-GPS-Fix einen neuen State und löst damit eine vollständige Recomposition aus (`RecordingService.kt:1014`): nur bei geänderter, gerundeter Geschwindigkeit emittieren.
- Datei-`stat()` in der Komposition: `file.length()`/`lastModified()` bei jedem Recompose (`MainActivity.kt:671`, `MainActivity.kt:1876`); Metadaten einmal beim Scan berechnen.
- `pruneClips()` scannt doppelt (Prune plus `refreshClips`, `RecordingService.kt:1144`); Scans auf Executor verlagern, `usableSpace` nicht pro Kandidat erneut abfragen.
- SharedPreferences nur bei echter Änderung schreiben; Schutz-Doppelwrites zusammenfassen.

### Mittel

- G-Sensor mit `SENSOR_DELAY_GAME` (~50 Hz) auf eigenen Handler verlagern und im Event-Standby eine langsamere Rate verwenden (`RecordingService.kt:787`).
- GPS-Intervall/`minDistance` erhöhen (nur für den Wasserzeichen-Modus; die Live-Geschwindigkeit bleibt bewusst bei 1 Hz).
- Overlap-Remux verursacht CPU-/I/O-Spitzen pro Segmentgrenze; bewusst als Option belassen (Default ist bereits aus).
- `AutoFitText`-Messung memoisieren.

### Größer, mit Funktionskompromiss

- `ImageCapture` im Leerlauf nicht binden.
- Kamera bei echtem Idle ganz freigeben (Startlatenz versus Bereitschaftswärme).
- Session-FPS auf 25/30 begrenzen.
- Bitrate pro Qualitätsprofil senken.
- Kürzeren Display-Default anbieten.

## Entscheidungen und offene Punkte

- **Umgesetzt (`39dc01b`): Bitrate und FPS senken.** 720p 4 → 2 Mbps, 1080p 6/10 → 4/6 Mbps. Session-FPS auf 30 begrenzt, sofern die Kamera die Range meldet; Fallback ohne Foto, dann ohne Cap.
- **Umgesetzt (aktuell): Thermie-Regler.** Alle 15 s `getCurrentThermalStatus()`; zwei aufeinanderfolgende Messungen nötig (Hysterese). Stufe 1: Bitrate ×0,75, Overlap aus, Fotos 1280×720. Stufe 2: 720p, 25 fps, 2 Mbps, Overlay/Preview aus. Hinweis erscheint als Pill im Hoch- und Querformat. Änderungen an der Kamera-Session werden während laufender Aufnahme bis zum nächsten Bind verzögert.
- **Umgesetzt (aktuell): GPS-Split.** 1 Hz nur bei aktivem Geschwindigkeits-Overlay; im Wasserzeichen-only-Modus ein Fix alle 3 min plus `getLastKnownLocation()` als Startwert. Die Uhrzeit im Wasserzeichen bleibt sekundengenau.
- **Umgesetzt (aktuell): „Vorschau aus“** als Option im Aufnahmemodus; `Preview`-UseCase wird dann nicht gebunden.
- 480p als Sparprofil vorerst verworfen: etwas weniger Encoderarbeit, aber schlechtere Kennzeichenlesbarkeit und nur moderater Effekt.
- Bewusst nicht umgesetzt: `ImageCapture` vollständig lazy zu binden. Ein Rebind während der Aufnahme würde die Videoaufnahme unterbrechen; stattdessen wird die Fotoauflösung bei Wärme reduziert.
- Offen: A/B-Messung mit `getCurrentThermalStatus()`/`BatteryManager` und Dauerausfall-Test über 75 min.
