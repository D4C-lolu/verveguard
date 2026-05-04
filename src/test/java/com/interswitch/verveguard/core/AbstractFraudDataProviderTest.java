package com.interswitch.verveguard.core;

import com.interswitch.verveguard.api.GeoIpService;
import com.interswitch.verveguard.api.GeoIpService.LocationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbstractFraudDataProviderTest {

    private GeoIpService geoIpService;
    private TestFraudDataProvider provider;

    @BeforeEach
    void setUp() {
        geoIpService = mock(GeoIpService.class);
        provider = new TestFraudDataProvider(geoIpService);
    }

    static class TestFraudDataProvider extends AbstractFraudDataProvider {
        TestFraudDataProvider(GeoIpService geoIpService) {
            super(geoIpService);
        }

        @Override
        public boolean isBlacklisted(String accountIdentifier) {
            return false;
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
    }

    @Nested
    class LocationAnomalyScore {

        @Test
        void shouldReturnZeroWhenCurrentIpCannotBeResolved() {
            when(geoIpService.lookup("1.2.3.4")).thenReturn(null);

            int score = provider.getLocationAnomalyScore("1.2.3.4", Set.of("5.6.7.8"));

            assertThat(score).isZero();
        }

        @Test
        void shouldReturnZeroWhenHistoryIsEmpty() {
            LocationInfo currentLoc = new LocationInfo(51.5074, -0.1278, "GB");
            when(geoIpService.lookup("1.2.3.4")).thenReturn(currentLoc);

            int score = provider.getLocationAnomalyScore("1.2.3.4", Set.of());

            assertThat(score).isZero();
        }

        @Test
        void shouldReturnZeroWhenHistoricalIpCannotBeResolved() {
            LocationInfo currentLoc = new LocationInfo(51.5074, -0.1278, "GB");
            when(geoIpService.lookup("1.2.3.4")).thenReturn(currentLoc);
            when(geoIpService.lookup("5.6.7.8")).thenReturn(null);

            int score = provider.getLocationAnomalyScore("1.2.3.4", Set.of("5.6.7.8"));

            assertThat(score).isZero();
        }

        @Test
        void shouldSkipCurrentIpInHistory() {
            LocationInfo loc = new LocationInfo(51.5074, -0.1278, "GB");
            when(geoIpService.lookup("1.2.3.4")).thenReturn(loc);

            Set<String> history = new HashSet<>();
            history.add("1.2.3.4");

            int score = provider.getLocationAnomalyScore("1.2.3.4", history);

            assertThat(score).isZero();
            verify(geoIpService, times(1)).lookup("1.2.3.4");
        }
    }

    @Nested
    class CountryMismatch {

        @Test
        void shouldAdd25PointsForDifferentCountry() {
            // London, UK
            LocationInfo currentLoc = new LocationInfo(51.5074, -0.1278, "GB");
            // New York, US
            LocationInfo historicalLoc = new LocationInfo(40.7128, -74.0060, "US");

            when(geoIpService.lookup("current-ip")).thenReturn(currentLoc);
            when(geoIpService.lookup("historical-ip")).thenReturn(historicalLoc);

            Set<String> history = new HashSet<>();
            history.add("historical-ip");

            int score = provider.getLocationAnomalyScore("current-ip", history);

            // 25 (country mismatch) + 35 (>1000km distance) = 60
            assertThat(score).isGreaterThanOrEqualTo(25);
        }

        @Test
        void shouldNotAddCountryPointsForSameCountry() {
            LocationInfo currentLoc = new LocationInfo(51.5074, -0.1278, "GB");
            LocationInfo historicalLoc = new LocationInfo(51.4545, -0.9781, "GB");

            when(geoIpService.lookup("current-ip")).thenReturn(currentLoc);
            when(geoIpService.lookup("historical-ip")).thenReturn(historicalLoc);

            Set<String> history = new HashSet<>();
            history.add("historical-ip");

            int score = provider.getLocationAnomalyScore("current-ip", history);

            assertThat(score).isZero();
        }
    }

    @Nested
    class DistanceScoring {

        @Test
        void shouldReturnZeroForCloseLocations() {
            LocationInfo london = new LocationInfo(51.5074, -0.1278, "GB");
            LocationInfo reading = new LocationInfo(51.4545, -0.9781, "GB");

            when(geoIpService.lookup("london-ip")).thenReturn(london);
            when(geoIpService.lookup("reading-ip")).thenReturn(reading);

            Set<String> history = new HashSet<>();
            history.add("reading-ip");

            int score = provider.getLocationAnomalyScore("london-ip", history);

            assertThat(score).isZero();
        }

        @Test
        void shouldAdd10PointsForModerateDistance() {
            LocationInfo london = new LocationInfo(51.5074, -0.1278, "GB");
            LocationInfo manchester = new LocationInfo(53.4808, -2.2426, "GB");

            when(geoIpService.lookup("london-ip")).thenReturn(london);
            when(geoIpService.lookup("manchester-ip")).thenReturn(manchester);

            Set<String> history = new HashSet<>();
            history.add("manchester-ip");

            int score = provider.getLocationAnomalyScore("london-ip", history);

            assertThat(score).isEqualTo(10);
        }

        @Test
        void shouldAdd20PointsForLongDistance() {
            LocationInfo london = new LocationInfo(51.5074, -0.1278, "GB");
            LocationInfo edinburgh = new LocationInfo(55.9533, -3.1883, "GB");

            when(geoIpService.lookup("london-ip")).thenReturn(london);
            when(geoIpService.lookup("edinburgh-ip")).thenReturn(edinburgh);

            Set<String> history = new HashSet<>();
            history.add("edinburgh-ip");

            int score = provider.getLocationAnomalyScore("london-ip", history);

            assertThat(score).isEqualTo(20);
        }

        @Test
        void shouldAdd35PointsForImpossibleTravel() {
            LocationInfo london = new LocationInfo(51.5074, -0.1278, "GB");
            LocationInfo newYork = new LocationInfo(40.7128, -74.0060, "US");

            when(geoIpService.lookup("london-ip")).thenReturn(london);
            when(geoIpService.lookup("ny-ip")).thenReturn(newYork);

            Set<String> history = new HashSet<>();
            history.add("ny-ip");

            int score = provider.getLocationAnomalyScore("london-ip", history);

            assertThat(score).isEqualTo(60);
        }
    }

    @Nested
    class MultipleHistoricalIps {

        @Test
        void shouldAccumulateScoresFromMultipleIps() {
            LocationInfo current = new LocationInfo(51.5074, -0.1278, "GB");
            LocationInfo hist1 = new LocationInfo(40.7128, -74.0060, "US");
            LocationInfo hist2 = new LocationInfo(35.6762, 139.6503, "JP");

            when(geoIpService.lookup("current")).thenReturn(current);
            when(geoIpService.lookup("ny")).thenReturn(hist1);
            when(geoIpService.lookup("tokyo")).thenReturn(hist2);

            Set<String> history = new HashSet<>();
            history.add("ny");
            history.add("tokyo");

            int score = provider.getLocationAnomalyScore("current", history);

            assertThat(score).isEqualTo(100);
        }

        @Test
        void shouldCapScoreAt100() {
            LocationInfo current = new LocationInfo(51.5074, -0.1278, "GB");

            when(geoIpService.lookup("current")).thenReturn(current);
            when(geoIpService.lookup("ip1")).thenReturn(new LocationInfo(40.7128, -74.0060, "US"));
            when(geoIpService.lookup("ip2")).thenReturn(new LocationInfo(35.6762, 139.6503, "JP"));
            when(geoIpService.lookup("ip3")).thenReturn(new LocationInfo(-33.8688, 151.2093, "AU"));

            Set<String> history = new HashSet<>();
            history.add("ip1");
            history.add("ip2");
            history.add("ip3");

            int score = provider.getLocationAnomalyScore("current", history);

            assertThat(score).isEqualTo(100);
        }

        @Test
        void shouldStopProcessingWhenScoreReaches100() {
            LocationInfo current = new LocationInfo(51.5074, -0.1278, "GB");

            lenient().when(geoIpService.lookup("current")).thenReturn(current);
            lenient().when(geoIpService.lookup("ip1")).thenReturn(new LocationInfo(40.7128, -74.0060, "US"));
            lenient().when(geoIpService.lookup("ip2")).thenReturn(new LocationInfo(35.6762, 139.6503, "JP"));

            Set<String> history = new HashSet<>();
            history.add("ip1");
            history.add("ip2");

            int score = provider.getLocationAnomalyScore("current", history);

            assertThat(score).isEqualTo(100);
        }
    }

    @Nested
    class Caching {

        @Test
        void shouldCacheLocationLookups() {
            LocationInfo loc = new LocationInfo(51.5074, -0.1278, "GB");
            when(geoIpService.lookup("1.2.3.4")).thenReturn(loc);

            Set<String> history1 = new HashSet<>();
            history1.add("1.2.3.4");
            Set<String> history2 = new HashSet<>();
            history2.add("1.2.3.4");

            provider.getLocationAnomalyScore("1.2.3.4", history1);
            provider.getLocationAnomalyScore("1.2.3.4", history2);

            verify(geoIpService, times(1)).lookup("1.2.3.4");
        }

        @Test
        void shouldReuseCurrentIpLookupForHistoricalCheck() {
            LocationInfo loc = new LocationInfo(51.5074, -0.1278, "GB");
            when(geoIpService.lookup("same-ip")).thenReturn(loc);

            Set<String> history = new HashSet<>();
            history.add("same-ip");

            provider.getLocationAnomalyScore("same-ip", history);

            verify(geoIpService, times(1)).lookup("same-ip");
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldReturnZeroOnException() {
            when(geoIpService.lookup(any())).thenThrow(new RuntimeException("Database error"));

            int score = provider.getLocationAnomalyScore("1.2.3.4", Set.of("5.6.7.8"));

            assertThat(score).isZero();
        }
    }
}