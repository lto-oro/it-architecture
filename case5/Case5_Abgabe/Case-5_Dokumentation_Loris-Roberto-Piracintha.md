# Case 5 — Event Stream Processing & Complex Event Processing

**Gruppe 4** — Loris Trifoglio, Roberto Panizza, Piracintha
FHNW Software Architecture, FS26 — Dozent: Marc Schaaf

---

# Lernfragen und Antworten

## Definierte Lernfragen

Auf Basis der Problemfallbeschreibung und der bereitgestellten Quellen wurden folgende Lernfragen für das Selbststudium definiert. Die ersten drei sind vom Dozenten in `case_5.pdf` (§3) zwingend gefordert; die letzten beiden haben wir in Phase 1 zusätzlich aufgenommen, weil sie für unser Lösungskonzept zentral sind.

1. Was ist ein Event, und wie unterscheidet es sich von einer Nachricht (im Sinne von Enterprise Messaging)?
2. Was unterscheidet Event Processing von „klassischem" Enterprise Messaging?
3. Was unterscheidet Complex Event Processing (CEP) von „normalem" Event Processing?
4. Was ist ein Event Processing Agent (EPA), und wie spielen mehrere EPAs in einem Event Processing Network (EPN) zusammen?
5. Welche Rolle spielen Zeitfenster sowie kausale und temporale Beziehungen in CEP-Regeln?

## Antworten auf die Lernfragen

**Lernfrage 1: Event vs. Nachricht**
Ein Event ist die Repräsentation von etwas, das passiert ist oder dessen Eintreten erwartet wird — typischerweise die **Zustandsänderung eines realen oder virtuellen Objekts**. Es trägt zwei Teile: domänenunabhängige **Metadaten** (Event-ID, Zeitstempel, Quelle, Typ) und einen fachlichen **Payload** (Ereigniskontext). Eine Nachricht im Enterprise-Messaging-Sinn adressiert primär die **zuverlässige Zustellung von Inhalten** an einen oder mehrere bekannte Empfänger; sie wird einmalig konsumiert und ist damit. Ein Event hingegen ist Teil eines **kontinuierlichen Stroms** und kann von beliebig vielen Konsumenten gleichzeitig analysiert werden, ohne dass der Produzent sie kennt. Kurz: Nachricht = „dieser Inhalt soll bei dir ankommen", Event = „etwas ist passiert, alle Interessierten dürfen reagieren".

**Lernfrage 2: Event Processing vs. Enterprise Messaging**
Enterprise Messaging fokussiert auf die **Vermittlung einzelner Nachrichten** zwischen entkoppelten Systemen — Punkt-zu-Punkt oder Pub/Sub, mit Transaktionssicherheit, Reihenfolgegarantie und durable Queues (Case 3/4). Verarbeitung passiert pro Nachricht, oft synchron auf Empfängerseite. Event Processing fokussiert auf den **kontinuierlichen Strom** und seine **Korrelation in Echtzeit**: Events werden nicht primär konsumiert, sondern **analysiert, gefiltert, aggregiert und ggf. zu neuen, abgeleiteten Events weiterpubliziert**. Sender und Empfänger sind noch stärker entkoppelt — der Produzent weiss nicht, welche Auswertungen sein Strom auslöst. In unserem Case: der Stream Generator publiziert nur GPS-Updates, ohne irgendeine Vorstellung von „Verspätung" oder „Dashboard".

**Lernfrage 3: CEP vs. „normales" Event Processing**
„Normales" Event Processing arbeitet meist auf Einzel-Events oder einfachen 1:1-Transformationen. **Complex Event Processing** (CEP, geprägt von David Luckham) geht darüber hinaus: es analysiert **massive Ereignisströme dynamisch in Echtzeit** und drückt **kausale, temporale, räumliche und semantische Beziehungen** zwischen Events aus. Die zentralen Bausteine sind **Event Patterns** (boolesche Kombinationen, Sequenzen, Zeitfenster) und das Konzept der **Abstraktion**: aus mehreren niederwertigen Events wird mehrstufig ein höherwertiges, fachlich aussagekräftigeres Event abgeleitet. Beschrieben wird diese Verarbeitung **deklarativ** in Event Processing Languages (EPL). In unserem Case ist das genau der Schritt von vielen GPS-Punkten → einer berechneten Verspätung → einer fachlich relevanten „Significant-Delay"-Meldung.

**Lernfrage 4: EPA und EPN**
Ein **Event Processing Agent** (EPA) ist ein Softwaremodul, das einen oder mehrere Eingangsströme konsumiert, in einem definierten Ereignismodell und Regelwerk Muster erkennt und daraus neue Events erzeugt. Intern besteht er aus Ereignismodell, Regeln und einer Engine mit In-Memory-Verarbeitung über Zeit- oder Längenfenstern. Mehrere EPAs werden über **Ereigniskanäle** (in Kafka: Topics) zu einem **Event Processing Network** (EPN) verschaltet — die Ausgabe eines EPA ist die Eingabe des nächsten. Damit lässt sich komplexe Verarbeitung in **kleinen, gut testbaren Stages** zerlegen, statt eine monolithische Regel-Engine zu bauen. Genau dieser Ansatz ist im Case explizit gefordert (zwei Stages) und im Lösungskonzept (§ Lösungskonzept) abgebildet.

**Lernfrage 5: Zeitfenster und temporale Beziehungen**
Ein roher Eventstrom enthält keine fachliche Aussage — diese entsteht erst durch **Bezug auf Zeit**. CEP-Regeln nutzen daher Zeitfenster (Tumbling, Sliding, Session) und temporale Operatoren („A vor B innerhalb von 5 min"), um Korrelationen über mehrere Events auszudrücken. Ohne Zeitfenster wäre weder „Mittelwert über 30 min" noch „signifikante Verspätung" formulierbar. In unserem Case ist das Zeitfenster implizit: Stage 2 bewertet jedes RouteTiming-Event sofort gegen den festen Schwellwert von 180 s. Eine erweiterte Lösung könnte hier sliding-window-Aggregate ergänzen (z.B. „nur warnen, wenn 3 aufeinanderfolgende Updates eine Verspätung > 180 s zeigen"), um Ausreisser zu glätten. Für den geforderten technischen Durchstich genügt jedoch die einfache Filter-Logik.

---

# Lösungskonzept

Die Aufgabenstellung verlangt einen **vereinfachten technischen Durchstich**: nicht persistente Speicherung, sondern **zeitnahe Verarbeitung** der Position-Events zur automatischen Anpassung der Planung. Wir folgen dabei dem vom Dozenten vorgegebenen dreistufigen Vorgehen (Event-Typen → CEP-Flow → Architektur, siehe `beispiel_loesungskonzept.pdf`) und adaptieren das Beispiel „Temperaturmonitoring" auf unser Driver-Delay-Szenario. Das Konzept wird als **Event Processing Network mit zwei Stages** umgesetzt, wie im Step-by-Step-Guide explizit gefordert.

## Schritt 1 — Event-Typen

Aus dem Szenario lassen sich genau drei relevante Event-Typen ableiten — einer pro Verarbeitungsstufe. Damit ist jeder Typ eindeutig einer Stage als In- oder Output zugeordnet und das EPN bleibt überschaubar.

| Event-Typ | Bedeutung | Felder | Erzeuger |
|---|---|---|---|
| `DriverPosition` | Rohes GPS-Update eines Fahrers auf dem Weg zum Kunden. | `id, time, lat, lon` | Stream Generator (extern) |
| `RouteTiming` | Berechnete Abweichung zur geplanten Ankunftszeit für eine konkrete Position (Sekunden, kann negativ sein). | `id, delay` | Stage 1 (EPA 1) |
| `SignificantDelay` | Verspätung > 180 s — fachlich relevant für Dashboard und Kunde. | `id, delay` | Stage 2 (EPA 2) |

Die Schwelle „signifikant > 180 s" ist als Konstante (`SIGNIFICANT_DELAY_SECONDS`) im Code hinterlegt und entspricht der Vorgabe des Dozenten („grösser als 3 min").

## Schritt 2 — CEP-Flow in zwei Stages

Stufe 1 ist eine **Anreicherungsstage** (Position → Verspätung), Stufe 2 ist eine **Filterstage** (alle Verspätungen → nur signifikante). Diese Trennung folgt direkt dem EPN-Prinzip: kleine, zusammensteckbare EPAs statt einer monolithischen Regel.

**Stage 1 — EPA 1: `DriverPosition` → `RouteTiming`**

- Input: Topic `driver-position`.
- Pro Event: GPS-String mit `Utils.extractCoordinates(...)` parsen, REST-Service `http://192.168.111.11:8080/route/{id}?time=…&lat=…&lon=…` aufrufen (Helper `Utils.requestDelay(...)`).
- Output: `id: <key>, delay: <sekunden>` auf Topic `group4-route-timing`.
- Fehlertoleranz: kann der GPS-String nicht geparst werden, wird das Event verworfen (`null` → `filter`), nicht propagiert.

**Stage 2 — EPA 2: `RouteTiming` → `SignificantDelay`**

- Input: Topic `group4-route-timing` (eigenständiger Konsument, **nicht** ein In-Memory-Strom aus Stage 1).
- Regel: `delay > 180 s`.
- Output: gleiches Format `id: 123, delay: 321` auf das gemeinsame Topic `delays`.

Der Step-by-Step-Guide erlaubt explizit, beide Stages **technisch in derselben Anwendung** zu betreiben (das verbindende Topic dürfte in dem Fall entfallen). Wir gehen einen Schritt strikter und schalten Stage 2 **als eigenständigen Konsumenten auf das Zwischen-Topic `group4-route-timing`** — damit sind die beiden EPAs auch im Code-Sinn lose gekoppelt: Stage 2 liesse sich isoliert in eine zweite Anwendung herauslösen, ohne dass an Stage 1 etwas geändert werden müsste, und in Kouncil ist jede Stage einzeln inspizierbar.

## Schritt 3 — Architektur (Topics + Anwendungen)

```
                ┌──────────────────────┐
                │   Stream Generator   │  (Container 192.168.111.11:8080)
                │   GPS-Events  +  REST-Service  +  Dashboard /status/
                └──────────┬───────────┘
                           │ produziert DriverPosition
                           ▼
              Topic:  driver-position
                           │
                           ▼
             ┌────────────────────────────┐
             │  CEP-Anwendung (Java)      │
             │  Two-Thread Plain Consumer/Producer
             │                            │
             │   ┌──────────────────┐     │
             │   │  EPA 1 — Stage 1 │  →  REST /route/{id}?…
             │   │  Map + Enrich    │     │
             │   │  (Thread 1)      │     │
             │   └──────────┬───────┘     │
             │              │ produce      │
             │              ▼             │
             │   Topic: group4-route-timing
             │              │ consume     │
             │   ┌──────────▼───────┐     │
             │   │  EPA 2 — Stage 2 │     │
             │   │  Filter > 180 s  │     │
             │   │  (Thread 2)      │     │
             │   └──────────┬───────┘     │
             └──────────────┼─────────────┘
                            ▼
                Topic:  delays  (geteilt, fest formatiert)
                            │
                            ▼
                   Dashboard /status/
```

**Topic-Übersicht:**

- `driver-position` — gemeinsamer Input-Stream (vom Stream Generator gespeist, alle Gruppen lesen).
- `group4-route-timing` — gruppen-eigenes Zwischen-Topic (Präfix `group4-` zwingend, damit Topics anderer Gruppen nicht überschrieben werden).
- `delays` — gemeinsames Output-Topic, vom Dashboard konsumiert. Format **`id: <DeliveryID>, delay: <Sekunden>`** ist verbindlich; falsch formatierte Events werden ignoriert.

---

# Architektur und Umsetzung

Die Implementierung besteht aus einem einzigen Maven-Modul:

- [`Case5_KafkaApp/`](../Case5_KafkaApp/) — Java-Anwendung mit beiden EPAs (Stage 1 + Stage 2) als zwei unabhängige Threads, jeweils auf Basis von plain `KafkaConsumer` + `KafkaProducer`.

> **Hinweis zur Tool-Wahl.** Der Step-by-Step-Guide steuert auf Kafka Streams zu, schreibt es aber nicht zwingend vor („Sample Java code for implementing Kafka Stream Processing applications", „It is probably the easiest …"). Wir mussten auf die plain Kafka-Clients-API ausweichen, weil der **Group Coordinator** auf dem FHNW-Broker `192.168.111.10:9092` nicht verfügbar ist (`FindCoordinator → errorCode 15 / COORDINATOR_NOT_AVAILABLE`, `__consumer_offsets`-Topic in Kouncil leer). Kafka Streams setzt zwingend einen funktionierenden Group Coordinator voraus (die `application.id` ist die Consumer-Group-ID), und konnte daher gegen die Lehrumgebung nicht laufen. Mit `KafkaConsumer.assign(...)` statt `subscribe(...)` umgehen wir den Coordinator-Pfad komplett — die fachliche Architektur (zwei EPAs, drei Topics, EPN-Pattern) bleibt identisch.

Die Trennung der Stages bleibt im Code (zwei separate Threads, je eigener Consumer + Producer) und im Topic-Layout (`group4-route-timing` als sichtbare Schnittstelle) erhalten — die beiden EPAs sind dadurch sogar strikter entkoppelt als in einer einzelnen Streams-Topology, da sie keine gemeinsamen In-Memory-Strukturen teilen.

## Case5_KafkaApp — Two-Thread Plain Consumer/Producer

Implementiert in [`Launcher.java`](../Case5_KafkaApp/src/main/java/Launcher.java). Jeder EPA läuft in einem eigenen Thread und besitzt einen eigenen `KafkaConsumer` und `KafkaProducer`.

**Gemeinsame Bausteine**

| # | Baustein | Aufgabe |
|---|---|---|
| 1 | Consumer-Config | `bootstrap.servers = 192.168.111.10:9092`, String-Deserializer, **kein** `group.id` (nicht nötig im `assign(...)`-Modus), `enable.auto.commit = false`. |
| 2 | Producer-Config | `bootstrap.servers = 192.168.111.10:9092`, String-Serializer. Producer benötigen keinen Group Coordinator. |
| 3 | `assignFromBeginning(consumer, topic)` | Holt via `consumer.partitionsFor(topic)` die Partitionen (MetadataRequest, kein Coordinator), ruft `consumer.assign(...)` auf und seekt mit `seekToBeginning(...)` an den Topic-Anfang — entspricht dem bisherigen `auto.offset.reset = earliest`. |
| 4 | Shutdown-Hook | Setzt ein gemeinsames `AtomicBoolean running` auf `false`; beide Threads verlassen ihre Poll-Schleife sauber. |

**EPA 1 — Stage 1: `DriverPosition` → `RouteTiming`** (Thread `epa-1-enrichment`)

| # | Schritt | Aufgabe |
|---|---|---|
| 1 | `assignFromBeginning(consumer, "driver-position")` | Subscribe auf den Input-Stream im `assign`-Modus. |
| 2 | `consumer.poll(500ms)`-Schleife | Jedes Event lesen und einzeln verarbeiten. |
| 3 | `Utils.extractCoordinates(value)` | GPS-String parsen; bei `null` Event verwerfen. |
| 4 | `Utils.requestDelay(key, pos)` | REST `/route/{id}?time=…&lat=…&lon=…` aufrufen. |
| 5 | Output-String bilden | `id: <key>, delay: <sec>`. |
| 6 | `producer.send(... INTERMEDIATE_TOPIC ...)` | RouteTiming-Event ins gruppen-eigene Zwischen-Topic publizieren — **Ende von Stage 1**. |

**EPA 2 — Stage 2: `RouteTiming` → `SignificantDelay`** (Thread `epa-2-filter`)

| # | Schritt | Aufgabe |
|---|---|---|
| 1 | `assignFromBeginning(consumer, "group4-route-timing")` | **Beginn von Stage 2:** eigenständiger Konsument auf dem Zwischen-Topic, damit die beiden EPAs technisch wie konzeptionell entkoppelt sind. |
| 2 | `consumer.poll(500ms)`-Schleife | Routenzeiten lesen. |
| 3 | `Utils.extractDelay(value)` + Schwellwert-Filter | `delay > 180` — alles unter 3 min wird verworfen. |
| 4 | `producer.send(... OUTPUT_TOPIC ...)` | SignificantDelay-Event ins gemeinsame Dashboard-Topic publizieren — Format zwingend `id: 123, delay: 321`. |

## Helper — Utils.java

Für GPS-Parsing, REST-Call und Delay-Parsing wird die vom Dozenten gestellte [`Utils.java`](../Case5_KafkaApp/src/main/java/Utils.java) verwendet (nicht selbst neu geschrieben). Zentral sind:

- `Utils.extractCoordinates(String) → GpsPos { time, lat, lon }` — Regex-Parser für `id: …, time: …, lat: …, lon: …`.
- `Utils.requestDelay(String key, GpsPos pos) → int` — baut die REST-URL inkl. URL-encodetem Zeitstempel und parst die Antwort als Sekundenwert (kann negativ sein, wenn der Fahrer schneller als geplant ist).
- `Utils.extractDelay(String) → Integer` — Regex-Parser für `id: …, delay: …`.

## Eingesetzte CEP- und EPN-Patterns

| Pattern | Einsatzort | Begründung |
|---|---|---|
| Event Processing Network (EPN) | Stage 1 + Stage 2 | Komplexität wird in zwei kleine, hintereinandergeschaltete EPAs zerlegt. Kerngedanke des Cases. |
| Event Enrichment | Stage 1 (`mapValues` mit REST-Call) | Roh-Event (GPS) wird mit fachlicher Information (Verspätung in Sekunden) aus externer Quelle (REST-Service) angereichert. |
| Event Filter | Stage 2 (`filter delay > 180 s`) | Reduktion vom Volldatenstrom auf fachlich relevante Events (signifikante Verspätungen). |
| Event Abstraction | RouteTiming → SignificantDelay | Aus mehreren niederwertigen Events wird ein semantisch höherwertiges Event („dieser Fahrer ist relevant zu spät") abgeleitet. |
| Pub/Sub Channel | Kafka-Topics `driver-position`, `group4-route-timing`, `delays` | Lose Kopplung: weder Stream Generator noch Dashboard kennen die CEP-App. |
| Stateless Stream Processing | Plain `KafkaConsumer.poll()`-Schleife je EPA, ohne State Store | Pro Event reicht eine Punktentscheidung, kein Zeitfenster nötig — passend zum „technischen Durchstich". |

---

# Konfiguration

## Case5_KafkaApp — Consumer- und Producer-Properties

Die Konfiguration ist direkt in [`Launcher.java`](../Case5_KafkaApp/src/main/java/Launcher.java) gesetzt (für einen Durchstich angemessen — eine externe `application.properties` ist nicht erforderlich):

```java
// Consumer (je EPA eine eigene Instanz)
bootstrap.servers   = "192.168.111.10:9092"
key.deserializer    = StringDeserializer
value.deserializer  = StringDeserializer
enable.auto.commit  = false
// kein group.id: wir verwenden assign(...) statt subscribe(...)

// Producer (je EPA eine eigene Instanz)
bootstrap.servers   = "192.168.111.10:9092"
key.serializer      = StringSerializer
value.serializer    = StringSerializer
```

Das Weglassen von `group.id` und der Einsatz von `consumer.assign(partitionsFor(topic))` umgehen den Group Coordinator vollständig: parallele Läufe in der Lehrumgebung kollidieren nicht (jeder Consumer liest ohne Group-Membership), und ohne persistierte Offsets startet jeder Lauf am Topic-Anfang (`seekToBeginning(...)`).

## Externe Endpoints

| Host:Port | Komponente | Zweck |
|---|---|---|
| `192.168.111.10:9092` | Kafka Broker | Event-Backbone (alle Topics) |
| `192.168.111.11:8080` | Stream Generator | Erzeugt `driver-position`, REST `/route/{id}`, Dashboard `/status/` |
| `192.168.111.12:8080` | Kouncil Web UI | Topic-Browser (`viewer` / `viewer`, Settings nicht ändern) |

Alle Endpoints sind ausschliesslich über das WireGuard-VPN aus [`group_10.conf`](../../group_10.conf) erreichbar.

## Laufzeit-Umgebung

- Java 17.
- Apache Kafka Clients **3.5.0** (transitiv über `kafka-streams 3.5.0`, siehe [`pom.xml`](../Case5_KafkaApp/pom.xml) — wir benutzen aktiv nur die `KafkaConsumer`/`KafkaProducer`-Klassen).
- SLF4J / reload4j 1.7.36 für Konsolen-Logging.
- Kafka-Broker, Stream Generator und Kouncil als Docker-Container in der FHNW-Lehrumgebung (siehe [`System Overview.png`](../../System%20Overview.png)).
- Netzwerk-Zugriff zwingend über WireGuard-VPN.

---

# GitHub Repository

<https://github.com/lto-oro/it-architecture>

Relevante Ordner:

- [`case5/Case5_KafkaApp/`](../Case5_KafkaApp/) — Maven-Projekt mit der CEP-Anwendung (Stage 1 + Stage 2 als zwei Threads, plain `KafkaConsumer`/`KafkaProducer`).
- [`case5/Materialien zu Umsetzung/`](../Materialien%20zu%20Umsetzung/) — Vom Dozenten gestellter Step-by-Step-Guide, Beispiel-Lösungskonzept, Standalone-`Utils.java`.
- [`case5/Startpunkte Selbststudium/`](../Startpunkte%20Selbststudium/) — Quellen für die Lernfragen (Bruns/Dunkel 2015, Schaaf/Wilke 2015).
- [`case5/Case_5_Zusammenfassung.md`](../Case_5_Zusammenfassung.md) — Arbeits-Zusammenfassung der Gruppe (Vorbereitungsdokument).

---

# Build und Start

## Voraussetzungen

1. WireGuard-VPN mit [`group_10.conf`](../../group_10.conf) aktivieren — sonst hängt jede Verbindung zum Broker / REST-Service.
2. Java 17 und Maven installiert.

## CEP-Anwendung starten

```bash
cd case5/Case5_KafkaApp
mvn -DskipTests package
mvn exec:java -Dexec.mainClass=Launcher
```

Alternativ in der IDE: `Launcher.java` als Java-Application starten.

## Verifikation

- **Kouncil** (<http://192.168.111.12:8080>, `viewer`/`viewer`) — Topic `group4-route-timing` muss neue Events bekommen, Topic `delays` muss unsere `id: …, delay: …`-Strings enthalten.
- **Dashboard** (<http://192.168.111.11:8080/status/>) — sollte die DeliveryIDs unserer Fahrer mit ihren Sekundenverspätungen tabellarisch anzeigen.

---

# Testergebnisse

## Build

Der Maven-Build ist auf Code-Ebene erfolgreich (`mvn -DskipTests compile`). Die Anwendung startet beide EPA-Threads, jeder Thread baut seinen eigenen `KafkaConsumer` (im `assign`-Modus) und `KafkaProducer` auf und betritt seine Poll-Schleife.

## Hintergrund — Wechsel zu Plain Consumer/Producer (Plan B)

In den Vorab-Tests gegen den Broker `192.168.111.10:9092` lieferte `FindCoordinator` für jeden neuen Consumer (Kafka Streams **und** `KafkaConsumer` im `subscribe`-Modus) konstant `errorCode = 15` (`COORDINATOR_NOT_AVAILABLE`) mit leerem Coordinator-Host. In Kouncil war die Consumer-Groups-Liste leer („No data to display"), was darauf hindeutet, dass das interne `__consumer_offsets`-Topic seitens Broker fehlt oder nicht erreichbar ist. Eine Mail an `marc.schaaf@fhnw.ch` blieb bis zum Abgabezeitpunkt unbeantwortet.

Da Kafka Streams ohne funktionierenden Group Coordinator nicht startfähig ist (die `application.id` **ist** die Consumer-Group-ID), haben wir auf die plain Kafka-Clients-API gewechselt und nutzen `KafkaConsumer.assign(...)` statt `subscribe(...)`. Im `assign`-Modus wird kein Group Coordinator angefragt — der defekte Pfad auf dem Broker wird komplett umgangen, und die Anwendung kann trotzdem produzieren und konsumieren. Konzeptionell bleibt die Lösung unverändert: zwei EPAs, drei Topics, EPN-Pattern.

## End-to-End-Test

**Geplanter Test (gegen die Lehrumgebung mit aktivem VPN):**

1. App starten — erwartet im Konsolen-Log:
   ```
   Assigned partitions for driver-position: [...]
   Assigned partitions for group4-route-timing: [...]
   Stage 1 in <- key=<id> value=id: <id>, time: …, lat: …, lon: …
   Requesting: http://192.168.111.11:8080/route/<id>?time=…&lat=…&lon=…
   Stage 1 -> id: <id>, delay: <sec>
   Stage 2 (significant) -> id: <id>, delay: <sec>     (nur falls > 180)
   ```
2. **Kouncil-Check** — Topic `group4-route-timing` füllt sich pro DriverPosition mit einem RouteTiming-Event, Topic `delays` nur mit den signifikanten.
3. **Dashboard-Check** — `http://192.168.111.11:8080/status/` listet die Fahrer aus Schritt 2 auf.

_Platzhalter für Logs:_

```
<KONSOLEN-LOG STAGE 1 + STAGE 2 EINSETZEN>
```

_Platzhalter für Dashboard-Screenshot:_

```
<SCREENSHOT /status/ EINSETZEN>
```

## Verifikations-Matrix

| Anforderung | Erfüllt durch | Nachweis |
|---|---|---|
| F1. zeitnahe Verarbeitung statt Speicherung | Stateless `poll()`-Schleifen, kein State Store, kein DB-Layer | [`Launcher.java`](../Case5_KafkaApp/src/main/java/Launcher.java) Zeilen 62–80, 94–105 |
| F2. zwei-stufiger CEP-Flow | Stage 1 (Enrichment) + Stage 2 (Filter), je eigener Thread + Consumer + Producer, via Zwischen-Topic entkoppelt | [`Launcher.java`](../Case5_KafkaApp/src/main/java/Launcher.java) Zeilen 56–85 (Stage 1), 88–110 (Stage 2) |
| F3. Position-Stream lesen + Verspätung abfragen (Step 2) | `assignFromBeginning(consumer, "driver-position")` + `Utils.requestDelay` | [`Launcher.java:60`](../Case5_KafkaApp/src/main/java/Launcher.java#L60), [`Launcher.java:74`](../Case5_KafkaApp/src/main/java/Launcher.java#L74) |
| F4. signifikante Verspätung erkennen (Step 3) | `delay > 180 s` Schwellwert-Filter | [`Launcher.java:99`](../Case5_KafkaApp/src/main/java/Launcher.java#L99) |
| F5. Dashboard-Format `id: X, delay: Y` | Stage 1 baut den String, Stage 2 reicht ihn unverändert weiter | Output-Format identisch zur Regex `Utils.extractDelay` |
| F6. Topic-Präfix `group4-` für eigene Topics | `group4-route-timing` als `INTERMEDIATE_TOPIC` | [`Launcher.java:22`](../Case5_KafkaApp/src/main/java/Launcher.java#L22) |
| F7. End-to-End-Durchstich gegen Dashboard | _ausstehend — wird beim nächsten VPN-Test gegen die Lehrumgebung ergänzt_ | _<KONSOLEN-LOG …> + <SCREENSHOT …>_ |
| NF1. Helper-Verwendung statt Eigenbau | `Utils.extractCoordinates`, `Utils.requestDelay`, `Utils.extractDelay` | Stage 1 + Stage 2 |
| NF2. Parallele Läufe ohne State-Kollision | Kein `group.id` gesetzt, `assign(...)` statt `subscribe(...)` — keine Group-Membership, also kein gemeinsamer State | [`Launcher.java:114-124`](../Case5_KafkaApp/src/main/java/Launcher.java#L114-L124) |
| NF3. Lose Kopplung der EPAs | Stage 1 publiziert auf `group4-route-timing`; Stage 2 läuft in eigenem Thread und konsumiert es als eigenständiger `KafkaConsumer` | Topic-Layout + [`Launcher.java:92`](../Case5_KafkaApp/src/main/java/Launcher.java#L92) |

---

# Aktueller Stand & offene Punkte

- **Konzeption + Implementierung abgeschlossen.** Event-Modell, 2-Stage-CEP-Flow, Topic-Architektur und Java-Code stehen, der Maven-Build geht durch.
- **Wechsel auf Plain Consumer/Producer (Plan B) vollzogen**, weil der Group Coordinator auf dem FHNW-Broker `192.168.111.10:9092` nicht verfügbar war (`COORDINATOR_NOT_AVAILABLE`) und damit Kafka Streams nicht startfähig blieb. `KafkaConsumer.assign(...)` umgeht den Coordinator-Pfad — die Architektur (zwei EPAs, drei Topics) bleibt erhalten.
- **End-to-End-Test gegen die Lehrumgebung steht aus** und wird beim nächsten Lauf mit aktivem VPN nachgeholt. Logs und Dashboard-Screenshot werden an den im Test-Abschnitt vorgesehenen Stellen ergänzt.

---

# Fazit

Der vom Case geforderte **vereinfachte technische Durchstich** für ein Event-Stream-Processing-Szenario mit Complex Event Processing ist konzeptionell und im Code vollständig umgesetzt: drei klar definierte Event-Typen (`DriverPosition`, `RouteTiming`, `SignificantDelay`), zwei eigenständige Event Processing Agents (Enrichment + Filter), drei Topics in einer sauberen EPN-Topologie und das verbindliche Output-Format `id: X, delay: Y` für das Dashboard.

Die zentralen **Lernziele** des Cases — Verständnis von Event Processing, Abgrenzung zu Enterprise Messaging, Grundprinzipien von CEP (Abstraktion, Muster, EPN) — werden durch die gewählte Lösung direkt abgebildet: aus einem rohen, kontinuierlichen GPS-Strom wird in zwei deklarativen Verarbeitungsstufen ein fachlich aussagekräftiges, höherwertiges Ereignis abgeleitet und an genau die Konsumenten publiziert, die es interessiert — ohne dass Stream Generator oder Dashboard die CEP-Logik kennen müssen.

Der ausstehende End-to-End-Lauf gegen die Lehr-Infrastruktur ist der einzige offene Punkt und ist eine Umgebungsfrage, kein konzeptioneller oder codebezogener Mangel.
