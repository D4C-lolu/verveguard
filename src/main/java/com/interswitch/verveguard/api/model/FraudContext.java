package com.interswitch.verveguard.api.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.Map;

@Builder
public record FraudContext(
    String transactionId,
    String accountIdentifier,
    String cardHash,
    String ipAddress,
    BigDecimal amount,
    String currency,
    Instant transactionTime,
    Set<String> lastKnownIpAddresses,
    Map<String, Object> metadata
) {
}