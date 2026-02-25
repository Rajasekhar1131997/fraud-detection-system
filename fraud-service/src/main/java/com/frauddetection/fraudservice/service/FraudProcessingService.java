package com.frauddetection.fraudservice.service;

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
import java.math.RoundingMode;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FraudProcessingService.class);
    private static final String PROCESSING_LATENCY_METRIC = "fraud.processing.latency";

    private final FraudDecisionRepository fraudDecisionRepository;
    private final FeatureEngineeringService featureEngineeringService;
    private final MlFeatureEngineeringService mlFeatureEngineeringService;
    private final MlInferenceClient mlInferenceClient;
    private final RuleEngine ruleEngine;
    private final RiskAggregationService riskAggregationService;
    private final DecisionEngine decisionEngine;
    private final FraudDecisionMapper mapper;
    private final FraudDecisionEventPublisher eventPublisher;
    private final DashboardStreamService dashboardStreamService;
    private final MeterRegistry meterRegistry;
    private final Timer processingLatencyTimer;

    public FraudProcessingService(
            FraudDecisionRepository fraudDecisionRepository,
            FeatureEngineeringService featureEngineeringService,
            MlFeatureEngineeringService mlFeatureEngineeringService,
            MlInferenceClient mlInferenceClient,
            RuleEngine ruleEngine,
            RiskAggregationService riskAggregationService,
            DecisionEngine decisionEngine,
            FraudDecisionMapper mapper,
            FraudDecisionEventPublisher eventPublisher,
            DashboardStreamService dashboardStreamService,
            MeterRegistry meterRegistry
    ) {
        this.fraudDecisionRepository = fraudDecisionRepository;
        this.featureEngineeringService = featureEngineeringService;
        this.mlFeatureEngineeringService = mlFeatureEngineeringService;
        this.mlInferenceClient = mlInferenceClient;
        this.ruleEngine = ruleEngine;
        this.riskAggregationService = riskAggregationService;
        this.decisionEngine = decisionEngine;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.dashboardStreamService = dashboardStreamService;
        this.meterRegistry = meterRegistry;
        this.processingLatencyTimer = meterRegistry.timer(PROCESSING_LATENCY_METRIC);
    }

    @Transactional
    public void processAndPublish(TransactionCreatedEvent transactionEvent) {
        long processingStartNanos = System.nanoTime();
        try {
            if (fraudDecisionRepository.findByTransactionId(transactionEvent.transactionId()).isPresent()) {
                log.info("fraud_decision_already_exists transactionId={}", transactionEvent.transactionId());
                return;
            }

            FeatureContext featureContext = featureEngineeringService.buildFeatureContext(transactionEvent);
            RuleEvaluationResult ruleEvaluation = ruleEngine.evaluate(transactionEvent, featureContext);
            BigDecimal ruleScore = BigDecimal.valueOf(ruleEvaluation.normalizedScore()).setScale(4, RoundingMode.HALF_UP);

            MlPredictionRequest mlPredictionRequest = mlFeatureEngineeringService.buildRequest(transactionEvent, featureContext);
            BigDecimal mlScore = resolveMlScore(mlPredictionRequest, ruleScore);

            BigDecimal riskScore = riskAggregationService.aggregate(ruleScore, mlScore);
            DecisionType decision = decisionEngine.decide(riskScore);

            try {
                FraudDecision decisionEntity = mapper.toEntity(
                        transactionEvent,
                        riskScore,
                        decision,
                        ruleScore,
                        mlScore
                );

                FraudDecision savedDecision = fraudDecisionRepository.save(decisionEntity);
                FraudDecisionEvent decisionEvent = mapper.toEvent(savedDecision);
                eventPublisher.publish(decisionEvent);
                dashboardStreamService.publish(savedDecision);
                meterRegistry.counter("fraud.decisions.total", "decision", decision.name()).increment();

                log.info(
                        "fraud_decision_created transactionId={} userId={} decision={} riskScore={} ruleScore={} "
                                + "mlScore={} mlInput={} ruleDetails={}",
                        transactionEvent.transactionId(),
                        transactionEvent.userId(),
                        decision,
                        riskScore,
                        ruleScore,
                        mlScore,
                        mlPredictionRequest,
                        ruleEvaluation.individualRuleScores()
                );
            } catch (DataIntegrityViolationException exception) {
                log.warn(
                        "fraud_decision_duplicate transactionId={} reason={}",
                        transactionEvent.transactionId(),
                        exception.getMessage()
                );
            }
        } finally {
            processingLatencyTimer.record(System.nanoTime() - processingStartNanos, TimeUnit.NANOSECONDS);
        }
    }

    private BigDecimal resolveMlScore(MlPredictionRequest request, BigDecimal fallbackScore) {
        try {
            return mlInferenceClient.predictScore(request, fallbackScore).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "ml_inference_interrupted fallbackScore={} reason={}",
                    fallbackScore,
                    exception.getMessage()
            );
            return fallbackScore;
        } catch (ExecutionException exception) {
            log.warn(
                    "ml_inference_execution_failed fallbackScore={} reason={}",
                    fallbackScore,
                    exception.getMessage()
            );
            return fallbackScore;
        }
    }
}
