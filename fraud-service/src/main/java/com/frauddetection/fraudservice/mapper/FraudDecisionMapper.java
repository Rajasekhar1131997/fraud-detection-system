package com.frauddetection.fraudservice.mapper;

import com.frauddetection.fraudservice.event.FraudDecisionEvent;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import com.frauddetection.fraudservice.model.DecisionType;
import com.frauddetection.fraudservice.model.FraudDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class FraudDecisionMapper {

    public FraudDecision toEntity(
            TransactionCreatedEvent transaction,
            BigDecimal riskScore,
            DecisionType decision,
            BigDecimal ruleScore,
            BigDecimal mlScore
    ) {
        FraudDecision fraudDecision = new FraudDecision();
        fraudDecision.setTransactionId(transaction.transactionId());
        fraudDecision.setUserId(transaction.userId());
        fraudDecision.setRiskScore(scale(riskScore));
        fraudDecision.setDecision(decision);
        fraudDecision.setRuleScore(scale(ruleScore));
        fraudDecision.setMlScore(scale(mlScore));
        fraudDecision.setAmount(transaction.amount());
        fraudDecision.setCurrency(transaction.currency());
        fraudDecision.setMerchantId(transaction.merchantId());
        fraudDecision.setLocation(transaction.location());
        return fraudDecision;
    }

    public FraudDecisionEvent toEvent(FraudDecision decision) {
        return new FraudDecisionEvent(
                decision.getId(),
                decision.getTransactionId(),
                decision.getUserId(),
                decision.getRiskScore(),
                decision.getDecision(),
                decision.getRuleScore(),
                decision.getMlScore(),
                decision.getAmount(),
                decision.getCurrency(),
                decision.getMerchantId(),
                decision.getLocation(),
                decision.getCreatedAt()
        );
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
