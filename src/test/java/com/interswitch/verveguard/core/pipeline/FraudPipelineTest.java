package com.interswitch.verveguard.core.pipeline;

import com.interswitch.verveguard.api.FraudDataProvider;
import com.interswitch.verveguard.api.FraudDecision;
import com.interswitch.verveguard.api.FraudGate;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.FraudResult;
import com.interswitch.verveguard.api.model.GateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudPipelineTest {

    @Mock
    private FraudDataProvider dataProvider;

    private FraudContext defaultContext;

    @BeforeEach
    void setUp() {
        defaultContext = FraudContext.builder()
                .transactionId("txn-123")
                .accountIdentifier("account-456")
                .build();
    }

    private FraudGate mockHardBlockGate(String name, int order, GateResult result) {
        FraudGate gate = mock(FraudGate.class);
        lenient().when(gate.getName()).thenReturn(name);
        lenient().when(gate.getOrder()).thenReturn(order);
        lenient().when(gate.isHardBlockCapable()).thenReturn(true);
        lenient().when(gate.evaluate(any(), any())).thenReturn(result);
        return gate;
    }

    private FraudGate mockSoftGate(String name, int order, GateResult result) {
        FraudGate gate = mock(FraudGate.class);
        lenient().when(gate.getName()).thenReturn(name);
        lenient().when(gate.getOrder()).thenReturn(order);
        lenient().when(gate.isHardBlockCapable()).thenReturn(false);
        lenient().when(gate.evaluate(any(), any())).thenReturn(result);
        return gate;
    }

    @Nested
    class HardBlockBehavior {

        @Test
        void shouldBlockImmediatelyOnHardBlock() {
            FraudGate blacklistGate = mockHardBlockGate("BLACKLIST", 1,
                    GateResult.block("BLACKLIST", "BLACKLISTED", "Account blacklisted"));
            FraudGate rateLimitGate = mockHardBlockGate("RATE_LIMIT", 2,
                    GateResult.pass("RATE_LIMIT"));
            FraudGate softGate = mockSoftGate("VELOCITY", 10,
                    GateResult.flag("VELOCITY", 30, "VELOCITY_EXCEEDED", "Too fast"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(blacklistGate, rateLimitGate, softGate),
                    dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.primaryReasonCode()).isEqualTo("BLACKLISTED");
            // Should not evaluate subsequent gates after hard-block
            verify(rateLimitGate, never()).evaluate(any(), any());
            verify(softGate, never()).evaluate(any(), any());
        }

        @Test
        void shouldShortCircuitOnSecondHardBlockGate() {
            FraudGate blacklistGate = mockHardBlockGate("BLACKLIST", 1,
                    GateResult.pass("BLACKLIST"));
            FraudGate rateLimitGate = mockHardBlockGate("RATE_LIMIT", 2,
                    GateResult.block("RATE_LIMIT", "RATE_LIMITED", "IP blocked"));
            FraudGate softGate = mockSoftGate("VELOCITY", 10,
                    GateResult.flag("VELOCITY", 50, "VELOCITY_EXCEEDED", "Fast"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(blacklistGate, rateLimitGate, softGate),
                    dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.primaryReasonCode()).isEqualTo("RATE_LIMITED");
            verify(blacklistGate).evaluate(any(), any());
            verify(rateLimitGate).evaluate(any(), any());
            verify(softGate, never()).evaluate(any(), any());
        }

        @Test
        void shouldRunAllHardBlockGatesBeforeSoftGatesWhenNoBlock() {
            FraudGate blacklistGate = mockHardBlockGate("BLACKLIST", 1,
                    GateResult.pass("BLACKLIST"));
            FraudGate rateLimitGate = mockHardBlockGate("RATE_LIMIT", 2,
                    GateResult.pass("RATE_LIMIT"));
            FraudGate softGate = mockSoftGate("VELOCITY", 10,
                    GateResult.pass("VELOCITY"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(blacklistGate, rateLimitGate, softGate),
                    dataProvider, 70, 30);

            pipeline.evaluate(defaultContext);

            verify(blacklistGate).evaluate(any(), any());
            verify(rateLimitGate).evaluate(any(), any());
            verify(softGate).evaluate(any(), any());
        }
    }

    @Nested
    class ScoreAggregation {

        @Test
        void shouldSumScoresFromAllSoftGates() {
            FraudGate gate1 = mockSoftGate("VELOCITY", 10,
                    GateResult.flag("VELOCITY", 25, "V1", "detail"));
            FraudGate gate2 = mockSoftGate("LIMIT", 11,
                    GateResult.flag("LIMIT", 20, "L1", "detail"));
            FraudGate gate3 = mockSoftGate("TIME", 20,
                    GateResult.flag("TIME", 15, "T1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate1, gate2, gate3),
                    dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.totalScore()).isEqualTo(60); // 25 + 20 + 15
        }

        @Test
        void shouldIncludePassingGatesWithZeroScore() {
            FraudGate passingGate = mockSoftGate("PASS", 10,
                    GateResult.pass("PASS"));
            FraudGate flaggingGate = mockSoftGate("FLAG", 11,
                    GateResult.flag("FLAG", 30, "F1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(passingGate, flaggingGate),
                    dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.totalScore()).isEqualTo(30);
            assertThat(result.gateResults()).hasSize(2);
        }

        @Test
        void shouldIncludeHardBlockGateScoresWhenNotBlocking() {
            FraudGate hardGate = mockHardBlockGate("BLACKLIST", 1,
                    GateResult.pass("BLACKLIST"));
            FraudGate softGate = mockSoftGate("VELOCITY", 10,
                    GateResult.flag("VELOCITY", 40, "V1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(hardGate, softGate),
                    dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.totalScore()).isEqualTo(40);
            assertThat(result.gateResults()).hasSize(2);
        }
    }

    @Nested
    class DecisionThresholds {

        @Test
        void shouldAllowWhenScoreBelowReviewThreshold() {
            FraudGate gate = mockSoftGate("GATE", 10,
                    GateResult.flag("GATE", 20, "G1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
            assertThat(result.isBlocked()).isFalse();
        }

        @Test
        void shouldReviewWhenScoreAtReviewThreshold() {
            FraudGate gate = mockSoftGate("GATE", 10,
                    GateResult.flag("GATE", 30, "G1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
        }

        @Test
        void shouldReviewWhenScoreBetweenThresholds() {
            FraudGate gate = mockSoftGate("GATE", 10,
                    GateResult.flag("GATE", 50, "G1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
        }

        @Test
        void shouldBlockWhenScoreAtBlockThreshold() {
            FraudGate gate = mockSoftGate("GATE", 10,
                    GateResult.flag("GATE", 70, "G1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.isBlocked()).isTrue();
        }

        @Test
        void shouldBlockWhenScoreAboveBlockThreshold() {
            FraudGate gate1 = mockSoftGate("G1", 10,
                    GateResult.flag("G1", 50, "CODE1", "detail"));
            FraudGate gate2 = mockSoftGate("G2", 11,
                    GateResult.flag("G2", 40, "CODE2", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate1, gate2), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.totalScore()).isEqualTo(90);
        }

        @Test
        void shouldAllowWithZeroScore() {
            FraudGate gate = mockSoftGate("GATE", 10,
                    GateResult.pass("GATE"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
            assertThat(result.totalScore()).isZero();
        }
    }

    @Nested
    class PrimaryReason {

        @Test
        void shouldUseHardBlockGateAsPrimaryReason() {
            FraudGate hardGate = mockHardBlockGate("BLACKLIST", 1,
                    GateResult.block("BLACKLIST", "BLOCKED", "Account blocked"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(hardGate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.primaryReasonCode()).isEqualTo("BLOCKED");
            assertThat(result.primaryReasonDetail()).isEqualTo("Account blocked");
        }

        @Test
        void shouldUseHighestScoringGateAsPrimaryReasonWhenNoHardBlock() {
            FraudGate lowScoreGate = mockSoftGate("LOW", 10,
                    GateResult.flag("LOW", 20, "LOW_CODE", "Low detail"));
            FraudGate highScoreGate = mockSoftGate("HIGH", 11,
                    GateResult.flag("HIGH", 50, "HIGH_CODE", "High detail"));
            FraudGate midScoreGate = mockSoftGate("MID", 12,
                    GateResult.flag("MID", 35, "MID_CODE", "Mid detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(lowScoreGate, highScoreGate, midScoreGate),
                    dataProvider, 200, 100);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.primaryReasonCode()).isEqualTo("HIGH_CODE");
            assertThat(result.primaryReasonDetail()).isEqualTo("High detail");
        }

        @Test
        void shouldHaveNullPrimaryReasonWhenAllGatesPass() {
            FraudGate gate = mockSoftGate("GATE", 10,
                    GateResult.pass("GATE"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.primaryReasonCode()).isNull();
            assertThat(result.primaryReasonDetail()).isNull();
        }
    }

    @Nested
    class GateOrdering {

        @Test
        void shouldRunHardBlockGatesInOrder() {
            FraudGate gate1 = mockHardBlockGate("FIRST", 5, GateResult.pass("FIRST"));
            FraudGate gate2 = mockHardBlockGate("SECOND", 1, GateResult.pass("SECOND"));
            FraudGate gate3 = mockHardBlockGate("THIRD", 10, GateResult.pass("THIRD"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate1, gate2, gate3), dataProvider, 70, 30);

            pipeline.evaluate(defaultContext);

            // Verify order by checking the gate results list
            // Gates should be sorted: SECOND (1), FIRST (5), THIRD (10)
            var inOrder = inOrder(gate2, gate1, gate3);
            inOrder.verify(gate2).evaluate(any(), any());
            inOrder.verify(gate1).evaluate(any(), any());
            inOrder.verify(gate3).evaluate(any(), any());
        }

        @Test
        void shouldRunSoftGatesInOrder() {
            FraudGate gate1 = mockSoftGate("A", 20, GateResult.pass("A"));
            FraudGate gate2 = mockSoftGate("B", 10, GateResult.pass("B"));
            FraudGate gate3 = mockSoftGate("C", 15, GateResult.pass("C"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate1, gate2, gate3), dataProvider, 70, 30);

            pipeline.evaluate(defaultContext);

            // Should be sorted: B (10), C (15), A (20)
            var inOrder = inOrder(gate2, gate3, gate1);
            inOrder.verify(gate2).evaluate(any(), any());
            inOrder.verify(gate3).evaluate(any(), any());
            inOrder.verify(gate1).evaluate(any(), any());
        }

        @Test
        void shouldUseTiebreakerByNameWhenSameOrder() {
            // All gates have order 10, should sort alphabetically by name: ALPHA, BETA, GAMMA
            FraudGate gammaGate = mockSoftGate("GAMMA", 10, GateResult.pass("GAMMA"));
            FraudGate alphaGate = mockSoftGate("ALPHA", 10, GateResult.pass("ALPHA"));
            FraudGate betaGate = mockSoftGate("BETA", 10, GateResult.pass("BETA"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gammaGate, alphaGate, betaGate), dataProvider, 70, 30);

            pipeline.evaluate(defaultContext);

            // Should be sorted alphabetically: ALPHA, BETA, GAMMA
            var inOrder = inOrder(alphaGate, betaGate, gammaGate);
            inOrder.verify(alphaGate).evaluate(any(), any());
            inOrder.verify(betaGate).evaluate(any(), any());
            inOrder.verify(gammaGate).evaluate(any(), any());
        }

        @Test
        void shouldUseTiebreakerForHardBlockGatesWithSameOrder() {
            FraudGate zGate = mockHardBlockGate("Z_GATE", 1, GateResult.pass("Z_GATE"));
            FraudGate aGate = mockHardBlockGate("A_GATE", 1, GateResult.pass("A_GATE"));
            FraudGate mGate = mockHardBlockGate("M_GATE", 1, GateResult.pass("M_GATE"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(zGate, aGate, mGate), dataProvider, 70, 30);

            pipeline.evaluate(defaultContext);

            // Should be sorted alphabetically: A_GATE, M_GATE, Z_GATE
            var inOrder = inOrder(aGate, mGate, zGate);
            inOrder.verify(aGate).evaluate(any(), any());
            inOrder.verify(mGate).evaluate(any(), any());
            inOrder.verify(zGate).evaluate(any(), any());
        }
    }

    @Nested
    class ResultMetadata {

        @Test
        void shouldIncludeEvaluationTime() {
            FraudGate gate = mockSoftGate("GATE", 10, GateResult.pass("GATE"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.evaluationTime()).isNotNull();
            assertThat(result.evaluationTime().toNanos()).isPositive();
        }

        @Test
        void shouldIncludeAllGateResults() {
            FraudGate hardGate = mockHardBlockGate("HARD", 1, GateResult.pass("HARD"));
            FraudGate softGate1 = mockSoftGate("SOFT1", 10,
                    GateResult.flag("SOFT1", 20, "S1", "d1"));
            FraudGate softGate2 = mockSoftGate("SOFT2", 11,
                    GateResult.pass("SOFT2"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(hardGate, softGate1, softGate2),
                    dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.gateResults()).hasSize(3);
            assertThat(result.allReasonCodes()).containsExactly("S1");
        }

        @Test
        void shouldOnlyIncludeEvaluatedGatesOnHardBlock() {
            FraudGate hardGate = mockHardBlockGate("HARD", 1,
                    GateResult.block("HARD", "BLOCKED", "blocked"));
            FraudGate softGate = mockSoftGate("SOFT", 10,
                    GateResult.flag("SOFT", 30, "S1", "detail"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(hardGate, softGate), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.gateResults()).hasSize(1);
            assertThat(result.gateResults().get(0).gateName()).isEqualTo("HARD");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void shouldHandleEmptyGateList() {
            FraudPipeline pipeline = new FraudPipeline(
                    List.of(), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
            assertThat(result.totalScore()).isZero();
            assertThat(result.gateResults()).isEmpty();
        }

        @Test
        void shouldHandleOnlyHardBlockGates() {
            FraudGate gate1 = mockHardBlockGate("G1", 1, GateResult.pass("G1"));
            FraudGate gate2 = mockHardBlockGate("G2", 2, GateResult.pass("G2"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate1, gate2), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
            assertThat(result.gateResults()).hasSize(2);
        }

        @Test
        void shouldHandleOnlySoftGates() {
            FraudGate gate1 = mockSoftGate("G1", 10,
                    GateResult.flag("G1", 40, "C1", "d1"));
            FraudGate gate2 = mockSoftGate("G2", 11,
                    GateResult.flag("G2", 35, "C2", "d2"));

            FraudPipeline pipeline = new FraudPipeline(
                    List.of(gate1, gate2), dataProvider, 70, 30);

            FraudResult result = pipeline.evaluate(defaultContext);

            assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(result.totalScore()).isEqualTo(75);
        }
    }
}
