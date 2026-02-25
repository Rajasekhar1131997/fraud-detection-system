package com.frauddetection.fraudservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DashboardMetricsResponse(
        Instant from,
        Instant to,
        long totalTransactions,
        long approvedCount,
        long reviewCount,
        long blockedCount,
        BigDecimal fraudRatePercentage,
        BigDecimal averageRiskScore,
        List<TransactionsPerMinutePoint> transactionsPerMinute
) {
}
