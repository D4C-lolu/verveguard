package com.interswitch.verveguard.gates;

import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.GateResult;
import lombok.RequiredArgsConstructor;

import java.time.ZoneId;

@RequiredArgsConstructor
public class TimeWindowGate implements FraudGate {

    private final int startHour;
    private final int endHour;
    private final int score;

    public String getName() { return "TIME_WINDOW"; }

    public int getOrder() { return 20; }

    public boolean isHardBlockCapable() { return false; }

    @Override
    public GateResult evaluate(FraudContext ctx, FraudDataProvider data) {
        int hour = ctx.transactionTime().atZone(ZoneId.systemDefault()).getHour();
        if (hour < startHour || hour >= endHour) {
            return GateResult.flag(getName(), score, "AFTER_HOURS",
                "Transaction at hour " + hour + " outside " + startHour + "-" + endHour);
        }
        return GateResult.pass(getName());
    }
}