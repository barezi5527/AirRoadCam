# Notizen: Wärmeentwicklung reduzieren

Stand: nach Commit `519dbbc`. Zwei Agenten haben den Code unabhängig geprüft (Kamera/Encoder sowie UI/Hintergrund). Dies ist eine Ideensammlung – nichts davon ist umgesetzt.

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

- **Umgesetzt (noch nicht committet): Bitrate und FPS senken.** 720p 4 → 2 Mbps, 1080p 6/10 → 4/6 Mbps (`RecordQuality.kt:11-13`). Session-FPS auf 30 begrenzt, sofern die Kamera eine passende Range meldet und die Session unterstützt; bei Bind-Fehler zuerst Fallback ohne Foto, dann ohne FPS-Cap (`RecordingService.kt:709`). A/B-Messung der Wirkung steht noch aus.
- **GPS aufteilen (beschlossen):** 1-Hz-Updates nur noch bei aktivem `gpsSpeedEnabled` und nur bei Änderung der auf 1 km/h gerundeten Geschwindigkeit (`RecordingService.kt:1014`). Im Wasserzeichen-only-Modus kein 1-Hz-Listener, sondern `lastLocation` über `getLastKnownLocation()` oder einen Fix alle 1–2 Minuten aktualisieren (`RecordingService.kt:949, 997`). Wenn beides aktiv ist, nutzt das Wasserzeichen denselben 1-Hz-Fix ohne Zusatzarbeit. Grund: Der 1-Hz-State löst eine komplette Compose-Recomposition aus (`DashcamViewModel.kt:54`, `MainActivity.kt:188`), auch wenn im Hochformat gar keine Geschwindigkeit angezeigt wird.
- 480p als Sparprofil vorerst verworfen: etwas weniger Encoderarbeit, aber schlechtere Kennzeichenlesbarkeit und nur moderater Effekt, solange Bitrate/Preview/Display unverändert bleiben.
- Offen: ob 25 fps statt 30 fps nochmals messbar hitzereduziert (bei Bewegung weniger flüssig).
- Offen: ob ein 480p-Profil später doch als zusätzliche Option sinnvoll ist.
- Offen: A/B-Messung mit `getCurrentThermalStatus()`/`BatteryManager` vor und nach der Änderung.
