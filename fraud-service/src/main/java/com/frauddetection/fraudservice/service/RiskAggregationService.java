package com.frauddetection.fraudservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class RiskAggregationService {

    private static final BigDecimal RULE_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal ML_WEIGHT = new BigDecimal("0.60");

    public BigDecimal aggregate(BigDecimal ruleScore, BigDecimal mlScore) {
        BigDecimal boundedRuleScore = clamp(ruleScore);
        BigDecimal boundedMlScore = clamp(mlScore);

        return boundedRuleScore.multiply(RULE_WEIGHT)
                .add(boundedMlScore.multiply(ML_WEIGHT))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value;
    }
}
