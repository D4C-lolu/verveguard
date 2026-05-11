package com.interswitch.verveguard.health;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;
import com.interswitch.verveguard.config.VerveguardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerveguardHealthIndicatorTest {

    @Test
    void shouldReturnUpHealthWithDetails() {
        // Given
        List<FraudGate> gates = List.of(
            createGate("BLACKLIST"),
            createGate("RATE_LIMIT")
        );

        VerveguardProperties props = new VerveguardProperties();
        props.setEnabled(true);
        props.setBlockThreshold(70);
        props.setReviewThreshold(30);

        VerveguardHealthIndicator indicator = new VerveguardHealthIndicator(gates, props);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("enabled", true);
        assertThat(health.getDetails()).containsEntry("gatesLoaded", 2);
        assertThat(health.getDetails()).containsEntry("blockThreshold", 70);
        assertThat(health.getDetails()).containsEntry("reviewThreshold", 30);
        assertThat(health.getDetails().get("gates")).isEqualTo(List.of("BLACKLIST", "RATE_LIMIT"));
    }

    @Test
    void shouldReportDisabledWhenVerveguardIsDisabled() {
        // Given
        List<FraudGate> gates = List.of();
        VerveguardProperties props = new VerveguardProperties();
        props.setEnabled(false);

        VerveguardHealthIndicator indicator = new VerveguardHealthIndicator(gates, props);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("enabled", false);
        assertThat(health.getDetails()).containsEntry("gatesLoaded", 0);
    }

    private FraudGate createGate(String name) {
        return new FraudGate() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getOrder() {
                return 0;
            }

            @Override
            public boolean isHardBlockCapable() {
                return false;
            }

            @Override
            public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
                return GateResult.pass(name);
            }
        };
    }
}
