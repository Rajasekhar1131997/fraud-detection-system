package com.frauddetection.fraudservice.dto;

import java.time.Instant;

public record TransactionsPerMinutePoint(
        Instant minute,
        long count
) {
}
