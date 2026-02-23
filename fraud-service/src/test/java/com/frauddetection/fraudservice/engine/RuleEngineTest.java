package com.frauddetection.fraudservice.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.TestFixtures;
import com.frauddetection.fraudservice.rule.ForeignLocationRule;
import com.frauddetection.fraudservice.rule.HighAmountRule;
import com.frauddetection.fraudservice.rule.RapidTransactionRule;
import com.frauddetection.fraudservice.rule.SuspiciousMerchantRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    @Test
    void aggregatesWeightedRuleScoresInRange() {
        RuleEngine engine = new RuleEngine(List.of(
                new HighAmountRule(),
                new ForeignLocationRule(),
                new SuspiciousMerchantRule(),
                new RapidTransactionRule()
        ));

        RuleEvaluationResult result = engine.evaluate(
                TestFixtures.transactionEvent(
                        "txn-1",
                        "user-1",
                        BigDecimal.valueOf(12000),
                        "crypto-exchange-99",
                        "Moscow, RU"
                ),
                new FeatureContext(6, 9, 2)
        );

        assertThat(result.normalizedScore()).isBetween(0.0, 1.0);
        assertThat(result.individualRuleScores()).hasSize(4);
        assertThat(result.normalizedScore()).isGreaterThan(0.7);
    }
}
