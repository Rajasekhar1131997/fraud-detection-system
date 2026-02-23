package com.frauddetection.fraudservice.event;

import com.frauddetection.fraudservice.service.FraudProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final FraudProcessingService fraudProcessingService;

    public TransactionEventConsumer(FraudProcessingService fraudProcessingService) {
        this.fraudProcessingService = fraudProcessingService;
    }

    @KafkaListener(topics = "${app.kafka.transactions-topic}")
    public void consume(TransactionCreatedEvent event) {
        if (event == null) {
            log.warn("transaction_event_ignored reason=null_payload");
            return;
        }

        log.info(
                "transaction_event_received transactionId={} userId={} amount={} merchantId={} location={}",
                event.transactionId(),
                event.userId(),
                event.amount(),
                event.merchantId(),
                event.location()
        );

        fraudProcessingService.processAndPublish(event);
    }
}
