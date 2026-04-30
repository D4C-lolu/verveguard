package com.interswitch.verveguard.api;

public interface FraudGate {
    String getName();
    int getOrder();                    // Lower = runs first
    boolean isHardBlockCapable();      // Can this gate hard-block?
    GateResult evaluate(FraudContext ctx, FraudDataProvider data);
}