package com.frauddetection.fraudservice.engine;

import java.util.Map;

public record RuleEvaluationResult(
        double normalizedScore,
        Map<String, Double> individualRuleScores
) {
}
