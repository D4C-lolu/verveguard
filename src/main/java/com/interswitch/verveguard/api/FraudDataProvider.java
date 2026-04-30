package com.interswitch.verveguard.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public interface FraudDataProvider {

    boolean isBlacklisted(String accountIdentifier);

    boolean isRateLimited(String ipAddress);

    int getVelocityCount(String cardHash, Duration window);

    Optional<BigDecimal> getTransactionLimit(String accountIdentifier);

    /**
     * Detect location anomaly based on IP address history.
     * Returns the anomaly score (0-100), where higher scores indicate more suspicious patterns.
     * Factors considered: distance from last known locations, travel speed, time between transactions.
     */
    int getLocationAnomalyScore(String ipAddress, Set<String> lastKnownIpAddresses);
}