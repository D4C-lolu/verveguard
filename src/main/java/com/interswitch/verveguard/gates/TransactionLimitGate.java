package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.GateResult;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionLimitGate implements FraudGate {

    private final int score;

    public String getName() { return "TRANSACTION_LIMIT"; }
    public int getOrder() { return 11; }
    public boolean isHardBlockCapable() { return false; }

    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        return data.getTransactionLimit(ctx.accountIdentifier())
            .filter(limit -> ctx.amount().compareTo(limit) > 0)
            .map(limit -> GateResult.flag(getName(), score, "EXCEEDS_LIMIT",
                "Amount " + ctx.amount() + " exceeds limit " + limit))
            .orElse(GateResult.pass(getName()));
    }
}