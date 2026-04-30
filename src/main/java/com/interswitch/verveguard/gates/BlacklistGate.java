package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.GateResult;

public class BlacklistGate implements FraudGate {

    public String getName() { return "BLACKLIST"; }

    public int getOrder() { return 1; }

    public boolean isHardBlockCapable() { return true; }

    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        if (data.isBlacklisted(ctx.accountIdentifier())) {
            return GateResult.block(getName(), "BLACKLISTED",
                "Account is blacklisted");
        }
        return GateResult.pass(getName());
    }
}