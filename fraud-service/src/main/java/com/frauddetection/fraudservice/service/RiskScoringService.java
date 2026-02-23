package com.frauddetection.fraudservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class RiskScoringService {

    public BigDecimal calculate(double ruleScore, BigDecimal mlScore) {
        BigDecimal boundedRuleScore = clamp(BigDecimal.valueOf(ruleScore));
        return boundedRuleScore.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return score;
    }
}
