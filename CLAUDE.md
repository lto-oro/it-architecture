# Case 5 — Event Stream Processing & CEP (FHNW Software Architecture, FS26)

PBL-Schulaufgabe von **Gruppe 4**: Lösungskonzept und vereinfachter technischer Durchstich für ein Apache-Kafka-basiertes ESP/CEP-Szenario (Erkennung verspäteter Aussendienstfahrer und Dashboard-Meldung). Dozent: Marc Schaaf.

Ziel laut Aufgabenstellung: **zeitnahe Verarbeitung** (nicht dauerhafte Speicherung) der Position-Events, zwei-stufiger CEP-Flow.

## Stack & Struktur

- **Sprache/Build:** Java + Maven, Kafka Streams `3.5.0` (siehe `Case5_KafkaApp/pom.xml`)
- **Startpunkt für eigenen Code:** [`Case5_KafkaApp/`](Case5_KafkaApp/) — alle Dependencies konfiguriert. Helper in `Utils.java` benutzen (Regex-Parsing, REST-Aufruf), nicht selbst neu schreiben.
- **Group-Prefix für eigene Topics:** `group4-…` (z.B. `group4-route-timing`). Die im Step-by-Step-Guide gezeigten `group1234-…` sind Platzhalter.
- **Externe Endpoints (nur via VPN, siehe `../group_10.conf`):**
  - Kafka Broker `192.168.111.10:9092`
  - REST + Dashboard `192.168.111.11:8080` (`/route/ID?time=…&lat=…&lon=…`, `/status/`)
  - Kouncil Topic-Browser `192.168.111.12:8080` (viewer/viewer — Settings nicht ändern)

## Arbeiten an diesem Case

- **VPN aktivieren** (`group_10.conf` mit WireGuard) — sonst hängt jede Verbindung.
- **Build/Run:** `mvn -DskipTests package` im `Case5_KafkaApp`-Verzeichnis; Run via IDE oder `mvn exec:java -Dexec.mainClass=Launcher`.
- **Topic-Inspektion:** Kouncil-UI im Browser — vor und nach Code-Änderungen einen Blick auf das `delays`-Topic werfen, um zu verifizieren, dass das Output-Format `id: 123, delay: 321` exakt stimmt (sonst ignoriert das Dashboard die Events).
- **Application-ID:** in der Streams-Config eindeutig halten (`"streams-…" + Math.random()` oder fester Name mit Group-Prefix), damit parallele Läufe sich nicht den Consumer-State teilen.

## Was vom Dozenten gefordert wird (Abgaben Phase 3)

Drei Artefakte auf Moodle (durch Moderator), pro Case zwingend:

1. **Antworten auf die Lernfragen** (Event vs. Nachricht; Event Processing vs. Enterprise Messaging; CEP vs. „normales" Event Processing — siehe `case_5.pdf` §3).
2. **Lösungskonzept** mit Begründung (Event-Modell, 2-Stage-CEP-Flow, Topic-Architektur). Vorlage-Aufbau aus dem Beispielkonzept (Temperaturmonitoring) übernehmen, auf Driver-Delay adaptieren.
3. **Lösungsumsetzung** (Kafka-Streams-Java-Code).

Format-Pflicht für Dashboard-Output auf Topic `delays`: **`id: <DeliveryID>, delay: <Sekunden>`** — falsch formatierte Events werden ignoriert. Signifikant = Verspätung > 180 s.

## Reference Docs

Bei Bedarf gezielt lesen — *nicht* alles vorab:

| Datei | Wann lesen |
|---|---|
| [`Case_5_Zusammenfassung.md`](Case_5_Zusammenfassung.md) | **Erste Anlaufstelle** — vollständige Zusammenfassung von Szenario, Lernfragen, CEP-Grundlagen, Beispielkonzept, Step-by-Step, Endpoints, Abgabe-Checkliste |
| [`case_5.pdf`](case_5.pdf) | Original-Aufgabenstellung, exakter Wortlaut der Lernfragen und PBL-Phasen |
| [`Materialien zu Umsetzung/beispiel_loesungskonzept.pdf`](Materialien%20zu%20Umsetzung/beispiel_loesungskonzept.pdf) | Vor dem Schreiben des Lösungskonzepts — gibt die geforderte Struktur (Event-Typen → CEP-Flow-Stages → Topic-Architektur) vor |
| [`Materialien zu Umsetzung/Step By Step Guide Umsetzung.pdf`](Materialien%20zu%20Umsetzung/Step%20By%20Step%20Guide%20Umsetzung.pdf) | Vor der Implementierung — Steps 1–4, REST-URL-Schema, Dashboard-Format |
| [`Case5_KafkaApp/src/main/java/Utils.java`](Case5_KafkaApp/src/main/java/Utils.java) | Wann immer GPS-Strings, REST-Calls oder Delay-Strings geparst/erzeugt werden — Helper sind hier |
| [`Case5_KafkaApp/src/main/java/Launcher.java`](Case5_KafkaApp/src/main/java/Launcher.java) | Beim Aufsetzen einer neuen Streams-App — Boilerplate (Config, Topology, Shutdown-Hook) |
| [`Startpunkte Selbststudium/`](Startpunkte%20Selbststudium/) | Beim Beantworten der Lernfragen / Schreiben des Konzepts — primäre Quellen für Event/CEP/EPA/EPN/EDA |
| [`../Modulübersicht.pdf`](../Modul%C3%BCbersicht.pdf) | Bei Fragen zu PBL-Prozess, Abgabemodus, Bewertung, Coaching |
| [`../System Overview.png`](../System%20Overview.png) | Beim Architektur-Diagramm — Container-Landscape (Kafka, Stream Generator, Kouncil) |
| [`../group_10.conf`](../group_10.conf) | VPN-Konfig (WireGuard) — VPN ist Voraussetzung für jeden Test gegen die FHNW-Infrastruktur |

## Wichtige Constraints aus den Materialien

- **Fokus laut Aufgabenstellung:** zeitnahe Verarbeitung & automatische Planungsanpassung — *nicht* persistente Speicherung. Lösung darf entsprechend schlank sein („vereinfachter technischer Durchstich").
- **Zwei Stages explizit gefordert** (Step-by-Step §3). Beide Stages dürfen technisch in derselben App laufen, das Konzept muss aber zwei EPAs zeigen.
- **`delays`-Topic wird von allen Gruppen geteilt** — das Dashboard differenziert nicht. Eigene Zwischen-Topics zwingend mit `group4-` präfixieren.
- **Microsoft Teams ist kein Kommunikationskanal zum Dozenten.** Fragen via E-Mail an `marc.schaaf@fhnw.ch` oder Coaching über Moodle.
