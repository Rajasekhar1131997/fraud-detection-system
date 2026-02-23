package com.frauddetection.fraudservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FraudDecisionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FraudDecisionEventPublisher.class);

    private final KafkaTemplate<String, FraudDecisionEvent> kafkaTemplate;

    @Value("${app.kafka.fraud-decisions-topic}")
    private String decisionsTopic;

    public FraudDecisionEventPublisher(KafkaTemplate<String, FraudDecisionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(FraudDecisionEvent event) {
        kafkaTemplate.send(decisionsTopic, event.transactionId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error(
                                "fraud_decision_publish_failed transactionId={} topic={}",
                                event.transactionId(),
                                decisionsTopic,
                                ex
                        );
                        return;
                    }

                    log.info(
                            "fraud_decision_published transactionId={} decision={} topic={}",
                            event.transactionId(),
                            event.decision(),
                            decisionsTopic
                    );
                });
    }
}
