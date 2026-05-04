package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitGateTest {

    private final FraudDataProvider dataProvider = mock(FraudDataProvider.class);
    private RateLimitGate gate;

    @BeforeEach
    void setUp() {
        gate = new RateLimitGate();
    }

    @Test
    void shouldHardBlockWhenIpIsRateLimited() {
        FraudContext ctx = FraudContext.builder()
                .ipAddress("192.168.1.100")
                .build();
        when(dataProvider.isRateLimited("192.168.1.100")).thenReturn(true);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.hardBlock()).isTrue();
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.reasonCode()).isEqualTo("RATE_LIMITED");
        assertThat(result.reasonDetail()).contains("192.168.1.100");
    }

    @Test
    void shouldPassWhenIpIsNotRateLimited() {
        FraudContext ctx = FraudContext.builder()
                .ipAddress("10.0.0.1")
                .build();
        when(dataProvider.isRateLimited("10.0.0.1")).thenReturn(false);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void shouldBeHardBlockCapable() {
        assertThat(gate.isHardBlockCapable()).isTrue();
    }

    @Test
    void shouldHaveCorrectOrder() {
        assertThat(gate.getOrder()).isEqualTo(2);
    }
}
