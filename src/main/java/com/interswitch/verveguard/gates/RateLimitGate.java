package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.GateResult;

public class RateLimitGate implements FraudGate {

    public String getName() { return "RATE_LIMIT"; }
    public int getOrder() { return 2; }
    public boolean isHardBlockCapable() { return true; }

    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        if (data.isRateLimited(ctx.ipAddress())) {
            return GateResult.block(getName(), "RATE_LIMITED",
                "IP " + ctx.ipAddress() + " exceeded rate limit");
        }
        return GateResult.pass(getName());
    }
}