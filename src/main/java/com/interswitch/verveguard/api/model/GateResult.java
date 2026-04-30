package com.interswitch.verveguard.api.model;

public record GateResult(
        String gateName,
        int score,
        String reasonCode,
        String reasonDetail,
        boolean hardBlock
) {
    public static GateResult pass(String gateName) {
        return new GateResult(gateName, 0, null, null, false);
    }

    public static GateResult flag(String gateName, int score, String code, String detail) {
        return new GateResult(gateName, score, code, detail, false);
    }

    public static GateResult block(String gateName, String code, String detail) {
        return new GateResult(gateName, 100, code, detail, true);
    }

    public static GateResult block(String gateName, int score, String code, String detail) {
        return new GateResult(gateName, score, code, detail, true);
    }
}
