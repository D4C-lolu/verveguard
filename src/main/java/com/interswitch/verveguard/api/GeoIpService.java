package com.interswitch.verveguard.api;

/**
 * Service for resolving IP addresses to geographical locations.
 * Implementations should use offline databases to avoid HTTP calls.
 */
public interface GeoIpService {

    /**
     * Lookup geographical information for an IP address.
     * Returns null if location cannot be determined.
     */
    LocationInfo lookup(String ipAddress);

    /**
     * Geographical location information for an IP address.
     */
    record LocationInfo(
        Double latitude,
        Double longitude,
        String country
    ) {
        public boolean isValid() {
            return latitude != null && longitude != null && country != null;
        }
    }
}
