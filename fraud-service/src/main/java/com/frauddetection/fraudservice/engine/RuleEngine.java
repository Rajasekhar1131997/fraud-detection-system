package com.frauddetection.fraudservice.engine;

import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
    }

    public RuleEvaluationResult evaluate(TransactionCreatedEvent transaction, FeatureContext featureContext) {
        if (rules.isEmpty()) {
            return new RuleEvaluationResult(0.0, Map.of());
        }

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        Map<String, Double> individualScores = new LinkedHashMap<>();

        for (Rule rule : rules) {
            double score = normalize(rule.evaluate(transaction, featureContext));
            double weight = Math.max(0.0, rule.weight());
            individualScores.put(rule.name(), round(score));

            weightedSum += score * weight;
            totalWeight += weight;
        }

        double normalizedScore = totalWeight == 0.0 ? 0.0 : weightedSum / totalWeight;
        return new RuleEvaluationResult(round(normalizedScore), individualScores);
    }

    private double normalize(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
