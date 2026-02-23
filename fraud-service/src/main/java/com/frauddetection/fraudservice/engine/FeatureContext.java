package com.frauddetection.fraudservice.engine;

public record FeatureContext(
        int transactionsPerMinute,
        int transactionsPerFiveMinutes,
        long secondsSinceLastTransaction
) {
}
