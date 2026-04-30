package com.interswitch.verveguard.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "verveguard")
@Data
public class VerveguardProperties {

    private boolean enabled = true;
    private int blockThreshold = 70;
    private int reviewThreshold = 30;

    private ToggleConfig blacklist = new ToggleConfig();
    private ToggleConfig rateLimit = new ToggleConfig();
    private GateConfig velocity = new GateConfig();
    private TransactionLimitConfig transactionLimit = new TransactionLimitConfig();
    private TimeWindowConfig timeWindow = new TimeWindowConfig();
    private LocationAnomalyConfig locationAnomaly = new LocationAnomalyConfig();
    private GeoIpConfig geoIp = new GeoIpConfig();

    @Data
    public static class ToggleConfig {
        private boolean enabled = true;
    }

    @Data
    public static class GateConfig {
        private boolean enabled = true;
        private int threshold = 3;
        private int windowSeconds = 60;
        private int score = 30;
    }

    @Data
    public static class TransactionLimitConfig {
        private boolean enabled = true;
        private int score = 25;
    }

    @Data
    public static class TimeWindowConfig {
        private boolean enabled = true;
        private int startHour = 6;
        private int endHour = 22;
        private int score = 10;
    }

    @Data
    public static class LocationAnomalyConfig {
        private boolean enabled = true;
        private int anomalyThreshold = 60;  // Score (0-100) above which to flag
        private int score = 35;
    }

    @Data
    public static class GeoIpConfig {
        private boolean enabled = true;
        private String databasePath;  // Optional: path to GeoLite2-City.mmdb file
    }
}