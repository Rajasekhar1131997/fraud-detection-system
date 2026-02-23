package com.frauddetection.fraudservice.service;

import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import com.frauddetection.fraudservice.rule.ForeignLocationRule;
import com.frauddetection.fraudservice.rule.SuspiciousMerchantRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class MlFeatureEngineeringService {

    private final ForeignLocationRule foreignLocationRule;
    private final SuspiciousMerchantRule suspiciousMerchantRule;

    public MlFeatureEngineeringService(
            ForeignLocationRule foreignLocationRule,
            SuspiciousMerchantRule suspiciousMerchantRule
    ) {
        this.foreignLocationRule = foreignLocationRule;
        this.suspiciousMerchantRule = suspiciousMerchantRule;
    }

    public MlPredictionRequest buildRequest(TransactionCreatedEvent transaction, FeatureContext featureContext) {
        FeatureContext safeFeatureContext = featureContext == null
                ? new FeatureContext(0, 0, Long.MAX_VALUE)
                : featureContext;

        int transactionFrequency = Math.max(
                0,
                Math.max(
                        safeFeatureContext.transactionsPerMinute(),
                        safeFeatureContext.transactionsPerFiveMinutes()
                )
        );

        BigDecimal amount = sanitizeAmount(transaction.amount());
        BigDecimal locationRisk = toScore(foreignLocationRule.evaluate(transaction, safeFeatureContext));
        BigDecimal merchantRisk = toScore(suspiciousMerchantRule.evaluate(transaction, safeFeatureContext));

        return new MlPredictionRequest(amount, transactionFrequency, locationRisk, merchantRisk);
    }

    private BigDecimal sanitizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal toScore(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        double bounded = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(bounded).setScale(4, RoundingMode.HALF_UP);
    }
}
