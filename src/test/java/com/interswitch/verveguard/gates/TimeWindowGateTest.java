package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TimeWindowGateTest {

    private final FraudDataProvider dataProvider = mock(FraudDataProvider.class);

    private Instant timeAtHour(int hour) {
        return LocalDateTime.of(2024, 1, 15, hour, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    @Test
    void shouldFlagTransactionBeforeStartHour() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 10);
        FraudContext ctx = FraudContext.builder()
                .transactionTime(timeAtHour(3))
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isEqualTo(10);
        assertThat(result.reasonCode()).isEqualTo("AFTER_HOURS");
        assertThat(result.reasonDetail()).contains("hour 3");
    }

    @Test
    void shouldFlagTransactionAtOrAfterEndHour() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 10);
        FraudContext ctx = FraudContext.builder()
                .transactionTime(timeAtHour(22))
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isEqualTo(10);
        assertThat(result.reasonCode()).isEqualTo("AFTER_HOURS");
    }

    @Test
    void shouldFlagLateNightTransaction() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 15);
        FraudContext ctx = FraudContext.builder()
                .transactionTime(timeAtHour(23))
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isEqualTo(15);
        assertThat(result.reasonCode()).isEqualTo("AFTER_HOURS");
    }

    @Test
    void shouldPassTransactionWithinWindow() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 10);
        FraudContext ctx = FraudContext.builder()
                .transactionTime(timeAtHour(12))
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void shouldPassTransactionAtStartHour() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 10);
        FraudContext ctx = FraudContext.builder()
                .transactionTime(timeAtHour(6))
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
    }

    @Test
    void shouldPassTransactionJustBeforeEndHour() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 10);
        FraudContext ctx = FraudContext.builder()
                .transactionTime(timeAtHour(21))
                .build();

        GateResult result = gate.evaluate(ctx, dataProvider);

        assertThat(result.score()).isZero();
    }

    @Test
    void shouldNotBeHardBlockCapable() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 10);
        assertThat(gate.isHardBlockCapable()).isFalse();
    }

    @Test
    void shouldHaveCorrectOrder() {
        TimeWindowGate gate = new TimeWindowGate(6, 22, 10);
        assertThat(gate.getOrder()).isEqualTo(20);
    }
}
