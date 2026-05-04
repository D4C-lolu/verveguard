package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VelocityGateTest {

    private final FraudDataProvider dataProvider = mock(FraudDataProvider.class);

    @Test
    void shouldFlagWhenVelocityExceedsThreshold() {
        VelocityGate gate = new VelocityGate(5, Duration.ofSeconds(60), 30);
        FraudContext ctx = FraudContext.builder()
                .cardHash("card-hash-abc")
                .build();
        when(dataProvider.getVelocityCount("card-hash-abc", Duration.ofSeconds(60))).thenReturn(5);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.score()).isEqualTo(30);
        assertThat(result.reasonCode()).isEqualTo("VELOCITY_EXCEEDED");
        assertThat(result.reasonDetail()).contains("5 transactions");
    }

    @Test
    void shouldFlagWhenVelocityExceedsThresholdByMore() {
        VelocityGate gate = new VelocityGate(3, Duration.ofSeconds(120), 25);
        FraudContext ctx = FraudContext.builder()
                .cardHash("card-hash-xyz")
                .build();
        when(dataProvider.getVelocityCount("card-hash-xyz", Duration.ofSeconds(120))).thenReturn(10);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isEqualTo(25);
        assertThat(result.reasonCode()).isEqualTo("VELOCITY_EXCEEDED");
    }

    @Test
    void shouldPassWhenVelocityIsBelowThreshold() {
        VelocityGate gate = new VelocityGate(5, Duration.ofSeconds(60), 30);
        FraudContext ctx = FraudContext.builder()
                .cardHash("card-hash-def")
                .build();
        when(dataProvider.getVelocityCount("card-hash-def", Duration.ofSeconds(60))).thenReturn(2);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void shouldPassWhenVelocityIsExactlyOneUnderThreshold() {
        VelocityGate gate = new VelocityGate(5, Duration.ofSeconds(60), 30);
        FraudContext ctx = FraudContext.builder()
                .cardHash("card-hash-ghi")
                .build();
        when(dataProvider.getVelocityCount("card-hash-ghi", Duration.ofSeconds(60))).thenReturn(4);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
    }

    @Test
    void shouldNotBeHardBlockCapable() {
        VelocityGate gate = new VelocityGate(5, Duration.ofSeconds(60), 30);
        assertThat(gate.isHardBlockCapable()).isFalse();
    }

    @Test
    void shouldHaveCorrectOrder() {
        VelocityGate gate = new VelocityGate(5, Duration.ofSeconds(60), 30);
        assertThat(gate.getOrder()).isEqualTo(10);
    }
}
