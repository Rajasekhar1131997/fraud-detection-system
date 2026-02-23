package com.frauddetection.fraudservice.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MlPredictionRequest(
        BigDecimal amount,
        int transactionFrequency,
        BigDecimal locationRisk,
        BigDecimal merchantRisk
) {
}
