package com.frauddetection.fraudservice.rule;

import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.engine.Rule;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ForeignLocationRule implements Rule {

    private static final Set<String> HIGH_RISK_LOCATIONS = Set.of(
            "lagos",
            "moscow",
            "bucharest",
            "phnom penh",
            "jakarta"
    );

    @Override
    public String name() {
        return "foreign_location";
    }

    @Override
    public double weight() {
        return 0.20;
    }

    @Override
    public double evaluate(TransactionCreatedEvent transaction, FeatureContext featureContext) {
        if (transaction.location() == null || transaction.location().isBlank()) {
            return 0.0;
        }

        String location = transaction.location().trim().toLowerCase(Locale.ROOT);

        for (String riskyLocation : HIGH_RISK_LOCATIONS) {
            if (location.contains(riskyLocation)) {
                return 1.0;
            }
        }

        if (location.contains(",") && !location.endsWith("us") && !location.endsWith("usa")
                && !location.endsWith("united states")) {
            return 0.65;
        }

        return 0.0;
    }
}
