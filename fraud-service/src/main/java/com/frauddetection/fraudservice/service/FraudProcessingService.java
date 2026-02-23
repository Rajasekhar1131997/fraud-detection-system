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
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FraudProcessingService.class);

    private final FraudDecisionRepository fraudDecisionRepository;
    private final FeatureEngineeringService featureEngineeringService;
    private final RuleEngine ruleEngine;
    private final RiskScoringService riskScoringService;
    private final DecisionEngine decisionEngine;
    private final FraudDecisionMapper mapper;
    private final FraudDecisionEventPublisher eventPublisher;

    public FraudProcessingService(
            FraudDecisionRepository fraudDecisionRepository,
            FeatureEngineeringService featureEngineeringService,
            RuleEngine ruleEngine,
            RiskScoringService riskScoringService,
            DecisionEngine decisionEngine,
            FraudDecisionMapper mapper,
            FraudDecisionEventPublisher eventPublisher
    ) {
        this.fraudDecisionRepository = fraudDecisionRepository;
        this.featureEngineeringService = featureEngineeringService;
        this.ruleEngine = ruleEngine;
        this.riskScoringService = riskScoringService;
        this.decisionEngine = decisionEngine;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void processAndPublish(TransactionCreatedEvent transactionEvent) {
        if (fraudDecisionRepository.findByTransactionId(transactionEvent.transactionId()).isPresent()) {
            log.info("fraud_decision_already_exists transactionId={}", transactionEvent.transactionId());
            return;
        }

        FeatureContext featureContext = featureEngineeringService.buildFeatureContext(transactionEvent);
        RuleEvaluationResult ruleEvaluation = ruleEngine.evaluate(transactionEvent, featureContext);
        BigDecimal ruleScore = BigDecimal.valueOf(ruleEvaluation.normalizedScore()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal mlScore = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal riskScore = riskScoringService.calculate(ruleEvaluation.normalizedScore(), mlScore);
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

            log.info(
                    "fraud_decision_created transactionId={} userId={} decision={} riskScore={} ruleScore={} ruleDetails={}",
                    transactionEvent.transactionId(),
                    transactionEvent.userId(),
                    decision,
                    riskScore,
                    ruleScore,
                    ruleEvaluation.individualRuleScores()
            );
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "fraud_decision_duplicate transactionId={} reason={}",
                    transactionEvent.transactionId(),
                    exception.getMessage()
            );
        }
    }
}
