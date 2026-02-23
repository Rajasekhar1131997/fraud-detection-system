package com.frauddetection.fraudservice.event;

import com.frauddetection.fraudservice.model.DecisionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudDecisionEvent(
        UUID id,
        String transactionId,
        String userId,
        BigDecimal riskScore,
        DecisionType decision,
        BigDecimal ruleScore,
        BigDecimal mlScore,
        Instant createdAt
) {
}
