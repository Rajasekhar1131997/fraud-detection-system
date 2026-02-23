package com.frauddetection.fraudservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.TestFixtures;
import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.rule.ForeignLocationRule;
import com.frauddetection.fraudservice.rule.SuspiciousMerchantRule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MlFeatureEngineeringServiceTest {

    private final MlFeatureEngineeringService mlFeatureEngineeringService = new MlFeatureEngineeringService(
            new ForeignLocationRule(),
            new SuspiciousMerchantRule()
    );

    @Test
    void buildsPredictionPayloadFromTransactionAndVelocityFeatures() {
        MlPredictionRequest request = mlFeatureEngineeringService.buildRequest(
                TestFixtures.transactionEvent(
                        "txn-1",
                        "user-1",
                        BigDecimal.valueOf(8700),
                        "crypto-exchange-2",
                        "Moscow, RU"
                ),
                new FeatureContext(3, 8, 10)
        );

        assertThat(request.amount()).isEqualByComparingTo("8700.0000");
        assertThat(request.transactionFrequency()).isEqualTo(8);
        assertThat(request.locationRisk()).isEqualByComparingTo("1.0000");
        assertThat(request.merchantRisk()).isEqualByComparingTo("1.0000");
    }

    @Test
    void defaultsNullOrInvalidValuesToSafeBounds() {
        MlPredictionRequest request = mlFeatureEngineeringService.buildRequest(
                TestFixtures.transactionEvent(
                        "txn-2",
                        "user-1",
                        BigDecimal.valueOf(-90),
                        "merchant-1",
                        "Austin, US"
                ),
                null
        );

        assertThat(request.amount()).isEqualByComparingTo("0.0000");
        assertThat(request.transactionFrequency()).isZero();
        assertThat(request.locationRisk()).isEqualByComparingTo("0.0000");
        assertThat(request.merchantRisk()).isEqualByComparingTo("0.0000");
    }
}
