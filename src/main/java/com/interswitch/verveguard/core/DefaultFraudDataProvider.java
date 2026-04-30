package com.interswitch.verveguard.core;

import com.interswitch.verveguard.api.GeoIpService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Default concrete implementation of AbstractFraudDataProvider.
 *
 * Provides no-op implementations for application-specific methods.
 * Applications should extend this class and override the methods they need.
 *
 * Example usage:
 * ```java
 * @Bean
 * public FraudDataProvider fraudDataProvider() {
 *     return new DefaultFraudDataProvider() {
 *         @Override
 *         public boolean isBlacklisted(String accountIdentifier) {
 *             return myDatabase.isBlacklisted(accountIdentifier);
 *         }
 *         // ... override other methods as needed
 *     };
 * }
 * ```
 */
@Slf4j
public class DefaultFraudDataProvider extends AbstractFraudDataProvider {

    public DefaultFraudDataProvider(GeoIpService geoIpService) {
        super(geoIpService);
    }

    @Override
    public boolean isBlacklisted(String accountIdentifier) {
        log.warn("isBlacklisted() not implemented. Returning false. Override this method in your implementation.");
        return false;
    }

    @Override
    public boolean isRateLimited(String ipAddress) {
        log.warn("isRateLimited() not implemented. Returning false. Override this method in your implementation.");
        return false;
    }

    @Override
    public int getVelocityCount(String cardHash, Duration window) {
        log.warn("getVelocityCount() not implemented. Returning 0. Override this method in your implementation.");
        return 0;
    }

    @Override
    public Optional<BigDecimal> getTransactionLimit(String accountIdentifier) {
        log.warn("getTransactionLimit() not implemented. Returning empty. Override this method in your implementation.");
        return Optional.empty();
    }
}
