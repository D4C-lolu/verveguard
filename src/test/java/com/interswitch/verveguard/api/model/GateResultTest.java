package com.interswitch.verveguard.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GateResultTest {

    @Test
    void passShouldCreateZeroScoreResult() {
        GateResult result = GateResult.pass("TEST_GATE");

        assertThat(result.gateName()).isEqualTo("TEST_GATE");
        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
        assertThat(result.reasonDetail()).isNull();
        assertThat(result.hardBlock()).isFalse();
    }

    @Test
    void flagShouldCreateSoftBlockResult() {
        GateResult result = GateResult.flag("VELOCITY", 30, "VELOCITY_EXCEEDED", "5 txns in 60s");

        assertThat(result.gateName()).isEqualTo("VELOCITY");
        assertThat(result.score()).isEqualTo(30);
        assertThat(result.reasonCode()).isEqualTo("VELOCITY_EXCEEDED");
        assertThat(result.reasonDetail()).isEqualTo("5 txns in 60s");
        assertThat(result.hardBlock()).isFalse();
    }

    @Test
    void blockWithDefaultScoreShouldCreate100PointHardBlock() {
        GateResult result = GateResult.block("BLACKLIST", "BLOCKED", "Account blacklisted");

        assertThat(result.gateName()).isEqualTo("BLACKLIST");
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.reasonCode()).isEqualTo("BLOCKED");
        assertThat(result.reasonDetail()).isEqualTo("Account blacklisted");
        assertThat(result.hardBlock()).isTrue();
    }

    @Test
    void blockWithCustomScoreShouldCreateHardBlock() {
        GateResult result = GateResult.block("RATE_LIMIT", 50, "RATE_LIMITED", "Too many requests");

        assertThat(result.gateName()).isEqualTo("RATE_LIMIT");
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.reasonCode()).isEqualTo("RATE_LIMITED");
        assertThat(result.reasonDetail()).isEqualTo("Too many requests");
        assertThat(result.hardBlock()).isTrue();
    }
}
