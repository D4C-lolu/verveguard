package com.interswitch.verveguard.exception;

import com.interswitch.verveguard.api.FraudDecision;
import com.interswitch.verveguard.api.model.FraudResult;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FraudBlockedExceptionTest {

    @Test
    void shouldContainFraudResultAndMessage() {
        // Given
        FraudResult result = new FraudResult(
            FraudDecision.BLOCK,
            85,
            "BLACKLIST",
            "Account is blacklisted",
            List.of(GateResult.block("BLACKLIST", 50, "BLACKLIST", "Account is blacklisted")),
            Duration.ofMillis(10)
        );

        // When
        FraudBlockedException exception = new FraudBlockedException(result);

        // Then
        assertThat(exception.getMessage()).isEqualTo("Transaction blocked: BLACKLIST");
        assertThat(exception.getResult()).isSameAs(result);
        assertThat(exception.getResult().decision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(exception.getResult().totalScore()).isEqualTo(85);
    }

    @Test
    void shouldBeARuntimeException() {
        // Given
        FraudResult result = new FraudResult(
            FraudDecision.BLOCK,
            100,
            "VELOCITY",
            "Too many transactions",
            List.of(),
            Duration.ofMillis(5)
        );

        // When
        FraudBlockedException exception = new FraudBlockedException(result);

        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
