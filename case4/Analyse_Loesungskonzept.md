# Case 4 — Enterprise Service Bus: Analyse und Lösungskonzept

Modul: FHNW Software Architecture, FS26 (Marc Schaaf, marc.schaaf@fhnw.ch)
Gruppe: **Gruppe 4** (Roberto Panizza, Loris Trifoglio — bestätigt über Case-3-Artefakte; der Dateiname `group_10.conf` ist irreführend und bezeichnet lediglich die VPN-Config-Nummer).
Grundlagen Case 4: [case_4.pdf](case_4.pdf), [Hinweise_umsetzung.pdf](Hinweise_umsetzung.pdf), [Hinweise_Umsetzung.md](Hinweise_Umsetzung.md), [Enterprise_Service_Bus_An_overview.pdf](Enterprise_Service_Bus_An_overview.pdf)
Modulweite Grundlagen: [../Modulübersicht.pdf](../Modulübersicht.pdf), [../System Overview.png](../System%20Overview.png)
Case-3-Artefakte (unsere bestehende Lösung, Vorgänger von Case 4): [../Case3_Abgabe/EIP_Loesungskonzept.drawio.png](../Case3_Abgabe/EIP_Loesungskonzept.drawio.png), [../Case3_ClientApp/](../Case3_ClientApp/) (Spring-Boot-Client mit JMS + Jackson)

---

## 1. Anforderungen

### 1.1 Funktionale Anforderungen

**Teil 1 — Anbindung des Callcenters (Umsetzung gefordert)**

- F1.1 Aufträge des Callcenters automatisch aus dessen Verwaltungssystem abrufen, statt sie manuell an die Queue zu übergeben.
- F1.2 Aufruf des Callcenter-Web-Service (HTTP+SOAP) über die Methode `getNewJobs`. Der Service liefert pro Aufruf beispielhaft zwei neue Jobs.
- F1.3 Endpunkt: `http://192.168.111.9:9090/service/cc` (WSDL unter `?WSDL`, XSD unter `?xsd=1`). Zugriff nur via WireGuard-VPN.
- F1.4 Die abgerufenen Jobs müssen an den bestehenden ActiveMQ-Broker aus Case 3 weitergegeben werden.
- F1.5 Ziel-Destination ist eine gruppenspezifische Queue `groupX.jobs.new` (nicht mehr das Topic `dispo.jobs.new` aus Case 3).
- F1.6 Jede empfangene Job-Nachricht enthält mindestens: `customernumber`, `jobnumber`, `description`, `region`, `scheduledDateTime`, `type` (Werte laut Beispiel: `Maintenance`, `Repair`). Diese Felder müssen im weitergereichten Payload erhalten bleiben.
- F1.7 Der Abruf erfolgt periodisch (Polling) — der Service bietet keine Push-Schnittstelle.

**Teil 2 — Message Routing (Umsetzung optional, einer von Teil 1 oder 2 als Durchstich)**

- F2.1 Dringende Aufträge sollen von Routineaufträgen getrennt und an unterschiedliche Ziele geroutet werden (konzeptionell in Case 3 vorgesehen, dort nicht umgesetzt).
- F2.2 Entscheidung inhaltsbasiert (Content-Based Routing) über den Mulesoft Choice Router.
- F2.3 Kriterium für „dringend" vs. „routine" ist nicht explizit definiert → **offene Frage / Annahme**: nutzbar erscheint das Feld `type` (`Repair` = dringend, `Maintenance` = routine) oder `scheduledDateTime` (nahe Fälligkeit = dringend).

**Teil 3 — Stabilität von Service-Adressen (nur Konzept, keine Umsetzung)**

- F3.1 Geschäftsprozesse / External Service Tasks sollen externe Services nicht mehr direkt adressieren.
- F3.2 Wenn ein Service auf einen anderen Server umzieht, dürfen weder Prozessmodelle noch Task-Implementierungen angepasst werden müssen.

### 1.2 Nicht-funktionale Anforderungen

- NF1 Lose Kopplung zwischen Callcenter-System, ESB und Auftragsdisposition (Änderungen auf einer Seite sollen die andere nicht brechen).
- NF2 Wartbarkeit / Betreibbarkeit: Lösung auf Mulesoft ESB, wird durch die IT-Abteilung betrieben.
- NF3 Technologische Vorgabe: Mulesoft ESB + Anypoint Studio 7.17.0 (bereits entschieden).
- NF4 Demonstrierbarkeit: „technischer Durchstich, welcher die zentralen Funktionen demonstriert" — d. h. keine produktionsreife Lösung, aber end-to-end lauffähig.
- NF5 Dokumentation: Lösungskonzept mit nachvollziehbarer Begründung + Umsetzung (PBL-Abgabe auf Moodle).

---

## 2. Randbedingungen, Annahmen und Abgrenzungen

### Randbedingungen (aus den Unterlagen)

- Mulesoft ESB und Anypoint Studio sind gesetzt (keine Tool-Evaluation nötig).
- ActiveMQ-Broker aus Case 3 existiert und wird wiederverwendet.
- Netzwerkzugriff auf den Callcenter-Service nur via WireGuard.
- Abgabe erfolgt im Rahmen des PBL-Prozesses (Lernfragen, Konzept, Umsetzung, Reflektion).

### Infrastruktur / Umgebung (aus [../System Overview.png](../System%20Overview.png))

- Host: Linux (Debian Bookworm), Container via Docker/Podman.
- Zugriff ausschliesslich über WireGuard in das Netzwerk `192.168.111.0/24` (Bridge `br_sw_arch`). Unsere VPN-Adresse: `192.168.111.139/24` (siehe `../group_10.conf`).
- Für Case 4 relevante Container (gruppiert als „Case 3&4"):
  - **ActiveMQ** auf `192.168.111.6:61616` — Ziel für die Job-Nachrichten. Login: `group4` / `Password4` (aus Case-3-Client bestätigt).
  - **JobGenerator** auf `192.168.111.7` — Erzeugt Jobs (Case 3-Kontext, in Case 4 nicht direkt benötigt).
  - **JobAssigner** auf `192.168.111.8` — Konsument der Queue, dispatcht die Jobs (Case 3-Kontext).
  - **CallCenter** (SOAP-Service) auf `192.168.111.9:9090` — Quelle für `getNewJobs`. Bestätigt durch die WSDL-URL in den Case-Hinweisen.
- Andere Cases (Camunda, Kafka, Konsul, MySQL) sind für Case 4 nicht relevant.

### Kontext aus Case 3 (bestehende Lösung von Gruppe 4)

Unsere Case-3-Implementierung ([Case3_ClientApp](../Case3_ClientApp/), [EIP_Loesungskonzept](../Case3_Abgabe/EIP_Loesungskonzept.drawio.png)) hat Folgendes bereits etabliert, das Case 4 nun aufgreift:

- **Nachrichtenformat (kritisch für Kompatibilität)**: JMS `TextMessage` mit JSON-Body, serialisiert via `MappingJackson2MessageConverter` mit `_type`-Property als Typ-Indikator (siehe [MessageReceiver.java](../Case3_ClientApp/src/main/java/ch/fhnw/digi/mockups/case3/client/MessageReceiver.java)). Der Mule-Flow in Case 4 muss dasselbe Format erzeugen, sonst kann der bestehende Client die Nachrichten nicht deserialisieren.
- **Job-Datenstruktur** (aus [JobMessage.java](../Case3_ClientApp/src/main/java/ch/fhnw/digi/mockups/case3/JobMessage.java)): `jobnumber`, `customernumber`, `description`, `region`, `scheduledDateTime` (String), `type` (Enum).
- **Enum-Werte für `type`**: Case-3-POJO verwendet `Maintanence` (Schreibfehler im Code!) und `Repair`. **Offene Frage**: liefert der Callcenter-WSDL diesen Typo ebenfalls oder korrekt `Maintenance`? → im Transform Message des Flows ggf. normalisieren.
- **EIP-Pattern in Case 3** bereits konzipiert:
  - Content-Based Router (Auftragstyp): `Repair` → „Direkte Koordination (out of scope)"; `Maintanence` → Topic `dispo.jobs.new`.
  - Topic → Durable Subscribers → Message Filter (Region) → Client Apps.
  - Job-Anforderung über Queue `dispo.jobs.requestAssignment` (Competing Consumers → Disposition).
  - Zuweisungs-Rückkanal über Topic `dispo.jobs.assignments`.
- Die **manuelle Schnittstelle** war genau der Schritt `Callcenter → Topic dispo.jobs.new` — also der Punkt, den Case 4 Teil 1 automatisiert.

### Relevanz von Case 3 für Case 4 (konsolidiert)

1. **Teil 1 ersetzt den bisher manuellen Schritt**: Der Mule-Flow übernimmt die Rolle, die zuvor „von Hand" erledigt wurde (Callcenter → Broker).
2. **Ziel-Destination hat sich geändert**: Nicht mehr Topic `dispo.jobs.new`, sondern Queue `group4.jobs.new` (explizite Anweisung in [Hinweise_umsetzung.pdf](Hinweise_umsetzung.pdf)). Das entkoppelt unsere Gruppe von den anderen und macht aus dem Pub/Sub-Kanal einen Point-to-Point-Kanal — der bestehende Client muss entsprechend angepasst werden (`setPubSubDomain(false)` für diese Destination) **oder** der JMS-Client bleibt auf dem Topic und wir führen zusätzlich einen Mule-internen Schritt ein. **Einfachster Weg**: wir publizieren in die Queue wie gefordert; wer die Queue liest, ist Sache der jeweiligen Gruppe.
3. **Teil 2 (Choice Router) passt konzeptionell zum bereits in Case 3 vorgesehenen Content-Based Router**. Die Dringlichkeits-Abgrenzung ist dort über `type` gelöst (`Repair` = dringend, `Maintanence` = routine). Damit ist Annahme A2 aus Kapitel 2 durch Case 3 gestützt und nicht mehr rein spekulativ.
4. **Gruppen-Identifikation**: Gruppe 4 ist durch Case-3-Code, Drawio-Titel und ActiveMQ-Credentials eindeutig belegt. Queue-Name konsequent `group4.jobs.new` (ggf. `group4.jobs.urgent`).

### Abgabeformat (aus [../Modulübersicht.pdf](../Modulübersicht.pdf))

Das Modul verwendet *Design-oriented Problem-based Learning* mit vier Phasen (Problemanalyse → Recherche → Problemlösung → Reflektion). Pro Case sind **zwingend** abzugeben (Abgabe via Moodle durch den Moderator, Fristen auf Moodle):

- **Beantwortung der Lernfragen** (gruppenweit erarbeitet, individuelle Learning Reports pro Person).
- **Lösungskonzeption** mit nachvollziehbarer Begründung.
- **Lösungsumsetzung** (nur wenn der Case diese fordert — für Case 4 gefordert: Durchstich für Teil 1 oder Teil 2).
- **Protokoll** aus Phase 1 (durch Schriftführer).

Zusätzliche modulweite Abgaben (nicht pro Case, sondern je einmal pro Semester, von Schaaf zugewiesen):

- **Lösungspräsentation** als Video (15–25 Min, alle Gruppenmitglieder ca. gleicher Anteil) für einen zugewiesenen Case. Inhalt: Vorstellung Case, neue Konzepte, Lösungskonzept + Demo.
- **Lösungsverteidigung** mündlich für einen zugewiesenen Case — *individuelle* Bewertung, jede Person muss die gesamte Gruppen-Lösung verteidigen können (Fragen werden zufällig zugeteilt).

Gesamtnote = Gruppennote Präsentation + individuelle Note Verteidigung + Gruppennote Case-Abgaben.

**Konkrete Konsequenzen für Case 4:**

- Das Abgabeartefakt ist zumindest: Lernfragen-Antworten, dieses Lösungskonzept-Dokument, die Mulesoft-Implementierung des gewählten Teils (Mule-Projekt / Flow-Export), und ein Phase-1-Protokoll.
- Da jede Person die Lösung verteidigen können muss, muss *jedes* Gruppenmitglied Mule-Flow, Callcenter-WSDL und ActiveMQ-Anbindung verstehen — nicht nur der Umsetzende.
- Coaching-Termine laufen über Moodle; Rückfragen per E-Mail an marc.schaaf@fhnw.ch (nicht Teams).

### Annahmen (bewusst markiert, weil nicht im Case-Text)

- A1 Der Callcenter-Service ist zustandslos bezüglich bereits abgerufener Jobs — jeder `getNewJobs`-Aufruf liefert schlicht „die nächsten zwei Jobs" (keine ID-basierte Deduplizierung auf Clientseite notwendig). **Offen** — falls der Service Jobs mehrfach liefert, wäre Idempotenz clientseitig nötig.
- A2 Das Schlüsselfeld für Dringlichkeit in Teil 2 ist `type` (`Repair` = dringend, `Maintanence` = routine). Durch unsere Case-3-Lösung gestützt, dort explizit als CBR-Kriterium modelliert.
- A3 Die Zielqueue heisst `group4.jobs.new`. Für „dringende" Routes in Teil 2 wäre `group4.jobs.urgent` eine plausible Konvention. **Offen** — zweite Queue nicht explizit vorgegeben.
- A4 Die Polling-Frequenz wird pragmatisch gewählt (z. B. alle 30–60 s im Dev-Durchstich). **Offen** — nicht spezifiziert. *Hinweis*: Der Case-3-JobGenerator publiziert laut Kommentar „alle 2 Sekunden" — für uns ist das nicht relevant, aber zeigt, dass höhere Frequenzen toleriert werden.
- A5 Nachrichtenformat ist **JMS TextMessage mit JSON-Body und `_type`-Property**, strukturell identisch zum `JobMessage`-POJO aus [Case3_ClientApp](../Case3_ClientApp/). Bestätigt durch die Case-3-Implementierung — das ist nicht mehr Wahl, sondern Kompatibilitäts­pflicht, falls die bestehende Client-App ohne Anpassung weiter konsumieren soll.
- A6 Das Enum-Feld `type` wird mit der exakten Schreibweise aus dem Case-3-POJO (`Maintanence` inkl. Typo, `Repair`) publiziert. **Offen** — Schreibweise im WSDL/XSD ist noch zu verifizieren; bei Abweichung im Transform Message normalisieren.

### Abgrenzungen

- Teil 3 wird **nur konzeptionell** behandelt, nicht implementiert (expliziter Case-Hinweis).
- Authentifizierung, Verschlüsselung (WS-Security, TLS) sind nicht Thema des Cases.
- Fehlerbehandlung, Retry, Dead-Letter-Queues sind für den Durchstich nicht gefordert, werden aber im Konzept kurz erwähnt.
- Persistente Verarbeitungshistorie / Audit ist nicht gefordert.

---

## 3. Pragmatische Analyse für den Schulrahmen

**Der Case zielt auf Lernziele, nicht auf Produktionscode.** Die zentralen Lernziele sind:

1. Verständnis von ESB-Konzepten und -Nutzung.
2. Verständnis von Web-Services / WSDLs.
3. Zusammenspiel von Service- und Message-orientierten Ansätzen im ESB.

Daraus folgt für die Umsetzung:

- **Teil 1 ist der sinnvollste Durchstich**, weil er beide Welten (SOAP-Service *und* JMS-Queue) im ESB zusammenführt und damit genau das Lernziel 3 adressiert. Teil 2 allein (Choice Router) deckt nur eine einzelne ESB-Primitive ab.
- **Alle benötigten Mule-Bausteine sind in `Hinweise_umsetzung.pdf` vorgemerkt**: Scheduler, Service Consumer, Transform Message, For Each, JMS Publish, Choice Router. Die Lösung lässt sich 1:1 aus diesen Komponenten zusammenstecken — kein Eigenbau nötig.
- **Kein Overengineering**: weder Registry, Orchestrator, BPEL, Security-Layer noch UDDI sind für die Aufgabe erforderlich. Sie sind im ESB-Paper als *extended*-Features erwähnt und können bestenfalls als Einordnung im Teil-3-Konzept genannt werden.
- **Teil 2 lässt sich kostengünstig mitnehmen**, indem der Choice Router direkt in denselben Mule-Flow eingeschoben wird. Dann deckt ein einziger Flow beide Pflichtteile ab. Das ist die ökonomische Wahl, sofern die Zeit reicht.
- **Teil 3 wird konzeptuell über zwei klassische ESB-Ideen gelöst**: (a) Service-Virtualisierung / Proxy-Endpoints im ESB, (b) optional eine zentrale Service-Registry. Beides wird in den bereitgestellten Quellen direkt adressiert.

---

## 4. Architekturvorschlag (minimal, sauber)

### 4.1 Gesamtbild

```
┌──────────────────────┐  SOAP/HTTP   ┌─────────────────────────────┐   JMS    ┌──────────────────────┐
│ CallCenter           │◄─────────────│ Mule ESB Flow               │─────────►│ ActiveMQ             │
│ 192.168.111.9:9090   │  getNewJobs  │ (Anypoint Studio, lokal)    │ publish  │ 192.168.111.6        │
│ (SOAP-Service)       │              │                             │          │ queue:               │
└──────────────────────┘              └─────────────────────────────┘          │ group4.jobs.new     │
                                        Scheduler → Service Consumer →         │ [+ .urgent]          │
                                        Transform → For Each →                 └──────────┬───────────┘
                                        (Choice Router) → JMS Publish                     │
                                                                                          ▼
                                                                               ┌──────────────────────┐
                                                                               │ JobAssigner          │
                                                                               │ 192.168.111.8        │
                                                                               │ (aus Case 3)         │
                                                                               └──────────────────────┘
Alle Pfeile durchqueren den WireGuard-Tunnel ins Netz 192.168.111.0/24.
```

### 4.2 Mule-Flow für Teil 1 (+ Teil 2)

Ein einziger Flow, sequenziell:

1. **Scheduler** — löst periodisch aus (z. B. alle 60 s, Wert offen).
2. **Service Consumer (WSDL)** — ruft `getNewJobs` über die registrierte WSDL-URL auf. Der Consumer wird mit der WSDL aus `http://192.168.111.9:9090/service/cc?WSDL` konfiguriert; Mule generiert Datentypen automatisch.
3. **Transform Message** — wandelt die SOAP-Response in das Case-3-kompatible JSON um (DataWeave, `output application/json`). Zielstruktur entspricht `JobMessage` aus [JobMessage.java](../Case3_ClientApp/src/main/java/ch/fhnw/digi/mockups/case3/JobMessage.java). `type`-Wert auf Case-3-Schreibweise normalisieren, falls nötig.
4. **For Each** — iteriert über die im Response enthaltene Job-Liste (laut Beispiel 2 Jobs pro Aufruf).
5. **Choice Router** (Teil 2) — routet pro Job:
   - `type == 'Repair'` → Queue `group4.jobs.urgent` *(Annahme A3)*
   - sonst → Queue `group4.jobs.new`
6. **JMS Publish** — veröffentlicht die Nachricht auf der gewählten Queue. Connection Properties: `brokerUrl=tcp://192.168.111.6:61616`, `username=group4`, `password=Password4`. MessageType = `TEXT` (JSON-Body), optional `_type`-Property setzen, falls der Case-3-Client ohne Anpassung weiterkonsumieren soll.

### 4.3 Konfiguration (Global Elements)

- **Web Service Consumer Config**: WSDL-URL `http://192.168.111.9:9090/service/cc?WSDL`, Service/Port aus der WSDL generiert.
- **JMS Config**: ActiveMQ Connection Factory, `brokerURL=tcp://192.168.111.6:61616`, `username=group4`, `password=Password4` (1:1 aus [Case3_ClientApp/src/main/resources/application.properties](../Case3_ClientApp/src/main/resources/application.properties)).
- **HTTP Listener**: nicht nötig (Flow ist timer-getrieben, kein Eingang von aussen).
- Credentials möglichst in Mule-Properties-Datei statt im Flow-XML.

### 4.4 Konzept für Teil 3 — Stabilität von Service-Adressen

**Ziel**: Umzug eines Services darf keine Änderung an Prozessen oder External Service Tasks erzwingen.

**Zwei komplementäre Bausteine, minimal gehalten:**

1. **Service-Virtualisierung im ESB (Pflichtteil des Konzepts)** — Der ESB stellt einen stabilen, fachlich benannten Endpoint bereit (z. B. `/esb/services/callcenter/getNewJobs`). Prozesse und External Service Tasks adressieren ausschliesslich diesen ESB-Endpoint. Der reale Ziel-Endpoint wird im ESB konfiguriert. Zieht der Service um, wird nur die ESB-Konfiguration angepasst — die Prozesse bleiben unverändert. Das entspricht dem in der Case-Literatur verlinkten Mulesoft-Konzept („Service Virtualization") und den im ESB-Paper beschriebenen Kernaufgaben *Routing* und *Invocation*.
2. **Zentrale Service-Konfiguration (optional, nur skizzieren)** — Für grössere Setups kann eine Service-Registry (z. B. UDDI-ähnlich oder ein einfaches Key/Value-Konfigurationsverzeichnis) ergänzt werden. Für die Accolaia AG bei derzeitiger Prozessanzahl **nicht notwendig**; ESB-Endpoint-Abstraktion reicht.

**Begründung gegen Overengineering**: Eine vollwertige Registry mit dynamischer Discovery ist für das Szenario überdimensioniert. Der Case spricht explizit von einer „kleinen Anzahl an Prozessen" mit Sorgen vor zukünftigem Wachstum — die ESB-Abstraktion löst genau dieses Problem, ohne neue Infrastruktur.

---

## 5. Grobe Umsetzungsplanung

| Schritt | Inhalt | Artefakt / Ergebnis |
| --- | --- | --- |
| 1 | VPN (WireGuard) mit `../group_10.conf` aktivieren, Erreichbarkeit prüfen: SOAP via SoapUI (`?WSDL`, `?xsd=1`), ActiveMQ via Webkonsole `http://192.168.111.6:8161`. | Beide Endpoints erreichbar. |
| 2 | Anypoint Studio 7.17.0 installieren, neues Mule-Projekt `case4-callcenter-integration` anlegen. | Leeres Projekt. |
| 3 | WSDL importieren, Web Service Consumer Config anlegen, `getNewJobs` testweise aufrufen. Dabei Schreibweise `Maintenance` vs. `Maintanence` aus XSD verifizieren (OP6). | SOAP-Consumer ruft Service erfolgreich ab; Typo-Status geklärt. |
| 4 | JMS-Connector konfigurieren (`tcp://192.168.111.6:61616`, `group4`/`Password4`). Testnachricht manuell auf `group4.jobs.new` publizieren und z.B. mit `activemq-admin` oder einem JMS-Client gegenlesen. | Nachricht in ActiveMQ-Konsole sichtbar. |
| 5 | DataWeave-Transformation schreiben, die SOAP-Response auf Case-3-JSON mappt (identisch zu [JobMessage.java](../Case3_ClientApp/src/main/java/ch/fhnw/digi/mockups/case3/JobMessage.java)). Unit-Test in Anypoint Studio mit statischem XML-Input. | Mapping korrekt. |
| 6 | Flow zusammenstecken: Scheduler → Consumer → Transform → For Each → JMS Publish. Mit aktivem Case-3-Client verifizieren: Jobs erscheinen in dessen UI (ggf. Client auf Queue umstellen, siehe OP7). | End-to-End-Durchstich funktioniert. |
| 7 | Teil 2: Choice Router einbauen, zweite Queue `group4.jobs.urgent` ergänzen, Routing-Kriterium `type == 'Repair'` dokumentieren. | Beide Queues erhalten gemäss Kriterium Nachrichten. |
| 8 | Teil 3 als Konzeptkapitel ausarbeiten (Service-Virtualisierung + optionale Registry, inkl. Begründung). | Abschnitt in der Lösungsdokumentation. |
| 9 | Lernfragen beantworten (ESB = Produkt oder Konzept?, SOA ↔ MOM im ESB, zielführender ESB-Einsatz). | Dokumentation. |
| 10 | Phase-1-Protokoll finalisieren (durch Schriftführer). | Protokoll im Repo. |
| 11 | Moderator lädt Artefakt-Paket (Lernfragen, Konzept, Umsetzung, Protokoll) auf Moodle hoch. | Abgabe erfolgt. |
| 12 | Nur falls Case 4 uns als Präsentations- oder Verteidigungs-Case zugewiesen wird: Videopräsentation (15–25 Min) bzw. Verteidigungsvorbereitung (jede Person muss die gesamte Lösung verteidigen können). | Video / Bereitschaft. |

---

## 6. Offene Punkte (zusammengefasst)

- OP1 **geklärt durch Case 3**: Nachrichtenformat = JMS TextMessage, JSON-Body passend zum `JobMessage`-POJO, `_type`-Property für den Case-3-Jackson-Converter.
- OP2 **weitgehend geklärt durch Case 3**: Dringlichkeits-Kriterium ist `type` (`Repair` = dringend, `Maintanence` = routine). Nur zu bestätigen, dass wir beide Queues gewünscht haben — oder ob `Repair` weiterhin „out of scope" bleibt.
- OP3 Queue-Naming für „urgent" (Annahme: `group4.jobs.urgent`) — ggf. mit Dozent abstimmen.
- OP4 Polling-Intervall — frei wählbar, Default 60 s.
- OP5 Idempotenz: unklar, ob der Service denselben Job mehrfach liefern kann.
- OP6 Schreibweise von `type` im WSDL/XSD: `Maintenance` (korrekt) oder `Maintanence` (Case-3-Typo)? → bei WSDL-Import direkt verifizieren und im Transform Message ggf. auf Case-3-Schreibweise mappen.
- OP7 Soll die alte Case-3-Client-App unverändert weiterlaufen? Falls ja, muss sie zusätzlich zur Queue `group4.jobs.new` (Point-to-Point, `setPubSubDomain(false)`) gewechselt werden — aktuell hört sie auf das Topic `dispo.jobs.new`.
