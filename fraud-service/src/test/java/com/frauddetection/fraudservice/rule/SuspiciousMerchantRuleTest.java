package com.frauddetection.fraudservice.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.TestFixtures;
import com.frauddetection.fraudservice.engine.FeatureContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SuspiciousMerchantRuleTest {

    private final SuspiciousMerchantRule rule = new SuspiciousMerchantRule();

    @Test
    void returnsOneWhenMerchantContainsSuspiciousKeyword() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-1", "user-1", BigDecimal.TEN, "crypto-exchange-1", "Austin, US"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void returnsZeroWhenMerchantIsNormal() {
        double score = rule.evaluate(
                TestFixtures.transactionEvent("txn-2", "user-1", BigDecimal.TEN, "merchant-grocery-1", "Austin, US"),
                new FeatureContext(1, 1, 120)
        );

        assertThat(score).isZero();
    }
}
