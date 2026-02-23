package com.frauddetection.fraudservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskAggregationServiceTest {

    private final RiskAggregationService riskAggregationService = new RiskAggregationService();

    @Test
    void combinesMlAndRuleScoresUsingConfiguredWeights() {
        BigDecimal aggregated = riskAggregationService.aggregate(
                new BigDecimal("0.4000"),
                new BigDecimal("0.9000")
        );

        assertThat(aggregated).isEqualByComparingTo("0.7000");
    }

    @Test
    void clampsOutOfRangeInputs() {
        BigDecimal high = riskAggregationService.aggregate(new BigDecimal("5.0000"), new BigDecimal("2.0000"));
        BigDecimal low = riskAggregationService.aggregate(new BigDecimal("-1.0000"), new BigDecimal("-2.0000"));

        assertThat(high).isEqualByComparingTo("1.0000");
        assertThat(low).isEqualByComparingTo("0.0000");
    }
}
