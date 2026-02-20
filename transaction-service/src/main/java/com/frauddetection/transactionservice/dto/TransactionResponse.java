package com.frauddetection.transactionservice.dto;

import com.frauddetection.transactionservice.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String transactionId,
        String userId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String location,
        String deviceId,
        TransactionStatus status,
        Instant createdAt
) {
}
