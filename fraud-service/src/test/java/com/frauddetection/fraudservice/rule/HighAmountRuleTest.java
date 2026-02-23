package com.frauddetection.fraudservice.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.TestFixtures;
import com.frauddetection.fraudservice.engine.FeatureContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HighAmountRuleTest {

    private final HighAmountRule rule = new HighAmountRule();

    @Test
    void returnsZeroWhenAmountAtOrBelowThreshold() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-1", "user-1", BigDecimal.valueOf(5000), "merchant-1", "Austin, US"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isZero();
    }

    @Test
    void returnsScaledScoreWhenAmountAboveThreshold() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-2", "user-1", BigDecimal.valueOf(7500), "merchant-1", "Austin, US"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isEqualTo(0.5);
    }

    @Test
    void capsScoreAtOne() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-3", "user-1", BigDecimal.valueOf(20000), "merchant-1", "Austin, US"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isEqualTo(1.0);
    }
}
