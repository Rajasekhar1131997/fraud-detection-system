package com.frauddetection.fraudservice.rule;

import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.engine.Rule;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SuspiciousMerchantRule implements Rule {

    private static final Set<String> SUSPICIOUS_KEYWORDS = Set.of(
            "casino",
            "gambling",
            "bet",
            "crypto",
            "giftcard",
            "money-transfer",
            "wire"
    );

    @Override
    public String name() {
        return "suspicious_merchant";
    }

    @Override
    public double weight() {
        return 0.20;
    }

    @Override
    public double evaluate(TransactionCreatedEvent transaction, FeatureContext featureContext) {
        if (transaction.merchantId() == null || transaction.merchantId().isBlank()) {
            return 0.0;
        }

        String merchantId = transaction.merchantId().trim().toLowerCase(Locale.ROOT);
        for (String keyword : SUSPICIOUS_KEYWORDS) {
            if (merchantId.contains(keyword)) {
                return 1.0;
            }
        }

        return 0.0;
    }
}
