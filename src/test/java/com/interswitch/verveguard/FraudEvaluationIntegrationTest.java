package com.interswitch.verveguard;

import com.interswitch.verveguard.api.FraudDecision;
import com.interswitch.verveguard.api.FraudEvaluator;
import com.interswitch.verveguard.api.GeoIpService;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.FraudResult;
import com.interswitch.verveguard.core.AbstractFraudDataProvider;
import com.interswitch.verveguard.core.pipeline.FraudPipeline;
import com.interswitch.verveguard.gates.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that wire up the full pipeline with all gates.
 * Uses in-memory test doubles instead of mocks for a more realistic test.
 */
class FraudEvaluationIntegrationTest {

    private TestDataProvider dataProvider;
    private FraudEvaluator evaluator;

    @BeforeEach
    void setUp() {
        GeoIpService geoIpService = _ -> new GeoIpService.LocationInfo(0.0, 0.0, "XX");
        dataProvider = new TestDataProvider(geoIpService);

        List<com.interswitch.verveguard.api.FraudGate> gates = List.of(
                new BlacklistGate(),
                new RateLimitGate(),
                new VelocityGate(5, Duration.ofSeconds(60), 30),
                new TransactionLimitGate(25),
                new TimeWindowGate(6, 22, 10),
                new LocationAnomalyGate(60, 35)
        );

        evaluator = new FraudPipeline(gates, dataProvider, 70, 30);
    }

    private FraudContext.FraudContextBuilder baseContext() {
        return FraudContext.builder()
                .transactionId("txn-" + System.nanoTime())
                .accountIdentifier("account-123")
                .cardHash("card-hash-abc")
                .ipAddress("1.2.3.4")
                .amount(new BigDecimal("100.00"))
                .currency("NGN")
                .transactionTime(businessHoursTime());
    }

    private Instant businessHoursTime() {
        return LocalDateTime.of(2024, 6, 15, 14, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    private Instant afterHoursTime() {
        return LocalDateTime.of(2024, 6, 15, 23, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    @Nested
    class CleanTransactions {

        @Test
        void shouldAllowCleanTransaction() {
            FraudContext ctx = baseContext().build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
            assertThat(result.totalScore()).isZero();
            assertThat(result.isBlocked()).isFalse();
        }

        @Test
        void shouldAllowTransactionWithinAllLimits() {
            dataProvider.setTransactionLimit("account-123", new BigDecimal("1000.00"));

            FraudContext ctx = baseContext()
                    .amount(new BigDecimal("500.00"))
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
        }
    }

    @Nested
    class HardBlocks {

        @Test
        void shouldImmediatelyBlockBlacklistedAccount() {
            dataProvider.blacklist("bad-account");

            FraudContext ctx = baseContext()
                    .accountIdentifier("bad-account")
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.primaryReasonCode()).isEqualTo("BLACKLISTED");
            assertThat(result.gateResults()).hasSize(1); // Short-circuited
        }

        @Test
        void shouldImmediatelyBlockRateLimitedIp() {
            dataProvider.rateLimit("192.168.1.100");

            FraudContext ctx = baseContext()
                    .ipAddress("192.168.1.100")
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.primaryReasonCode()).isEqualTo("RATE_LIMITED");
        }

        @Test
        void shouldBlockBlacklistedBeforeRateLimit() {
            dataProvider.blacklist("bad-account");
            dataProvider.rateLimit("1.2.3.4");

            FraudContext ctx = baseContext()
                    .accountIdentifier("bad-account")
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            // Blacklist gate has order 1, rate limit has order 2
            assertThat(result.primaryReasonCode()).isEqualTo("BLACKLISTED");
        }
    }

    @Nested
    class SoftFlags {

        @Test
        void shouldFlagButAllowAfterHoursTransaction() {
            FraudContext ctx = baseContext()
                    .transactionTime(afterHoursTime())
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            // Score 10 is below review threshold of 30
            assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
            assertThat(result.totalScore()).isEqualTo(10);
            assertThat(result.allReasonCodes()).contains("AFTER_HOURS");
        }

        @Test
        void shouldFlagHighVelocity() {
            dataProvider.setVelocityCount("fast-card", 6);

            FraudContext ctx = baseContext()
                    .cardHash("fast-card")
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.totalScore()).isEqualTo(30);
            assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
        }

        @Test
        void shouldFlagTransactionOverLimit() {
            dataProvider.setTransactionLimit("account-123", new BigDecimal("100.00"));

            FraudContext ctx = baseContext()
                    .amount(new BigDecimal("150.00"))
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.totalScore()).isEqualTo(25);
            assertThat(result.allReasonCodes()).contains("EXCEEDS_LIMIT");
        }
    }

    @Nested
    class CombinedScoring {

        @Test
        void shouldReviewWhenMultipleFlagsExceedReviewThreshold() {
            // After hours (10) + velocity exceeded (30) = 40 > review threshold (30)
            dataProvider.setVelocityCount("card-abc", 5);

            FraudContext ctx = baseContext()
                    .cardHash("card-abc")
                    .transactionTime(afterHoursTime())
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
            assertThat(result.totalScore()).isEqualTo(40);
        }

        @Test
        void shouldBlockWhenCombinedScoreExceedsBlockThreshold() {
            // After hours (10) + velocity (30) + over limit (25) + location anomaly (35) = 100
            dataProvider.setVelocityCount("card-xyz", 5);
            dataProvider.setTransactionLimit("account-123", new BigDecimal("50.00"));
            dataProvider.setLocationAnomalyScore(70);

            FraudContext ctx = baseContext()
                    .cardHash("card-xyz")
                    .transactionTime(afterHoursTime())
                    .amount(new BigDecimal("100.00"))
                    .lastKnownIpAddresses(Set.of("5.6.7.8"))
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.totalScore()).isEqualTo(100);
        }

        @Test
        void shouldSelectHighestScoringGateAsPrimaryReason() {
            // Velocity (30) vs after hours (10) - velocity should be primary
            dataProvider.setVelocityCount("card-123", 5);

            FraudContext ctx = baseContext()
                    .cardHash("card-123")
                    .transactionTime(afterHoursTime())
                    .build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.primaryReasonCode()).isEqualTo("VELOCITY_EXCEEDED");
        }
    }

    @Nested
    class EvaluationMetadata {

        @Test
        void shouldRecordEvaluationTime() {
            FraudContext ctx = baseContext().build();

            FraudResult result = evaluator.evaluate(ctx);

            assertThat(result.evaluationTime()).isNotNull();
            assertThat(result.evaluationTime().toNanos()).isPositive();
        }

        @Test
        void shouldIncludeAllGateResultsWhenNoHardBlock() {
            FraudContext ctx = baseContext().build();

            FraudResult result = evaluator.evaluate(ctx);

            // All 6 gates should run
            assertThat(result.gateResults()).hasSize(6);
        }
    }

    /**
     * Simple test implementation of FraudDataProvider
     */
    static class TestDataProvider extends AbstractFraudDataProvider {
        private final java.util.Set<String> blacklistedAccounts = new java.util.HashSet<>();
        private final java.util.Set<String> rateLimitedIps = new java.util.HashSet<>();
        private final java.util.Map<String, Integer> velocityCounts = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> transactionLimits = new java.util.HashMap<>();
        private int fixedLocationAnomalyScore = 0;

        TestDataProvider(GeoIpService geoIpService) {
            super(geoIpService);
        }

        void blacklist(String account) {
            blacklistedAccounts.add(account);
        }

        void rateLimit(String ip) {
            rateLimitedIps.add(ip);
        }

        void setVelocityCount(String cardHash, int count) {
            velocityCounts.put(cardHash, count);
        }

        void setTransactionLimit(String account, BigDecimal limit) {
            transactionLimits.put(account, limit);
        }

        void setLocationAnomalyScore(int score) {
            this.fixedLocationAnomalyScore = score;
        }

        @Override
        public boolean isBlacklisted(String accountIdentifier) {
            return blacklistedAccounts.contains(accountIdentifier);
        }

        @Override
        public boolean isRateLimited(String ipAddress) {
            return rateLimitedIps.contains(ipAddress);
        }

        @Override
        public int getVelocityCount(String cardHash, Duration window) {
            return velocityCounts.getOrDefault(cardHash, 0);
        }

        @Override
        public Optional<BigDecimal> getTransactionLimit(String accountIdentifier) {
            return Optional.ofNullable(transactionLimits.get(accountIdentifier));
        }

        @Override
        public int getLocationAnomalyScore(String ipAddress, Set<String> lastKnownIpAddresses) {
            if (lastKnownIpAddresses == null || lastKnownIpAddresses.isEmpty()) {
                return 0;
            }
            return fixedLocationAnomalyScore;
        }
    }
}
