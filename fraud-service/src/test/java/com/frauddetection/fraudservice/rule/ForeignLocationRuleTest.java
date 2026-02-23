package com.frauddetection.fraudservice.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.TestFixtures;
import com.frauddetection.fraudservice.engine.FeatureContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ForeignLocationRuleTest {

    private final ForeignLocationRule rule = new ForeignLocationRule();

    @Test
    void returnsHighRiskForKnownRiskyLocations() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-1", "user-1", BigDecimal.TEN, "merchant-1", "Moscow, RU"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void returnsMediumRiskForNonUsLocationFormat() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-2", "user-1", BigDecimal.TEN, "merchant-1", "Toronto, CA"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isEqualTo(0.65);
    }

    @Test
    void returnsZeroForUsLocation() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-3", "user-1", BigDecimal.TEN, "merchant-1", "Austin, US"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isZero();
    }
}
