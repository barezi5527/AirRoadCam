# TODO – RoadCam

Sinnvolle nächste Schritte, nach Nutzen/Aufwand sortiert:

- [x] **1. Hintergrundaufnahme (Foreground Service)** – aktuell läuft die Aufnahme nur im Vordergrund; für eine Dashcam der wichtigste Praxispunkt.
- [x] **2. Aufbewahrung einstellbar** – Tage (statt fix 5) und Speicherlimit (statt fix 10 GB) in den Aufnahmemodus.
- [x] **3. Segment-Überlappung (1–2 s)** – verhindert Lücken beim 3-Minuten-Wechsel.
- [x] **4. HEVC/H.265** – 30–50 % kleinere Dateien (CameraX-Upgrade oder MediaCodec).
- [x] **5. Audio optional** – Schalter, standardmäßig aus.
- [x] **6. Aufräumen/Testbarkeit** – Texte in `strings.xml`, Logik aus `MainActivity` in ein ViewModel, Unit-Tests für `pruneClips`/`deleteClip`.
- [x] **7. Kleinigkeiten** – Bildschirm anlassen, Hochformat sperren, Front-Preview spiegeln, Aufnahmeordner öffnen/teilen.
- [x] **8. Foto-Snapshot** – Standbild-Button neben der Aufnahme, ohne laufende Aufnahme zu unterbrechen; Fotos in Liste, Schutz, Teilen und Speicherbereinigung integriert.

**Empfehlung:** Mit 1 (Hintergrundaufnahme) anfangen, dann 2 und 3.
