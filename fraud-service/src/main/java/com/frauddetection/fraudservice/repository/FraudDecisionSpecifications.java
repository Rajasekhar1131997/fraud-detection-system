package com.frauddetection.fraudservice.repository;

import com.frauddetection.fraudservice.model.DecisionType;
import com.frauddetection.fraudservice.model.FraudDecision;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public final class FraudDecisionSpecifications {

    private FraudDecisionSpecifications() {
    }

    public static Specification<FraudDecision> hasUserId(String userId) {
        return (root, query, criteriaBuilder) -> {
            if (userId == null || userId.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("userId"), userId.trim());
        };
    }

    public static Specification<FraudDecision> hasDecision(DecisionType decision) {
        return (root, query, criteriaBuilder) -> {
            if (decision == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("decision"), decision);
        };
    }

    public static Specification<FraudDecision> amountGte(BigDecimal minAmount) {
        return (root, query, criteriaBuilder) -> {
            if (minAmount == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount);
        };
    }

    public static Specification<FraudDecision> amountLte(BigDecimal maxAmount) {
        return (root, query, criteriaBuilder) -> {
            if (maxAmount == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount);
        };
    }

    public static Specification<FraudDecision> createdAtFrom(Instant from) {
        return (root, query, criteriaBuilder) -> {
            if (from == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from);
        };
    }

    public static Specification<FraudDecision> createdAtTo(Instant to) {
        return (root, query, criteriaBuilder) -> {
            if (to == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to);
        };
    }

    public static Specification<FraudDecision> build(
            String userId,
            DecisionType decision,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Instant from,
            Instant to
    ) {
        return Specification.where(hasUserId(userId))
                .and(hasDecision(decision))
                .and(amountGte(minAmount))
                .and(amountLte(maxAmount))
                .and(createdAtFrom(from))
                .and(createdAtTo(to));
    }
}
