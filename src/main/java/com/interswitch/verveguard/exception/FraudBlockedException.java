package com.interswitch.verveguard.exception;

import com.interswitch.verveguard.api.model.FraudResult;
import lombok.Getter;

@Getter
public class FraudBlockedException extends RuntimeException {

    private final FraudResult result;

    public FraudBlockedException(FraudResult result) {
        super("Transaction blocked: " + result.primaryReasonCode());
        this.result = result;
    }
}