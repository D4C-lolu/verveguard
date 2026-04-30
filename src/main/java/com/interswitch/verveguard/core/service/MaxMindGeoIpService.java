package com.interswitch.verveguard.core.service;

import com.interswitch.verveguard.api.GeoIpService;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

/**
 * MaxMind GeoIP2 implementation of GeoIpService.
 * Uses embedded GeoLite2-City.mmdb database for offline IP lookups.
 *
 * For library usage, applications should:
 * 1. Download GeoLite2-City.mmdb from https://dev.maxmind.com/geoip/geolite2-free-geolite2-city/
 * 2. Place it in their classpath or provide the path via constructor
 */
@Slf4j
public class MaxMindGeoIpService implements GeoIpService {

    private final DatabaseReader reader;

    /**
     * Load database from classpath (src/main/resources/GeoLite2-City.mmdb)
     */
    public MaxMindGeoIpService() throws IOException {
        this(null);
    }

    /**
     * Load database from specified file path, or fallback to classpath/default location if null
     */
    public MaxMindGeoIpService(String databasePath) throws IOException {
        DatabaseReader.Builder builder;

        if (databasePath != null) {
            // Load from specified file path
            File dbFile = new File(databasePath);
            if (!dbFile.exists()) {
                throw new IOException("GeoIP database not found at: " + databasePath);
            }
            builder = new DatabaseReader.Builder(dbFile);
        } else {
            // Try to load from classpath first
            InputStream database = getClass().getClassLoader()
                    .getResourceAsStream("GeoLite2-City.mmdb");

            if (database != null) {
                builder = new DatabaseReader.Builder(database);
            } else {
                // Fallback to local file (downloaded on startup)
                File defaultDb = new File("GeoLite2-City.mmdb");
                if (defaultDb.exists()) {
                    builder = new DatabaseReader.Builder(defaultDb);
                } else {
                    throw new IOException("GeoLite2-City.mmdb not found in classpath or at: " +
                        defaultDb.getAbsolutePath() + ". The database should be auto-downloaded on startup. " +
                        "If not present, visit: https://dev.maxmind.com/geoip/geolite2-free-geolite2-city/");
                }
            }
        }

        this.reader = builder.build();
        log.info("MaxMind GeoIP database loaded successfully");
    }

    @Override
    public LocationInfo lookup(String ipAddress) {
        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            CityResponse response = reader.city(inetAddress);

            Double latitude = response.getLocation().getLatitude();
            Double longitude = response.getLocation().getLongitude();
            String country = response.getCountry().getIsoCode();

            if (latitude != null && longitude != null && country != null) {
                return new LocationInfo(latitude, longitude, country);
            }

        } catch (IOException | GeoIp2Exception e) {
            log.debug("Could not resolve location for IP: {}", ipAddress, e);
        }

        return null; // Location not found or invalid
    }
}
