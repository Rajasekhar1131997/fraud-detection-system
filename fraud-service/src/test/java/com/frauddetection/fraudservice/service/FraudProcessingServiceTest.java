package com.frauddetection.fraudservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.frauddetection.fraudservice.TestFixtures;
import com.frauddetection.fraudservice.engine.FeatureContext;
import com.frauddetection.fraudservice.engine.RuleEngine;
import com.frauddetection.fraudservice.engine.RuleEvaluationResult;
import com.frauddetection.fraudservice.event.FraudDecisionEvent;
import com.frauddetection.fraudservice.event.FraudDecisionEventPublisher;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import com.frauddetection.fraudservice.mapper.FraudDecisionMapper;
import com.frauddetection.fraudservice.model.DecisionType;
import com.frauddetection.fraudservice.model.FraudDecision;
import com.frauddetection.fraudservice.repository.FraudDecisionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FraudProcessingServiceTest {

    @Mock
    private FraudDecisionRepository fraudDecisionRepository;

    @Mock
    private FeatureEngineeringService featureEngineeringService;

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private RiskScoringService riskScoringService;

    @Mock
    private DecisionEngine decisionEngine;

    @Mock
    private FraudDecisionMapper mapper;

    @Mock
    private FraudDecisionEventPublisher eventPublisher;

    private FraudProcessingService fraudProcessingService;

    @BeforeEach
    void setUp() {
        fraudProcessingService = new FraudProcessingService(
                fraudDecisionRepository,
                featureEngineeringService,
                ruleEngine,
                riskScoringService,
                decisionEngine,
                mapper,
                eventPublisher
        );
    }

    @Test
    void processesTransactionAndPublishesDecision() {
        TransactionCreatedEvent transaction = TestFixtures.transactionEvent(
                "txn-1",
                "user-1",
                BigDecimal.valueOf(9999),
                "crypto-exchange-1",
                "Moscow, RU"
        );

        FraudDecision savedDecision = new FraudDecision(
                UUID.randomUUID(),
                "txn-1",
                "user-1",
                new BigDecimal("0.8200"),
                DecisionType.BLOCKED,
                new BigDecimal("0.8200"),
                new BigDecimal("0.0000"),
                Instant.now()
        );

        FraudDecisionEvent decisionEvent = new FraudDecisionEvent(
                savedDecision.getId(),
                "txn-1",
                "user-1",
                new BigDecimal("0.8200"),
                DecisionType.BLOCKED,
                new BigDecimal("0.8200"),
                new BigDecimal("0.0000"),
                savedDecision.getCreatedAt()
        );

        when(fraudDecisionRepository.findByTransactionId("txn-1")).thenReturn(Optional.empty());
        when(featureEngineeringService.buildFeatureContext(transaction)).thenReturn(new FeatureContext(5, 9, 2));
        when(ruleEngine.evaluate(transaction, new FeatureContext(5, 9, 2)))
                .thenReturn(new RuleEvaluationResult(0.82, Map.of("high_amount", 1.0)));
        when(riskScoringService.calculate(anyDouble(), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.8200"));
        when(decisionEngine.decide(new BigDecimal("0.8200"))).thenReturn(DecisionType.BLOCKED);
        when(mapper.toEntity(any(), any(), any(), any(), any())).thenReturn(savedDecision);
        when(fraudDecisionRepository.save(savedDecision)).thenReturn(savedDecision);
        when(mapper.toEvent(savedDecision)).thenReturn(decisionEvent);

        fraudProcessingService.processAndPublish(transaction);

        verify(fraudDecisionRepository).save(savedDecision);
        verify(eventPublisher).publish(decisionEvent);
    }

    @Test
    void skipsDuplicateTransactionId() {
        TransactionCreatedEvent transaction = TestFixtures.transactionEvent(
                "txn-1",
                "user-1",
                BigDecimal.valueOf(100),
                "merchant-1",
                "Austin, US"
        );

        when(fraudDecisionRepository.findByTransactionId("txn-1"))
                .thenReturn(Optional.of(new FraudDecision()));

        fraudProcessingService.processAndPublish(transaction);

        verify(featureEngineeringService, never()).buildFeatureContext(any());
        verify(fraudDecisionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
