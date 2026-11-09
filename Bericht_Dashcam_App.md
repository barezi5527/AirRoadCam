# Bericht: Einfache Dashcam-App für Android

## Kurzantwort

Ja. Eine einfache Android-Dashcam-App mit den üblichen Grundfunktionen lässt sich umsetzen. Für den ersten Stand empfiehlt sich eine native App in **Kotlin** mit Androids **CameraX**-Bibliothek. Sie kann Videos in kurzen Abschnitten aufnehmen, ältere normale Aufnahmen bei vollem Speicher überschreiben und wichtige Clips vor dem Löschen schützen.

## Vorgeschlagener Funktionsumfang

### Grundversion (MVP)

- **Live-Kamerabild** mit gut sichtbarer Aufnahme- und Speicheranzeige.
- **Aufnahme starten und stoppen** über eine einfache Oberfläche.
- **Segmentierte Aufnahme**, beispielsweise in Clips von 1, 3 oder 5 Minuten. So bleiben Dateien handhabbar und ein einzelner Fehler betrifft nicht die gesamte Fahrt.
- **Schleifenaufnahme**: Bei knappem Speicherplatz werden die ältesten, nicht geschützten Clips entfernt.
- **Clip schützen**: Eine Schaltfläche markiert die aktuelle Aufnahme, sodass sie bei der Speicherbereinigung erhalten bleibt.
- **Speicher- und Kamerastatus**: Anzeige von freiem Speicher, Aufnahmezeit und relevanten Fehlern.
- **Aufnahme bei ausgeschaltetem Display** über einen Android-Foreground-Service mit dauerhafter Benachrichtigung, sofern das Gerät und die Android-Version dies zulassen.
- **Lokale Speicherung** der Videos auf dem Gerät; kein Konto und kein Cloud-Upload erforderlich.

### Sinnvolle optionale Funktionen

- Datum und Uhrzeit als Einblendung im Video.
- GPS-Koordinaten oder Geschwindigkeit als optionale Einblendung beziehungsweise Metadaten.
- Audioaufnahme mit abschaltbarer Mikrofonberechtigung.
- Automatisches Sperren eines Clips bei Erschütterung, sofern das Smartphone einen geeigneten Bewegungssensor bereitstellt.
- Auswahl von Auflösung und Audio sowie eine Liste zum Abspielen, Schützen und Löschen gespeicherter Clips.

## Technischer Ansatz

- **Sprache und Plattform:** Kotlin, Android Studio, native Android-App.
- **Kamera:** CameraX für Vorschau und Videoaufnahme; für gerätespezifische Sonderfälle kann Camera2 nötig werden.
- **Aufnahme im Hintergrund:** Foreground-Service mit sichtbarer Benachrichtigung. Moderne Android-Versionen schränken den Start und die Verwendung von Kamera und Mikrofon im Hintergrund ein; die App muss die passenden Service-Typen und Berechtigungen verwenden.
- **Dateien:** Speicherung über die Android-Medienschnittstellen (MediaStore) oder einen vom Nutzer gewählten Ordner. Dadurch wird die App an aktuelle Android-Speicherregeln angepasst.
- **Schleifenlogik:** Clips mit Status „geschützt“ bleiben erhalten. Beim Erreichen eines konfigurierten Speicherlimits werden die ältesten ungeschützten Clips gelöscht.
- **Berechtigungen:** Kamera zwingend; Mikrofon und Standort nur, wenn die jeweiligen Funktionen aktiviert sind.

## Wichtige Einschränkungen

- Dashcam-Aufnahmen beanspruchen viel Speicher und können Smartphone sowie Akku stark belasten. Dauerhafte Aufnahme bei hoher Auflösung kann außerdem zu Erwärmung und Leistungsbegrenzung führen.
- Verhalten, verfügbare Auflösungen und Hintergrundbetrieb unterscheiden sich je nach Hersteller und Gerät. Eine zuverlässige App muss deshalb auf den vorgesehenen Smartphones getestet werden.
- Bei manchen Geräten kann die Kamera nicht gleichzeitig von einer anderen App verwendet werden. Telefonate, Energiesparmodi oder Systemprozesse können die Aufnahme unterbrechen.
- Für die Nutzung im Auto sind eine sichere Halterung, freie Sicht und eine Bedienung ohne Ablenkung wichtig. Die App sollte im Fahrbetrieb keine komplexen Eingaben verlangen.

## Datenschutz und Nutzung

Video-, Audio- und Standortdaten können sensible Informationen enthalten. Audio und GPS sollten daher standardmäßig optional sein; Aufnahmen sollten lokal bleiben und einfach gelöscht werden können. Bei Nutzung in Deutschland sind insbesondere Persönlichkeitsrechte und Datenschutz zu berücksichtigen: Eine anlasslose, dauerhafte Aufzeichnung des öffentlichen Verkehrs kann rechtlich problematisch sein. Die App kann technisch eine kurze, überschreibende Schleifenaufnahme unterstützen, ersetzt aber keine rechtliche Prüfung des konkreten Einsatzes.

## Abnahmekriterien für die Grundversion

1. Die App zeigt eine Kameravorschau und kann eine Aufnahme zuverlässig starten und beenden.
2. Aufnahmen werden in konfigurierbare, einzeln abspielbare Clips geteilt.
3. Geschützte Clips werden bei der Speicherbereinigung nicht gelöscht.
4. Die App zeigt verständliche Hinweise bei fehlender Berechtigung, vollem Speicher oder nicht verfügbarer Kamera.
5. Der Nutzer kann Clips ansehen und löschen sowie die Aufnahme jederzeit sichtbar stoppen.
6. Die Grundfunktionen sind auf den vereinbarten Android-Versionen und Zielgeräten getestet.

## Fazit

Eine praktikable erste Version ist machbar. Der sinnvollste Start ist eine schlanke App mit Vorschau, segmentierter Schleifenaufnahme, Clip-Schutz, lokaler Speicherung und klarer Statusanzeige. Für die konkrete Umsetzung wären anschließend die gewünschte Android-Mindestversion, Zielgeräte, bevorzugte Videoauflösung und die Entscheidung zu GPS sowie Audio festzulegen.
