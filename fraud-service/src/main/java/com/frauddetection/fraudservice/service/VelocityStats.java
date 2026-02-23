package com.frauddetection.fraudservice.service;

public record VelocityStats(
        int transactionsPerMinute,
        int transactionsPerFiveMinutes,
        long secondsSinceLastTransaction
) {
}
