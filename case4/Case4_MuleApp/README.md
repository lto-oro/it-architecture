# Case4_MuleApp — Mulesoft ESB Flow (Gruppe 4)

Durchstich für **Case 4 Teil 1 + Teil 2** (Entscheidungen E1–E5 in
[`../Analyse_Loesungskonzept.md`](../Analyse_Loesungskonzept.md)).

## Was der Flow macht

`Scheduler → WSC (getNewJobs) → Transform (Job‑Liste) → ForEach →
Transform (Job → JSON) → Choice (type) → JMS Publish`

- **Teil 1**: ruft alle 60 s den Callcenter‑SOAP‑Service
  `http://192.168.111.9:9090/service/cc` auf (Operation `getNewJobs`).
- **Teil 2**: routet je Job anhand `type`:
  - `type == "Repair"` → Queue `group4.jobs.urgent` (dringend, Disposition)
  - `type == "Maintanence"` → Queue `group4.jobs.new` (Routine, Aussendienst)
- Publish‑Format: `TextMessage` mit JSON‑Body und User‑Property
  `_type = ch.fhnw.digi.mockups.case3.JobMessage` (kompatibel mit
  `MappingJackson2MessageConverter` des Spring‑Clients aus Case 3/4).

## Dateien

| Pfad | Zweck |
| --- | --- |
| `pom.xml` | Mule‑Maven‑Plugin, Runtime 4.4.0, Connectors, `activemq-client` als `sharedLibrary` |
| `mule-artifact.json` | `minMuleVersion` |
| `src/main/mule/global-config.xml` | `configuration-properties` + `wsc:config` + `jms:config` |
| `src/main/mule/callcenter-to-queue.xml` | Der eigentliche Flow |
| `src/main/resources/application.properties` | Endpoint‑URL, Credentials, Queue‑Namen, Poll‑Frequenz |
| `src/main/resources/wsdl/cc.wsdl` | Lokale WSDL‑Kopie (schemaLocation auf `cc.xsd` umgebogen) |
| `src/main/resources/wsdl/cc.xsd` | Lokale XSD‑Kopie |
| `src/main/resources/log4j2.xml` | Logging |

## Voraussetzungen

- **WireGuard‑VPN aktiv** (`../../group_10.conf`) — sowohl `192.168.111.9:9090`
  (Callcenter) als auch `192.168.111.6:61616` (ActiveMQ) müssen erreichbar sein.
- **Anypoint Studio 7.17.0** (bringt Mule Runtime 4.6 mit; Flow ist auf 4.4
  gepinnt und läuft auf beiden).

## Import in Anypoint Studio

1. `File` → `Import` → `Anypoint Studio` → `Anypoint Studio project from File System`
2. Project Root: `case4/Case4_MuleApp/`
3. `Finish` — Studio löst Dependencies automatisch auf.

## Ausführen (lokal)

1. WireGuard aktivieren.
2. Rechtsklick auf `callcenter-to-queue.xml` → `Run` → `Mule Application`.
3. Alle 60 s erscheint im Studio‑Console‑Log:
   ```
   INFO case4.router: [MAINTANENCE -> group4.jobs.new] {...}
   INFO case4.router: [REPAIR     -> group4.jobs.urgent] {...}
   ```
4. Die Nachrichten sind in der ActiveMQ‑Webkonsole
   [http://192.168.111.6:8161](http://192.168.111.6:8161) unter
   `Queues` sichtbar (`group4.jobs.new`, `group4.jobs.urgent`).

## End‑to‑End‑Demo mit Case4_ClientApp

Parallel zum Mule‑Flow den angepassten Spring‑Boot‑Client starten
([`../Case4_ClientApp`](../Case4_ClientApp)):

```
cd ../Case4_ClientApp
mvn spring-boot:run
```

Die Swing‑UI zeigt beide Queues kombiniert in der Liste „offene Jobs";
Konsolenausgabe markiert `[NEW]` bzw. `[URGENT]`.

## Konfigurations‑Overrides

Alles über `src/main/resources/application.properties`:

| Key | Default | Zweck |
| --- | --- | --- |
| `cc.service.address` | `http://192.168.111.9:9090/service/cc` | SOAP‑Endpoint |
| `cc.poll.frequency` | `60` (Sekunden) | Scheduler |
| `activemq.broker-url` | `tcp://192.168.111.6:61616` | ActiveMQ |
| `activemq.user` / `activemq.password` | `group4` / `Password4` | aus Case 3 |
| `queue.new` | `group4.jobs.new` | Ziel für Maintanence |
| `queue.urgent` | `group4.jobs.urgent` | Ziel für Repair |
| `jms.type.property` | `ch.fhnw.digi.mockups.case3.JobMessage` | Jackson‑Typ‑ID für den Client |
