package com.interswitch.verveguard.api.model;

import com.interswitch.verveguard.api.FraudDecision;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record FraudResult(
    FraudDecision decision,
    int totalScore,
    String primaryReasonCode,
    String primaryReasonDetail,
    List<GateResult> gateResults,
    Duration evaluationTime
) {
    public boolean isBlocked() { return decision == FraudDecision.BLOCK; }

    public List<String> allReasonCodes() {
        return gateResults.stream()
                .map(GateResult::reasonCode)
                .filter(Objects::nonNull)
                .toList();
    }
}