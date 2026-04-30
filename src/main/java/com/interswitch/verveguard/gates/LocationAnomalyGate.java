package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.GateResult;
import lombok.RequiredArgsConstructor;

/**
 * LocationAnomalyGate evaluates suspicious geographic patterns.
 * Uses the user's last known IP addresses to detect impossible travel
 * and location anomalies that indicate account compromise.
 */
@RequiredArgsConstructor
public class LocationAnomalyGate implements FraudGate {

    private final int anomalyThreshold;  // Score threshold (0-100) to flag as suspicious
    private final int flagScore;         // Score to assign when anomaly detected

    public String getName() {
        return "LOCATION_ANOMALY";
    }

    public int getOrder() {
        return 4;  // Runs after hardblock gates (blacklist, rate limit) but before others
    }

    public boolean isHardBlockCapable() {
        return false;  // Location anomalies are suspicious but not definitive blocks
    }

    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        // Skip if we don't have location history
        if (ctx.lastKnownIpAddresses() == null || ctx.lastKnownIpAddresses().isEmpty()) {
            return GateResult.pass(getName());
        }

        int anomalyScore = data.getLocationAnomalyScore(ctx.ipAddress(), ctx.lastKnownIpAddresses());

        if (anomalyScore >= anomalyThreshold) {
            String detail = String.format(
                "Suspicious location pattern detected. Anomaly score: %d/100. Current IP: %s",
                anomalyScore,
                ctx.ipAddress()
            );
            return GateResult.flag(getName(), flagScore, "LOCATION_ANOMALY_DETECTED", detail);
        }

        return GateResult.pass(getName());
    }
}

