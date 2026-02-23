package com.frauddetection.fraudservice.service;

import static org.mockito.ArgumentMatchers.any;
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    private MlFeatureEngineeringService mlFeatureEngineeringService;

    @Mock
    private MlInferenceClient mlInferenceClient;

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private RiskAggregationService riskAggregationService;

    @Mock
    private DecisionEngine decisionEngine;

    @Mock
    private FraudDecisionMapper mapper;

    @Mock
    private FraudDecisionEventPublisher eventPublisher;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Timer processingTimer;

    private FraudProcessingService fraudProcessingService;

    @BeforeEach
    void setUp() {
        when(meterRegistry.timer("fraud.processing.latency")).thenReturn(processingTimer);
        fraudProcessingService = new FraudProcessingService(
                fraudDecisionRepository,
                featureEngineeringService,
                mlFeatureEngineeringService,
                mlInferenceClient,
                ruleEngine,
                riskAggregationService,
                decisionEngine,
                mapper,
                eventPublisher,
                meterRegistry
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

        MlPredictionRequest mlPredictionRequest = new MlPredictionRequest(
                new BigDecimal("9999.0000"),
                9,
                new BigDecimal("1.0000"),
                new BigDecimal("1.0000")
        );

        FraudDecision savedDecision = new FraudDecision(
                UUID.randomUUID(),
                "txn-1",
                "user-1",
                new BigDecimal("0.8740"),
                DecisionType.BLOCKED,
                new BigDecimal("0.8200"),
                new BigDecimal("0.9100"),
                Instant.now()
        );

        FraudDecisionEvent decisionEvent = new FraudDecisionEvent(
                savedDecision.getId(),
                "txn-1",
                "user-1",
                new BigDecimal("0.8740"),
                DecisionType.BLOCKED,
                new BigDecimal("0.8200"),
                new BigDecimal("0.9100"),
                savedDecision.getCreatedAt()
        );

        when(fraudDecisionRepository.findByTransactionId("txn-1")).thenReturn(Optional.empty());
        when(featureEngineeringService.buildFeatureContext(transaction)).thenReturn(new FeatureContext(5, 9, 2));
        when(ruleEngine.evaluate(transaction, new FeatureContext(5, 9, 2)))
                .thenReturn(new RuleEvaluationResult(0.82, Map.of("high_amount", 1.0)));
        when(mlFeatureEngineeringService.buildRequest(transaction, new FeatureContext(5, 9, 2)))
                .thenReturn(mlPredictionRequest);
        when(mlInferenceClient.predictScore(mlPredictionRequest, new BigDecimal("0.8200")))
                .thenReturn(CompletableFuture.completedFuture(new BigDecimal("0.9100")));
        when(riskAggregationService.aggregate(new BigDecimal("0.8200"), new BigDecimal("0.9100")))
                .thenReturn(new BigDecimal("0.8740"));
        when(decisionEngine.decide(new BigDecimal("0.8740"))).thenReturn(DecisionType.BLOCKED);
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
        verify(mlFeatureEngineeringService, never()).buildRequest(any(), any());
        verify(mlInferenceClient, never()).predictScore(any(), any());
        verify(fraudDecisionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void fallsBackToRuleScoreWhenMlInferenceFails() {
        TransactionCreatedEvent transaction = TestFixtures.transactionEvent(
                "txn-3",
                "user-3",
                BigDecimal.valueOf(6800),
                "merchant-1",
                "Austin, US"
        );

        MlPredictionRequest mlPredictionRequest = new MlPredictionRequest(
                new BigDecimal("6800.0000"),
                4,
                new BigDecimal("0.0000"),
                new BigDecimal("0.0000")
        );

        FraudDecision savedDecision = new FraudDecision(
                UUID.randomUUID(),
                "txn-3",
                "user-3",
                new BigDecimal("0.5500"),
                DecisionType.REVIEW,
                new BigDecimal("0.5500"),
                new BigDecimal("0.5500"),
                Instant.now()
        );

        when(fraudDecisionRepository.findByTransactionId("txn-3")).thenReturn(Optional.empty());
        when(featureEngineeringService.buildFeatureContext(transaction)).thenReturn(new FeatureContext(4, 4, 10));
        when(ruleEngine.evaluate(transaction, new FeatureContext(4, 4, 10)))
                .thenReturn(new RuleEvaluationResult(0.55, Map.of("rapid_transactions", 0.8)));
        when(mlFeatureEngineeringService.buildRequest(transaction, new FeatureContext(4, 4, 10)))
                .thenReturn(mlPredictionRequest);
        when(mlInferenceClient.predictScore(mlPredictionRequest, new BigDecimal("0.5500")))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException(new RuntimeException("timeout"))));
        when(riskAggregationService.aggregate(new BigDecimal("0.5500"), new BigDecimal("0.5500")))
                .thenReturn(new BigDecimal("0.5500"));
        when(decisionEngine.decide(new BigDecimal("0.5500"))).thenReturn(DecisionType.REVIEW);
        when(mapper.toEntity(any(), any(), any(), any(), any())).thenReturn(savedDecision);
        when(fraudDecisionRepository.save(savedDecision)).thenReturn(savedDecision);
        when(mapper.toEvent(savedDecision)).thenReturn(
                new FraudDecisionEvent(
                        savedDecision.getId(),
                        savedDecision.getTransactionId(),
                        savedDecision.getUserId(),
                        savedDecision.getRiskScore(),
                        savedDecision.getDecision(),
                        savedDecision.getRuleScore(),
                        savedDecision.getMlScore(),
                        savedDecision.getCreatedAt()
                )
        );

        fraudProcessingService.processAndPublish(transaction);

        verify(riskAggregationService).aggregate(new BigDecimal("0.5500"), new BigDecimal("0.5500"));
        verify(fraudDecisionRepository).save(savedDecision);
    }
}
