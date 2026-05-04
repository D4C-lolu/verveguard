package com.interswitch.verveguard.config;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudEvaluator;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.GeoIpService;
import com.interswitch.verveguard.core.*;
import com.interswitch.verveguard.core.pipeline.FraudPipeline;
import com.interswitch.verveguard.core.service.MaxMindGeoIpService;
import com.interswitch.verveguard.gates.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "verveguard", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(VerveguardProperties.class)
public class VerveguardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "verveguard.blacklist", name = "enabled", matchIfMissing = true)
    public BlacklistGate blacklistGate() {
        return new BlacklistGate();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "verveguard.rate-limit", name = "enabled", matchIfMissing = true)
    public RateLimitGate rateLimitGate() {
        return new RateLimitGate();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "verveguard.velocity", name = "enabled", matchIfMissing = true)
    public VelocityGate velocityGate(VerveguardProperties props) {
        var cfg = props.getVelocity();
        return new VelocityGate(cfg.getThreshold(), Duration.ofSeconds(cfg.getWindowSeconds()), cfg.getScore());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "verveguard.transaction-limit", name = "enabled", matchIfMissing = true)
    public TransactionLimitGate transactionLimitGate(VerveguardProperties props) {
        return new TransactionLimitGate(props.getTransactionLimit().getScore());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "verveguard.time-window", name = "enabled", matchIfMissing = true)
    public TimeWindowGate timeWindowGate(VerveguardProperties props) {
        var cfg = props.getTimeWindow();
        return new TimeWindowGate(cfg.getStartHour(), cfg.getEndHour(), cfg.getScore());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "verveguard.location-anomaly", name = "enabled", matchIfMissing = true)
    public LocationAnomalyGate locationAnomalyGate(VerveguardProperties props) {
        var cfg = props.getLocationAnomaly();
        return new LocationAnomalyGate(cfg.getAnomalyThreshold(), cfg.getScore());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "verveguard.location-anomaly", name = "enabled", matchIfMissing = true)
    public GeoIpService geoIpService(VerveguardProperties props) throws Exception {
        var config = props.getGeoIp();
        return new MaxMindGeoIpService(config.getDatabasePath());
    }

    @Bean
    @ConditionalOnMissingBean
    public FraudDataProvider fraudDataProvider(GeoIpService geoIpService) {
        return new DefaultFraudDataProvider(geoIpService);
    }

    @Bean
    @ConditionalOnMissingBean
    public FraudEvaluator fraudEvaluator(
            List<FraudGate> gates,
            FraudDataProvider dataProvider,
            VerveguardProperties props) {
        return new FraudPipeline(gates, dataProvider, props.getBlockThreshold(), props.getReviewThreshold());
    }
}