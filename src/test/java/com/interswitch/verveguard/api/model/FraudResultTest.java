package com.interswitch.verveguard.api.model;

import com.interswitch.verveguard.api.FraudDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FraudResultTest {

    @Test
    void isBlockedShouldReturnTrueForBlockDecision() {
        FraudResult result = new FraudResult(
                FraudDecision.BLOCK,
                100,
                "BLOCKED",
                "detail",
                List.of(),
                Duration.ofMillis(10)
        );

        assertThat(result.isBlocked()).isTrue();
    }

    @Test
    void isBlockedShouldReturnFalseForAllowDecision() {
        FraudResult result = new FraudResult(
                FraudDecision.ALLOW,
                0,
                null,
                null,
                List.of(),
                Duration.ofMillis(10)
        );

        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    void isBlockedShouldReturnFalseForReviewDecision() {
        FraudResult result = new FraudResult(
                FraudDecision.REVIEW,
                50,
                "SUSPICIOUS",
                "detail",
                List.of(),
                Duration.ofMillis(10)
        );

        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    void allReasonCodesShouldCollectNonNullCodes() {
        List<GateResult> gateResults = List.of(
                GateResult.flag("G1", 10, "CODE_A", "detail"),
                GateResult.pass("G2"),
                GateResult.flag("G3", 20, "CODE_B", "detail"),
                GateResult.flag("G4", 15, null, "detail with no code")
        );

        FraudResult result = new FraudResult(
                FraudDecision.REVIEW,
                45,
                "CODE_B",
                "primary",
                gateResults,
                Duration.ofMillis(10)
        );

        assertThat(result.allReasonCodes())
                .containsExactly("CODE_A", "CODE_B");
    }

    @Test
    void allReasonCodesShouldReturnEmptyListWhenNoReasons() {
        List<GateResult> gateResults = List.of(
                GateResult.pass("G1"),
                GateResult.pass("G2")
        );

        FraudResult result = new FraudResult(
                FraudDecision.ALLOW,
                0,
                null,
                null,
                gateResults,
                Duration.ofMillis(10)
        );

        assertThat(result.allReasonCodes()).isEmpty();
    }
}
