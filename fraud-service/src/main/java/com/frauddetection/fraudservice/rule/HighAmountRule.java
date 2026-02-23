package com.frauddetection.fraudservice.rule;

import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.engine.Rule;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class HighAmountRule implements Rule {

    private static final BigDecimal THRESHOLD = BigDecimal.valueOf(5000);

    @Override
    public String name() {
        return "high_amount";
    }

    @Override
    public double weight() {
        return 0.40;
    }

    @Override
    public double evaluate(TransactionCreatedEvent transaction, FeatureContext featureContext) {
        if (transaction.amount() == null || transaction.amount().compareTo(THRESHOLD) <= 0) {
            return 0.0;
        }

        BigDecimal riskWindow = transaction.amount()
                .subtract(THRESHOLD)
                .divide(THRESHOLD, 4, java.math.RoundingMode.HALF_UP);
        return Math.min(1.0, riskWindow.doubleValue());
    }
}
