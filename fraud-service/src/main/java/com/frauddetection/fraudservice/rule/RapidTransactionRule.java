package com.frauddetection.fraudservice.rule;

import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.engine.Rule;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import org.springframework.stereotype.Component;

@Component
public class RapidTransactionRule implements Rule {

    @Override
    public String name() {
        return "rapid_transactions";
    }

    @Override
    public double weight() {
        return 0.20;
    }

    @Override
    public double evaluate(TransactionCreatedEvent transaction, FeatureContext featureContext) {
        if (featureContext.transactionsPerMinute() >= 6 || featureContext.transactionsPerFiveMinutes() >= 12) {
            return 1.0;
        }
        if (featureContext.transactionsPerMinute() >= 4 || featureContext.transactionsPerFiveMinutes() >= 8) {
            return 0.80;
        }
        if (featureContext.transactionsPerMinute() >= 3 || featureContext.transactionsPerFiveMinutes() >= 6) {
            return 0.55;
        }
        if (featureContext.secondsSinceLastTransaction() <= 5) {
            return 0.45;
        }
        return 0.0;
    }
}
