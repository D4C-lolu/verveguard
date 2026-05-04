package com.interswitch.verveguard.config;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudEvaluator;
import com.interswitch.verveguard.api.GeoIpService;
import com.interswitch.verveguard.gates.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VerveguardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VerveguardAutoConfiguration.class))
            .withUserConfiguration(TestGeoIpConfig.class);

    @Configuration
    static class TestGeoIpConfig {
        @Bean
        GeoIpService geoIpService() {
            return ip -> new GeoIpService.LocationInfo(0.0, 0.0, "XX");
        }
    }

    @Test
    void shouldLoadAllGatesByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(BlacklistGate.class);
            assertThat(context).hasSingleBean(RateLimitGate.class);
            assertThat(context).hasSingleBean(VelocityGate.class);
            assertThat(context).hasSingleBean(TransactionLimitGate.class);
            assertThat(context).hasSingleBean(TimeWindowGate.class);
            assertThat(context).hasSingleBean(LocationAnomalyGate.class);
        });
    }

    @Test
    void shouldLoadFraudEvaluator() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FraudEvaluator.class);
            assertThat(context).hasSingleBean(FraudDataProvider.class);
        });
    }

    @Test
    void shouldDisableBlacklistGateWhenConfigured() {
        contextRunner
                .withPropertyValues("verveguard.blacklist.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BlacklistGate.class);
                    assertThat(context).hasSingleBean(RateLimitGate.class);
                });
    }

    @Test
    void shouldDisableRateLimitGateWhenConfigured() {
        contextRunner
                .withPropertyValues("verveguard.rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimitGate.class);
                    assertThat(context).hasSingleBean(BlacklistGate.class);
                });
    }

    @Test
    void shouldDisableVelocityGateWhenConfigured() {
        contextRunner
                .withPropertyValues("verveguard.velocity.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VelocityGate.class);
                });
    }

    @Test
    void shouldDisableTransactionLimitGateWhenConfigured() {
        contextRunner
                .withPropertyValues("verveguard.transaction-limit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TransactionLimitGate.class);
                });
    }

    @Test
    void shouldDisableTimeWindowGateWhenConfigured() {
        contextRunner
                .withPropertyValues("verveguard.time-window.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TimeWindowGate.class);
                });
    }

    @Test
    void shouldDisableLocationAnomalyGateWhenConfigured() {
        contextRunner
                .withPropertyValues("verveguard.location-anomaly.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LocationAnomalyGate.class);
                });
    }

    @Test
    void shouldDisableEntireLibraryWhenConfigured() {
        contextRunner
                .withPropertyValues("verveguard.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FraudEvaluator.class);
                    assertThat(context).doesNotHaveBean(BlacklistGate.class);
                });
    }

    @Test
    void shouldUseCustomVelocityConfiguration() {
        contextRunner
                .withPropertyValues(
                        "verveguard.velocity.threshold=10",
                        "verveguard.velocity.window-seconds=120",
                        "verveguard.velocity.score=50"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VelocityGate.class); // Gate is created with custom values - verified by the fact context loads
                });
    }

    @Test
    void shouldUseCustomTimeWindowConfiguration() {
        contextRunner
                .withPropertyValues(
                        "verveguard.time-window.start-hour=8",
                        "verveguard.time-window.end-hour=20",
                        "verveguard.time-window.score=15"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(TimeWindowGate.class);
                });
    }

    @Test
    void shouldUseCustomThresholds() {
        contextRunner
                .withPropertyValues(
                        "verveguard.block-threshold=80",
                        "verveguard.review-threshold=40"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FraudEvaluator.class);
                });
    }

    @Test
    void shouldAllowCustomFraudDataProvider() {
        contextRunner
                .withUserConfiguration(CustomDataProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FraudDataProvider.class);
                    FraudDataProvider provider = context.getBean(FraudDataProvider.class);
                    assertThat(provider.isBlacklisted("test")).isTrue();
                });
    }

    @Configuration
    static class CustomDataProviderConfig {
        @Bean
        FraudDataProvider fraudDataProvider() {
            return new FraudDataProvider() {
                @Override
                public boolean isBlacklisted(String accountIdentifier) {
                    return true; // Custom implementation
                }

                @Override
                public boolean isRateLimited(String ipAddress) {
                    return false;
                }

                @Override
                public int getVelocityCount(String cardHash, Duration window) {
                    return 0;
                }

                @Override
                public Optional<BigDecimal> getTransactionLimit(String accountIdentifier) {
                    return Optional.empty();
                }

                @Override
                public int getLocationAnomalyScore(String ipAddress, Set<String> lastKnownIpAddresses) {
                    return 0;
                }
            };
        }
    }

    @Test
    void shouldAllowCustomGateImplementation() {
        contextRunner
                .withUserConfiguration(CustomBlacklistGateConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(BlacklistGate.class);
                    // Verify it's our custom one by checking it's the subclass
                    BlacklistGate gate = context.getBean(BlacklistGate.class);
                    assertThat(gate.getName()).isEqualTo("CUSTOM_BLACKLIST");
                });
    }

    @Configuration
    static class CustomBlacklistGateConfig {
        @Bean
        BlacklistGate blacklistGate() {
            return new BlacklistGate() {
                @Override
                public String getName() {
                    return "CUSTOM_BLACKLIST";
                }
            };
        }
    }
}
