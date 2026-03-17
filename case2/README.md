# Case 2 Setup

## Enthaltene Artefakte

- `case2.bpmn`
- Spring-Boot-Projekt als Worker-App mit zwei External-Task-Workern
- Drools-Regeln unter `src/main/resources/rules`
- MySQL-Schema für die Entscheidungsprotokollierung

## Ablauf mit Camunda 7

1. `case2/case2.bpmn` im Camunda Modeler öffnen.
2. BPMN auf die Camunda-7-Instanz unter `192.168.111.3:8080` deployen.
3. Sicherstellen, dass eine MySQL-Datenbank für `case2` erreichbar ist.
4. `case2` als Spring-Boot-App starten.
5. In Camunda einen neuen Prozess vom Typ `group4_case2` starten.

## Zielumgebung

- Camunda REST: `http://192.168.111.3:8080/engine-rest`
- Camunda Credentials: `group4 / PLVbIZynDiaW6K`
- Camunda Topic für die Entscheidungslogik: `group4_decision`
- Camunda Topic für den Speditionsaufruf: `group4_rest`
- Datenbank: `jdbc:mysql://192.168.111.4:3306/db_group4`
- DB Credentials: `group4 / ala3d3adqa3ed0x4`
- Speditions-API: `http://192.168.111.5:8080/v1/consignment/request`

## Override per Umgebungsvariablen

- `CAMUNDA_ENGINE_REST_URL`
- `CAMUNDA_USERNAME`
- `CAMUNDA_PASSWORD`
- `CASE2_CAMUNDA_TOPIC`
- `CASE2_CAMUNDA_SHIPPING_TOPIC`
- `SPEDITION_BASE_URL`
- `SPEDITION_REQUEST_PATH`
- `CASE2_DB_URL`
- `CASE2_DB_USERNAME`
- `CASE2_DB_PASSWORD`

## Hinweise

- Die Spring-Boot-App in `case2` startet jetzt beide External-Task-Worker (`group4_decision` und `group4_rest`).
- Die Decision-Komponente automatisiert nur die Fälle, für die in der vorhandenen Landschaft ein realer Folgepfad existiert. Alle anderen Regeln werden sauber erkannt und in einen manuellen Prozesspfad übergeben.
