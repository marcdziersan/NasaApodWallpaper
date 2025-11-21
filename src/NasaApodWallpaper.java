import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * NasaApodWallpaper
 * -----------------
 * Kleines Java-Konsolenprogramm, das:
 * - das "Astronomy Picture of the Day" (APOD) von der NASA-API abruft,
 * - prüft, ob es ein Bild ist,
 * - das Bild in einen Ordner "NasaBilder" auf dem Desktop speichert,
 * - und unter Windows versucht, dieses Bild als Desktop-Hintergrund zu setzen.
 *
 * Technischer Fokus:
 * - HTTP-Aufruf mit HttpClient (JDK 11+),
 * - einfacher JSON-Parsing-Ansatz für wenige Felder,
 * - Dateizugriffe mit java.nio.file,
 * - Aufruf eines externen PowerShell-Befehls über ProcessBuilder.
 */
public class NasaApodWallpaper {

    /**
     * NASA-API-Key.
     * Hinweis: In echten Projekten sollte der Key nicht im Quelltext stehen,
     * sondern z. B. aus einer Umgebungsvariable gelesen werden.
     */
    private static final String API_KEY = "DEIN_API_KEY";

    /**
     * URL der NASA-APOD-API mit eingebettetem API-Key.
     */
    private static final String API_URL =
            "https://api.nasa.gov/planetary/apod?api_key=" + API_KEY;

    /**
     * Datumsformat, das im Dateinamen des heruntergeladenen Bildes verwendet wird.
     * Beispiel: 2025-11-21
     */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Einstiegspunkt des Programms.
     *
     * Ablauf:
     * 1. Zielordner auf dem Desktop vorbereiten.
     * 2. APOD-JSON von der NASA-API holen.
     * 3. Prüfen, ob media_type == "image".
     * 4. Titel und HD-URL aus dem JSON extrahieren.
     * 5. Bild unter "Desktop\NasaBilder\<Datum> - <Titel>.jpg" speichern.
     * 6. Unter Windows versuchen, das Bild als Wallpaper zu setzen.
     */
    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println(" NASA APOD Downloader (Java-Version)");
        System.out.println("==============================================");

        try {
            // Zielordner anlegen oder wiederverwenden
            Path targetDir = prepareTargetDirectory();
            System.out.println("Zielordner: " + targetDir.toAbsolutePath());

            // NASA-APOD-JSON abrufen
            String json = fetchApodJson();
            System.out.println("API-Antwort erhalten.");

            // media_type prüfen (image, video, etc.)
            String mediaType = extractJsonField(json, "media_type");
            if (!"image".equalsIgnoreCase(mediaType)) {
                System.out.println("Heutiger APOD ist kein Bild (media_type=" + mediaType + ").");
                System.out.println("Programm wird beendet.");
                return;
            }

            // Titel und HD-URL aus JSON holen
            String title = extractJsonField(json, "title");
            String hdUrl = extractJsonField(json, "hdurl");

            // Dateinamen aus Datum + aufbereitetem Titel zusammenbauen
            String safeTitle = sanitizeTitle(title);
            String datePart = LocalDate.now().format(DATE_FORMAT);
            String fileName = datePart + " - " + safeTitle + ".jpg";

            // Zielpfad für das Bild
            Path imagePath = targetDir.resolve(fileName);

            System.out.println("Titel  : " + title);
            System.out.println("HD-URL: " + hdUrl);
            System.out.println("Speichere nach: " + imagePath.toAbsolutePath());

            // Bild herunterladen
            downloadFile(hdUrl, imagePath);
            System.out.println("Download abgeschlossen.");

            // Unter Windows: Wallpaper setzen
            if (isWindows()) {
                System.out.println("Versuche, das Bild als Wallpaper zu setzen...");
                setWindowsWallpaper(imagePath);
                System.out.println("Wallpaper-Befehl an PowerShell übergeben.");
            } else {
                System.out.println("Kein Windows-System erkannt – Wallpaper wird nicht gesetzt.");
            }

            System.out.println("Fertig.");

        } catch (Exception e) {
            // Zentrale Fehlerbehandlung – alle nicht speziell gefangenen Fehler landen hier
            System.err.println("Fehler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ermittelt den Zielordner "Desktop\NasaBilder" im Benutzerprofil
     * und legt ihn bei Bedarf an.
     *
     * @return Pfad zum Zielordner.
     * @throws IOException wenn der Ordner nicht erstellt werden kann.
     */
    private static Path prepareTargetDirectory() throws IOException {
        // Benutzerverzeichnis, z. B. C:\Users\marcu
        String userHome = System.getProperty("user.home");

        // Unterordner Desktop\NasaBilder
        Path targetDir = Paths.get(userHome, "Desktop", "NasaBilder");

        // Falls der Ordner noch nicht existiert, anlegen (inkl. Zwischenordnern)
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        return targetDir;
    }

    /**
     * Ruft die NASA-APOD-API per HTTP GET auf und gibt den JSON-Body als String zurück.
     *
     * @return JSON-Antwort der NASA-APOD-API.
     * @throws IOException          bei I/O- und Netzwerkfehlern.
     * @throws InterruptedException wenn der HTTP-Aufruf unterbrochen wird.
     */
    private static String fetchApodJson() throws IOException, InterruptedException {
        // HttpClient zum Versenden von HTTP-Anfragen
        HttpClient client = HttpClient.newHttpClient();

        // GET-Request zur APOD-URL aufbauen
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .GET()
                .build();

        // Request senden und Antwort als String empfangen
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Nur Statuscode 200 (OK) akzeptieren
        if (response.statusCode() != 200) {
            throw new IOException("NASA-API antwortete mit HTTP-Status: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Liest aus einem einfachen JSON-String den Wert eines String-Feldes.
     *
     * Beispiel:
     *   JSON enthält: "title":"Alnitak"
     *   extractJsonField(json, "title") liefert "Alnitak"
     *
     * Diese Methode ist bewusst sehr einfach gehalten und setzt voraus,
     * dass das Feld in der Form "name":"wert" vorkommt.
     *
     * @param json      JSON-Text.
     * @param fieldName Feldname, z. B. "media_type", "title", "hdurl".
     * @return Wert des Feldes als String.
     * @throws IOException wenn das Feld nicht gefunden wird oder das Format unerwartet ist.
     */
    private static String extractJsonField(String json, String fieldName) throws IOException {
        // Nach dem Feldnamen im JSON suchen, z. B. "title"
        String key = "\"" + fieldName + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            throw new IOException("Feld \"" + fieldName + "\" nicht im JSON gefunden.");
        }

        // Doppelpunkt nach dem Feldnamen finden
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            throw new IOException("Doppelpunkt nach Feld \"" + fieldName + "\" nicht gefunden.");
        }

        // Erstes Anführungszeichen nach dem Doppelpunkt
        int firstQuote = json.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) {
            throw new IOException("Öffnendes Anführungszeichen für Feld \"" + fieldName + "\" nicht gefunden.");
        }

        // Zweites Anführungszeichen nach dem ersten
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            throw new IOException("Schließendes Anführungszeichen für Feld \"" + fieldName + "\" nicht gefunden.");
        }

        // Inhalt zwischen den Anführungszeichen zurückgeben
        return json.substring(firstQuote + 1, secondQuote);
    }

    /**
     * Bereinigt einen Titel, damit er als Dateiname unter Windows zulässig ist.
     * Entfernt alle Zeichen:
     *  \ / : * ? " < > |
     *
     * @param title ursprünglicher Titel.
     * @return bereinigter Titel, ohne unzulässige Zeichen und ohne führende / abschließende Leerzeichen.
     */
    private static String sanitizeTitle(String title) {
        if (title == null) {
            return "Unbenannt";
        }
        // Verbotene Zeichen in Windows-Dateinamen durch nichts ersetzen
        return title.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    /**
     * Lädt eine Datei von der angegebenen URL herunter und speichert sie unter dem angegebenen Pfad.
     *
     * @param url    URL der Datei (z. B. Bild-URL der NASA).
     * @param target Pfad zur Zieldatei.
     * @throws IOException          bei Netzwerk- oder Dateifehlern.
     * @throws InterruptedException wenn der HTTP-Request unterbrochen wird.
     */
    private static void downloadFile(String url, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        // HTTP-GET-Anfrage für die Datei
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        // Antwort als InputStream empfangen, um direkt in die Datei zu schreiben
        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Download-URL antwortete mit HTTP-Status: " + response.statusCode());
        }

        // Stream in die Zieldatei kopieren, vorhandene Datei wird überschrieben
        try (InputStream in = response.body()) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Prüft, ob das Programm auf einem Windows-Betriebssystem läuft.
     *
     * @return true, wenn "os.name" den String "win" enthält (z. B. "Windows 10").
     */
    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    /**
     * Setzt unter Windows das Desktop-Hintergrundbild über einen PowerShell-Aufruf.
     *
     * Technik:
     * - Es wird ein PowerShell-Befehl gebaut, der:
     *   1) den Registry-Wert "wallpaper" unter
     *      HKCU:\Control Panel\Desktop
     *      auf den angegebenen Bildpfad setzt.
     *   2) über "rundll32.exe user32.dll,UpdatePerUserSystemParameters"
     *      eine Aktualisierung der Benutzereinstellungen anstößt.
     *
     * @param imagePath Pfad zum heruntergeladenen Bild.
     * @throws IOException          bei Problemen beim Starten von PowerShell.
     * @throws InterruptedException wenn der PowerShell-Prozess unterbrochen wird.
     */
    private static void setWindowsWallpaper(Path imagePath) throws IOException, InterruptedException {
        // Absoluten Pfad holen und einfache Hochkommas escapen,
        // da PowerShell Strings mit '...' interpretiert.
        String pathForPs = imagePath.toAbsolutePath().toString().replace("'", "''");

        // PowerShell-Befehl:
        // - Setzt Registry-Eintrag "wallpaper" auf den Bildpfad.
        // - Stößt eine Aktualisierung der Systemparameter an.
        String psCommand =
                "Set-ItemProperty -Path 'HKCU:Control Panel\\Desktop' -Name wallpaper -Value '" + pathForPs + "'; " +
                "rundll32.exe user32.dll,UpdatePerUserSystemParameters";

        // ProcessBuilder startet PowerShell als externen Prozess
        ProcessBuilder pb = new ProcessBuilder(
                "powershell",
                "-NoProfile",                    // kein Benutzerprofil laden
                "-ExecutionPolicy", "Bypass",    // Ausführungsrichtlinie erleichtern
                "-Command", psCommand            // Befehl ausführen
        );

        // Konsolen-Ausgabe/Fehler aus PowerShell direkt durchreichen
        pb.inheritIO();

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("PowerShell zum Setzen des Wallpapers meldete ExitCode " + exitCode);
        }
    }

}
