package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationAnomalyGateTest {

    private final FraudDataProvider dataProvider = mock(FraudDataProvider.class);
    private LocationAnomalyGate gate;

    @BeforeEach
    void setUp() {
        gate = new LocationAnomalyGate(60, 35);
    }

    @Test
    void shouldFlagWhenAnomalyScoreExceedsThreshold() {
        Set<String> lastKnownIps = Set.of("192.168.1.1", "192.168.1.2");
        FraudContext ctx = FraudContext.builder()
                .ipAddress("203.0.113.50")
                .lastKnownIpAddresses(lastKnownIps)
                .build();
        when(dataProvider.getLocationAnomalyScore("203.0.113.50", lastKnownIps)).thenReturn(75);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.score()).isEqualTo(35);
        assertThat(result.reasonCode()).isEqualTo("LOCATION_ANOMALY_DETECTED");
        assertThat(result.reasonDetail()).contains("75/100");
    }

    @Test
    void shouldFlagWhenAnomalyScoreEqualsThreshold() {
        Set<String> lastKnownIps = Set.of("10.0.0.1");
        FraudContext ctx = FraudContext.builder()
                .ipAddress("198.51.100.1")
                .lastKnownIpAddresses(lastKnownIps)
                .build();
        when(dataProvider.getLocationAnomalyScore("198.51.100.1", lastKnownIps)).thenReturn(60);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isEqualTo(35);
        assertThat(result.reasonCode()).isEqualTo("LOCATION_ANOMALY_DETECTED");
    }

    @Test
    void shouldPassWhenAnomalyScoreBelowThreshold() {
        Set<String> lastKnownIps = Set.of("192.168.1.1");
        FraudContext ctx = FraudContext.builder()
                .ipAddress("192.168.1.5")
                .lastKnownIpAddresses(lastKnownIps)
                .build();
        when(dataProvider.getLocationAnomalyScore("192.168.1.5", lastKnownIps)).thenReturn(20);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void shouldPassWhenNoLocationHistory() {
        FraudContext ctx = FraudContext.builder()
                .ipAddress("203.0.113.100")
                .lastKnownIpAddresses(null)
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
    }

    @Test
    void shouldPassWhenEmptyLocationHistory() {
        FraudContext ctx = FraudContext.builder()
                .ipAddress("203.0.113.100")
                .lastKnownIpAddresses(Set.of())
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
    }

    @Test
    void shouldNotBeHardBlockCapable() {
        assertThat(gate.isHardBlockCapable()).isFalse();
    }

    @Test
    void shouldHaveCorrectOrder() {
        assertThat(gate.getOrder()).isEqualTo(4);
    }

    @Test
    void shouldUseConfiguredThresholdAndScore() {
        LocationAnomalyGate customGate = new LocationAnomalyGate(80, 50);
        Set<String> lastKnownIps = Set.of("10.0.0.1");

        FraudContext ctx = FraudContext.builder()
                .ipAddress("172.16.0.1")
                .lastKnownIpAddresses(lastKnownIps)
                .build();
        when(dataProvider.getLocationAnomalyScore("172.16.0.1", lastKnownIps)).thenReturn(85);

        GateResult result = customGate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isEqualTo(50);
    }
}
