package com.interswitch.verveguard.core.service;

import com.interswitch.verveguard.api.GeoIpService.LocationInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for MaxMindGeoIpService.
 * These tests require GeoLite2-City.mmdb in src/test/resources.
 * Tests are skipped if the database file is not present.
 */
class MaxMindGeoIpServiceTest {

    private static final String TEST_DB_PATH = "src/test/resources/GeoLite2-City.mmdb";
    private static MaxMindGeoIpService service;
    private static boolean databaseAvailable;

    @BeforeAll
    static void setUp() {
        databaseAvailable = new File(TEST_DB_PATH).exists();
        if (databaseAvailable) {
            try {
                service = new MaxMindGeoIpService(TEST_DB_PATH);
            } catch (IOException e) {
                databaseAvailable = false;
            }
        }
    }

    static boolean isDatabaseAvailable() {
        return databaseAvailable;
    }

    @Test
    void shouldThrowWhenDatabaseNotFound() {
        assertThatThrownBy(() -> new MaxMindGeoIpService("/nonexistent/path.mmdb"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @EnabledIf("isDatabaseAvailable")
    void shouldLookupKnownPublicIp() {
        // Google DNS - should resolve to US
        LocationInfo result = service.lookup("8.8.8.8");

        assertThat(result).isNotNull();
        assertThat(result.country()).isEqualTo("US");
        assertThat(result.latitude()).isNotNull();
        assertThat(result.longitude()).isNotNull();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @EnabledIf("isDatabaseAvailable")
    void shouldReturnNullForPrivateIp() {
        // Private IP addresses are not in the GeoIP database
        LocationInfo result = service.lookup("192.168.1.1");

        assertThat(result).isNull();
    }

    @Test
    @EnabledIf("isDatabaseAvailable")
    void shouldReturnNullForLocalhostIp() {
        LocationInfo result = service.lookup("127.0.0.1");

        assertThat(result).isNull();
    }

    @Test
    @EnabledIf("isDatabaseAvailable")
    void shouldReturnNullForInvalidIp() {
        LocationInfo result = service.lookup("not.an.ip.address");

        assertThat(result).isNull();
    }

    @Test
    @EnabledIf("isDatabaseAvailable")
    void shouldLookupCloudflareIp() {
        // Cloudflare DNS - should have valid location if present in database
        LocationInfo result = service.lookup("1.1.1.1");

        if (result != null) {
            assertThat(result.isValid()).isTrue();
        }
    }

    @Test
    @EnabledIf("isDatabaseAvailable")
    void shouldHandleIpv6Address() {
        // Google's IPv6 DNS
        LocationInfo result = service.lookup("2001:4860:4860::8888");

        // May or may not be in database depending on version
        if (result != null) {
            assertThat(result.isValid()).isTrue();
        }
    }
}
