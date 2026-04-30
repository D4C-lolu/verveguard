package com.interswitch.verveguard.api;

public interface FraudEvaluator {
    FraudResult evaluate(FraudContext context);
}