# Case 4 — Enterprise Service Bus: Analyse und Lösungskonzept

Modul: FHNW Software Architecture, FS26 (Marc Schaaf, marc.schaaf@fhnw.ch)
Gruppe: **Gruppe 4** — Loris Trifoglio (Moderator), Roberto Panizza (Schriftführer), Piracintha Alfred.
(Der Dateiname `../group_10.conf` ist irreführend — er bezeichnet nur die VPN-Config-Nummer, nicht die Gruppe.)
Grundlagen Case 4: [case_4.pdf](case_4.pdf), [Hinweise_umsetzung.pdf](Hinweise_umsetzung.pdf), [Hinweise_Umsetzung.md](Hinweise_Umsetzung.md), [Enterprise_Service_Bus_An_overview.pdf](Enterprise_Service_Bus_An_overview.pdf)
Modulweite Grundlagen: [../Modulübersicht.pdf](../Modulübersicht.pdf), [../System Overview.png](../System%20Overview.png)
Case-3-Grundlagen (Vorgänger und fachliche Basis von Case 4): [../case3/case3-3.pdf](../case3/case3-3.pdf), [../Case3_Abgabe/EIP_Loesungskonzept.drawio.png](../Case3_Abgabe/EIP_Loesungskonzept.drawio.png), [../Case3_ClientApp/](../Case3_ClientApp/) (Spring-Boot-Client mit JMS + Jackson)

---

## 0. Entscheidungen (finalisiert nach Case-3-Rückblick)

| # | Frage | Entscheidung | Begründung |
| --- | --- | --- | --- |
| E1 | Routing-Kriterium Teil 2 | `type == Repair` → `group4.jobs.urgent` (Dispositionsabteilung), `type == Maintanence` → `group4.jobs.new` (Aussendienst) | Case 3 Kap. 1 schreibt genau diese Trennung vor (Repair = dringend, direkt koordiniert; Maintanence = Routine, publiziert). Der ESB automatisiert hier die manuelle Weiche aus Case 3. |
| E2 | Umfang des Durchstichs | **Beide Teile** (Teil 1 + Teil 2) in einem einzigen Mule-Flow | Teil 2 ist mit Choice Router geringer Zusatzaufwand und vervollständigt das Case-3-Konzept. |
| E3 | Case-3-Client-Anpassung | Client auf Queue `group4.jobs.new` umstellen (Destination + `setPubSubDomain(false)`) | Minimaler Eingriff, dafür vollständige End-to-End-Demo im Video. |
| E4 | Ablage im Repo | Neuer Top-Level-Ordner `../Case4_MuleApp/` (parallel zu `../Case3_ClientApp/`) | Analogie-Prinzip mit Case 3; trennt Mule-Artefakte sauber vom Konzept-Ordner `case4/`. |
| E5 | Rollen | Moderator: Loris Trifoglio — Schriftführer: Roberto Panizza — Mitglied: Piracintha Alfred | Gruppenentscheid. |

---

## 1. Anforderungen

### 1.1 Funktionale Anforderungen

**Teil 1 — Anbindung des Callcenters (Umsetzung gefordert)**

- F1.1 Aufträge des Callcenters automatisch aus dessen Verwaltungssystem abrufen, statt sie manuell an die Queue zu übergeben.
- F1.2 Aufruf des Callcenter-Web-Service (HTTP+SOAP) über die Methode `getNewJobs`. Der Service liefert pro Aufruf beispielhaft zwei neue Jobs.
- F1.3 Endpunkt: `http://192.168.111.9:9090/service/cc` (WSDL unter `?WSDL`, XSD unter `?xsd=1`). Zugriff nur via WireGuard-VPN.
- F1.4 Die abgerufenen Jobs müssen an den bestehenden ActiveMQ-Broker aus Case 3 weitergegeben werden.
- F1.5 Ziel-Destination ist eine gruppenspezifische Queue `groupX.jobs.new` (nicht mehr das Topic `dispo.jobs.new` aus Case 3).
- F1.6 Jede empfangene Job-Nachricht enthält mindestens: `customernumber`, `jobnumber`, `description`, `region`, `scheduledDateTime`, `type` (Werte serverseitig: `Maintanence`, `Repair` — bewusst so geschrieben, einheitlich in WSDL/XSD und Case-3-Client). Diese Felder müssen im weitergereichten Payload erhalten bleiben.
- F1.7 Der Abruf erfolgt periodisch (Polling) — der Service bietet keine Push-Schnittstelle.

**Teil 2 — Message Routing (laut E2 mit umgesetzt)**

- F2.1 Dringende Aufträge (`Repair`) sollen von Routineaufträgen (`Maintanence`) getrennt und an unterschiedliche Ziele geroutet werden (konzeptionell in Case 3 vorgesehen, dort manuell erledigt).
- F2.2 Entscheidung inhaltsbasiert (Content-Based Routing) über den Mulesoft Choice Router.
- F2.3 Kriterium für „dringend" vs. „routine": Feld `type`. Belegt durch [case3-3.pdf](../case3/case3-3.pdf) Kap. 1 — „Dringender Reparaturauftrag: Die Dispositionsabteilung koordiniert den Einsatz direkt. […] Routineauftrag: Die Dispositionsabteilung publiziert den Auftrag an die Aussendienstmitarbeitenden."
- F2.4 Routing-Ziele: `Repair` → Queue `group4.jobs.urgent` (Dispositionsabteilung, direkte Koordination bleibt in Case 3/4 out of scope), `Maintanence` → Queue `group4.jobs.new` (Aussendienst).

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
- **Enum-Werte für `type`**: `Maintanence` und `Repair`. Diese Schreibweise ist serverseitig (Callcenter-Service, WSDL/XSD) und clientseitig (Case-3-POJO) identisch — also die kanonische Form im System, keine Normalisierung nötig.
- **EIP-Pattern in Case 3** bereits konzipiert:
  - Content-Based Router (Auftragstyp): `Repair` → „Direkte Koordination (out of scope)"; `Maintanence` → Topic `dispo.jobs.new`.
  - Topic → Durable Subscribers → Message Filter (Region) → Client Apps.
  - Job-Anforderung über Queue `dispo.jobs.requestAssignment` (Competing Consumers → Disposition).
  - Zuweisungs-Rückkanal über Topic `dispo.jobs.assignments`.
- Die **manuelle Schnittstelle** war genau der Schritt `Callcenter → Topic dispo.jobs.new` — also der Punkt, den Case 4 Teil 1 automatisiert.
- **Wörtliche Prozessvorgabe aus [case3-3.pdf](../case3/case3-3.pdf) Kap. 1**: „*Dringender Reparaturauftrag: Die Dispositionsabteilung koordiniert den Einsatz direkt.*" / „*Routineauftrag: Die Dispositionsabteilung publiziert den Auftrag an die Aussendienstmitarbeitenden.*" — das ist 1:1 die Logik, die der Choice Router in Teil 2 automatisiert.

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

- A1 Der Callcenter-Service ist zustandslos bezüglich bereits abgerufener Jobs — jeder `getNewJobs`-Aufruf liefert „die nächsten zwei Jobs". Entscheidung: keine clientseitige Deduplizierung (einfachste Lösung); mögliche Duplikate sind für den Durchstich unkritisch.
- A2 Dringlichkeits-Feld ist `type` (siehe F2.3, durch [case3-3.pdf](../case3/case3-3.pdf) Kap. 1 belegt, nicht mehr Annahme).
- A3 Queue-Naming-Konvention: `group4.jobs.new` (vorgegeben) und `group4.jobs.urgent` (analoge Konvention für den dringenden Zweig — mit Coach bestätigen, falls Unsicherheit).
- A4 Polling-Frequenz: 60 s als Default. Pragmatisch für Dev-Demo ausreichend.
- A5 Nachrichtenformat: **JMS TextMessage mit JSON-Body und `_type`-Property**, strukturell identisch zum `JobMessage`-POJO aus [Case3_ClientApp](../Case3_ClientApp/). Belegt durch Case-3-Code — Kompatibilitäts­pflicht, da der bestehende Client laut E3 unverändert das Format erwartet.
- A6 Das Enum-Feld `type` wird mit der kanonischen Schreibweise `Maintanence` / `Repair` durchgereicht — serverseitig und clientseitig einheitlich, keine Normalisierung nötig.

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

Daraus folgt für die Umsetzung (entsprechend Entscheidungen E1–E5):

- **Teil 1 und Teil 2 werden gemeinsam** in einem einzigen Mule-Flow umgesetzt (E2). Teil 1 allein adressiert zwar Lernziel 3 (SOA ↔ MOM), Teil 2 ist aber mit einem einzigen Choice Router zusätzlich machbar und vervollständigt das Case-3-Konzept ohne Mehraufwand.
- **Alle benötigten Mule-Bausteine sind in `Hinweise_umsetzung.pdf` vorgemerkt**: Scheduler, Service Consumer, Transform Message, For Each, Choice Router, JMS Publish. Die Lösung lässt sich 1:1 aus diesen Komponenten zusammenstecken — kein Eigenbau nötig.
- **Kein Overengineering**: weder Registry, Orchestrator, BPEL, Security-Layer noch UDDI sind für die Aufgabe erforderlich. Sie sind im ESB-Paper als *extended*-Features erwähnt und werden höchstens im Teil-3-Konzept eingeordnet.
- **Case-3-Client (Spring-Boot) wird minimal angepasst**, um den Durchstich end-to-end demonstrieren zu können (E3): Destination wechselt von Topic `dispo.jobs.new` auf Queue `group4.jobs.new`, Container-Factory von `PubSubDomain=true` auf `false`. Formatkompatibilität ist durch den Jackson-Converter automatisch gegeben, solange der Mule-Flow JSON publiziert.
- **Teil 3 wird konzeptuell über zwei klassische ESB-Ideen gelöst**: (a) Service-Virtualisierung / Proxy-Endpoints im ESB, (b) optional eine zentrale Service-Registry. Beides ist in den bereitgestellten Quellen direkt adressiert.

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
3. **Transform Message** — wandelt die SOAP-Response in das Case-3-kompatible JSON um (DataWeave, `output application/json`). Zielstruktur entspricht `JobMessage` aus [JobMessage.java](../Case3_ClientApp/src/main/java/ch/fhnw/digi/mockups/case3/JobMessage.java). `type` wird 1:1 durchgereicht (`Maintanence` / `Repair`).
4. **For Each** — iteriert über die im Response enthaltene Job-Liste (laut Beispiel 2 Jobs pro Aufruf).
5. **Choice Router** (Teil 2) — routet pro Job:
   - `type == 'Repair'` → Queue `group4.jobs.urgent` (dringend, Dispositionsabteilung)
   - `type == 'Maintanence'` → Queue `group4.jobs.new` (Routine, Aussendienst)
6. **JMS Publish** — veröffentlicht die Nachricht auf der gewählten Queue. Connection: `brokerUrl=tcp://192.168.111.6:61616`, `username=group4`, `password=Password4`. MessageType = `TEXT` (JSON-Body), `_type`-Property setzen (`ch.fhnw.digi.mockups.case3.JobMessage`), damit der Case-3-Jackson-Converter deserialisieren kann.

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

Verantwortlichkeits-Kürzel: **L**=Loris, **R**=Roberto, **P**=Piracintha. Die eigentliche Arbeit wird paarweise/gemeinsam erledigt; die Kürzel markieren Lead.

| # | Inhalt | Lead | Artefakt / Ergebnis |
| --- | --- | --- | --- |
| 1 | VPN (WireGuard) mit `../group_10.conf` aktivieren, Erreichbarkeit prüfen: SOAP via SoapUI (`?WSDL`, `?xsd=1`), ActiveMQ via Webkonsole `http://192.168.111.6:8161`. | alle | Beide Endpoints erreichbar. |
| 2 | Anypoint Studio 7.17.0 installieren, Mule-Projekt im neuen Repo-Ordner `../Case4_MuleApp/` anlegen. | R | Leeres Projekt commitet. |
| 3 | WSDL importieren, Web Service Consumer Config anlegen, `getNewJobs` testweise aufrufen. | R | SOAP-Consumer funktioniert. |
| 4 | JMS-Connector konfigurieren (`tcp://192.168.111.6:61616`, `group4`/`Password4`). Testnachricht manuell auf `group4.jobs.new` publizieren und über ActiveMQ-Webkonsole gegenlesen. | P | Nachricht sichtbar. |
| 5 | DataWeave-Transformation SOAP → Case-3-JSON (`JobMessage`-Struktur, `_type`-Property). Test mit statischem XML-Input in Anypoint Studio. | R | Mapping korrekt. |
| 6 | Flow zusammenbauen: Scheduler → Service Consumer → Transform → For Each → Choice Router → JMS Publish (beide Queues). | R+P | Flow end-to-end lauffähig. |
| 7 | Case-3-Client anpassen (E3): `MessageReceiver.receiveNewJob` auf Destination `group4.jobs.new` umstellen, neue `DefaultJmsListenerContainerFactory` mit `setPubSubDomain(false)` für Queues. Ggf. zweiter `@JmsListener` für `group4.jobs.urgent` zur Demo. | R | End-to-End-Demo sichtbar: Jobs erscheinen in UI. |
| 8 | Teil 3 konzeptuell ausarbeiten (Service-Virtualisierung + optionale Registry). | L | Konzept-Abschnitt. |
| 9 | Lernfragen beantworten: (a) ESB = Produkt oder Konzept? (b) Zielführender ESB-Einsatz? (c) SOA ↔ MOM im ESB? Jede Person schreibt eigenen Learning Report. | alle einzeln | Learning Reports + gruppenweite Antworten. |
| 10 | Phase-1-Protokoll aus Gruppensitzung finalisieren (Vorlage auf Moodle). | R (Schriftführer) | Protokoll im Repo. |
| 11 | Abgabepaket schnüren: Protokoll, Lernfragen-Antworten, dieses Konzept-Dokument, `Case4_MuleApp/` (Mule-Export), angepasster Case-3-Client. Upload auf Moodle. | L (Moderator) | Abgabe erfolgt. |
| 12 | Falls Case 4 als Präsentations-/Verteidigungs-Case zugewiesen: Videopräsentation (15–25 Min, alle drei gleich verteilt) und Verteidigungs-Dry-Run — jede Person muss gesamte Lösung erklären können. | alle | Video / Bereitschaft. |

---

## 6. Offene Punkte

Keine verbleibenden Punkte — alle Fragen sind durch Kapitel 0 und die Case-3-Unterlagen geschlossen.

Historisch bereinigt:
- Queue-Name `group4.jobs.urgent` als Konvention akzeptiert (analog zu `group4.jobs.new`).
- Idempotenz des Callcenter-Service: bewusst nicht adressiert — einfachste Lösung, keine clientseitige Deduplizierung. Mögliche Duplikate sind für den Durchstich unkritisch.
