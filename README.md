# NASA APOD – Java Wallpaper Downloader

Java-Port des PowerShell-Skripts  
[`Nasa_API_powershel_Picture_of_the_day`](https://github.com/marcdziersan/Nasa_API_powershel_Picture_of_the_day)

Statt PowerShell-Skript jetzt eine **javac-kompilierbare Schulungsanwendung**:

- Holt das **Astronomy Picture of the Day (APOD)** von der NASA-API.
- Speichert das Bild in einen Ordner auf deinem Desktop.
- Setzt unter Windows das Bild als **Desktop-Hintergrund**.
- Eignet sich als **Java-Lernprojekt** (HTTP, JSON, Files, ProcessBuilder).

---

## 1. Was diese Java-Version macht

Die Anwendung `NasaApodWallpaper.java`:

1. Ruft die NASA-APOD-API auf:
   - Endpoint: `https://api.nasa.gov/planetary/apod`
   - Parameter: `api_key=<DEIN_API_KEY>`

2. Prüft das Feld `media_type`:
   - Nur wenn `media_type == "image"` geht es weiter.
   - Bei Video oder anderen Typen: sauberer Exit.

3. Lädt das HD-Bild herunter:
   - Verwendet `hdurl` aus der API-Antwort.
   - Speichert in:
     ```text
     Desktop\NasaBilder\YYYY-MM-DD - <Titel>.jpg
     ```
   - Beispiel:
     ```text
     C:\Users\DeinName\Desktop\NasaBilder\2025-11-21 - Alnitak, Alnilam, Mintaka.jpg
     ```

4. Setzt unter Windows das Wallpaper:
   - Startet PowerShell im Hintergrund.
   - Setzt per Registry den Wert:
     ```powershell
     HKCU:\Control Panel\Desktop\wallpaper
     ```
   - Triggert mit `rundll32.exe user32.dll,UpdatePerUserSystemParameters` ein **Refresh** der Benutzer-Desktop-Einstellungen.

---

## 2. Voraussetzungen

### 2.1 Technisch

- **Java Development Kit (JDK) 11 oder höher**
  - wegen `java.net.http.HttpClient`
- Internetverbindung
- Betriebssystem:
  - Getestet: **Windows 10/11**
  - Auf anderen Systemen:
    - Download funktioniert,
    - Wallpaper-Setzen wird übersprungen.

### 2.2 NASA API-Key

Wie beim PowerShell-Projekt brauchst du einen eigenen Schlüssel:

1. Öffne: https://api.nasa.gov/
2. Formular ausfüllen (Name, E-Mail).
3. API-Key kopieren.

Diesen trägst du in der Java-Datei ein.

---

## 3. Projektstruktur

Beispielstruktur für dieses Projekt:

```text
NasaApodWallpaper/
├─ src/
│  └─ NasaApodWallpaper.java
└─ README.md
```

Lege die Datei `NasaApodWallpaper.java` in `src/` ab.

---

## 4. API-Key in Java eintragen

In der Datei `NasaApodWallpaper.java` gibt es oben die Konstante:

```java
// NASA-API-Key.
private static final String API_KEY = "QBGeRZB8bejGy035saAMem7n3bFReJPUpsGtOhr0";
```

Ersetze den Wert durch deinen eigenen:

```java
private static final String API_KEY = "DEIN_EIGENER_API_KEY_HIER";
```

> Hinweis: Für private Repos ist das okay.  
> Für öffentliche Repos besser über eine Umgebungsvariable lösen (kann als Übungsaufgabe eingebaut werden).

---

## 5. Kompilieren und Ausführen (Windows, PowerShell)

### 5.1 In den Projektordner wechseln

```powershell
cd "C:\GithubBackup\projects\NasaApodWallpaper"
```

Passe den Pfad an deine Struktur an.

### 5.2 Java-Datei kompilieren

```powershell
javac -d out src\NasaApodWallpaper.java
```

- `-d out` erzeugt den Ordner `out` (falls nicht vorhanden) und legt dort die `.class`-Dateien ab.

### 5.3 Programm starten (ohne JAR)

```powershell
java -cp out NasaApodWallpaper
```

Wenn alles klappt, siehst du in der Konsole u. a.:

- Zielordner,
- Titel und HD-URL,
- Speicherpfad,
- Meldung über den Wallpaper-Update-Befehl.

Auf deinem Desktop entsteht:

```text
C:\Users\<DeinName>\Desktop\NasaBilder\<Datum> - <Titel>.jpg
```

---

## 6. Optional: JAR bauen und direkt ausführbar machen

Wenn du das Projekt „rund“ als JAR haben möchtest:

### 6.1 Manifest-Datei anlegen

```powershell
cd "C:\GithubBackup\projects\NasaApodWallpaper"

"Main-Class: NasaApodWallpaper" | Out-File -FilePath manifest.txt -Encoding ASCII
```

Wichtig:
- `Main-Class:` gefolgt von einem Leerzeichen und dem Klassennamen.
- Zeilenumbruch am Ende (PowerShell macht den automatisch).

### 6.2 JAR erzeugen

```powershell
jar cfm NasaApodWallpaper.jar manifest.txt -C out .
```

Dadurch entsteht:

```text
NasaApodWallpaper.jar
```

im Projektverzeichnis.

### 6.3 JAR starten

```powershell
java -jar NasaApodWallpaper.jar
```

---

## 7. Automatische Ausführung via Aufgabenplanung (Task Scheduler)

Analog zum PowerShell-Skript kannst du auch die Java-Version täglich ausführen lassen.

### 7.1 Aufgabenplanung öffnen

**Variante A:**

- Windows-Taste drücken, „Aufgabenplanung“ eintippen, öffnen.

**Variante B:**

- `Win + R`
- `taskschd.msc` eingeben
- Enter → Aufgabenplanung startet.

### 7.2 Neue Aufgabe anlegen

1. Rechts: **Einfache Aufgabe erstellen…**
2. Name z. B.: `NASA APOD Java Wallpaper`
3. Trigger:
   - „Täglich“
   - Uhrzeit wählen (z. B. 08:00 Uhr morgens).
4. Aktion: „Programm starten“.

### 7.3 Aktion konfigurieren

#### Variante 1: JAR mit absolutem Pfad

- **Programm/Skript**:
  ```text
  java
  ```
- **Argumente hinzufügen**:
  ```text
  -jar "C:\Users\DeinName\Desktop\NasaApodWallpaper\NasaApodWallpaper.jar"
  ```
- **Starten in**:
  ```text
  C:\Users\DeinName\Desktop\NasaApodWallpaper
  ```

#### Variante 2: Benutzerunabhängig mit %HOMEDRIVE%%HOMEPATH%

Wenn du flexibler sein willst:

- **Programm/Skript**:
  ```text
  java
  ```
- **Argumente hinzufügen**:
  ```text
  -jar "%HOMEDRIVE%%HOMEPATH%\Desktop\NasaApodWallpaper\NasaApodWallpaper.jar"
  ```

Damit funktioniert es für jeden Benutzer, sofern die Struktur gleich ist.

---

## 8. Vergleich: PowerShell-Skript vs. Java-Anwendung

### 8.1 Ursprung: PowerShell-Version

Das ursprüngliche Repo enthält:

- `skript.ps1`  
  PowerShell-Skript, das:
  - die NASA-API anspricht,
  - das Bild herunterlädt,
  - den Desktop-Hintergrund direkt setzt,
  - sich gut über die Aufgabenplanung automatisieren lässt.

### 8.2 Java-Port (dieses Projekt)

`NasaApodWallpaper.java` demonstriert:

- HTTP in Java mit `HttpClient`.
- Einfaches JSON-Parsing für gezielte Felder (`media_type`, `title`, `hdurl`).
- Dateisystem-Operationen mit `java.nio.file`.
- Aufruf von PowerShell über `ProcessBuilder`.

Das eignet sich ideal als:

- **Schulungsprojekt** für Umschulung Fachinformatiker Anwendungsentwicklung,
- Beispiel für „Java spricht mit Windows“,
- Ausgangspunkt, um weitere Features zu üben:
  - Logging,
  - Fehlerbehandlung,
  - API-Key über Umgebungsvariable laden,
  - einfache GUI (Swing/JavaFX) o. ä.

---

## 9. Sicherheit & Hinweise

- Der NASA-API-Key sollte nicht in öffentlichen Repositories im Klartext stehen.
- API-Limits beachten: Die kostenlose Nutzung ist begrenzt.
- Das Wallpaper-Setzen über Registry und `rundll32` betrifft den **aktuellen Benutzer**:
  - HKCU = „HKEY_CURRENT_USER“.
- Falls das Hintergrundbild nicht sofort aktualisiert wird:
  - Ab- und wieder Anmelden,
  - Bildschirm sperren und wieder entsperren,
  - oder Windows-Einstellungen für Hintergrund prüfen.

---

## 10. Lizenz / Nutzung

- Ursprungsidee und PowerShell-Variante: siehe Original-Repository.
- Die Java-Portierung in diesem Projekt kann für:
  - Lernzwecke,
  - Demos,
  - interne Schulungen
  frei verwendet und erweitert werden.
