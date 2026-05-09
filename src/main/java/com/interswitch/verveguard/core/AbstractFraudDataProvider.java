package com.interswitch.verveguard.core;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.interswitch.verveguard.api.GeoIpService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base implementation of FraudDataProvider.
 * Provides complete implementation for location-based fraud detection using:
 * - Offline GeoIP database lookups (no HTTP required)
 * - In-memory caching for performance
 * - Distance calculations for impossible travel detection
 * Applications must extend this class and implement the abstract methods
 * for their specific data sources (blacklists, rate limits, etc.)
 */
@Slf4j
public abstract class AbstractFraudDataProvider implements FraudDataProvider {

    private final GeoIpService geoIpService;
    private final Cache<String, GeoIpService.LocationInfo> locationCache;

    protected AbstractFraudDataProvider(GeoIpService geoIpService) {
        this.geoIpService = geoIpService;
        // Cache with 1-hour expiry for frequently seen IPs
        this.locationCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(10000)
                .build();
    }

    // ========== APPLICATION-SPECIFIC METHODS (MUST BE IMPLEMENTED) ==========

    @Override
    public abstract boolean isBlacklisted(String accountIdentifier);

    @Override
    public abstract boolean isRateLimited(String ipAddress);

    @Override
    public abstract int getVelocityCount(String cardHash, Duration window);

    @Override
    public abstract Optional<BigDecimal> getTransactionLimit(String accountIdentifier);

    // ========== LOCATION ANOMALY DETECTION (FULLY IMPLEMENTED) ==========

    @Override
    public int getLocationAnomalyScore(String currentIp, Set<String> lastKnownIpAddresses) {
        try {
            GeoIpService.LocationInfo currentLocation = getOrFetchLocation(currentIp);
            if (currentLocation == null) {
                log.warn("Could not resolve location for current IP: {}", currentIp);
                return 0;
            }

            if (lastKnownIpAddresses.isEmpty()) {
                return 0;
            }

            // Pre-convert current IP coordinates to radians once — reused in every Haversine call
            final double currentLatRad = Math.toRadians(currentLocation.latitude());
            final double currentLonRad = Math.toRadians(currentLocation.longitude());
            final String currentCountry = currentLocation.country();

            int anomalyScore = 0;

            for (String historicalIp : lastKnownIpAddresses) {
                // Already saturated — no point computing more
                if (anomalyScore >= 100) {
                    break;
                }

                // Skip the current IP itself (distance = 0, same country)
                if (historicalIp.equals(currentIp)) {
                    continue;
                }

                GeoIpService.LocationInfo historicalLocation = getOrFetchLocation(historicalIp);
                if (historicalLocation == null) {
                    continue;
                }

                if (!currentCountry.equals(historicalLocation.country())) {
                    anomalyScore += 25;
                }

                anomalyScore += distanceScore(
                        Math.toRadians(historicalLocation.latitude()),
                        Math.toRadians(historicalLocation.longitude()),
                        currentLatRad,
                        currentLonRad
                );
            }

            return Math.min(anomalyScore, 100);

        } catch (Exception e) {
            log.error("Error calculating location anomaly score for IP: {}", currentIp, e);
            return 0;
        }
    }

    /**
     * Accepts pre-converted radians — callers must not pass raw degrees.
     * Haversine formula; returns distance in kilometers.
     */
    private static double haversineKm(double lat1Rad, double lon1Rad, double lat2Rad, double lon2Rad) {
        double sinDLat = Math.sin((lat2Rad - lat1Rad) / 2);
        double sinDLon = Math.sin((lon2Rad - lon1Rad) / 2);

        double a = sinDLat * sinDLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinDLon * sinDLon;

        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Maps a Haversine distance (km) to an anomaly score contribution.
     * Accepts pre-converted radians — callers must not pass raw degrees.
     */
    private static int distanceScore(double lat1Rad, double lon1Rad, double lat2Rad, double lon2Rad) {
        double distance = haversineKm(lat1Rad, lon1Rad, lat2Rad, lon2Rad);

        if (distance > 900) return 35; // Likely impossible travel
        if (distance > 500) return 20; // Long-distance
        if (distance > 100) return 10; // Moderate
        return 0;
    }

    private GeoIpService.LocationInfo getOrFetchLocation(String ipAddress) {
        if (isPrivateIp(ipAddress)) return null;
        return locationCache.get(ipAddress, geoIpService::lookup);
    }

    private static boolean isPrivateIp(String ip) {
        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("172.16.")
                || ip.startsWith("127.")
                || ip.equals("::1");
    }

    private static final int EARTH_RADIUS_KM = 6371;
}
