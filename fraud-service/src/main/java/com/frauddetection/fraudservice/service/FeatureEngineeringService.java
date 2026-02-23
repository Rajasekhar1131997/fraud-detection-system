package com.frauddetection.fraudservice.service;

import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FeatureEngineeringService {

    private final VelocityTrackingService velocityTrackingService;

    public FeatureEngineeringService(VelocityTrackingService velocityTrackingService) {
        this.velocityTrackingService = velocityTrackingService;
    }

    public FeatureContext buildFeatureContext(TransactionCreatedEvent transaction) {
        Instant eventTime = transaction.createdAt() == null ? Instant.now() : transaction.createdAt();
        VelocityStats velocityStats = velocityTrackingService.trackAndMeasure(
                transaction.userId(),
                transaction.transactionId(),
                eventTime
        );

        return new FeatureContext(
                velocityStats.transactionsPerMinute(),
                velocityStats.transactionsPerFiveMinutes(),
                velocityStats.secondsSinceLastTransaction()
        );
    }
}
