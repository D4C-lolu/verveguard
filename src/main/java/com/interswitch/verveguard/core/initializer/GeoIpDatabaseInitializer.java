package com.interswitch.verveguard.core.initializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Automatically downloads the GeoLite2-City database on application startup if not present.
 * Downloads only once and caches the database locally.
 */
@Slf4j
@Component
public class GeoIpDatabaseInitializer {

    private static final String GEOLITE2_DOWNLOAD_URL =
        "https://git.io/GeoLite2-City.mmdb";
    private static final String DEFAULT_DB_PATH = "GeoLite2-City.mmdb";

    public GeoIpDatabaseInitializer() {
        try {
            initializeDatabase();
        } catch (Exception e) {
            log.warn("Failed to initialize GeoIP database on startup: {}", e.getMessage());
        }
    }

    private void initializeDatabase() throws IOException {
        // Check if database already exists in classpath resources
        if (databaseExistsInClasspath()) {
            log.info("GeoLite2 database already exists in classpath");
            return;
        }

        // Check if database file already exists in temp/app directory
        File dbFile = new File(DEFAULT_DB_PATH);
        if (dbFile.exists()) {
            log.info("GeoLite2 database already exists at: {}", dbFile.getAbsolutePath());
            return;
        }

        // Download the database
        log.info("Downloading GeoLite2-City database for the first time...");
        downloadDatabase(dbFile);
    }

    private boolean databaseExistsInClasspath() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("GeoLite2-City.mmdb")) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }

    private void downloadDatabase(File targetFile) throws IOException {
        try (HttpClient client = HttpClient.newBuilder().build()){

            // Follow redirects to the actual MaxMind CDN
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEOLITE2_DOWNLOAD_URL))
                    .GET()
                    .build();

            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    HttpResponse<InputStream> response = client.send(request,
                            HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() == 200) {
                        try (InputStream is = response.body();
                             FileOutputStream fos = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                        log.info("Successfully downloaded GeoLite2 database to: {}",
                            targetFile.getAbsolutePath());
                        return;
                    } else if (response.statusCode() >= 400) {
                        throw new IOException("HTTP " + response.statusCode() + " downloading database");
                    }
                } catch (IOException | InterruptedException e) {
                    if (attempt == maxRetries) {
                        throw new IOException("Failed to download GeoLite2 database after " + maxRetries + " attempts", e);
                    }
                    log.warn("Download attempt {} failed, retrying...", attempt, e);
                    Thread.sleep(1000L * attempt); // Exponential backoff
                }
            }

        } catch (Exception e) {
            throw new IOException("Could not download GeoLite2 database: " + e.getMessage(), e);
        }
    }
}
