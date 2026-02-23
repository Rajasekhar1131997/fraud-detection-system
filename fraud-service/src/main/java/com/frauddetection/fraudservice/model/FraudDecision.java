package com.frauddetection.fraudservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_decisions")
public class FraudDecision {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String transactionId;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionType decision;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal ruleScore;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal mlScore;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public FraudDecision() {
    }

    public FraudDecision(
            UUID id,
            String transactionId,
            String userId,
            BigDecimal riskScore,
            DecisionType decision,
            BigDecimal ruleScore,
            BigDecimal mlScore,
            Instant createdAt
    ) {
        this.id = id;
        this.transactionId = transactionId;
        this.userId = userId;
        this.riskScore = riskScore;
        this.decision = decision;
        this.ruleScore = ruleScore;
        this.mlScore = mlScore;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public DecisionType getDecision() {
        return decision;
    }

    public void setDecision(DecisionType decision) {
        this.decision = decision;
    }

    public BigDecimal getRuleScore() {
        return ruleScore;
    }

    public void setRuleScore(BigDecimal ruleScore) {
        this.ruleScore = ruleScore;
    }

    public BigDecimal getMlScore() {
        return mlScore;
    }

    public void setMlScore(BigDecimal mlScore) {
        this.mlScore = mlScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (mlScore == null) {
            mlScore = BigDecimal.ZERO.setScale(4);
        }
    }
}
