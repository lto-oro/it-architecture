# Case 2 Infrastructure Overview

## Ausgangspunkt

Die fuer `case2` relevanten Laufzeitsysteme liegen nicht lokal auf `localhost`, sondern im Netzwerk `192.168.111.0/24`. Das geht aus dem vorhandenen `System Overview` hervor.

## Netzwerk und Host

- Host-System: Linux, Debian Bookworm
- VPN-Zugang ueber Wireguard
- Bridge-Netz: `br_sw_arch`
- Subnetz: `192.168.111.0/24`

## Fuer Case 1 und Case 2 relevante Systeme

Im Bereich `Case 1&2` sind laut System Overview die folgenden Komponenten vorhanden:

- `Camunda`
- `MySQL 1`
- `Shipping Service`

## Bekannte bzw. abgeleitete Adressen

Aus dem bestehenden `case1`-Code lassen sich folgende Endpunkte ablesen:

- Camunda Engine REST:
  - `http://192.168.111.3:8080/engine-rest`
- Shipping Service:
  - `http://192.168.111.5:8080/v1`

Diese Werte sind in [SpeditionController.java](C:/Users/Loris/Github/it-architecture/case1/src/main/java/com/group4/case1/SpeditionController.java) als Defaults hinterlegt.

Ergaenzend sind fuer die Zielumgebung nun die folgenden Zugangsdaten bekannt:

- Camunda
  - User: `group4`
  - Passwort: `PLVbIZynDiaW6K`
- Case 2 MySQL
  - Server IP: `192.168.111.4`
  - DB Name: `db_group4`
  - User: `group4`
  - Passwort: `ala3d3adqa3ed0x4`
- ActiveMQ
  - User: `group4`
  - Passwort: `fzX7YciZB4ZjvqQ`
- Case 6 MySQL DBMS
  - User: `vl_custmgmt`
  - Passwort: `d854hg23t48+f2z-fvtz8tb0b4v`

## Wichtige Konsequenz fuer Case 2

Die aktuelle `case2`-Implementierung ist inzwischen auf die echte Zielumgebung vorkonfiguriert. Fuer die Zielumgebung aus dem System Overview gilt:

- Camunda Engine ist im Netz `192.168.111.x`
- MySQL liegt ebenfalls im Netz `192.168.111.x`
- der Shipping Service liegt ebenfalls im Netz `192.168.111.x`

Das bedeutet:

- `CAMUNDA_ENGINE_REST_URL` sollte auf die Camunda-Instanz im `192.168.111.x`-Netz zeigen
- `CASE2_DB_URL` sollte auf `MySQL 1` im `192.168.111.x`-Netz zeigen
- der bestehende Shipping-Worker aus `case1` muss ebenfalls gegen diese Zielumgebung konfiguriert sein

## Offene Information

Das System Overview benennt die Komponenten, zeigt aber nicht fuer alle Systeme explizit die einzelnen IP-Adressen im Bildtext. Direkt aus dem vorhandenen Repository eindeutig belegt sind:

- Camunda auf `192.168.111.3`
- Shipping Service auf `192.168.111.5`

Die konkrete IP von `MySQL 1` ist jetzt bekannt:

- `MySQL 1`: `192.168.111.4`

## Empfehlung fuer die weitere Konfiguration von case2

Vor einem echten End-to-End-Test sollten die Werte in `case2` nicht auf `localhost`, sondern auf die Zielumgebung gesetzt werden, typischerweise ueber Umgebungsvariablen:

```powershell
$env:CAMUNDA_ENGINE_REST_URL="http://192.168.111.3:8080/engine-rest"
$env:CAMUNDA_USERNAME="group4"
$env:CAMUNDA_PASSWORD="PLVbIZynDiaW6K"
$env:CASE2_DB_URL="jdbc:mysql://192.168.111.4:3306/db_group4?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:CASE2_DB_USERNAME="group4"
$env:CASE2_DB_PASSWORD="ala3d3adqa3ed0x4"
```

## Bezug zu case2

Fuer `case2` sind damit die folgenden Infrastrukturannahmen massgeblich:

- Prozessdeployment erfolgt in Camunda 7
- BPMN greift auf External Tasks zu
- der neue Decision Worker verbindet sich zur Camunda Engine im `192.168.111.x`-Netz
- die Entscheidungsprotokollierung erfolgt gegen `MySQL 1`
- die bestehende Speditionsanbindung aus `case1` bleibt Teil des Gesamtprozesses
