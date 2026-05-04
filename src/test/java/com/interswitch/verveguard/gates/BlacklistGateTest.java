package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlacklistGateTest {

    private final FraudDataProvider dataProvider = mock(FraudDataProvider.class);
    private BlacklistGate gate;

    @BeforeEach
    void setUp() {
        gate = new BlacklistGate();
    }

    @Test
    void shouldHardBlockWhenAccountIsBlacklisted() {
        FraudContext ctx = FraudContext.builder()
                .accountIdentifier("blocked-account-123")
                .build();
        when(dataProvider.isBlacklisted("blocked-account-123")).thenReturn(true);

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.hardBlock()).isTrue();
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.reasonCode()).isEqualTo("BLACKLISTED");
        assertThat(result.gateName()).isEqualTo("BLACKLIST");
    }

    @Test
    void shouldPassWhenAccountIsNotBlacklisted() {
        FraudContext ctx = FraudContext.builder()
                .accountIdentifier("good-account-456")
                .build();
        when(dataProvider.isBlacklisted("good-account-456")).thenReturn(false);

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
        assertThat(gate.getOrder()).isEqualTo(1);
    }
}
