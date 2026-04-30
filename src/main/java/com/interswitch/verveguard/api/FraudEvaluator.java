package com.interswitch.verveguard.api;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.FraudResult;

public interface FraudEvaluator {
    FraudResult evaluate(FraudContext context);
}