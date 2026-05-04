# Verveguard

Spring Boot fraud detection library for payment/transaction systems.

## Installation

```xml
<dependency>
    <groupId>com.interswitch</groupId>
    <artifactId>verveguard</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@Autowired
private FraudEvaluator fraudEvaluator;

FraudContext ctx = FraudContext.builder()
    .transactionId("txn-123")
    .accountIdentifier("acc-456")
    .cardHash("hash-789")
    .ipAddress("203.0.113.45")
    .amount(new BigDecimal("1000.00"))
    .currency("NGN")
    .transactionTime(Instant.now())
    .lastKnownIpAddresses(Set.of("203.0.113.44", "198.51.100.1"))
    .build();

FraudResult result = fraudEvaluator.evaluate(ctx);

switch (result.decision()) {
    case ALLOW -> proceed();
    case REVIEW -> flagForReview(result.primaryReasonCode());
    case BLOCK -> reject(result.primaryReasonCode());
}
```

## Gates

| Gate | Type | Order | Score | What it does |
|------|------|-------|-------|--------------|
| `BlacklistGate` | Hard-block | 1 | 100 | Blocks blacklisted accounts |
| `RateLimitGate` | Hard-block | 2 | 100 | Blocks rate-limited IPs |
| `LocationAnomalyGate` | Soft | 4 | 35 | Flags impossible travel |
| `VelocityGate` | Soft | 10 | 30 | Flags rapid transactions |
| `TransactionLimitGate` | Soft | 11 | 25 | Flags over-limit amounts |
| `TimeWindowGate` | Soft | 20 | 10 | Flags after-hours (outside 6am-10pm) |

**Hard-block** = immediate `BLOCK`, skips remaining gates.
**Soft** = accumulates points toward threshold.

## Decision Thresholds

| Score | Decision |
|-------|----------|
| < 30 | `ALLOW` |
| 30-69 | `REVIEW` |
| >= 70 | `BLOCK` |

## Configuration

```yaml
verveguard:
  enabled: true
  block-threshold: 70
  review-threshold: 30

  blacklist.enabled: true
  rate-limit.enabled: true

  velocity:
    enabled: true
    threshold: 3
    window-seconds: 60
    score: 30

  transaction-limit:
    enabled: true
    score: 25

  time-window:
    enabled: true
    start-hour: 6
    end-hour: 22
    score: 10

  location-anomaly:
    enabled: true
    anomaly-threshold: 60
    score: 35

  geo-ip:
    database-path: /path/to/GeoLite2-City.mmdb
```

## Implement FraudDataProvider

**Required** — the default does nothing:

```java
@Bean
public FraudDataProvider fraudDataProvider(GeoIpService geoIpService) {
    return new AbstractFraudDataProvider(geoIpService) {
        @Override
        public boolean isBlacklisted(String accountId) {
            return blacklistRepo.exists(accountId);
        }

        @Override
        public boolean isRateLimited(String ip) {
            return rateLimiter.isLimited(ip);
        }

        @Override
        public int getVelocityCount(String cardHash, Duration window) {
            return txnRepo.countSince(cardHash, Instant.now().minus(window));
        }

        @Override
        public Optional<BigDecimal> getTransactionLimit(String accountId) {
            return accountRepo.findLimit(accountId);
        }
    };
}
```

## GeoIP Setup

Download [GeoLite2-City.mmdb](https://dev.maxmind.com/geoip/geolite2-free-geolite2-city/) and configure:

```yaml
verveguard:
  geo-ip:
    database-path: /opt/geoip/GeoLite2-City.mmdb
```

## Custom Gate

```java
@Component
public class DeviceFingerprintGate implements FraudGate {
    public String getName() { return "DEVICE_FINGERPRINT"; }
    public int getOrder() { return 5; }
    public boolean isHardBlockCapable() { return false; }

    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        if (isNewDevice(ctx)) {
            return GateResult.flag(getName(), 15, "NEW_DEVICE", "Unrecognized device");
        }
        return GateResult.pass(getName());
    }
}
```

## Gotchas

1. **DefaultFraudDataProvider is a no-op** — logs warnings and returns safe defaults. You must override the abstract methods.

2. **GeoLite2-City.mmdb not included** — download and provide your own. Location checks return 0 if missing.

3. **Gate order matters** — sorted by `getOrder()`, then alphabetically by `getName()`. Hard-block gates run first.

4. **TimeWindowGate uses system timezone** — `ZoneId.systemDefault()`. Adjust `start-hour`/`end-hour` if server timezone differs from users.

5. **LocationAnomalyGate needs history** — populate `lastKnownIpAddresses` in `FraudContext`. Returns 0 if null/empty.

6. **Scores accumulate** — velocity (30) + after-hours (10) + over-limit (25) = 65 → `REVIEW`.

7. **Disable the whole library**:
   ```yaml
   verveguard:
     enabled: false
   ```

## Location Anomaly Scoring

| Condition | Points |
|-----------|--------|
| Different country | +25 |
| Distance > 900km | +35 |
| Distance > 500km | +20 |
| Distance > 100km | +10 |

Score capped at 100. Flagged if >= `anomaly-threshold` (default 60).
