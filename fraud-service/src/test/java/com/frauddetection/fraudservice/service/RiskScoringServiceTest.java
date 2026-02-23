package com.frauddetection.fraudservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskScoringServiceTest {

    private final RiskScoringService riskScoringService = new RiskScoringService();

    @Test
    void combinesRuleAndMlScoresWithWeekThreeWeights() {
        BigDecimal score = riskScoringService.calculate(0.5000, new BigDecimal("0.9000"));
        assertThat(score).isEqualByComparingTo("0.7400");
    }

    @Test
    void clampsOutOfRangeScoresBeforeCombining() {
        BigDecimal high = riskScoringService.calculate(10, new BigDecimal("5"));
        BigDecimal low = riskScoringService.calculate(-3, BigDecimal.ZERO);

        assertThat(high).isEqualByComparingTo("1.0000");
        assertThat(low).isEqualByComparingTo("0.0000");
    }
}
