package com.frauddetection.fraudservice.dto;

import com.frauddetection.fraudservice.model.DecisionType;
import java.math.BigDecimal;
import java.time.Instant;

public record DashboardDecisionDto(
        String transactionId,
        String userId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String location,
        BigDecimal riskScore,
        DecisionType decision,
        BigDecimal ruleScore,
        BigDecimal mlScore,
        Instant createdAt
) {
}
