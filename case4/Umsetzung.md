# Case 4 — Umsetzungsdokumentation (Gruppe 4)

Zugehöriges Konzept: [`Analyse_Loesungskonzept.md`](Analyse_Loesungskonzept.md)
Umsetzungsartefakte: [`Case4_MuleApp/`](Case4_MuleApp/) (Mule‑Flow), [`Case4_ClientApp/`](Case4_ClientApp/) (Spring‑Boot‑Konsument)

---

## 1. Kurzfassung

Der Durchstich deckt **Teil 1 (Callcenter‑Anbindung)** und **Teil 2 (Content‑Based Routing)** in einem einzigen Mule‑Flow ab (Entscheidung E2). Der Flow löst periodisch aus, ruft per SOAP den Callcenter‑Service auf, transformiert die Antwort in das bestehende `JobMessage`‑JSON‑Format und publiziert jeden Job – je nach `type` – auf eine der beiden ActiveMQ‑Queues `group4.jobs.new` (Maintanence) bzw. `group4.jobs.urgent` (Repair).

Der bestehende Case‑3‑Client wurde nach [`Case4_ClientApp/`](Case4_ClientApp/) kopiert und so angepasst, dass er beide Queues als Point‑to‑Point‑Konsument liest und damit die End‑to‑End‑Demo liefert.

Live‑verifiziert wurden vorab:
- Erreichbarkeit `192.168.111.9:9090` (SOAP 200, 2 Jobs pro Call).
- Erreichbarkeit `192.168.111.6:8161` (ActiveMQ Web‑Konsole, 401 = erwartet, Credentials `group4`/`Password4`).
- WSDL/XSD geladen, Service liefert `customernumber`, `description`, `jobnumber`, `region`, `scheduledDateTime`, `type ∈ {Maintanence, Repair}`.

---

## 2. Repository‑Layout

```
case4/
├── Analyse_Loesungskonzept.md           # Anforderungen, Entscheidungen, Architektur
├── Umsetzung.md                         # dieses Dokument
├── Case4_MuleApp/                       # Mule 4 Maven‑Projekt (Teil 1 + Teil 2)
│   ├── pom.xml
│   ├── mule-artifact.json
│   ├── README.md
│   └── src/main/
│       ├── mule/
│       │   ├── global-config.xml        # wsc:config + jms:config + properties
│       │   └── callcenter-to-queue.xml  # der Flow
│       └── resources/
│           ├── application.properties   # Endpoints, Credentials, Queues, Polling
│           ├── log4j2.xml
│           └── wsdl/
│               ├── cc.wsdl              # lokale Kopie (schemaLocation → cc.xsd)
│               └── cc.xsd
└── Case4_ClientApp/                     # Fork von case3/Case3_ClientApp
    ├── pom.xml                          # artifactId auf Case4_ClientApp umbenannt
    └── src/main/java/ch/fhnw/digi/mockups/case3/
        ├── client/MessageReceiver.java  # neu: queueFactory + urgent‑Listener
        └── …                            # restliche Case‑3‑Klassen unverändert
```

Begründungen:
- Package `ch.fhnw.digi.mockups.case3` **bewusst beibehalten**. Der Mule‑Flow setzt die JMS‑User‑Property `_type = ch.fhnw.digi.mockups.case3.JobMessage`. Spring’s `MappingJackson2MessageConverter` lädt die Klasse exakt unter diesem FQCN. Eine Umbenennung würde die Deserialisierung brechen.
- Case 3 bleibt unter [`case3/Case3_ClientApp/`](../case3/Case3_ClientApp/) **unverändert**, damit die Case‑3‑Abgabe reproduzierbar bleibt.

---

## 3. Mule‑Flow im Detail

### 3.1 Globale Konfigurationen ([`global-config.xml`](Case4_MuleApp/src/main/mule/global-config.xml))

| Element | Zweck |
| --- | --- |
| `configuration-properties` | Lädt `application.properties` → alle Adressen/Credentials/Queues sind extern konfigurierbar, keine Magic Strings im Flow. |
| `wsc:config name="callcenter-soap-config"` | Web Service Consumer. `wsdlLocation=wsdl/cc.wsdl` (lokale Kopie, damit Startup nicht am Netz hängt), Service + Port fest, `address=${cc.service.address}` override‑bar. |
| `jms:config name="activemq-config"` | `active-mq-connection` mit `brokerUrl=${activemq.broker-url}`, Credentials aus Properties. Wird von beiden Publish‑Endpoints geteilt. |

### 3.2 Flow ([`callcenter-to-queue.xml`](Case4_MuleApp/src/main/mule/callcenter-to-queue.xml))

```
┌ Scheduler (fixed‑frequency ${cc.poll.frequency}s) ┐
│                                                   │
│  → wsc:consume operation="getNewJobs"             │
│  → ee:transform  (SOAP body → Array<Job>)          │
│  → foreach collection="#[payload]"                 │
│       → ee:transform (Job → JobMessage JSON)       │
│       → choice  payload.type == "Repair"?          │
│            when   → jms:publish ${queue.urgent}    │
│            other  → jms:publish ${queue.new}       │
└───────────────────────────────────────────────────┘
```

Pro Komponente:

1. **Scheduler** — `fixed-frequency frequency="${cc.poll.frequency}" timeUnit="SECONDS"`. Default 60 s (Annahme A4). Löst den Flow periodisch aus; kein externer Trigger nötig, da der SOAP‑Service kein Push anbietet (F1.7).

2. **`wsc:consume operation="getNewJobs"`** — führt den SOAP‑Call aus. Payload enthält anschliessend den `getNewJobsResponse`‑Body.

3. **Transform „SOAP body → Job list"**
   ```dw
   %dw 2.0
   output application/java
   ---
   (payload..*job default []) as Array
   ```
   Der Descendants‑Selector `..*job` ist namespace‑agnostisch und robust gegen Unterschiede im WSC‑Response‑Wrapping (wichtig: der Service antwortet mit Namespace‑Präfix `ns2`, siehe Raw‑Response in §5). `default []` schützt gegen leere Antworten; `as Array` fixiert den Typ für das nachfolgende `foreach`.

4. **`foreach collection="#[payload]"`** — iteriert über die Job‑Liste (laut Spec zwei Jobs pro Call).

5. **Transform „Job → JobMessage JSON"**
   ```dw
   %dw 2.0
   output application/json
   ---
   {
       jobnumber:         payload.jobnumber         as String,
       customernumber:    payload.customernumber    as String,
       description:       payload.description       as String,
       region:            payload.region            as String,
       scheduledDateTime: payload.scheduledDateTime as String,
       type:              payload.type              as String
   }
   ```
   Feldnamen 1:1 zu [`JobMessage.java`](Case4_ClientApp/src/main/java/ch/fhnw/digi/mockups/case3/JobMessage.java). `type` bleibt als String `"Maintanence"` / `"Repair"`; Jackson mappt ihn clientseitig auf das Enum `JobType`. `as String` verhindert, dass DataWeave Text‑Node‑Wrapper statt Skalaren schreibt.

6. **`choice` — Content‑Based Router (Teil 2)**
   ```xml
   <when expression='#[payload.type == "Repair"]'>    → group4.jobs.urgent
   <otherwise>                                        → group4.jobs.new
   ```
   Entscheidung E1: `Repair` = dringend (Dispositionsabteilung), alles andere = Routine (Aussendienst). Beleg: [`case3/case3-3.pdf`](../case3/case3-3.pdf) Kap. 1.

7. **`jms:publish`** — pro Zweig eine Publish‑Action:
   ```xml
   <jms:message>
       <jms:body>#[output application/json --- payload]</jms:body>
       <jms:properties>#[{"_type": p('jms.type.property')}]</jms:properties>
   </jms:message>
   ```
   - `jms:body` erzeugt den JSON‑Text der `TextMessage`.
   - `jms:properties` setzt die JMS‑User‑Property `_type`. Diese wird von `MappingJackson2MessageConverter.setTypeIdPropertyName("_type")` clientseitig ausgelesen und bestimmt die Ziel‑POJO‑Klasse.

Zusätzlich loggt ein `<logger>` vor jedem Publish eine Zeile `[REPAIR → group4.jobs.urgent] {...}` bzw. `[MAINTANENCE → group4.jobs.new] {...}`, damit das Routing im Studio‑Console‑Log sofort sichtbar ist (Demo‑/Nachweis‑Zweck).

### 3.3 Externe Konfiguration ([`application.properties`](Case4_MuleApp/src/main/resources/application.properties))

| Key | Wert | Zweck |
| --- | --- | --- |
| `cc.service.address` | `http://192.168.111.9:9090/service/cc` | SOAP‑Endpoint (überschreibt die Adresse aus der WSDL) |
| `cc.poll.frequency` | `60` | Scheduler‑Intervall in Sekunden |
| `activemq.broker-url` | `tcp://192.168.111.6:61616` | Broker |
| `activemq.user` / `activemq.password` | `group4` / `Password4` | aus Case 3 |
| `queue.new` | `group4.jobs.new` | Ziel Maintanence |
| `queue.urgent` | `group4.jobs.urgent` | Ziel Repair |
| `jms.type.property` | `ch.fhnw.digi.mockups.case3.JobMessage` | `_type`‑Header für Jackson |

---

## 4. Case4_ClientApp — Anpassungen gegenüber Case 3

Nur [`MessageReceiver.java`](Case4_ClientApp/src/main/java/ch/fhnw/digi/mockups/case3/client/MessageReceiver.java) und [`pom.xml`](Case4_ClientApp/pom.xml) wurden geändert. Alles andere ist byte‑gleich zu Case 3.

Diff auf einen Blick (konzeptionell):

| Vor (Case 3) | Nach (Case 4) |
| --- | --- |
| ein `@Bean myFactory` mit `setPubSubDomain(true)` | zwei Factories: `queueFactory` (pubSub=false) und `topicFactory` (pubSub=true) |
| `@JmsListener(destination="dispo.jobs.new", containerFactory="myFactory")` | `@JmsListener(destination="group4.jobs.new", containerFactory="queueFactory")` |
| – | zusätzlicher `@JmsListener(destination="group4.jobs.urgent", containerFactory="queueFactory")` |
| `@JmsListener(destination="dispo.jobs.assignments", containerFactory="myFactory")` | `… containerFactory="topicFactory"` (identisches Verhalten, andere Factory) |

Begründungen:
- **Zwei Factories statt einer** — JMS‑spec: eine Destination ist entweder Queue oder Topic, eine Factory entscheidet via `pubSubDomain` über das Verhalten. Da der Rückkanal `dispo.jobs.assignments` weiterhin ein Topic ist, werden zwei Factories gebraucht.
- **Rückkanal `dispo.jobs.assignments` bleibt aktiv** — er gehört zum Case‑3‑Zuweisungs‑Flow, nicht zum Case‑4‑Scope. Er verhindert kein Teil des Case‑4‑Durchstichs und entfernt den Diff gegenüber Case 3 auf das absolute Minimum.
- **Ein Listener pro Queue** — ursprüngliche Überlegung war, die `urgent`‑Queue in der UI farblich zu markieren; dagegen entschieden, weil (a) minimaler Eingriff (E3) und (b) die Konsolen‑Prefixes `[NEW]`/`[URGENT]` in Verbindung mit dem Mule‑Log für die Demo ausreichen.

`application.properties` ist unverändert — dieselben Broker‑Credentials wie in Case 3.

---

## 5. Verifikation

### 5.1 Vorab‑Checks ohne Mule

```bash
# 1) SOAP‑Endpoint (WireGuard aktiv)
curl -s -X POST -H 'Content-Type: text/xml; charset=utf-8' -H 'SOAPAction: ""' \
  --data '<?xml version="1.0"?><soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:cc="http://callcenter.case4.mockups.digi.fhnw.ch/">
      <soapenv:Body><cc:getNewJobs/></soapenv:Body>
    </soapenv:Envelope>' \
  http://192.168.111.9:9090/service/cc
```

Erwartete Antwort (gekürzt):
```xml
<ns2:getNewJobsResponse xmlns:ns2="http://callcenter.case4.mockups.digi.fhnw.ch/">
  <return>
    <job><customernumber>c645242</customernumber><description>A fun maintanence job</description>
         <jobnumber>job_31154</jobnumber><region>Solothurn</region>
         <scheduledDateTime>2026-04-20T21:27:48.177017</scheduledDateTime>
         <type>Maintanence</type></job>
    <job>… <type>Repair</type></job>
  </return>
</ns2:getNewJobsResponse>
```

```bash
# 2) ActiveMQ‑Webkonsole
curl -s -o /dev/null -w "%{http_code}\n" http://192.168.111.6:8161/admin/
# 401 = erwartet (Basic Auth), login group4 / Password4
```

### 5.2 End‑to‑End‑Demo

1. WireGuard aktivieren (`../group_10.conf`).
2. `Case4_MuleApp` in Anypoint Studio 7.17.0 importieren, `callcenter-to-queue.xml` → Run as Mule Application.
3. Parallel Case4_ClientApp starten:
   ```bash
   cd case4/Case4_ClientApp && mvn spring-boot:run
   ```
4. Alle 60 s:
   - Studio‑Console zeigt `[MAINTANENCE → group4.jobs.new] {…}` und/oder `[REPAIR → group4.jobs.urgent] {…}`.
   - Client‑Console zeigt `[NEW] Routine-Job empfangen: …` und/oder `[URGENT] Reparatur-Job empfangen: …`.
   - ActiveMQ‑Webkonsole (`http://192.168.111.6:8161` → Queues) zählt die publizierten Nachrichten.
   - Swing‑UI des Clients listet die Jobs unter „offene Jobs".

### 5.3 Negative Pfade (bewusst nicht implementiert)

| Szenario | Verhalten |
| --- | --- |
| Callcenter liefert leere Liste | `default []` in der Transform ⇒ `foreach` läuft 0× ⇒ kein Publish. |
| Callcenter‑Timeout / 5xx | Flow schlägt fehl, Scheduler triggert beim nächsten Intervall neu. Keine Retries, keine DLQ (Case‑Scope, siehe §2 „Abgrenzungen" im Konzept). |
| Unbekannter `type`‑Wert | Fiele in den `otherwise`‑Zweig → `group4.jobs.new`. Konsequenz akzeptiert, weil die WSDL `type` auf Enum `{Maintanence, Repair}` einschränkt (serverseitig validiert). |
| Duplikate (Service zustandslos, A1) | Bewusst keine Deduplizierung (Case‑Scope). |

---

## 6. Mapping Anforderung → Artefakt

| ID | Anforderung | Erfüllt durch |
| --- | --- | --- |
| F1.1 | Automatisierter Abruf statt manuell | `<scheduler>` + `<wsc:consume>` in [`callcenter-to-queue.xml`](Case4_MuleApp/src/main/mule/callcenter-to-queue.xml) |
| F1.2 | SOAP `getNewJobs` | `wsc:consume operation="getNewJobs"`, WSDL‑Operation 1:1 |
| F1.3 | Endpoint `192.168.111.9:9090/service/cc` | [`application.properties`](Case4_MuleApp/src/main/resources/application.properties) → `cc.service.address` |
| F1.4 | An ActiveMQ‑Broker weitergeben | `jms:publish` mit `jms:config` gegen `tcp://192.168.111.6:61616` |
| F1.5 | Queue `group4.jobs.new` (nicht Topic) | `queue.new=group4.jobs.new`; Client liest mit `setPubSubDomain(false)` |
| F1.6 | Alle Felder erhalten | DataWeave bildet alle 6 Felder inkl. `type` ab |
| F1.7 | Polling | `<fixed-frequency frequency="${cc.poll.frequency}" timeUnit="SECONDS"/>` |
| F2.1 | Dringend vs. Routine trennen | `<choice>` im Flow |
| F2.2 | Content‑Based via Choice Router | ebd. |
| F2.3 | Kriterium `type` | `payload.type == "Repair"` |
| F2.4 | `Repair` → urgent, `Maintanence` → new | `queue.urgent` / `queue.new` in beiden `when`/`otherwise`‑Branches |
| F3.\* | Nur Konzept | [`Analyse_Loesungskonzept.md` §4.4](Analyse_Loesungskonzept.md) |
| NF1 | Lose Kopplung | ESB als Vermittler; Client kennt nur Queue‑Name, nicht Callcenter |
| NF2 | Wartbarkeit | Alle Parameter extern in `application.properties` |
| NF3 | Mulesoft ESB / Anypoint 7.17 | pom.xml auf Mule 4.4, Flow‑XML Studio‑kompatibel |
| NF4 | Durchstich demonstrierbar | End‑to‑End in §5.2 beschrieben |
| NF5 | Dokumentation | Konzept + dieses Dokument |

---

## 7. Bewusst weggelassen

- **Fehlerbehandlung / Retries / DLQ** — nicht Teil des Durchstichs, würde die Demo ohne Nutzen verkomplizieren.
- **TLS / WS‑Security** — explizit ausgeklammert im Case‑Text.
- **Idempotenz / Deduplizierung** — A1, kein Nutzen für den Lernzielfokus.
- **Service‑Registry / UDDI** — für Teil 3 konzeptuell diskutiert, nicht gebaut.
- **Orchestrator / BPEL** — Overengineering für zwei Routing‑Targets.

---

## 8. Referenzen

- Konzept: [`Analyse_Loesungskonzept.md`](Analyse_Loesungskonzept.md)
- Case‑Grundlagen: [`case_4.pdf`](case_4.pdf), [`Hinweise_umsetzung.pdf`](Hinweise_umsetzung.pdf), [`Enterprise_Service_Bus_An_overview.pdf`](Enterprise_Service_Bus_An_overview.pdf)
- Case‑3‑Basis: [`../case3/case3-3.pdf`](../case3/case3-3.pdf), [`../case3/Case3_ClientApp/`](../case3/Case3_ClientApp/), [`../case3/Case3_Abgabe/EIP_Loesungskonzept.drawio.png`](../case3/Case3_Abgabe/EIP_Loesungskonzept.drawio.png)
- Infrastruktur: [`../System Overview.png`](../System%20Overview.png), [`../group_10.conf`](../group_10.conf)
- Mule‑Projekt‑README: [`Case4_MuleApp/README.md`](Case4_MuleApp/README.md)
