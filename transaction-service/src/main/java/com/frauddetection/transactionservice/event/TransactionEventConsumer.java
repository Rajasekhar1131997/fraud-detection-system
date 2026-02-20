package com.frauddetection.transactionservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    @KafkaListener(
            topics = "${app.kafka.transactions-topic}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void onTransactionCreated(TransactionCreatedEvent event) {
        log.info("transaction_event_received transactionId={} status={} amount={}",
                event.transactionId(), event.status(), event.amount());
    }
}
