package com.interswitch.verveguard.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

public interface FraudDataProvider {

    boolean isBlacklisted(String accountIdentifier);

    boolean isRateLimited(String ipAddress);

    int getVelocityCount(String cardHash, Duration window);

    Optional<BigDecimal> getTransactionLimit(String accountIdentifier);
}