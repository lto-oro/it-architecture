# Case 5 — Event Stream Processing & Complex Event Processing

> Software Architecture, FHNW, FS26 — Dozent: Marc Schaaf
> Quelle: `case_5.pdf`, `Materialien zu Umsetzung/`, `Startpunkte Selbststudium/`, `../Modulübersicht.pdf`

Diese Datei ist die **vollständige Arbeitsgrundlage für Gruppe 4**: Sie fasst Szenario, Lernfragen, theoretische Grundlagen (Event/CEP), Beispiel-Lösungskonzept, Step-by-Step-Umsetzungsleitfaden, Systemumgebung und Abgabe-Checkliste zusammen.

---

## 1 Szenario (aus `case_5.pdf`, §1)

Im Anschluss an einen vorherigen Problemfall (Enterprise Messaging) ist auf den mobilen Endgeräten der Aussendienstmitarbeitenden bereits eine Java-Applikation für die Auftragsdisposition ausgerollt. Diese soll nun erweitert werden, damit die Abarbeitung der Aufträge **live nachverfolgbar** wird und Planungen **zeitnah dynamisch angepasst** werden können.

**Live zu erfassende Datenpunkte (durch die Aussendienst-App):**

1. Ankunftszeit beim Kunden
2. Zeitpunkt der ersten Aufwandseinschätzung + geschätzter Aufwand
3. Unerwartete Verzögerungen durch Komplikationen inkl. geschätzter Dauer
4. Abschluss des Auftrags (Abfahrt beim Kunden)

**Konkretes Szenario** (mit Disposition besprochen): Aus der initialen Aufwandseinschätzung ableiten, ob der aktuelle Auftrag in der geplanten Zeit abgeschlossen werden kann. Bei nennenswerter Verzögerung prüfen, ob ein Folgetermin betroffen wäre — wenn ja, **Kunde informieren**, bei grösseren Verschiebungen **Disposition informieren**.

**Rahmenbedingungen (aus `case_5.pdf`, §2):**

- Geplante Einsätze (Startzeit, geplante Dauer, …) sind über einen **REST-Service** abrufbar.
- Endgeräte haben Zugriff auf das Unternehmensnetz via VPN.
- **Apache Kafka** ist als ESP-System gesetzt; ein Kafka-Server wird durch die IT bereitgestellt.
- Die Übertragung der Daten von den Endgeräten ins Backend ist frei wählbar.

**Fokus der Lösung (aus `case_5.pdf`, §4):** *Nicht* dauerhafte Speicherung, sondern **zeitnahe Verarbeitung der Daten zur automatischen Anpassung der Planung**. Das Lösungskonzept ist als **vereinfachter technischer Durchstich** umzusetzen, der die zentralen Funktionen demonstriert.

**Zentrales Lernziel:** Grundlegendes Verständnis von Event (Stream) Processing und Complex Event Processing inkl. Einsatz in einfachen Szenarien.

---

## 2 Lernfragen (vom Dozenten gefordert + Selbststudium)

Aus `case_5.pdf`, §3 — diese sind **zwingend zu beantworten** (Abgabe Phase 3):

1. **Was ist ein Event und wie unterscheidet es sich von einer Nachricht** (im Sinne von Enterprise Messaging)?
2. **Was unterscheidet Event Processing von „klassischem" Enterprise Messaging?**
3. **Was unterscheidet Complex Event Processing von „normalem" Event Processing?**

Ergänzend sollen eigene Lernfragen aus Phase 1 aufgenommen werden (z.B. „Wie funktioniert Kafka Streams?", „Was ist ein Event Processing Agent / Network?", „Welche Rolle spielt ein Zeitfenster?").

### 2.1 Antwort-Bausteine aus den Selbststudium-Quellen

Quellen: `Startpunkte Selbststudium/Bruns-Dunkel2015_Chapter_ComplexEventProcessingImÜberbl.pdf`, `…SprachkonzepteZurEreignisverar.pdf`, `2015_Article_.pdf`.

**Event (Ereignis):** Alles, was passiert oder von dem erwartet wird, dass es passiert — typischerweise die Zustandsänderung eines realen oder virtuellen Objekts. Ein Event trägt:

- **Metadaten** (domänenunabhängig): eindeutige Event-ID, Zeitstempel, Quelle, Ereignistyp.
- **Ereigniskontext / Payload**: die fachlichen Nutzdaten.

**Event Stream:** linear geordnete, kontinuierliche Sequenz von Events.

**Event Processing vs. Enterprise Messaging:**

- Messaging adressiert *zuverlässige Zustellung von Nachrichten* zwischen bekannten Empfängern (oft Punkt-zu-Punkt oder Pub/Sub mit fachlichem Inhalt). Verarbeitung passiert pro Nachricht.
- Event Processing fokussiert auf den **kontinuierlichen Strom** von Ereignissen und deren **Korrelation in Echtzeit**. Sender/Empfänger sind weiter entkoppelt; Events werden nicht primär konsumiert, sondern **analysiert und ggf. abgeleitet weiterpubliziert**.

**Complex Event Processing (CEP) — Luckham:**

- CEP analysiert massive Ereignisströme dynamisch und in Echtzeit.
- Drückt **kausale, temporale, räumliche und andere Beziehungen** zwischen Events aus.
- Sucht **Ereignismuster (Event Patterns)** im Strom — boolesche Kombinationen, Sequenzen, Zeitfenster.
- **Abstraktion:** Aus erkannten Mustern werden **höherwertige Events** erzeugt (z.B. aus mehreren Messwerten ein „RegionalHighTemperatureWarning"). Mehrstufig.
- Beschreibung der Verarbeitung **deklarativ in Regeln** (Event Processing Languages / EPL).

**Event Processing Agent (EPA):** Softwaremodul, das Muster in einem Ereignisstrom erkennt und komplexe Events erzeugt. Besteht aus Ereignismodell, Ereignisregeln und einer Event Processing Engine (mit In-Memory-Verarbeitung über Zeit-/Längenfenstern).

**Event Processing Network (EPN):** Mehrere EPAs, verbunden über **Ereigniskanäle** — die Ausgabe eines EPA ist die Eingabe des nächsten. Komplexe Verarbeitung wird in **kleinen Stufen** umgesetzt.

**Event-Driven Architecture (EDA):** Drei Schichten — *Ereignisquellen → Ereignisverarbeitung (CEP/EPN) → Ereignisbehandlung* (Service-Aufrufe, Dashboard-Updates, Nachrichten, Eskalation). Rückkopplung möglich (Behandlung erzeugt neue Events).

---

## 3 Beispiel-Lösungskonzept (aus `Materialien zu Umsetzung/beispiel_loesungskonzept.pdf`)

Der Dozent gibt explizit ein Vorgehen in **drei Schritten** vor — dieses Vorgehen ist auf unseren Case zu **adaptieren** (Driver-Delay statt Temperatur):

### 3.1 Schritt 1 — Event-Typen definieren

Beispiel Temperaturmonitoring:

| Event-Typ | Bedeutung |
|---|---|
| `TemperatureMeasurement` | Einzelner Messwert eines Sensors |
| `AverageTemperature` | Mittlere Temperatur eines Sensors über 30 Minuten |
| `HighTemperatureWarning` | Überhöhte Temperatur eines Sensors (über sensorspezifischem Grenzwert) |
| `RegionalHighTemperatureWarning` | Überhöhte Temperatur in einer Region |

### 3.2 Schritt 2 — CEP-Flow in Stages definieren

Beispiel Temperaturmonitoring, drei Stages:

- **Stage 1:** `TemperatureMeasurement`-Events eines Sensors über 30 Minuten zu Mittelwert aggregieren → `AverageTemperature`.
- **Stage 2:** Wenn `AverageTemperature > sensorspezifischer Grenzwert` (aus externer Quelle / DB) → `HighTemperatureWarning`.
- **Stage 3:** `HighTemperatureWarning`-Events nach Region clustern; > 3 Warnings in einer Region → `RegionalHighTemperatureWarning`.

### 3.3 Schritt 3 — Architektur (Topics, Anwendungen)

Beispiel-Architektur Temperaturmonitoring:

```
Topic: Temperature Measurements
   ↓
CEP Engine (Kafka Streams, Stage 1+2) — liest Sensor-Threshold aus DB
   ↓
Queue: High Temperature Warnings
   ↓
CEP Engine (Kafka Streams, Stage 3)
   ↓
Topic: Regional High Temperature Warnings
```

**Erwartete Artefakte für unseren Case (analog):**

1. Definition der Event-Typen für das Driver-Delay-Szenario.
2. CEP-Flow in **2 Stages** (vom Dozenten so vorgegeben — siehe §4.2).
3. Architektur-Diagramm mit Topics + Apps.

---

## 4 Step-by-Step-Implementierungsguide (aus `Materialien zu Umsetzung/Step By Step Guide Umsetzung.pdf`)

Der Dozent vereinfacht das Szenario für die Implementierung: **Wir tracken nur, ob Fahrer auf dem Weg zum Kunden verspätet sind** — keine Auftragsdaten.

### 4.1 Vorgaben

**Verfügbare Daten:**

- Häufige GPS-Reports der Fahrer auf dem Weg zum Job
- Geplante Ankunftszeiten und Routen

**Ziel:**

- Verspätung erkennen
- Entscheiden, ob die Verspätung signifikant genug ist, um den Kunden zu informieren
- Dashboard für den Kundenservice mit verspäteten Fahrern (einfaches Dashboard ist bereitgestellt)

**Sample Code:** vorkonfiguriertes Maven-Projekt unter [`Case5_KafkaApp/`](Case5_KafkaApp/) — alle Dependencies sind drin. **Empfehlung:** dort starten.

**Hinweise des Dozenten:**

- `Utils.java` enthält Helper für Event-Parsing und REST-Aufrufe — verwenden, statt selbst zu schreiben.
- Beide Stages dürfen vereinfachend in **derselben Anwendung** implementiert werden (das verbindende Topic kann dann entfallen) — wir setzen aber gemäss Vorgabe zwei Stages um.

### 4.2 Step 1 — Processing-Flow definieren

> **Annahme des Dozenten:** Eine Verspätung gilt als signifikant, wenn sie **grösser als 3 Minuten** ist. In diesem Fall soll der Zustand auf einem Dashboard gemeldet werden.
>
> **Vorgabe:** Verarbeitung explizit in **2 Stages** umsetzen.

**Erwartete Lieferobjekte aus Step 1:**

- Definition des Processing-Flows (Diagramm)
- Definition des Event-Modells
- Definition der benötigten Topics

**Empfehlung für Gruppe 4 (Vorschlag, im Lösungskonzept zu begründen):**

| Event-Typ | Bedeutung | Topic |
|---|---|---|
| `DriverPosition` | GPS-Update eines Fahrers (`id, time, lat, lon`) | `driver-position` (vom Stream-Generator gespeist) |
| `RouteTiming` | Berechnete Verspätung in Sekunden zu einer Position | `group4-route-timing` |
| `SignificantDelay` | Verspätung > 3 min — für Dashboard | `delays` (festes Format `id: X, delay: Y`) |

- **Stage 1 (EPA 1):** liest `driver-position` → ruft REST-Service `/route/ID` auf → publiziert `RouteTiming` auf `group4-route-timing`.
- **Stage 2 (EPA 2):** liest `group4-route-timing` → filtert `delay > 180 Sekunden` → publiziert `SignificantDelay` auf `delays` (Format genau `id: 123, delay: 321`).

### 4.3 Step 2 — Position-Stream lesen + Verspätung abfragen

Java-Programm mit Kafka Streams. Schritte:

1. Verbindung zum Kafka-Broker: `192.168.111.10:9092`.
2. Topic `driver-position` subscriben (Stream der GPS-Updates).
3. Pro Event den REST-Service abfragen, um die Verspätung zu bekommen.
4. Neues Event auf `groupXXXX-route-timing` publizieren — **für uns: `group4-route-timing`** (Topic-Präfix mit Gruppenname).

### 4.4 Step 3 — Signifikante Verspätung erkennen

Zweite Kafka-Streams-App. Schritte:

1. Verbindung zum Kafka-Broker.
2. Topic `group4-route-timing` subscriben.
3. Verspätung > 3 min prüfen.
4. Konsolen-Ausgabe für jeden Treffer.

### 4.5 Step 4 — Dashboard / Kunden informieren

- Dashboard: <http://192.168.111.11:8080/status/> — zeigt Verspätungen aus dem Kafka-Topic **`delays`** als einfache Tabelle. Nur korrekt formatierte Events werden angezeigt.
- **Format zwingend:** `id: 123, delay: 321` (ID = DeliveryID, delay = Sekunden).
- Das Dashboard unterscheidet die Gruppen **nicht**; alle publizieren ins gleiche `delays`-Topic.
- **Tatsächliche Aufgabe:** Code aus Step 2/3 anpassen, sodass das `SignificantDelay`-Event im richtigen Format auf `delays` publiziert wird.

---

## 5 Datenformate, Endpoints, Utilities

Quellen: Step-by-Step-Guide, [`Materialien zu Umsetzung/Utils.java`](Materialien%20zu%20Umsetzung/Utils.java) und [`Case5_KafkaApp/src/main/java/Utils.java`](Case5_KafkaApp/src/main/java/Utils.java) (identisch).

### 5.1 Position-Update (Input)

- **Topic:** `driver-position`
- **Format:** `id: 6, time: 2024-04-01T12:01:21.123647949+02:00, lat: 47.35203, lon: 7.905917`
- **Regex aus `Utils.java`:** `^id: ([0-9]+), time: (.+?), lat: ([0-9.]+?), lon: ([0-9.]+?)$`
- **Helper:** `Utils.extractCoordinates(String) → GpsPos { time, lat, lon }`

### 5.2 REST-Service (Verspätungsberechnung)

- **URL-Schema:** `http://192.168.111.11:8080/route/ID?time=TIME&lat=LAT&lon=LON`
- **TIME muss URL-encoded sein** (`URLEncoder.encode(s, StandardCharsets.UTF_8)`), Format wie im Position-Event.
- **Beispiel:** `http://192.168.111.11:8080/route/10?time=2023-10-17T16%3A00%3A56.558554461%2B02%3A00&lat=47.34876&lon=7.908264`
- **Antwort:** Sekunden Verspätung (Integer, kann negativ sein, falls schneller als geplant).
- **Helper:** `Utils.requestDelay(String key, GpsPos pos) → int`.

### 5.3 Delay-Event (Output)

- **Topic:** `delays` (gemeinsam, vom Dashboard konsumiert)
- **Format:** `id: 123, delay: 321`
- **Regex aus `Utils.java`:** `^id: ([0-9]+), delay: ([0-9-]+?)$`
- **Helper:** `Utils.extractDelay(String) → Integer`

### 5.4 Zwischen-Topic (frei benennbar)

- **Topic:** `group4-route-timing` (Präfix mit Gruppenname zwingend, damit es zu keinen Kollisionen kommt).

---

## 6 Systemumgebung & Zugang (System Overview, Modulübersicht, group_10.conf)

### 6.1 VPN

- **Konfiguration:** [`../group_10.conf`](../group_10.conf) (WireGuard).
- **Endpoint:** `vpn.fhnw.i.schaaf.es:51820`
- **Zugewiesene Adresse:** `192.168.111.139/24`
- **Erreichbar:** `192.168.111.0/24`
- **Wichtig:** *Alle* Kafka-/REST-/Dashboard-Endpoints sind **ausschliesslich über VPN** erreichbar.

### 6.2 Container / Services für Case 5

| Host | Komponente | Zweck |
|---|---|---|
| `192.168.111.10:9092` | **Kafka Broker** | Event-Backbone |
| `192.168.111.11:8080` | **Stream Generator** | Erzeugt Driver-Position-Events; serviert REST `/route/...` und Dashboard `/status/` |
| `192.168.111.12:8080` | **Kouncil Web UI** | Topic-Browser; Login `viewer` / `viewer` (Settings *nicht* ändern, Instanz ist geteilt) |

### 6.3 Gruppen-Identifikation

- **Gruppe:** **Gruppe 4** (siehe `group_10.conf`).
- **Konsequenz:** Eigene Topics werden mit `group4-` präfixiert (z.B. `group4-route-timing`). Beispiel im Step-by-Step-Guide nutzt `group1234-` — das war ein Platzhalter.

---

## 7 Sample-Code-Struktur (`Case5_KafkaApp/`)

Maven-Projekt — siehe `pom.xml`:

- Kafka Streams `3.5.0`
- SLF4J / reload4j

Quellen unter [`Case5_KafkaApp/src/main/java/`](Case5_KafkaApp/src/main/java/):

- [`Launcher.java`](Case5_KafkaApp/src/main/java/Launcher.java) — Boilerplate für Kafka Streams: Config, `StreamsBuilder`, Subscribe auf `driver-position`, Hookup von `MyProcessor` (Foreach) und `MyMapper` (KeyValue-Map), Publish auf `someOtherTopic`, Shutdown-Hook.
- [`MyProcessor.java`](Case5_KafkaApp/src/main/java/MyProcessor.java) — `ForeachAction<String,String>`: gibt jedes Event auf der Konsole aus.
- [`MyMapper.java`](Case5_KafkaApp/src/main/java/MyMapper.java) — `KeyValueMapper<String,String,KeyValue<String,String>>`: erzeugt aus jedem Event ein neues Event.
- [`Utils.java`](Case5_KafkaApp/src/main/java/Utils.java) — Helper (siehe §5).
- `src/main/resources/log4j.properties` — Logging-Konfiguration.

**Kafka-Streams-Setup-Eckpunkte (aus `Launcher.java`):**

```java
props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-position" + Math.random()); // unique
props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.111.10:9092");
props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class);
```

**Build & Run:**

```bash
cd Case5_KafkaApp
mvn -DskipTests package
mvn exec:java -Dexec.mainClass=Launcher   # oder via IDE
```

(VPN muss aktiv sein, sonst hängt die Connection.)

> ⚠️ Die `Utils.java` auf der obersten Ebene des Materialordners hat `package kafka_streams;` — die Variante im `Case5_KafkaApp/`-Default-Package nicht. Bei eigener Implementation auf konsistente Packages achten.

---

## 8 PBL-Prozess & Abgaben (`../Modulübersicht.pdf`, `case_5.pdf` §5)

Wir folgen dem **Design-oriented Problem-Based Learning** mit 4 Phasen:

### Phase 1 — Problemanalyse (Gruppe)

1. Problemfall lesen.
2. Moderator/Schriftführer für Case 5 festlegen.
3. Lernfragen für das Selbststudium definieren (≈3–5).
4. **Abgabe:** Protokoll inkl. Lernfragen (Vorlage auf Moodle).

### Phase 2 — Selbststudium (einzeln)

- Recherche basierend auf Lernfragen + bereitgestellten Quellen ([`Startpunkte Selbststudium/`](Startpunkte%20Selbststudium/)) + weiteren vertrauenswürdigen Quellen.
- Resultate im **persönlichen Learning Report** in eigenen Worten + mit Quellen dokumentieren.

### Phase 3 — Lösung & Umsetzung (Gruppe)

1. Selbststudien-Resultate zusammentragen, einheitliches Verständnis schaffen, Lernfragen beantworten.
2. Lösungsstrategie definieren.
3. Coaching-Termin (Moodle) für zentrale Konzepte und Vorgehen.
4. Lösung entwerfen → umsetzen → dokumentieren.
5. **Abgaben (Moodle, durch Moderator):**
   - **Antworten auf die Lernfragen**
   - **Lösungskonzept** mit nachvollziehbarer Begründung (Event-Typen, CEP-Flow, Architektur)
   - **Lösungsumsetzung** (Java-Code)

### Phase 4 — Reflexion (einzeln)

- Bewertung des PBL-Prozesses auf Moodle.
- Reflexion + persönlicher Lernbericht ergänzen.

### Modulebene (zusätzlich)

- Pro **zugewiesenem Case** zusätzlich: **Videopräsentation** (15–25 min, Gruppe) + **individuelle Lösungsverteidigung** (mündlich, Einzelnote).
- Coaching-Buchung über Moodle, Fragen via E-Mail an `marc.schaaf@fhnw.ch` (Microsoft Teams wird **nicht** beantwortet).

---

## 9 Konkrete Definition-of-Done für Case 5 (Gruppe 4)

- [ ] **Phase-1-Protokoll** mit Lernfragen abgegeben (Moderator/Schriftführer benannt).
- [ ] **Antworten auf die drei Pflicht-Lernfragen** (Event vs. Nachricht, Event Processing vs. Messaging, CEP vs. EP) + eigene Lernfragen, mit Quellenangaben.
- [ ] **Lösungskonzept**: Event-Modell, 2-Stage-CEP-Flow, Topic-Architektur, Begründung — orientiert am Beispielkonzept aus §3, adaptiert auf Driver-Delay aus §4.
- [ ] **Lösungsumsetzung** (Java/Kafka Streams):
  - [ ] Stage 1 liest `driver-position`, ruft REST `…/route/ID` (URL-encoded `time`) auf, publiziert `RouteTiming` auf `group4-route-timing`.
  - [ ] Stage 2 liest `group4-route-timing`, filtert `delay > 180 s`, publiziert ins `delays`-Topic im Format **`id: 123, delay: 321`**.
  - [ ] Manuell verifiziert: Dashboard <http://192.168.111.11:8080/status/> zeigt unsere Verspätungen, Topics in Kouncil sichtbar.
- [ ] **Persönliche Reflexion + Lernbericht** in Phase 4.
- [ ] (Sofern Case 5 zugewiesen:) **Videopräsentation** + **Lösungsverteidigung**.

---

## 10 Quellen-Index

| Datei | Inhalt |
|---|---|
| [`case_5.pdf`](case_5.pdf) | Aufgabenstellung, Lernfragen, PBL-Prozess |
| [`Materialien zu Umsetzung/beispiel_loesungskonzept.pdf`](Materialien%20zu%20Umsetzung/beispiel_loesungskonzept.pdf) | Vorlage Temperaturmonitoring (Konzept-Aufbau) |
| [`Materialien zu Umsetzung/Step By Step Guide Umsetzung.pdf`](Materialien%20zu%20Umsetzung/Step%20By%20Step%20Guide%20Umsetzung.pdf) | Implementierungs-Anleitung Driver-Delay |
| [`Materialien zu Umsetzung/Utils.java`](Materialien%20zu%20Umsetzung/Utils.java) | Helper-Klasse (Standalone-Variante mit `package kafka_streams`) |
| [`Case5_KafkaApp/`](Case5_KafkaApp/) | Maven-Projekt-Skelett (empfohlener Startpunkt) |
| [`Startpunkte Selbststudium/Bruns-Dunkel2015_Chapter_ComplexEventProcessingImÜberbl.pdf`](Startpunkte%20Selbststudium/Bruns-Dunkel2015_Chapter_ComplexEventProcessingIm%C3%9Cberbl.pdf) | CEP-Grundlagen (Event, EPA, EPN, EDA) |
| [`Startpunkte Selbststudium/Bruns-Dunkel2015_Chapter_SprachkonzepteZurEreignisverar.pdf`](Startpunkte%20Selbststudium/Bruns-Dunkel2015_Chapter_SprachkonzepteZurEreignisverar.pdf) | Sprachkonzepte / EPLs / Esper |
| [`Startpunkte Selbststudium/2015_Article_.pdf`](Startpunkte%20Selbststudium/2015_Article_.pdf) | Schaaf/Wilke 2015 — Ereignisverarbeitung in Smart Cities |
| [`../Modulübersicht.pdf`](../Modul%C3%BCbersicht.pdf) | Modul- und PBL-Konzept, Bewertung, Abgabemodi |
| [`../System Overview.png`](../System%20Overview.png) | Container-Landscape (Kafka, Stream Generator, Kouncil etc.) |
| [`../group_10.conf`](../group_10.conf) | WireGuard-VPN-Konfig für Gruppe 4 |
