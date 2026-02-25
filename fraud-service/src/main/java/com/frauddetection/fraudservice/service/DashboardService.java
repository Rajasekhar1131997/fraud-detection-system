package com.frauddetection.fraudservice.service;

import com.frauddetection.fraudservice.dto.DashboardDecisionDto;
import com.frauddetection.fraudservice.dto.DashboardDecisionPageResponse;
import com.frauddetection.fraudservice.dto.DashboardMetricsResponse;
import com.frauddetection.fraudservice.dto.TransactionsPerMinutePoint;
import com.frauddetection.fraudservice.model.DecisionType;
import com.frauddetection.fraudservice.model.FraudDecision;
import com.frauddetection.fraudservice.repository.FraudDecisionRepository;
import com.frauddetection.fraudservice.repository.FraudDecisionSpecifications;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final Duration DEFAULT_METRICS_RANGE = Duration.ofHours(1);

    private final FraudDecisionRepository fraudDecisionRepository;

    public DashboardService(FraudDecisionRepository fraudDecisionRepository) {
        this.fraudDecisionRepository = fraudDecisionRepository;
    }

    public DashboardDecisionPageResponse fetchDecisions(
            String userId,
            DecisionType decision,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        validateAmountRange(minAmount, maxAmount);
        validateDateRange(from, to);

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<FraudDecision> specification =
                FraudDecisionSpecifications.build(userId, decision, minAmount, maxAmount, from, to);

        Page<FraudDecision> decisionPage = fraudDecisionRepository.findAll(specification, pageable);
        List<DashboardDecisionDto> content = decisionPage.getContent()
                .stream()
                .map(this::toDashboardDecision)
                .toList();

        return new DashboardDecisionPageResponse(
                content,
                decisionPage.getNumber(),
                decisionPage.getSize(),
                decisionPage.getTotalElements(),
                decisionPage.getTotalPages(),
                decisionPage.isLast()
        );
    }

    public DashboardMetricsResponse fetchMetrics(Instant from, Instant to) {
        Instant effectiveTo = to == null ? Instant.now() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_METRICS_RANGE) : from;

        validateDateRange(effectiveFrom, effectiveTo);

        Specification<FraudDecision> specification =
                FraudDecisionSpecifications.build(null, null, null, null, effectiveFrom, effectiveTo);
        List<FraudDecision> decisions = fraudDecisionRepository.findAll(specification, Sort.by(Sort.Direction.ASC, "createdAt"));

        long total = decisions.size();
        long approvedCount = decisions.stream().filter(decision -> decision.getDecision() == DecisionType.APPROVED).count();
        long reviewCount = decisions.stream().filter(decision -> decision.getDecision() == DecisionType.REVIEW).count();
        long blockedCount = decisions.stream().filter(decision -> decision.getDecision() == DecisionType.BLOCKED).count();

        BigDecimal fraudRatePercentage = total == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf((reviewCount + blockedCount) * 100.0 / total).setScale(2, RoundingMode.HALF_UP);

        long riskScoreCount = decisions.stream()
                .map(FraudDecision::getRiskScore)
                .filter(value -> value != null)
                .count();

        BigDecimal averageRiskScore = decisions.stream()
                .map(FraudDecision::getRiskScore)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (riskScoreCount > 0) {
            averageRiskScore = averageRiskScore.divide(BigDecimal.valueOf(riskScoreCount), 4, RoundingMode.HALF_UP);
        } else {
            averageRiskScore = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        Map<Instant, Long> groupedByMinute = decisions.stream()
                .filter(decision -> decision.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        decision -> decision.getCreatedAt().truncatedTo(ChronoUnit.MINUTES),
                        TreeMap::new,
                        Collectors.counting()
                ));

        List<TransactionsPerMinutePoint> transactionsPerMinute = groupedByMinute.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> new TransactionsPerMinutePoint(entry.getKey(), entry.getValue()))
                .toList();

        return new DashboardMetricsResponse(
                effectiveFrom,
                effectiveTo,
                total,
                approvedCount,
                reviewCount,
                blockedCount,
                fraudRatePercentage,
                averageRiskScore,
                transactionsPerMinute
        );
    }

    public DashboardDecisionDto toDashboardDecision(FraudDecision decision) {
        return new DashboardDecisionDto(
                decision.getTransactionId(),
                decision.getUserId(),
                decision.getAmount(),
                decision.getCurrency(),
                decision.getMerchantId(),
                decision.getLocation(),
                decision.getRiskScore(),
                decision.getDecision(),
                decision.getRuleScore(),
                decision.getMlScore(),
                decision.getCreatedAt()
        );
    }

    private void validateAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount == null || maxAmount == null) {
            return;
        }
        if (minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount must be less than or equal to maxAmount");
        }
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }
}
