package com.interswitch.verveguard.api;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.GateResult;

public interface FraudGate {
    String getName();
    int getOrder();                    // Lower = runs first
    boolean isHardBlockCapable();      // Can this gate hard-block?
    GateResult evaluate(FraudContext ctx, FraudDataProvider data);
}