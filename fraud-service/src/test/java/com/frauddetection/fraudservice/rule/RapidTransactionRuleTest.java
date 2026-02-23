package com.frauddetection.fraudservice.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.TestFixtures;
import com.frauddetection.fraudservice.engine.FeatureContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RapidTransactionRuleTest {

    private final RapidTransactionRule rule = new RapidTransactionRule();

    @Test
    void returnsHighestScoreForVeryHighVelocity() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-1", "user-1", BigDecimal.TEN, "merchant-1", "Austin, US"),
                new FeatureContext(7, 10, 2)
        );

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void returnsIntermediateScoreForElevatedVelocity() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-2", "user-1", BigDecimal.TEN, "merchant-1", "Austin, US"),
                new FeatureContext(4, 7, 30)
        );

        assertThat(score).isEqualTo(0.8);
    }

    @Test
    void returnsBurstScoreWhenTransactionsAreVeryClose() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-3", "user-1", BigDecimal.TEN, "merchant-1", "Austin, US"),
                new FeatureContext(1, 2, 4)
        );

        assertThat(score).isEqualTo(0.45);
    }
}
