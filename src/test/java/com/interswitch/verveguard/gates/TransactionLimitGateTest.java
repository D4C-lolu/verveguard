package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionLimitGateTest {

    private final FraudDataProvider dataProvider = mock(FraudDataProvider.class);
    private TransactionLimitGate gate;

    @BeforeEach
    void setUp() {
        gate = new TransactionLimitGate(25);
    }

    @Test
    void shouldFlagWhenAmountExceedsLimit() {
        FraudContext ctx = FraudContext.builder()
                .accountIdentifier("account-123")
                .amount(new BigDecimal("15000.00"))
                .build();
        when(dataProvider.getTransactionLimit("account-123"))
                .thenReturn(Optional.of(new BigDecimal("10000.00")));

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.score()).isEqualTo(25);
        assertThat(result.reasonCode()).isEqualTo("EXCEEDS_LIMIT");
        assertThat(result.reasonDetail()).contains("15000.00").contains("10000.00");
    }

    @Test
    void shouldPassWhenAmountIsUnderLimit() {
        FraudContext ctx = FraudContext.builder()
                .accountIdentifier("account-456")
                .amount(new BigDecimal("5000.00"))
                .build();
        when(dataProvider.getTransactionLimit("account-456"))
                .thenReturn(Optional.of(new BigDecimal("10000.00")));

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void shouldPassWhenAmountEqualsLimit() {
        FraudContext ctx = FraudContext.builder()
                .accountIdentifier("account-789")
                .amount(new BigDecimal("10000.00"))
                .build();
        when(dataProvider.getTransactionLimit("account-789"))
                .thenReturn(Optional.of(new BigDecimal("10000.00")));

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
    }

    @Test
    void shouldPassWhenNoLimitConfigured() {
        FraudContext ctx = FraudContext.builder()
                .accountIdentifier("account-no-limit")
                .amount(new BigDecimal("999999.99"))
                .build();
        when(dataProvider.getTransactionLimit("account-no-limit"))
                .thenReturn(Optional.empty());

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void shouldNotBeHardBlockCapable() {
        assertThat(gate.isHardBlockCapable()).isFalse();
    }

    @Test
    void shouldHaveCorrectOrder() {
        assertThat(gate.getOrder()).isEqualTo(11);
    }
}
