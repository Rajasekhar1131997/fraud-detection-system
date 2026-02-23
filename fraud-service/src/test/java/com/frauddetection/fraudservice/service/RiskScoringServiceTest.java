package com.frauddetection.fraudservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskScoringServiceTest {

    private final RiskScoringService riskScoringService = new RiskScoringService();

    @Test
    void returnsRuleScoreForWeekTwo() {
        BigDecimal score = riskScoringService.calculate(0.63453, BigDecimal.ZERO);
        assertThat(score).isEqualByComparingTo("0.6345");
    }

    @Test
    void clampsOutOfRangeScores() {
        BigDecimal high = riskScoringService.calculate(10, BigDecimal.ZERO);
        BigDecimal low = riskScoringService.calculate(-3, BigDecimal.ZERO);

        assertThat(high).isEqualByComparingTo("1.0000");
        assertThat(low).isEqualByComparingTo("0.0000");
    }
}
