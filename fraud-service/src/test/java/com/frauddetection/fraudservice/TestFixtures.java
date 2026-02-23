package com.frauddetection.fraudservice;

import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import com.frauddetection.fraudservice.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static TransactionCreatedEvent transactionEvent(
            String transactionId,
            String userId,
            BigDecimal amount,
            String merchantId,
            String location
    ) {
        return new TransactionCreatedEvent(
                UUID.randomUUID(),
                transactionId,
                userId,
                amount,
                "USD",
                merchantId,
                location,
                "device-1",
                TransactionStatus.RECEIVED,
                Instant.now()
        );
    }
}
