package com.frauddetection.fraudservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.model.DecisionType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecisionEngineTest {

    private final DecisionEngine decisionEngine = new DecisionEngine();

    @Test
    void returnsApprovedBelowReviewThreshold() {
        DecisionType decision = decisionEngine.decide(new BigDecimal("0.3999"));
        assertThat(decision).isEqualTo(DecisionType.APPROVED);
    }

    @Test
    void returnsReviewBetweenThresholds() {
        DecisionType decision = decisionEngine.decide(new BigDecimal("0.6500"));
        assertThat(decision).isEqualTo(DecisionType.REVIEW);
    }

    @Test
    void returnsBlockedAtOrAboveBlockThreshold() {
        DecisionType decision = decisionEngine.decide(new BigDecimal("0.7000"));
        assertThat(decision).isEqualTo(DecisionType.BLOCKED);
    }
}
