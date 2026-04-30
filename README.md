# Verveguard - Fraud Detection Library

A Spring Boot auto-configurable fraud detection library providing comprehensive fraud gate implementations including location anomaly detection.

## Features

- **Multiple Fraud Gates**: Blacklist, Rate Limit, Velocity, Transaction Limit, Time Window, and Location Anomaly
- **Location Anomaly Detection**: Detects impossible travel patterns using offline GeoIP lookups
- **Zero Network Latency**: All fraud checks use local caches and databases
- **Configurable Thresholds**: Control block and review thresholds
- **Spring Boot Starter**: Auto-configuration with sensible defaults

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.interswitch</groupId>
    <artifactId>verveguard</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Configure Application

```properties
verveguard.enabled=true
verveguard.block-threshold=70
verveguard.review-threshold=30

# Location Anomaly Gate
verveguard.location-anomaly.enabled=true
verveguard.location-anomaly.anomaly-threshold=60
verveguard.location-anomaly.score=35
```

### 3. Implement FraudDataProvider

Extend `DefaultFraudDataProvider` and implement application-specific methods:

```java
@Bean
public FraudDataProvider fraudDataProvider(GeoIpService geoIpService) {
    return new DefaultFraudDataProvider(geoIpService) {
        @Override
        public boolean isBlacklisted(String accountId) {
            return myDatabase.isBlacklisted(accountId);
        }
        
        @Override
        public boolean isRateLimited(String ipAddress) {
            return myRateLimiter.isLimited(ipAddress);
        }
        
        // Implement other methods...
    };
}
```

## GeoIP Database Setup

Verveguard uses MaxMind's GeoLite2-City database for offline IP geolocation lookups.

### Automatic Download

On first application startup, the `GeoIpDatabaseInitializer` component automatically:
1. Checks if the database exists in classpath or local cache
2. **Downloads GeoLite2-City.mmdb from MaxMind CDN** if not found
3. Caches the database at `/var/lib/verveguard/GeoLite2-City.mmdb` (configurable)
4. Reuses the cached database on subsequent startups

**No manual download required!** The database is downloaded automatically on first run.

### Database Location

The GeoIP database is stored at:
- **Linux/Docker**: `/var/lib/verveguard/GeoLite2-City.mmdb`
- **Windows**: `C:\Users\<username>\AppData\Local\verveguard\GeoLite2-City.mmdb`
- **Custom path**: Override via configuration property

### Configuration

```properties
# Optional: specify custom database path
# verveguard.geoip.database-path=/custom/path/to/GeoLite2-City.mmdb

# If database path not specified, defaults to:
# /var/lib/verveguard/GeoLite2-City.mmdb
```

### Docker Usage

For Docker deployments, mount a persistent volume for the database:

```dockerfile
FROM openjdk:17-slim
COPY target/verveguard-app.jar app.jar
VOLUME /var/lib/verveguard
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker run -v geoip-data:/var/lib/verveguard verveguard-app:latest
```

**Benefits:**
- First container run: Downloads and caches the database
- Subsequent runs: Reuse the cached database
- Volume persists across container restarts
- Multiple container instances share the same cached database

### Classpath Packaging (Alternative)

If you prefer to bundle the database in your JAR:

1. Download `GeoLite2-City.mmdb` from [MaxMind](https://dev.maxmind.com/geoip/geolite2-free-geolite2-city/)
2. Place in `src/main/resources/GeoLite2-City.mmdb`
3. Database will be loaded from classpath automatically

**Trade-off**: JAR size increases by ~50-100MB

## Location Anomaly Gate

The `LocationAnomalyGate` detects suspicious geographical patterns:

### How It Works

1. **Requires last 5 IP addresses** in `FraudContext.lastKnownIpAddresses()`
2. **Calculates distance** between current and historical IP locations using Haversine formula
3. **Assigns anomaly score** (0-100):
   - Different country: +25 points
   - Distance > 900km (impossible travel): +35 points
   - Distance > 500km: +20 points
   - Distance > 100km: +10 points
4. **Flags transaction** if score >= threshold (default: 60)

### Configuration

```properties
# Enable/disable location anomaly detection
verveguard.location-anomaly.enabled=true

# Anomaly score threshold (0-100) for flagging
verveguard.location-anomaly.anomaly-threshold=60

# Score to assign when anomaly is detected
verveguard.location-anomaly.score=35
```

### Example FraudContext

```java
FraudContext ctx = FraudContext.builder()
    .transactionId("txn-123")
    .accountIdentifier("acc-456")
    .ipAddress("203.0.113.45")  // Current IP
    .lastKnownIpAddresses(Arrays.asList(
        "203.0.113.44",  // Yesterday
        "203.0.113.43",  // 2 days ago
        "198.51.100.1",  // 3 days ago (different country)
        "198.51.100.2",
        "198.51.100.3"
    ))
    .amount(BigDecimal.valueOf(1000))
    .currency("USD")
    .transactionTime(Instant.now())
    .metadata(Map.of("device", "mobile"))
    .build();

FraudDecision decision = fraudEvaluator.evaluate(ctx);
```

## Architecture

### Gate Execution Order

Gates execute in order (lower `getOrder()` runs first):

1. **BlacklistGate** (order: 1) - Hard block if account is blacklisted
2. **RateLimitGate** (order: 2) - Hard block if IP is rate limited
3. **LocationAnomalyGate** (order: 4) - Flag suspicious location patterns
4. **VelocityGate** (order: 10) - Flag rapid transaction velocity
5. **TimeWindowGate** (order: 20) - Flag transactions outside normal hours
6. **TransactionLimitGate** (order: 30) - Flag unusually large transactions

### Fraud Scoring

- **Block Threshold** (default: 70): Transaction is hard-blocked
- **Review Threshold** (default: 30): Transaction flagged for manual review
- **Below 30**: Transaction approved

## Extension Points

### Custom Fraud Gates

Implement `FraudGate` interface:

```java
@Component
public class CustomGate implements FraudGate {
    @Override
    public String getName() { return "CUSTOM"; }
    
    @Override
    public int getOrder() { return 15; }
    
    @Override
    public boolean isHardBlockCapable() { return true; }
    
    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        // Custom logic
        return GateResult.pass(getName());
    }
}
```

### Custom FraudDataProvider

Override methods as needed:

```java
public class CustomFraudDataProvider extends DefaultFraudDataProvider {
    // Inherited: getLocationAnomalyScore() - fully implemented
    
    @Override
    public boolean isBlacklisted(String accountId) {
        // Custom blacklist logic
    }
    
    // Override other methods as needed
}
```

## Troubleshooting

### GeoIP Database Not Found

**Error**: `GeoLite2-City.mmdb not found in classpath`

**Solutions**:
1. **Wait for auto-download**: First startup automatically downloads the database
2. **Verify network access**: Ensure application can reach MaxMind CDN
3. **Check directory permissions**: `/var/lib/verveguard/` must be writable
4. **Manual placement**: Download from [MaxMind](https://dev.maxmind.com/geoip/geolite2-free-geolite2-city/) and place at configured path

### Location Anomaly Not Working

**Check**:
1. Gate is enabled: `verveguard.location-anomaly.enabled=true`
2. `FraudContext.lastKnownIpAddresses()` is populated with valid IPs
3. Valid IP addresses in list (not nulls or empty strings)

## Performance

- **Location lookups**: < 1ms (in-memory cache)
- **Distance calculations**: < 0.1ms per comparison
- **Full fraud evaluation**: < 10ms typical
- **Database file**: ~50-100MB (downloaded once, cached)
- **Memory cache**: 10,000 IP entries with 1-hour TTL

## License

Proprietary - Interswitch

