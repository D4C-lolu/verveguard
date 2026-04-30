package com.interswitch.verveguard.core.pipeline;

import com.interswitch.verveguard.api.*;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.FraudResult;
import com.interswitch.verveguard.api.model.GateResult;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
public class FraudPipeline implements FraudEvaluator {

    private final List<FraudGate> hardBlockGates;
    private final List<FraudGate> softGates;
    private final FraudDataProvider dataProvider;
    private final int blockThreshold;
    private final int reviewThreshold;

    public FraudPipeline(List<FraudGate> gates, FraudDataProvider dataProvider, int blockThreshold, int reviewThreshold) {
        // split the flat gate list into two ordered lists based on whether the gate can hard-block
        this.hardBlockGates = gates.stream()
                .filter(FraudGate::isHardBlockCapable)
                .sorted(Comparator.comparingInt(FraudGate::getOrder))
                .toList();
        this.softGates = gates.stream()
                .filter(g -> !g.isHardBlockCapable())
                .sorted(Comparator.comparingInt(FraudGate::getOrder))
                .toList();
        this.dataProvider = dataProvider;
        this.blockThreshold = blockThreshold;
        this.reviewThreshold = reviewThreshold;
    }

    @Override
    public FraudResult evaluate(FraudContext ctx) {
        long start = System.nanoTime();
        List<GateResult> results = new ArrayList<>();

        // 1. Run hard-block gates first (fail-fast)
        for (FraudGate gate : hardBlockGates) {
            GateResult result = gate.evaluate(ctx, dataProvider);
            results.add(result);
            if (result.hardBlock()) {
                return buildResult(FraudDecision.BLOCK, results, start);
            }
        }

        // 2. Run soft gates
        for (FraudGate gate : softGates) {
            results.add(gate.evaluate(ctx, dataProvider));
        }

        // 3. Calculate total score and decide
        int totalScore = results.stream().mapToInt(GateResult::score).sum();
        FraudDecision decision = decide(totalScore);

        return buildResult(decision, results, start);
    }

    private FraudDecision decide(int score) {
        if (score >= blockThreshold) return FraudDecision.BLOCK;
        if (score >= reviewThreshold) return FraudDecision.REVIEW;
        return FraudDecision.ALLOW;
    }

    private FraudResult buildResult(FraudDecision decision, List<GateResult> results, long start) {
        int totalScore = results.stream().mapToInt(GateResult::score).sum();

        GateResult primary = results.stream()
                .filter(GateResult::hardBlock)
                .findFirst()
                .orElseGet(() -> results.stream()
                        .max(Comparator.comparingInt(GateResult::score))
                        .orElse(null));

        String primaryReasonCode = primary != null ? primary.reasonCode() : null;
        String primaryReasonDetail = primary != null ? primary.reasonDetail() : null;

        Duration evaluationTime = Duration.ofNanos(System.nanoTime() - start);

        return new FraudResult(decision, totalScore, primaryReasonCode, primaryReasonDetail, results, evaluationTime);
    }
}