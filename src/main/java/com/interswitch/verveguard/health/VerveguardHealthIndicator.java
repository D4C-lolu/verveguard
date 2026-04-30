package com.interswitch.verveguard.health;

import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.config.VerveguardProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "verveguard.health", name = "enabled", matchIfMissing = true)
public class VerveguardHealthIndicator implements HealthIndicator {

    private final List<FraudGate> gates;
    private final VerveguardProperties props;

    @Override
    public Health health() {
        return Health.up()
            .withDetail("enabled", props.isEnabled())
            .withDetail("gatesLoaded", gates.size())
            .withDetail("gates", gates.stream().map(FraudGate::getName).toList())
            .withDetail("blockThreshold", props.getBlockThreshold())
            .withDetail("reviewThreshold", props.getReviewThreshold())
            .build();
    }
}