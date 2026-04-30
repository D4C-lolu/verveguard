package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.GateResult;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@RequiredArgsConstructor
public class VelocityGate implements FraudGate {

    private final int threshold;
    private final Duration window;
    private final int score;

    public String getName() { return "VELOCITY"; }

    public int getOrder() { return 10; }

    public boolean isHardBlockCapable() { return false; }

    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        int count = data.getVelocityCount(ctx.cardHash(), window);
        if (count >= threshold) {
            return GateResult.flag(getName(), score, "VELOCITY_EXCEEDED",
                count + " transactions in " + window.toSeconds() + "s");
        }
        return GateResult.pass(getName());
    }
}